package org.jikesrvm.replay.sys.ditto;

import org.jikesrvm.replay.ReplayConstants;

/**
 * A thread interaction represents a point in logical time in which two threads
 * are known to have performed an operation over the same object/field that
 * requires them to synchronize.
 * <p>
 * The thread interaction object does not identify the threads that interacted,
 * only the point in logical time at which they did.
 */
public final class ThreadInteraction {

  /**
   * Logical clock of the second thread at the moment in which it performed an
   * operation over an object that has now been the target of the first thread,
   * causing this interation to exist.
   */
  public long theirClock = ReplayConstants.INIT_CLOCK;
}
