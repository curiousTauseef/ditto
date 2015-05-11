package org.jikesrvm.replay.instrumentation;

import java.lang.reflect.Constructor;

import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.replay.tracefile.leap.FieldNameIdMap;

/**
 * An OPT compiler phase that wraps memory accesses in calls to the replay
 * sub-system when recording or replaying in LEAP mode.
 * <p>
 * All memory access instructions are wrapped in two calls to handlers of the
 * replay sub-system. Both handlers receive two arguments: the current thread
 * and the id of the field being accesses, as provided by
 * {@link org.jikesrvm.replay.tracefile.leap.FieldNameIdMap}.
 * <p>
 * The current thread object is reused as far as possible when calling the
 * handlers.
 */
public final class LeapWrapMemoryAccesses extends CompilerPhase {

  /** The IR on which the phase is being performed. */
  private IR ir;

  @Override
  public String getName() {
    return "Wrap Memory Accesses For Leap Replay";
  }

  /** Constructor for this compiler phase. */
  private static final Constructor<CompilerPhase> CONSTRUCTOR =
      getCompilerPhaseConstructor(LeapWrapMemoryAccesses.class);

  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return CONSTRUCTOR;
  }

  @Override
  public void perform(IR ir) {
    this.ir = ir;
    MemoryAccessInstructionsIterator it =
        new MemoryAccessInstructionsIterator(ir);

    Instruction i;
    while (it.hasNext()) {
      i = it.next();

      IntConstantOperand fieldIdOp = null;

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
        case Operators.REF_ASTORE_opcode:
        case Operators.BYTE_ASTORE_opcode:
        case Operators.DOUBLE_ASTORE_opcode:
        case Operators.FLOAT_ASTORE_opcode:
        case Operators.LONG_ASTORE_opcode:
        case Operators.SHORT_ASTORE_opcode:
        case Operators.INT_ASTORE_opcode:
          fieldIdOp = new IntConstantOperand(FieldNameIdMap.ARRAY_ID);
          break;

        case Operators.GETSTATIC_opcode:
          fieldIdOp = makeFieldIdOp(GetStatic.getLocation(i).getFieldRef());
          break;

        case Operators.PUTSTATIC_opcode:
          fieldIdOp = makeFieldIdOp(PutStatic.getLocation(i).getFieldRef());
          break;

        case Operators.GETFIELD_opcode:
          fieldIdOp = makeFieldIdOp(GetField.getLocation(i).getFieldRef());
          break;

        case Operators.PUTFIELD_opcode:
          fieldIdOp = makeFieldIdOp(PutField.getLocation(i).getFieldRef());
          break;

        default:
          break;
      }

      if (fieldIdOp != null) {
        wrap(i, MemoryAccessEntrypoints.beforeEvent,
                MemoryAccessEntrypoints.afterEvent,
                fieldIdOp);
      }
    }

    // if the instrumentation requires the current thread, insert instructions
    // to get it
    if (threadOp != null) {
      addCurrentThreadCall(it.getFirstInstructionAfterPrologue());
    }
  }

  /**
   * Makes an integer constant operand containing the identifier provided by
   * {@link org.jikesrvm.replay.tracefile.leap.FieldNameIdMap} for a given
   * field.
   * @param fieldRef the field
   * @return         operand with the field's id
   */
  private IntConstantOperand makeFieldIdOp(FieldReference fieldRef) {
    String fieldName = fieldRef.getType().getName() + "" + fieldRef.getName();
    return new IntConstantOperand(FieldNameIdMap.getIdForName(fieldName));
  }

  /**
   * Wraps a given instruction with two static call instructions to a before
   * and an after method.
   * <p>
   * Both the before and the after methods take two arguments: the current
   * thread and the id of the field being accessed.
   * @param inst      instruction to wrap
   * @param beforeM   before method
   * @param afterM    after method
   * @param fieldIdOp id of the field
   */
  private void wrap(Instruction inst, NormalMethod beforeM,
                    NormalMethod afterM, Operand fieldIdOp) {
    // get an operand with the current thread
    RegisterOperand threadOp = getOrCreateThreadOp();

    // call static beforeM(currentThread, fieldId)
    Instruction beforeCall = Call.create2(
      Operators.CALL,
      null,
      new AddressConstantOperand(beforeM.getOffset()),
      MethodOperand.STATIC(beforeM),
      new TrueGuardOperand(),
      threadOp.copyD2U(),
      fieldIdOp);
    beforeCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    // call static afterM(currentThread, fieldId)
    Instruction afterCall = Call.create2(
      Operators.CALL,
      null,
      new AddressConstantOperand(afterM.getOffset()),
      MethodOperand.STATIC(afterM),
      new TrueGuardOperand(),
      threadOp.copyD2U(),
      fieldIdOp.copy());
    afterCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    inst.insertBefore(beforeCall);
    inst.insertAfter(afterCall);
  }


  ///
  /// Current thread
  ///

  /** The operand containing the current thread. */
  private RegisterOperand threadOp = null;

  /**
   * Gets an operand whose value is the current thread.
   * The operand is reused whenever the current thread is needed, so it is only
   * created once.
   * @return operand with the current thread
   */
  private RegisterOperand getOrCreateThreadOp() {
    if (threadOp == null) {
      threadOp = ir.regpool.makeTemp(TypeReference.Thread);
    }
    return threadOp;
  }

  /**
   * Creates instructions to obtain the current thread and place it in the
   * operand returned by {@link #getOrCreateThreadOp}. These instructions are
   * injected after a given instruction.
   * @param inst the instruction after which to inject the new ones
   */
  private void addCurrentThreadCall(Instruction inst) {
    NormalMethod m = MemoryAccessEntrypoints.GET_CURRENT_THREAD;
    Instruction call = Call.create0(
      Operators.CALL,
      threadOp,
      new AddressConstantOperand(m.getOffset()),
      MethodOperand.STATIC(m),
      new TrueGuardOperand());
    call.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;
    inst.insertAfter(call);
  }
}
