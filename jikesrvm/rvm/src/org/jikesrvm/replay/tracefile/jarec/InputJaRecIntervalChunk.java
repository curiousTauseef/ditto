package org.jikesrvm.replay.tracefile.jarec;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * The input version of a jarec trace file interval chunk.
 */
public class InputJaRecIntervalChunk extends JaRecIntervalChunk {

  /** Header of the chunk. */
  private InputChunkHeader inputHeader;

  /** Trace file offset of the next interval chunk in the thread's chain. */
  private long nextChunkOffset;

  /** Buffer containing the actual binary data of the chunk. */
  private ByteBuffer buffer;

  /** First logical clock of the current interval. */
  private long currentStartClock;

  /** Last logical clock of the current interval. */
  private long currentEndClock;

  /** Constructor. */
  public InputJaRecIntervalChunk() {
    super();
    inputHeader = new InputChunkHeader(ChunkHeader.ChunkType.JaRecInterval);
    nextChunkOffset = NULL_OFFSET;
    buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
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
   * Reads an interval chunk from the trace file at a given offset.
   * <p>
   * Note that this method only fills the buffer of the interval chunk. The
   * caller should always call {@link #init} afterwards, to ensure that the
   * interval chunk is ready for use.
   * @param  stream      input trace file stream
   * @param  offset      trace file offset from which to read the interval chunk
   * @throws IOException if an I/O error occurs
   */
  public void read(RandomAccessFile stream, long offset) throws IOException {
    FileChannel fc = stream.getChannel();
    fc.position(offset);
    buffer.clear();
    fc.read(buffer);
    buffer.flip();
  }

  /**
   * Reads the next interval chunk from the trace file, using the current
   * interval chunk's forward pointer.
   * <p>
   * Note that this method only fills the buffer of the interval chunk. The
   * caller should always call {@link #init} afterwards, to ensure that the
   * interval chunk is ready for use.
   * @param  stream      input trace file stream
   * @throws IOException if an I/O error occurs
   */
  public void readNext(RandomAccessFile stream) throws IOException {
    read(stream, nextChunkOffset);
  }

  /**
   * Initializes the input interval chunk for use after its buffer has been
   * filled with data from the trace file.
   * @throws TraceFileException if the chunk header is invalid
   */
  public void init() throws TraceFileException {
    inputHeader.update(buffer);
    nextChunkOffset = buffer.getLong();
  }

  /**
   * Checks whether the interval chunk is the last in its thread's chain.
   * @return whether the interval chunk is the last
   */
  public boolean isLast() {
    return nextChunkOffset == NULL_OFFSET;
  }

  /**
   * Checks whether the interval chunk is empty.
   * @return whether the interval chunk is empty
   */
  public boolean isEmpty() {
    return false;
  }

  /**
   * Checks whether the interval chunk has any more intervals left to read.
   * @param  currentClock last logical clock obtained from the interval chunk
   * @return              whether the interval hcunk has more intervals to read
   */
  public boolean hasRemaining(long currentClock) {
    return (currentClock >= currentStartClock && currentClock < currentEndClock)
        || (buffer.position() < ChunkHeader.BYTES + inputHeader.getChunkSize());
  }

  /**
   * Gets the first logical clock of the first interval in the interval chunk.
   * The caller must make sure that this method is called right after the
   * interval chunk has been read from the trace file.
   * @return first logical clock of the interval chunk
   */
  public long getFirstClock() {
    currentStartClock = buffer.getLong();
    currentEndClock = buffer.getLong();
    return currentStartClock;
  }

  /**
   * Gets the next logical clock of the interval chunk, either by advancing
   * inside the current interval or by reading a new interval and getting its
   * first clock.
   * @param  currentClock last logical clock value read from the interval chunk
   * @return              next logical clock in the interval chunk
   */
  public long getNextClockValue(long currentClock) {
    // if the interval is not yet exhausted
    if (currentClock < currentEndClock) {
      return currentClock + 1;
    }

    // a new interval is needed
    currentStartClock = buffer.getLong();
    currentEndClock = buffer.getLong();
    return currentStartClock;
  }


  ///
  /// Null input interval chunk
  ///

  /** Singleton instance of a null input jarec interval chunk. */
  private static InputJaRecIntervalChunk nullInstance = null;

  /**
   * Dummy constructor used to create the null instance ({@link #nullInstance}).
   * @param  dummy dummy
   */
  private InputJaRecIntervalChunk(boolean dummy) { }

  /**
   * Gets an input jarec interval data chunk that is null, i.e, is the last in
   * its chain and has no remaining intervals. Such a null chunk can be used for
   * threads that did not generate any trace during the recording phase.
   * @return input jarec interval chunk
   */
  public static InputJaRecIntervalChunk nullInstance() {
    if (nullInstance != null) {
      return nullInstance;
    }

    // create the singleton instance
    synchronized (InputJaRecIntervalChunk.class) {
      if (nullInstance == null) {
        nullInstance = new InputJaRecIntervalChunk(false) {
          @Override
          public boolean isLast() {
            return true;
          }
          @Override
          public boolean hasRemaining(long currentClock) {
            return false;
          }
          @Override
          public boolean isEmpty() {
            return true;
          }
        };
      }
    }
    return nullInstance;
  }
}
