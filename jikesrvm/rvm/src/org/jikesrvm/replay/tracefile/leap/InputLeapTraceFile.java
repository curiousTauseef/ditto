package org.jikesrvm.replay.tracefile.leap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.vmmagic.pragma.Inline;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * Input version of the leap trace file.
 */
public final class InputLeapTraceFile extends LeapTraceFile {

  /** Trace file header. */
  private InputLeapTraceFileHeader inputHeader;

  /** Trace file stream. */
  private RandomAccessFile stream;

  /**
   * Already known offsets to the head of each field's data chunk list.
   * Maps field names to the absolute trace file offset of the head of their
   * data chunk list.
   */
  private Map<String, Long> chunkOffsets;

  /** Absolute trace file offset of the next field table chunk. */
  private long nextTableChunkOffset;

  /** List of current leap data chunks, one per traced field. */
  private InputLeapDataChunk[] fieldTraces;

  /** Maximum size of a leap data chunk, in bytes. */
  private int bufferSize;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   * @param  header trace file header
   */
  public InputLeapTraceFile(File file, RandomAccessFile stream,
                            InputLeapTraceFileHeader header) {
    super(file);
    inputHeader = header;
    this.stream = stream;
    chunkOffsets = new HashMap<String, Long>();
    nextTableChunkOffset = header.getTablePtr();
    int numFields = FieldNameIdMap.getNumberOfFields();
    bufferSize = getBufferSize(numFields);
    fieldTraces = new InputLeapDataChunk[numFields];
    for (int i = 0; i < numFields; i++) {
      fieldTraces[i] = null;
    }
  }

  /**
   * Creates an input leap trace file by reading its header from a given file.
   * @param  file               file handle
   * @param  stream             file stream
   * @return                    an input leap trace file instance
   * @throws TraceFileException if an I/O error occurs
   */
  public static InputLeapTraceFile read(File file, RandomAccessFile stream)
                                 throws TraceFileException {
    try {
      FileChannel fc = stream.getChannel();
      fc.position(HEADER_OFFSET);
      ByteBuffer bb = ByteBuffer.allocate(LeapTraceFileHeader.BYTES);
      fc.read(bb);
      bb.flip();
      InputLeapTraceFileHeader header = InputLeapTraceFileHeader.read(bb);
      return new InputLeapTraceFile(file, stream, header);
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }

  /**
   * Waits until it is a given threads turn to access a given field.
   * @param  threadId           thread replay id
   * @param  fieldId            field id
   * @throws TraceFileException if an I/O error occurs
   */
  @Inline
  public void waitForTurn(long threadId, int fieldId)
                   throws TraceFileException {
    // get the current data chunk for the field
    // if there is none, read it from the trace file
    InputLeapDataChunk ftc = fieldTraces[fieldId];
    if (ftc == null) {
      synchronized (fieldTraces) {
        ftc = fieldTraces[fieldId];
        if (ftc == null) {
          ftc = getFirstLeapDataChunk(fieldId);
          fieldTraces[fieldId] = ftc;
        }
      }
    }

    try {
      // if it is the thread's turn to access the field, let it go through
      if (ftc.curThreadId == threadId) {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Leap Replayer] t#" + threadId
              + " going through " + FieldNameIdMap.getNameForId(fieldId));
        }
        return;
      }

