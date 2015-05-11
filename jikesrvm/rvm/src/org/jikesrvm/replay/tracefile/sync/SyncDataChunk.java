package org.jikesrvm.replay.tracefile.sync;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.replay.sys.sync.SyncTraceEvent;
import org.jikesrvm.replay.tracefile.Chunk;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.OutputChunkHeader;
import org.jikesrvm.replay.tracefile.TraceFileConstants;

/**
 * A ditto trace file data chunk.
 * <p>
 * For each thread, the sync trace file contains a chain of data chunks, linked
 * together in a linked-list-like manner.
 * <p>
 * The layout of a sync data chunk is as follows:
 * <ul>
 *   <li>A chunk header of {@link ChunkHeader#BYTES} bytes, whose layout is as
 *   documented in {@link ChunkHeader};</li>
 *   <li>A pointer to the next data chunk in the thread's chain, as an absolute
 *   trace file offset of {@link #NEXT_CHUNK_BYTES} bytes;</li>
 *   <li>The number of events in the data chunk, as a {@link #BYTES_IN_SHORT}
 *   sized integer;</li>
 *   <li>A byte encoding (a) the length of the dependency events in the data
 *   chunk, left shifted by {@link #DEPENDENCY_LENGTH_SHIFT}, and (b) the
 *   length of the free run events;</li>
 *   <li>A sequence of dependency and free run events, encoded as positive and
 *   negative numbers integers, repectfully.</li>
 * </ul>
 */
public abstract class SyncDataChunk implements Chunk, TraceFileConstants,
                                               SizeConstants {

  /** Position of the next-chunk pointer, relative to the chunk header. */
  protected static final int NEXT_CHUNK_OFFSET = 0;

  /** Size of the next-chunk pointer, in bytes. */
  protected static final int NEXT_CHUNK_BYTES = BYTES_IN_LONG;

  /** Position of the number-of-events count, relative to the chunk header. */
  protected static final int NUM_EVENTS_OFFSET = NEXT_CHUNK_OFFSET
                                               + NEXT_CHUNK_BYTES;

  /** Size of the number-of-events count, in bytes. */
  protected static final int NUM_EVENTS_BYTES = BYTES_IN_SHORT;

  /** Position of the event-lengths byte, relative to the chunk header. */
  protected static final int EVENT_LENGTHS_OFFSET = NUM_EVENTS_OFFSET
                                                  + NUM_EVENTS_BYTES;

  /** Size of the event-lengths byte, in bytes. */
  protected static final int EVENT_LENGTHS_BYTES = BYTES_IN_BYTE;

  /** Position at which the chunk data starts, relative to the chunk header. */
  protected static final int DATA_OFFSET = EVENT_LENGTHS_OFFSET
                                         + EVENT_LENGTHS_BYTES;

  /**
   * Mask used to obtain each event length stored in the event-length byte,
   * after right shifting appropriately.
   */
  protected static final byte EVENT_LENGTH_MASK = 0xf;

  /** Left shift of the length of dependency events in the event-length byte. */
  protected static final int DEPENDENCY_LENGTH_SHIFT = 4;

  /** Maximum number of events per data chunk. */
  protected static final int MAX_EVENTS = 64;


  /** Header of the data chunk. */
  protected ChunkHeader header;

  /** Trace file offset of the previous data chunk in the thread's chain. */
  public long previousChunkOffset;

  /** Trace file offset of the next data chunk in the thread's chain. */
  protected long nextChunkOffset;

  /** Events stored in the data chunk. */
  protected SyncTraceEvent[] events;

  /** Total number of events in the data chunk. */
  protected int numEvents;

  /** Number of free run events in the data chunk. */
  protected int numFreeRunEvents;

  /** Number of dependency events in the data chunk. */
  protected int numDependencyEvents;

  /** Constructor. */
  protected SyncDataChunk() {
    header = new OutputChunkHeader(ChunkHeader.ChunkType.SyncData);
    previousChunkOffset = NULL_OFFSET;
    nextChunkOffset = NULL_OFFSET;
    events = new SyncTraceEvent[MAX_EVENTS];
    numEvents = 0;
  }

  /**
   * Constructor.
   * @param  header              chunk header
   * @param  nextChunkOffset     offset of the next data chunk
   * @param  events              events of the data chunk
   * @param  numFreeRunEvents    number of free run events in the data chunk
   * @param  numDependencyEvents number of dependency events in the data chunk
   */
  protected SyncDataChunk(ChunkHeader header, long nextChunkOffset,
                          SyncTraceEvent[] events, int numFreeRunEvents,
                          int numDependencyEvents) {
    this.header = header;
    this.nextChunkOffset = nextChunkOffset;
    this.events = events;
    this.numFreeRunEvents = numFreeRunEvents;
    this.numDependencyEvents = numDependencyEvents;
    numEvents = numFreeRunEvents + numDependencyEvents;
    previousChunkOffset = NULL_OFFSET;
  }

  @Override
  public final int getTotalSize() {
    return ChunkHeader.BYTES + getChunkSize();
  }

  @Override
  public final ChunkHeader getHeader() {
    return header;
  }
}
