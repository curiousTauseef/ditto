package org.jikesrvm.replay.instrumentation;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
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
import org.jikesrvm.replay.ReplayManager;

/**
 * An OPT compiler phase that wraps memory accesses in calls to the replay
 * sub-system when recording or replaying in JaRec mode.
 * <p>
 * All memory access instructions are wrapped in two calls to handlers of the
 * replay sub-system. The before handler receives 2 arguments: the current
 * thread, and the object to which the field being accessed belongs. The after
 * handler only takes one argument: the current thread. If the replay sub-system
 * is in replay mode, the object argument to the before handler is not required.
 * <p>
 * For static memory accesses, the object passed to the before handler is the
 * class to which the field belongs. The classes are obtained inline and shared
 * between handler calls as much as possible.
 * <p>
 * The effort to get the current thread is reused as far as possible when
 * calling the handlers.
 */
public final class JaRecWrapMemoryAccesses extends CompilerPhase {

  /** The IR on which the phase is being performed. */
  private IR ir;

  @Override
  public String getName() {
    return "Wrap Memory Accesses For JaRec Replay";
  }

  /** Constructor for this compiler phase. */
  private static final Constructor<CompilerPhase> CONSTRUCTOR =
      getCompilerPhaseConstructor(JaRecWrapMemoryAccesses.class);

  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return CONSTRUCTOR;
  }

  @Override
  public void perform(IR ir) {
    this.ir = ir;

    MemoryAccessInstructionsIterator it =
        new MemoryAccessInstructionsIterator(ir);

    BasicBlock lastBB = null;
    Instruction i;

    // if recording...
    if (ReplayManager.getReplayMode().isRecorder()) {
      while (it.hasNext()) {
        i = it.next();

        // clear the classof operands when the basic block changes
        if (lastBB != null && lastBB != i.getBasicBlock()) {
          classOfTypeOps.clear();
        }

        // find the target object of the memory access
        Operand objOperand = null;
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
            objOperand = ALoad.getArray(i).copy();
            break;

          case Operators.REF_ASTORE_opcode:
          case Operators.BYTE_ASTORE_opcode:
          case Operators.DOUBLE_ASTORE_opcode:
          case Operators.FLOAT_ASTORE_opcode:
          case Operators.LONG_ASTORE_opcode:
          case Operators.SHORT_ASTORE_opcode:
          case Operators.INT_ASTORE_opcode:
            objOperand = AStore.getArray(i).copy();
            break;

          case Operators.GETSTATIC_opcode:
            objOperand = getOrCreateClassOfFieldOp(
                GetStatic.getLocation(i).getFieldRef(), i);
            break;

          case Operators.PUTSTATIC_opcode:
            objOperand = getOrCreateClassOfFieldOp(
                PutStatic.getLocation(i).getFieldRef(), i);
            break;

          case Operators.GETFIELD_opcode:
            objOperand = GetField.getRef(i).copy();
            break;

          case Operators.PUTFIELD_opcode:
            objOperand = PutField.getRef(i).copy();
            break;

          default:
            break;
        }

        // wrap the memory access in before and after handlers
        if (objOperand != null) {
          wrap(i, MemoryAccessEntrypoints.beforeEvent,
                  MemoryAccessEntrypoints.afterEvent,
                  objOperand);
        }
      }
    }
    // if replaying...
    else {
      while (it.hasNext()) {
        i = it.next();

        // clear the classof operands when the basic block changes
        if (lastBB != null && lastBB != i.getBasicBlock()) {
          classOfTypeOps.clear();
        }

        // wrap memory accesses in before and after handlers
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
          case Operators.GETSTATIC_opcode:
          case Operators.PUTSTATIC_opcode:
          case Operators.GETFIELD_opcode:
          case Operators.PUTFIELD_opcode:
            wrap(i, MemoryAccessEntrypoints.beforeEvent,
                    MemoryAccessEntrypoints.afterEvent);
            break;

          default:
            break;
        }
      }
    }

    // if the instrumentation requires the current thread, insert instructions
    // to get it
    if (threadOp != null) {
      addCurrentThreadCall(it.getFirstInstructionAfterPrologue());
    }
  }

  /**
   * Wraps a given instruction with two static call instructions to a before
   * and an after method.
   * <p>
   * The before method takes the current thread and the target object as
   * arguments, while the after method simply takes the current thread.
   * @param inst    instruction to wrap
   * @param beforeM before method
   * @param afterM  after method
   * @param objOp   target object, argument to {@code beforeM}
   */
  private void wrap(Instruction inst, NormalMethod beforeM,
                    NormalMethod afterM, Operand objOp) {
    // get an operand with the current thread
    RegisterOperand threadOp = getOrCreateThreadOp();

    // call static beforeM(currentThread, objOp)
    Instruction beforeCall = Call.create2(
      Operators.CALL,
      null,
      new AddressConstantOperand(beforeM.getOffset()),
      MethodOperand.STATIC(beforeM),
      null,
      threadOp.copyD2U(),
      objOp);
    beforeCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    // call static afterM(currentThread, objOp)
    Instruction afterCall = Call.create1(
      Operators.CALL,
      null,
      new AddressConstantOperand(afterM.getOffset()),
      MethodOperand.STATIC(afterM),
      null,
      threadOp.copyD2U());
    afterCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    inst.insertBefore(beforeCall);
    inst.insertAfter(afterCall);
  }

  /**
   * Wraps a given instruction with two static call instructions to a before
   * and an after method.
   * <p>
   * Both methods take the current thread as their sole argument.
   * @param inst    instruction to wrap
   * @param beforeM before method
   * @param afterM  after method
   */
  private void wrap(Instruction inst,
                    NormalMethod beforeM, NormalMethod afterM) {
    // get an operand with the current thread
    RegisterOperand threadOp = getOrCreateThreadOp();

    // call static beforeM(currentThread)
    Instruction beforeCall = Call.create1(
      Operators.CALL,
      null,
      new AddressConstantOperand(beforeM.getOffset()),
      MethodOperand.STATIC(beforeM),
      null,
      threadOp.copyD2U());
    beforeCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    // call static afterM(currentThread)
    Instruction afterCall = Call.create1(
      Operators.CALL,
      null,
      new AddressConstantOperand(afterM.getOffset()),
      MethodOperand.STATIC(afterM),
      null,
      threadOp.copyD2U());
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
      null);
    call.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;
    inst.insertAfter(call);
  }


  ///
  /// Class of types
  ///

  /** Maps types to the operands containing their class object. */
  private Map<TypeReference, RegisterOperand> classOfTypeOps =
      new HashMap<TypeReference, RegisterOperand>();

  /**
   * Gets an operand whose value is the class object of a given field's type.
   * The operands are created on-demand and reused if possible. When an operand
   * is created, instructions to fill it are inserted before a given
   * instruction.
   * @param  fieldRef the field
   * @param  inst     instruction before which to place the instructions to
   *                  fill the operand
   * @return          operand with the class object of the field's type
   */
  private RegisterOperand getOrCreateClassOfFieldOp(FieldReference fieldRef,
                                                    Instruction inst) {
    TypeReference typeRef = fieldRef.getType();

    // if the operand already exists, return it
    RegisterOperand ro = classOfTypeOps.get(typeRef);
    if (ro != null) {
      return ro.copyD2U();
    }

    // create the operand
    ro = ir.regpool.makeTemp(TypeReference.JavaLangObject);

    // create an instruction to fill the operand:
    // call static getClassOfType(typeId)
    NormalMethod m = MemoryAccessEntrypoints.GET_CLASS_OF_TYPE;
    Instruction call = Call.create1(
      Operators.CALL,
      ro,
      new AddressConstantOperand(m.getOffset()),
      MethodOperand.STATIC(m),
      null,
      new IntConstantOperand(typeRef.getId()));
    call.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;
    inst.insertBefore(call);

    // cache the operand
    classOfTypeOps.put(typeRef, ro);
    return ro.copyD2U();
  }
}
