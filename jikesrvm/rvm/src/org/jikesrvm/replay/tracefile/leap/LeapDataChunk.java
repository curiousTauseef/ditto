package org.jikesrvm.replay.tracefile.leap;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.Chunk;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * A leap trace file data chunk.
 * <p>
 * For each traced field, the leap trace file contains a chain of data chunks,
 * linked together in a linked-list-like manner.
 * <p>
 * The layout of a leap data chunk is as follows:
 * <ul>
 *   <li>A chunk header of {@link ChunkHeader#BYTES} bytes, whose layout is as
 *   documented in {@link ChunkHeader};</li>
 *   <li>A pionter to the next data chunk in the fields's chain, as an absolute
 *   trace file offset of {@link #NEXT_CHUNK_BYTES} bytes;</li>
 *   <li>A sequence of entries, each with a thread replay id as an integer
 *   of {@link #BYTES_IN_LONG} bytes, and a consecutive access count as an
 *   integer of {@link #BYTES_IN_INT} bytes. Each pair represents a sequence of
 *   consecutive accesses to the field by the same thread.</li>
 * </ul>
 */
public abstract class LeapDataChunk implements Chunk, SizeConstants,
                                               TraceFileConstants {

  /** Position of the next-chunk pointer, relative to the chunk header. */
  protected static final int NEXT_CHUNK_OFFSET = 0;

  /** Size of the next-chunk pointer, in bytes. */
  protected static final int NEXT_CHUNK_BYTES = BYTES_IN_LONG;

  /** Position at which the entries start, relative to the chunk header. */
  protected static final int ENTRIES_OFFSET = NEXT_CHUNK_OFFSET
                                            + NEXT_CHUNK_BYTES;

  /** Size of a single entry, in bytes. */
  protected static final int ENTRY_BYTES = BYTES_IN_LONG + BYTES_IN_INT;


  /** Name of the field to which the data chunk belongs. */
  protected String fieldName;

  /** Replay id of the last thread to register an operation in the chunk. */
  protected long curThreadId;

  /**
   * Number of consecutive operations registered in the chunk by the thread
   * with replay id {@link #curThreadId}.
   */
  protected int curTimes;

  /** Number of entries in the chunk. */
  protected int numEntries;

  /**
   * Constructor.
   * @param  fieldName name of the field to which the chunk belongs
   */
  protected LeapDataChunk(String fieldName) {
    this.fieldName = fieldName;
    curThreadId = ReplayConstants.NULL_THREAD_ID;
    numEntries = 0;
  }

  @Override
  public final int getTotalSize() {
    return ChunkHeader.BYTES + getChunkSize();
  }

  /**
   * Gets the name of the field to which the chunk belongs.
   * @return name of the field
   */
  public final String getFieldName() {
    return fieldName;
  }
}
