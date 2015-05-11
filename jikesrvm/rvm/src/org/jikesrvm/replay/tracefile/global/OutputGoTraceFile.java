package org.jikesrvm.replay.tracefile.global;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

import org.jikesrvm.replay.ReplayConstants;
import org.jikesrvm.replay.tracefile.TraceFileException;

/**
 * Output version of the global-order trace file.
 */
public final class OutputGoTraceFile extends GoTraceFile {

  /** Trace file output stream. */
  private DataOutputStream stream;

  /** Trace file output header. */
  private OutputGoTraceFileHeader outputHeader;

  /**
   * Replay id of the last thread that registered an event in the trace file.
   */
  private long lastThreadId;

  /**
   * Current number of consecutive events that the thread with replay id
   * {@link #lastThreadId} registered in the trace file.
   */
  private int count;

  /**
   * Constructor.
   * @param  file   trace file handle
   * @param  stream trace file output stream
   */
  public OutputGoTraceFile(File file, DataOutputStream stream) {
    super(file);
    this.stream = stream;
    outputHeader = (OutputGoTraceFileHeader)header;
    lastThreadId = ReplayConstants.NULL_THREAD_ID;
    count = 0;
  }

  /**
   * Initializes the output trace file, so that it is ready for tracing.
   * @throws TraceFileException if file I/O fails
   */
  public void init() throws TraceFileException {
    try {
      outputHeader.write(stream);
    } catch (IOException ioe) {
      throw new TraceFileException(ioe);
    }
  }

  /**
   * Traces an event by a given thread.
   * @param  threadId           thread replay id
   * @throws TraceFileException if an I/O error occurs
   */
  public void traceEvent(long threadId) throws TraceFileException {
    // if the thread tracing the event is the same thread that traced the
    // previous one, simply increment the count
    if (threadId == lastThreadId) {
      count++;
      return;
    }

    // otherwise, write the current (thread replay id, count) tuple and start
    // a new one
    if (count > 0) {
      try {
        stream.writeLong(lastThreadId);
        stream.writeInt(count);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
    lastThreadId = threadId;
    count = 1;
  }

  /**
   * Finishes the trace file.
   * @throws TraceFileException if an I/O error occurs
   */
  public void finish() throws TraceFileException {
    if (count > 0) {
      try {
        stream.writeLong(lastThreadId);
        stream.writeInt(count);
      } catch (IOException ioe) {
        throw new TraceFileException(ioe);
      }
    }
  }
}
