package org.jikesrvm.replay.tracefile.sync;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.replay.sys.sync.DependencyTraceEvent;
import org.jikesrvm.replay.sys.sync.FreeRunTraceEvent;
import org.jikesrvm.replay.tracefile.OutputTableChunk;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Output version of the sync trace file.
 */
public final class OutputSyncTraceFile extends SyncTraceFile {

  /** Trace file header. */
  private OutputSyncTraceFileHeader outputHeader;

  /** Current table chunk. */
  private OutputTableChunk tableChunk;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   */
  public OutputSyncTraceFile(File file, RandomAccessFile stream) {
    super(file, stream);
    outputHeader = (OutputSyncTraceFileHeader)header;
    tableChunk = new OutputTableChunk();
  }

  /**
   * Initializes the output trace file, so that it is ready for tracing.
   * @throws TraceFileException if file I/O fails
   */
  public void init() throws TraceFileException {
    synchronized (stream) {
      try {
        stream.seek(SyncTraceFileHeader.BYTES);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }


  ///
  /// Event tracing
  ///

  /**
   * Traces a free run event in the current data chunk of a given thread.
   * @param  thr                the thread
   * @param  evt                free run event
   * @throws TraceFileException if tracing fails
   */
  public void traceEvent(RVMThread thr, FreeRunTraceEvent evt)
                  throws TraceFileException {
    OutputSyncDataChunk dc = thr.syncRecState.outputDataChunk;
    if (!dc.tryAddEvent(evt)) {
      long offset = write(dc);
      if (dc.isFirst()) {
        registerChunkChain(thr.replayId, offset);
      }
      dc = new OutputSyncDataChunk();
      dc.previousChunkOffset = offset;
      thr.syncRecState.outputDataChunk = dc;
      dc.tryAddEvent(evt);
    }
  }

  /**
   * Traces a dependency event in the current data chunk of a given thread.
   * @param  thr                the thread
   * @param  evt                dependency event
   * @throws TraceFileException if tracing fails
   */
  public void traceEvent(RVMThread thr, DependencyTraceEvent evt)
                  throws TraceFileException {
    OutputSyncDataChunk dc = thr.syncRecState.outputDataChunk;
    if (!dc.tryAddEvent(evt)) {
      long offset = write(dc);
      if (dc.isFirst()) {
        registerChunkChain(thr.replayId, offset);
      }
      dc = new OutputSyncDataChunk();
      dc.previousChunkOffset = offset;
      thr.syncRecState.outputDataChunk = dc;
      dc.tryAddEvent(evt);
    }
  }


  ///
  /// Thread lifetime management
  ///

  /**
   * Finish the trace for a given thread.
   * @param  thr                the thread
   * @throws TraceFileException if an I/O error occurs
   */
  public void finishThreadTrace(RVMThread thr) throws TraceFileException {
    OutputSyncDataChunk dc = thr.syncRecState.outputDataChunk;
    if (dc == null || dc.isEmpty()) {
      registerChunkChain(thr.replayId, NULL_OFFSET);
    } else {
      long offset = write(dc);
      if (dc.isFirst()) {
        registerChunkChain(thr.replayId, offset);
      }
    }
  }

  /**
   * Finishes the trace file.
   * @throws TraceFileException if an I/O error occurs
   */
  public synchronized void finish() throws TraceFileException {
    long offset = write(tableChunk);
    if (tableChunk.isFirst()) {
      outputHeader.setOffsetToTable(offset);
    }
    write(outputHeader);
  }

  /**
   * Adds an entry to the table chunk, mapping a thread replay id to the head
   * of its data chunk list.
   * <p>If the current table chunk is full, it will be dumped to disk and a new
   * one started.
   * @param  threadId           thread replay id
   * @param  startOffset        absolute trace file offset of the data chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private synchronized void registerChunkChain(long threadId, long startOffset)
                                        throws TraceFileException {
    if (!tableChunk.tryAddEntry(threadId, startOffset)) {
      long offset = write(tableChunk);
      if (offset != NULL_OFFSET) {
        if (tableChunk.isFirst()) {
          outputHeader.setOffsetToTable(offset);
        }
        tableChunk = new OutputTableChunk();
        tableChunk.previousChunkOffset = offset;
        tableChunk.tryAddEntry(threadId, startOffset);
      }
    }
  }


  ///
  /// Writers
  ///

  /**
   * Writes a sync data chunk to the trace file in a thread-safe manner.
   * @param  dataChunk          data chunk
   * @return                    absolute trace file offset where the data chunk
   *                            was written
   * @throws TraceFileException if an I/O error occurs
   */
  private long write(OutputSyncDataChunk dataChunk) throws TraceFileException {
    ByteBuffer buf = dataChunk.write();
    buf.flip();
    synchronized (stream) {
      try {
        FileChannel fc = stream.getChannel();
        long offset = fc.position();
        fc.write(buf);
        long previous = dataChunk.previousChunkOffset;
        if (previous != NULL_OFFSET) {
          long now = fc.position();
          fc.position(previous + OutputSyncDataChunk.TOTAL_NEXT_CHUNK_OFFSET);
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
   * Writes a table chunk to the trace file in a thread-safe manner.
   * @param  tableChunk         table chunk
   * @return                    absolute trace file offset where the table chunk
   *                            was written
   * @throws TraceFileException if an I/O error occurs
   */
  private long write(OutputTableChunk tableChunk) throws TraceFileException {
    ByteBuffer buf = tableChunk.write();
    buf.flip();
    synchronized (stream) {
      try {
        FileChannel fc = stream.getChannel();
        long offset = fc.position();
        fc.write(buf);
        long previous = tableChunk.previousChunkOffset;
        if (previous != NULL_OFFSET) {
          long now = fc.position();
          fc.position(previous + OutputTableChunk.TOTAL_NEXT_CHUNK_OFFSET);
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
  private void write(OutputSyncTraceFileHeader header)
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
