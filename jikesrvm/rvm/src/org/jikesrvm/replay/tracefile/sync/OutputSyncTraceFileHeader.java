package org.jikesrvm.replay.tracefile.sync;

import java.nio.ByteBuffer;

/**
 * Output version of the header of a sync trace file.
 */
public final class OutputSyncTraceFileHeader extends SyncTraceFileHeader {

  /** Constructor. */
  public OutputSyncTraceFileHeader() {
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
   * Writes the header to a given buffer.
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
