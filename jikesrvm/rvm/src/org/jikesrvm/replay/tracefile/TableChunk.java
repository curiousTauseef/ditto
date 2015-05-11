package org.jikesrvm.replay.tracefile;

import org.jikesrvm.SizeConstants;

/**
 * A table chunk maps thread replay ids to trace file offsets pointing to that
 * thread's trace.
 * <p>
 * The layout of a table chunk is as follows:
 * <ul>
 *   <li>A chunk header of {@link ChunkHeader#BYTES} bytes, whose layout is as
 *   documented in {@link ChunkHeader};</li>
 *   <li>A pointer to the next table chunk of the trace file, as an absolute
 *   trace file offset of {@link #NEXT_CHUNK_OFFSET} bytes;</li>
 *   <li>A sequence of thread replay id -> offset entries. Each entry is
 *   made up of a thread replay id as an integer of {@link #BYTES_IN_LONG}, and
 *   a trace file offset of {@link #BYTES_IN_LONG} bytes.</li>
 * </ul>
 */
public class TableChunk implements Chunk, SizeConstants, TraceFileConstants {

  /** Position of the next-chunk pointer, relative to the chunk header. */
  protected static final int NEXT_CHUNK_OFFSET = 0;

  /** Size of the next-chunk pointer, in bytes. */
  protected static final int NEXT_CHUNK_BYTES = BYTES_IN_LONG;

  /**
   * Position of the first mapping entry of the chunk, relative to the chunk
   * header.
   */
  protected static final int ENTRIES_OFFSET = NEXT_CHUNK_OFFSET
                                            + NEXT_CHUNK_BYTES;

  /**
   * Offset of the thread replay id of an entry, relative to the beginning of
   * the entry.
   */
  protected static final int ENTRY_THREAD_ID_OFFSET = 0;

  /** Size of the thread replay id, in bytes. */
  protected static final int ENTRY_THREAD_ID_BYTES = BYTES_IN_LONG;

  /**
   * Offset of the chunk pointer of an entry, relative to the beginning of
   * the entry.
   */
  protected static final int ENTRY_CHUNK_OFFSET = ENTRY_THREAD_ID_OFFSET
                                                + ENTRY_THREAD_ID_BYTES;

  /** Size of the chunk pointer, in bytes. */
  protected static final int ENTRY_CHUNK_BYTES = BYTES_IN_LONG;

  /** Total size of each entry, in bytes. */
  protected static final int ENTRY_BYTES = ENTRY_CHUNK_OFFSET
                                         + ENTRY_CHUNK_BYTES;

  /** Maximum number of entries per table chunk. */
  public static final int MAX_NUM_ENTRIES = 32;

  /** Maximum size of a table chunk. */
  public static final int MAX_BYTES = ENTRIES_OFFSET
                                    + MAX_NUM_ENTRIES * ENTRY_BYTES;


  /** Header of the chunk. */
  protected ChunkHeader header;

  /** Trace file offset of the previous table chunk in the trace file. */
  public long previousChunkOffset;

  /** Trace file offset of the next table chunk in the trace file. */
  public long nextChunkOffset;

  /** Table entries in the chunk. */
  protected long[] entries;

  /** Number of entries in the chunk. */
  protected int numEntries;

  /** Constructor. */
  protected TableChunk() {
    header = new OutputChunkHeader(ChunkHeader.ChunkType.Table);
    previousChunkOffset = NULL_OFFSET;
    nextChunkOffset = NULL_OFFSET;
    entries = new long[2 * MAX_NUM_ENTRIES];
    numEntries = 0;
  }

  /**
   * Constructor.
   * @param header          header of the chunk
   * @param nextChunkOffset trace file offset of the next table chunk
   * @param entries         table entries of the chunk
   */
  protected TableChunk(ChunkHeader header, long nextChunkOffset,
                       long[] entries) {
    this.header = header;
    this.nextChunkOffset = nextChunkOffset;
    this.entries = entries;
    numEntries = entries.length / 2;
  }

  @Override
  public final int getTotalSize() {
    return ChunkHeader.BYTES + getChunkSize();
  }

  @Override
  public final int getChunkSize() {
    return ENTRIES_OFFSET + (numEntries * ENTRY_BYTES);
  }

  @Override
  public final ChunkHeader getHeader() {
    return header;
  }
}
