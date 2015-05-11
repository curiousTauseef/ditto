package org.jikesrvm.replay.tracefile.jarec;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.tracefile.Chunk;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * A jarec trace file interval chunk.
 * <p>
 * For each thread, the jarec ditto trace file contains a chain of interval
 * chunks, linked together in a linked-list-like manner.
 * <p>
 * The layout of a jarec interval chunk is as follows:
 * <ul>
 *   <li>A chunk header of {@link ChunkHeader#BYTES} bytes, whose layout is as
 *   documents in {@link ChunkHeader};</li>
 *   <li>A pointer to the next interval chunk in the thread's cahin, as an
 *   absolute trace file offset of {@link #NEXT_CHUNK_BYTES} bytes;</li>
 *   <li>A sequence of intervals. Each interval is characterized by its first
 *   logical clock and its last logical clock, each stored as an integer of
 *   {@link #BYTES_IN_LONG} bytes;</li>
 * </ul>
 */
public abstract class JaRecIntervalChunk implements TraceFileConstants, Chunk,
                                                    SizeConstants {

  /** Position of the next-chunk pointer, relative to the chunk header. */
  protected static final int NEXT_CHUNK_OFFSET = 0;

  /** Size of the next-chunk pointer, in bytes. */
  private static final int NEXT_CHUNK_BYTES = BYTES_IN_LONG;

  /** Position at which the chunk data starts, relative to the chunk header. */
  protected static final int DATA_OFFSET = NEXT_CHUNK_OFFSET + NEXT_CHUNK_BYTES;

  /** Maximum size of an interval chunk. */
  public static final int BUFFER_SIZE = 8192;

  /** Constructor. */
  protected JaRecIntervalChunk() { }

  @Override
  public final int getTotalSize() {
    return ChunkHeader.BYTES + getChunkSize();
  }
}
