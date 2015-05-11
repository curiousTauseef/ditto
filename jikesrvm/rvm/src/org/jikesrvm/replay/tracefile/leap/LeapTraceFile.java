package org.jikesrvm.replay.tracefile.leap;

import java.io.File;

import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * The leap trace file.
 * <p>
 * The layout of a leap trace file is as follows:
 * <ul>
 *   <li>An header, documented in {@link LeapTraceFileHeader};</li>
 *   <li>A sequence of chunks of two types: leap data chunks
 *   ({@link LeapDataChunk}) and field table chunks
 *   ({@link LeapFieldTableChunk}). These chunks are organized in the following
 *   structure:
 *   <ul>
 *     <li>The field table chunks form a single linked-list, whose head is
 *     identified by a pointer in the trace file's header;</li>
 *     <li>The field table chunks map the names of each traced field to the head
 *     of a linked-list of data chunks that make up that field's trace.</li>
 *   </ul></li>
 * </ul>
 */
public class LeapTraceFile implements TraceFileConstants {

  /**
   * Maximum combined size of all the leap data chunks, in bytes.
   * Depending on the number of fields traced, this maximum may have to be
   * bypassed.
   */
  private static final int MAX_TOTAL_BUFFER_SIZE = 64 * 8192;

  /** Maximum size of a leap data chunk, in bytes. */
  private static final int MAX_BUFFER_SIZE = 8192;

  /** Minimum size of a leap data chunk, in bytes. */
  private static final int MIN_BUFFER_SIZE = 512;

  /** Offset of the trace file header, relative to the beginning of the file. */
  protected static final int HEADER_OFFSET = 0;

  /**
   * Offset of the first chunk in the trace file, relative to the beginning
   * of the file.
   */
  protected static final int DATA_OFFSET = HEADER_OFFSET
                                         + LeapTraceFileHeader.BYTES;

  /** Trace file handle. */
  protected File file;

  /** Trace file header. */
  protected LeapTraceFileHeader header;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  header trace file header
   */
  protected LeapTraceFile(File file, LeapTraceFileHeader header) {
    this.file = file;
    this.header = header;
  }

  /**
   * Constructor.
   * @param  file trace file handle
   */
  protected LeapTraceFile(File file) {
    this.file = file;
    this.header = new OutputLeapTraceFileHeader();
  }

  /**
   * Calculates the maximum size of a leap data chunk, depending on the number
   * of fields that need to be traced.
   * <p>The maximum size is {@link #MAX_TOTAL_BUFFER_SIZE} bytes, divided by the
   * number of fields. The result has an upper bound of {@link #MAX_BUFFER_SIZE}
   * and a lower bound of {@link #MIN_BUFFER_SIZE}.
   * @param  numFields number of fields that need to be traced
   * @return           maximum size of a leap data chunk
   */
  protected final int getBufferSize(int numFields) {
    int fieldBufferSize = MAX_TOTAL_BUFFER_SIZE / numFields;
    if (fieldBufferSize > MAX_BUFFER_SIZE) {
      return MAX_BUFFER_SIZE;
    }
    if (fieldBufferSize < MIN_BUFFER_SIZE) {
      return MIN_BUFFER_SIZE;
    }
    return fieldBufferSize;
  }
}
