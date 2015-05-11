package org.jikesrvm.replay.tracefile.ditto;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.tracefile.Chunk;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * A ditto trace file data chunk.
 * <p>
 * For each thread, the ditto trace file contains a chain of data chunks, linked
 * together in a linked-list-like manner.
 * <p>
 * The layout of a ditto data chunk is as follows:
 * <ul>
 *   <li>A chunk header of {@link ChunkHeader#BYTES} bytes, whose layout is as
 *   documented in {@link ChunkHeader};</li>
 *   <li>A pointer to the next data chunk in the thread's chain, as an absolute
 *   trace file offset of {@link #NEXT_CHUNK_BYTES} bytes;</li>
 *   <li>One or more value chains, with the following layout:
 *     <ul>
 *       <li>A mark of {@link DataChunkValue#MARK_SIZE} bytes containing the
 *       length of the value chain and the size of each value;</li>
 *       <li>A sequence of values of the mark-identified size:
 *       {@link #BYTES_IN_BYTE}, {@link #BYTES_IN_SHORT}, {@link #BYTES_IN_INT},
 *       or {@link #BYTES_IN_LONG}. The size of the values in a value chain is
 *       the smallest of these 4 options that can represent all the values in
 *       the chain.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public abstract class DittoDataChunk implements TraceFileConstants, Chunk,
                                                SizeConstants {

  /** Position of the next-chunk pointer, relative to the chunk header. */
  protected static final int NEXT_CHUNK_OFFSET = 0;

  /** Size of the next-chunk pointer, in bytes. */
  private static final int NEXT_CHUNK_BYTES = BYTES_IN_LONG;

  /** Position at which the chunk data starts, relative to the chunk header. */
  protected static final int DATA_OFFSET = NEXT_CHUNK_OFFSET + NEXT_CHUNK_BYTES;

  /** Maximum size of a data chunk. */
  public static final int BUFFER_SIZE = /*2 **/ 8192;

  /** Constructor. */
  protected DittoDataChunk() { }

  @Override
  public final int getTotalSize() {
    return ChunkHeader.BYTES + getChunkSize();
  }
}
