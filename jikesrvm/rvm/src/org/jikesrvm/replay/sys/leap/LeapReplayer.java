package org.jikesrvm.replay.sys.leap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.replay.tracefile.leap.InputLeapTraceFile;
import org.jikesrvm.scheduler.RVMThread;

/**
 * LEAP replayer.
 */
public final class LeapReplayer {

  /** Hidden constructor. */
  private LeapReplayer() { }

  /** Trace file stream. */
  private static RandomAccessFile inputStream;

  /** LEAP input trace file. */
  private static InputLeapTraceFile inputTraceFile;

  /**
   * Initializes a LEAP replayer execution.
   * @throws ReplayException if initialization fails
   */
  public static void init() throws ReplayException {
    try {
      File inputFile = new File(ReplayOptions.TRACE_FILE_NAME);
      inputStream = new RandomAccessFile(inputFile, "r");
      inputTraceFile = InputLeapTraceFile.read(inputFile, inputStream);
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
   * @param thr     current thread
   * @param fieldId field id
   */
  public static void beforeEvent(RVMThread thr, int fieldId) {
    try {
      inputTraceFile.waitForTurn(thr.replayId, fieldId);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }
  }

  /**
   * Handler executed after a memory access.
   * @param thr     current thread
   * @param fieldId field id
   */
  public static void afterEvent(RVMThread thr, int fieldId) {
    try {
      inputTraceFile.advance(thr.replayId, fieldId);
    } catch (TraceFileException tfe) {
      ReplayManager.failAndExit(tfe);
    }
  }
}
