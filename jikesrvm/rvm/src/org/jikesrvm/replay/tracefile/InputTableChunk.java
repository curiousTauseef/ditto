package org.jikesrvm.replay.tracefile;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * The import version of a table chunk.
 */
public final class InputTableChunk extends TableChunk {

  /**
   * Private constructor.
   * @param header          header of the chunk
   * @param nextChunkOffset trace file offset of the next table chunk
   * @param entries         entries of the chunk
   */
  private InputTableChunk(ChunkHeader header, long nextChunkOffset,
                          long[] entries) {
    super(header, nextChunkOffset, entries);
  }

  /**
   * Creates an input table chunk by using an already-read chunk header to read
   * it from a trace file buffer.
   * @param  header chunk header
   * @param  buf     trace file buffer
   * @return        input table chunk
   */
  public static InputTableChunk read(InputChunkHeader header, ByteBuffer buf) {
    long nextChunkOffset = buf.getLong();
    int numEntries = header.getChunkSize() / ENTRY_BYTES;
    long[] entries = new long[2 * numEntries];
    for (int i = 0, c = 0; i < numEntries; i++) {
      entries[c++] = buf.getLong();
      entries[c++] = buf.getLong();
    }
    return new InputTableChunk(header, nextChunkOffset, entries);
  }

  /**
   * Fills a map with the data in the table chunk's entries.
   * @param map map to fill
   */
  public void fillMap(Map<Long, Long> map) {
    for (int i = 0; i < numEntries; i++) {
      long threadId = entries[2 * i];
      long offset = entries[2 * i + 1];
      map.put(threadId, offset);
    }
  }
}
