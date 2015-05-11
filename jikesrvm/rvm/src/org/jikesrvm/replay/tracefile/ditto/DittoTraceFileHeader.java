package org.jikesrvm.replay.tracefile.ditto;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * Header of a ditto trace file.
 * <p>
 * The layout of the header is as follows:
 * <ul>
 *   <li>A signature of {@link #SIGNATURE_BYTES} bytes, whose value is
 *   {@link #SIGNATURE};</li>
 *   <li>A version number of {@link #VERSION_NUMBER_BYTES} bytes, identifying
 *   the version of the Ditto trace file format used in the trace file to which
 *   the header belongs;</li>
 *   <li>A pointer to the chunk table of {@link #OFFSET_TO_TABLE_BYTES} bytes,
 *   as an absolute trace file offset;</li>
 *   <li>A CRC32 checksum of the header.</li>
 * </ul>
 */
public class DittoTraceFileHeader implements SizeConstants,
                                             TraceFileConstants {

  /** Offset to the signature, relative to the start of the header. */
  private static final int SIGNATURE_OFFSET = 0;
  /** Size of the signature, in bytes. */
  private static final int SIGNATURE_BYTES = BYTES_IN_LONG;

  /** Offset to the version number, relative to the start of the header. */
  private static final int VERSION_NUMBER_OFFSET = SIGNATURE_OFFSET
                                                 + SIGNATURE_BYTES;
  /** Size of the version number, in bytes. */
  private static final int VERSION_NUMBER_BYTES = BYTES_IN_SHORT;

  /** Offset to the table pointer, relative to the start of the header. */
  private static final int OFFSET_TO_TABLE_OFFSET = VERSION_NUMBER_OFFSET
                                                  + VERSION_NUMBER_BYTES;
  /** Size of the table pointer, in bytes. */
  private static final int OFFSET_TO_TABLE_BYTES = BYTES_IN_LONG;

  /** Offset to the header checksum, relative to the start of the header. */
  private static final int CHECKSUM_OFFSET = OFFSET_TO_TABLE_OFFSET
                                           + OFFSET_TO_TABLE_BYTES;
  /** Size of the header checksum, in bytes. */
  private static final int CHECKSUM_BYTES = BYTES_IN_INT;

  /** Total size of the header, in bytes. */
  protected static final int BYTES = CHECKSUM_OFFSET + CHECKSUM_BYTES;

  /**
   * Signature of a Ditto trace file.
   * <pre>
   * {@code ASCII: \211    T    D    I    T    T    O \032}
   * {@code HEX:     89   84   68   73   84   84   79   1A}
   * </pre>
   */
  protected static final long SIGNATURE = -8551134710731616486L;

  /** Current version number of the Ditto trace file format. */
  private static final short VERSION_NUMBER = 0;


  /**
   * Version number of the format used in the Ditto trace file to which the
   * header belongs.
   */
  protected short versionNumber;

  /** Pointer to the chunk table, as an absolute trace file offset. */
  protected long offsetToTable;

  /** Constructor. */
  protected DittoTraceFileHeader() {
    versionNumber = VERSION_NUMBER;
    offsetToTable = NULL_OFFSET;
  }

  /**
   * Constructor.
   * @param  versionNumber version number of the format used in the Ditto trace
   *                       file to which the header belongs
   * @param  offsetToTable pointer to the chunk table, as an absolute trace
   *                       file offset
   */
  protected DittoTraceFileHeader(short versionNumber, long offsetToTable) {
    this.versionNumber = versionNumber;
    this.offsetToTable = offsetToTable;
  }

  /**
   * Computes the checksum of the header.
   * @return checksum
   */
  protected final int computeChecksum() {
    ByteBuffer buf = ByteBuffer.allocate(CHECKSUM_OFFSET);
    buf.putLong(SIGNATURE);
    buf.putShort(versionNumber);
    buf.putLong(offsetToTable);
    CRC32 crc = new CRC32();
    crc.update(buf.array());
    return (int)crc.getValue();
  }
}
