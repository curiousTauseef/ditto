package org.jikesrvm.replay;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.SystemThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.NonMoving;

/**
 * A thread responsible for finishing/closing any ongoing replay traces in
 * scenarios in which the running application may not be able to finish.
 * <p>
 * The thread blocks until it is requested to finish the replay traces.
 * Such a request is done by setting the static member
 * {@link #finishTraceRequested} to true. A timer will periodically check this
 * member.
 * <p>
 * As an example, in case of a deadlocked application, the user may issue a
 * {@code SIGUSR1} signal to JikesRVM, which will set the
 * {@link #finishTraceRequested} member. On the next timer tick, the thread will
 * be awoken and will finish any ongoing replay traces. The traces may then be
 * used to replay the deadlock.
 * <p>
 * Uses the singleton pattern: there can only be a maximum of one finish-traces
 * thread.
 */
@NonMoving
public final class FinishTraceThread extends SystemThread {

  /** The singleton instance of the thread. Initialized by {@link #init} */
  private static FinishTraceThread instance;

  /**
   * Used to activate the thread. A finish-traces request is performed by
   * setting this member to true.
   */
  @Entrypoint
  public static boolean finishTraceRequested = false;

  /**
   * Spawns the finish-trace thread and starts it.
   */
  public static synchronized void init() {
    if (instance == null) {
      instance = new FinishTraceThread();
      instance.start();
    }
  }

  /**
   * Checks whether a finish-traces request has been issued and awakes the
   * thread if it has.
   */
  public static void checkFinishTraceRequest() {
    if (finishTraceRequested) {
      if (instance != null) {
        synchronized (instance.signal) {
          instance.signal.notifyAll();
        }
      }
      finishTraceRequested = false;
    }
  }

  /**
   * Used as a semaphore in which the thread blocks until a finish-tracesrequest
   * awakes it.
   */
  private Object signal;

  /** Constructor. */
  private FinishTraceThread() {
    super("Finish Replay Trace Thread");
    signal = new Object();
  }

  @Override
  public void run() {
    // wait forever for a finish-traces request
    for (;;) {
      synchronized (signal) {
        try {
          signal.wait();
          break;
        } catch (InterruptedException ie) { }
      }
    }

    // the thread has been awoken: finish any ongoing traces
    VM.sysWriteln("Finishing trace...");
    try {
      ReplayManager.getReplayMode().terminate();
    } catch (ReplayException re) {
      ReplayManager.failAndExit(re);
    }
    VM.sysWriteln("The trace has been finished. You may now kill the RVM.");
  }
}
