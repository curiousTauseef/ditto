package org.jikesrvm.replay.tracefile.jarec;

import java.io.File;
import java.io.RandomAccessFile;

import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * The jarec trace file.
 * <p>
 * The layout of a jarec trace file is as follows:
 * <ul>
 *   <li>An header, documented in {@link JaRecTraceFileHeader};</li>
 *   <li>A sequence of chunks of two types: jarec interval chunks
 *   ({@link JaRecIntervalChunk}) and table chunks
 *   ({@link org.jikesrvm.replay.tracefile.TableChunk}). These chunks are
 *   organized in the following structure:
 *   <ul>
 *     <li>The table chunks form a single linked-list, whose head is
 *     identified by a pointer in the trace file's header;</li>
 *     <li>The table chunks map the replay id of each thread to the head of a
 *     linked-list of interval chunks that make up that thread's trace.</li>
 *   </ul></li>
 * </ul>
 */
public class JaRecTraceFile implements TraceFileConstants {

  /** Offset of the trace file header, relative to the beginning of the file. */
  protected static final int HEADER_OFFSET = 0;

  /**
   * Offset of the first chunk in the trace file, relative to the beginning of
   * the file.
   */
  protected static final int DATA_OFFSET = HEADER_OFFSET
                                         + JaRecTraceFileHeader.BYTES;

  /** Trace file handle. */
  protected File file;

  /** Trace file stream. */
  protected RandomAccessFile stream;

  /** Trace file header. */
  protected JaRecTraceFileHeader header;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   * @param  header trace file header
   */
  protected JaRecTraceFile(File file, RandomAccessFile stream,
                           JaRecTraceFileHeader header) {
    this.file = file;
    this.stream = stream;
    this.header = header;
  }

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   */
  protected JaRecTraceFile(File file, RandomAccessFile stream) {
    this.file = file;
    this.stream = stream;
    this.header = new OutputJaRecTraceFileHeader();
  }
}
