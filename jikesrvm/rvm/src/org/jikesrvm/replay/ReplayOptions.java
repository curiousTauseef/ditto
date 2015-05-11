package org.jikesrvm.replay;

import java.util.ArrayList;
import java.util.List;

import org.jikesrvm.CommandLineArgs;
import org.jikesrvm.VM;

/**
 * Manages command line options of the replay subsystem.
 * Also manages some non-command-line options.
 */
public final class ReplayOptions {

  /** Whether debug information is active for the replay subsystem. */
  public static final boolean DEBUG = true;

  /** Possible values for the 'mode' option. */
  public enum ReplayMode {
    /** Disabled. */                 Disabled    ("disabled"),
    /** Sync record mode. */         SyncRecord  ("sync_record"),
    /** Sync replay mode. */         SyncReplay  ("sync_replay"),
    /** Ditto record mode. */        DittoRecord ("ditto_record"),
    /** Ditto replay mode. */        DittoReplay ("ditto_replay"),
    /** Global order record mode. */ GoRecord    ("go_record"),
    /** Global order replay mode. */ GoReplay    ("go_replay"),
    /** JaRec record mode. */        JaRecRecord ("jarec_record"),
    /** JaRec replay mode. */        JaRecReplay ("jarec_replay"),
    /** LEAP record mode. */         LeapRecord  ("leap_record"),
    /** LEAP replay mode. */         LeapReplay  ("leap_replay");

    /** The 'mode' option value that corresponds to the enum value. */
    private final String optionValue;

    /**
     * Constructor.
     * @param  optValue value for the 'mode' option that maps to this mode
     */
    private ReplayMode(String optValue) {
      optionValue = optValue;
    }

    @Override
    public String toString() {
      return optionValue;
    }
  };

  /**
   * List of default ignored packages, i.e, packages which will not be taken
   * into account by the replay subsystem.
   */
  public static final String[] IGNORED_PACKAGES = new String[] {
    "org.jikesrvm.",
    "org.mmtk.",
    "org.vmmagic.",
    "java.",
    "javax.",
    "com.sun.",
    "sun.",
    "gnu.java.",
    "gnu.classpath.",
    "org.apache."
  };

  /** Default thread-escaped-fields filename. */
  public static final String THREAD_ESCAPED_FIELDS_DEFAULT_FILE_NAME =
      "thread_escaped_fields.tef";

  //
  // Command line options
  //

  /** Verbosity level of the debug dumped by the replay subsystem. */
  public static int VERBOSITY = 0;

  /**
   * Mode of the replay subsystem.
   * <p> Defaults to {@link ReplayMode#Disabled}.
   */
  public static ReplayMode MODE = ReplayMode.Disabled;

  /**
   * Whether the replay subsystem should report on thread start events.
   * <p> Defaults to {@code false}.
   */
  public static boolean REPORT_THREAD_START = false;

  /**
   * Filename of the synchronization recording trace.
   * <p> Defaults to {@code sync_recording.rec}.
   */
  public static String SYNC_TRACE_FILE_NAME = "sync_recording.rec";

  /**
   * Filename of the recording trace.
   * <p> Defaults to {@code recording.rec}.
   */
  public static String TRACE_FILE_NAME = "recording.rec";

  /**
   * Extra user-provided ignored packages, which are considered in addition
   * to the default ones in {@link #IGNORED_PACKAGES}.
   */
  public static List<String> EXTRA_IGNORED_PACKAGES = null;

  /**
   * Number of I/O threads
   * ({@link org.jikesrvm.replay.tracefile.ditto.WriterThread}) used by the
   * replay subsystem.
   * <p> Defaults to 2.
   */
  public static int NUM_IO_THREADS = 2;

  /**
   * Filename of the thread-escaped-fields file.
   * Defaults to {@code null}, but there is a default filename at
   * {@link #THREAD_ESCAPED_FIELDS_DEFAULT_FILE_NAME}. This is so we can
   * have a default value, but know whether or not it was provided by the user.
   */
  public static String THREAD_ESCAPED_FIELDS_FILE_NAME = null;

  /**
   * Whether the replay subsystem should consider different instances of the
   * same class as separate entities for tracing.
   * <p> Defaults to {@code true}.
   */
  public static boolean INDIVIDUAL_OBJECTS = true;

  /**
   * Whether the replay subsystem should consider different fields of the same
   * object as separate entities for tracing.
   * <p> Default to {@code true}.
   */
  public static boolean INDIVIDUAL_FIELDS = true;

