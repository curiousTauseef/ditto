package org.jikesrvm.replay.tracefile.leap;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * The output version of a leap trace file data chunk.
 */
public final class OutputLeapDataChunk extends LeapDataChunk {

  /** Initial position of the output buffer. */
  private static final int INITIAL_BUFFER_POSITION =
      ChunkHeader.BYTES + ENTRIES_OFFSET;

  /** Header of the chunk. */
  private OutputChunkHeader outputHeader;

  /** Current buffer of the data chunk. */
  private ByteBuffer buffer;

  /** Trace file offset of the previous data chunk in the field's chain. */
  private long previousChunkOffset;

  /**
   * Constructor.
   * @param  fieldName  name of the field to which the chunk belongs
   * @param  bufferSize maximum size of the chunk
   */
  public OutputLeapDataChunk(String fieldName, int bufferSize) {
    super(fieldName);
    outputHeader = new OutputChunkHeader(ChunkHeader.ChunkType.LeapData);
    buffer = ByteBuffer.allocateDirect(bufferSize);
    previousChunkOffset = TraceFileConstants.NULL_OFFSET;
    buffer.position(INITIAL_BUFFER_POSITION);
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
  /// Trace file lifetime
  ///

  /**
   * Prepares the data chunk for being dumped to the trace file.
   */
  public void prepareToWrite() {
    // write the last entry of the chunk
    if (curThreadId != ReplayConstants.NULL_THREAD_ID) {
      buffer.putLong(curThreadId);
      buffer.putInt(curTimes);
    }

    // go to the beginning of the buffer and write the header and the still-null
    // pointer to the next data chunk in the field's chain
    int chunkSize = getChunkSize();
    int pos = buffer.position();
    buffer.position(0);
    outputHeader.write(buffer, chunkSize);
    buffer.putLong(NULL_OFFSET);
    buffer.position(pos);
  }

  /**
   * Resets the data chunk, so that it may be used.
   * @param offset trace file offset of the previous data chunk in the field's
   *               chain
   */
  public void reset(long offset) {
    previousChunkOffset = offset;
    numEntries = 0;
    curThreadId = ReplayConstants.NULL_THREAD_ID;
    buffer.clear();
    buffer.position(INITIAL_BUFFER_POSITION);
  }

  /**
   * Checks whether the data chunk is empty.
   * @return whether the data chunk is empty
   */
  public boolean isEmpty() {
    return curThreadId == ReplayConstants.NULL_THREAD_ID;
  }

  /**
   * Checks whether this data chunk is the first of its thread.
   * @return whether this data chunk is the first
   */
  public boolean isFirst() {
    return previousChunkOffset == NULL_OFFSET;
  }

  /**
   * Dumps the data chunk to the trace file.
   * @param  stream      output trace file stream
   * @return             trace file offset where the data chunk was written
   * @throws IOException if an I/O error occurs
   */
  public long write(RandomAccessFile stream) throws IOException {
    FileChannel fc = stream.getChannel();
    long startOffset = fc.position();
    long endOffset;
    buffer.flip();
    fc.write(buffer);

    // go back to the previous chunk, write a pointer to the next chunk (which
    // we just wrote), and go back to the original position
    if (previousChunkOffset != NULL_OFFSET) {
      endOffset = fc.position();
      fc.position(previousChunkOffset + ChunkHeader.BYTES + NEXT_CHUNK_OFFSET);
      stream.writeLong(startOffset);
      fc.position(endOffset);
    }

    return startOffset;
  }


  ///
  /// Thread operations management
  ///

  /**
   * Tries to register an access to the field by a thread with a given relay id.
   * The access will not be added to the chunk if it requires a new entry to be
   * created, but there is no remaining space in the buffer for it.
   * @param  threadId thread replay id
   * @return          whether the access was successfully registered
   */
  public boolean tryAddAccess(long threadId) {
    // if the access is by the same thread as the previous one, increment the
    // access count
    if (curThreadId == threadId) {
      curTimes++;
      return true;
    }

    // otherwise, create a new entry, but only if there is enough space left
    // in the buffer
    if (buffer.remaining() >= ENTRY_BYTES) {
      if (curThreadId != ReplayConstants.NULL_THREAD_ID) {
        buffer.putLong(curThreadId);
        buffer.putInt(curTimes);
      }
      curThreadId = threadId;
      curTimes = 1;
      numEntries++;
      return true;
    }

    return false;
  }
}
