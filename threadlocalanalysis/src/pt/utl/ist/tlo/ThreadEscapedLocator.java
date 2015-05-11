package pt.utl.ist.tlo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import soot.Body;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.ConcreteRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.Stmt;
import soot.jimple.toolkits.thread.ThreadLocalObjectsAnalysis;
import soot.util.Chain;

/**
 * Identifies thread-escaped fields.
 */
public final class ThreadEscapedLocator {

  /** Singleton instance. */
  public static final ThreadEscapedLocator INSTANCE =
      new ThreadEscapedLocator();

  /** An instance of soot's thread-local-objects analysis. */
  private ThreadLocalObjectsAnalysis tlo;

  /** Set of already-identified thread-escaped fields. */
  private HashSet<ThreadEscapedField> threadEscapedFields;

  /** Private constructor. */
  private ThreadEscapedLocator() { }

  /**
   * Initializes the locator.
   * @param tlo soot's thread-local-objects analysis
   */
  public void init(ThreadLocalObjectsAnalysis tlo) {
    this.tlo = tlo;
    threadEscapedFields = new HashSet<ThreadEscapedField>();
  }

  /**
   * Gets a list of identified thread-escaped fields.
   * @return list of thread-escaped fields
   */
  public List<ThreadEscapedField> getThreadEscapedFields() {
    return new ArrayList<ThreadEscapedField>(threadEscapedFields);
  }

  /**
   * Gest a list of identified thread-escaped fields, sorted alphabetically.
   * @return alphabetically sorted list of thread-escaped fields
   */
  public List<ThreadEscapedField> getSortedThreadEscapedFields() {
    List<ThreadEscapedField> tef =
        new ArrayList<ThreadEscapedField>(threadEscapedFields);

    Collections.sort(tef, new Comparator<ThreadEscapedField>() {
      public int compare(ThreadEscapedField a, ThreadEscapedField b) {
        return a.toString().compareTo(b.toString());
      }
    });

    return tef;
  }


  ///
  /// Identify thread-escaped fields
  ///

  /**
   * Goes through the methods of a given class and tries to locate
   * thread-escaped fields referenced by them.
   * @param sClass soot class to process
   */
  public void processClass(SootClass sClass) {
    List<SootMethod> methods = sClass.getMethods();
    for (SootMethod sMethod : methods) {
      processMethod(sMethod);
    }
  }

  /**
   * Goes through each statement in a given method and tries to locate
   * thread-escaped fields referenced by them.
   * @param sMethod soot method to process
   */
  private void processMethod(SootMethod sMethod) {
    if (!shouldIgnoreMethod(sMethod)) {
      Body body = sMethod.retrieveActiveBody();
      Chain<Unit> units = body.getUnits();
      for (Unit u : units) {
        processStmt(sMethod, (Stmt)u);
      }
    }
  }

  /**
   * Checks whether a given method should be ignored in regards to the
   * identification of thread-escaped fields.
   * @param  sMethod soot method to check
   * @return         whether the method is to be ignored
   */
  private boolean shouldIgnoreMethod(SootMethod sMethod) {
    return sMethod.isAbstract()
        || sMethod.isNative()
        || sMethod.getName().equals("<clinit>")
        || sMethod.getName().equals("<init>");
  }

  /**
   * Checks assignment statements to find thread-escaped fields.
   * @param sMethod soot method to which the statement belongs
   * @param stmt    statment to process
   */
  private void processStmt(SootMethod sMethod, Stmt stmt) {
    // only interested in references on the left hand side of assignment
    // statements
    if (stmt instanceof AssignStmt) {
      AssignStmt as = (AssignStmt)stmt;
      Value lValue = as.getLeftOp();
      if (lValue instanceof ConcreteRef) {
        processConcreteRef(sMethod, (ConcreteRef)lValue);
      }
      /*if (as.getRightOp() instanceof ArrayRef) {
        processArrayRef(sMethod, (ArrayRef)as.getRightOp());
      }*/
    }
  }

  /**
   * Processes a reference to an instance, static or array field to find
   * if the field is thread-escaped.
   * @param sMethod   soot method in which the reference exists
   * @param ref       field reference
   */
  private void processConcreteRef(SootMethod sMethod, ConcreteRef ref) {
    if (ref instanceof InstanceFieldRef) {
      processInstanceFieldRef(sMethod, (InstanceFieldRef)ref);
    }/* else if (ref instanceof StaticFieldRef) {
      processStaticFieldRef(sMethod, (StaticFieldRef)ref);
    } else if (ref instanceof ArrayRef) {
      processArrayRef(sMethod, (ArrayRef)ref);
    }*/ else {
      throw new RuntimeException("Invalid concrete ref.");
    }
  }

  /**
   * Process a reference to an instace field to find if the field is
   * thread-escaped.
   * @param sMethod   soot method in which the reference exists
   * @param ref instance field reference
   */
  private void processInstanceFieldRef(SootMethod sMethod,
                                       InstanceFieldRef ref) {
    if (!ref.getField().isFinal() && !tlo.isObjectThreadLocal(ref, sMethod)) {
      threadEscapedFields.add(new ThreadEscapedField(ref.getField()));
    }
  }

  // soot's TLO analysis doesn't seem to do a good job with static and array
  // references. thus, all static and array accesses are assumed
  // thread-escaped by Ditto

  /*private void processStaticFieldRef(SootMethod sMethod, StaticFieldRef ref) {
    if (!ref.getField().isFinal() && !tlo.isObjectThreadLocal(ref, m)) {
      threadEscapedFields.add(new ThreadEscapedField(ref.getField()));
    }
  }

  private void processArrayRef(SootMethod sMethod, ArrayRef ref) {
    System.out.println("Is " + ref + " of type " + ref.getType()
        + " in " + m + " local? " + tlo.isObjectThreadLocal(ref, m));
  }*/
}
