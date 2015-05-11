package org.jikesrvm.replay.tracefile.leap;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.tracefile.Chunk;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * A leap field table chunk.
 * <p>
 * A field table chunk maps each field name to the head of that field's data
 * chunk list, which contains the actual trace data for the field.
 * <p>
 * The layout of a field table chunk is as follows:
 * <ul>
 *   <li>A chunk header of {@link ChunkHeader#BYTES} bytes, whose layout is as
 *   documented in {@link ChunkHeader};</li>
 *   <li>A pointer to the next field table chunk of the trace file, as an
 *   absolute trace file offset of {@link #NEXT_CHUNK_BYTES} bytes;</li>
 *   <li>A list of field name -> offset mappings: the length of the field name
 *   as an integer of {@link #BYTES_IN_SHORT} bytes, followed by the field name,
 *   and then a pointer into the head of the field's data chunk list, as an
 *   offset of {@link #BYTES_IN_LONG} bytes.</li>
 * </ul>
 */
public abstract class LeapFieldTableChunk implements Chunk, SizeConstants,
                                                     TraceFileConstants {

  /**
   * An entry in the field table, mapping a field name to the trace file offset
   * of the field's data chunk list.
   */
  protected static class Entry {

    /** Field name. */
    public String fieldName;

    /** Trace file offset into the field's data chunk list. */
    public long offset;

    /**
     * Constructor.
     * @param  fieldName field name
     * @param  offset    trace file offset into the field's data chunk list
     */
    public Entry(String fieldName, long offset) {
      this.fieldName = fieldName;
      this.offset = offset;
    }
  }

  /** Position of the next-chunk pointer, relative to the chunk header. */
  protected static final int NEXT_CHUNK_OFFSET = 0;

  /** Size of the next-chunk pointer, in bytes. */
  protected static final int NEXT_CHUNK_BYTES = BYTES_IN_LONG;

  /** Position of the number of entries, relative to the chunk header. */
  protected static final int NUM_ENTRIES_OFFSET = NEXT_CHUNK_OFFSET
                                                + NEXT_CHUNK_BYTES;

  /** Size of the number of entries, in bytes. */
  protected static final int NUM_ENTRIES_BYTES = BYTES_IN_SHORT;

  /**
   * Position at which the field table's entries starts, relative to the chunk
   * header.
   */
  protected static final int ENTRIES_OFFSET = NUM_ENTRIES_OFFSET
                                            + NUM_ENTRIES_BYTES;

  /** Maximum number of entries per field table chunk. */
  protected static final int MAX_ENTRIES = 32;


  /** Entries in the field table chunk. */
  protected Entry[] entries;

  /** Number of entries in the field table chunk. */
  protected int numEntries;

  /** Constructor. */
  protected LeapFieldTableChunk() {
    entries = new Entry[MAX_ENTRIES];
    numEntries = 0;
  }

  @Override
  public final int getTotalSize() {
    return ChunkHeader.BYTES + getChunkSize();
  }

  @Override
  public final int getChunkSize() {
    int s = numEntries * (BYTES_IN_SHORT + BYTES_IN_LONG);
    for (int i = 0; i < numEntries; i++) {
      s += entries[i].fieldName.length();
    }
    return ENTRIES_OFFSET + s;
  }
}
