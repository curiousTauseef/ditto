package org.jikesrvm.replay.tracefile.jarec;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jikesrvm.replay.sys.jarec.JaRecRecorderThreadState;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputTableChunk;
import org.jikesrvm.replay.tracefile.TableChunk;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Output version of the jarec trace file.
 */
public final class OutputJaRecTraceFile extends JaRecTraceFile {

  /** List of threads currently being traced to this trace file. */
  private List<RVMThread> runningThreads;

  /** Trace file header. */
  private OutputJaRecTraceFileHeader outputHeader;

  /** Current table chunk. */
  private OutputTableChunk tableChunk;

  /** Buffer used to write table chunks. */
  private ByteBuffer buffer;

  /** Whether the trace file is finished. */
  private boolean finished;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   */
  public OutputJaRecTraceFile(File file, RandomAccessFile stream) {
    super(file, stream);
    runningThreads = Collections.synchronizedList(new ArrayList<RVMThread>());
    outputHeader = (OutputJaRecTraceFileHeader)header;
    tableChunk = new OutputTableChunk();
    buffer = ByteBuffer.allocateDirect(
        ChunkHeader.BYTES + TableChunk.MAX_BYTES);
  }

  /**
   * Initializes the output trace file, so that it is ready for tracing.
   * @throws TraceFileException if an I/O error occurs
   */
  public void init() throws TraceFileException {
    synchronized (stream) {
      try {
        stream.seek(JaRecTraceFileHeader.BYTES);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }


  ///
  /// Interval/logical clock tracing
  ///

  /**
   * Traces a logical clock for a given thread.
   * @param  thr                the thread
   * @throws TraceFileException if an I/O error occurs
   */
  public void traceClock(RVMThread thr) throws TraceFileException {
    JaRecRecorderThreadState ts = thr.jaRecRecState;
    OutputJaRecIntervalChunk ic = ts.intervalChunk;
    if (!ic.tryAddClock(ts.clock)) {
      ic.prepareToWrite();
      long offset = write(ic);
      if (ic.isFirst()) {
        registerChunkChain(thr.replayId, offset);
      }
      ic.reset(offset);
      ic.tryAddClock(ts.clock);
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
   * @throws TraceFileException if an I/O error occurs
   */
  public void finishThreadTrace(RVMThread thr) throws TraceFileException {
    JaRecRecorderThreadState ts = thr.jaRecRecState;
    synchronized (ts) {
      // if the thread's trace is already finished do nothing
      if (ts.traceFinished) {
        return;
      }

      // if the thread did not generate any trace, register it in the table
      // chunk as having no interval chunks (null offset)
      OutputJaRecIntervalChunk ic = ts.intervalChunk;
      if (ic.isFirst() && ic.isEmpty()) {
        registerChunkChain(thr.replayId, NULL_OFFSET);
      }
      // otherwise, dump the interval chunk to disk
      // if the interval chunk is the thread's first, register its offset in the
      // table chunk
      else {
        ic.finish();
        ic.prepareToWrite();
        long offset = write(ic);
        if (ic.isFirst()) {
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
        RVMThread t = runningThreads.get(0);
        finishThreadTrace(t);
      } else {
        break;
      }
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
   * of its interval chunk list.
   * <p>If the current table chunk is full, it will be dumped to disk and a new
   * one started.
   * @param  threadId           thread replay id
   * @param  startOffset        absolute trace file offset of the interval chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private synchronized void registerChunkChain(long threadId, long startOffset)
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
   * Writes an interval chunk to the trace file in a thread-safe manner.
   * @param  intervalChunk      interval chunk
   * @return                    absolute trace file offset where the interval
   *                            chunk was written
   * @throws TraceFileException if an I/O error occurs
   */
  private long write(OutputJaRecIntervalChunk intervalChunk)
              throws TraceFileException {
    synchronized (stream) {
      try {
        long offset = intervalChunk.write(stream);
        return offset;
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }

  /**
   * Writes a table chunk to the trace file in a thread-safe manner.
   * @param  tableChunk         table chunk
   * @return                    absolute trace file offset where the table
   *                            chunk was written
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

        // if this is not the first table chunk, go back to the previous one
        // and write the forward pointer into this one
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
   * @param  header                trace file header
   * @throws TraceFileException if an I/O error occurs
   */
  private void write(OutputJaRecTraceFileHeader header)
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
