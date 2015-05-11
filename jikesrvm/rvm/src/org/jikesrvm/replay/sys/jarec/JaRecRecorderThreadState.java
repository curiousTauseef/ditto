package org.jikesrvm.replay.sys.jarec;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.jarec.OutputJaRecIntervalChunk;

/**
 * State associated with a thread during an execution of the JaRec recorder.
 */
public class JaRecRecorderThreadState {

  /** Logical clock of the thread. */
  public long clock;

  /** Current chunk of trace data. */
  public OutputJaRecIntervalChunk intervalChunk;

  /**
   * Auxiliary: holds a reference to the target object of a memory access
   * between the before and after handlers.
   */
  public JaRecRecorderObjectState currentObjectState;

  /** Whether this thread's trace is finished. */
  public boolean traceFinished;

  /** Constructor. */
  public JaRecRecorderThreadState() {
    clock = ReplayConstants.INIT_CLOCK;
    intervalChunk = new OutputJaRecIntervalChunk();
    currentObjectState = null;
  }
}
