package org.jikesrvm.replay.instrumentation;

import static org.jikesrvm.runtime.EntrypointHelper.getMethod;
import static org.jikesrvm.runtime.EntrypointHelper.getField;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.modes.ReplayMode;
import org.jikesrvm.scheduler.RVMThread;

/**
 * Entrypoints required to instrument memory access operations for the
 * purposes of the replay subsystem.
 */
public final class MemoryAccessEntrypoints {

  /**
   * Entrypoint for method
   * {@link org.jikesrvm.scheduler.RVMThread#getCurrentThread}.
   */
  public static final NormalMethod GET_CURRENT_THREAD = getMethod(
      RVMThread.class,
      "getCurrentThread",
      "()Lorg/jikesrvm/scheduler/RVMThread;");

  /**
   * Location of field
   * {@link ReplayOptions#MAX_ARRAY_FIELDS}.
   */
  public static final RVMField MAX_ARRAY_FIELDS = getField(
      ReplayOptions.class,
      "MAX_ARRAY_FIELDS",
      int.class);

  /**
   * Entrypoint for method {@link org.jikesrvm.replay.ReplayUtils#classOfType}.
   */
  public static final NormalMethod GET_CLASS_OF_TYPE = getMethod(
      org.jikesrvm.replay.ReplayUtils.class,
      "classOfType",
      "(I)Ljava/lang/Class;");


  ///
  /// Entrypoints that depend on the replay mode
  ///

  /** Entrypoint for the before-static-field-load handler. */
  public static NormalMethod beforeStaticLoad;

  /** Entrypoint for the before-instance-field-load handler. */
  public static NormalMethod beforeInstanceLoad;

  /** Entrypoint for the after-field-load handler. */
  public static NormalMethod afterLoad;

  /** Entrypoint for the before-static-field-store handler. */
  public static NormalMethod beforeStaticStore;

  /** Entrypoint for the before-instance-field-store handler. */
  public static NormalMethod beforeInstanceStore;

  /** Entrypoint for the after-field-store handler. */
  public static NormalMethod afterStore;

  /** Entrypoint for the before-event handler. */
  public static NormalMethod beforeEvent;

  /** Entrypoint for the after-event handler. */
  public static NormalMethod afterEvent;

  /** Location of the thread's replay state field. */
  public static RVMField threadState;

  /** Type of the thread's replay state field. */
  public static TypeReference threadStateType;

  /**
   * Initializes entrypoints that depend on the running mode of the replay
   * subsystem.
   * @param mode current replay mode
   */
  public static void init(ReplayMode mode) {
    if (mode.hasLoadStoreStyleEntrypoints()) {
      beforeStaticLoad = mode.getBeforeStaticLoadMethod();
      beforeInstanceLoad = mode.getBeforeInstanceLoadMethod();
      afterLoad = mode.getAfterLoadMethod();
      beforeStaticStore = mode.getBeforeStaticStoreMethod();
      beforeInstanceStore = mode.getBeforeInstanceStoreMethod();
      afterStore = mode.getAfterStoreMethod();
      threadState = mode.getThreadStateField();
      threadStateType = mode.getThreadStateType();
    }
    if (mode.hasEventStyleEntrypoints()) {
      beforeEvent = mode.getBeforeEventMethod();
      afterEvent = mode.getAfterEventMethod();
    }
  }


  /**
   * Memory access entrypoints specific to the Ditto recorder mode.
   */
  public static final class DittoRecorderEntrypoints {

    /**
     * Class of the Ditto recorder:
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder}.
     */
    public static final Class<?> CL =
        org.jikesrvm.replay.sys.ditto.DittoRecorder.class;

