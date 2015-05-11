package org.jikesrvm.replay.tracefile.ditto;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.sys.ditto.DittoRecorderThreadState;
import org.jikesrvm.replay.sys.ditto.LoadEvent;
import org.jikesrvm.replay.sys.ditto.StoreEvent;
import org.jikesrvm.replay.sys.ditto.SyncEvent;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputTableChunk;
import org.jikesrvm.replay.tracefile.TableChunk;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;

/**
 * Output version of the ditto trace file.
 * <p>
 * The threads being traced are not responsible for actually writing to disk.
 * Instead, a group of writer threads is maintained and given resposability for
 * performing I/O on the trace file. The number of writer threads can be
 * configured through the
 * ({@link org.jikesrvm.replay.ReplayOptions#NUM_IO_THREADS}) option.
 */
public final class OutputDittoTraceFile extends DittoTraceFile {

  /** List of threads currently being traced to this trace file. */
  private List<RVMThread> runningThreads;

  /** Trace file header. */
  private OutputDittoTraceFileHeader outputHeader;

  /** Current table chunk. */
  private OutputTableChunk tableChunk;

  /** Buffer used to write table chunks. */
  private ByteBuffer buffer;

  /** Writer threads. */
  private WriterThread[] writerThreads;

  /** Whether the trace file is finished. */
  private boolean finished;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   */
  public OutputDittoTraceFile(File file, RandomAccessFile stream) {
    super(file, stream);
    outputHeader = (OutputDittoTraceFileHeader)header;
    runningThreads = Collections.synchronizedList(new ArrayList<RVMThread>());
    tableChunk = new OutputTableChunk();
    buffer = ByteBuffer.allocateDirect(
        ChunkHeader.BYTES + TableChunk.MAX_BYTES);
    writerThreads = new WriterThread[ReplayOptions.NUM_IO_THREADS];
    finished = false;
  }

