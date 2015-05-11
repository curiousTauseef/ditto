package org.jikesrvm.replay.tracefile.global;

import java.io.File;

import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * The global-order trace file.
 * <p>
 * The layout of a global-order trace file is as follows:
 * <ul>
 *   <li>An header, documented in {@link GoTraceFileHeader};</li>
 *   <li>A sequence of (thread replay id, count) tuples, identifying the order
 *   in which the threads performed their operations. The replay id identifies
 *   the thread, while the count specifies the number of consecutive operations
 *   it performed without interruption by other threads.</li>
 * </ul>
 */
public class GoTraceFile implements TraceFileConstants {

  /** Offset of the trace file header, relative to the beginning of the file. */
  protected static final int HEADER_OFFSET = 0;

  /**
   * Offset of the first (thread replay id, count) tuple in the trace file,
   * relative to the beginning of the file.
   */
  protected static final int DATA_OFFSET = HEADER_OFFSET
                                         + GoTraceFileHeader.BYTES;

  /** Trace file handle. */
  protected File file;

  /** Trace file header. */
  protected GoTraceFileHeader header;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  header trace file header
   */
  protected GoTraceFile(File file, GoTraceFileHeader header) {
    this.file = file;
    this.header = header;
  }

  /**
   * Constructor.
   * @param  file trace file handle
   */
  protected GoTraceFile(File file) {
    this.file = file;
    this.header = new OutputGoTraceFileHeader();
  }
}
