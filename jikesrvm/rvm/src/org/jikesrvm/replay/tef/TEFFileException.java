package org.jikesrvm.replay.tef;

/**
 * Exception for TEF file-related errors.
 */
public final class TEFFileException extends Exception {

  /** UID for serialization. */
  private static final long serialVersionUID = 3745416675989786152L;

  /**
   * Constructor.
   * @param  message exception message
   * @param  cause   exception cause
   */
  public TEFFileException(String message, Exception cause) {
    super(message, cause);
  }
}
