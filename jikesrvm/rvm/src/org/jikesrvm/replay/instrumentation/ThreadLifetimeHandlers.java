package org.jikesrvm.replay.instrumentation;

import org.jikesrvm.scheduler.RVMThread;

/**
 * To be implemented by recorders and replayers that need to trace events
 * related to a thread's life cycle.
 */
public interface ThreadLifetimeHandlers {

  /**
   * Handler executed before a thread starts and before the thread is assigned
   * a unique replay id by the replay sub-system.
   * @param parent parent thread
   * @param child  child thread
   */
  void threadBeforeStarting(RVMThread parent, RVMThread child);

  /**
   * Handler executed before a thread starts, but after the thread is assigned
   * a unique replay id by the replay sub-system.
   * @param parent parent thread
   * @param child  child thread
   */
  void threadBeforeStartingAfterId(RVMThread parent, RVMThread child);

  /**
   * Handler executed after a thread starts.
   * @param parent parent thread
   * @param child  child thread
   */
  void threadAfterStarting(RVMThread parent, RVMThread child);

  /**
   * Handler executed after a thread joins another thread.
   * @param joining thread which called join on {@code joined}
   * @param joined  thread which is being joined
   */
  void threadJoining(RVMThread joining, Thread joined);

  /**
   * Handler executed when a thread is terminating.
   * @param t terminating thread
   */
  void threadTerminating(RVMThread t);
}
