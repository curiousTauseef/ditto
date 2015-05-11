package org.jikesrvm.replay.modes;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.instrumentation.SyncEntrypoints;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.sys.sync.SyncRecorder;

/**
 * Synchronization-only recorder mode.
 */
public final class SyncRecorderMode extends ReplayMode {

  @Override
  public void init() throws ReplayException {
    SyncEntrypoints.init(this);
    SyncRecorder.init();
  }

  @Override
  public void terminate() throws ReplayException {
    SyncRecorder.terminate();
  }

  @Override
  public ThreadLifetimeHandlers[] getThreadLifetimeHandlers() {
    return new ThreadLifetimeHandlers[] {
      SyncRecorder.INSTANCE
    };
  }

  @Override
  public boolean isRecorder() {
    return true;
  }

  @Override
  public boolean wrapsSynchronization() {
    return true;
  }

  @Override
  public boolean wrapsMemoryAccesses() {
    return false;
  }

  @Override
  public boolean trapsExits() {
    return true;
  }

  ///
  /// Synchronization entrypoints
  ///

  @Override
  public boolean hasSyncEntrypoints() {
    return true;
  }

  @Override
  public NormalMethod genericLockMethod() {
    return SyncEntrypoints.SyncRecorderEntrypoints.GENERIC_LOCK;
  }

  @Override
  public NormalMethod inlineLockMethod() {
    return SyncEntrypoints.SyncRecorderEntrypoints.INLINE_LOCK;
  }

  @Override
  public void runtimeGenericLock(Object o) {
    SyncRecorder.genericLock(o);
  }
}
