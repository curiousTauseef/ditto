package org.jikesrvm.replay.tracefile.ditto;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.VM;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.sys.ditto.LoadEvent;
import org.jikesrvm.replay.sys.ditto.StoreEvent;
import org.jikesrvm.replay.sys.ditto.SyncEvent;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;
import org.jikesrvm.replay.tracefile.InputTableChunk;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * Input version of the ditto trace file.
 */
public final class InputDittoTraceFile extends DittoTraceFile {

  /**
   * Already known offsets to the head of each thread's data chunk list.
   * Maps thread replay ids to the absolute trace file offset of the head of
   * their data chunk list.
   */
  private Map<Long, Long> dataChunkOffsets;

  /** Absolute trace file offset of the next table chunk. */
  private long nextTableChunkOffset;

  /** Auxiliary buffer used to read chunk headers. */
  private ByteBuffer headerBuffer;

  /**
   * Private constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   * @param  header trace file header
   */
  private InputDittoTraceFile(File file, RandomAccessFile stream,
                              InputDittoTraceFileHeader header) {
    super(file, stream, header);
    dataChunkOffsets = Collections.synchronizedMap(new HashMap<Long, Long>());
    nextTableChunkOffset = header.getOffsetToTable();
    headerBuffer = ByteBuffer.allocate(ChunkHeader.BYTES);
  }

