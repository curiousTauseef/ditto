package org.jikesrvm.replay.sys.sync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.sync.OutputSyncTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.ThinLock;
import org.vmmagic.unboxed.Offset;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * Synchronization recorder.
 * <p> Traces synchronization operations only.
 */
public final class SyncRecorder implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final SyncRecorder INSTANCE = new SyncRecorder();

  /** Private constructor. */
  private SyncRecorder() { }

  /** Trace file stream. */
  private static RandomAccessFile outputStream;

  /** Sync output trace file. */
  private static OutputSyncTraceFile outputTraceFile;

  /** Object used as a monitor to serialize thread start operations. */
  private static Object threadStartLock;

  /**
   * Initializes a Sync recorder execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    if (ReplayOptions.SYNC_TRACE_FILE_NAME == null) {
      throw new ReplayException("Sync trace file unspecified");
    }
    try {
      File outputFile = new File(ReplayOptions.SYNC_TRACE_FILE_NAME);
      outputStream = new RandomAccessFile(outputFile, "rw");
      outputStream.setLength(0);
      outputTraceFile = new OutputSyncTraceFile(outputFile, outputStream);
      outputTraceFile.init();
      threadStartLock = new Object();
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open sync trace file");
    } catch (IOException ioe) {
      throw new ReplayException("Unable to write to sync trace file", ioe);
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }

  /**
   * Terminates a Sync recorder execution.
   * @throws ReplayException if termination fails
   */
  public static void terminate() throws ReplayException {
    try {
      outputTraceFile.finish();
      outputStream.close();
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    } catch (IOException ioe) {
    }
  }


  ///
  /// Synchronization tracing
  ///

  /**
   * Wraps {@link org.jikesrvm.objectmodel.ObjectModel#genericLock}, so that
   * {@link #afterLock} is called afterwards.
   * @param obj only argument of
   *            {@link org.jikesrvm.objectmodel.ObjectModel#genericLock}
   *            (object whose monitor to enter)
   */
  public static void genericLock(Object obj) {
    ObjectModel.genericLock(obj);
    afterLock(obj);
  }

  /**
   * Wraps {@link org.jikesrvm.scheduler.ThinLock#inlineLock}, so that
   * {@link #afterLock} is called afterwards.
   * @param obj        1st argument of
   *                   {@link org.jikesrvm.scheduler.ThinLock#inlineLock}
   *                   (object whose monitor to enter)
   * @param lockOffset 2nd argument of {@code inlineLock}
   */
  public static void inlineLock(Object obj, Offset lockOffset) {
    ThinLock.inlineLock(obj, lockOffset);
    afterLock(obj);
  }

  /**
   * Handler executed after the current thread enters the monitor of a given
   * object.
   * @param obj the object whose monitor was entered
   */
  private static void afterLock(Object obj) {
    RVMThread thr = RVMThread.getCurrentThread();
    if (!thr.isSystemThread()) {
      SyncRecorderThreadState ts = thr.syncRecState;
      SyncRecorderObjectState os = SyncRecorderObjectState.getState(obj);

      // compute the logical clock of this operation
      long tClock = ts.clock;
      long oClock = os.clock;
      long newClock = (tClock > oClock ? tClock : oClock) + 1;

      // update the logical clocks of both the object and the thread
      os.clock = newClock;
      ts.clock = newClock;

      // A dependency is traced only if:
      //   (a) the last thread to synchronize on the object is not the current
      //       thread;
      //   (b) the last known clock of the last thread to synchronize on the
      //       object is lower then the clock of the object; and
      //   (c) the object has been synchronized on before.
      boolean isIndependent = true;
      RVMThread lastThread = os.lastThread;
      if (lastThread != null && lastThread.replayId != thr.replayId) {
        long lastItrClock = ts.getLastInteractionWith(lastThread.replayId);
        if (lastItrClock < oClock && oClock > ReplayConstants.INIT_CLOCK) {
          // trace the current free run and this dependency
          try {
            FreeRunTraceEvent currentFreeRun = ts.currentFreeRun;
            if (currentFreeRun != null) {
              if (DEBUG && ReplayOptions.VERBOSITY > 0) {
                VM.sysWriteln("[Sync Recorder] t#" + thr.replayId
                    + " tracing " + currentFreeRun);
              }
              outputTraceFile.traceEvent(thr, currentFreeRun);
              ts.currentFreeRun = null;
            }
            DependencyTraceEvent e = new DependencyTraceEvent(oClock);
            if (DEBUG && ReplayOptions.VERBOSITY > 0) {
              VM.sysWriteln("[Sync Recorder] t#" + thr.replayId
                  + " tracing " + e);
            }
              outputTraceFile.traceEvent(thr, e);
          } catch (TraceFileException tfe) {
            ReplayManager.failAndExit(tfe);
          }
          isIndependent = false;
        }
        /* As long as the last thread to synchronize on the object is not the
         * current thread, these must be updated, even if the dependence is
         * not traced for other reasons. */
        os.lastThread = thr;
        ts.registerInteraction(lastThread, oClock);
      } else if (lastThread == null) {
        os.lastThread = thr;
      }

      // if no dependency was traced, increase the current free run
      if (isIndependent) {
        FreeRunTraceEvent currentFreeRun = ts.currentFreeRun;
        if (currentFreeRun == null) {
          currentFreeRun = new FreeRunTraceEvent(0);
          ts.currentFreeRun = currentFreeRun;
        }
        currentFreeRun.length++;
      }
    }
  }


  ///
  /// Thread lifetime handlers
  ///

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      ObjectModel.genericLock(threadStartLock);
      afterLock(threadStartLock);
    }
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
    if (!child.isSystemThread()) {
      child.syncRecState = new SyncRecorderThreadState(child);
      if (!parent.isSystemThread()) {
        // initialize the child thread's clock with that of the parent at
        // the moment it started the child
        long pClock = parent.syncRecState.clock;
        child.syncRecState.clock = pClock;
        child.syncRecState.registerInteraction(parent, pClock);
      }
    }
  }

  @Override
  public void threadAfterStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      ObjectModel.genericUnlock(threadStartLock);
    }
  }

  @Override
  public void threadJoining(RVMThread joining, Thread joined) {
  }

  @Override
  public void threadTerminating(RVMThread t) {
    if (!t.isSystemThread()) {
      try {
        FreeRunTraceEvent currentFreeRun = t.syncRecState.currentFreeRun;
        if (currentFreeRun != null) {
          if (DEBUG && ReplayOptions.VERBOSITY > 0) {
            VM.sysWriteln("[Sync Recorder] t#" + t.replayId
                + " tracing " + currentFreeRun);
          }
          outputTraceFile.traceEvent(t, currentFreeRun);
        }
        outputTraceFile.finishThreadTrace(t);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
      }
    }
  }
}
