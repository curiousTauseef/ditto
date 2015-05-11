package org.jikesrvm.replay.instrumentation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.escape.FI_EscapeSummary;
import org.jikesrvm.compilers.opt.escape.SimpleEscape;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.tef.ThreadEscapedFields;

/**
 * Iterates through every memory access instruction in an IR which accesses
 * fields that the replay sub-system should trace.
 */
public final class MemoryAccessInstructionsIterator {

  /** IR being iterated. */
  private IR ir;

  /** List of memory access instructions in the IR. */
  private List<Instruction> instructions;

  /** Iterator over {@link #instructions}. */
  private Iterator<Instruction> iterator;

  /** Escape analysis results on the iterated IR. */
  private FI_EscapeSummary escapeSummary;

  /**
   * Constructor.
   * Initializes the iterator.
   * @param  ir IR to iterate
   */
  public MemoryAccessInstructionsIterator(IR ir) {
    this.ir = ir;
    instructions = new ArrayList<Instruction>();
    build();
    iterator = instructions.iterator();
  }

  /**
   * Checks if there are more instructions left in the iterator.
   * @return whether there are more instructions
   */
  public boolean hasNext() {
    return iterator.hasNext();
  }

  /**
   * Advances the iterator to the next instruction.
   * @return the next instruction
   */
  public Instruction next() {
    return iterator.next();
  }

  /**
   * Gets the first instruction after the method's prologue.
   * Code should only be injected after this instruction.
   * @return the first instruction
   */
  public Instruction getFirstInstructionAfterPrologue() {
    BasicBlock entryBB = ir.cfg.entry();
    Instruction f = entryBB.firstRealInstruction();
    if (f != null && f.getOpcode() == Operators.IR_PROLOGUE_opcode) {
      return f;
    } else {
      return entryBB.firstInstruction();
    }
  }

