package org.jikesrvm.replay;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Provides thread identifiers which are consistent between different executions
 * of a given application, as long as the threads are started in the same order.
 */
public final class ConsistentThreadIdentifier {

  /**
   * Number of threads given ids.
   * Its current value is the id that will be given to the next thread that
   * requires an id.
   */
  private static long numThreads = 0;

  /**
   * Handles the event in which a given thread, the child, is starting after
   * being spawned by another thread, the parent.
   * The child thread is given a consistent thread identifier.
   * @param parent thread spawning the starting thread
   * @param child  starting thread
   */
  public static void threadStarting(RVMThread parent, RVMThread child) {
    // ignore system threads, we don't want to replay the execution of those
    if (!child.isSystemThread()) {
      identifyThread(child);
      if (ReplayOptions.REPORT_THREAD_START) {
        String parentName = parent.isSystemThread() ? parent.getName()
                                                    : "#" + parent.replayId;
        VM.sysWriteln("Thread " + parentName
                      + " created thread #" + child.replayId);
      }
    }
  }

  /**
   * Gives an identifier to a thread.
   * @param t thread to id
   */
  private static void identifyThread(RVMThread t) {
    t.replayId = numThreads++;
  }

  // Hide the constructor
  private ConsistentThreadIdentifier() { }
}
