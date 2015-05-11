package org.jikesrvm.replay.tracefile.ditto;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jikesrvm.VM;
import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputChunkHeader;
import org.vmmagic.pragma.Inline;

/**
 * The output version of a Ditto trace file data chunk.
 * <p>
 * Each thread only needs a single instance of this class, as it is reusable
 * once full. Additionally, a data chunk can be filled by its thread while the
 * previous one is still being dumped to disk by a writer thread.
 */
public final class OutputDittoDataChunk extends DittoDataChunk {

  /** Position of the first mark. */
  private static final int INITIAL_MARK_POSITION =
      ChunkHeader.BYTES + DATA_OFFSET;

  /** Initial position of the output buffer. */
  private static final int INITIAL_BUFFER_POSITION =
      INITIAL_MARK_POSITION + DataChunkValue.MARK_SIZE;

  /** Number of most recent values to keep for space optimization purposes. */
  private static final int N_LAST_VALUES = 5;


  /**
   * Represents the possible states of a data chunk in the process of being
   * dumped to the trace file.
   */
  private enum WriteState {
    /** The data chunk has just been created and has never been dumped. */
    INITIAL,
    /** The data chunk has a buffer that is still being dumped. */
    WRITING_LAST,
    /** The data chunk's last buffer has already been dumped. */
    LAST_WRITTEN
  };

  /** Current write state of the data chunk. */
  private WriteState state;

  /** Replay id of the thread to which this data chunk belongs. */
  private long threadId;

  /** Header of the chunk. */
  private OutputChunkHeader outputHeader;


  /**
   * Buffer of the previous data chunk. When a data chunk is full, the buffer
   * in {@link #buffer} is saved here so that a writer thread may dump it to
   * the trace file while a new buffer is being filled.
   */
  private ByteBuffer lastBuffer;

  /** Current buffer of the data chunk. */
  private ByteBuffer buffer;

  /** Trace file offset of the previous data chunk in the thread's chain. */
  private long previousChunkOffset;


  /**
   * Length of the type (byte, short, int or long) currently in use to store
   * values in the data chunk.
   */
  private int curValueLen;

  /**
   * Position in {@link #buffer} of the mark corresponding to the current value
   * chain.
   */
  private int markPosition;


  /**
   * Circular array containing the most recent values of the data chunk.
   * These are kept so that the type currently in use to store values may be
   * dynamically changed for optimum space efficiency.
   */
  private long[] lastValues;

  /**
   * Circular array containing the length of the smallest type needed to store
   * the most recent values of the data chunk.
   * Has a one-to-one correspondence with the values in {@link #lastValues}.
   */
  private int[] lastValueLengths;

  /**
   * Tail of the circular arrays {@link #lastValues} and
   * {@link #lastValueLengths}.
   */
  private int tail;


  /**
   * The most recently store clock value.
   * Allows subsequent clock values to be saved as increments instead of
   * absolute values.
   */
  private long lastClockValue;

  /**
   * Constructor.
   * @param  threadId id of the thread to which the data chunk belongs
   */
  public OutputDittoDataChunk(long threadId) {
    super();
    state = WriteState.INITIAL;
    this.threadId = threadId;
    outputHeader = new OutputChunkHeader(ChunkHeader.ChunkType.DittoData);

    // initialize the buffers
    buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    buffer.position(INITIAL_BUFFER_POSITION);
    lastBuffer = null;

    // initialize the current value chain
    curValueLen = BYTES_IN_BYTE;
    markPosition = INITIAL_MARK_POSITION;

    // initialize the most-recent-values circular arrays
    lastValues = new long[N_LAST_VALUES];
    lastValueLengths = new int[N_LAST_VALUES];
    tail = -1;

    previousChunkOffset = NULL_OFFSET;
    lastClockValue = ReplayConstants.INIT_CLOCK;
  }

