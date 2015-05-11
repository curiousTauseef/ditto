package org.jikesrvm.replay.tracefile.global;

import java.io.DataInputStream;
import java.io.IOException;

import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * Input version of the header of a global-order trace file.
 */
public final class InputGoTraceFileHeader extends GoTraceFileHeader {

  /**
   * Private constructor.
   * @param  versionNumber version number of the format used in the global-order
   *                       trace file format to which the header belongs
   */
  private InputGoTraceFileHeader(short versionNumber) {
    super(versionNumber);
  }

  /**
   * Reads a global-order trace file header from a given stream.
   * @param  stream             trace file stream
   * @return                    global-order trace file header
   * @throws TraceFileException if either the signature of the trace file is
   *                            incorrect or an I/O error occurs
   */
  public static InputGoTraceFileHeader read(DataInputStream stream)
                                     throws TraceFileException {
    try {
      // read and validate the signature
      long signature = stream.readLong();
      if (signature != SIGNATURE) {
        throw new TraceFileException("Invalid signature in trace file.");
      }

      // read the version number
      short versionNumber = stream.readShort();

      return new InputGoTraceFileHeader(versionNumber);
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }
}
