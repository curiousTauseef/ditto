package pt.utl.ist.tlo;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

import soot.Main;
import soot.PackManager;
import soot.PhaseOptions;
import soot.Scene;
import soot.SootClass;
import soot.Transform;
import soot.jimple.spark.SparkTransformer;
import soot.options.Options;

/**
 * Executes thread-local analysis on a Java application.
 * <p>
 * The output of the program is a .tef file containing the names of the
 * thread-escaped-fields in the application.
 * <p>
 * For applications that use reflection, a reflection log can be provided so
 * that thread-local analysis can be performed on the classes used through
 * reflection.
 */
public final class TLO {

  /**
   * Default name for the output file containing the application's
   * thread-escaped fields.
   */
  private static final String DEFAULT_REPORT_FILENAME =
      "thread_escaped_fields.tef";

  /**
   * Main program handler.
   * @param args command line arguments
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: java pt.utl.ist.tlo.TLO <main_class> "
                       + "[-f tef_output_filename] [-rl reflection_log]");
      System.exit(1);
    }

    String mainClass = args[0];
    String tefFilename = DEFAULT_REPORT_FILENAME;
    String reflectionLog = null;

    // process command line argments
    for (int i = 1; i + 1 < args.length; i += 2) {
      if (args[i].equals("-f")) {
        tefFilename = args[i + 1];
      } else if (args[i].equals("-rl")) {
        reflectionLog = args[i + 1];
      }
    }

    // set soot options
    setOptionsBeforeMainClassLoad(reflectionLog);
    SootClass appClass = Scene.v().loadClassAndSupport(mainClass);
    Scene.v().setMainClass(appClass);
    setOptionsAfterMainClassLoad();

    // run the analysis
    Main.main(new String[] {mainClass});

    // write the output tef file
    writeTefFile(tefFilename);
    printResults();
  }

  /**
   * Sets soot options that must be present before loading the main class of
   * the application.
   * @param reflectionLog optional reflection log filename
   */
  private static void setOptionsBeforeMainClassLoad(String reflectionLog) {
    Scene.v().setSootClassPath(
        System.getProperty("sun.boot.class.path") + File.pathSeparator
        + System.getProperty("java.class.path")
    );

    if (reflectionLog != null) {
      PhaseOptions.v().setPhaseOption("cg", "reflection-log:" + reflectionLog);
    }

    PhaseOptions.v().setPhaseOption("jb", "enabled:true");
    PhaseOptions.v().setPhaseOption("jb", "use-original-names:true");
    Options.v().set_keep_line_number(true);
    Options.v().set_whole_program(true);
    Options.v().set_app(true);
    Options.v().set_output_format(0);
    Options.v().set_allow_phantom_refs(true);

    HashMap<String, String> opt = new HashMap<String, String>();
    opt.put("propagator", "worklist");
    opt.put("simple-edges-bidirectional", "false");
    opt.put("on-fly-cg", "true");
    opt.put("set-impl", "double");
    opt.put("double-set-old", "hybrid");
    opt.put("double-set-new", "hybrid");
    opt.put("pre_jimplify", "true");
    SparkTransformer.v().transform("", opt);
    PhaseOptions.v().setPhaseOption("cg.spark", "enabled:true");

    PackManager.v().getPack("wjtp").add(
        new Transform("wjtp.transformer", new WholeProgramTLOTransformer()));
  }

  /**
   * Sets soot options that must only be provided after loading the main class
   * of the application.
   */
  private static void setOptionsAfterMainClassLoad() {
    Options.v().set_exclude(Arrays.asList(new String[] {
        "java.", "javax.", "sun.", "com.", "org.apache."
    }));
    Options.v().set_no_bodies_for_excluded(true);
    //Options.v().set_validate(true);

    Scene.v().addBasicClass(
        "com.sun.org.apache.xerces.internal.impl.xs.traversers.OneAttr", 1);

  }

  /**
   * Writes the output tef file.
   * @param filename output tef filename
   */
  private static void writeTefFile(String filename) {
    TefFile tefFile = new TefFile(filename);
    try {
      tefFile.init();
      tefFile.fillWith(ThreadEscapedLocator.INSTANCE.getThreadEscapedFields());
      tefFile.terminate();
    } catch (TefFileException tfe) {
      System.err.println("Error: " + tfe.getMessage());
      System.exit(1);
    }
  }

  /**
   * Prints the results of the analysis to standard output.
   */
  private static void printResults() {
    System.out.println("");
    System.out.println("*************************************");
    System.out.println("*      Thread escaped fields        *");
    System.out.println("*************************************");
    System.out.println("");
    for (ThreadEscapedField s
        : ThreadEscapedLocator.INSTANCE.getSortedThreadEscapedFields()) {
      System.out.println(s.toString());
    }
    System.out.println("*************************************");
  }

  /** Hidden constructor. */
  private TLO() { }
}