    /**
     * Class of the Ditto recorder thread state:
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorderThreadState}.
     */
    public static final Class<?> TS =
        org.jikesrvm.replay.sys.ditto.DittoRecorderThreadState.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#beforeStaticLoad}.
     */
    public static final NormalMethod BEFORE_STATIC_LOAD = getMethod(CL,
        "beforeStaticLoad",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoRecorderThreadState;II)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#beforeInstanceLoad}.
     */
    public static final NormalMethod BEFORE_INSTANCE_LOAD = getMethod(CL,
        "beforeInstanceLoad",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoRecorderThreadState;Ljava/lang/Object;I)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#afterLoad}.
     */
    public static final NormalMethod AFTER_LOAD = getMethod(CL,
        "afterLoad",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoRecorderThreadState;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#beforeStaticStore}.
     */
    public static final NormalMethod BEFORE_STATIC_STORE = getMethod(CL,
        "beforeStaticStore",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoRecorderThreadState;II)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#beforeInstanceStore}.
     */
    public static final NormalMethod BEFORE_INSTANCE_STORE = getMethod(CL,
        "beforeInstanceStore",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoRecorderThreadState;Ljava/lang/Object;I)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoRecorder#afterStore}.
     */
    public static final NormalMethod AFTER_STORE = getMethod(CL,
        "afterStore",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoRecorderThreadState;)V");

    /**
     * Thread state field:
     * {@link org.jikesrvm.scheduler.RVMThread#dittoRecState}.
     */
    public static final RVMField THREAD_STATE =
        getField(RVMThread.class, "dittoRecState", TS);

    /** Type of the thread state. */
    public static final TypeReference THREAD_STATE_TYPE =
        TypeReference.findOrCreate(TS);
  }


  /**
   * Memory access entrypoints specific to the Ditto replayer mode.
   */
  public static final class DittoReplayerEntrypoints {

    /**
     * Class of the Ditto replayer:
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer}.
     */
    public static final Class<?> CL =
        org.jikesrvm.replay.sys.ditto.DittoReplayer.class;

    /**
     * Class of the Ditto replayer thread state:
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayerThreadState}.
     */
    public static final Class<?> TS =
        org.jikesrvm.replay.sys.ditto.DittoReplayerThreadState.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#beforeStaticLoad}.
     */
    public static final NormalMethod BEFORE_STATIC_LOAD = getMethod(CL,
        "beforeStaticLoad",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoReplayerThreadState;II)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#beforeStaticLoad}.
     */
    public static final NormalMethod BEFORE_INSTANCE_LOAD = getMethod(CL,
        "beforeInstanceLoad",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoReplayerThreadState;Ljava/lang/Object;I)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#beforeStaticLoad}.
     */
    public static final NormalMethod AFTER_LOAD = getMethod(CL,
        "afterLoad",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoReplayerThreadState;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#beforeStaticLoad}.
     */
    public static final NormalMethod BEFORE_STATIC_STORE = getMethod(CL,
        "beforeStaticStore",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoReplayerThreadState;II)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#beforeInstanceStore}.
     */
    public static final NormalMethod BEFORE_INSTANCE_STORE = getMethod(CL,
        "beforeInstanceStore",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoReplayerThreadState;Ljava/lang/Object;I)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.ditto.DittoReplayer#afterStore}.
     */
    public static final NormalMethod AFTER_STORE = getMethod(CL,
        "afterStore",
        "(Lorg/jikesrvm/replay/sys/ditto/DittoReplayerThreadState;)V");

    /**
     * Thread state field:
     * {@link org.jikesrvm.scheduler.RVMThread#dittoRepState}.
     */
    public static final RVMField THREAD_STATE =
        getField(RVMThread.class, "dittoRepState", TS);

    /** Type of the thread state. */
    public static final TypeReference THREAD_STATE_TYPE =
        TypeReference.findOrCreate(TS);
  }


  /**
   * Memory access entrypoints specific to the Global-Order recorder mode.
   */
  public static final class GoRecorderEntrypoints {

    /**
     * Class of the Go recorder:
     * {@link org.jikesrvm.replay.sys.global.GoRecorder}.
     */
    private static final Class<?> C =
        org.jikesrvm.replay.sys.global.GoRecorder.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.global.GoRecorder#beforeEvent}.
     */
    public static final NormalMethod BEFORE_EVENT = getMethod(C,
        "beforeEvent", "(Lorg/jikesrvm/scheduler/RVMThread;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.global.GoRecorder#afterEvent}.
     */
    public static final NormalMethod AFTER_EVENT = getMethod(C,
        "afterEvent", "()V");
  }


  /**
   * Memory access entrypoints specific to the Global-Order replayer mode.
   */
  public static final class GoReplayerEntrypoints {

