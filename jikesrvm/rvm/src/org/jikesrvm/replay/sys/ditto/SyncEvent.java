package org.jikesrvm.replay.sys.ditto;

/**
 * A synchronization event represents a synchronization operation, executed
 * by a thread over an object, that must be synchronized with operations by
 * other threads during replay.
 * <p>
 * A synchronization event is characterized by two properties: a free-run count
 * ({@link #freeRun}), and a logical clock ({@link #clock}).
 * <p>
 * Synchronization events are used to convey information between the replay
 * subsystem and the trace files, in both recording and replaying executions.
 */
public final class SyncEvent {

  /**
   * Length of the free run prior to this event, i.e, the amount of operations
   * which the thread is allowed to freely execute without having to synchronize
   * with other threads, before having to synchronize for this synchronization
   * event.
   */
  public long freeRun;

  /**
   * Synchronization logical clock of this event's target object prior to its
   * ocurrence.
   */
  public long clock;

  /** Constructor. */
  public SyncEvent() {
    freeRun = 0;
    clock = 0;
  }

  @Override
  public String toString() {
    return "<<" + freeRun + "->" + clock + ">>";
  }
}
