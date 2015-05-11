package org.jikesrvm.replay.modes;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.replay.ReplayException;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;

/**
 * A mode of execution for the replay subsystem.
 * <p>
 * Defines any properties and functionaly of the replay subsystem that vary
 * depending on which mode the user chooses during the VM's boot.
 */
public abstract class ReplayMode {

  /**
   * Initializes the replay mode.
   * @throws ReplayException if initialization fails
   */
  public abstract void init() throws ReplayException;

  /**
   * Terminates the replay mode.
   * @throws ReplayException if termination fails
   */
  public abstract void terminate() throws ReplayException;

  /**
   * Obtains the thread lifetime wrappers for this replay mode.
   * @return thread lifetime wrappers
   */
  public abstract ThreadLifetimeHandlers[] getThreadLifetimeHandlers();

  /**
   * Specifies whether this replay mode is a recorder mode.
   * @return true if the mode is a recorder mode; false if it is a replay mode
   */
  public abstract boolean isRecorder();

  /**
   * Specifies whether this replay mode wraps synchronization operations.
   * @return whether the mode wraps synchronization
   */
  public abstract boolean wrapsSynchronization();

  /**
   * Specifies whether this replay mode wraps memory accesses.
   * @return whether the mode wraps memory accesses
   */
  public abstract boolean wrapsMemoryAccesses();

  /**
   * Specifies whether this replay mode needs to trap VM exits to terminate
   * cleanly.
   * @return whether the mode needs to trap exits
   */
  public abstract boolean trapsExits();

  /**
   * Specifies whether the replay mode requires the user to provide a
   * thread-escaped-fields file.
   * @return whether a TEF file is mandatory
   */
  public boolean requiresTEFFile() {
    return false;
  }


  ///
  /// Synchronization entrypoints
  ///

  /**
   * Specififes whether this replay mode defines synchronization entrypoints.
   * If true, the mode must override {@link #genericLockMethod},
   * {@link #inlineLockMethod} and {@link #runtimeGenericLock}.
   * @return whether the mode defines synchronization entrypoints
   */
  public boolean hasSyncEntrypoints() {
    return false;
  }

  /**
   * Gets the generic-lock synchronization entrypoint for this replay mode.
   * @return generic-lock synchronization entrypoint
   */
  public NormalMethod genericLockMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the inline-lock synchronization entrypoint for this replay mode.
   * @return inline-lock synchronization entrypoint
   */
  public NormalMethod inlineLockMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * The handler for runtime-only generic locks for this replay mode.
   * @param obj object to lock
   */
  public void runtimeGenericLock(Object obj) {
    VM._assert(VM.NOT_REACHED);
  }


  ///
  /// Memory access entrypoints
  ///

  /**
   * Specifies whether this replay mode has memory access entrypoints that use
   * the load/store style, i.e, that specify different handlers for load and
   * store memory accesses.
   * <p> The truthfulness of this method is mutually exclusive with that of
   * {@link #hasEventStyleEntrypoints}.
   * <p> If true, the mode must override {@link #getBeforeStaticLoadMethod},
   * {@link #getBeforeInstanceLoadMethod}, {@link #getAfterLoadMethod},
   * {@link #getBeforeStaticStoreMethod}, {@link #getBeforeInstanceStoreMethod},
   * {@link #getAfterStoreMethod}, {@link #getThreadStateField} and
   * {@link #getThreadStateType}.
   * @return whether the mode has load/store-style memory access entrypoints
   */
  public boolean hasLoadStoreStyleEntrypoints() {
    return false;
  }

  /**
   * Gets the before-static-load memory access handler for this mode.
   * @return before-static-load handler
   */
  public NormalMethod getBeforeStaticLoadMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the before-instance-load memory access handler for this mode.
   * @return before-instance-load handler
   */
  public NormalMethod getBeforeInstanceLoadMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the after-load memory access handler for this mode.
   * @return after-load handler
   */
  public NormalMethod getAfterLoadMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the before-static-store memory access handler for this mode.
   * @return before-static-store handler
   */
  public NormalMethod getBeforeStaticStoreMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the before-instance-store memory access handler for this mode.
   * @return before-instance-store handler
   */
  public NormalMethod getBeforeInstanceStoreMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the after-store memory access handler for this mode.
   * @return after-store handler
   */
  public NormalMethod getAfterStoreMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the thread state field for this mode.
   * @return thread state field
   */
  public RVMField getThreadStateField() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the type of the thread state for this mode.
   * @return type of the thread state
   */
  public TypeReference getThreadStateType() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Specifies whether this replay mode has memory access entrypoints that use
   * the event style, i.e, that use the same handlers for every type of memory
   * access.
   * <p> The truthfulness of this method is mutually exclusive with that of
   * {@link #hasLoadStoreStyleEntrypoints}.
   * <p> If true, the mode must override {@link #getBeforeEventMethod} and
   * {@link #getAfterEventMethod}.
   * @return whether the mode has event-style memory access entrypoints
   */
  public boolean hasEventStyleEntrypoints() {
    return false;
  }

  /**
   * Gets the before-event memory access handler for this mode.
   * @return before-event handler
   */
  public NormalMethod getBeforeEventMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }

  /**
   * Gets the after-event memory access handler for this mode.
   * @return after-event handler
   */
  public NormalMethod getAfterEventMethod() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }


  ///
  /// Compiler phases
  ///

  /**
   * Gets the OPT compiler phase that instruments memory access instructions
   * for this mode.
   * @return wrap memory accesses compiler phase
   */
  public CompilerPhase getWrapMemoryAccessesCompilerPhase() {
    VM._assert(VM.NOT_REACHED);
    return null;
  }


  ///
  /// Static interface
  ///

  /**
   * Creates a replay mode using the value of the corresponsing option of the
   * replay sub-system.
   * @return current replay mode
   */
  public static ReplayMode getCurrentMode() {
    switch (ReplayOptions.MODE) {
      case SyncRecord: return new SyncRecorderMode();
      case SyncReplay: return new SyncReplayerMode();

      case DittoRecord: return new DittoRecorderMode();
      case DittoReplay: return new DittoReplayerMode();

      case GoRecord: return new GoRecorderMode();
      case GoReplay: return new GoReplayerMode();

      case JaRecRecord: return new JaRecRecorderMode();
      case JaRecReplay: return new JaRecReplayerMode();

      case LeapRecord: return new LeapRecorderMode();
      case LeapReplay: return new LeapReplayerMode();

      default:
        VM._assert(VM.NOT_REACHED);
        return null;
    }
  }
}
