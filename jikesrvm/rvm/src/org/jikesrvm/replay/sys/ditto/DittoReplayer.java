package org.jikesrvm.replay.sys.ditto;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayUtils;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.ditto.InputDittoTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.ThinLock;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Offset;

/**
 * Ditto replayer.
 */
public final class DittoReplayer implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final DittoReplayer INSTANCE = new DittoReplayer();

  /** Private constructor. */
  private DittoReplayer() { }

  /** Trace file stream. */
  private static RandomAccessFile inputStream;

  /** Ditto input trace file. */
  private static InputDittoTraceFile inputTraceFile;

  /** Object used as a monitor to serialize thread start operations. */
  private static Object threadStartLock;

  /**
   * Initializes a Ditto replayer execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File inputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      inputStream = new RandomAccessFile(inputFile, "r");
      inputTraceFile = InputDittoTraceFile.read(inputFile, inputStream);
      threadStartLock = new Object();
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open trace file.");
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }


  ///
  /// Synchronization replay
  ///

  /**
   * Wraps {@link org.jikesrvm.objectmodel.ObjectModel#genericLock} in calls
   * to {@link #beforeLock} and {@link #afterLock}.
   * @param obj only argument of
   *            {@link org.jikesrvm.objectmodel.ObjectModel#genericLock}
   *            (object whose monitor to enter)
   */
  public static void genericLock(Object obj) {
    RVMThread t = RVMThread.getCurrentThread();
    DittoReplayerThreadState ts = t.dittoRepState;
    DittoReplayerObjectState os = obj instanceof Class<?>
        ? DittoReplayerObjectState.getStaticState((Class<?>)obj)
        : DittoReplayerObjectState.getInstanceState(obj);

    beforeLock(ts, os);
    ObjectModel.genericLock(obj);
    afterLock(ts, os);
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
    RVMThread t = RVMThread.getCurrentThread();
    DittoReplayerThreadState ts = t.dittoRepState;
    DittoReplayerObjectState os = obj instanceof Class<?>
        ? DittoReplayerObjectState.getStaticState((Class<?>)obj)
        : DittoReplayerObjectState.getInstanceState(obj);

    beforeLock(ts, os);
    ThinLock.inlineLock(obj, lockOffset);
    afterLock(ts, os);
  }

  /**
   * Handler executed before a thread enters the monitor of a given object.
   * @param ts state of the thread entering the monitor
   * @param os state of the object whose monitor is to be entered
   */
  @Inline
  private static void beforeLock(DittoReplayerThreadState ts,
                                 DittoReplayerObjectState os) {
    // if the thread is in a free run, no synchronization is required
    if (ts.currentFreeRun > 0) {
      return;
    }

    // get a synchronization event from the trace file
    SyncEvent se = ts.syncEvent;
    try {
      inputTraceFile.getNextSyncEvent(ts.inputDataChunk, se);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }

    // if the event starts with a free run, no synchronization is required
    if (se.freeRun > 0) {
      ts.currentFreeRun = se.freeRun;
    }
    // otherwise, wait until the object reaches the required state
    else {
      os.waitBeforeSync(se.clock, ts.syncKey);
    }
  }

  /**
   * Handler executed after a thread enters the monitor of a given object.
   * @param ts state of the thread that entered the monitor
   * @param os state of the object whose monitor was entered
   */
  @Inline
  private static void afterLock(DittoReplayerThreadState ts,
                                DittoReplayerObjectState os) {
    // if the thread is in a free run, decrease its size by one, due to the
    // just executed synchronization operation
    if (ts.currentFreeRun > 0) {
      ts.currentFreeRun--;
    }

    // compute the synchronization logical clock of this operation
    long newClock = ReplayUtils.max(ts.syncClock, os.syncClock) + 1;

    // update the synchronization logical clock of the thread
    ts.syncClock = newClock;

    // update the synchronization logical clock of the object, possibly
    // notifying threads waiting for it
    os.registerSync(newClock, ts.syncKey);
  }


  ///
  /// Memory accesses replay
  ///

  /**
   * Handler executed before a memory load is performed on a static field.
   * @param ts     current thread state
   * @param typeId id of the type to which the static field belongs
   * @param fId    field id
   */
  @Entrypoint
  public static void beforeStaticLoad(DittoReplayerThreadState ts,
                                      int typeId, int fId) {
    RVMType type = TypeReference.getTypeRef(typeId).peekType();
    Class<?> klass = type.getClassForType();
    DittoReplayerObjectState.FieldState fs =
        DittoReplayerObjectState.getStaticState(klass, type).fieldStates[fId];
    beforeLoad(ts, fs);
  }

  /**
   * Handler executed before a memory load is performed on an instance or array
   * field.
   * @param ts  current thread state
   * @param obj object/array to which the instance/array field belongs
   * @param fId field id
   */
  @Entrypoint
  public static void beforeInstanceLoad(DittoReplayerThreadState ts,
                                        Object obj, int fId) {
    DittoReplayerObjectState.FieldState fs =
        DittoReplayerObjectState.getInstanceState(obj).fieldStates[fId];
    beforeLoad(ts, fs);
  }

  /**
   * Handler executed before a memory load is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   * @param fs field state
   */
  @Inline
  private static void beforeLoad(DittoReplayerThreadState ts,
                                 DittoReplayerObjectState.FieldState fs) {
    // save the field state in the thread state to persist it until the
    // afterLoad method is reached
    ts.currentFieldState = fs;

    // if the thread is in a free run, no synchonization is required
    if (ts.currentFreeRun > 0) {
      return;
    }

    // get a load event from the trace file
    LoadEvent le = ts.loadEvent;
    try {
      inputTraceFile.getNextLoadEvent(ts.inputDataChunk, le);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }

    // if the event starts with a free run, no synchronization is required
    if (le.freeRun > 0) {
      ts.currentFreeRun = le.freeRun;
    }
    // otherwise, wait until the field reaches the required state
    else {
      fs.waitBeforeLoad(le.storeClock, ts.loadKey);
    }
  }

  /**
   * Handler executed after a memory load is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   */
  @Entrypoint
  public static void afterLoad(DittoReplayerThreadState ts) {
    // if the thread is in a free run, decrease its size by one, due to the
    // just executed memory access operation
    if (ts.currentFreeRun > 0) {
      ts.currentFreeRun--;
    }

    // update the logical clock of the thread
    DittoReplayerObjectState.FieldState fs = ts.currentFieldState;
    if (fs.storeClock > ts.clock) {
      ts.clock = fs.storeClock;
    }

    // update the field state, possibly notifying threads waiting for it
    fs.registerLoad(ts.storeKey);
  }

  /**
   * Handler executed before a memory store is performed on a static field.
   * @param ts     current thread state
   * @param typeId id of the type to which the static field belongs
   * @param fId    field id
   */
  @Entrypoint
  public static void beforeStaticStore(DittoReplayerThreadState ts,
                                       int typeId, int fId) {
    RVMType type = TypeReference.getTypeRef(typeId).peekType();
    Class<?> klass = type.getClassForType();
    DittoReplayerObjectState.FieldState fs =
        DittoReplayerObjectState.getStaticState(klass, type).fieldStates[fId];
    beforeStore(ts, fs);
  }

  /**
   * Handler executed before a memory store is performed on an instance or
   * array field.
   * @param ts  current thread state
   * @param obj object/array to which the instance/array field belongs
   * @param fId field id
   */
  @Entrypoint
  public static void beforeInstanceStore(DittoReplayerThreadState ts,
                                         Object obj, int fId) {
    DittoReplayerObjectState.FieldState fs =
        DittoReplayerObjectState.getInstanceState(obj).fieldStates[fId];
    beforeStore(ts, fs);
  }

  /**
   * Handler executed before a memory store is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   * @param fs field state
   */
  @Inline
  private static void beforeStore(DittoReplayerThreadState ts,
                                  DittoReplayerObjectState.FieldState fs) {
    // save the field state in the thread state to persist it until the
    // afterStore method is reached
    ts.currentFieldState = fs;

    // if the thread is in a free run, no sychronization is required
    if (ts.currentFreeRun > 0) {
      return;
    }

    // get a store event from the trace field
    StoreEvent se = ts.storeEvent;
    try {
      inputTraceFile.getNextStoreEvent(ts.inputDataChunk, se);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }

    // if the event starts with a free run, no synchronization is required
    if (se.freeRun > 0) {
      ts.currentFreeRun = se.freeRun;
    }
    // otherwise, wait until the field reaches the required state
    else {
      fs.waitBeforeStore(se.storeClock, se.loadCount, ts.storeKey);
    }
  }

  /**
   * Handler executed after a memory store is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   */
  @Entrypoint
  public static void afterStore(DittoReplayerThreadState ts) {
    // if the thread is in a free run, decrease its size by one, due to the
    // just executed memory access operation
    if (ts.currentFreeRun > 0) {
      ts.currentFreeRun--;
    }

    DittoReplayerObjectState.FieldState fs = ts.currentFieldState;
    // compute the memory store logical clock of this operation
    long newClock = ReplayUtils.max(ts.clock, fs.storeClock) + 1;

    // update the logical clock of the thread
    ts.clock = newClock;

    // update the memory store logical clock of the field, possibly notifying
    // threads waiting for it
    fs.registerStore(newClock, ts.loadKey, ts.storeKey);
  }


  ///
  /// Thread lifetime handlers
  ///

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      // enter a critical section until the before->id->start->after chain is
      // completed
      genericLock(threadStartLock);
    }
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
    if (!child.isSystemThread()) {
      // create a thread state for the child thread
      child.dittoRepState = new DittoReplayerThreadState();

      // initialize the thread's trace file
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
  }

  @Override
  public void threadTerminating(RVMThread t) {
  }
}
