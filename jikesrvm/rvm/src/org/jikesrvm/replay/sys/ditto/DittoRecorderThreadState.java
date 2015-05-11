package org.jikesrvm.replay.sys.ditto;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.ditto.OutputDittoDataChunk;

/**
 * State associated with a thread during an execution of the Ditto recorder.
 */
public final class DittoRecorderThreadState {

  ///
  /// Recorder algorithm state
  ///

  /** Unique thread identifier used by the replay subsystem. */
  public long replayId;

  /** Memory-accesses-related logical clock of the thread. */
  public long clock = ReplayConstants.INIT_CLOCK;

  /** Synchronization-related logical clock of the thread. */
  public long syncClock = ReplayConstants.INIT_CLOCK;

  /** Length of the current free-run of the thread. */
  public int currentFreeRun = 0;

  /**
   * Table of interactions between the thread and other threads used for
   * transitive reduction.
   */
  public ThreadInteractionTable interactions = new ThreadInteractionTable();


  ///
  /// Trace-file-related state
  ///

  /** Current chunk of trace data. */
  public OutputDittoDataChunk outputDataChunk;

  /** Whether the thread's trace has been finished. */
  public boolean traceFinished = false;


  ///
  /// Auxiliary data for the recorder
  ///

  /**
   * Auxiliary: a {@link StoreEvent} used by the recorder to avoid creating
   * unnecessary thread-local instances.
   */
  public StoreEvent storeEvent = new StoreEvent();

  /**
   * Auxiliary: a {@link LoadEvent} used by the recorder to avoid creating
   * unnecessary thread-local instances.
   */
  public LoadEvent loadEvent = new LoadEvent();

  /**
   * Auxiliary: a {@link SyncEvent} used by the recorder to avoid creating
   * unnecessary thread-local instances.
   */
  public SyncEvent syncEvent = new SyncEvent();

  /**
   * Auxiliary: holds a reference to the target field of a memory access
   * between the before and after handlers.
   */
  public DittoRecorderObjectState.FieldState currentFieldState = null;

  /**
   * Constructor.
   * @param  threadId id of the thread provided by the replay sub-system
   */
  public DittoRecorderThreadState(long threadId) {
    replayId = threadId;
    outputDataChunk = new OutputDittoDataChunk(threadId);
  }
}
