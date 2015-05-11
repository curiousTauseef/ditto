package org.jikesrvm.replay.sys.sync;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayUtils;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.sync.InputSyncTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.ThinLock;
import org.vmmagic.unboxed.Offset;

/**
 * Synchronization replayer.
 */
public final class SyncReplayer implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final SyncReplayer INSTANCE = new SyncReplayer();

  /** Private constructor. */
  private SyncReplayer() { }

  /** Trace file stream. */
  private static RandomAccessFile inputStream;

  /** Sync input trace file. */
  private static InputSyncTraceFile inputTraceFile;

  /** Object used as a monitor to serialize thread start operations. */
  private static Object threadStartLock;

  /**
   * Initializes a Sync replayer execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    if (ReplayOptions.SYNC_TRACE_FILE_NAME == null) {
      throw new ReplayException("Sync trace file unspecified");
    }
    try {
      File inputFile = new File(ReplayOptions.SYNC_TRACE_FILE_NAME);
      inputStream = new RandomAccessFile(inputFile, "r");
      inputTraceFile = InputSyncTraceFile.read(inputFile, inputStream);
      threadStartLock = new Object();
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open sync trace file", fnfe);
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }


  ///
  /// Synchronization replay
  ///

  /**
   * Wraps {@link org.jikesrvm.objectmodel.ObjectModel#genericLock} in calls to
   * {@link #beforeLock} and {@link #afterLock}.
   * @param obj only argument of
   *            {@link org.jikesrvm.objectmodel.ObjectModel#genericLock}
   *            (object whose monitor to enter)
   */
  public static void genericLock(Object obj) {
    beforeLock(obj);
    ObjectModel.genericLock(obj);
    afterLock(obj);
  }

  /**
   * Wraps {@link org.jikesrvm.scheduler.ThinLock#inlineLock} in calls to
   * {@link #beforeLock} and {@link #afterLock}.
   * @param obj        1st argument of
   *                   {@link org.jikesrvm.scheduler.ThinLock#inlineLock}
   *                   (object whose monitor to enter)
   * @param lockOffset 2nd argument of {@code inlineLock}
   */
  public static void inlineLock(Object obj, Offset lockOffset) {
    beforeLock(obj);
    ThinLock.inlineLock(obj, lockOffset);
    afterLock(obj);
  }

  /**
   * Handler executed before a thread enters the monitor of a given object.
   * @param obj the object
   */
  private static void beforeLock(Object obj) {
    RVMThread thr = RVMThread.getCurrentThread();
    SyncReplayerThreadState ts = thr.syncRepState;
    SyncTraceEvent evt = ts.nextEvent;
    try {
      // if the thread is currently in a free run, decrease it. if now empty,
      // read the next event for the thread from the trace file
      if (evt instanceof FreeRunTraceEvent) {
        FreeRunTraceEvent fre = (FreeRunTraceEvent)evt;
        fre.length--;
        if (fre.length <= 0) {
          ts.nextEvent = inputTraceFile.getNextEvent(thr);
        }
      }
      // otherwise, block until the object reaches the state specified by the
      // dependency event. read the next event from the trace file
      else {
        DependencyTraceEvent de = (DependencyTraceEvent)evt;
        SyncReplayerObjectState.getState(obj).waitForClock(de.clock);
        ts.nextEvent = inputTraceFile.getNextEvent(thr);
      }
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }
  }

  /**
   * Handler executed after a thread enters the monitor of a given object.
   * @param obj the object
   */
  private static void afterLock(Object obj) {
    RVMThread thr = RVMThread.getCurrentThread();
    SyncReplayerThreadState ts = thr.syncRepState;
    SyncReplayerObjectState os = SyncReplayerObjectState.getState(obj);

    // compute the logical clock of this operation and update the logical clocks
    // of both the thread and the object
    long newClock = ReplayUtils.max(ts.clock, os.clock) + 1;
    ts.clock = newClock;
    os.updateClock(newClock);
  }


  ///
  /// Thread lifetime handlers
  ///

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      beforeLock(threadStartLock);
      ObjectModel.genericLock(threadStartLock);
      afterLock(threadStartLock);
    }
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
    if (!child.isSystemThread()) {
      child.syncRepState = new SyncReplayerThreadState();
      if (!parent.isSystemThread()) {
        child.syncRepState.clock = parent.syncRepState.clock;
      }
      try {
        inputTraceFile.initThreadTrace(child);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
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
    // TODO: if buggy, maybe this is missing...
    /*if (!joining.isSystemThread() && !joined.isSystemThread()) {
      long joinedClock = joined.syncRepState.getClock();
      if (joinedClock > joining.syncRepState.getClock()) {
        joining.syncRepState.setClock(joinedClock);
      }
    }*/
  }

  @Override
  public void threadTerminating(RVMThread t) {
  }
}
