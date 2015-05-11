package org.jikesrvm.replay.tracefile.leap;

import java.nio.ByteBuffer;

import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * Input version of the header of a leap trace file.
 */
public final class InputLeapTraceFileHeader extends LeapTraceFileHeader {

  /**
   * Constructor.
   * @param  versionNumber version number of the format used in the leap trace
   *                       file to which the header belongs
   * @param  tablePtr      pointer to the first field table chunk, as an
   *                       absolute trace file offset
   */
  private InputLeapTraceFileHeader(short versionNumber, long tablePtr) {
    super(versionNumber, tablePtr);
  }

  /**
   * Reads a leap trace file header from a given buffer.
   * The buffer must be positioned at the beginning of the header and have at
   * least {@link #BYTES} bytes past that point.
   * @param  buf                buffer
   * @return                    leap trace file header
   * @throws TraceFileException if either the signature of the trace file or the
   *                            header's checksum are incorrect
   */
  public static InputLeapTraceFileHeader read(ByteBuffer buf)
                                       throws TraceFileException {
    // read and validate the signature
    long signature = buf.getLong();
    if (signature != SIGNATURE) {
      throw new TraceFileException("Invalid signature in trace file.");
    }

    // read the version number and the field table pointer
    short versionNumber = buf.getShort();
    long tablePtr = buf.getLong();

    // read and validate the header checksum
    int checksum = buf.getInt();
    InputLeapTraceFileHeader header =
        new InputLeapTraceFileHeader(versionNumber, tablePtr);
    if (header.computeChecksum() != checksum) {
      throw new TraceFileException("The trace file header is corrupted.");
    }
    return header;
  }

  /**
   * Gets the offset to the first field table chunk.
   * @return field table chunk offset
   */
  public long getTablePtr() {
    return tablePtr;
  }
}
