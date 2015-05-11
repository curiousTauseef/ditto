package org.jikesrvm.replay;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.Callbacks;
import org.jikesrvm.Callbacks.ExitMonitor;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.replay.instrumentation.NullThreadLifetimeHandlers;
import org.jikesrvm.replay.instrumentation.ThreadLifetimeHandlers;
import org.jikesrvm.replay.modes.ReplayMode;
import org.jikesrvm.replay.tef.TEFFileException;
import org.jikesrvm.replay.tef.ThreadEscapedFields;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;

/**
 * Manager of the replay sub-system.
 * <p>
 * Provides an interface for the rest of the RVM to query the replay
 * sub-system.
 */
public final class ReplayManager implements ThreadLifetimeHandlers {

  /**
   * Main handler for thread lifetime events.
   * <p> If the replay sub-system is disabled the handlers will be null
   * ({@link org.jikesrvm.replay.instrumentation.NullThreadLifetimeHandlers}).
   * Otherwise, the handlers are an instance of {@link ReplayManager} itself.
   */
  public static ThreadLifetimeHandlers threadLifetime;

  /**
   * Additional handlers for thread lifetime events, provided by the current
   * mode.
   */
  private static ThreadLifetimeHandlers[] childThreadLifetime;

  static {
    threadLifetime = new NullThreadLifetimeHandlers();
  }

  /** The current mode of the replay sub-system. */
  private static ReplayMode mode;

  /**
   * Gets the current mode of the replay sub-system.
   * @return current mode
   */
  public static ReplayMode getReplayMode() {
    return mode;
  }

  /**
   * Checks whether the replay sub-system is enabled.
   * @return whether the replay sub-system is enabled.
   */
  public static boolean isEnabled() {
    return ReplayOptions.MODE != ReplayOptions.ReplayMode.Disabled;
  }


  ///
  /// Initialization
  ///

  /**
   * Callback for when the RVM is fully booted.
   * <p> Initializes the replay sub-system.
   */
  public static void fullyBootedVM() {
    // if disabled, do nothing
    if (!isEnabled()) {
      return;
    }

    // the main thread lifetime handlers are provided by an instance of
    // ReplaManager
    threadLifetime = new ReplayManager();

    try {
      // create a ReplayMode instance for the current mode
      mode = ReplayMode.getCurrentMode();

      // get the thread lifetime handlers provided by the mode
      childThreadLifetime = mode.getThreadLifetimeHandlers();

      // initialize the thread-escaped-fields directory
      initThreadEscapedFields();

      // initialize the mode itself
      mode.init();

      // if the current mode requires the VM's exit to be trapped, initialize
      // an exit monitor and a finish-trace thread
      if (mode.trapsExits()) {
        ReplayExitMonitor.register();
        FinishTraceThread.init();
      }
    } catch (ReplayException re) {
      failAndExit(re);
    }

    // initialize the should-wrap-method cache
    shouldWrapMethodCache = Collections.synchronizedMap(
        new HashMap<Object, Boolean>());
  }

  /**
   * Initializes the thread-escaped-fields directory, by reading the provided
   * TEF file.
   * <p> If there is no TEF file and the current mode requires that one is
   * provided by the user, the VM is terminated.
   */
  private static void initThreadEscapedFields() {
    // if there is a user-provided TEF file, read it
    if (ReplayOptions.THREAD_ESCAPED_FIELDS_FILE_NAME != null) {
      try {
        ThreadEscapedFields.readFromFile(
            ReplayOptions.THREAD_ESCAPED_FIELDS_FILE_NAME);
      } catch (TEFFileException teffe) {
        failAndExit(teffe);
      }
    }
    // otherwise, try to read a TEF file from the default location
    else {
      try {
        ThreadEscapedFields.readFromFile(
            ReplayOptions.THREAD_ESCAPED_FIELDS_DEFAULT_FILE_NAME);
      } catch (TEFFileException teffe) {
        // if no TEF file was explicitly provided and there isn't one in the
        // default location, we can still continue executing, unless the
        // current mode requires a TEF file.
        if (mode.requiresTEFFile()
            || !(teffe.getCause() instanceof FileNotFoundException)) {
          failAndExit(teffe);
        }
      }
    }
  }

  /**
   * An exit monitor used to provide the replay sub-system with an opportunity
   * to wrap-up.
   */
  private static final class ReplayExitMonitor implements ExitMonitor {

    /** Registers a replay exit monitor with the RVM. */
    public static void register() {
      Callbacks.addExitMonitor(new ReplayExitMonitor());
    }

    @Override
    public void notifyExit(int value) {
      // if the reason for VM termination is a failure of the replay sub-system
      // itself, just exit...
      if (value == VM.EXIT_STATUS_REPLAY_FAILED) {
        return;
      }

      try {
        // give the current mode a chance to terminate cleanly
        mode.terminate();
      } catch (ReplayException re) {
        failAndExit(re);
      }
    }

    /** Constructor (hidden). */
    private ReplayExitMonitor() { }
  }


  ///
  /// RVM exit conditions
  ///

  /**
   * Terminates the RVM due to an exception related to the replay sub-system.
   * @param ex replay sub-system exception
   */
  public static void failAndExit(Exception ex) {
    VM.sysWriteln("Replayer: Trouble.");
    ex.printStackTrace();
    VM.sysWriteln("Replayer: Exiting...");
    VM.sysExit(VM.EXIT_STATUS_REPLAY_FAILED);
  }

