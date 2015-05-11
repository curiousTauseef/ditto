package org.jikesrvm.replay.tracefile.global;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Output version of the header of a global-order trace file.
 */
public final class OutputGoTraceFileHeader extends GoTraceFileHeader {

  /** Constructor. */
  public OutputGoTraceFileHeader() {
    super();
  }

  /**
   * Writes the header to a given output stream.
   * @param  stream      trace file output stream
   * @throws IOException if an I/O error occurs
   */
  public void write(DataOutputStream stream) throws IOException {
    stream.writeLong(SIGNATURE);
    stream.writeShort(versionNumber);
  }
}
