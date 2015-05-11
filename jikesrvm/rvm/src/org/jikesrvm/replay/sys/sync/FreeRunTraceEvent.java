package org.jikesrvm.replay.sys.sync;

/**
 * A free run event represents a sequence of synchronization operations,
 * executed by a thread over one or more objects, that may be executed freely
 * during replay without synchronizing with other threads.
 * <p>
 * A free run event is characterized by a length ({@link #length}) that
 * identifies the number of operations in the free run.
 */
public final class FreeRunTraceEvent implements SyncTraceEvent {

  /**
   * Length of the free run, i.e., the amount of operation which the thread is
   * allowed to freely execute without having to synchronize with other threads.
   */
  public long length;

  /**
   * Constructor.
   * @param  length length of the free run
   */
  public FreeRunTraceEvent(long length) {
    this.length = length;
  }

  @Override
  public String toString() {
    return "f:" + length;
  }
}
