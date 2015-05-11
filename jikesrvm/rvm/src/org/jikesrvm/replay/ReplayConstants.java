package org.jikesrvm.replay;

/**
 * Constants used by the replay sub-system.
 */
public final class ReplayConstants {

  /** Default number of maximum array fields. */
  public static final int DEFAULT_MAX_ARRAY_FIELDS = 10;

  /** Null logical clock value. */
  public static final long NULL_CLOCK = -1L;

  /** Null thread id. */
  public static final long NULL_THREAD_ID = -1L;

  /** Value with which to initialized logical clocks. */
  public static final long INIT_CLOCK = 0;

  /** Hidden constructor. */
  private ReplayConstants() { }
}
