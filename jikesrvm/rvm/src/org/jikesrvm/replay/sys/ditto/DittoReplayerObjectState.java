package org.jikesrvm.replay.sys.ditto;

import java.util.HashMap;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.tef.ThreadEscapedFields;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * State associated with an object during an execution of the Ditto replayer.
 */
public final class DittoReplayerObjectState {

  /**
   * State associated with an object's field during an execution of the Ditto
   * replayer.
   */
  public final class FieldState {

    /** Memory store logical clock of the field. */
    public long storeClock;

    /** Memory load count of the field. */
    public int loadCount;

    /**
     * Table in which threads may wait for the field to reach a specific state.
     */
    private HashMap<TupleKey, Object> waitTable;

    /** Constructor. */
    public FieldState() {
      storeClock = ReplayConstants.INIT_CLOCK;
      loadCount = 0;
      waitTable = new HashMap<TupleKey, Object>();
    }

    /**
     * Registers an already-executed memory load operation on the field.
     * The state of the field is updated accordingly and any threads waiting for
     * the resulting state are notified.
     * @param key optimization: used to avoid creating unnecessary
     *            {@link StoreKey} instances
     */
    @Inline
    public void registerLoad(StoreKey key) {
      synchronized (waitTable) {
        // create a store key representing the next field state
        key.update(storeClock, loadCount + 1);
        // find any threads waiting for the next field state
        Object waitObj = waitTable.get(key);

        // no threads are waiting for the next field state. simply update it
        if (waitObj == null) {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] load t#"
                + RVMThread.getCurrentThread().replayId + " updating to " + key
                + " on " + obj2str(waitTable));
          }
          loadCount++;
        }
        // there are threads waiting for the next field state. update it and
        // notify all
        else {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] load t#"
                + RVMThread.getCurrentThread().replayId
                + " updating to and notifying " + key + " on "
                + obj2str(waitTable));
          }
          synchronized (waitObj) {
            loadCount++;
            waitObj.notifyAll();
            waitTable.remove(key);
          }
        }
      }
    }

    /**
     * Registers an already-executed memory store operation on the field.
     * The state of the field is update accordingly and any threads waiting for
     * the resulting state are notified.
     * @param newClock new memory store logical clock value
     * @param loadKey  optimization: used to avoid creating unnecessary
     *                 {@link LoadKey} instances
     * @param storeKey optimization: used to avoid creating unnecessary
     *                 {@link StoreKey} instances
     */
    @Inline
    public void registerStore(long newClock, LoadKey loadKey,
                              StoreKey storeKey) {
      // create load and store keys representing the next field state
      loadKey.update(newClock);
      storeKey.update(newClock, 0);

      synchronized (waitTable) {
        // find any threads waiting for the next field state
        Object loadWaitObj = waitTable.get(loadKey);
        Object storeWaitObj = waitTable.get(storeKey);

        // there are no threads waiting for the next field state
        if (loadWaitObj == null && storeWaitObj == null) {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] store t#"
                + RVMThread.getCurrentThread().replayId + " updating to "
                + storeKey + " on " + obj2str(waitTable));
          }
          loadCount = 0;
          storeClock = newClock;
        }
        // threre are threads waiting for the next field state to perform
        // memory load operations
        else if (loadWaitObj == null) {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] store t#"
                + RVMThread.getCurrentThread().replayId
                + " updating to and notifying " + storeKey
                + " on " + obj2str(waitTable));
          }
          synchronized (storeWaitObj) {
            loadCount = 0;
            storeClock = newClock;
            storeWaitObj.notifyAll();
            waitTable.remove(storeKey);
          }
        }
        // there are threads waiting for the next field state to perform
        // memory store operations
        else if (storeWaitObj == null) {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] store t#"
                + RVMThread.getCurrentThread().replayId + " updating to "
                + storeKey + " and notifying " + loadKey
                + " on " + obj2str(waitTable));
          }
          synchronized (loadWaitObj) {
            loadCount = 0;
            storeClock = newClock;
            loadWaitObj.notifyAll();
            waitTable.remove(loadKey);
          }
        }
        // there are threads waiting for the next field state to perform
        // both memory load and memory store operations
        else {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] store t#"
                + RVMThread.getCurrentThread().replayId + " updating to "
                + storeKey + " and notifying " + loadKey + " and " + storeKey
                + " on " + obj2str(waitTable));
          }
          synchronized (loadWaitObj) {
            synchronized (storeWaitObj) {
              loadCount = 0;
              storeClock = newClock;
              storeWaitObj.notifyAll();
              waitTable.remove(storeKey);
              loadWaitObj.notifyAll();
              waitTable.remove(loadKey);
            }
          }
        }
      }
    }

    /**
     * Waits until the field state is at the point in which a specific memory
     * load operation can be executed.
     * @param tStoreClock target memory store logical clock
     * @param key         optimization: used to avoid creating unnecessary
     *                    {@link LoadKey} instances
     */
    @Inline
    public void waitBeforeLoad(long tStoreClock, LoadKey key) {
      if (VM.VerifyAssertions) {
        VM._assert(storeClock <= tStoreClock,
            "ASSERT FAIL: t#" + RVMThread.getCurrentThread().replayId
            + " target=(" + tStoreClock + ") but now=(" + storeClock
            + ") on " + obj2str(waitTable));
      }

      // if the current memory store logical clock is higher than the target,
      // the memory load can already go through
      if (storeClock >= tStoreClock) {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Replayer] load t#"
              + RVMThread.getCurrentThread().replayId + " going through "
              + tStoreClock + " on " + obj2str(waitTable));
        }
        return;
      }

      // optimization: many times, yielding once is enough to reach the target
      // state; the overhead of waiting on a monitor is saved
      Thread.yield();
      if (storeClock >= tStoreClock) {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Replayer] load t#"
              + RVMThread.getCurrentThread().replayId
              + " going through (after yielding) " + tStoreClock
              + " on " + obj2str(waitTable));
        }
        return;
      }

      // create a load key representing the target field state.
      // then, use it to get a monitor on which to wait
      key.update(tStoreClock);
      Object waitObj;
      synchronized (waitTable) {
        // check again if waiting is required
        if (storeClock >= tStoreClock) {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] load t#"
                + RVMThread.getCurrentThread().replayId
                + " going through (after table) " + key
                + " on " + obj2str(waitTable));
          }
          return;
        }

        // get an object on whose monitor to wait. if there is none, the key
        // itself will fulfill the role
        if (!waitTable.containsKey(key)) {
          waitObj = key;
          waitTable.put(key, waitObj);
        } else {
          waitObj = waitTable.get(key);
        }
      }

      synchronized (waitObj) {
        // wait on the object's monitor until the target field state has been
        // reached
        while (storeClock < tStoreClock) {
          try {
            if (DEBUG && ReplayOptions.VERBOSITY > 2) {
              VM.sysWriteln("[Ditto Replayer] load t#"
                  + RVMThread.getCurrentThread().replayId + " waiting for "
                  + key + " on " + obj2str(waitTable));
            }
            waitObj.wait();
          } catch (InterruptedException ie) {
            // go back to waiting...
          }
        }
      }
      if (DEBUG && ReplayOptions.VERBOSITY > 2) {
        VM.sysWriteln("[Ditto Replayer] load t#"
            + RVMThread.getCurrentThread().replayId
            + " going through (after wait) " + key
            + " on " + obj2str(waitTable));
      }
    }

    /**
     * Waits until the field state is at the point in which a specific memory
     * store operation can be executed.
     * @param tStoreClock target memory store logical clock
     * @param tLoadCount  target memory load count
     * @param key         optimization: used to avoid creating unnecessary
     *                    {@link StoreKey} instances
     */
    @Inline
    public void waitBeforeStore(long tStoreClock, int tLoadCount,
                                StoreKey key) {
      // we cannot do any checks outside of a monitor, because the load count
      // can go downwards
      Object waitObj = null;
      synchronized (waitTable) {
        if (VM.VerifyAssertions) {
          VM._assert(storeClock < tStoreClock
              || (storeClock == tStoreClock && loadCount <= tLoadCount),
              "ASSERT FAIL: t#" + RVMThread.getCurrentThread().replayId
              + " target=(" + tStoreClock + "," + tLoadCount + ") but now=("
              + storeClock + "," + loadCount + ") on " + obj2str(waitTable));
        }

        if (storeClock < tStoreClock || loadCount < tLoadCount) {
          // create a store key representing the target field state.
          // then, use it to get a monitor on which to wait
          key.update(tStoreClock, tLoadCount);
          if (!waitTable.containsKey(key)) {
            waitObj = key;
            waitTable.put(key, waitObj);
          } else {
            waitObj = waitTable.get(key);
          }
        }
      }

      // if there is no waitObj, then the field is already at the target state
      if (waitObj == null) {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Replayer] store t#"
              + RVMThread.getCurrentThread().replayId + " going through "
              + key + " on " + obj2str(waitTable));
        }
        return;
      }

      synchronized (waitObj) {
        // wait on the object's monitor, until the target field state has been
        // reached
        while (storeClock < tStoreClock || loadCount < tLoadCount) {
          try {
            if (DEBUG && ReplayOptions.VERBOSITY > 2) {
              VM.sysWriteln("[Ditto Replayer] store t#"
                  + RVMThread.getCurrentThread().replayId + " waiting for "
                  + key + " on " + obj2str(waitTable));
            }
            waitObj.wait();
          } catch (InterruptedException ie) {
            // go back to waiting...
          }
        }
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Replayer] store t#"
              + RVMThread.getCurrentThread().replayId
              + " going through (after wait) " + key
              + " on " + obj2str(waitTable));
        }
      }
    }
  }


  /** State associated with each field of the object. */
  public FieldState[] fieldStates;

  /** Synchronization-related logical clock of the object. */
  public long syncClock;

  /**
   * Table in which threads may wait for the object to reach a specific state.
   */
  private HashMap<TupleKey, Object> waitTable;

  /**
   * Constructor.
   * @param  n number of fields of the object
   */
  private DittoReplayerObjectState(int n) {
    fieldStates = new FieldState[n];
    syncClock = ReplayConstants.INIT_CLOCK;
    waitTable = new HashMap<TupleKey, Object>();
    for (int i = 0; i < n; i++) {
      fieldStates[i] = new FieldState();
    }
  }

  /**
   * Registers an already-executed synchronization operation on the object.
   * The state of the object is updated accordingly and any threads waiting for
   * the resulting state are notified.
   * @param newClock new synchronization logical clock value
   * @param key      optimization: used to avoid creating unnecessary
   *                 {@link SyncKey} instances
   */
  @Inline
  public void registerSync(long newClock, SyncKey key) {
    // create a sync key representing the next object state
    key.update(newClock);

    synchronized (waitTable) {
      // find any threads waiting for the next object state
      Object waitObj = waitTable.get(key);

      // no threads are waiting for the next object state. simply update it
      if (waitObj == null) {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Replayer] sync t#"
              + RVMThread.getCurrentThread().replayId + " updating to "
              + key + " on " + obj2str(waitTable));
        }
        syncClock = newClock;
      }
      // there are threads waiting for the next object state. update it and
      // notify all
      else {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Replayer] sync t#"
              + RVMThread.getCurrentThread().replayId
              + " updating to and notifying " + key
              + " on " + obj2str(waitTable));
        }
        synchronized (waitObj) {
          syncClock = newClock;
          waitObj.notifyAll();
          waitTable.remove(key);
        }
      }
    }
  }

  /**
   * Waits until the object state is at the point in which a specific
   * synchronization operation can be executed.
   * @param tSyncClock target synchronization logical clock
   * @param key        optimization: used to avoid creating unnecessary
   *                   {@link SyncKey} instances
   */
  @Inline
  public void waitBeforeSync(long tSyncClock, SyncKey key) {
    if (VM.VerifyAssertions) {
      VM._assert(syncClock <= tSyncClock,
          "ASSERT FAIL: t#" + RVMThread.getCurrentThread().replayId
          + " target_sync=(" + tSyncClock + ") now=(" + syncClock
          + ") on " + obj2str(waitTable));
    }

    // if the current synchronization logical clock is higher than the target,
    // the synchronization operation can already go through
    if (syncClock >= tSyncClock) {
      if (DEBUG && ReplayOptions.VERBOSITY > 2) {
        VM.sysWriteln("[Ditto Replayer] sync t#"
            + RVMThread.getCurrentThread().replayId + " going through "
            + tSyncClock + " on " + obj2str(waitTable));
      }
      return;
    }

    // optimization: many times, yielding once is enough to reach the target
    // state; the overhead of waiting on a monitor is saved
    Thread.yield();
    if (syncClock >= tSyncClock) {
      if (DEBUG && ReplayOptions.VERBOSITY > 2) {
        VM.sysWriteln("[Ditto Replayer] sync t#"
            + RVMThread.getCurrentThread().replayId
            + " going through (after yielding) " + tSyncClock
            + " on " + obj2str(waitTable));
      }
      return;
    }

    // create a sync key representing the target object state.
    // then, use it to get a monitor on which to wait
    key.update(tSyncClock);
    Object waitObj;
    synchronized (waitTable) {
      // check again if waiting is required
      if (syncClock >= tSyncClock) {
        if (DEBUG && ReplayOptions.VERBOSITY > 2) {
          VM.sysWriteln("[Ditto Replayer] sync t#"
              + RVMThread.getCurrentThread().replayId
              + " going through (after table) " + key
              + " on " + obj2str(waitTable));
        }
        return;
      }

      // get an object on whose monitor to wait. if there is none, the key
      // itself will fulfill the role
      if (!waitTable.containsKey(key)) {
        waitObj = key;
        waitTable.put(key, waitObj);
      } else {
        waitObj = waitTable.get(key);
      }
    }

    synchronized (waitObj) {
      // wait on the object's monitor until the target object state has been
      // reached
      while (syncClock < tSyncClock) {
        try {
          if (DEBUG && ReplayOptions.VERBOSITY > 2) {
            VM.sysWriteln("[Ditto Replayer] sync t#"
                + RVMThread.getCurrentThread().replayId + " waiting for "
                + key + " on " + obj2str(waitTable));
          }
          waitObj.wait();
        } catch (InterruptedException ie) {
          // go back to waiting...
        }
      }
      if (DEBUG && ReplayOptions.VERBOSITY > 2) {
        VM.sysWriteln("[Ditto Replayer] sync t#"
            + RVMThread.getCurrentThread().replayId
            + " going through (after wait) " + key
            + " on " + obj2str(waitTable));
      }
    }
  }


  ///
  /// Static interface
  ///

  /**
   * Gets the object state associated with a given instance of
   * {@link java.lang.Class}, used to trace accesses to static fields.
   * @param  klass the instance of {@link java.lang.Class}
   * @return       the object state associated with {@code klass}
   */
  @Inline
  public static DittoReplayerObjectState getStaticState(Class<?> klass) {
    return getStaticState(klass, JikesRVMSupport.getTypeForClass(klass));
  }

  /**
   * Gets the object state associated with a given instance of
   * {@link java.lang.Class}, used to trace accesses to static fields.
   * @param  klass the instance of {@link java.lang.Class}
   * @param  type  the {@link org.jikesrvm.classloader.RVMType} of {@code klass}
   * @return       the object state associated with {@code klass}
   */
  @Inline
  public static DittoReplayerObjectState getStaticState(Class<?> klass,
                                                        RVMType type) {
    Object state = MiscHeader.getReplayRef(klass);
    if (state != null) {
      return (DittoReplayerObjectState)state;
    } else {
      return initStaticState(klass, type);
    }
  }

  /**
   * Gets the object state associated with a given object.
   * @param  obj the object
   * @return     the object state associated with {@code obj}
   */
  @Inline
  public static DittoReplayerObjectState getInstanceState(Object obj) {
    if (obj != null) {
      Object state = MiscHeader.getReplayRef(obj);
      if (state != null) {
        return (DittoReplayerObjectState)state;
      } else {
        return initInstanceState(obj);
      }
    }
    return null;
  }

  ///
  /// State creation & initialization
  ///

  /**
   * Creates and initializes the object state associated with a given instance
   * of {@link java.lang.Class}.
   * @param  klass the instance of {@link java.lang.Class}
   * @param  type  the {@link org.jikesrvm.classloader.RVMType} of {@code klass}
   * @return       the object state associated with {@code klass}
   */
  @Inline
  private static DittoReplayerObjectState initStaticState(
                                         Class<?> klass, RVMType type) {
    synchronized (type) {
      Object state = MiscHeader.getReplayRef(klass);
      if (state == null) {
        int n = ThreadEscapedFields.getNumberOfStaticTEFields(type);
        DittoReplayerObjectState newState = new DittoReplayerObjectState(n);
        MiscHeader.setReplayRef(klass, newState);
        return newState;
      } else {
        return (DittoReplayerObjectState)state;
      }
    }
  }

  /**
   * Creates and initializes the object state associated with a given object.
   * @param  obj the object
   * @return     the object state associated with {@code obj}
   */
  @Inline
  private static synchronized DittoReplayerObjectState initInstanceState(
                                                       Object obj) {
    Object state = MiscHeader.getReplayRef(obj);
    if (state == null) {
      int n = ThreadEscapedFields.getNumberOfInstanceTEFields(obj);
      DittoReplayerObjectState newState = new DittoReplayerObjectState(n);
      MiscHeader.setReplayRef(obj, newState);
      return newState;
    } else {
      return (DittoReplayerObjectState)state;
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
