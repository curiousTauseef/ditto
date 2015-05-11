package org.jikesrvm.replay.tracefile.sync;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;

import org.jikesrvm.replay.sys.sync.SyncTraceEvent;
import org.jikesrvm.replay.tracefile.ChunkHeader;
import org.jikesrvm.replay.tracefile.InputChunkHeader;
import org.jikesrvm.replay.tracefile.InputTableChunk;
import org.jikesrvm.replay.tracefile.TraceFileException;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Input version of the Sync trace file.
 */
public final class InputSyncTraceFile extends SyncTraceFile {

  /**
   * Already known offsets to the head of each thread's data chunk list.
   * Maps thread replay ids to the absolute trace file offset of the head of
   * their data chunk list.
   */
  private ConcurrentHashMap<Long, Long> dataChunkOffsets;

  /** Absolute trace file offset of the next table chunk. */
  private long nextTableChunkOffset;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file stream
   * @param  header trace file header
   */
  private InputSyncTraceFile(File file, RandomAccessFile stream,
                             InputSyncTraceFileHeader header) {
    super(file, stream, header);
    dataChunkOffsets = new ConcurrentHashMap<Long, Long>();
    nextTableChunkOffset = header.getOffsetToTable();
  }

  /**
   * Creates an input sync trace file by reading its header from a given file.
   * @param  file               file handle
   * @param  stream             file stream
   * @return                    an input sync trace file instance
   * @throws TraceFileException if an I/O error occurs
   */
  public static InputSyncTraceFile read(File file, RandomAccessFile stream)
                                 throws TraceFileException {
    try {
      FileChannel fc = stream.getChannel();
      fc.position(HEADER_OFFSET);
      ByteBuffer buf = ByteBuffer.allocate(SyncTraceFileHeader.BYTES);
      fc.read(buf);
      buf.flip();
      InputSyncTraceFileHeader header = InputSyncTraceFileHeader.read(buf);
      return new InputSyncTraceFile(file, stream, header);
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }


  ///
  /// Event reading
  ///

  /**
   * Reads the next event for a given thread.
   * @param  thr                the thread
   * @return                    next event for the thread
   * @throws TraceFileException if the trace does not contain more events
   */
  public SyncTraceEvent getNextEvent(RVMThread thr) throws TraceFileException {
    // get the current data chunk
    InputSyncDataChunk dc = thr.syncRepState.inputDataChunk;
    if (dc == null) {
      return null;
    }

    // if the data chunk has events remaining, get the next one
    if (dc.hasRemaining()) {
      return dc.getNextEvent();
    }

    // otherwise, load the next data chunk and get an event from it
    dc = readDataChunkAt(dc.nextChunkOffset);
    thr.syncRepState.inputDataChunk = dc;
    if (dc != null) {
      return dc.getNextEvent();
    }
    return null;
  }

  ///
  /// Thread lifetime management
  ///

  /**
   * Initializes the trace of a given thread by reading its first data chunk.
   * @param  thr                the thread
   * @throws TraceFileException if there is no trace for the thread or an I/O
   *                            error occurs
   */
  public void initThreadTrace(RVMThread thr) throws TraceFileException {
    InputSyncDataChunk dc = getFirstDataChunk(thr.replayId);
    thr.syncRepState.inputDataChunk = dc;
    thr.syncRepState.nextEvent = getNextEvent(thr);
  }

  /**
   * Obtains the first data chunk in a given thread's trace.
   * @param  threadId           thread replay id
   * @return                    first data chunk of the thread
   * @throws TraceFileException if there is no trace for the thread or an I/O
   *                            error occurs
   */
  private InputSyncDataChunk getFirstDataChunk(long threadId)
                                        throws TraceFileException {
    if (!dataChunkOffsets.containsKey(threadId)
        && !tryToFindDataChunkOffset(threadId)) {
      throw new TraceFileException("No trace for thread " + threadId);
    }

    InputSyncDataChunk dc = readDataChunkAt(dataChunkOffsets.get(threadId));
    dataChunkOffsets.remove(threadId);
    return dc;
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
  private boolean tryToFindDataChunkOffset(long threadId)
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
        ByteBuffer headerBuf = ByteBuffer.allocate(ChunkHeader.BYTES);
        fc.read(headerBuf);
        headerBuf.flip();
        header.update(headerBuf);

        // read the chunk's body
        ByteBuffer dataBuf = ByteBuffer.allocate(header.getChunkSize());
        fc.read(dataBuf);
        dataBuf.flip();
        return InputTableChunk.read(header, dataBuf);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }

  /**
   * Reads a data chunk located at a given trace file offset in a thread-safe
   * manner.
   * @param  offset             trace file offset of the data chunk
   * @return                    read data chunk
   * @throws TraceFileException if an I/O error occurs
   */
  private InputSyncDataChunk readDataChunkAt(long offset)
                                      throws TraceFileException {
    if (offset == NULL_OFFSET) {
      return null;
    }

    synchronized (stream) {
      try {
        // go to the chunk's offset
        FileChannel fc = stream.getChannel();
        fc.position(offset);

        // read the chunk's header
        InputChunkHeader header = new InputChunkHeader(
            ChunkHeader.ChunkType.SyncData);
        ByteBuffer headerBuf = ByteBuffer.allocate(ChunkHeader.BYTES);
        fc.read(headerBuf);
        headerBuf.flip();
        header.update(headerBuf);

        // read the chunk's body
        ByteBuffer dataBuf = ByteBuffer.allocate(header.getChunkSize());
        fc.read(dataBuf);
        dataBuf.flip();
        return InputSyncDataChunk.read(header, dataBuf);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }
}