    /**
     * Class of the Go replayer:
     * {@link org.jikesrvm.replay.sys.global.GoReplayer}.
     */
    private static final Class<?> C =
        org.jikesrvm.replay.sys.global.GoReplayer.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.global.GoReplayer#beforeEvent}.
     */
    public static final NormalMethod BEFORE_EVENT = getMethod(C,
        "beforeEvent", "(Lorg/jikesrvm/scheduler/RVMThread;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.global.GoReplayer#afterEvent}.
     */
    public static final NormalMethod AFTER_EVENT = getMethod(C,
        "afterEvent",  "()V");
  }


  /**
   * Memory access entrypoints specific to the JaRec recorder mode.
   */
  public static final class JaRecRecorderEntrypoints {

    /**
     * Class of the JaRec recorder:
     * {@link org.jikesrvm.replay.sys.jarec.JaRecRecorder}.
     */
    private static final Class<?> C =
        org.jikesrvm.replay.sys.jarec.JaRecRecorder.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.jarec.JaRecRecorder#beforeEvent}.
     */
    public static final NormalMethod BEFORE_EVENT = getMethod(C,
        "beforeEvent",
        "(Lorg/jikesrvm/scheduler/RVMThread;Ljava/lang/Object;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.jarec.JaRecRecorder#afterEvent}.
     */
    public static final NormalMethod AFTER_EVENT = getMethod(C,
        "afterEvent",  "(Lorg/jikesrvm/scheduler/RVMThread;)V");
  }


  /**
   * Memory access entrypoints specific to the JaRec replayer mode.
   */
  public static final class JaRecReplayerEntrypoints {

    /**
     * Class of the JaRec replayer:
     * {@link org.jikesrvm.replay.sys.jarec.JaRecReplayer}.
     */
    private static final Class<?> C =
        org.jikesrvm.replay.sys.jarec.JaRecReplayer.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.jarec.JaRecReplayer#beforeEvent}.
     */
    public static final NormalMethod BEFORE_EVENT = getMethod(C,
        "beforeEvent", "(Lorg/jikesrvm/scheduler/RVMThread;)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.jarec.JaRecReplayer#afterEvent}.
     */
    public static final NormalMethod AFTER_EVENT = getMethod(C,
        "afterEvent",  "(Lorg/jikesrvm/scheduler/RVMThread;)V");
  }


  /**
   * Memory access entrypoints specific to the LEAP recorder mode.
   */
  public static final class LeapRecorderEntrypoints {

    /**
     * Class of the LEAP recorder:
     * {@link org.jikesrvm.replay.sys.leap.LeapRecorder}.
     */
    private static final Class<?> C =
        org.jikesrvm.replay.sys.leap.LeapRecorder.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.leap.LeapRecorder#beforeEvent}.
     */
    public static final NormalMethod BEFORE_EVENT = getMethod(C,
        "beforeEvent", "(Lorg/jikesrvm/scheduler/RVMThread;I)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.leap.LeapRecorder#afterEvent}.
     */
    public static final NormalMethod AFTER_EVENT = getMethod(C,
        "afterEvent",  "(Lorg/jikesrvm/scheduler/RVMThread;I)V");
  }

  /**
   * Memory access entrypoints specific to the LEAP replayer mode.
   */
  public static final class LeapReplayerEntrypoints {

    /**
     * Class of the LEAP replayer:
     * {@link org.jikesrvm.replay.sys.leap.LeapReplayer}.
     */
    private static final Class<?> C =
        org.jikesrvm.replay.sys.leap.LeapReplayer.class;

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.leap.LeapReplayer#beforeEvent}.
     */
    public static final NormalMethod BEFORE_EVENT = getMethod(C,
        "beforeEvent", "(Lorg/jikesrvm/scheduler/RVMThread;I)V");

    /**
     * Entrypoint for
     * {@link org.jikesrvm.replay.sys.leap.LeapReplayer#afterEvent}.
     */
    public static final NormalMethod AFTER_EVENT = getMethod(C,
        "afterEvent",  "(Lorg/jikesrvm/scheduler/RVMThread;I)V");
  }

  /** Hidden constructor. */
  private MemoryAccessEntrypoints() { }
}
