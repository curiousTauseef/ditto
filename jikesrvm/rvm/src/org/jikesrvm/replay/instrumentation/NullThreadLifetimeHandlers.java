package org.jikesrvm.replay.instrumentation;

import org.jikesrvm.scheduler.RVMThread;

/**
 * An null implementation of {@link ThreadLifetimeHandlers}: the handlers
 * do nothing.
 */
public class NullThreadLifetimeHandlers implements ThreadLifetimeHandlers {

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) { }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) { }

  @Override
  public void threadAfterStarting(RVMThread parent, RVMThread child) { }

  @Override
  public void threadJoining(RVMThread joining, Thread joined) { }

  @Override
  public void threadTerminating(RVMThread t) { }
}
