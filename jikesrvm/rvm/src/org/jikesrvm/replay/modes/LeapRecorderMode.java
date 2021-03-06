package org.jikesrvm.replay.modes;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.instrumentation.LeapWrapMemoryAccesses;
import org.jikesrvm.replay.instrumentation.MemoryAccessEntrypoints;
import org.jikesrvm.replay.instrumentation.SyncEntrypoints;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.sys.leap.LeapRecorder;
import org.jikesrvm.replay.sys.sync.SyncRecorder;
import org.jikesrvm.replay.tracefile.leap.FieldNameIdMap;

/**
 * LEAP recorder mode.
 */
public final class LeapRecorderMode extends ReplayMode {

  @Override
  public void init() throws ReplayException {
    FieldNameIdMap.init();
    SyncEntrypoints.init(this);
    MemoryAccessEntrypoints.init(this);
    LeapRecorder.init();
    SyncRecorder.init();
  }

  @Override
  public void terminate() throws ReplayException {
    LeapRecorder.terminate();
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
    return true;
  }

  @Override
  public boolean trapsExits() {
    return true;
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

  ///
  /// Memory access entrypoints
  ///

  @Override
  public boolean hasEventStyleEntrypoints() {
    return true;
  }

  @Override
  public NormalMethod getBeforeEventMethod() {
    return MemoryAccessEntrypoints.LeapRecorderEntrypoints.BEFORE_EVENT;
  }

  @Override
  public NormalMethod getAfterEventMethod() {
    return MemoryAccessEntrypoints.LeapRecorderEntrypoints.AFTER_EVENT;
  }

  ///
  /// Compiler phases
  ///

  @Override
  public CompilerPhase getWrapMemoryAccessesCompilerPhase() {
    return new LeapWrapMemoryAccesses();
  }
}