  /**
   * Gets the replay id of the thread to which the data chunk belongs.
   * @return thread's replay id
   */
  public long getThreadId() {
    return threadId;
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

  /**
   * Prepares the data chunk for being dumped to the trace file.
   * Because data chunk instances are reusable, this entails waiting until the
   * last buffer has been writen to disk and issuing a dump request for the
   * current buffer.
   * @param isLast whether this is the final data chunk of its thread
   */
  @Inline
  public void prepareToWrite(boolean isLast) {
    // wait until the previous buffer has been dumped and is ready for reuse
    waitForLastWrite();

    // write the last mark of the data chunk
    writeMark();

    // go to the beginning of the buffer and write the header and the, for now,
    // null pointer to the next data chunk in the thread's chain
    int chunkSize = getChunkSize();
    int pos = buffer.position();
    buffer.position(0);
    outputHeader.write(buffer, chunkSize);
    buffer.putLong(NULL_OFFSET);
    buffer.position(pos);

    // rotate buffers
    if (!isLast && lastBuffer == null) {
      lastBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    }
    ByteBuffer tmp = lastBuffer;
    lastBuffer = buffer;
    buffer = tmp;

    // change the current write state: we're now waiting for a buffer to be
    // dumped to the trace file
    state = WriteState.WRITING_LAST;
  }

  /**
   * Blocks the current thread until the write state of the data chunk is
   * {@link WriteState#LAST_WRITTEN}, meaning that the chunk is ready to be
   * reused.
   */
  private void waitForLastWrite() {
    if (state != WriteState.WRITING_LAST) {
      return;
    }

    Thread.yield();
    if (state != WriteState.WRITING_LAST) {
      return;
    }

    synchronized (this) {
      while (state == WriteState.WRITING_LAST) {
        try {
          this.wait();
          break;
        } catch (InterruptedException ie) { }
      }
    }
  }

  /**
   * Notifies the data chunk that its previous buffer has been dumped to the
   * trace file. Should be called by the writer thread.
   * @param offset trace file offset at which the previous buffer was written
   */
  public void notifyWrite(long offset) {
    // now we know where the last chunk in the chain is
    previousChunkOffset = offset;

    // change the current write state: there is no buffer waiting to be dumped
    // to the trace file. notify any threads waiting for the event
    synchronized (this) {
      state = WriteState.LAST_WRITTEN;
      this.notifyAll();
    }
  }

  /** Resets the data chunk, so that it may be reused. */
  @Inline
  public void reset() {
    buffer.clear();
    buffer.position(INITIAL_BUFFER_POSITION);
    curValueLen = BYTES_IN_BYTE;
    markPosition = INITIAL_MARK_POSITION;
    tail = -1;
  }

  /**
   * Checks whether the data chunk is empty.
   * @return whether the data chunk is empty
   */
  @Inline
  public boolean isEmpty() {
    return buffer.position() == INITIAL_BUFFER_POSITION;
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
   * Note that it is not the current buffer that is written to the trace, but
   * the last buffer ({@link #lastBuffer}).
   * @param  stream      output trace file stream
   * @return             trace file offset where the data chunk was written
   * @throws IOException if writing to the trace file fails
   */
  public long write(RandomAccessFile stream) throws IOException {
    FileChannel fc = stream.getChannel();

    // calculate the start and end offsets of the chunk
    long startOffset = fc.position();
    long endOffset = startOffset + lastBuffer.position();

    // write the chunk
    lastBuffer.flip();
    fc.write(lastBuffer);

    // go back to the previous chunk, write a pointer to the next chunk (the
    // one we just wrote), and go back to the original position
    if (previousChunkOffset != NULL_OFFSET) {
      fc.position(previousChunkOffset + ChunkHeader.BYTES + NEXT_CHUNK_OFFSET);
      stream.writeLong(startOffset);
      fc.position(endOffset);
    }

    return startOffset;
  }


  ///
  /// Value writers
  ///

  /**
   * Tries to add a logical clock value to the data chunk.
   * The value will not be added to the chunk if it does not have enough free
   * space in it.
   * @param  value logical clock value
   * @return       whether the value was successfully added to the chunk
   */
  @Inline
  public boolean tryAddClockValue(long value) {
    // clock values are stored as increments relative to the last such value
    // in the data chunk
    int valueLen;
    long inc = value - lastClockValue;
    if (inc >= 0) {
      valueLen = inc <= DataChunkValue.BYTE_MAX_VALUE  ? BYTES_IN_BYTE
               : inc <= DataChunkValue.SHORT_MAX_VALUE ? BYTES_IN_SHORT
               : inc <= DataChunkValue.INT_MAX_VALUE   ? BYTES_IN_INT
                                                       : BYTES_IN_LONG;
    } else {
      valueLen = inc >= DataChunkValue.BYTE_MIN_VALUE  ? BYTES_IN_BYTE
               : inc >= DataChunkValue.SHORT_MIN_VALUE ? BYTES_IN_SHORT
               : inc >= DataChunkValue.INT_MIN_VALUE   ? BYTES_IN_INT
                                                       : BYTES_IN_LONG;
    }

    if (tryAddValue(valueLen, inc, false)) {
      lastClockValue = value;
      return true;
    }
    return false;
  }

  /**
   * Tries to add a load count value to the data chunk.
   * The value will not be added to the chunk if it does not have enough free
   * space in it.
   * @param  value load count value
   * @return       whether the value was successfully added to the chunk
   */
  @Inline
  public boolean tryAddCountValue(long value) {
    int valueLen = value <= DataChunkValue.BYTE_MAX_VALUE  ? BYTES_IN_BYTE
                 : value <= DataChunkValue.SHORT_MAX_VALUE ? BYTES_IN_SHORT
                 : value <= DataChunkValue.INT_MAX_VALUE   ? BYTES_IN_INT
                                                           : BYTES_IN_LONG;
    return tryAddValue(valueLen, value, false);
  }

  /**
   * Tries to add a free run value to the data chunk.
   * The value will not be added to the chunk if it does not have enough free
   * space in it.
   * @param  fr free run value
   * @return    whether the value was successfully added to the chunk
   */
  @Inline
  public boolean tryAddFreeRun(long fr) {
    int valueLen = fr <= DataChunkValue.BYTE_MAX_FREE_RUN  ? BYTES_IN_BYTE
                 : fr <= DataChunkValue.SHORT_MAX_FREE_RUN ? BYTES_IN_SHORT
                 : fr <= DataChunkValue.INT_MAX_FREE_RUN   ? BYTES_IN_INT
                                                           : BYTES_IN_LONG;
    return tryAddValue(valueLen, fr, true);
  }

  /**
   * Tries to add a value to the data chunk.
   * The value will not be added to the chunk if it does not have enough free
   * space in its buffer.
   * @param  valueLen  length of the smallest type that can hold the value
   * @param  value     the value
   * @param  isFreeRun whether the value is a free run
   * @return           whether the value was successfully added to the chunk
   */
  @Inline
  private boolean tryAddValue(int valueLen, long value, boolean isFreeRun) {
    // check if the current value length can be decreased by looking at the
    // most recent values in the data chunk
    tryDecreaseValueLength();

    // if the value to add fits exactly in the current value length, go ahead
    // and add the value
    if (valueLen == curValueLen) {
      return tryAddValueAux(valueLen, value, isFreeRun);
    }

    // if the value to add does not fit in the current value length, the latter
    // must be increased
    // also, if the current length is LONG, but the value fits in a SHORT or
    // less, it always pays off to reduce the current value length right away
    if (valueLen > curValueLen
        || (curValueLen == BYTES_IN_LONG && valueLen <= BYTES_IN_SHORT)) {
      if (tryUpdateValueLength(valueLen)) {
        // add the value without checking the buffer size, because
        // tryUpdateValueLength already did so
        addValue(valueLen, value, isFreeRun);
        return true;
      }
      return false;
    }

    // otherwise, tentatively add the value using a larger value length than
    // it needs
    return tryAddValueAux(valueLen, value, isFreeRun);
  }

  /**
   * Tries to add a value to the data chunk using the current value length, even
   * if the value does not need so much space.
   * The value will not be added to the chunk if it does not have enough free
   * space in its buffer.
   * @param  valueLen  length of the smallest type that can hold the value
   * @param  value     the value
   * @param  isFreeRun whether the value is a free run
   * @return           whether the value was successfully added to the chunk
   */
  @Inline
  private boolean tryAddValueAux(int valueLen, long value, boolean isFreeRun) {
    switch (curValueLen) {
      case BYTES_IN_BYTE:
        if (buffer.remaining() >= BYTES_IN_BYTE) {
          byte byteValue = isFreeRun ? DataChunkValue.encodeByteFreeRun(value)
                                     : (byte)value;
          buffer.put(byteValue);
          pushRecentValue(valueLen, byteValue);
          return true;
        }
        return false;

      case BYTES_IN_SHORT:
        if (buffer.remaining() >= BYTES_IN_SHORT) {
          short shortValue = isFreeRun
                           ? DataChunkValue.encodeShortFreeRun(value)
                           : (short)value;
          buffer.putShort(shortValue);
          pushRecentValue(valueLen, shortValue);
          return true;
        }
        return false;

      case BYTES_IN_INT:
        if (buffer.remaining() >= BYTES_IN_INT) {
          int intValue = isFreeRun ? DataChunkValue.encodeIntFreeRun(value)
                                   : (int)value;
          buffer.putInt(intValue);
          pushRecentValue(valueLen, intValue);
          return true;
        }
        return false;

      case BYTES_IN_LONG:
        if (buffer.remaining() >= BYTES_IN_LONG) {
          long longValue = isFreeRun ? DataChunkValue.encodeLongFreeRun(value)
                                     : value;
          buffer.putLong(longValue);
          pushRecentValue(valueLen, longValue);
          return true;
        }
        return false;

      default:
        VM._assert(false);
        return false;
    }
  }

  /**
   * Adds a value to the data chunk using the current value length, even if the
   * value does not need so much space.
   * Does not check whether the current buffer has enough free space for the
   * value. The caller is responsible for performing that validation.
   * @param valueLen  length of the smallest type that can hold the value
   * @param value     the value
   * @param isFreeRun whether the value is a free run
   */
  @Inline
  private void addValue(int valueLen, long value, boolean isFreeRun) {
    switch (curValueLen) {
      case BYTES_IN_BYTE:
        byte byteValue = isFreeRun ? DataChunkValue.encodeByteFreeRun(value)
                                   : (byte)value;
        buffer.put(byteValue);
        pushRecentValue(valueLen, byteValue);
        return;

      case BYTES_IN_SHORT:
        short shortValue = isFreeRun ? DataChunkValue.encodeShortFreeRun(value)
                                     : (short)value;
        buffer.putShort(shortValue);
        pushRecentValue(valueLen, shortValue);
        return;

      case BYTES_IN_INT:
        int intValue = isFreeRun ? DataChunkValue.encodeIntFreeRun(value)
                                 : (int)value;
        buffer.putInt(intValue);
        pushRecentValue(valueLen, intValue);
        return;

      case BYTES_IN_LONG:
        long longValue = isFreeRun ? DataChunkValue.encodeLongFreeRun(value)
                                   : value;
        buffer.putLong(longValue);
        pushRecentValue(valueLen, longValue);
        return;

      default:
        VM._assert(false);
    }
  }

  /**
   * Pushes a (value, value length) pair into the circular arrays holding the
   * most recent values of the data chunk.
   * @param valueLen length of the smallest type that can hold the value
   * @param value    the value
   */
  @Inline
  private void pushRecentValue(int valueLen, long value) {
    tail++;
    lastValues[tail % N_LAST_VALUES] = value;
    lastValueLengths[tail % N_LAST_VALUES] = valueLen;
  }

  /**
   * Tries to update the data chunk's current value length.
   * The value length will not be changes if there isn't enough free space in
   * the buffer to hold a mark and at least one value.
   * @param  valueLen new value length
   * @return          whether the value length was successfully updated
   */
  @Inline
  private boolean tryUpdateValueLength(int valueLen) {
    if (buffer.remaining() > DataChunkValue.MARK_SIZE + valueLen) {
      writeMark();
      int p = buffer.position();
      markPosition = p;
      curValueLen = valueLen;
      buffer.position(p + DataChunkValue.MARK_SIZE);
      return true;
    }
    return false;
  }

  /**
   * Updates the data chunk's current value length.
   * Does not check whether the chunk has enough free space for a mark and at
   * least one value; the caller is responsible for that validation.
   * @param valueLen new value length
   */
  @Inline
  private void updateValueLength(int valueLen) {
    writeMark();
    int p = buffer.position();
    markPosition = p;
    curValueLen = valueLen;
    buffer.position(p + DataChunkValue.MARK_SIZE);
  }

  /** Writes the mark for the current value chain of the data chunk. */
  @Inline
  private void writeMark() {
    int p = buffer.position();
    int size = (p - markPosition - DataChunkValue.MARK_SIZE) / curValueLen;
    if (size > 0) {
      buffer.position(markPosition);
      buffer.putShort(DataChunkValue.makeMark(curValueLen, size));
      buffer.position(p);
    } else {
      buffer.position(markPosition);
    }
  }

  /**
   * Tries to decrease the current value length by taking into account the
   * current value length and smallest possible length for the most recent
   * values in the value chunk.
   * <p>
   * The current value length is decreased when the decrease is guaranteed to be
   * worth it, assuming that it will have to be brought back to the original
   * value length right as the next value arrives. More specifically, to
   * decrease from a type of length A to another of length B, there must be N
   * most recent values that fit in B, so that the following expression holds
   * true:
   * {@code (size(mark) + N*size(B) + size(mark) + size(A)) < (N+1)*size(A)}
   * <p>
   * The following table describes the conditions for decreasing the current
   * value length from A to B:
   * <table>
   * <tr><td>from</td><td>to</td><td>condition</td></tr>
   * <tr><td>short</td><td>byte</td><td>most recent 5 fit in byte</td></tr>
   * <tr><td>int</td><td>short</td><td>most recent 3 fit in short</td></tr>
   * <tr><td>int</td><td>byte</td><td>most recent 2 fit in byte</td></tr>
   * <tr><td>long</td><td>int</td><td>most recent 2 fit in int</td></tr>
   * </table>
   * Note that decreasing from long to either short or byte is always worth it,
   * so these two cases are handled elsewhere.
   */
  @Inline
  private void tryDecreaseValueLength() {
    switch (curValueLen) {
      case BYTES_IN_BYTE:
        return;

      case BYTES_IN_SHORT:
        // if the 5 most recent values all fit in a byte, decrease to byte
        if (tail >= 5
            && lastValueLengths[tail % N_LAST_VALUES] == BYTES_IN_BYTE
            && lastValueLengths[(tail - 1) % N_LAST_VALUES] == BYTES_IN_BYTE
            && lastValueLengths[(tail - 2) % N_LAST_VALUES] == BYTES_IN_BYTE
            && lastValueLengths[(tail - 3) % N_LAST_VALUES] == BYTES_IN_BYTE
            && lastValueLengths[(tail - 4) % N_LAST_VALUES] == BYTES_IN_BYTE) {

          buffer.position(buffer.position() - 5 * BYTES_IN_SHORT);
          updateValueLength(BYTES_IN_BYTE);
          for (int i = 4; i >= 0; i--) {
            short v = (short)lastValues[(tail - i) % N_LAST_VALUES];
            if (DataChunkValue.isShortFreeRun(v)) {
              byte nv = DataChunkValue.encodeByteFreeRun(
                  DataChunkValue.decodeShortFreeRun(v));
              lastValues[(tail - i) % N_LAST_VALUES] = nv;
              buffer.put(nv);
            } else {
              buffer.put((byte)v);
            }
          }
        }
        break;

      case BYTES_IN_INT:
        // if the 3 most recent values all fit in a short, decrease to short
        if (tail >= 3
            && lastValueLengths[(tail - 2) % N_LAST_VALUES] == BYTES_IN_SHORT
            && lastValueLengths[tail % N_LAST_VALUES] <= BYTES_IN_SHORT
            && lastValueLengths[(tail - 1) % N_LAST_VALUES] <= BYTES_IN_SHORT) {

          buffer.position(buffer.position() - 3 * BYTES_IN_INT);
          updateValueLength(BYTES_IN_SHORT);
          for (int i = 2; i >= 0; i--) {
            int v = (int)lastValues[(tail - i) % N_LAST_VALUES];
            if (DataChunkValue.isIntFreeRun(v)) {
              short nv = DataChunkValue.encodeShortFreeRun(
                  DataChunkValue.decodeIntFreeRun(v));
              lastValueLengths[(tail - i) % N_LAST_VALUES] = nv;
              buffer.putShort(nv);
            } else {
              buffer.putShort((short)v);
            }
          }
        }
        // otherwise, if the 2 most recent values all fit in a byte, decrease
        // to byte
        else if (tail >= 2
            && lastValueLengths[tail % N_LAST_VALUES] == BYTES_IN_BYTE
            && lastValueLengths[(tail - 1) % N_LAST_VALUES] == BYTES_IN_BYTE) {

          buffer.position(buffer.position() - 2 * BYTES_IN_INT);
          updateValueLength(BYTES_IN_BYTE);
          for (int i = 1; i >= 0; i--) {
            int v = (int)lastValues[(tail - i) % N_LAST_VALUES];
            if (DataChunkValue.isIntFreeRun(v)) {
              byte nv = DataChunkValue.encodeByteFreeRun(
                  DataChunkValue.decodeIntFreeRun(v));
              lastValues[(tail - i) % N_LAST_VALUES] = nv;
              buffer.put(nv);
            } else {
              buffer.put((byte)v);
            }
          }
        }
        break;

      case BYTES_IN_LONG:
        // if the 2 most recent values fit in an int, decrease to int
        if (tail >= 2
            && lastValueLengths[(tail - 1) % N_LAST_VALUES] == BYTES_IN_INT
            && lastValueLengths[tail % N_LAST_VALUES] <= BYTES_IN_INT) {

          buffer.position(buffer.position() - 2 * BYTES_IN_LONG);
          updateValueLength(BYTES_IN_INT);
          for (int i = 1; i >= 0; i--) {
            long v = lastValues[(tail - i) % N_LAST_VALUES];
            if (DataChunkValue.isLongFreeRun(v)) {
              int nv = DataChunkValue.encodeIntFreeRun(
                  DataChunkValue.decodeLongFreeRun(v));
              lastValues[(tail - i) % N_LAST_VALUES] = nv;
              buffer.putInt(nv);
            } else {
              buffer.putInt((int)v);
            }
          }
        }
        break;

      default:
        VM._assert(false);
    }
  }
}
