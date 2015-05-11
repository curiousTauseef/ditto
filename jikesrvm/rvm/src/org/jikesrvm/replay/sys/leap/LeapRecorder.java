package org.jikesrvm.replay.sys.leap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.leap.OutputLeapDataChunk;
import org.jikesrvm.replay.tracefile.leap.OutputLeapTraceFile;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Entrypoint;

/**
 * LEAP recorder.
 * <p> Traces memory accesses only.
 */
public final class LeapRecorder {

  /** Hidden constructor. */
  private LeapRecorder() { }

  /** Trace file stream. */
  private static RandomAccessFile outputStream;

  /** LEAP output trace file. */
  private static OutputLeapTraceFile outputTraceFile;

  /**
   * Initializes a LEAP recorder execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File outputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      outputStream = new RandomAccessFile(outputFile, "rw");
      outputStream.setLength(0);
      outputTraceFile = new OutputLeapTraceFile(outputFile, outputStream);
      outputTraceFile.init();
    } catch (FileNotFoundException fnfe) {
      throw new ReplayException("Unable to open trace file.");
    } catch (IOException ioe) {
      throw new ReplayException("Unable to write to trace file.");
    } catch (TraceFileException tfe) {
      throw new ReplayException(tfe);
    }
  }

  /**
   * Terminates a LEAP recorder execution.
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
   * @param thr     current thread
   * @param fieldId field id
   */
  @Entrypoint
  public static void beforeEvent(RVMThread thr, int fieldId) {
    // get the trace for the field
    OutputLeapDataChunk ft = outputTraceFile.fieldTraces[fieldId];

    // lock the field trace until the memory access is finished
    ObjectModel.genericLock(ft);

    // trace the memory access
    try {
      outputTraceFile.traceAccess(ft, thr.replayId);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }
  }

  /**
   * Handler executed after a memory access.
   * @param thr     current thread
   * @param fieldId field id
   */
  @Entrypoint
  public static void afterEvent(RVMThread thr, int fieldId) {
    OutputLeapDataChunk ft = outputTraceFile.fieldTraces[fieldId];
    ObjectModel.genericUnlock(ft);
  }
}