  /**
   * Maximum number of separate entities considered for tracing by the replay
   * subsystem per array.
   * <p> Defaults to {@link ReplayConstants#DEFAULT_MAX_ARRAY_FIELDS}.
   * <p> A value of 10, means that an array with 50 fields will be traced as
   * though it had only 10 fields, by aggregating them in groups of 5.
   */
  public static int MAX_ARRAY_FIELDS = ReplayConstants.DEFAULT_MAX_ARRAY_FIELDS;

  /**
   * Whether the replay subsystem should dump debug data related to the
   * thread-escaped-fields.
   * <p> Defaults to {@code false}.
   */
  public static boolean VERBOSE_TEF = false;

  /**
   * Whether the replay subsystem should dump debug data related to the
   * instrumentation it performs.
   * <p> Defaults to {@code false}.
   */
  public static boolean VERBOSE_INSTRUMENTATION = false;

  /**
   * Processes an option to the replay sub-system.
   * @param  prefix prefix of the option
   * @param  arg    option in key=value form
   * @return        whether the options was processed successfully
   */
  @org.vmmagic.pragma.NoOptCompile
  public static boolean processAsOption(String prefix, String arg) {

    if (arg.length() == 0 || arg.equals("help")) {
      printHelp(prefix);
      return true;
    }

    if (arg.equals("printOptions")) {
      printOptions();
      return true;
    }

    int split = arg.indexOf('=');
    if (split == -1) {
      VM.sysWriteln("  Illegal options specification!");
      VM.sysWriteln("  \"" + arg + "\" must be specified as a name-value pair "
                    + "in the form of option=value");
      return false;
    }

    String name = arg.substring(0, split);
    String value = arg.substring(split + 1);

    if (name.equals("verbosity")) {
      VERBOSITY = CommandLineArgs.primitiveParseInt(value);
      return true;
    }

    if (name.equals("mode")) {
      if (value.equals(ReplayMode.DittoRecord.toString())) {
        MODE = ReplayMode.DittoRecord;
      } else if (value.equals(ReplayMode.DittoReplay.toString())) {
        MODE = ReplayMode.DittoReplay;
      } else if (value.equals(ReplayMode.GoRecord.toString())) {
        MODE = ReplayMode.GoRecord;
      } else if (value.equals(ReplayMode.GoReplay.toString())) {
        MODE = ReplayMode.GoReplay;
      } else if (value.equals(ReplayMode.JaRecRecord.toString())) {
        MODE = ReplayMode.JaRecRecord;
      } else if (value.equals(ReplayMode.JaRecReplay.toString())) {
        MODE = ReplayMode.JaRecReplay;
      } else if (value.equals(ReplayMode.LeapRecord.toString())) {
        MODE = ReplayMode.LeapRecord;
      } else if (value.equals(ReplayMode.LeapReplay.toString())) {
        MODE = ReplayMode.LeapReplay;
      } else if (value.equals(ReplayMode.SyncRecord.toString())) {
        MODE = ReplayMode.SyncRecord;
      } else if (value.equals(ReplayMode.SyncReplay.toString())) {
        MODE = ReplayMode.SyncReplay;
      } else {
        return false;
      }
      return true;
    }

    if (name.equals("report_thread_start")) {
      if (value.equals("true")) {
        REPORT_THREAD_START = true;
      } else if (value.equals("false")) {
        REPORT_THREAD_START = false;
      } else {
        return false;
      }
      return true;
    }

    if (name.equals("sync_trace_file")) {
      SYNC_TRACE_FILE_NAME = value;
      return true;
    }

    if (name.equals("trace_file")) {
      TRACE_FILE_NAME = value;
      return true;
    }

    if (name.equals("ignore_package")) {
      if (EXTRA_IGNORED_PACKAGES == null) {
        EXTRA_IGNORED_PACKAGES = new ArrayList<String>();
      }
      EXTRA_IGNORED_PACKAGES.add(value);
      return true;
    }

    if (name.equals("num_io_threads")) {
      NUM_IO_THREADS = CommandLineArgs.primitiveParseInt(value);
      return true;
    }

    if (name.equals("tef_file")) {
      THREAD_ESCAPED_FIELDS_FILE_NAME = value;
      return true;
    }

    if (name.equals("individual_fields")) {
      if (value.equals("true")) {
        INDIVIDUAL_FIELDS = true;
      } else if (value.equals("false")) {
        INDIVIDUAL_FIELDS = false;
      } else {
        return false;
      }
      return true;
    }

    if (name.equals("individual_objects")) {
      if (value.equals("true")) {
        INDIVIDUAL_OBJECTS = true;
      } else if (value.equals("false")) {
        INDIVIDUAL_OBJECTS = false;
      } else {
        return false;
      }
      return true;
    }

    if (name.equals("max_array_fields")) {
      MAX_ARRAY_FIELDS = CommandLineArgs.primitiveParseInt(value);
      return true;
    }

    if (name.equals("verbose_tef")) {
      if (value.equals("true")) {
        VERBOSE_TEF = true;
      } else if (value.equals("false")) {
        VERBOSE_TEF = false;
      } else {
        return false;
      }
      return true;
    }

    if (name.equals("verbose_instrumentation")) {
      if (value.equals("true")) {
        VERBOSE_INSTRUMENTATION = true;
      } else if (value.equals("false")) {
        VERBOSE_INSTRUMENTATION = false;
      } else {
        return false;
      }
      return true;
    }

    return false;
  }

