package org.jikesrvm.replay.tracefile.jarec;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputChunkHeader;

/**
 * The output version of a jarec trace file interval chunk.
 */
public final class OutputJaRecIntervalChunk extends JaRecIntervalChunk {

  /** Position of the first interval. */
  private static final int INITIAL_BUFFER_POSITION = ChunkHeader.BYTES
                                                   + DATA_OFFSET;

  /** Header of the chunk. */
  private OutputChunkHeader outputHeader;

  /** Current buffer of the data chunk. */
  private ByteBuffer buffer;

  /** Trace file offset of the previous data chunk in the thread's chain. */
  private long previousChunkOffset;

  /** Last logical clock registered in the chunk. */
  private long lastClock;

  /** Constructor. */
  public OutputJaRecIntervalChunk() {
    super();
    this.outputHeader = new OutputChunkHeader(
        ChunkHeader.ChunkType.JaRecInterval);
    buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    buffer.position(INITIAL_BUFFER_POSITION);
    previousChunkOffset = NULL_OFFSET;
    lastClock = ReplayConstants.NULL_CLOCK;
  }

  @Override
  public int getChunkSize() {
    return buffer.position() - ChunkHeader.BYTES;
  }

  @Override
  public ChunkHeader getHeader() {
    return outputHeader;
  }


  ///
  /// Trace file dump lifetime
  ///

  /** Prepares the interval chunk for being dumped to the trace file. */
  public void prepareToWrite() {
    // go to the beginnin of the buffer and write the header and the still-null
    // pointer to the next interval chunk
    int chunkSize = getChunkSize();
    int pos = buffer.position();
    buffer.position(0);
    outputHeader.write(buffer, chunkSize);
    buffer.putLong(NULL_OFFSET);
    buffer.position(pos);
  }

  /**
   * Resets the buffer, so that it may be reused.
   * @param previousChunkOffset trace file offset of the previous interval chunk
   */
  public void reset(long previousChunkOffset) {
    buffer.clear();
    buffer.position(INITIAL_BUFFER_POSITION);
    this.previousChunkOffset = previousChunkOffset;
    lastClock = ReplayConstants.NULL_CLOCK;
  }

  /**
   * Checks whether the interval chunk is empty.
   * @return whether the interval chunk is empty
   */
  public boolean isEmpty() {
    return buffer.position() == INITIAL_BUFFER_POSITION;
  }

  /**
   * Checks whether this interval chunk is the first of its thread.
   * @return whether this interval chunk is the first
   */
  public boolean isFirst() {
    return previousChunkOffset == NULL_OFFSET;
  }

  /**
   * Dumps the interval chunk to the trace file.
   * @param  stream      output trace file stream
   * @return             trace file offset where the interval chunk was written
   * @throws IOException if an I/O error occurs
   */
  public long write(RandomAccessFile stream) throws IOException {
    FileChannel fc = stream.getChannel();

    // calculate the start and end offset of the chunk
    long startOffset = fc.position();
    long endOffset = startOffset + buffer.position();

    // write the chunk
    buffer.flip();
    fc.write(buffer);

    // go back to the previous chunk, write a pointer to the next chunk (the one
    // we just wrote), and go back to the original position
    if (previousChunkOffset != NULL_OFFSET) {
      fc.position(previousChunkOffset + ChunkHeader.BYTES + NEXT_CHUNK_OFFSET);
      stream.writeLong(startOffset);
      fc.position(endOffset);
    }

    return startOffset;
  }


  ///
  /// Logical clock interval management
  ///

  /**
   * Tries to add a logical clock value to the interval chunk.
   * If the logical clock value cannot be included in the current interval and
   * there is no more space in the chunk for a new interval, the logical clock
   * will not be added.
   * @param  clock logical clock value
   * @return       whether the logical clock was successfully added to the chunk
   */
  public boolean tryAddClock(long clock) {
    if (buffer.position() > INITIAL_BUFFER_POSITION) {
      if (clock - lastClock > 1) {
        buffer.putLong(lastClock);
        if (buffer.remaining() >= 2 * SizeConstants.BYTES_IN_LONG) {
          buffer.putLong(clock);
        } else {
          return false;
        }
      }
    } else {
      buffer.putLong(clock);
    }
    lastClock = clock;
    return true;
  }

  /** Finishes the interval chunk by closing the current interval. */
  public void finish() {
    if (buffer.position() > DATA_OFFSET) {
      buffer.putLong(lastClock);
    }
  }
}
