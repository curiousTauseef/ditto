package org.jikesrvm.replay.tracefile.jarec;

import java.nio.ByteBuffer;

/**
 * Output version of he header of a jarec trace file.
 */
public final class OutputJaRecTraceFileHeader extends JaRecTraceFileHeader {

  /** Constructor. */
  public OutputJaRecTraceFileHeader() {
    super();
  }

  /**
   * Sets the offset to the chunk table.
   * @param offsetToTable chunk table offset
   */
  public void setOffsetToTable(long offsetToTable) {
    this.offsetToTable = offsetToTable;
  }

  /**
   * Writes the header o a given buffer.
   * The buffer must have at least {@link #BYTES} free bytes past its current
   * position.
   * @param buf buffer
   */
  public void write(ByteBuffer buf) {
    buf.putLong(SIGNATURE);
    buf.putShort(versionNumber);
    buf.putLong(offsetToTable);
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
