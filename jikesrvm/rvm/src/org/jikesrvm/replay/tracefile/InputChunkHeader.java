package org.jikesrvm.replay.tracefile;

import java.nio.ByteBuffer;

/**
 * Input version of the header of a chunk.
 */
public final class InputChunkHeader extends ChunkHeader {

  /**
   * Constructor.
   * @param  chunkType type of the chunk
   */
  public InputChunkHeader(ChunkType chunkType) {
    super(chunkType);
  }

  /**
   * Fills the header with data read from a trace file buffer.
   * @param  buf                buffer
   * @throws TraceFileException if the chunk type is invalid
   */
  public void update(ByteBuffer buf) throws TraceFileException {
    chunkType = ChunkType.fromByte(buf.get());
    if (chunkType == null) {
      throw new TraceFileException("Unknown chunk type");
    }
    chunkSize = buf.getInt();
  }

  /**
   * Gets the size of the chunk.
   * @return size of the chunk
   */
  public int getChunkSize() {
    return chunkSize;
  }
}
