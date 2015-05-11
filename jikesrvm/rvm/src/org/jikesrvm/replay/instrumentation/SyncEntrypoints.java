package org.jikesrvm.replay.instrumentation;

import static org.jikesrvm.runtime.EntrypointHelper.getMethod;

import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.modes.ReplayMode;
import org.jikesrvm.replay.sys.ditto.DittoRecorder;
import org.jikesrvm.replay.sys.ditto.DittoReplayer;
import org.jikesrvm.replay.sys.sync.SyncRecorder;
import org.jikesrvm.replay.sys.sync.SyncReplayer;

/**
 * Entrypoints required to instrument synchronization operations for the
 * purposes of the replay subsystem.
 */
public final class SyncEntrypoints {

  /**
   * Entrypoint for the
   * {@link org.jikesrvm.replay.ReplayManager#traceNextSyncMethod} method.
   */
  public static final NormalMethod TRACE_NEXT_SYNC_METHOD =
      getMethod(ReplayManager.class, "traceNextSyncMethod", "()V");

  /**
   * Entrypoint for the
   * {@link org.jikesrvm.replay.ReplayManager#threadJoined} method.
   */
  public static final NormalMethod THREAD_JOINED =
      getMethod(ReplayManager.class, "threadJoined", "(Ljava/lang/Thread;)V");


  ///
  /// Entrypoints that depend on the replay mode
  ///

  /** Entrypoint for the genericLock method. */
  public static NormalMethod genericLock;

  /** Entrypoint for the inlineLock method. */
  public static NormalMethod inlineLock;

  /**
   * Initializes entrypoints that depend on the running mode of the replay
   * subsystem.
   * @param mode current replay mode
   */
  public static void init(ReplayMode mode) {
    if (mode.hasSyncEntrypoints()) {
      genericLock = mode.genericLockMethod();
      inlineLock = mode.inlineLockMethod();
    }
  }

  /**
   * Synchronization entrypoints specific to the Sync recorder mode.
   */
  public static class SyncRecorderEntrypoints {

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.sync.SyncRecorder#genericLock}.
     */
    public static final NormalMethod GENERIC_LOCK = getMethod(
        SyncRecorder.class, "genericLock", "(Ljava/lang/Object;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.sync.SyncRecorder#inlineLock}.
     */
    public static final NormalMethod INLINE_LOCK = getMethod(
        SyncRecorder.class, "inlineLock",
        "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  }

  /**
   * Synchronization entrypoints specific to the Sync replayer mode.
   */
  public static class SyncReplayerEntrypoints {

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.sync.SyncReplayer#genericLock}.
     */
    public static final NormalMethod GENERIC_LOCK = getMethod(
        SyncReplayer.class, "genericLock", "(Ljava/lang/Object;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.sync.SyncReplayer#inlineLock}.
     */
    public static final NormalMethod INLINE_LOCK = getMethod(
        SyncReplayer.class, "inlineLock",
        "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  }

  /**
   * Synchronization entrypoints specific to the Ditto recorder mode.
   */
  public static class DittoRecorderEntrypoints {

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#genericLock}.
     */
    public static final NormalMethod GENERIC_LOCK = getMethod(
        DittoRecorder.class, "genericLock", "(Ljava/lang/Object;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#inlineLock}.
     */
    public static final NormalMethod INLINE_LOCK = getMethod(
        DittoRecorder.class, "inlineLock",
        "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  }

  /**
   * Synchronization entrypoints specific to the Ditto replayer mode.
   */
  public static class DittoReplayerEntrypoints {

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#genericLock}.
     */
    public static final NormalMethod GENERIC_LOCK = getMethod(
        DittoReplayer.class, "genericLock", "(Ljava/lang/Object;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#inlineLock}.
     */
    public static final NormalMethod INLINE_LOCK = getMethod(
        DittoReplayer.class, "inlineLock",
        "(Ljava/lang/Object;Lorg/vmmagic/unboxed/Offset;)V");
  }

  /** Hidden constructor. */
  private SyncEntrypoints() { }
}
