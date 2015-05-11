package org.jikesrvm.replay.sys.ditto;

import org.vmmagic.pragma.Inline;

/**
 * Key that identifies a specific synchronization event.
 * <p>
 * A synchronization event is identified by its logical clock.
 */
public final class SyncKey extends TupleKey {

  /** Synchronization logical clock. */
  public long clock;

  /**
   * Updates the key, as an alternative to a new instance.
   * @param clock new synchronization logical clock
   */
  @Inline
  public void update(long clock) {
    this.clock = clock;
  }

  @Override
  protected boolean keyEquals(Object o) {
    if (!(o instanceof SyncKey)) {
      return false;
    }
    SyncKey k = (SyncKey)o;
    return k.clock == clock;
  }

  @Override
  protected int keyHashCode() {
    return (int)(PRIME * INIT_HASHCODE + (int)(clock ^ (clock >> 32)));
  }

  @Override
  public String toString() {
    return "((" + clock + "))";
  }
}
