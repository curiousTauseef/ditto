package org.jikesrvm.replay;

/**
 * A simple exception class used by the replay subsystem.
 */
public final class ReplayException extends Exception {

  private static final long serialVersionUID = 5478846420272866225L;

  /**
   * Constructor.
   * @param  message exception message
   * @param  cause   inner exception
   */
  public ReplayException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructor.
   * @param  message exception message
   */
  public ReplayException(String message) {
    super(message);
  }

  /**
   * Constructor.
   * @param  cause   inner exception
   */
  public ReplayException(Throwable cause) {
    super(cause);
  }
}
