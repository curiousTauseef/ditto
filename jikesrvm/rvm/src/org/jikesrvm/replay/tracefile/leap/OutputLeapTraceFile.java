package org.jikesrvm.replay.tracefile.leap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.replay.tracefile.TraceFileException;
import org.vmmagic.pragma.Inline;

/**
 * Output version of the leap trace file.
 */
public final class OutputLeapTraceFile extends LeapTraceFile {

  /** Trace file stream. */
  private RandomAccessFile stream;

  /** Trace file header. */
  private OutputLeapTraceFileHeader outputHeader;

  /** List of current leap data chunks, one per traced field. */
  public OutputLeapDataChunk[] fieldTraces;

  /** Current field table chunk. */
  private OutputLeapFieldTableChunk tableChunk;

  /** Whether the trace file is finished. */
  private boolean finished;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   */
  public OutputLeapTraceFile(File file, RandomAccessFile stream) {
    super(file);
    this.stream = stream;
    outputHeader = (OutputLeapTraceFileHeader)header;
    finished = false;
    tableChunk = new OutputLeapFieldTableChunk();
  }

  /**
   * Initializes the output trace file, so that it is ready for tracing.
   * @throws TraceFileException [description]
   */
  public void init() throws TraceFileException {
    int numFields = FieldNameIdMap.getNumberOfFields();
    int bufferSize = getBufferSize(numFields);
    fieldTraces = new OutputLeapDataChunk[numFields];
    for (int i = 0; i < numFields; i++) {
      fieldTraces[i] = new OutputLeapDataChunk(
          FieldNameIdMap.getNameForId(i), bufferSize);
    }
    synchronized (stream) {
      try {
        stream.seek(LeapTraceFileHeader.BYTES);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }


  ///
  /// Access tracing
  ///

  /**
   * Traces an access to a given field by a given thread.
   * @param  fieldDataChunk     leap data chunk of the field
   * @param  threadId           thread replay id
   * @throws TraceFileException if an I/O error occurs
   */
  @Inline
  public void traceAccess(OutputLeapDataChunk fieldDataChunk, long threadId)
                   throws TraceFileException {
    // try to add the access to the current data chunk
    while (!fieldDataChunk.tryAddAccess(threadId)) {
      // if not possible, write the current data chunk to the trace file
      // and reset it
      fieldDataChunk.prepareToWrite();
      long offset = write(fieldDataChunk);
      if (fieldDataChunk.isFirst()) {
        registerChunkChain(fieldDataChunk.getFieldName(), offset);
      }
      fieldDataChunk.reset(offset);
    }
  }

  /**
   * Finishes the trace file.
   * @throws TraceFileException if an I/O error occurs
   */
  public synchronized void finish() throws TraceFileException {
    // if the trace file is already finished, do nothing
    if (finished) {
      return;
    }

    // close the traces for each field
    for (int i = 0; i < fieldTraces.length; i++) {
      OutputLeapDataChunk ft = fieldTraces[i];
      if (!ft.isEmpty()) {
        ft.prepareToWrite();
        long offset = write(ft);
        if (ft.isFirst()) {
          registerChunkChain(ft.getFieldName(), offset);
        }
      }
    }

    // close the final field table chunk and the file header
    long offset = write(tableChunk);
    if (tableChunk.isFirst()) {
      outputHeader.setTablePtr(offset);
    }
    write(outputHeader);

    finished = true;
  }

  /**
   * Adds an entry to the field table chunk, mapping a field name to the head
   * of its data chunk list.
   * <p>If the current field table chunk is full, it will be dumped to disk and
   * a new one started.
   * @param  fieldName          field name
   * @param  startOffset        absolute trace file offset of the data chunk
   * @throws TraceFileException if an I/O error occurs
   */
  public synchronized void registerChunkChain(String fieldName,
                                              long startOffset)
                                       throws TraceFileException {
    if (!tableChunk.tryAddEntry(fieldName, startOffset)) {
      long offset = write(tableChunk);
      if (tableChunk.isFirst()) {
        outputHeader.setTablePtr(offset);
      }
      tableChunk = new OutputLeapFieldTableChunk();
      tableChunk.previousChunkOffset = offset;
      tableChunk.tryAddEntry(fieldName, startOffset);
    }
  }


  ///
  /// Writers
  ///

  /**
   * Writes a leap data chunk to the trace file in a thread-safe manner.
   * @param  dataChunk          data chunk
   * @return                    absolute trace file offset where the data chunk
   *                            was written
   * @throws TraceFileException if an I/O error occurs
   */
  public long write(OutputLeapDataChunk dataChunk) throws TraceFileException {
    synchronized (stream) {
      try {
        return dataChunk.write(stream);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }

  /**
   * Writes a field table chunk to the trace file in a thread-safe manner.
   * @param  fieldTableChunk    field table chunk
   * @return                    absolute trace file offset where the field table
   *                            chunk was written
   * @throws TraceFileException if an I/O error occurs
   */
  private long write(OutputLeapFieldTableChunk fieldTableChunk)
              throws TraceFileException {
    ByteBuffer buf = fieldTableChunk.write();
    buf.flip();
    synchronized (stream) {
      try {
        FileChannel fc = stream.getChannel();
        long offset = fc.position();
        fc.write(buf);

        // if this is not the first field table chunk, go back to the previous
        // one and write the forward pointer into this one
        if (!fieldTableChunk.isFirst()) {
          long now = fc.position();
          fc.position(fieldTableChunk.previousChunkOffset
                      + OutputLeapFieldTableChunk.TOTAL_NEXT_CHUNK_OFFSET);
          stream.writeLong(offset);
          fc.position(now);
        }

        return offset;
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }

  /**
   * Writes the trace file header in a thread-safe manner.
   * @param  header             trace file header
   * @throws TraceFileException if an I/O error occurs
   */
  private void write(OutputLeapTraceFileHeader header)
              throws TraceFileException {
    ByteBuffer buf = header.write();
    buf.flip();
    synchronized (stream) {
      try {
        FileChannel fc = stream.getChannel();
        fc.position(HEADER_OFFSET);
        fc.write(buf);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }
}