  /**
   * Initializes the output trace file, so that it is ready for tracing.
   * Also launches the writer threads.
   * @throws TraceFileException if file I/O fails
   */
  public void init() throws TraceFileException {
    synchronized (stream) {
      try {
        stream.seek(DittoTraceFileHeader.BYTES);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
    WriterThread.init();
    for (int i = 0; i < ReplayOptions.NUM_IO_THREADS; i++) {
      writerThreads[i] = new WriterThread(this, stream);
      writerThreads[i].start();
    }
  }


  ///
  /// Event tracing
  ///

  /**
   * Traces a load event in a given data chunk. The event is traced as a free
   * run value (if its free run is non-zero) and a logical clock value.
   * @param  dataChunk          data chunk in which to trace the event
   * @param  evt                load event
   * @throws TraceFileException if tracing fails
   */
  @Inline
  public void traceEvent(OutputDittoDataChunk dataChunk, LoadEvent evt)
                  throws TraceFileException {
    if (evt.freeRun > 0) {
      traceFreeRunValue(dataChunk, evt.freeRun);
    }
    traceClockValue(dataChunk, evt.storeClock);
  }

  /**
   * Traces a store event in a given data chunk. The event is traced as a free
   * run value (if its free run is non-zero), logical clock value and a count
   * value.
   * @param  dataChunk          data chunk in which to trace the event
   * @param  evt                store event
   * @throws TraceFileException if tracing fails
   */
  @Inline
  public void traceEvent(OutputDittoDataChunk dataChunk, StoreEvent evt)
                  throws TraceFileException {
    if (evt.freeRun > 0) {
      traceFreeRunValue(dataChunk, evt.freeRun);
    }
    traceClockValue(dataChunk, evt.storeClock);
    traceCountValue(dataChunk, evt.loadCount);
  }

  /**
   * Traces a synchronization event in a given data chunk. The event is traced
   * as a free run value (if its free run is non-zero) and a logical clock
   * value.
   * @param  dataChunk          data chunk in which to trace the event
   * @param  evt                synchronization event
   * @throws TraceFileException if tracing fails
   */
  @Inline
  public void traceEvent(OutputDittoDataChunk dataChunk, SyncEvent evt)
                  throws TraceFileException {
    if (evt.freeRun > 0) {
      traceFreeRunValue(dataChunk, evt.freeRun);
    }
    traceClockValue(dataChunk, evt.clock);
  }

  /**
   * Traces a free in a given data chunk.
   * @param  dataChunk          data chunk in which to trace the free run
   * @param  freeRun            free run
   * @throws TraceFileException if tracing fails
   */
  public void traceFreeRun(OutputDittoDataChunk dataChunk, int freeRun)
                    throws TraceFileException {
    traceFreeRunValue(dataChunk, freeRun);
  }

  /**
   * Traces a logical clock value in a given data chunk. If the chunk is full,
   * it will be rotated.
   * @param  dataChunk          data chunk in which to trace the value
   * @param  value              logical clock value
   * @throws TraceFileException if tracing fails
   */
  @Inline
  private void traceClockValue(OutputDittoDataChunk dataChunk, long value)
                        throws TraceFileException {
    while (!dataChunk.tryAddClockValue(value)) {
      if (!dataChunk.isEmpty()) {
        dataChunk.prepareToWrite(false);
        WriterThread.addDataChunk(dataChunk);
        dataChunk.reset();
      } else {
        dataChunk.reset();
      }
    }
  }

  /**
   * Traces a count value in a given data chunk. If the chunk is full, it will
   * be rotated.
   * @param  dataChunk          data chunk in which to trace the value
   * @param  value              count value
   * @throws TraceFileException if tracing fails
   */
  @Inline
  private void traceCountValue(OutputDittoDataChunk dataChunk, long value)
                        throws TraceFileException {
    while (!dataChunk.tryAddCountValue(value)) {
      if (!dataChunk.isEmpty()) {
        dataChunk.prepareToWrite(false);
        WriterThread.addDataChunk(dataChunk);
        dataChunk.reset();
      } else {
        dataChunk.reset();
      }
    }
  }

  /**
   * Traces a free run value in a given data chunk. If the chunk is full, it
   * will be rotated.
   * @param  dataChunk          data chunk in which to trace the value
   * @param  freeRunValue       free run value
   * @throws TraceFileException if tracing fails
   */
  @Inline
  private void traceFreeRunValue(OutputDittoDataChunk dataChunk,
                                 long freeRunValue) throws TraceFileException {
    while (!dataChunk.tryAddFreeRun(freeRunValue)) {
      if (!dataChunk.isEmpty()) {
        dataChunk.prepareToWrite(false);
        WriterThread.addDataChunk(dataChunk);
        dataChunk.reset();
      } else {
        dataChunk.reset();
      }
    }
  }


  ///
  /// Thread lifetime management
  ///

  /**
   * Initializes the trace for a given thread.
   * @param thr the thread
   */
  public void initThreadTrace(RVMThread thr) {
    runningThreads.add(thr);
  }

  /**
   * Finishes the trace for a given thread.
   * @param  thr                the thread
   * @throws TraceFileException if finishing the thread's trace fails
   */
  public void finishThreadTrace(RVMThread thr) throws TraceFileException {
    DittoRecorderThreadState ts = thr.dittoRecState;
    synchronized (ts) {
      // if the thread's trace is already finished do nothing
      if (ts.traceFinished) {
        return;
      }

      // if the thread is currently in a non-zero free run, trace it directly,
      // as there will be no more events
      if (ts.currentFreeRun > 0) {
        traceFreeRun(ts.outputDataChunk, ts.currentFreeRun);
      }

      OutputDittoDataChunk dc = thr.dittoRecState.outputDataChunk;

      // if the thread did not generate any trace, register it in the table
      // chunk as having no data chunks (null offset)
      if (dc.isFirst() && dc.isEmpty()) {
        registerChunkChain(thr.replayId, NULL_OFFSET);
      }
      // otherwise, dump the data chunk to disk (this thread is already done
      // executing, there is no need to delegate this job to a writer thread)
      // if the data chunk is the thread's first, register its offset in the
      // table chunk
      else {
        dc.prepareToWrite(true);
        long offset = write(dc);
        if (dc.isFirst()) {
          registerChunkChain(thr.replayId, offset);
        }
      }

      runningThreads.remove(thr);
      ts.traceFinished = true;
    }
  }

  /**
   * Finishes the trace file.
   * <p> If there are still threads running, their traces are closed as well,
   * which is useful when the application is in a deadlock situation.
   * @throws TraceFileException if an I/O error occurs
   */
  public synchronized void finish() throws TraceFileException {
    // if the trace file is already finished, do nothing
    if (finished) {
      return;
    }

    // finish the trace of every still-running thread
    while (true) {
      if (runningThreads.size() > 0) {
        RVMThread thr = runningThreads.get(0);
        finishThreadTrace(thr);
      } else {
        break;
      }
    }

    // wait until the writer threads have no more work
    for (WriterThread wt : writerThreads) {
      wt.waitForTermination();
    }

    // write the current table chunk and the file header
    long offset = write(tableChunk);
    if (tableChunk.isFirst()) {
      outputHeader.setOffsetToTable(offset);
    }
    write(outputHeader);

    finished = true;
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
  public synchronized void registerChunkChain(long threadId, long startOffset)
                                       throws TraceFileException {
    if (!tableChunk.tryAddEntry(threadId, startOffset)) {
      long offset = write(tableChunk);
      if (tableChunk.isFirst()) {
        outputHeader.setOffsetToTable(offset);
      }
      tableChunk = new OutputTableChunk();
      tableChunk.previousChunkOffset = offset;
      tableChunk.tryAddEntry(threadId, startOffset);
    }
  }


  ///
  /// Writers
  ///

  /**
   * Writes a ditto data chunk to the trace file in a thread-safe manner.
   * @param  dataChunk          data chunk
   * @return                    absolute trace file offset where the data chunk
   *                            was written
   * @throws TraceFileException if an I/O error occurs
   */
  private long write(OutputDittoDataChunk dataChunk) throws TraceFileException {
    synchronized (stream) {
      try {
        return dataChunk.write(stream);
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
    synchronized (stream) {
      buffer.clear();
      tableChunk.write(buffer);
      buffer.flip();
      try {
        FileChannel fc = stream.getChannel();
        long offset = fc.position();
        fc.write(buffer);

        // if this is not the first table chunk, go back to the previous one and
        // write the forward pointer into this one
        if (!tableChunk.isFirst()) {
          long now = fc.position();
          fc.position(tableChunk.previousChunkOffset
                      + OutputTableChunk.TOTAL_NEXT_CHUNK_OFFSET);
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
  private void write(OutputDittoTraceFileHeader header)
              throws TraceFileException {
    synchronized (stream) {
      buffer.clear();
      header.write(buffer);
      buffer.flip();
      try {
        FileChannel fc = stream.getChannel();
        fc.position(HEADER_OFFSET);
        fc.write(buffer);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }
}
