package org.jikesrvm.replay.tracefile;

import org.jikesrvm.SizeConstants;

/**
 * A chunk header.
 * <p>
 * The layout of a chunk header is as follows:
 * <ul>
 *   <li>The type of the chunk, which can be one of the enum values defined
 *   by {@link ChunkType}, as a byte;</li>
 *   <li>The size of the chunk, in bytes, as an integer of
 *   {@link org.jikesrvm.SizeConstants#BYTES_IN_INT} bytes.</li>
 * </ul>
 */
public class ChunkHeader {

  /** Possible types of a chunk. */
  public static enum ChunkType {
    /** Chunk table chunk ({@link TableChunk}). */
    Table((byte)1),

    /**
     * Sync data chunk
     * ({@link org.jikesrvm.replay.tracefile.sync.SyncDataChunk}).
     */
    SyncData((byte)2),

    /**
     * Ditto data chunk
     * ({@link org.jikesrvm.replay.tracefile.ditto.DittoDataChunk}).
     */
    DittoData((byte)3),

    /**
     * JaRec interval chunk
     * ({@link org.jikesrvm.replay.tracefile.jarec.JaRecIntervalChunk}).
     */
    JaRecInterval((byte)4),

    /**
     * Leap data chunk
     * ({@link org.jikesrvm.replay.tracefile.leap.LeapDataChunk}).
     */
    LeapData((byte)5),

    /**
     * Leap field table chunk
     * ({@link org.jikesrvm.replay.tracefile.leap.LeapFieldTableChunk}).
     */
    LeapFieldTable((byte)6);

    /** The byte value of the chunk type, used in the trace file header. */
    private byte value;

    /**
     * Constructor.
     * @param  value value of the chunk type
     */
    private ChunkType(byte value) {
      this.value = value;
    }

    /**
     * Gets the byte value of the chunk type.
     * @return byte value of the chunk type
     */
    public byte asByte() {
      return value;
    }

    /**
     * Gets the chunk type from its byte value.
     * @param  value byte value of the chunk type
     * @return       chunk type; null if there is no chunk type for the given
     *               byte value
     */
    public static ChunkType fromByte(byte value) {
      // cannot use values() for some reason...
      // results in segfault at runtime

      if (value == ChunkType.Table.asByte()) {
        return ChunkType.Table;
      } else if (value == ChunkType.DittoData.asByte()) {
        return ChunkType.DittoData;
      } else if (value == ChunkType.SyncData.asByte()) {
        return ChunkType.SyncData;
      } else if (value == ChunkType.JaRecInterval.asByte()) {
        return ChunkType.JaRecInterval;
      } else if (value == ChunkType.LeapData.asByte()) {
        return ChunkType.LeapData;
      } else if (value == ChunkType.LeapFieldTable.asByte()) {
        return ChunkType.LeapFieldTable;
      }

      return null;
    }
  }

  /** Offset of the chunk type value, relative to the start the header. */
  protected static final int TYPE_OFFSET = 0;

  /** Size of the chunk type, in bytes. */
  protected static final int TYPE_BYTES  = SizeConstants.BYTES_IN_BYTE;

  /** Offset of the chunk size value, relative to the start of the header. */
  protected static final int SIZE_OFFSET = TYPE_OFFSET + TYPE_BYTES;

  /** Size of the chunk size value, in bytes. */
  protected static final int SIZE_BYTES  = SizeConstants.BYTES_IN_INT;

  /** Total size of a chunk header. */
  public static final int BYTES = SIZE_OFFSET + SIZE_BYTES;


  /** Type of the chunk. */
  protected ChunkType chunkType;

  /** Size of the chunk, in bytes. */
  protected int chunkSize;

  /**
   * Constructor.
   * @param  chunkType type of the chunk
   */
  protected ChunkHeader(ChunkType chunkType) {
    this.chunkType = chunkType;
  }

  /**
   * Constructor.
   * @param  chunkType type of the chunk
   * @param  chunkSize size of the chunk
   */
  protected ChunkHeader(ChunkType chunkType, int chunkSize) {
    this.chunkType = chunkType;
    this.chunkSize = chunkSize;
  }
}
