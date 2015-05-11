package pt.utl.ist.tlo;

/**
 * An exception type used for tef-file-related errors.
 */
public final class TefFileException extends Exception {

  private static final long serialVersionUID = 2261103103129699460L;

  /**
   * Constructor.
   * @param  message exception message
   * @param  cause   inner exception
   */
  public TefFileException(String message, Exception cause) {
    super(message, cause);
  }
}