  /**
   * Prints help information regarding the replay sub-system's options.
   * @param prefix [description]
   */
  public static void printHelp(String prefix) {
    VM.sysWriteln("Commands");
    VM.sysWriteln(prefix + "[:help]       "
        + "Print a bried description of replay command-line options");
    VM.sysWriteln(prefix + ":printOptions "
        + "Print the current replay option values");
    VM.sysWriteln(prefix + ":o=v          "
        + "Pass the option-value pair, o=v, to the replay system");
    VM.sysWriteln("");

    VM.sysWriteln("Boolean Options (" + prefix + ":<option>=true or "
        + prefix + ":<option>=false)");
    VM.sysWriteln("report_thread_start      "
        + "Report thread starts");
    VM.sysWriteln("individual_objects       "
        + "Trace objects of the same class as individual entities?");
    VM.sysWriteln("individual_fields        "
        + "Trace different fields of same object as individual entities?");
    VM.sysWriteln("verbose_tef              "
        + "Dump thread escaped fields info.");
    VM.sysWriteln("verbose_instrumentation  "
        + "Dump info about instrumented instructions.");
    VM.sysWriteln("");

    VM.sysWriteln("Value Options (" + prefix + ":<option>=<value>)");
    VM.sysWriteln("verbosity             int      "
        + "Level of verbosity (0-2)");
    VM.sysWriteln("sync_trace_file       String   "
        + "Filename of the synchronization-only trace file");
    VM.sysWriteln("trace_file            String   "
        + "Filename of the trace file");
    VM.sysWriteln("tef_file              String   "
        + "Filename of the thread escaped fields file.");
    VM.sysWriteln("num_io_threads        int      "
        + "Number of IO threads.");
    VM.sysWriteln("max_array_fields      int      "
        + "Maximum number of fields for tracing each array instance.");
    VM.sysWriteln("");

    VM.sysWriteln("Selection Options (set to one of the possible values)");
    VM.sysWrite("mode                  ");
    for (ReplayMode mode : ReplayMode.values()) {
      VM.sysWrite(mode + " ");
    }
    VM.sysWriteln("");
    VM.sysWriteln("");

    VM.sysWriteln("Set Options (one option per value)");
    VM.sysWriteln("ignore_package        Packages that should not be traced");

    VM.sysExit(VM.EXIT_STATUS_PRINTED_HELP_MESSAGE);
  }

  /**
   * Prints the current value of each option.
   */
  @org.vmmagic.pragma.NoOptCompile
  public static void printOptions() {
    VM.sysWriteln("Current replay options:");
    VM.sysWriteln("  report_thread_start     = " + REPORT_THREAD_START);
    VM.sysWriteln("  individual_objects      = " + INDIVIDUAL_OBJECTS);
    VM.sysWriteln("  individual_fields       = " + INDIVIDUAL_FIELDS);
    VM.sysWriteln("  verbose_tef             = " + VERBOSE_TEF);
    VM.sysWriteln("  verbose_instrumentation = " + VERBOSE_INSTRUMENTATION);
    VM.sysWriteln("  verbosity               = " + VERBOSITY);
    VM.sysWriteln("  sync_trace_file         = " + SYNC_TRACE_FILE_NAME);
    VM.sysWriteln("  trace_file              = " + TRACE_FILE_NAME);
    VM.sysWriteln("  tef_file                = "
        + THREAD_ESCAPED_FIELDS_FILE_NAME);
    VM.sysWriteln("  io_threads              = " + NUM_IO_THREADS);
    VM.sysWriteln("  max_array_fields        = " + MAX_ARRAY_FIELDS);
    VM.sysWriteln("  mode                    = " + MODE);
    VM.sysWriteln("  ignore_package          = "
        + (EXTRA_IGNORED_PACKAGES == null ? "[]"
                                          : EXTRA_IGNORED_PACKAGES.toString()));
  }

  /** Hidden constructor. */
  private ReplayOptions() { }
}
