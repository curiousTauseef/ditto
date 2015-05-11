package org.jikesrvm.replay.sys.jarec;

import org.jikesrvm.replay.sys.ditto.TupleKey;

/**
 * Key that identifies a specific logical time, though the use of a logical
 * clock.
 */
public final class ClockKey extends TupleKey {

  /** Logical clock. */
  public long clock;

  @Override
  public boolean keyEquals(Object o) {
    if (o instanceof ClockKey) {
      return ((ClockKey)o).clock == clock;
    }
    return false;
  }

  @Override
  public int keyHashCode() {
    return PRIME * INIT_HASHCODE
        + (int)(clock ^ (clock >> 32));
  }
}
