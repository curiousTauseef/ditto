package org.jikesrvm.replay.tracefile.ditto;

import java.io.File;
import java.io.RandomAccessFile;

import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * The ditto trace file.
 * <p>
 * The layout of a ditto trace file is as follows:
 * <ul>
 *   <li>An header, documented in {@link DittoTraceFileHeader};</li>
 *   <li>A sequence of chunks of two types: ditto data chunks
 *   ({@link DittoDataChunk}) and table chunks
 *   ({@link org.jikesrvm.replay.tracefile.TableChunk}). These
 *   chunks are organized in the following structure:
 *   <ul>
 *     <li>The table chunks form a single linked-list, whose head is
 *     identified by a pointer in the trace file's header;</li>
 *     <li>The table chunks map the replay id of each thread to the head of a
 *     linked-list of data chunks that make up that thread's trace.</li>
 *   </ul></li>
 * </ul>
 */
public class DittoTraceFile implements TraceFileConstants {

  /** Offset of the trace file header, relative to the beginning of the file. */
  protected static final int HEADER_OFFSET = 0;

  /**
   * Offset of the first chunk in the trace file, relative to the beginning
   * of the file.
   */
  protected static final int DATA_OFFSET = HEADER_OFFSET
                                         + DittoTraceFileHeader.BYTES;

  /** Trace file handle. */
  protected File file;

  /** Trace file stream. */
  protected RandomAccessFile stream;

  /** Trace file header. */
  protected DittoTraceFileHeader header;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   * @param  header trace file header
   */
  protected DittoTraceFile(File file, RandomAccessFile stream,
                           DittoTraceFileHeader header) {
    this.file = file;
    this.stream = stream;
    this.header = header;
  }

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   */
  protected DittoTraceFile(File file, RandomAccessFile stream) {
    this.file = file;
    this.stream = stream;
    this.header = new OutputDittoTraceFileHeader();
  }
}
