package org.jikesrvm.replay.sys.ditto;

import org.vmmagic.pragma.Inline;

/**
 * Key that identifies a specific memory load event.
 * <p>
 * A memory load event is identified by the store logical clock: concurrent
 * memory loads may execute in any order, as long as they read the value of
 * the correct memory store.
 */
public final class LoadKey extends TupleKey {

  /** Memory store logical clock. */
  public long storeClock;

  /**
   * Updates the key, as an alternative to a new instance.
   * @param storeClock new memory store logical clock
   */
  @Inline
  public void update(long storeClock) {
    this.storeClock = storeClock;
  }

  @Override
  protected boolean keyEquals(Object o) {
    if (!(o instanceof LoadKey)) {
      return false;
    }
    LoadKey t = (LoadKey)o;
    return t.storeClock == storeClock;
  }

  @Override
  protected int keyHashCode() {
    return PRIME * INIT_HASHCODE + (int)(storeClock ^ (storeClock >> 32));
  }

  @Override
  public String toString() {
    return "(" + storeClock + ")";
  }
}
