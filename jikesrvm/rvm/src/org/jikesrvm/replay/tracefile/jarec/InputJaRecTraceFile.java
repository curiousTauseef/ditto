package org.jikesrvm.replay.tracefile.jarec;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.sys.jarec.JaRecReplayerThreadState;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;
import org.jikesrvm.replay.tracefile.InputTableChunk;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Input version of the jarec trace file.
 */
public final class InputJaRecTraceFile extends JaRecTraceFile {

  /**
   * Already known offsets to the head of each thread's interval chunk list.
   * Maps thread replay ids to the absolute trace file offset of the head of
   * their interval chunk list.
   */
  private ConcurrentHashMap<Long, Long> dataChunkOffsets;

  /** Absolute trace file offset of the next table chunk. */
  private long nextTableChunkOffset;

  /** Auxiliary buffer used to read chunk headers. */
  private ByteBuffer headerBuffer;

  /**
   * Private constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   * @param  header trace file header
   */
  private InputJaRecTraceFile(File file, RandomAccessFile stream,
                              InputJaRecTraceFileHeader header) {
    super(file, stream, header);
    dataChunkOffsets = new ConcurrentHashMap<Long, Long>();
    nextTableChunkOffset = header.getOffsetToTable();
    headerBuffer = ByteBuffer.allocate(ChunkHeader.BYTES);
  }

  /**
   * Creates an input jarec trace file by reading its header from a given file.
   * @param  file               file handle
   * @param  stream             file stream
   * @return                    an input jarec trace file instance
   * @throws TraceFileException if an I/O error occurs
   */
  public static InputJaRecTraceFile read(File file, RandomAccessFile stream)
                                  throws TraceFileException {
    try {
      FileChannel fc = stream.getChannel();
      fc.position(HEADER_OFFSET);
      ByteBuffer buf = ByteBuffer.allocate(JaRecTraceFileHeader.BYTES);
      fc.read(buf);
      buf.flip();
      InputJaRecTraceFileHeader header = InputJaRecTraceFileHeader.read(buf);
      return new InputJaRecTraceFile(file, stream, header);
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }


  ///
  /// Interval/logical clock reading
  ///

  /**
   * Gets the next logical clock of a given thread's trace.
   * @param  thr                the thread
   * @return                    next logical clock of the thread
   * @throws TraceFileException if an I/O error occurs or there is not enough
   *                            trace
   */
  public long getNextClock(RVMThread thr) throws TraceFileException {
    JaRecReplayerThreadState ts = thr.jaRecRepState;
    InputJaRecIntervalChunk ic = ts.intervalChunk;
    while (true) {
      // if the current interval chunk has remaining logical clocks, get one
      // from there
      if (ic.hasRemaining(ts.clock)) {
        return ic.getNextClockValue(ts.clock);
      }

      // otherwise, if the interval chunk is not the thread's last, read the
      // next interval chunk and retry
      if (!ic.isLast()) {
        readNextIntervalChunk(ic);
        continue;
      }

      return -1;
    }
  }


  ///
  /// Thread lifetime management
  ///

  /**
   * Initializes the trace of a given thread by reading its first interval chunk
   * and getting its first logical clock from it.
   * @param  thr                the thread
   * @throws TraceFileException if an I/O error occurs
   */
  public void initThreadTrace(RVMThread thr) throws TraceFileException {
    JaRecReplayerThreadState ts = thr.jaRecRepState;
    InputJaRecIntervalChunk ic = getFirstIntervalChunk(thr.replayId);
    ts.intervalChunk = ic;
    if (!ic.isEmpty()) {
      ts.clock = ic.getFirstClock();
    } else {
      ts.clock = ReplayConstants.NULL_CLOCK;
    }
  }

  /**
   * Obtains the first interval chunk in a given thread's trace.
   * @param  threadId           thread replay id
   * @return                    first interval chunk of the thread
   * @throws TraceFileException if there is no trace for the thread or an I/O
   *                            error occurs
   */
  private InputJaRecIntervalChunk getFirstIntervalChunk(long threadId)
                                                 throws TraceFileException {
    // find the head of the thread's interval chunk list: start by looking at
    // the already known initial interval chunks; if necessary, read more table
    // chunks until the entry for the thread is found or we run out of table
    // chunks
    if (!dataChunkOffsets.containsKey(threadId)
        && !tryToFindIntervalChunkOffset(threadId)) {
      throw new TraceFileException("No trace for thread " + threadId);
    }

    // get the offset of the first interval chunk
    // if the offset is non-null, read its first interval chunk
    long offset = dataChunkOffsets.get(threadId);
    dataChunkOffsets.remove(threadId);
    if (offset != NULL_OFFSET) {
      InputJaRecIntervalChunk ic = new InputJaRecIntervalChunk();
      readIntervalChunkAt(ic, offset);
      return ic;
    }

    // otherwise, return a null interval chunk
    return InputJaRecIntervalChunk.nullInstance();
  }


  ///
  /// Readers
  ///

  /**
   * Reads table chunks until an entry for a given thread is found or the trace
   * file runs out of table chunks.
   * @param  threadId           thread replay id
   * @return                    whether the entry was found
   * @throws TraceFileException if an I/O error occurs
   */
  private boolean tryToFindIntervalChunkOffset(long threadId)
                                        throws TraceFileException {
    while (!dataChunkOffsets.containsKey(threadId)) {
      if (nextTableChunkOffset == NULL_OFFSET) {
        return false;
      }
      InputTableChunk tc = readTableChunkAt(nextTableChunkOffset);
      nextTableChunkOffset = tc.nextChunkOffset;
      tc.fillMap(dataChunkOffsets);
    }
    return true;
  }

  /**
   * Reads a table chunk located at a given trace file offset in a thread-safe
   * manner.
   * @param  offset             trace file offset of the table chunk
   * @return                    read table chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private InputTableChunk readTableChunkAt(long offset)
                                    throws TraceFileException {
    synchronized (stream) {
      try {
        // go to the chunk's offset
        FileChannel fc = stream.getChannel();
        fc.position(offset);

        // read the chunk's header
        InputChunkHeader header = new InputChunkHeader(
            ChunkHeader.ChunkType.Table);
        headerBuffer.clear();
        fc.read(headerBuffer);
        headerBuffer.flip();
        header.update(headerBuffer);

        // read the chunk's body
        ByteBuffer body = ByteBuffer.allocate(header.getChunkSize());
        fc.read(body);
        body.flip();
        return InputTableChunk.read(header, body);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }

  /**
   * Reads an interval chunk located at a given trace file offset in a
   * thread-safe manner.
   * @param  intervalChunk      interval chunk instance to fill
   * @param  offset             trace file offset of the data chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private void readIntervalChunkAt(InputJaRecIntervalChunk intervalChunk,
                                   long offset) throws TraceFileException {
    try {
      synchronized (stream) {
        intervalChunk.read(stream, offset);
      }
      intervalChunk.init();
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }

  /**
   * Reads the next interval chunk in a given interval chunk's list in a
   * thread-safe manner.
   * @param  intervalChunk      current interval chunk; recycled as the next
   *                            chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private void readNextIntervalChunk(InputJaRecIntervalChunk intervalChunk)
                              throws TraceFileException {
    try {
      synchronized (stream) {
        intervalChunk.readNext(stream);
      }
      intervalChunk.init();
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }
}
