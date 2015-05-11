package org.jikesrvm.replay.tracefile.ditto;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;
import java.util.Queue;

import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.SystemThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NonMoving;

/**
 * A writer thread handles requests from other threads to write trace file
 * chunks to disk.
 * <p>
 * The main advantage of using writer threads is that the application's
 * threads being traced do not have to be delayed due to the I/O required to
 * build the trace file.
 */
@NonMoving
public final class WriterThread extends SystemThread {

  /** Queue of pending requests. */
  private static Queue<OutputDittoDataChunk> toWriteChunks;

  /** Ditto trace file. */
  private OutputDittoTraceFile traceFile;

  /** Trace file stream. */
  private RandomAccessFile stream;

  /** Whether the writer thread should exit when there are no more requests. */
  private boolean stop = false;

  /**
   * Object on whose monitor other threads may wait until the writer thread
   * exits due to {@link #stop} being {@code true} and lack of requests to
   * handle.
   */
  private Object terminationNotifier = new Object();

  /** Initializes the structures used by the writer threads. */
  public static void init() {
    toWriteChunks = new LinkedList<OutputDittoDataChunk>();
  }

  /**
   * Adds a request to write a given data chunk to the request queue.
   * @param dataChunk data chunk
   */
  @Inline
  public static void addDataChunk(OutputDittoDataChunk dataChunk) {
    synchronized (toWriteChunks) {
      toWriteChunks.offer(dataChunk);
      toWriteChunks.notify();
    }
  }

  /**
   * Constructor.
   * @param  traceFile ditto trace file
   * @param  stream    trace file stream
   */
  public WriterThread(OutputDittoTraceFile traceFile, RandomAccessFile stream) {
    super("Ditto Trace Writer");
    this.rvmThread.setPriority(Thread.MAX_PRIORITY);
    this.traceFile = traceFile;
    this.stream = stream;
  }

  /**
   * Requests the writer thread to terminate as soon as it runs out of requests
   * to handle, and blocks until it exits.
   */
  public void waitForTermination() {
    for (;;) {
      try {
        synchronized (terminationNotifier) {
          stop = true;
          this.getRVMThread().interrupt();
          terminationNotifier.wait();
        }
        return;
      } catch (InterruptedException ie) { }
    }
  }

  @Override
  public void run() {
    for (;;) {

      // get a data chunk from the requests queue
      OutputDittoDataChunk dataChunk;
      for (;;) {
        if (!stop) {
          dataChunk = getNextChunk();
          if (dataChunk != null) {
            break;
          }
        } else {
          synchronized (WriterThread.class) {
            // the thread has been requested to stop, but it must finish
            // handling all the requests before it terminates
            if (!toWriteChunks.isEmpty()) {
              dataChunk = getNextChunk();
              break;
            }

            // the thread is done, notify anyone waiting for this and exit
            synchronized (terminationNotifier) {
              terminationNotifier.notifyAll();
            }
            return;
          }
        }
      }

      // we got a data chunk request; execute it
      long offset;
      try {
        // write the chunk in a thread-safe manner
        synchronized (stream) {
          offset = dataChunk.write(stream);
        }

        // register the chunk with the trace file if it is the thread's first
        if (dataChunk.isFirst()) {
          traceFile.registerChunkChain(dataChunk.getThreadId(), offset);
        }

        // signal that the data chunk has been written to disk
        dataChunk.notifyWrite(offset);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
      } catch (IOException ioe) {
        ReplayManager.failAndExit(new TraceFileException(ioe));
      }
    }
  }

  /**
   * Gets a data chunk from the requests queue.
   * @return data chunk
   */
  private OutputDittoDataChunk getNextChunk() {
    OutputDittoDataChunk dataChunk = null;
    synchronized (toWriteChunks) {
      for (;;) {
        try {
          dataChunk = toWriteChunks.poll();
          if (dataChunk != null) {
            break;
          } else {
            toWriteChunks.wait();
          }
        } catch (InterruptedException ie) {
          // if the writer thread is requested to terminate, it will also be
          // interrupted
          if (stop) {
            return null;
          }
        }
      }
    }
    return dataChunk;
  }
}
