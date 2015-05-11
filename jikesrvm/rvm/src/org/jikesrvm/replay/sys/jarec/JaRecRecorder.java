package org.jikesrvm.replay.sys.jarec;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayUtils;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.jarec.OutputJaRecTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * JaRec recorder.
 * <p> Traces memory accesses only.
 */
public final class JaRecRecorder implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final JaRecRecorder INSTANCE = new JaRecRecorder();

  /** Private constructor. */
  private JaRecRecorder() { }

  /** Trace file stream. */
  private static RandomAccessFile outputStream;

  /** JaRec output trace file. */
  private static OutputJaRecTraceFile outputTraceFile;

  /** Object used as a monitor to serialize thread start operations. */
  private static Object threadStartLock;

  /**
   * Maps the id of dead threads to their last logical clock.
   * The id used as the key is not the replay id, but the id returned by
   * {@link java.lang.Thread#getId}.
   */
  private static Map<Long, Long> deadThreadClocks;

  /**
   * Initializes a JaRec recorder execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File outputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      outputStream = new RandomAccessFile(outputFile, "rw");
      outputStream.setLength(0);
      outputTraceFile = new OutputJaRecTraceFile(outputFile, outputStream);
      outputTraceFile.init();
      threadStartLock = new Object();
      deadThreadClocks = Collections.synchronizedMap(new HashMap<Long, Long>());
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open trace file.");
    } catch (IOException ioe) {
      throw new ReplayException("Unable to write to trace file.");
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }

  /**
   * Terminates a JaRec recorder execution.
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
  /// Memory accesses tracing
  ///

  /**
   * Handler executed before a memory access.
   * @param thr current thread
   * @param obj object to which the target field of the memory access belongs
   */
  private static void beforeEvent(RVMThread thr, Object obj) {
    JaRecRecorderThreadState ts = thr.jaRecRecState;
    JaRecRecorderObjectState os = JaRecRecorderObjectState.getState(obj);

    // save the object state in the thread state to persist it until the
    // afterEvent handler is reached
    ts.currentObjectState = os;

    // lock the object state until the memory access is completed
    ObjectModel.genericLock(os);

    // compute the logical clock of this operation
    long newClock = ReplayUtils.max(ts.clock, os.clock) + 1;
    if (DEBUG && ReplayOptions.VERBOSITY > 0) {
      VM.sysWriteln("[JaRec Recorder] t#" + thr.replayId
          + " increasing to " + newClock + " on " + obj);
    }

    // update the logical clocks of both the current thread and the object
    os.clock = newClock;
    ts.clock = newClock;

    // trace the memory access
    try {
      outputTraceFile.traceClock(thr);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }
  }

  /**
   * Handler executed after a memory access.
   * @param thr current thread
   */
  private static void afterEvent(RVMThread thr) {
    JaRecRecorderObjectState os = thr.jaRecRecState.currentObjectState;
    ObjectModel.genericUnlock(os);
  }


  ///
  /// Thread lifetime handlers
  ///

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    if (!parent.isSystemThread()) {
      beforeEvent(parent, threadStartLock);
    }
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
    if (!child.isSystemThread()) {
      child.jaRecRecState = new JaRecRecorderThreadState();
      if (!parent.isSystemThread()) {
        // when a thread is started, its initial logical clock is that of
        // its parent at that moment
        child.jaRecRecState.clock = parent.jaRecRecState.clock;
      }
      outputTraceFile.initThreadTrace(child);
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
    if (!joining.isSystemThread()) {
      // if we have joined a thread, it must have already terminated. as a
      // result, {@link #deadThreadClocks} should contain its last logical clock
      if (VM.VerifyAssertions) {
        VM._assert(deadThreadClocks.containsKey(joined.getId()));
      }

      // when a thread joins another, its logical clock becomes the maximum
      // of its own and the last logical clock of the joined thread
      long joinedClock = deadThreadClocks.get(joined.getId());
      if (joinedClock > joining.jaRecRecState.clock) {
        joining.jaRecRecState.clock = joinedClock;
      }
    }
  }

  @Override
  public void threadTerminating(RVMThread t) {
    if (!t.isSystemThread()) {
      try {
        outputTraceFile.finishThreadTrace(t);
        Thread jt = t.getJavaLangThread();
        deadThreadClocks.put(jt.getId(), t.jaRecRecState.clock);
      } catch (TraceFileException tfe) {
        ReplayManager.failAndExit(tfe);
      }
    }
  }
}
