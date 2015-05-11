package org.jikesrvm.replay.tracefile.sync;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * Header ofa sync trace file.
 * <p>
 * The layout of the header is as follows:
 * <ul>
 *   <li>A signature of {@link #SIGNATURE_BYTES} bytes, whose value is
 *   {@link #SIGNATURE};</li>
 *   <li>A version number of {@link #VERSION_NUMBER_BYTES} bytes, identifying
 *   the version of the sync trace file format used in the trace file to which
 *   the header belongs;</li>
 *   <li>A pointer to the chunk table of {@link #OFFSET_TO_TABLE_BYTES} bytes,
 *   as an absolute trace file offset;</li>
 *   <li>A CRC32 checksum of the header.</li>
 * </ul>
 */
public class SyncTraceFileHeader implements SizeConstants, TraceFileConstants {

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

  /** Offset of the header checksum, relative to the start of the header. */
  private static final int CHECKSUM_OFFSET = OFFSET_TO_TABLE_OFFSET
                                           + OFFSET_TO_TABLE_BYTES;

  /** Size of the header checksum, in bytes. */
  private static final int CHECKSUM_BYTES = BYTES_IN_INT;

  /** Total size of the header, in bytes. */
  public static final int BYTES = CHECKSUM_OFFSET + CHECKSUM_BYTES;

  /**
   * Signature of a sync trace file.
   * <pre>
   * {@code ASCII: \211    T    R    S    Y    N    C \032}
   * {@code   HEX:   89   54   52   53   59   4E   43   1A}
   * </pre>
   */
  protected static final long SIGNATURE = -8551119274535664870L;

  /** Current version number of the sync trace file format. */
  private static final short VERSION_NUMBER = 0;


  /**
   * Version number of the format used in the sync trace file to which the
   * header belongs.
   */
  protected short versionNumber;

  /** Pointer to the chunk table, as an absolute trace file offset. */
  protected long offsetToTable;

  /** Constructor. */
  protected SyncTraceFileHeader() {
    versionNumber = VERSION_NUMBER;
    offsetToTable = NULL_OFFSET;
  }

  /**
   * Constructor.
   * @param  versionNumber version number of the format used in the sync trace
   *                       file format to which the header belongs
   * @param  offsetToTable pointer to the chunk table, as an absolute trace file
   *                       offset
   */
  protected SyncTraceFileHeader(short versionNumber, long offsetToTable) {
    this.versionNumber = versionNumber;
    this.offsetToTable = offsetToTable;
  }

  /**
   * Computes the checksum of the header.
   * @return checksum
   */
  protected final int computeChecksum() {
    ByteBuffer bb = ByteBuffer.allocate(CHECKSUM_OFFSET);
    bb.putLong(SIGNATURE);
    bb.putShort(versionNumber);
    bb.putLong(offsetToTable);
    CRC32 crc = new CRC32();
    crc.update(bb.array());
    return (int)crc.getValue();
  }
}
