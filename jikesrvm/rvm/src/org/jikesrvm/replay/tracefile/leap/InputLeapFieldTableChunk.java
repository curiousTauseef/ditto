package org.jikesrvm.replay.tracefile.leap;

import java.nio.ByteBuffer;
import java.util.Map;

import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;

/**
 * The input version of a leap field table chunk.
 */
public final class InputLeapFieldTableChunk extends LeapFieldTableChunk {

  /** Header of the chunk. */
  private InputChunkHeader inputHeader;

  /** Trace file offset of the next field table chunk. */
  public long nextChunkOffset;

  /**
   * Private constructor.
   * @param  inputHeader     header of the chunk
   * @param  nextChunkOffset trace file offset of the next field table chunk
   * @param  entries         entires of the chunk
   */
  private InputLeapFieldTableChunk(InputChunkHeader inputHeader,
                                   long nextChunkOffset, Entry[] entries) {
    super();
    this.inputHeader = inputHeader;
    this.nextChunkOffset = nextChunkOffset;
    this.entries = entries;
    numEntries = entries.length;
  }

  @Override
  public ChunkHeader getHeader() {
    return inputHeader;
  }

  /**
   * Creates an input leap field table chunk using an already-read chunk header
   * and a buffer of data read from the trace file.
   * @param  header chunk header
   * @param  buf    buffer
   * @return        input leap field table chunk
   */
  public static InputLeapFieldTableChunk read(InputChunkHeader header,
                                              ByteBuffer buf) {
    long nextChunkOffset = buf.getLong();
    int numEntries = buf.getShort();
    Entry[] entries = new Entry[numEntries];
    for (int i = 0; i < numEntries; i++) {
      int fieldNameLength = buf.getShort();
      byte[] fieldNameBytes = new byte[fieldNameLength];
      buf.get(fieldNameBytes);
      String fieldName = new String(fieldNameBytes);
      long offset = buf.getLong();
      entries[i] = new Entry(fieldName, offset);
    }
    return new InputLeapFieldTableChunk(header, nextChunkOffset, entries);
  }

  /**
   * Fills a map with the data in the field table chunk's entries.
   * @param map map to fill
   */
  public void fillMap(Map<String, Long> map) {
    for (int i = 0; i < numEntries; i++) {
      Entry e = entries[i];
      map.put(e.fieldName, e.offset);
    }
  }
}
