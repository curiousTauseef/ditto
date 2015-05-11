package org.jikesrvm.replay.sys.sync;

import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.scheduler.RVMThread;

/**
 * State associated with an object during an execution of the Sync recorder.
 */
public final class SyncRecorderObjectState {

  /**
   * Logical clock of the object, identifying the moment in logical time at
   * which the last synchronization operation on the object was performed.
   */
  public long clock;

  /** Last thread that performed a synchronization operation on the object. */
  public RVMThread lastThread;

  /** Private constructor. */
  private SyncRecorderObjectState() {
    clock = ReplayConstants.INIT_CLOCK;
    lastThread = null;
  }

  /**
   * Gets the object state associated with a given object.
   * @param  obj the object
   * @return     the object state associated with {@code obj}
   */
  public static SyncRecorderObjectState getState(Object obj) {
    Object state = MiscHeader.getReplayRef(obj);
    if (state != null) {
      return (SyncRecorderObjectState)state;
    } else {
      // no two threads can get here simultaneously due to the very
      // synchronization we are tracing. Lazy initialization needs no further
      // synchronization.
      SyncRecorderObjectState newState = new SyncRecorderObjectState();
      MiscHeader.setReplayRef(obj, newState);
      return newState;
    }
  }
}
