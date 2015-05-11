package org.jikesrvm.replay.tracefile.leap;

import java.nio.ByteBuffer;

import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputChunkHeader;

/**
 * The output version of a leap field table chunk.
 */
public final class OutputLeapFieldTableChunk extends LeapFieldTableChunk {

  /**
   * Position of the next-chunk pointer, relative to the beginning of the
   * trace file.
   */
  public static final int TOTAL_NEXT_CHUNK_OFFSET = ChunkHeader.BYTES
                                                  + NEXT_CHUNK_OFFSET;

  /** Header of the chunk. */
  private OutputChunkHeader outputHeader;

  /** Trace file offset of the previous field table chunk. */
  public long previousChunkOffset;

  /** Constructor. */
  public OutputLeapFieldTableChunk() {
    super();
    outputHeader = new OutputChunkHeader(ChunkHeader.ChunkType.LeapFieldTable);
    previousChunkOffset = NULL_OFFSET;
  }

  @Override
  public ChunkHeader getHeader() {
    return outputHeader;
  }

  /**
   * Checks whether this field table chunk is the first of the trace file.
   * @return whether the field table chunk is the first
   */
  public boolean isFirst() {
    return previousChunkOffset == NULL_OFFSET;
  }

  /**
   * Dumps the field table chunk to a buffer.
   * @param buf buffer
   */
  public void write(ByteBuffer buf) {
    outputHeader.write(buf, this);
    buf.putLong(NULL_OFFSET);
    buf.putShort((short)numEntries);
    for (int i = 0; i < numEntries; i++) {
      Entry e = entries[i];
      buf.putShort((short)e.fieldName.length());
      buf.put(e.fieldName.getBytes());
      buf.putLong(e.offset);
    }
  }

  /**
   * Creates a buffer and dumps the field table chunk to it.
   * @return created buffer
   */
  public ByteBuffer write() {
    ByteBuffer buf = ByteBuffer.allocate(getTotalSize());
    write(buf);
    return buf;
  }

  /**
   * Tries to add an entry to the field table chunk.
   * If the field table chunk has no space for more entries, the new one will
   * not be added
   * @param  fieldName field name for the entry
   * @param  offset    trace file offset for the entry
   * @return           whether the entry was successfully added to the chunk
   */
  public boolean tryAddEntry(String fieldName, long offset) {
    if (numEntries >= MAX_ENTRIES) {
      return false;
    }
    entries[numEntries++] = new Entry(fieldName, offset);
    return true;
  }
}
