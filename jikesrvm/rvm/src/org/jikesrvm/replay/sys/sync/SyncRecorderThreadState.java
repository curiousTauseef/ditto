package org.jikesrvm.replay.sys.sync;

import java.util.HashMap;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.sync.OutputSyncDataChunk;
import org.jikesrvm.scheduler.RVMThread;

/**
 * State associated with a thread during an execution of the Sync recorder.
 */
public final class SyncRecorderThreadState {

  /** Thread to which the state belongs. */
  private RVMThread myThread;

  /** Logical clock of the thread. */
  public long clock;

  /** Current (on-going) free run of the thread. */
  public FreeRunTraceEvent currentFreeRun;

  /** Current chunk of trace data. */
  public OutputSyncDataChunk outputDataChunk;

  /**
   * Table of interactions between the thread and other threads used for
   * transitive reduction.
   */
  public HashMap<Long, Long> lastInteractions;

  /**
   * Constructor.
   * @param  myThread thread to which the state belongs
   */
  public SyncRecorderThreadState(RVMThread myThread) {
    this.myThread = myThread;
    clock = ReplayConstants.INIT_CLOCK;
    currentFreeRun = null;
    outputDataChunk = new OutputSyncDataChunk();
    lastInteractions = new HashMap<Long, Long>();
  }


  ///
  /// Inter-thread interactions
  ///

  /**
   * Registers an interaction with a thread at a given logical time.
   * @param thread      thread
   * @param threadClock logical clock of the thread at the interaction point
   */
  public void registerInteraction(RVMThread thread, long threadClock) {
    long myId = myThread.replayId;
    long threadId = thread.replayId;
    SyncRecorderThreadState threadState = thread.syncRecState;

    // the states' monitors are acquired in the order of their corresponding
    // thread replay id's to avoid deadlocks
    SyncRecorderThreadState stateA = myId < threadId ? this : threadState;
    SyncRecorderThreadState stateB = stateA == this ? threadState : this;
    synchronized (stateA.lastInteractions) {
      synchronized (stateB.lastInteractions) {
        Long current = lastInteractions.get(threadId);
        if (current == null || current < threadClock) {
          lastInteractions.put(threadId, threadClock);
        }
      }
    }
  }

  /**
   * Obtains the logical time of the last interaction with a given thread.
   * @param  threadId id of the thread
   * @return          logical time of the last interaction
   */
  public long getLastInteractionWith(long threadId) {
    Long interaction = lastInteractions.get(threadId);
    return interaction != null ? interaction : ReplayConstants.NULL_CLOCK;
  }
}
