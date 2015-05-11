package org.jikesrvm.replay.sys.leap;

import org.jikesrvm.replay.tracefile.leap.LeapDataChunk;

/**
 * State associated with a thread during an execution of the Leap recorder.
 */
public class LeapRecorderThreadState {

  /** Whether the thread's trace has been finished. */
  public boolean traceFinished;

  /**
   * Auxiliary: holds a reference to the trace of the target field of a memory
   * access between the before and after handlers.
   */
  public LeapDataChunk curFieldTrace;
}
