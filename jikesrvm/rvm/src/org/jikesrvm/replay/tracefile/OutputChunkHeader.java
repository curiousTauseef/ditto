package org.jikesrvm.replay.tracefile;

import java.nio.ByteBuffer;

/**
 * Output version of the header of a chunk.
 */
public final class OutputChunkHeader extends ChunkHeader {

  /**
   * Constructor.
   * @param  chunkType type of the chunk
   */
  public OutputChunkHeader(ChunkType chunkType) {
    super(chunkType);
  }

  /**
   * Writes the header to a given buffer.
   * @param buf    buffer
   * @param chunk chunk to which the header belongs
   */
  public void write(ByteBuffer buf, Chunk chunk) {
    chunkSize = chunk.getChunkSize();
    buf.put(chunkType.asByte());
    buf.putInt(chunkSize);
  }

  /**
   * Writes the header to a given buffer.
   * @param buf       buffer
   * @param chunkSize size of the chunk to which the header belongs
   */
  public void write(ByteBuffer buf, int chunkSize) {
    buf.put(chunkType.asByte());
    buf.putInt(chunkSize);
  }
}
