package org.jikesrvm.replay.tracefile.leap;

import java.nio.ByteBuffer;

/**
 * Output version of the header of a leap trace file.
 */
public final class OutputLeapTraceFileHeader extends LeapTraceFileHeader {

  /** Constructor. */
  public OutputLeapTraceFileHeader() {
    super();
  }

  /**
   * Sets the offset to the first field table chunk.
   * @param tablePtr field table chunk offset
   */
  public void setTablePtr(long tablePtr) {
    this.tablePtr = tablePtr;
  }

  /**
   * Writes the header to a given buffer.
   * The buffer must have at least {@link #BYTES} free bytes past its current
   * position.
   * @param buf buffer
   */
  public void write(ByteBuffer buf) {
    buf.putLong(SIGNATURE);
    buf.putShort(versionNumber);
    buf.putLong(tablePtr);
    buf.putInt(computeChecksum());
  }

  /**
   * Creates a buffer of {@link #BYTES} bytes and writes the header into it.
   * @return the created buffer
   */
  public ByteBuffer write() {
    ByteBuffer buf = ByteBuffer.allocate(BYTES);
    write(buf);
    return buf;
  }
}
