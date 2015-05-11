package org.jikesrvm.replay.sys.ditto;

import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tef.ThreadEscapedFields;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;

/**
 * State associated with an object during an execution of the Ditto recorder.
 */
public final class DittoRecorderObjectState {

  /**
   * State associated with an object's field during an execution of the Ditto
   * recorder.
   */
  public class FieldState {

    /** Memory store logical clock of the field. */
    public long storeClock = ReplayConstants.INIT_CLOCK;

    /** Memory load count of the field. */
    public int loadCount = 0;

    /** Last thread that performed a memory store on the field. */
    public DittoRecorderThreadState lastThread = null;

    /**
     * Whether the current value of the field has been read by threads other
     * than {@link #lastThread}.
     */
    public boolean loadedByOtherThreads = false;
  }


  /** State associated with each field of the object. */
  public FieldState[] fieldStates;

  /** Synchronization-related logical clock of the object. */
  public long syncClock = ReplayConstants.INIT_CLOCK;

  /** Last thread that performed a synchronization operation on the object. */
  public RVMThread syncLastThread = null;

  /**
   * Constructor.
   * @param  n number of fields of the object
   */
  private DittoRecorderObjectState(int n) {
    fieldStates = new FieldState[n];
    for (int i = 0; i < n; i++) {
      fieldStates[i] = new FieldState();
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
  public static DittoRecorderObjectState getStaticState(Class<?> klass) {
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
  public static DittoRecorderObjectState getStaticState(
                                        Class<?> klass, RVMType type) {
    Object state = MiscHeader.getReplayRef(klass);
    if (state != null) {
      return (DittoRecorderObjectState)state;
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
  public static DittoRecorderObjectState getInstanceState(Object obj) {
    if (obj != null) {
      Object state = MiscHeader.getReplayRef(obj);
      if (state != null) {
        return (DittoRecorderObjectState)state;
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
  private static DittoRecorderObjectState initStaticState(
                                         Class<?> klass, RVMType type) {
    synchronized (type) {
      Object state = MiscHeader.getReplayRef(klass);
      if (state == null) {
        int nFields = ThreadEscapedFields.getNumberOfStaticTEFields(type);
        DittoRecorderObjectState newState =
            new DittoRecorderObjectState(nFields);
        MiscHeader.setReplayRef(klass, newState);
        return newState;
      } else {
        return (DittoRecorderObjectState)state;
      }
    }
  }

  /**
   * Creates and initializes the object state associated with a given object.
   * @param  obj the object
   * @return     the object state associated with {@code obj}
   */
  @Inline
  private static synchronized DittoRecorderObjectState initInstanceState(
                                                      Object obj) {
    Object state = MiscHeader.getReplayRef(obj);
    if (state == null) {
      int nFields = ThreadEscapedFields.getNumberOfInstanceTEFields(obj);
      DittoRecorderObjectState newState = new DittoRecorderObjectState(nFields);
      MiscHeader.setReplayRef(obj, newState);
      return newState;
    } else {
      return (DittoRecorderObjectState)state;
    }
  }
}
