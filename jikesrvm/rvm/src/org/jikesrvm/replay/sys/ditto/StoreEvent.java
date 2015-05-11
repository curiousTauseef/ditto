package org.jikesrvm.replay.sys.ditto;

/**
 * A store event represents a memory store operation, executed by a thread over
 * a field, that must be synchronized with operations by other threads
 * during replay.
 * <p>
 * A store event is characterized by three properties: a free-run count
 * ({@link #freeRun}), a memory store logical clock ({@link #storeClock}), and
 * a memory load count ({@link #loadCount}).
 * <p>
 * Store events are used to convey information between the replay subsystem and
 * the trace files, in both recording and replaying executions.
 */
public final class StoreEvent {

  /**
   * Length of the free run prior to this event, i.e, the amount of operations
   * which the thread is allowed to freely execute without having to synchronize
   * with other threads, before having to synchronize for this store event.
   */
  public long freeRun;

  /**
   * Store logical clock of this event's target field prior to its occurrence.
   */
  public long storeClock;

  /**
   * Number of memory loads executed on this event's target field after the
   * field's logical clock became {@link #storeClock}.
   */
  public int loadCount;

  /** Constructor. */
  public StoreEvent() {
    freeRun = 0;
    storeClock = 0;
    loadCount = 0;
  }

  @Override
  public String toString() {
    return "<" + freeRun + "->" + storeClock + "," + loadCount + ">";
  }
}