  /**
   * Initializes the iterator by filling the {@link #instructions} list.
   */
  private void build() {
    // save ourselves the trouble if the replay sub-system is not instrumenting
    // memory accesses.
    if (!ReplayManager.shouldWrapMemoryAccesses(ir.getMethod())) {
      return;
    }

    // iterate over all instructions and insert the memory access ones in the
    // iterator's list
    RVMMethod curMethod = null;
    boolean wrap = false;
    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder();
         bb != null;
         bb = bb.nextBasicBlockInCodeOrder()) {
      for (Instruction i = bb.firstInstruction();
           i != null && i.getBasicBlock() == bb;
           i = i.nextInstructionInCodeOrder()) {

        if (i.position == null) {
          continue;
        }

        // get the current method and check whether it should be instrumented
        // by the replay sub-system
        if (curMethod != i.position.getMethod()) {
          curMethod = i.position.getMethod();
          if (!ReplayManager.shouldWrapMemoryAccesses(curMethod)) {
            continue;
          }
        }

        // save the memory access instructions that target fields in which
        // the replay sub-system is interested
        switch (i.getOpcode()) {

          case Operators.REF_ALOAD_opcode:
          case Operators.BYTE_ALOAD_opcode:
          case Operators.UBYTE_ALOAD_opcode:
          case Operators.USHORT_ALOAD_opcode:
          case Operators.DOUBLE_ALOAD_opcode:
          case Operators.FLOAT_ALOAD_opcode:
          case Operators.LONG_ALOAD_opcode:
          case Operators.SHORT_ALOAD_opcode:
          case Operators.INT_ALOAD_opcode:
            if (shouldWrapArray(ALoad.getArray(i))) {
              instructions.add(i);
            }
            break;

          case Operators.REF_ASTORE_opcode:
          case Operators.BYTE_ASTORE_opcode:
          case Operators.DOUBLE_ASTORE_opcode:
          case Operators.FLOAT_ASTORE_opcode:
          case Operators.LONG_ASTORE_opcode:
          case Operators.SHORT_ASTORE_opcode:
          case Operators.INT_ASTORE_opcode:
            if (shouldWrapArray(AStore.getArray(i))) {
              instructions.add(i);
            }
            break;

          case Operators.GETSTATIC_opcode:
            if (shouldWrapField(GetStatic.getLocation(i).getFieldRef())) {
              instructions.add(i);
            }
            break;

          case Operators.PUTSTATIC_opcode:
            if (shouldWrapField(PutStatic.getLocation(i).getFieldRef())) {
              instructions.add(i);
            }
            break;

          case Operators.GETFIELD_opcode:
            if (shouldWrapField(GetField.getLocation(i).getFieldRef())) {
              instructions.add(i);
            }
            break;

          case Operators.PUTFIELD_opcode:
            if (shouldWrapField(PutField.getLocation(i).getFieldRef())) {
              instructions.add(i);
            }
            break;

          default:
            break;
        }
      }
    }
  }

  /**
   * Checks whether a given array operand references an array whose accesses
   * should be part of the iterator.
   * <p> If the operand is a register, escape analysis is used to ignore some
   * thread local accesses.
   * @param  arrayOp array operand
   * @return         whether the accesses to the array should be in the iterator
   */
  private boolean shouldWrapArray(Operand arrayOp) {
    return !arrayOp.isRegister()
         || !isThreadLocal(arrayOp.asRegister().getRegister());
  }

  /**
   * Checks whether accesses to a given field should be part of the iterator.
   * <p> Non-thread-escaped fields and final fields can be ignored.
   * @param  fr field reference
   * @return    whether accesses to the field should be in the iterator
   */
  private boolean shouldWrapField(FieldReference fr) {
    if (!ThreadEscapedFields.isFieldThreadEscaped(fr)) {
      return false;
    }
    RVMField field = fr.peekResolvedField();
    return field == null || !isFinal(field, fr);
  }

  /**
   * Checks whether a given field is final.
   * @param  field    the field
   * @param  fieldRef the field reference
   * @return          whether the field is final
   */
  private boolean isFinal(RVMField field, FieldReference fieldRef) {
    // for some reason the final modifier of java.lang.System.out, in and err
    // are stripped away during classloading... See ClassFileReader.
    return field.isFinal()
        || (fieldRef.getType() == TypeReference.JavaLangSystem
            && field.isStatic() && field.isPublic());
  }

  /**
   * Checks whether a given register is thread-local by using simple escape
   * analysis. Generates false negatives, but not false positives.
   * @param  r the register
   * @return   whether the register is thread-local
   */
  private boolean isThreadLocal(Register r) {
    if (!getOrCreateEscapeSummary().isThreadLocal(r)) {
      return false;
    }

    // we must further check whether the definition of the register comes from
    // non-escaped source.
    RegisterOperand defOp = r.defList;
    if (defOp != null) {
      Instruction defI = defOp.instruction;
      switch (defI.getOpcode()) {
        case Operators.NEWARRAY_opcode:
        case Operators.NEWARRAY_UNRESOLVED_opcode:
        case Operators.NEWOBJMULTIARRAY_opcode:
          // the array has been created inside the current method
          return true;

        case Operators.REF_ALOAD_opcode:
          // if the array comes from another array, check the source array
          return isThreadLocal(ALoad.getArray(defI).asRegister().getRegister());

        case Operators.REF_MOVE_opcode:
          // if the array comes from another register, check the register
          return isThreadLocal(Move.getVal(defI).asRegister().getRegister());

        default:
          // assume that it escapes for everything else...
          return false;
      }
    }
    return false;
  }

  /**
   * Performs escape analysis on the governing IR on-demand and saves the
   * results.
   * @return results of the escape analysis
   */
  private FI_EscapeSummary getOrCreateEscapeSummary() {
    if (escapeSummary == null) {
      escapeSummary = new SimpleEscape().simpleEscapeAnalysis(ir);
    }
    return escapeSummary;
  }
}