      // optimization: many times, yielding once is enough to reach the target
      // logical time
      Thread.yield();
      if (ftc.curThreadId == threadId) {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Leap Replayer] t#" + threadId
              + " going through (after yielding) "
              + FieldNameIdMap.getNameForId(fieldId));
        }
        return;
      }

      // wait for the thread's turn on the monitor of the data chunk itself
      synchronized (ftc) {
        while (ftc.curThreadId != threadId) {
          try {
            if (DEBUG && ReplayOptions.VERBOSITY > 2) {
              VM.sysWriteln("[Leap Replayer] t#" + threadId
                  + " waiting on " + FieldNameIdMap.getNameForId(fieldId));
            }
            ftc.wait();
          } catch (InterruptedException ie) { }
        }
      }
      if (DEBUG && ReplayOptions.VERBOSITY > 2) {
        VM.sysWriteln("[Leap Replayer] t#" + threadId
            + " going through (after wait) "
            + FieldNameIdMap.getNameForId(fieldId));
      }
    } finally {
      // lock the data chunk until the access is finished
      ObjectModel.genericLock(ftc);
    }
  }

  /**
   * Registers an access by a given thread to a given field.
   * @param  threadId           thread replay id
   * @param  fieldId            field id
   * @throws TraceFileException if an I/O error occurs
   */
  @Inline
  public void advance(long threadId, int fieldId) throws TraceFileException {
    // get the field's data chunk
    InputLeapDataChunk ftc = fieldTraces[fieldId];

    try {
      // decrease the number of consecutive accesses the thread is allowed to
      // execute
      ftc.curTimes--;

      // if the thread is not allowed to execute any further accesses, advance
      // the data chunk forward
      if (ftc.curTimes == 0) {
        // if necessary, read a new data chunk
        if (!ftc.hasRemaining()) {
          if (!ftc.isLast()) {
            readNextLeapDataChunk(ftc);
          } else {
            return;
          }
        }

        // advance the data chunk
        ftc.advance();

        // notify threads waiting for their turn to access the field
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Leap Replayer] t#" + threadId
              + " notifying " + FieldNameIdMap.getNameForId(fieldId));
        }
        ftc.notifyAll();
      }
    } finally {
      // finally, unlock the data chunk
      ObjectModel.genericUnlock(ftc);
    }
  }


  ///
  /// Readers
  ///

  /**
   * Reads field table chunks until an entry for a given field is found or the
   * trace file runs out of field table chunks.
   * @param  fieldName          field name
   * @return                    whether the entry was found
   * @throws TraceFileException if an I/O error occurs
   */
  private boolean tryToFindChunkOffset(String fieldName)
                                throws TraceFileException {
    while (!chunkOffsets.containsKey(fieldName)) {
      if (nextTableChunkOffset == NULL_OFFSET) {
        return false;
      }
      InputLeapFieldTableChunk tc = readFieldTableChunkAt(nextTableChunkOffset);
      nextTableChunkOffset = tc.nextChunkOffset;
      tc.fillMap(chunkOffsets);
    }
    return true;
  }

  /**
   * Reads a field table chunk located at a given trace file offset in a
   * thread-safe manner.
   * @param  offset             trace file offset of the field table chunk
   * @return                    read field table chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private InputLeapFieldTableChunk readFieldTableChunkAt(long offset)
                                                  throws TraceFileException {
    synchronized (stream) {
      try {
        // go to the chunk's offset
        FileChannel fc = stream.getChannel();
        fc.position(offset);

        // read the chunk's header
        InputChunkHeader header = new InputChunkHeader(
            ChunkHeader.ChunkType.LeapFieldTable);
        ByteBuffer headerBuf = ByteBuffer.allocate(ChunkHeader.BYTES);
        fc.read(headerBuf);
        headerBuf.flip();
        header.update(headerBuf);

        // read the chunk's body
        ByteBuffer bodyBuf = ByteBuffer.allocate(header.getChunkSize());
        fc.read(bodyBuf);
        bodyBuf.flip();
        return InputLeapFieldTableChunk.read(header, bodyBuf);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }

  /**
   * Reads the first data chunk in a given field's list in a tread-safe manner.
   * @param  fieldId            field id
   * @return                    first data chunk for the field
   * @throws TraceFileException if an I/O error occurs or there is no trace for
   *                            the field
   */
  private InputLeapDataChunk getFirstLeapDataChunk(int fieldId)
                                            throws TraceFileException {
    // get the name of the field from its id
    String fieldName = FieldNameIdMap.getNameForId(fieldId);

    // find the offset to the field's first data chunk and read it
    if (chunkOffsets.containsKey(fieldName)
        || tryToFindChunkOffset(fieldName)) {
      InputLeapDataChunk ftc = new InputLeapDataChunk(fieldName, bufferSize);
      long offset = chunkOffsets.get(fieldName);
      readFirstLeapDataChunk(ftc, offset);
      return ftc;
    } else {
      throw new TraceFileException("No trace for field " + fieldName);
    }
  }

  /**
   * Reads the first data chunk of a given field's list in a thread-safe manner.
   * @param  dataChunk          data chunk instance to fill
   * @param  offset             trace file offset from which to read the chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private void readFirstLeapDataChunk(InputLeapDataChunk dataChunk, long offset)
                               throws TraceFileException {
    try {
      synchronized (stream) {
        dataChunk.read(stream, offset);
      }
      dataChunk.init();
      dataChunk.advance();
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }

  /**
   * Reads the next data chunk in a given field's list in a thread-safe manner.
   * @param  dataChunk          current data chunk; recycled as the next chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private void readNextLeapDataChunk(InputLeapDataChunk dataChunk)
                              throws TraceFileException {
    try {
      synchronized (stream) {
        dataChunk.readNext(stream);
      }
      dataChunk.init();
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }
}
