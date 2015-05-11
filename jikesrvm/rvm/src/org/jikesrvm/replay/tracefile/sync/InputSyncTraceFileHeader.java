package org.jikesrvm.replay.tracefile.sync;

import java.nio.ByteBuffer;

import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * Input version of the header of a sync trace file.
 */
public final class InputSyncTraceFileHeader extends SyncTraceFileHeader {

  /**
   * Private constructor.
   * @param  versionNumber version number of the format used in the sync trace
   *                       file format to which the header belongs
   * @param  offsetToTable pointer to the chunk table, as an absolute trace
   *                       file offset
   */
  private InputSyncTraceFileHeader(short versionNumber, long offsetToTable) {
    super(versionNumber, offsetToTable);
  }

  /**
   * Reads a sync trace file header from a given buffer.
   * The buffer must be positioned at the beginning of the header and have at
   * least {@link #BYTES} bytes past that point.
   * @param  buf                buffer
   * @return                    sync trace file header
   * @throws TraceFileException if either the signature of the trace file or
   *                            the header's checksum are incorrect
   */
  public static InputSyncTraceFileHeader read(ByteBuffer buf)
                                       throws TraceFileException {
    // read and validate the signature
    long magicNumber = buf.getLong();
    if (magicNumber != SIGNATURE) {
      throw new TraceFileException("Invalid signature in the sync trace file.");
    }

    // read the version number and the chunk table pointer
    short versionNumber = buf.getShort();
    long offsetToTable = buf.getLong();

    // read and validate the header checksum
    int checksum = buf.getInt();
    InputSyncTraceFileHeader header =
        new InputSyncTraceFileHeader(versionNumber, offsetToTable);
    if (header.computeChecksum() != checksum) {
      throw new TraceFileException("The sync trace file header is corrupted.");
    }
    return header;
  }

  /**
   * Gets the chunk table offset.
   * @return chunk table offset
   */
  public long getOffsetToTable() {
    return offsetToTable;
  }
}
