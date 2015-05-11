package org.jikesrvm.replay.sys.jarec;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import org.jikesrvm.VM;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.jarec.InputJaRecTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * JaRec replayer.
 */
public final class JaRecReplayer implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final JaRecReplayer INSTANCE = new JaRecReplayer();

  /** Private constructor. */
  private JaRecReplayer() { }

  /** Trace file stream. */
  private static RandomAccessFile inputStream;

  /** JaRec input trace file. */
  private static InputJaRecTraceFile inputTraceFile;

  /**
   * Minimum logical clock of all the threads.
   */
  private static long minClock;

  /**
   * Table in which threads may wait for the replayer to reach a specific
   * point in logical time.
   */
  private static Map<ClockKey, Object> waitTable;

  /**
   * Ordered queue containing the logical clocks of the next memory accesses
   * each thread wants to perform, used to calculate the next value for
   * {@link #minClock}.
   */
  private static Queue<Long> clockQueue;

  /**
   * Initializes an execution of the JaRec replayer.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File inputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      inputStream = new RandomAccessFile(inputFile, "r");
      inputTraceFile = InputJaRecTraceFile.read(inputFile, inputStream);
      minClock = ReplayConstants.INIT_CLOCK + 1;
      waitTable = new HashMap<ClockKey, Object>();
      clockQueue = new PriorityQueue<Long>();
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
   * @param thr current thread
   */
  private static void beforeEvent(RVMThread thr) {
    JaRecReplayerThreadState ts = thr.jaRecRepState;
    long clock = ts.clock;

    // if the current thread's logical clock is at or past the minimum clock,
    // it may execute its memory access operation
    if (minClock >= clock) {
      if (DEBUG && ReplayOptions.VERBOSITY > 0) {
        VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId + "going through"
            + minClock + " with " + clock);
      }
      return;
    }

    // optimization: many times, yielding once is enough to reach the target
    // state; the overhead of waiting on a monitor is saved
    Thread.yield();
    if (minClock >= clock) {
      if (DEBUG && ReplayOptions.VERBOSITY > 0) {
        VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
            + "going through (after yielding)" + minClock + " with " + clock);
      }
      return;
    }

    // create a clock key representing the target state of the replayer.
    // then, use it to get a monitor on which to wait for it
    ClockKey key = ts.key;
    key.clock = clock;
    Object waitObj;
    synchronized (waitTable) {
      // check again if waiting is required
      if (minClock >= clock) {
        if (DEBUG && ReplayOptions.VERBOSITY > 0) {
          VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
              + "going through (after table)" + minClock + " with " + clock);
        }
        return;
      }

      waitObj = waitTable.get(key);
      if (waitObj == null) {
        waitObj = key;
        waitTable.put(key, key);
      }
    }

    synchronized (waitObj) {
      // wait on the object's monitor until the target state is reached
      while (minClock < clock) {
        try {
          if (DEBUG && ReplayOptions.VERBOSITY > 0) {
            VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
                + " waiting for " + clock);
          }
          waitObj.wait();
        } catch (InterruptedException ie) {
          // go back to waiting...
        }
      }
    }
    if (DEBUG && ReplayOptions.VERBOSITY > 0) {
      VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
          + "going through (after waiting)" + minClock + " with " + clock);
    }
  }

  /**
   * Handler executed after a memory access.
   * @param thr current thread
   */
  private static void afterEvent(RVMThread thr) {
    JaRecReplayerThreadState ts = thr.jaRecRepState;

    // get the thread's next logical clock from its trace
    long nextClock;
    try {
      nextClock = inputTraceFile.getNextClock(thr);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
      return;
    }
    ts.clock = nextClock;

    // calculate the new minimum thread clock in the system
    Long newMinClock;
    synchronized (clockQueue) {
      if (DEBUG && ReplayOptions.VERBOSITY > 0) {
        VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
            + " putting " + nextClock + " in the queue.");
      }
      if (nextClock != ReplayConstants.NULL_CLOCK) {
        clockQueue.offer(nextClock);
      }
      newMinClock = clockQueue.poll();
    }
    if (DEBUG && ReplayOptions.VERBOSITY > 0) {
      VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
          + " took " + newMinClock + " from queue.");
    }

    // update the minimum thread clock if different from the current
    // and notify any threads waiting for it
    if (newMinClock != null && newMinClock > minClock) {
      Object waitObj;
      ClockKey key = ts.key;
      key.clock = newMinClock;
      synchronized (waitTable) {
        if (newMinClock > minClock) {
          waitObj = waitTable.get(key);
          if (waitObj != null) {
            if (DEBUG && ReplayOptions.VERBOSITY > 0) {
              VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
                  + " updating minClock to " + newMinClock + " and notifying.");
            }
            synchronized (waitObj) {
              minClock = newMinClock;
              waitObj.notifyAll();
              waitTable.remove(key);
            }
          } else {
            minClock = newMinClock;
            if (DEBUG && ReplayOptions.VERBOSITY > 0) {
              VM.sysWriteln("[JaRec Replayer] t#" + thr.replayId
                  + " updating minClock to " + newMinClock);
            }
          }
        }
      }
    }
  }


  ///
  /// Thread lifetime handlers
  ///

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      beforeEvent(parent);
    }
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
    if (!child.isSystemThread()) {
      JaRecReplayerThreadState ts = new JaRecReplayerThreadState();
      child.jaRecRepState = ts;

      // get the thread's first logical clock
      try {
        inputTraceFile.initThreadTrace(child);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
      }

      // add the clock to the clock queue
      if (ts.clock > ReplayConstants.INIT_CLOCK + 1) {
        clockQueue.add(ts.clock);
      }
    }
  }

  @Override
  public void threadAfterStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      afterEvent(parent);
    }
  }

  @Override
  public void threadJoining(RVMThread joining, Thread joined) {
  }

  @Override
  public void threadTerminating(RVMThread t) {
  }
}
