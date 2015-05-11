package org.jikesrvm.replay.sys.sync;

import java.util.HashMap;

import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * State associated with an object during an execution of the Sync replayer.
 */
public final class SyncReplayerObjectState {

  /** Logical clock of the object. */
  public long clock;

  /**
   * Table in which threads may wait for the object to reach a specific state.
   */
  public HashMap<Long, Object> waitTable;

  /** Private constructor. */
  private SyncReplayerObjectState() {
    clock = ReplayConstants.INIT_CLOCK;
    waitTable = new HashMap<Long, Object>();
  }

  /**
   * Updates the object's logical clock after a synchronization operation is
   * performed on it. Any threads waiting for the resulting state are notified.
   * @param newClock new logical clock value
   */
  @Inline
  public void updateClock(long newClock) {
    synchronized (waitTable) {
      // find any threads waiting for the new logical clock value
      Object waitObj = waitTable.get(newClock);

      // if no threads are waiting, simply update the logical clock
      if (waitObj == null) {
        if (DEBUG && ReplayOptions.VERBOSITY > 1) {
          VM.sysWriteln("[Sync Replayer] t#"
              + RVMThread.getCurrentThread().replayId + " setting clock to "
              + newClock + " on " + obj2str(waitTable));
        }
        clock = newClock;
        return;
      }

      // threads are waiting... update the logical clock and notify all
      synchronized (waitObj) {
        if (DEBUG && ReplayOptions.VERBOSITY > 1) {
          VM.sysWriteln("[Sync Replayer] t#"
              + RVMThread.getCurrentThread().replayId
              + " updating to and notifying " + newClock
              + " on " + obj2str(waitTable));
        }
        waitObj.notifyAll();
        clock = newClock;
        waitTable.remove(newClock);
      }
    }
  }

  /**
   * Waits until the object's logical clock reaches a given value.
   * @param targetClock target logical clock
   */
  @Inline
  public void waitForClock(long targetClock) {
    // if the current logical clock is at or past the target, the thread can
    // go through
    if (clock >= targetClock) {
      if (DEBUG && ReplayOptions.VERBOSITY > 1) {
        VM.sysWriteln("[Sync Replayer] t#"
            + RVMThread.getCurrentThread().replayId + " going through "
            + targetClock + " on " + obj2str(waitTable));
      }
      return;
    }

    // optimization: many times, yielding once is enough to reach the target
    // state; the overhead of waiting on a monitor is saved
    Thread.yield();
    if (clock >= targetClock) {
      if (DEBUG && ReplayOptions.VERBOSITY > 1) {
        VM.sysWriteln("[Sync Replayer] t#"
            + RVMThread.getCurrentThread().replayId
            + " going through (after yielding) " + targetClock
            + " on " + obj2str(waitTable));
      }
      return;
    }

    // get an object on whose monitor to wait for the target logical clock
    Object waitObj;
    synchronized (waitTable) {
      // check again if waiting is requiredc
      if (clock >= targetClock) {
        if (DEBUG && ReplayOptions.VERBOSITY > 1) {
          VM.sysWriteln("[Sync Replayer] t#"
              + RVMThread.getCurrentThread().replayId
              + " going through (after table) " + targetClock
              + " on " + obj2str(waitTable));
        }
        return;
      }

      // get an object on whose monitor to wait. if there is none, create one
      // ourselves
      waitObj = waitTable.get(targetClock);
      if (waitObj == null) {
        waitObj = new Object();
        waitTable.put(targetClock, waitObj);
      }
    }

    synchronized (waitObj) {
      // wait on the object's monitor until the target logical clock has been
      // reached
      while (clock < targetClock) {
        try {
          if (DEBUG && ReplayOptions.VERBOSITY > 1) {
            VM.sysWriteln("[Sync Replayer] t#"
                + RVMThread.getCurrentThread().replayId
                + " going through (after wait) " + targetClock
                + " on " + obj2str(waitTable));
          }
          waitObj.wait();
        } catch (InterruptedException ie) {
          // go back to waiting...
        }
      }
    }
  }

  ///
  /// State creation & initialization
  ///

  /**
   * Gets the object state associated with a given object.
   * @param  obj the object
   * @return     the object state associated with {@code obj}
   */
  @Inline
  public static SyncReplayerObjectState getState(Object obj) {
    Object state = MiscHeader.getReplayRef(obj);
    if (state != null) {
      return (SyncReplayerObjectState)state;
    } else {
      return initState(obj);
    }
  }

  /**
   * Creates and initilizes the object state associated with a given object.
   * @param  obj the object
   * @return     the object state associated with {@code obj}
   */
  @Inline
  private static synchronized SyncReplayerObjectState initState(Object obj) {
    Object state = MiscHeader.getReplayRef(obj);
    if (state == null) {
      SyncReplayerObjectState newState = new SyncReplayerObjectState();
      MiscHeader.setReplayRef(obj, newState);
      return newState;
    } else {
      return (SyncReplayerObjectState)state;
    }
  }

  /**
   * Acts as a toString method for objects that identifies them uniquely with
   * an acceptable degree of certainty.
   * @param  obj object
   * @return     almost unique string representation of the object
   */
  private String obj2str(Object obj) {
    return '[' + Integer.toHexString(hashCode()) + ']';
  }
}
