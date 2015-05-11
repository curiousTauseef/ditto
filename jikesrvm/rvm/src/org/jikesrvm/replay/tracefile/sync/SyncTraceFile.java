package org.jikesrvm.replay.tracefile.sync;

import java.io.File;
import java.io.RandomAccessFile;

import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * The sync trace file.
 * <p>
 * The layout of a sync trace file is as follows:
 * <ul>
 *   <li>An header, documented in {@link SyncTraceFileHeader};</li>
 *   <li>A sequence of chunks of two types: sync data chunks
 *   ({@link SyncDataChunk}) and table chunks
 *   ({@link org.jikesrvm.replay.tracefile.TableChunk}). These chunks are
 *   organized in the following structure:
 *   <ul>
 *     <li>The table chunks form a single linked-list, whose head is
 *     identified by a pointer in the trace file's header;</li>
 *     <li>The table chunks map the replay id of each thread to the head of a
 *     linked-list of data chunks that make up that thread's trace.</li>
 *   </ul></li>
 * </ul>
 */
public class SyncTraceFile implements TraceFileConstants {

  /** Offset of the trace file header, relative to the beginning of the file. */
  protected static final int HEADER_OFFSET = 0;

  /**
   * Offset of the first chunk in the trace file, relative to the beginning of
   * the file.
   */
  protected static final int DATA_OFFSET = HEADER_OFFSET
                                         + SyncTraceFileHeader.BYTES;

  /** Trace file handle. */
  protected File file;

  /** Trace file stream. */
  protected RandomAccessFile stream;

  /** Trace file header. */
  protected SyncTraceFileHeader header;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   * @param  header trace file header
   */
  protected SyncTraceFile(File file, RandomAccessFile stream,
                          SyncTraceFileHeader header) {
    this.file = file;
    this.stream = stream;
    this.header = header;
  }

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   */
  protected SyncTraceFile(File file, RandomAccessFile stream) {
    this.file = file;
    this.stream = stream;
    header = new OutputSyncTraceFileHeader();
  }
}
