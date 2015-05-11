package org.jikesrvm.replay.modes;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.instrumentation.JaRecWrapMemoryAccesses;
import org.jikesrvm.replay.instrumentation.MemoryAccessEntrypoints;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.sys.jarec.JaRecRecorder;

/**
 * JaRec recorder mode.
 */
public final class JaRecRecorderMode extends ReplayMode {

  @Override
  public void init() throws ReplayException {
    MemoryAccessEntrypoints.init(this);
    JaRecRecorder.init();
  }

  @Override
  public void terminate() throws ReplayException {
    JaRecRecorder.terminate();
  }

  @Override
  public ThreadLifetimeHandlers[] getThreadLifetimeHandlers() {
    return new ThreadLifetimeHandlers[] {
      JaRecRecorder.INSTANCE
    };
  }

  @Override
  public boolean isRecorder() {
    return true;
  }

  @Override
  public boolean wrapsSynchronization() {
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
    return true;
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
    return MemoryAccessEntrypoints.JaRecRecorderEntrypoints.BEFORE_EVENT;
  }

  @Override
  public NormalMethod getAfterEventMethod() {
    return MemoryAccessEntrypoints.JaRecRecorderEntrypoints.AFTER_EVENT;
  }

  ///
  /// Compiler phases
  ///

  @Override
  public CompilerPhase getWrapMemoryAccessesCompilerPhase() {
    return new JaRecWrapMemoryAccesses();
  }
}
