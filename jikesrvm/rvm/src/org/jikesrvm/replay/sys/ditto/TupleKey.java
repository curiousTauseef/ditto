package org.jikesrvm.replay.sys.ditto;

/**
 * A key that identifies a replay state during execution replay.
 * <p>
 * The keys are used by threads, during replay, to wait for specific replay
 * events and notify other threads of events it generates.
 * <p>
 * The keys are used in collections, so they must provide {@code equals}
 * and {@code hashCode} implementations.
 */
public abstract class TupleKey {

  /** Auxiliary value for hash code calculation. */
  protected static final int INIT_HASHCODE = 23;

  /** Prime number for hash code calculation. */
  protected static final int PRIME = 37;

  /**
   * Same as {@link #equals}, but subclasses must implement it.
   * @param o object to which to compare
   * @return  whether the object equals this key
   */
  protected abstract boolean keyEquals(Object o);

  /**
   * Same as {@link #hashCode}, but subclasses must implement it.
   * @return hash code for the key
   */
  protected abstract int keyHashCode();

  @Override
  public final boolean equals(Object o) {
    return keyEquals(o);
  }

  @Override
  public final int hashCode() {
    return keyHashCode();
  }
}
