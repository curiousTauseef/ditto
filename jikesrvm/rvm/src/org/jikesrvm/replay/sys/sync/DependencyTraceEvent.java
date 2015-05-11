package org.jikesrvm.replay.sys.sync;

/**
 * A dependency event represents a synchronization operation, executed by
 * a thread over an object, that must be synchronized with operations by other
 * threads during replay.
 * <p>
 * A dependency event is characterized by a logical clock ({@link #clock}) that
 * identifies the moment in logical time at which the operation took place.
 */
public final class DependencyTraceEvent implements SyncTraceEvent {

  /** Logical clock of this event's target object prior to its occurrence. */
  public long clock;

  /**
   * Constructor.
   * @param  clock logical clock of the event
   */
  public DependencyTraceEvent(long clock) {
    this.clock = clock;
  }

  @Override
  public String toString() {
    return "d:" + clock;
  }
}
