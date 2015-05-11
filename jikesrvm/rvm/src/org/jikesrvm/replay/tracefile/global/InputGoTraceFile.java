package org.jikesrvm.replay.tracefile.global;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;

import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * Input version of the global-order trace file.
 */
public final class InputGoTraceFile extends GoTraceFile {

  /** Trace file input stream. */
  private DataInputStream stream;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file input stream
   * @param  header trace file header
   */
  private InputGoTraceFile(File file, DataInputStream stream,
                           InputGoTraceFileHeader header) {
    super(file, header);
    this.stream = stream;
  }

  /**
   * Creates an input global-order trace file by reading its header from a given
   * file.
   * @param  file               file handle
   * @param  stream             file input stream
   * @return                    an input global-order trace file instance
   * @throws TraceFileException if an I/O error occurs
   */
  public static InputGoTraceFile read(File file, DataInputStream stream)
                               throws TraceFileException {
    InputGoTraceFileHeader header = InputGoTraceFileHeader.read(stream);
    return new InputGoTraceFile(file, stream, header);
  }

  /**
   * Gets the next thread replay id in the trace file.
   * @return next thread replay id
   * @throws TraceFileException if an I/O error occurs
   * @throws EOFException       if the trace file has no more thread replay ids
   */
  public long getNextThreadId() throws TraceFileException, EOFException {
    try {
      return stream.readLong();
    } catch (EOFException eofe) {
      throw eofe;
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }

  /**
   * Gets the next operation count in the trace file.
   * @return next count
   * @throws TraceFileException if an I/O error occurs
   */
  public int getNextCount() throws TraceFileException {
    try {
      return stream.readInt();
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }
}
