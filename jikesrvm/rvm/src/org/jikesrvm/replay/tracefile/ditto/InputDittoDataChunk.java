package org.jikesrvm.replay.tracefile.ditto;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.VM;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;

/**
 * The input version of a Ditto trace file data chunk.
 * <p>
 * Each thread only needs a single instance of this class, as it is reusable.
 */
public class InputDittoDataChunk extends DittoDataChunk {

  /** Header of the chunk. */
  private InputChunkHeader inputHeader;

  /** Trace file offset of the next data chunk in the thread's chain. */
  private long nextChunkOffset = NULL_OFFSET;

  /** Buffer containing the actual binary data of the chunk. */
  private ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);

  /**
   * Length of the values (byte, short, int or long) in the current value chain,
   * as specified by the previous mark.
   */
  private int curValueLen;

  /**
   * Number of values left in the current value chain, as specified by the
   * previous mark.
   */
  private int numValues = 0;

  /** Whether the last value read from the data chunk was a free run. */
  private boolean lastWasFreeRun = false;

  /** Last logical clock value read from the data chunk. */
  private long lastClockValue = ReplayConstants.INIT_CLOCK;

  /** Constructor. */
  public InputDittoDataChunk() {
    super();
    inputHeader = new InputChunkHeader(ChunkHeader.ChunkType.DittoData);
  }

  @Override
  public final int getChunkSize() {
    return inputHeader.getChunkSize();
  }

  @Override
  public final ChunkHeader getHeader() {
    return inputHeader;
  }

  /**
   * Reads a data chunk from the trace file at a given offset.
   * <p>
   * Note that this method only fills the buffer of the data chunk. The caller
   * should always call {@link #init} afterwards, to ensure that the data chunk
   * is ready for use. This allows the caller to minimize the critical section
   * required to synchronize the various readers of the trace file.
   * @param  stream      input trace file stream
   * @param  offset      trace file offset from which to read the data chunk
   * @throws IOException if there is an error while reading the trace file
   */
  public final void read(RandomAccessFile stream, long offset)
                  throws IOException {
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
   * should always call {@link #init} afterwards, to ensure that the data chunk
   * is ready for use. This allows the caller to minimize the critical section
   * required to synchronize the various readers of the trace file.
   * @param  stream      input trace file stream
   * @throws IOException if there is an error while reading the trace file
   */
  public final void readNext(RandomAccessFile stream) throws IOException {
    read(stream, nextChunkOffset);
  }

  /** Reads the next mark of the data chunk. */
  private void readMark() {
    short mark = buffer.getShort();
    curValueLen = DataChunkValue.getMarkValueLength(mark);
    numValues = DataChunkValue.getMarkSize(mark);
    if (VM.VerifyAssertions) {
      VM._assert(numValues > 0, "mark = " + mark + " for t#"
          + RVMThread.getCurrentThread().replayId);
    }
  }

  /**
   * Initializes the input data chunk for use after its buffer has been filled
   * with data from the trace file.
   * @throws TraceFileException if the chunk header is invalid
   */
  public final void init() throws TraceFileException {
    inputHeader.update(buffer);
    nextChunkOffset = buffer.getLong();
    lastWasFreeRun = false;
    readMark();
  }

  /**
   * Checks whether the data chunk is the last in its thread's chain.
   * @return whether the data chunk is the last
   */
  public boolean isLast() {
    return nextChunkOffset == NULL_OFFSET;
  }

  /**
   * Checks whether the data chunk has any more values left to read.
   * @return whether the data chunk has more values to read
   */
  public boolean hasRemaining() {
    return buffer.position() < ChunkHeader.BYTES + inputHeader.getChunkSize();
  }

  /**
   * Attempts to read a logical clock value from the data chunk.
   * <p>
   * If the next value is a free run, its length is returned instead. This
   * scenario may be identified by calling {@link #wasLastValueFreeRun}, which
   * will return true.
   * @return next value in the data chunk, as either a logical clock or a
   *         free run
   */
  public final long getNextClockValue() {
    // if there are no more values in the current value chain, read the next
    // mark
    if (numValues == 0) {
      readMark();
    }

    // read the next value in the data chunk
    // if the value is a free run, return it, but also set the lastWasFreeRun
    // flag. otherwise, calculate the logical clock value to return by
    // combining the value read from the data chunk and the value of the
    // last logical clock read from the data chunk
    numValues--;
    switch (curValueLen) {
      case BYTES_IN_BYTE:
        byte b = buffer.get();
        lastWasFreeRun = DataChunkValue.isByteFreeRun(b);
        if (lastWasFreeRun) {
          return DataChunkValue.decodeByteFreeRun(b);
        } else {
          lastClockValue += b;
          return lastClockValue;
        }

      case BYTES_IN_SHORT:
        short s = buffer.getShort();
        lastWasFreeRun = DataChunkValue.isShortFreeRun(s);
        if (lastWasFreeRun) {
          return DataChunkValue.decodeShortFreeRun(s);
        } else {
          lastClockValue += s;
          return lastClockValue;
        }

      case BYTES_IN_INT:
        int i = buffer.getInt();
        lastWasFreeRun = DataChunkValue.isIntFreeRun(i);
        if (lastWasFreeRun) {
          return DataChunkValue.decodeIntFreeRun(i);
        } else {
          lastClockValue += i;
          return lastClockValue;
        }

      case BYTES_IN_LONG:
        long l = buffer.getLong();
        lastWasFreeRun = DataChunkValue.isLongFreeRun(l);
        if (lastWasFreeRun) {
          return DataChunkValue.decodeLongFreeRun(l);
        } else {
          lastClockValue += l;
          return lastClockValue;
        }

      default:
        VM._assert(VM.NOT_REACHED);
        return 0;
    }
  }

  /**
   * Attempts to read a load count value from the data chunk.
   * @return next value in the data chunk, as a load count
   */
  public final long getNextCountValue() {
    // if there are no more values in the current value chain, read the next
    // mark
    if (numValues == 0) {
      readMark();
    }

    // read the next value in the data chunk
    // note that a load count value is always preceeded by a logical clock
    // value, so there is no chance of there being a free run value in its
    // place
    numValues--;
    switch (curValueLen) {
      case BYTES_IN_BYTE:
        byte b = buffer.get();
        lastWasFreeRun = false;
        return b;

      case BYTES_IN_SHORT:
        short s = buffer.getShort();
        lastWasFreeRun = false;
        return s;

      case BYTES_IN_INT:
        int i = buffer.getInt();
        lastWasFreeRun = false;
        return i;

      case BYTES_IN_LONG:
        long l = buffer.getLong();
        lastWasFreeRun = false;
        return l;

      default:
        VM._assert(VM.NOT_REACHED);
        return 0;
    }
  }

  /**
   * Checks whether the last value read from the data chunk was a free run.
   * @return whether the last value was a free run
   */
  @Inline
  public final boolean wasLastValueFreeRun() {
    return lastWasFreeRun;
  }


  ///
  /// Null input data chunk
  ///

  /** Singleton instance of a null input ditto data chunk. */
  private static InputDittoDataChunk nullInstance = null;

  /**
   * Dummy constructor used to create the null instance ({@link #nullInstance}).
   * @param  dummy dummy
   */
  private InputDittoDataChunk(boolean dummy) { }

  /**
   * Gets an input ditto data chunk that is null, i.e, is the last in its chain
   * and has no remaining values. Such a null chunk can be used for threads
   * that did not generate any trace during the recording phase.
   * @return null input ditto data chunk
   */
  public static InputDittoDataChunk nullInstance() {
    if (nullInstance != null) {
      return nullInstance;
    }

    // create the singleton instance
    synchronized (InputDittoDataChunk.class) {
      if (nullInstance == null) {
        nullInstance = new InputDittoDataChunk(false) {
          @Override
          public boolean isLast() {
            return true;
          }
          @Override
          public boolean hasRemaining() {
            return false;
          }
        };
      }
    }
    return nullInstance;
  }
}
