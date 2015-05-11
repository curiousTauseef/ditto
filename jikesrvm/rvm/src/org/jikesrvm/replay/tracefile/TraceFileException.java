package org.jikesrvm.replay.tracefile;

/**
 * Exception for trace file related errors.
 */
public final class TraceFileException extends Exception {

  private static final long serialVersionUID = -4674112671018514052L;

  /**
   * Constructor.
   * @param message exception message
   */
  public TraceFileException(String message) {
    super(message);
  }

  /**
   * Constructor.
   * @param cause inner exception
   */
  public TraceFileException(Exception cause) {
    super(cause);
  }

  /**
   * Constructor.
   * @param message exception message
   * @param cause   inner exception
   */
  public TraceFileException(String message, Exception cause) {
    super(message, cause);
  }
}
