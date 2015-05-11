package org.jikesrvm.replay.modes;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.instrumentation.LeapWrapMemoryAccesses;
import org.jikesrvm.replay.instrumentation.MemoryAccessEntrypoints;
import org.jikesrvm.replay.instrumentation.SyncEntrypoints;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.sys.leap.LeapReplayer;
import org.jikesrvm.replay.sys.sync.SyncReplayer;
import org.jikesrvm.replay.tracefile.leap.FieldNameIdMap;

/**
 * LEAP replayer mode.
 */
public final class LeapReplayerMode extends ReplayMode {

  @Override
  public void init() throws ReplayException {
    FieldNameIdMap.init();
    SyncEntrypoints.init(this);
    MemoryAccessEntrypoints.init(this);
    LeapReplayer.init();
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
    return true;
  }

  @Override
  public boolean trapsExits() {
    return false;
  }

  @Override
  public boolean requiresTEFFile() {
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
    return MemoryAccessEntrypoints.LeapReplayerEntrypoints.BEFORE_EVENT;
  }

  @Override
  public NormalMethod getAfterEventMethod() {
    return MemoryAccessEntrypoints.LeapReplayerEntrypoints.AFTER_EVENT;
  }

  ///
  /// Compiler phases
  ///

  @Override
  public CompilerPhase getWrapMemoryAccessesCompilerPhase() {
    return new LeapWrapMemoryAccesses();
  }
}
