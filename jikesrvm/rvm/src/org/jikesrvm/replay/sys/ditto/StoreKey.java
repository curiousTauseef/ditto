package org.jikesrvm.replay.sys.ditto;

import org.vmmagic.pragma.Inline;

/**
 * Key that identifies a specific memory store event.
 * <p>
 * Memory store events are identified by both a store logical clock and a
 * load count: a memory store can only be executed after every memory load
 * that must read the previous value has been executed.
 */
public final class StoreKey extends TupleKey {

  /** Memory store logical clock. */
  public long storeClock;

  /** Memory load count after {@link #storeClock}. */
  public int loadCount;

  /**
   * Updates the key, as an alternative to a new instance.
   * @param storeClock new memory store logical clock
   * @param loadCount  new memory load count
   */
  @Inline
  public void update(long storeClock, int loadCount) {
    this.storeClock = storeClock;
    this.loadCount = loadCount;
  }

  @Override
  protected boolean keyEquals(Object o) {
    if (!(o instanceof StoreKey)) {
      return false;
    }
    StoreKey t = (StoreKey)o;
    return t.storeClock == storeClock && t.loadCount == loadCount;
  }

  @Override
  protected int keyHashCode() {
    return PRIME * (PRIME * INIT_HASHCODE
        + (int)(storeClock ^ (storeClock >> 32)))
        + loadCount;
  }

  @Override
  public String toString() {
    return "(" + storeClock + "," + loadCount + ")";
  }
}