  /**
   * Creates an input ditto trace file by reading its header from a given file.
   * @param  file               file handle
   * @param  stream             file stream
   * @return                    an input ditto trace file instance
   * @throws TraceFileException if an I/O error occurs
   */
  public static InputDittoTraceFile read(File file, RandomAccessFile stream)
                                  throws TraceFileException {
    try {
      FileChannel fc = stream.getChannel();
      fc.position(HEADER_OFFSET);
      ByteBuffer buf = ByteBuffer.allocate(DittoTraceFileHeader.BYTES);
      fc.read(buf);
      buf.flip();
      InputDittoTraceFileHeader header = InputDittoTraceFileHeader.read(buf);
      return new InputDittoTraceFile(file, stream, header);
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }


  ///
  /// Event reading
  ///

  /**
   * Reads a load event from a data chunk.
   * <p>If the current data chunk is exhausted, the next one will be read.
   * @param  dataChunk          data chunk
   * @param  evt                load event instance to fill
   * @throws TraceFileException if the trace does not contain enough data
   */
  @Inline
  public void getNextLoadEvent(InputDittoDataChunk dataChunk, LoadEvent evt)
                        throws TraceFileException {
    long value = getNextClockValue(dataChunk);
    if (dataChunk.wasLastValueFreeRun()) {
      evt.freeRun = value;
    } else {
      evt.freeRun = 0;
      evt.storeClock = value;
    }
    if (DEBUG && ReplayOptions.VERBOSITY > 3) {
      VM.sysWriteln("[Ditto Trace] t#" + RVMThread.getCurrentThread().replayId
          + " read load event: " + evt);
    }
  }

  /**
   * Reads a store event from a data chunk.
   * <p>If the current data chunk is exhausted, the next one will be read.
   * @param  dataChunk          data chunk
   * @param  evt                store event instance to fill
   * @throws TraceFileException if the trace does not contain enough data
   */
  @Inline
  public void getNextStoreEvent(InputDittoDataChunk dataChunk, StoreEvent evt)
                         throws TraceFileException {
    long value = getNextClockValue(dataChunk);
    if (dataChunk.wasLastValueFreeRun()) {
      evt.freeRun = value;
    } else {
      evt.freeRun = 0;
      evt.storeClock = value;
      evt.loadCount = (int)getNextCountValue(dataChunk);
    }
    if (DEBUG && ReplayOptions.VERBOSITY > 3) {
      VM.sysWriteln("[Ditto Trace] t#" + RVMThread.getCurrentThread().replayId
          + " read store event: " + evt);
    }
  }

  /**
   * Reads a synchronization event from a data chunk.
   * <p>If the current data chunk is exhausted, the next one will be read.
   * @param  dataChunk          data chunk
   * @param  evt                synchronization event instance to fill
   * @throws TraceFileException if the trace does not contain enough data
   */
  @Inline
  public void getNextSyncEvent(InputDittoDataChunk dataChunk, SyncEvent evt)
                        throws TraceFileException {
    long value = getNextClockValue(dataChunk);
    if (dataChunk.wasLastValueFreeRun()) {
      evt.freeRun = value;
    } else {
      evt.freeRun = 0;
      evt.clock = value;
    }
    if (DEBUG && ReplayOptions.VERBOSITY > 3) {
      VM.sysWriteln("[Ditto Trace] t#" + RVMThread.getCurrentThread().replayId
          + " read sync event: " + evt);
    }
  }

  /**
   * Reads a clock value from a data chunk.
   * @param  dataChunk          data chunk
   * @return                    logical clock or free run value
   * @throws TraceFileException if the trace does not contain enough data
   */
  @Inline
  private long getNextClockValue(InputDittoDataChunk dataChunk)
                          throws TraceFileException {
    while (true) {
      // if the data chunk still has values to read, go ahead
      if (dataChunk.hasRemaining()) {
        return dataChunk.getNextClockValue();
      }

      // otherwise, if the data chunk is not the thread's last, read the next
      // data chunk and retry
      if (!dataChunk.isLast()) {
        readNextDataChunk(dataChunk);
        continue;
      }

      // if there is no more trace, something went wrong...
      throw new TraceFileException("Not enough trace for thread "
          + RVMThread.getCurrentThread().replayId);
    }
  }

  /**
   * Reads a count value from a data chunk.
   * @param  dataChunk          data chunk
   * @return                    count value
   * @throws TraceFileException if the trace does not contain enough data
   */
  @Inline
  private long getNextCountValue(InputDittoDataChunk dataChunk)
                          throws TraceFileException {
    while (true) {
      // if the data chunk still has values to read, go ahead
      if (dataChunk.hasRemaining()) {
        return dataChunk.getNextCountValue();
      }

      // otherwise, if the data chunk is not the thread's last, read the next
      // data chunk and retry
      if (!dataChunk.isLast()) {
        readNextDataChunk(dataChunk);
        continue;
      }

      // if there is no more trace, something went wrong...
      throw new TraceFileException("Not enough trace for thread"
          + RVMThread.getCurrentThread().replayId);
    }
  }


  ///
  /// Thread lifetime management
  ///

  /**
   * Initializes the trace of a given thread by reading its first data chunk.
   * @param  thr                the thread
   * @throws TraceFileException if there is no trace for the thread or an I/O
   *                            error occurs
   */
  public void initThreadTrace(RVMThread thr) throws TraceFileException {
    thr.dittoRepState.inputDataChunk = getFirstDataChunk(thr.replayId);
  }

  /**
   * Obtains the first data chunk in a given thread's trace.
   * @param  threadId           thread replay id
   * @return                    first data chunk of the thread
   * @throws TraceFileException if there is no trace for the thread or an I/O
   *                            error occurs
   */
  private InputDittoDataChunk getFirstDataChunk(long threadId)
                                         throws TraceFileException {
    // find the head of the thread's data chunk list: start by looking at the
    // already known initial data chunks; if necessary, read more table chunks
    // until the entry for the thread is found or we run out of table chunks
    if (!dataChunkOffsets.containsKey(threadId)
        && !tryToFindDataChunkOffset(threadId)) {
      throw new TraceFileException("No trace for thread " + threadId);
    }

    // get the offset of the first data chunk
    long offset = dataChunkOffsets.get(threadId);
    dataChunkOffsets.remove(threadId);
    if (DEBUG && ReplayOptions.VERBOSITY > 0) {
      VM.sysWriteln("[Ditto Trace] t#" + threadId
          + "'s first data chunk is at " + offset);
    }

    // if the thread has a non-null data chunk list, read its first chunk
    if (offset != NULL_OFFSET) {
      InputDittoDataChunk dataChunk = new InputDittoDataChunk();
      readDataChunkAt(dataChunk, offset);
      return dataChunk;
    }

    // otherwise, return a null data chunk
    return InputDittoDataChunk.nullInstance();
  }


  ///
  /// Readers
  ///

  /**
   * Reads table chunks until an entry for a given thread is found or the trace
   * file runs out of table chunks.
   * @param  threadId           thread replay id
   * @return                    whether the entry was found
   * @throws TraceFileException if an I/O error occurs
   */
  private boolean tryToFindDataChunkOffset(long threadId)
                                    throws TraceFileException {
    while (!dataChunkOffsets.containsKey(threadId)) {
      if (nextTableChunkOffset == NULL_OFFSET) {
        return false;
      }
      InputTableChunk tc = readTableChunkAt(nextTableChunkOffset);
      nextTableChunkOffset = tc.nextChunkOffset;
      tc.fillMap(dataChunkOffsets);
    }
    return true;
  }

  /**
   * Reads a table chunk located at a given trace file offset in a thread-safe
   * manner.
   * @param  offset             trace file offset of the table chunk
   * @return                    read table chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private InputTableChunk readTableChunkAt(long offset)
                                    throws TraceFileException {
    synchronized (stream) {
      try {
        // go to the chunk's offset
        FileChannel fc = stream.getChannel();
        fc.position(offset);

        // read the chunk's header
        InputChunkHeader header =
            new InputChunkHeader(ChunkHeader.ChunkType.Table);
        headerBuffer.clear();
        fc.read(headerBuffer);
        headerBuffer.flip();
        header.update(headerBuffer);

        // read the chunk's body
        ByteBuffer data = ByteBuffer.allocate(header.getChunkSize());
        fc.read(data);
        data.flip();
        return InputTableChunk.read(header, data);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }

  /**
   * Reads a data chunk located at a given trace file offset in a thread-safe
   * manner.
   * @param  dataChunk          data chunk instance to fill
   * @param  offset             trace file offset of the data chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private void readDataChunkAt(InputDittoDataChunk dataChunk, long offset)
                        throws TraceFileException {
    try {
      synchronized (stream) {
        dataChunk.read(stream, offset);
      }
      dataChunk.init();
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }

  /**
   * Reads the next data chunk in a given data chunk's list in a thread-safe
   * manner.
   * @param  dataChunk          current data chunk; recycled as the next chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private void readNextDataChunk(InputDittoDataChunk dataChunk)
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
