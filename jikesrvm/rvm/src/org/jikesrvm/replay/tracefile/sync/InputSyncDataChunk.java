package org.jikesrvm.replay.tracefile.sync;

import java.nio.ByteBuffer;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.VM;
import org.jikesrvm.replay.sys.sync.DependencyTraceEvent;
import org.jikesrvm.replay.sys.sync.FreeRunTraceEvent;
import org.jikesrvm.replay.sys.sync.SyncTraceEvent;
import org.jikesrvm.replay.tracefile.InputChunkHeader;

/**
 * The input version of a Sync trace file data chunk.
 */
public final class InputSyncDataChunk extends SyncDataChunk {

  /** Index of the next not-yet-read event in the data chunk. */
  private int nextEventIdx;

  /** Length of the type that free run events are encoded in. */
  private int freeRunLength;

  /** Length of the type that dependency events are encoded in. */
  private int dependencyLength;

  /**
   * Constructor.
   * @param  header              chunk header
   * @param  nextChunkOffset     offset of the next data chunk
   * @param  events              events of the data chunk
   * @param  numFreeRunEvents    number of free run events in the data chunk
   * @param  numDependencyEvents number of dependency events in the data chunk
   * @param  freeRunLength       length of the type that encodes free run events
   * @param  dependencyLength    length of the type that encodes dependency
   *                             events
   */
  private InputSyncDataChunk(InputChunkHeader header, long nextChunkOffset,
                             SyncTraceEvent[] events, int numFreeRunEvents,
                             int numDependencyEvents, int freeRunLength,
                             int dependencyLength) {
    super(header, nextChunkOffset, events, numFreeRunEvents,
          numDependencyEvents);
    nextEventIdx = 0;
    this.freeRunLength = freeRunLength;
    this.dependencyLength = dependencyLength;
  }

  @Override
  public int getChunkSize() {
    return DATA_OFFSET
        + numFreeRunEvents * freeRunLength
        + numDependencyEvents * dependencyLength;
  }

  /**
   * Reads a data chunk from a buffer.
   * @param  header chunk header
   * @param  buf    buffer
   * @return        data chunk
   */
  public static InputSyncDataChunk read(InputChunkHeader header,
                                        ByteBuffer buf) {
    long nextChunkOffset = buf.getLong();
    int numEvents = buf.getShort();
    byte eventLengths = buf.get();
    byte freeRunLength = (byte)(eventLengths & EVENT_LENGTH_MASK);
    byte dependencyLength = (byte)((eventLengths >> DEPENDENCY_LENGTH_SHIFT)
                                   & EVENT_LENGTH_MASK);
    int numFreeRunEvents = 0;
    int numDependencyEvents = 0;

    SyncTraceEvent[] events = new SyncTraceEvent[numEvents];
    for (int i = 0; i < numEvents; i++) {
      byte tmp = buf.get();
      buf.position(buf.position() - 1);
      if (tmp < 0) {
        events[i] = readFreeRunEvent(buf, freeRunLength);
        numFreeRunEvents++;
      } else {
        events[i] = readDependencyEvent(buf, dependencyLength);
        numDependencyEvents++;
      }
    }

    return new InputSyncDataChunk(header, nextChunkOffset, events,
                                  numFreeRunEvents, numDependencyEvents,
                                  freeRunLength, dependencyLength);
  }

  /**
   * Reads a free run event from a given buffer, knowing the length of the type
   * used to encode it.
   * @param  buf    buffer
   * @param  length length of the type that encodes free run events
   * @return        free run event
   */
  public static FreeRunTraceEvent readFreeRunEvent(ByteBuffer buf, int length) {
    long freeRunLength;
    switch (length) {
      case SizeConstants.BYTES_IN_BYTE:
        freeRunLength = -buf.get();
        break;
      case SizeConstants.BYTES_IN_SHORT:
        freeRunLength = -buf.getShort();
        break;
      case SizeConstants.BYTES_IN_INT:
        freeRunLength = -buf.getInt();
        break;
      case SizeConstants.BYTES_IN_LONG:
        freeRunLength = -buf.getLong();
        break;
      default:
        VM._assert(VM.NOT_REACHED);
        return null;
    }
    return new FreeRunTraceEvent(freeRunLength);
  }

  /**
   * Reads a dependency event from a given buffer, knowing the length of the
   * type used to encode it.
   * @param  buf          buffer
   * @param  length length of the type that encodes dependency events
   * @return             dependency event
   */
  public static DependencyTraceEvent readDependencyEvent(ByteBuffer buf,
                                                         int length) {
    long clock;
    switch (length) {
      case SizeConstants.BYTES_IN_BYTE:
        clock = buf.get();
        break;
      case SizeConstants.BYTES_IN_SHORT:
        clock = buf.getShort();
        break;
      case SizeConstants.BYTES_IN_INT:
        clock = buf.getInt();
        break;
      case SizeConstants.BYTES_IN_LONG:
        clock = buf.getLong();
        break;
      default:
        VM._assert(VM.NOT_REACHED);
        return null;
    }
    return new DependencyTraceEvent(clock);
  }

  /**
   * Checks whether the data chunk has any more events left to read.
   * @return whether the data chunk has more events to read
   */
  public boolean hasRemaining() {
    return nextEventIdx < numEvents;
  }

  /**
   * Reads the next not-yet-read event, marking it as read.
   * @return next event
   */
  public SyncTraceEvent getNextEvent() {
    return events[nextEventIdx++];
  }
}
