package org.jikesrvm.replay.sys.ditto;

/**
 * A load event represents a memory load operation, executed by a thread over
 * a field, that must be synchronized with operations by other threads
 * during replay.
 * <p>
 * A load event is characterized by two properties: a free-run count
 * ({@link #freeRun}), and a memory store logical clock ({@link #storeClock}).
 * <p>
 * Load events are used to convey information between the replay subsystem and
 * the trace files, in both recording and replaying executions.
 */
public final class LoadEvent {

  /**
   * Length of the free run prior to this event, i.e, the amount of operations
   * which the thread is allowed to freely execute without having to synchronize
   * with other threads, before having to synchronize for this load event.
   */
  public long freeRun;

  /**
   * Store logical clock of this event's target field prior to its occurrence.
   */
  public long storeClock;

  /** Constructor. */
  public LoadEvent() {
    freeRun = 0;
    storeClock = 0;
  }

  @Override
  public String toString() {
    return "<" + freeRun + "->" + storeClock + ">";
  }
}
