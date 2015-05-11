package org.jikesrvm.replay.sys.global;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.global.OutputGoTraceFile;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Global order recorder.
 * <p> Traces memory accesses only.
 */
public final class GoRecorder implements ThreadLifetimeHandlers {

  /** Singleton instance. */
  public static final GoRecorder INSTANCE = new GoRecorder();

  /** Private constructor. */
  private GoRecorder() { }

  /** Trace file stream. */
  private static DataOutputStream outputStream;

  /** Global order output trace file. */
  private static OutputGoTraceFile outputTraceFile;

  /** The global lock used to serialize all traced events. */
  private static Object globalLock;

  /**
   * Initializes a global order recorder execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File outputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      outputStream = new DataOutputStream(
          new BufferedOutputStream(new FileOutputStream(outputFile)));
      outputTraceFile = new OutputGoTraceFile(outputFile, outputStream);
      outputTraceFile.init();
      globalLock = new Object();
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open trace file.");
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }

  /**
   * Terminates a global order recorder execution.
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
   */
  private static void beforeEvent(RVMThread thr) {
    ObjectModel.genericLock(globalLock);
    try {
      outputTraceFile.traceEvent(thr.replayId);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }
  }

  /** Handler executed after a memory access. */
  private static void afterEvent() {
    ObjectModel.genericUnlock(globalLock);
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
