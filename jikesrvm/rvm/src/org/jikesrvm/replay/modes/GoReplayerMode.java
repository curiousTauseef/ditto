package org.jikesrvm.replay.modes;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.instrumentation.GoWrapMemoryAccesses;
import org.jikesrvm.replay.instrumentation.MemoryAccessEntrypoints;
import org.jikesrvm.replay.instrumentation.SyncEntrypoints;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.sys.global.GoReplayer;
import org.jikesrvm.replay.sys.sync.SyncReplayer;

/**
 * Global order replayer mode.
 */
public final class GoReplayerMode extends ReplayMode {

  @Override
  public void init() throws ReplayException {
    SyncEntrypoints.init(this);
    MemoryAccessEntrypoints.init(this);
    GoReplayer.init();
    SyncReplayer.init();
  }

  @Override
  public void terminate() throws ReplayException {
  }

  @Override
  public ThreadLifetimeHandlers[] getThreadLifetimeHandlers() {
    return new ThreadLifetimeHandlers[] {
      GoReplayer.INSTANCE,
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
    return true;
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

  ///
  /// Memory access entrypoints
  ///

  @Override
  public boolean hasEventStyleEntrypoints() {
    return true;
  }

  @Override
  public NormalMethod getBeforeEventMethod() {
    return MemoryAccessEntrypoints.GoReplayerEntrypoints.BEFORE_EVENT;
  }

  @Override
  public NormalMethod getAfterEventMethod() {
    return MemoryAccessEntrypoints.GoReplayerEntrypoints.AFTER_EVENT;
  }

  ///
  /// Compiler phases
  ///

  @Override
  public CompilerPhase getWrapMemoryAccessesCompilerPhase() {
    return new GoWrapMemoryAccesses();
  }
}
