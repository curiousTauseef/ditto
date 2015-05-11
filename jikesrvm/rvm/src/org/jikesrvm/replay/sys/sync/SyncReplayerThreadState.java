package org.jikesrvm.replay.sys.sync;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.sync.InputSyncDataChunk;

/**
 * State associated with a thread during an execution of the Sync replayer.
 */
public class SyncReplayerThreadState {

  /** Logical clock of the thread. */
  public long clock;

  /** Current input chunk of trace data. */
  public InputSyncDataChunk inputDataChunk;

  /**
   * Next trace event that the thread should use to synchronize with the other
   * threads.
   */
  public SyncTraceEvent nextEvent;

  /** Constructor. */
  public SyncReplayerThreadState() {
    clock = ReplayConstants.INIT_CLOCK;
    inputDataChunk = null;
    nextEvent = null;
  }
}
