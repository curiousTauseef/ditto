package org.jikesrvm.replay.modes;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.instrumentation.SyncEntrypoints;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.sys.sync.SyncReplayer;

/**
 * Synchronization-only replayer mode.
 */
public final class SyncReplayerMode extends ReplayMode {

  @Override
  public void init() throws ReplayException {
    SyncEntrypoints.init(this);
    SyncReplayer.init();
  }

  @Override
  public void terminate() throws ReplayException {
  }

  @Override
  public ThreadLifetimeHandlers[] getThreadLifetimeHandlers() {
    return new ThreadLifetimeHandlers[] {
      SyncReplayer.INSTANCE
    };
  }

  @Override
  public boolean isRecorder() {
    return false;
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
    return false;
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
    return SyncEntrypoints.SyncReplayerEntrypoints.GENERIC_LOCK;
  }

  @Override
  public NormalMethod inlineLockMethod() {
    return SyncEntrypoints.SyncReplayerEntrypoints.INLINE_LOCK;
  }

  @Override
  public void runtimeGenericLock(Object o) {
    SyncReplayer.genericLock(o);
  }
}
