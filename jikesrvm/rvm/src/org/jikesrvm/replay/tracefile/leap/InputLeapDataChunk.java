package org.jikesrvm.replay.tracefile.leap;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * The input version of a leap trace file data chunk.
 */
public final class InputLeapDataChunk extends LeapDataChunk {

  /** Header of the chunk. */
  private InputChunkHeader inputHeader;

  /** Trace file offset of the next data chunk in the field's chain. */
  public long nextChunkOffset;

  /** Buffer containing the actual binary data of the chunk. */
  private ByteBuffer buffer;

  /**
   * Constructor.
   * @param  fieldName  name of the field to which the chunk belongs
   * @param  bufferSize maximum size of the chunk
   */
  public InputLeapDataChunk(String fieldName, int bufferSize) {
    super(fieldName);
    inputHeader = new InputChunkHeader(ChunkHeader.ChunkType.LeapData);
    buffer = ByteBuffer.allocateDirect(bufferSize);
    nextChunkOffset = NULL_OFFSET;
  }

  @Override
  public int getChunkSize() {
    return inputHeader.getChunkSize();
  }

  @Override
  public ChunkHeader getHeader() {
    return inputHeader;
  }

  /**
   * Reads a data chunk from the trace file at a given offset.
   * <p>
   * Note that this method only fills the buffer of the data chunk. The caller
   * should always call {@link #init} afterwards.
   * @param  stream      input trace file stream
   * @param  offset      trace file offset from which to read the data chunk
   * @throws IOException if an I/O occurs
   */
  public void read(RandomAccessFile stream, long offset) throws IOException {
    FileChannel fc = stream.getChannel();
    fc.position(offset);
    buffer.clear();
    fc.read(buffer);
    buffer.flip();
  }

  /**
   * Reads the next data chunk from the trace file, using the current data
   * chunk's forward pointer.
   * <p>
   * Note that this method only fills the buffer of the data chunk. The caller
   * should always call {@link #init} afterwards.
   * @param  stream      input trace file stream
   * @throws IOException if an I/O occurs
   */
  public void readNext(RandomAccessFile stream) throws IOException {
    read(stream, nextChunkOffset);
  }

  /**
   * Initializes the input data chunk for use after its buffer has been filled
   * with data from the trace file.
   * @throws TraceFileException if the chunk header is invalid
   */
  public void init() throws TraceFileException {
    inputHeader.update(buffer);
    nextChunkOffset = buffer.getLong();
    curThreadId = ReplayConstants.NULL_THREAD_ID;
    curTimes = 0;
  }

  /**
   * Checks whether the data chunk is the last in its field's cahin.
   * @return whether the data chunk is the last
   */
  public boolean isLast() {
    return nextChunkOffset == NULL_OFFSET;
  }

  /**
   * Checks whether the data chunk has any more entries left to read.
   * @return whether the data chunk has more entries
   */
  public boolean hasRemaining() {
    return curTimes > 0
        || buffer.position() < ChunkHeader.BYTES + inputHeader.getChunkSize();
  }

  /**
   * Reads the next entry in the data chunk.
   */
  public void advance() {
    curThreadId = buffer.getLong();
    curTimes = buffer.getInt();
  }
}
