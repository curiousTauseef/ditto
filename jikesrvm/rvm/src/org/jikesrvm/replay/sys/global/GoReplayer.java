package org.jikesrvm.replay.sys.global;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.concurrent.ConcurrentHashMap;

import org.jikesrvm.VM;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.global.InputGoTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * Global order replayer.
 */
public final class GoReplayer implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final GoReplayer INSTANCE = new GoReplayer();

  /** Private constructor. */
  private GoReplayer() { }

  /** Trace file stream. */
  private static DataInputStream inputStream;

  /** Global order input trace file. */
  private static InputGoTraceFile inputTraceFile;

  /**
   * Table in which threads may wait for the replayer to reach a specific
   * point in logical time.
   */
  private static ConcurrentHashMap<Long, Object> waitTable;

  /**
   * Replay id of the thread that is currently allowed to execute memory
   * accesses.
   */
  private static long curThreadId;

  /**
   * Number of memory accesses that the thread currently allowed to execute
   * may execute in succession.
   */
  private static int curCount;

  /** Whether the trace file is finished. */
  private static boolean finished;

  /**
   * Initializes a global order replayer execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File inputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      inputStream = new DataInputStream(
          new BufferedInputStream(new FileInputStream(inputFile)));
      inputTraceFile = InputGoTraceFile.read(inputFile, inputStream);
      waitTable = new ConcurrentHashMap<Long, Object>();

      // read the first thread-count pair
      try {
        curThreadId = inputTraceFile.getNextThreadId();
        curCount = inputTraceFile.getNextCount();
        finished = false;
      } catch (EOFException eofe) {
        finished = true;
      }
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open trace file.");
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }


  ///
  /// Memory accesses replay
  ///

  /**
   * Handler executed before a memory access.
   * @param currentThread current thread
   */
  private static void beforeEvent(RVMThread currentThread) {
    if (finished) {
      ReplayManager.failAndExit(
          new TraceFileException("Trace file is too small."));
    }

    long replayId = currentThread.replayId;

    // if the current thread is the one allowed to execute, go ahead
    if (curThreadId == currentThread.replayId) {
      if (DEBUG && ReplayOptions.VERBOSITY > 0) {
        VM.sysWriteln("[GO Replayer] t#" + replayId + " going through "
                      + curThreadId + ", " + curCount);
      }
      return;
    }

    // optimization: many times, yielding once is enough to reach the target
    // state; the overhead of waiting on a monitor is saved
    Thread.yield();
    if (curThreadId == currentThread.replayId) {
      if (DEBUG && ReplayOptions.VERBOSITY > 0) {
        VM.sysWriteln("[GO Replayer] t#" + replayId
            + " going through (after yielding) "
            + curThreadId + ", " + curCount);
      }
      return;
    }

    // register the current thread as waiting
    synchronized (waitTable) {
      if (curThreadId == currentThread.replayId) {
        if (DEBUG && ReplayOptions.VERBOSITY > 0) {
          VM.sysWriteln("[GO Replayer] t#" + replayId
              + " going through (after table) "
              + curThreadId + ", " + curCount);
        }
        return;
      }
      waitTable.put(currentThread.replayId, currentThread);
    }

    synchronized (currentThread) {
      // wait on current thread until the target logical time is reached and
      // we're notified
      while (curThreadId != currentThread.replayId) {
        try {
          if (DEBUG && ReplayOptions.VERBOSITY > 0) {
            VM.sysWriteln("[GO Replayer] t#" + replayId + " waiting on "
                + curThreadId + ", " + curCount);
          }
          currentThread.wait();
        } catch (InterruptedException ie) {
          // go back to waiting...
        }
      }
    }

    if (DEBUG && ReplayOptions.VERBOSITY > 0) {
      VM.sysWriteln("[GO Replayer] t#" + replayId
          + " going through (after wait) " + curThreadId + ", " + curCount);
    }
  }

  /**
   * Handler executed after a memory access.
   */
  private static void afterEvent() {
    // if the current thread still has authorization to execute more memory
    // accesses, decrement that number by one
    if (curCount > 1) {
      curCount--;
      return;
    }

    try {
      // otherwise, the current thread's reign is over:
      // get the next thread-count pair
      long nextThreadId = inputTraceFile.getNextThreadId();
      int nextCount = inputTraceFile.getNextCount();

      // notify the next thread if it is waiting and update the replayer state
      synchronized (waitTable) {
        Object waitObj = waitTable.get(nextThreadId);
        if (waitObj == null) {
          curThreadId = nextThreadId;
          curCount = nextCount;
          if (DEBUG && ReplayOptions.VERBOSITY > 0) {
            VM.sysWriteln("[GO Replayer] t#"
                + RVMThread.getCurrentThread().replayId + " updating to "
                + curThreadId + ", " + curCount);
          }
        } else {
          synchronized (waitObj) {
            if (DEBUG && ReplayOptions.VERBOSITY > 0) {
              VM.sysWriteln("[GO Replayer] t#"
                  + RVMThread.getCurrentThread().replayId + " updating to "
                  + curThreadId + ", " + curCount + " and notifying.");
            }
            curThreadId = nextThreadId;
            curCount = nextCount;
            waitObj.notifyAll();
            waitTable.remove(nextThreadId);
          }
        }
      }
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    } catch (EOFException eofe) {
      finished = true;
    }
  }


  ///
  /// Thread lifetime wrappers
  ///

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      beforeEvent(parent);
    }
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
  }

  @Override
  public void threadAfterStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      afterEvent();
    }
  }

  @Override
  public void threadJoining(RVMThread joining, Thread joined) {
  }

  @Override
  public void threadTerminating(RVMThread t) {
  }
}
