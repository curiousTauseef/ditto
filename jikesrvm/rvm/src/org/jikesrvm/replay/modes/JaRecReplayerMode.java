package org.jikesrvm.replay.modes;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.instrumentation.JaRecWrapMemoryAccesses;
import org.jikesrvm.replay.instrumentation.MemoryAccessEntrypoints;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.sys.jarec.JaRecReplayer;

/**
 * JaRec replayer mode.
 */
public final class JaRecReplayerMode extends ReplayMode {

  @Override
  public void init() throws ReplayException {
    MemoryAccessEntrypoints.init(this);
    JaRecReplayer.init();
  }

  @Override
  public void terminate() throws ReplayException {
  }

  @Override
  public ThreadLifetimeHandlers[] getThreadLifetimeHandlers() {
    return new ThreadLifetimeHandlers[] {
      JaRecReplayer.INSTANCE
    };
  }

  @Override
  public boolean wrapsSynchronization() {
    return false;
  }

  @Override
  public boolean isRecorder() {
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
  public boolean wrapsMemoryAccesses() {
    return true;
  }

  @Override
  public boolean trapsExits() {
    return false;
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
    return MemoryAccessEntrypoints.JaRecReplayerEntrypoints.BEFORE_EVENT;
  }

  @Override
  public NormalMethod getAfterEventMethod() {
    return MemoryAccessEntrypoints.JaRecReplayerEntrypoints.AFTER_EVENT;
  }

  ///
  /// Compiler phases
  ///

  @Override
  public CompilerPhase getWrapMemoryAccessesCompilerPhase() {
    return new JaRecWrapMemoryAccesses();
  }
}
