package org.jikesrvm.replay.sys.jarec;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.jarec.InputJaRecIntervalChunk;

/**
 * State associated with a thread during an execution of the JaRec replayer.
 */
public class JaRecReplayerThreadState {

  /** Logical clock of the thread. */
  public long clock = ReplayConstants.INIT_CLOCK;

  /** Curreent input chunk of trace data. */
  public InputJaRecIntervalChunk intervalChunk = null;

  /**
   * Auxiliary: a {@link ClockKey} used by the replayer to avoid creating
   * unnecessary thread-local instances.
   */
  public ClockKey key = new ClockKey();
}
