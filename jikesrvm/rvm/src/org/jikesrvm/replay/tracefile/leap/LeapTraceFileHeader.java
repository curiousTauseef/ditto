package org.jikesrvm.replay.tracefile.leap;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * Header of a leap trace file.
 * <p>
 * The layout of the header is as follows:
 * <ul>
 *   <li>A signature of {@link #SIGNATURE_BYTES} bytes, whose value is
 *   {@link #SIGNATURE};</li>
 *   <li>A version number of {@link #VERSION_NUMBER_BYTES} bytes, identifying
 *   the version of the leap trace file format used in the trace file to which
 *   the header belongs;</li>
 *   <li>A pointer to the field table chunk of {@link #TABLE_PTR_BYTES} bytes,
 *   as an absolute trace file offset;</li>
 *   <li>A CRC32 checksum of the header.</li>
 * </ul>
 */
public class LeapTraceFileHeader implements SizeConstants, TraceFileConstants {

  /** Offset to the signature, relative to the start of the header. */
  private static final int SIGNATURE_OFFSET = 0;
  /** Size of the signature, in bytes. */
  private static final int SIGNATURE_BYTES = BYTES_IN_LONG;

  /** Offset to the version number, relative to the start of the header. */
  private static final int VERSION_NUMBER_OFFSET = SIGNATURE_OFFSET
                                                 + SIGNATURE_BYTES;
  /** Size of the version number, in bytes. */
  private static final int VERSION_NUMBER_BYTES = BYTES_IN_SHORT;

  /** Offset to the field table pointer, relative to the start of the header. */
  private static final int TABLE_PTR_OFFSET = VERSION_NUMBER_OFFSET
                                            + VERSION_NUMBER_BYTES;
  /** Size of the field table pointer, in bytes. */
  private static final int TABLE_PTR_BYTES = BYTES_IN_LONG;

  /** Offset to the header checksum, relative to the start of the header. */
  private static final int CHECKSUM_OFFSET = TABLE_PTR_OFFSET + TABLE_PTR_BYTES;
  /** Size of the header checksum, in bytes. */
  private static final int CHECKSUM_BYTES = BYTES_IN_INT;

  /** Total size of the header, in bytes. */
  protected static final int BYTES = CHECKSUM_OFFSET + CHECKSUM_BYTES;

  /**
   * Signature of a leap trace file.
   * <pre>
   * {@code ASCII: \211    V    M    T    R    F    L \032}
   * {@code   HEX:   89   54   52   4C   45   41   50   1A}
   * </pre>
   */
  protected static final long SIGNATURE = -8551119304936828902L;

  /** Current version number of the leap trace file format. */
  private static final short VERSION_NUMBER = 0;


  /**
   * Version number of the format used in the leap trace file to which the
   * header belongs.
   */
  protected short versionNumber;

  /** Pointer to the first field table chunk, as a trace file offset. */
  protected long tablePtr;

  /** Constructor. */
  protected LeapTraceFileHeader() {
    versionNumber = VERSION_NUMBER;
    tablePtr = NULL_OFFSET;
  }

  /**
   * Constructor.
   * @param  versionNumber version number of the format used in the leap trace
   *                       file to which the header belongs
   * @param  tablePtr      pointer to the first field table chunk, as an
   *                       absolute trace file offset
   */
  protected LeapTraceFileHeader(short versionNumber, long tablePtr) {
    this.versionNumber = versionNumber;
    this.tablePtr = tablePtr;
  }

  /**
   * Computes the checksum of the header.
   * @return checksum
   */
  protected final int computeChecksum() {
    ByteBuffer buf = ByteBuffer.allocate(CHECKSUM_OFFSET);
    buf.putLong(SIGNATURE);
    buf.putShort(versionNumber);
    buf.putLong(tablePtr);
    CRC32 crc = new CRC32();
    crc.update(buf.array());
    return (int)crc.getValue();
  }
}
