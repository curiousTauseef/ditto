package org.jikesrvm.replay.tracefile.global;

import org.jikesrvm.SizeConstants;

/**
 * Header of a global-order trace file.
 * <p>
 * The layout of the header is as follows:
 * <ul>
 *   <li>A signature of {@link #SIGNATURE_BYTES} bytes, whose value is
 *   {@link #SIGNATURE};</li>
 *   <li>A version number of {@link #VERSION_NUMBER_BYTES} bytes, identifying
 *   the version of the global-order trace file format used in the trace file to
 *   which the header belongs.</li>
 * </ul>
 */
public class GoTraceFileHeader implements SizeConstants {

  /** Offset to the signature, relative to the start of the header. */
  private static final int SIGNATURE_OFFSET = 0;
  /** Size of the signature, in bytes. */
  private static final int SIGNATURE_BYTES = BYTES_IN_LONG;

  /** Offset to the version number, relative to the start of the header. */
  private static final int VERSION_NUMBER_OFFSET = SIGNATURE_OFFSET
                                                 + SIGNATURE_BYTES;
  /** Size of the version number, in bytes. */
  private static final int VERSION_NUMBER_BYTES = BYTES_IN_SHORT;

  /** Total size of the header, in bytes. */
  protected static final int BYTES = VERSION_NUMBER_OFFSET
                                   + VERSION_NUMBER_BYTES;

   /**
    * Signature of a global-order trace file.
    * <pre>
    * {@code ASCII: \211    T    G    L    O    R    D \032}
    * {@code   HEX:   89   54   47   4C   4F   52   44   1A}
    * </pre>
    */
  protected static final long SIGNATURE = -8551131399395851238L;

  /** Current version number of the global-order trace file format. */
  private static final short VERSION_NUMBER = 0;


  /**
   * Version number of the format used in the global-order trace file to which
   * the header belongs.
   */
  protected short versionNumber;

  /** Constructor. */
  protected GoTraceFileHeader() {
    versionNumber = VERSION_NUMBER;
  }

  /**
   * Constructor.
   * @param  versionNumber version number of the format used in the global-order
   *                       trace file format to which the header belongs
   */
  protected GoTraceFileHeader(short versionNumber) {
    this.versionNumber = versionNumber;
  }
}
