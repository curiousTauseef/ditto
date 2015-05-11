package org.jikesrvm.replay.tracefile.sync;

import java.nio.ByteBuffer;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.VM;
import org.jikesrvm.replay.ReplayUtils;
import org.jikesrvm.replay.sys.sync.DependencyTraceEvent;
import org.jikesrvm.replay.sys.sync.FreeRunTraceEvent;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputChunkHeader;

/**
 * The output version of a Sync trace file data chunk.
 */
public final class OutputSyncDataChunk extends SyncDataChunk {

  /**
   * Position of the next-chunk pointer, relative to the beginning of the chunk.
   */
  public static final int TOTAL_NEXT_CHUNK_OFFSET = ChunkHeader.BYTES
                                                  + NEXT_CHUNK_OFFSET;

  /** Output chunk header. */
  private OutputChunkHeader outputHeader;

  /** Largest length of all free run events in the data chunk. */
  private long maxFreeRunEventLength;

  /** Largest logical clock of all dependency events in the data chunk. */
  private long maxDependencyClock;

  /** Constructor. */
  public OutputSyncDataChunk() {
    super();
    outputHeader = (OutputChunkHeader)header;
    numFreeRunEvents = 0;
    numDependencyEvents = 0;
    maxFreeRunEventLength = -1;
    maxDependencyClock = -1;
  }

  @Override
  public int getChunkSize() {
    return DATA_OFFSET
        + numFreeRunEvents * ReplayUtils.minBytesToStore(maxFreeRunEventLength)
        + numDependencyEvents * ReplayUtils.minBytesToStore(maxDependencyClock);
  }

  /**
   * Checks whether the data chunk is empty.
   * @return whether the data chunk is empty
   */
  public boolean isEmpty() {
    return numEvents == 0;
  }

  /**
   * Checks whether the data chunk is the first of its thread.
   * @return whether the data chunk is the first
   */
  public boolean isFirst() {
    return previousChunkOffset == NULL_OFFSET;
  }


  ///
  /// Event writers
  ///

  /**
   * Tries to add a free run event to the data chunk.
   * The event will not be added to the chunk if it does not have enough free
   * space in it.
   * @param  evt free run event
   * @return     whether the free run was succesfully added to the chunk
   */
  public boolean tryAddEvent(FreeRunTraceEvent evt) {
    if (numEvents >= MAX_EVENTS) {
      return false;
    }
    events[numEvents++] = evt;
    numFreeRunEvents++;
    if (evt.length > maxFreeRunEventLength) {
      maxFreeRunEventLength = evt.length;
    }
    return true;
  }

  /**
   * Tries to add a dependency event to the data chunk.
   * The event will not be added to the chunk if it does not have enough free
   * space in it.
   * @param  evt dependency event
   * @return     whether the dependency was successfully added to the chunk
   */
  public boolean tryAddEvent(DependencyTraceEvent evt) {
    if (numEvents >= MAX_EVENTS) {
      return false;
    }
    events[numEvents++] = evt;
    numDependencyEvents++;
    if (evt.clock > maxDependencyClock) {
      maxDependencyClock = evt.clock;
    }
    return true;
  }


  ///
  /// Writers
  ///

  /**
   * Writes the data chunk to a given byte buffer.
   * @param buf buffer into which to write
   */
  public void write(ByteBuffer buf) {
    // encode the event-length byte
    byte freeRunLength = ReplayUtils.minBytesToStore(maxFreeRunEventLength);
    byte dependencyLength = ReplayUtils.minBytesToStore(maxDependencyClock);
    byte eventLengths = (byte)((dependencyLength << DEPENDENCY_LENGTH_SHIFT)
                                + freeRunLength);

    // write the header
    outputHeader.write(buf, this);

    // write the next-chunk pointer, the number of events in the chunk, and
    // the event-lengths byte
    buf.putLong(nextChunkOffset);
    buf.putShort((short)numEvents);
    buf.put(eventLengths);

    // write the events themselves
    for (int i = 0; i < numEvents; i++) {
      if (events[i] instanceof FreeRunTraceEvent) {
        FreeRunTraceEvent e = (FreeRunTraceEvent)events[i];
        write(e, buf, freeRunLength);
      } else {
        DependencyTraceEvent e = (DependencyTraceEvent)events[i];
        write(e, buf, dependencyLength);
      }
    }
  }

  /**
   * Creates a byte buffer and writes the data chunk into it.
   * @return buffer
   */
  public ByteBuffer write() {
    ByteBuffer buf = ByteBuffer.allocate(getTotalSize());
    write(buf);
    return buf;
  }

  /**
   * Writes a free run event to a given buffer, encoded in a negative integer
   * of a given size.
   * @param evt    free run event
   * @param buf    buffer into which to write
   * @param length length of the type that will hold the value
   */
  private void write(FreeRunTraceEvent evt, ByteBuffer buf, int length) {
    switch (length) {
      case SizeConstants.BYTES_IN_BYTE:
        buf.put((byte)-evt.length);
        break;
      case SizeConstants.BYTES_IN_SHORT:
        buf.putShort((short)-evt.length);
        break;
      case SizeConstants.BYTES_IN_INT:
        buf.putInt((int)-evt.length);
        break;
      case SizeConstants.BYTES_IN_LONG:
        buf.putLong(-evt.length);
        break;
      default:
        VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Writes a dependency event to a given buffer, encoded in a positive integer
   * of a given size.
   * @param evt    dependency event
   * @param buf    buffer into which to write
   * @param length length of the typ that will hold the value
   */
  private void write(DependencyTraceEvent evt, ByteBuffer buf, int length) {
    switch (length) {
      case SizeConstants.BYTES_IN_BYTE:
        buf.put((byte)evt.clock);
        break;
      case SizeConstants.BYTES_IN_SHORT:
        buf.putShort((short)evt.clock);
        break;
      case SizeConstants.BYTES_IN_INT:
        buf.putInt((int)evt.clock);
        break;
      case SizeConstants.BYTES_IN_LONG:
        buf.putLong(evt.clock);
        break;
      default:
        VM._assert(VM.NOT_REACHED);
    }
  }
}
