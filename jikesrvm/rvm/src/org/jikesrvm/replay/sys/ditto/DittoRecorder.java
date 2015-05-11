package org.jikesrvm.replay.sys.ditto;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayUtils;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.ditto.OutputDittoTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.ThinLock;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Offset;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * Ditto recorder.
 * <p> Traces both memory accesses and synchronization operations.
 */
public final class DittoRecorder implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final DittoRecorder INSTANCE = new DittoRecorder();

  /** Private constructor. */
  private DittoRecorder() { }

  /** Trace file stream. */
  private static RandomAccessFile outputStream;

  /** Ditto output trace file. */
  private static OutputDittoTraceFile outputTraceFile;

  /** Object used as a monitor to serialize thread start operations. */
  private static Object threadStartLock;

  /**
   * Initializes a Ditto recorder execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File outputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      outputStream = new RandomAccessFile(outputFile, "rw");
      outputStream.setLength(0);
      outputTraceFile = new OutputDittoTraceFile(outputFile, outputStream);
      outputTraceFile.init();
      threadStartLock = new Object();
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open trace file");
    } catch (IOException ioe) {
      throw new ReplayException("Unable to write to trace file", ioe);
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }

  /**
   * Terminates a Ditto recorder execution.
   * @throws ReplayException if termination fails
   */
  public static void terminate() throws ReplayException {
    try {
      outputTraceFile.finish();
      outputStream.close();
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    } catch (IOException ioe) {
      throw new ReplayException(ioe);
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
  @Inline
  private static void afterLock(Object obj) {
    RVMThread t = RVMThread.getCurrentThread();
    DittoRecorderThreadState ts = t.dittoRecState;
    DittoRecorderObjectState os = obj instanceof Class<?>
        ? DittoRecorderObjectState.getStaticState((Class<?>)obj)
        : DittoRecorderObjectState.getInstanceState(obj);

    // compute the synchronization logical clock of this operation
    long newClock = ReplayUtils.max(ts.syncClock, os.syncClock) + 1;

    // if the last thread to perform a synchonization operation on this
    // object was the current thread or no thread has yet executed such an
    // operation on this object, no synchronization is needed to replay
    // => increase the current free run
    if (os.syncLastThread == t) {
      ts.currentFreeRun++;
    } else if (os.syncLastThread == null) {
      ts.currentFreeRun++;
      os.syncLastThread = t;
    }
    // otherwise, a synchronization event must be traced
    else {
      SyncEvent se = ts.syncEvent;
      se.freeRun = ts.currentFreeRun;
      se.clock = os.syncClock;

      if (DEBUG && ReplayOptions.VERBOSITY > 2) {
        VM.sysWriteln("[Ditto Recorder] t#" + t.replayId + " tracing " + se
                      + " and updating to " + newClock);
      }

      try {
        outputTraceFile.traceEvent(ts.outputDataChunk, se);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
      }

      // the free run has been reset
      ts.currentFreeRun = 0;
      os.syncLastThread = t;
    }

    // update the synchronization logical clocks of both the object and the
    // thread
    os.syncClock = newClock;
    ts.syncClock = newClock;
  }


  ///
  /// Memory accesses tracing
  ///

  /**
   * Handler executed before a memory load is performed on a static field.
   * @param ts     current thread state
   * @param typeId id of the type to which the static field belongs
   * @param fId    field id
   */
  @Entrypoint
  public static void beforeStaticLoad(DittoRecorderThreadState ts,
                                      int typeId, int fId) {
    RVMType type = TypeReference.getTypeRef(typeId).peekType();
    Class<?> klass = type.getClassForType();
    DittoRecorderObjectState.FieldState fs =
        DittoRecorderObjectState.getStaticState(klass, type).fieldStates[fId];
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
  public static void beforeInstanceLoad(DittoRecorderThreadState ts,
                                        Object obj, int fId) {
    DittoRecorderObjectState.FieldState fs =
        DittoRecorderObjectState.getInstanceState(obj).fieldStates[fId];
    beforeLoad(ts, fs);
  }

  /**
   * Handler executed before a memory load is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   * @param fs field state
   */
  @Inline
  private static void beforeLoad(DittoRecorderThreadState ts,
                                 DittoRecorderObjectState.FieldState fs) {
    // save the field state in the thread state to persist it until the
    // afterLoad method is reached
    ts.currentFieldState = fs;

    // lock the field state so that the before->load->after chain is executed
    // in a critical section
    ObjectModel.genericLock(fs);

    // get the last thread to have executed a memory store on the field
    DittoRecorderThreadState lt = fs.lastThread;

    // if the last memory store on the field was executed by the current thread
    // or no memory store has yet been executed on the field, no synchronization
    // is needed to replay => increase the current free run
    if (lt == ts) {
      ts.currentFreeRun++;
    } else if (lt == null) {
      ts.currentFreeRun++;
      fs.loadedByOtherThreads = true;
    } else {
      // get the last interaction between the current thread and the last
      // thread to have executed a memory store on the field
      ThreadInteraction ti = ts.interactions.getInteractionWith(lt.replayId);

      // if the last interaction occurred at an higher logical time than the
      // last memory store on the field => increase the current free run
      if (ti.theirClock >= fs.storeClock) {
        ts.currentFreeRun++;
        fs.loadedByOtherThreads = true;
      }
      // otherwise, a load event must be traced
      else {
        LoadEvent le = ts.loadEvent;
        le.freeRun = ts.currentFreeRun;
        le.storeClock = fs.storeClock;
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Recorder] t#" + ts.replayId + " tracing " + le
                        + " and updating to (" + fs.storeClock
                        + ",*" + (fs.loadCount + 1) + "*)");
        }

        try {
          outputTraceFile.traceEvent(ts.outputDataChunk, le);
        } catch (TraceFileException tfe) {
          ReplayManager.failAndExit(tfe);
        }

        // the free run has been reset, and the field has now been loaded by
        // a thread other than the one that executed its last store
        ts.currentFreeRun = 0;
        fs.loadedByOtherThreads = true;

        // register this interaction between the current thread and the last
        // thread to have stored a value in the field
        ts.interactions.registerInteraction(lt.replayId, le.storeClock);
      }
    }

    // Update the field's load count and the thread's logical clock
    fs.loadCount++;
    if (fs.storeClock > ts.clock) {
      ts.clock = fs.storeClock;
    }
  }

  /**
   * Handler executed after a memory load is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   */
  @Entrypoint
  public static void afterLoad(DittoRecorderThreadState ts) {
    DittoRecorderObjectState.FieldState fs = ts.currentFieldState;
    ObjectModel.genericUnlock(fs);
  }

  /**
   * Handler executed before a memory store is performed on a static field.
   * @param ts     current thread state
   * @param typeId id of the type to which the static field belongs
   * @param fId    field id
   */
  @Entrypoint
  public static void beforeStaticStore(DittoRecorderThreadState ts,
                                       int typeId, int fId) {
    RVMType type = TypeReference.getTypeRef(typeId).peekType();
    Class<?> klass = type.getClassForType();
    DittoRecorderObjectState.FieldState fs =
        DittoRecorderObjectState.getStaticState(klass, type).fieldStates[fId];
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
  public static void beforeInstanceStore(DittoRecorderThreadState ts,
                                         Object obj, int fId) {
    DittoRecorderObjectState.FieldState fs =
        DittoRecorderObjectState.getInstanceState(obj).fieldStates[fId];
    beforeStore(ts, fs);
  }

  /**
   * Handler executed before a memory store is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   * @param fs field state
   */
  @Inline
  private static void beforeStore(DittoRecorderThreadState ts,
                                  DittoRecorderObjectState.FieldState fs) {
    // save the field state in the thread state to persist it until the
    // afterStore handler is reached
    ts.currentFieldState = fs;

    // lock the field state so that the before->store->after chain is executed
    // in a critical section
    ObjectModel.genericLock(fs);

    // get the last thread to have executed a memory store on the field
    DittoRecorderThreadState lt = fs.lastThread;

    // compute the memory store logical clock of this operation
    long newClock = ReplayUtils.max(ts.clock, fs.storeClock) + 1;

    // if the last memory store on the field was executed by the current thread
    // or no memory store has yet been executed on the field, and no memory
    // loads by other threads have been executed since, no synchronization is
    // needed to replay => increase the current free run
    if (lt == ts && !fs.loadedByOtherThreads) {
      ts.currentFreeRun++;
    } else if (lt == null && !fs.loadedByOtherThreads) {
      ts.currentFreeRun++;
      fs.lastThread = ts;
    }
    // otherwise, a store event must be traced
    else {
      StoreEvent se = ts.storeEvent;
      se.storeClock = fs.storeClock;
      se.loadCount = fs.loadCount;
      se.freeRun = ts.currentFreeRun;
      if (DEBUG && ReplayOptions.VERBOSITY > 2) {
        VM.sysWriteln("[Ditto Recorder] t#" + ts.replayId + " tracing " + se
                      + " and updating to (*" + newClock + "*,0)");
      }

      try {
        outputTraceFile.traceEvent(ts.outputDataChunk, se);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
      }

      // the free run has been reset
      ts.currentFreeRun = 0;
      fs.lastThread = ts;

      // register this interaction between the current thread and the last
      // thread to have stored a value in the field
      if (lt != null) {
        ts.interactions.registerInteraction(lt.replayId, se.storeClock);
      }
    }

    // update the logical clocks of the field and the thread
    fs.storeClock = newClock;
    ts.clock = newClock;
    // reset the load-related field state
    fs.loadCount = 0;
    fs.loadedByOtherThreads = false;
  }

  /**
   * Handler executed after a memory store is performed on either a static,
   * instance or array field.
   * @param ts current thread state
   */
  @Entrypoint
  public static void afterStore(DittoRecorderThreadState ts) {
    DittoRecorderObjectState.FieldState fs = ts.currentFieldState;
    ObjectModel.genericUnlock(fs);
  }


  ///
  /// Thread lifetime handlers
  ///

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      // enter a critical section until the before->id->start->after chain is
      // completed
      ObjectModel.genericLock(threadStartLock);
      // trace this lock so that the replayer may start the threads in the
      // original order
      afterLock(threadStartLock);
    }
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
    if (!child.isSystemThread()) {
      // create a thread state for the child thread
      child.dittoRecState = new DittoRecorderThreadState(child.replayId);

      // introduce the child thread to the trace file
      outputTraceFile.initThreadTrace(child);
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
        outputTraceFile.finishThreadTrace(t);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
      }
    }
  }
}