  /**
   * Processes a command line argument for the replay sub-system.
   * <p>Terminates the RVM if the argument is unrecognized.
   * @param prefix prefix used for replay sub-system arguments
   * @param arg    argument (in the form key=value)
   */
  public static void processCommandLineArg(String prefix, String arg) {
    if (!ReplayOptions.processAsOption(prefix, arg)) {
      VM.sysWriteln("Replayer: Unrecognized argument \"" + arg + "\"");
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
  }


  ///
  /// Thread lifetime handlers
  ///

  /**
   * Entrypoint called after the current thread calls
   * {@link java.lang.Thread#join} on another thread.
   * @param joined thread to which the curren thread joined
   */
  @Entrypoint
  public static void threadJoined(Thread joined) {
    threadLifetime.threadJoining(RVMThread.getCurrentThread(), joined);
  }

  @Override
  public void threadBeforeStarting(RVMThread parent, RVMThread child) {
    for (ThreadLifetimeHandlers childWrapper : childThreadLifetime) {
      childWrapper.threadBeforeStarting(parent, child);
    }
    ConsistentThreadIdentifier.threadStarting(parent, child);
    threadBeforeStartingAfterId(parent, child);
  }

  @Override
  public void threadBeforeStartingAfterId(RVMThread parent, RVMThread child) {
    for (ThreadLifetimeHandlers childWrapper : childThreadLifetime) {
      childWrapper.threadBeforeStartingAfterId(parent, child);
    }
  }

  @Override
  public void threadAfterStarting(RVMThread parent, RVMThread child) {
    for (ThreadLifetimeHandlers childWrapper : childThreadLifetime) {
      childWrapper.threadAfterStarting(parent, child);
    }
  }

  @Override
  public void threadJoining(RVMThread joining, Thread joined) {
    for (ThreadLifetimeHandlers childWrapper : childThreadLifetime) {
      childWrapper.threadJoining(joining, joined);
    }
  }

  @Override
  public void threadTerminating(RVMThread t) {
    for (ThreadLifetimeHandlers childWrapper : childThreadLifetime) {
      childWrapper.threadTerminating(t);
    }
  }


  ///
  /// Instrumentation
  ///

  /** Cache used by {@link #shouldWrapMethod}. */
  private static Map<Object, Boolean> shouldWrapMethodCache;

  /**
   * Checks whether a given method should be instrumented by the replay
   * sub-system.
   * <p> Uses an input->output cache.
   * @param  method the method
   * @return        whether the method should be instrumented
   */
  @Inline
  private static boolean shouldWrapMethod(RVMMethod method) {
    Boolean result = shouldWrapMethodCache.get((Object)method);
    if (result == null) {
      result = !VM.writingBootImage
            && !method.isClassInitializer()
            && !method.getDeclaringClass().isInBootImage()
            && ReplayUtils.isMethodFromNonIgnoredPackage(method);
      shouldWrapMethodCache.put(method, result);
    }
    return result;
  }

  /**
   * Checks whether a given method's memory accesses should be intrumented
   * by the replay sub-system.
   * @param  method the method
   * @return        whether the method should be instrumented
   */
  public static boolean shouldWrapMemoryAccesses(RVMMethod method) {
    return mode != null && mode.wrapsMemoryAccesses()
        && shouldWrapMethod(method);
  }

  /**
   * Checks whether a given method's synchronization operations should be
   * instrumented by the replay sub-system.
   * @param  method the method
   * @return        whether the method should be instrumented
   */
  public static boolean shouldWrapSync(RVMMethod method) {
    return mode != null && mode.wrapsSynchronization()
        && shouldWrapMethod(method);
  }

  /**
   * Performs a runtime generic lock, requested by a @{link ReplayerMethod},
   * and traces it if necessary.
   * <p> If the lock is to be traced, the lock is performed by the wrapper
   * provided by the current mode.
   * Otherwise, {@link org.jikesrvm.objectmodel.ObjectModel#genericLock} is
   * used.
   * @param obj  object to lock
   * @param rm   replayer method that requested the lock
   */
  public static void runtimeGenericLock(Object obj, ReplayerMethod rm) {
    if (mode.wrapsSynchronization()
        && ReplayUtils.wasCalledFromNonIgnoredPackage(rm)) {
      runtimeGenericLock(obj);
    } else {
      ObjectModel.genericLock(obj);
    }
  }

  /**
   * Performs and traces a runtime generic lock, i.e, a lock that could not be
   * instrumented at compile-time. The lock is performed by the wrapper
   * provided by the current mode.
   * @param obj object to lock
   */
  public static void runtimeGenericLock(Object obj) {
    mode.runtimeGenericLock(obj);
  }

  /**
   * Entrypoint used by instrumentation to inform the replay sub-system, during
   * runtime, that a subsequent special synchronization operation is to be
   * traced.
   * <p> Examples of such special synchronization operations are a thread
   * suspending, parking or waiting.
   */
  @Inline
  @Entrypoint
  public static void traceNextSyncMethod() {
    RVMThread t = RVMThread.getCurrentThread();
    t.traceNextSyncMethod = true;
  }

  /** Private constructor. */
  private ReplayManager() { }
}
