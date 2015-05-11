package org.jikesrvm.replay.tracefile;

import java.nio.ByteBuffer;

/**
 * The output version of a table chunk.
 */
public final class OutputTableChunk extends TableChunk {

  /**
   * Position of the next-chunk pointer, relative to the beginning of the
   * trace file.
   */
  public static final int TOTAL_NEXT_CHUNK_OFFSET = ChunkHeader.BYTES
                                                  + NEXT_CHUNK_OFFSET;

  /** Header of the chunk. */
  private OutputChunkHeader outputHeader;

  /** Constructor. */
  public OutputTableChunk() {
    super();
    outputHeader = (OutputChunkHeader)header;
  }

  /**
   * Checks whether this table chunk is the first of the trace file.
   * @return whether the table chunk is the first
   */
  public boolean isFirst() {
    return previousChunkOffset == NULL_OFFSET;
  }

  /**
   * Dumps the table chunk to a buffer.
   * @param buf buffer
   */
  public void write(ByteBuffer buf) {
    outputHeader.write(buf, this);
    buf.putLong(nextChunkOffset);
    for (int i = 0, c = 0; i < numEntries; i++) {
      buf.putLong(entries[c++]);
      buf.putLong(entries[c++]);
    }
  }

  /**
   * Creates a buffer and dumps the table chunk to it.
   * @return created buffer
   */
  public ByteBuffer write() {
    ByteBuffer buf = ByteBuffer.allocate(getTotalSize());
    write(buf);
    return buf;
  }

  /**
   * Tries to add an entry to the table chunk.
   * If the table chunk has no space for more entries, the new one will not be
   * added.
   * @param  threadId    thread replay id for the entry
   * @param  chunkOffset trace file offset for the entry
   * @return             whether the entry was successfully added to the chunk
   */
  public boolean tryAddEntry(long threadId, long chunkOffset) {
    if (numEntries >= MAX_NUM_ENTRIES) {
      return false;
    }
    entries[2 * numEntries] = threadId;
    entries[2 * numEntries + 1] = chunkOffset;
    numEntries++;
    return true;
  }
}
