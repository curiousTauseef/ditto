package pt.utl.ist.tlo;

import java.util.Map;

import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.jimple.toolkits.thread.ThreadLocalObjectsAnalysis;
import soot.jimple.toolkits.thread.mhp.SynchObliviousMhpAnalysis;
import soot.util.Chain;

/**
 * Performs Thread Local Objects Analysis on the whole application. Afterwards,
 * identifies the thread-escaped fields by calling {@link ThreadEscapedLocator}.
 */
public final class WholeProgramTLOTransformer extends SceneTransformer {

  /**
   * Executes the transformer.
   * @param _  ignored
   * @param __ ignored
   */
  protected void internalTransform(String _, Map __) {
    // execute TLO analysis
    ThreadLocalObjectsAnalysis tlo =
        new ThreadLocalObjectsAnalysis(new SynchObliviousMhpAnalysis());

    // locate thread-escaped fields
    ThreadEscapedLocator loc = ThreadEscapedLocator.INSTANCE;
    loc.init(tlo);
    Chain<SootClass> classes = Scene.v().getApplicationClasses();
    for (SootClass sc : classes) {
      loc.processClass(sc);
    }
  }
}
