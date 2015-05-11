package org.jikesrvm.replay.tracefile.jarec;

import java.nio.ByteBuffer;

import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * Input version of the header of a jarec trace file.
 */
public final class InputJaRecTraceFileHeader extends JaRecTraceFileHeader {

  /**
   * Private constructor.
   * @param  versionNumber version number of the format used in the jarec trace
   *                       file to which the header belongs
   * @param  offsetToTable pointer to the chunk table, as an absolute trace
   *                       file offset
   */
  private InputJaRecTraceFileHeader(short versionNumber, long offsetToTable) {
    super(versionNumber, offsetToTable);
  }

  /**
   * Reads a jarec trace file header from a given buffer.
   * The buffer must be positioned at the beginning of the header and have at
   * least {@link #BYTES} bytes past that point.
   * @param  buf                buffer
   * @return                    jarec trace file header
   * @throws TraceFileException if either the signature of the trace file or the
   *                            header's checksum are incorrect
   */
  public static InputJaRecTraceFileHeader read(ByteBuffer buf)
                                        throws TraceFileException {
    // read and validate the signature
    long magicNumber = buf.getLong();
    if (magicNumber != SIGNATURE) {
      throw new TraceFileException("Invalid signature in trace file.");
    }

    // read the version number and the chunk table pointer
    short versionNumber = buf.getShort();
    long offsetToTable = buf.getLong();

    // read and validate the header checksum
    int checksum = buf.getInt();
    InputJaRecTraceFileHeader header =
        new InputJaRecTraceFileHeader(versionNumber, offsetToTable);
    if (header.computeChecksum() != checksum) {
      throw new TraceFileException("The trace file header is corrupted.");
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
