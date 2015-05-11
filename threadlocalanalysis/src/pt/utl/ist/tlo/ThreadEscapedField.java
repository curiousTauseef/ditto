package pt.utl.ist.tlo;

import soot.SootField;

/**
 * A thread-escaped field identified by the TLO analysis.
 */
public final class ThreadEscapedField {

  /** The soot field representation of the thread-escaped field. */
  public SootField field;

  /**
   * Constructor.
   * @param  field soot field
   */
  public ThreadEscapedField(SootField field) {
    this.field = field;
  }

  @Override
  public int hashCode() {
    return field.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ThreadEscapedField) {
      return field.equals(((ThreadEscapedField)o).field);
    }
    return false;
  }

  @Override
  public String toString() {
    return "L" + field.getDeclaringClass().getName().replace('.', '/') + ";"
        + field.getName();
  }
}
