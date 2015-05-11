package org.jikesrvm.replay.sys.jarec;

import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.replay.ReplayConstants;

/**
 * State associated with an object during an execution of the JaRec recorder.
 */
public final class JaRecRecorderObjectState {

  /** Logical clock of the object. */
  public long clock = ReplayConstants.INIT_CLOCK;

  /** Private constructor. */
  private JaRecRecorderObjectState() { }

  /**
   * Gets the object state associated with a given object.
   * @param  obj the object
   * @return     object state associated with the object
   */
  public static JaRecRecorderObjectState getState(Object obj) {
    Object state = MiscHeader.getReplayRef(obj);
    if (state != null) {
      return (JaRecRecorderObjectState)state;
    } else {
      return initState(obj);
    }
  }

  /**
   * Creates and initializes the object state associated with a given object.
   * @param  obj the object
   * @return     object state associated with the object
   */
  private static synchronized JaRecRecorderObjectState initState(Object obj) {
    Object state = MiscHeader.getReplayRef(obj);
    if (state == null) {
      JaRecRecorderObjectState newState = new JaRecRecorderObjectState();
      MiscHeader.setReplayRef(obj, newState);
      return newState;
    } else {
      return (JaRecRecorderObjectState)state;
    }
  }
}
