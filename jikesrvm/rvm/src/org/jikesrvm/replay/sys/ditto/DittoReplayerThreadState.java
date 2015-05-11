package org.jikesrvm.replay.sys.ditto;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.ditto.InputDittoDataChunk;

/**
 * State associated with a thread during an execution of the Ditto replayer.
 */
public final class DittoReplayerThreadState {

  ///
  /// Replayer algorithm state
  ///

  /** Memory-accesses-related logical clock of the thread. */
  public long clock;

  /** Synchronization-realted logical clock of the thread. */
  public long syncClock;

  /** Length of the current free-run of the thread. */
  public long currentFreeRun;


  ///
  /// Trace-file-related state
  ///

  /** Current input chunk of trace data. */
  public InputDittoDataChunk inputDataChunk;


  ///
  /// Auxiliary data for the replayer
  ///

  /**
   * Auxiliary: a {@link LoadEvent} used by the replayer to avoid creating
   * unnecessary thread-local instances.
   */
  public LoadEvent loadEvent;

  /**
   * Auxiliary: a {@link StoreEvent} used by the replayer to avoid creating
   * unnecessary thread-local instances.
   */
  public StoreEvent storeEvent;

  /**
   * Auxiliary: a {@link SyncEvent} used by the replayer to avoid creating
   * unnecessary thread-local instances.
   */
  public SyncEvent syncEvent;

  /**
   * Auxiliary: holds a reference to the target field of a memory access
   * between the before and after handlers.
   */
  public DittoReplayerObjectState.FieldState currentFieldState;

  /**
   * Auxiliary: a {@link LoadKey} used by the replayer to avoid creating
   * unnecessary thread-local instances.
   */
  public LoadKey loadKey;

  /**
   * Auxiliary: a {@link StoreKey} used by the replayer to avoid creating
   * unnecessary thread-local instances.
   */
  public StoreKey storeKey;

  /**
   * Auxiliary: a {@link SyncKey} used by the replayer to avoid creating
   * unnecessary thread-local instances.
   */
  public SyncKey syncKey;

  /** Constructor. */
  public DittoReplayerThreadState() {
    clock = ReplayConstants.INIT_CLOCK;
    syncClock = ReplayConstants.INIT_CLOCK;
    inputDataChunk = null;
    loadEvent = new LoadEvent();
    storeEvent = new StoreEvent();
    syncEvent = new SyncEvent();
    currentFreeRun = 0;
    loadKey = new LoadKey();
    storeKey = new StoreKey();
    syncKey = new SyncKey();
  }
}
