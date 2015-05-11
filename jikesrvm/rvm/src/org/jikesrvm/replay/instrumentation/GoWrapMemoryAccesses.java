package org.jikesrvm.replay.instrumentation;

import java.lang.reflect.Constructor;

import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * An OPT compiler phase that wraps memory access operations in calls to the
 * replay sub-system when recording or replaying in Global Order mode.
 * <p>
 * All memory access instructions are wrapped in two calls to handlers of the
 * replay sub-system. The before handler receives the current thread as an
 * argument, while the after handlers has no arguments.
 * <p>
 * The current thread object is shared as far as possible between calls to the
 * handlers.
 */
public final class GoWrapMemoryAccesses extends CompilerPhase {

  /** The IR on which the phase is being performed. */
  private IR ir;

  @Override
  public String getName() {
    return "Wrap Memory Accesses For GO Recorder/Replayer";
  }

  /** Contructor for this compiler phase. */
  private static final Constructor<CompilerPhase> CONSTRUCTOR =
      getCompilerPhaseConstructor(GoWrapMemoryAccesses.class);

  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return CONSTRUCTOR;
  }

  @Override
  public void perform(IR ir) {
    this.ir = ir;

    MemoryAccessInstructionsIterator it =
        new MemoryAccessInstructionsIterator(ir);

    if (!it.hasNext()) {
      return;
    }

    Instruction i;
    while (it.hasNext()) {
      i = it.next();
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
          wrap(i,
            MemoryAccessEntrypoints.beforeEvent,
            MemoryAccessEntrypoints.afterEvent);
          break;

        default:
          break;
      }
    }

    // if the instrumentation performed requires the current thread, insert
    // instructions to get them
    if (threadOp != null) {
      addCurrentThreadCall(it.getFirstInstructionAfterPrologue());
    }
  }

  /*
   * Wrap instrumentation
   */

  /**
   * Wraps a given instruction with two static call instructions to a before and
   * an after method.
   * <p>
   * The before method takes the current thread as an argument, while the after
   * method takes none.
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
    Instruction afterCall = Call.create0(
      Operators.CALL,
      null,
      new AddressConstantOperand(afterM.getOffset()),
      MethodOperand.STATIC(afterM),
      null);
    afterCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    inst.insertBefore(beforeCall);
    inst.insertAfter(afterCall);
  }


  ///
  /// Current thread
  ///

  /** The operand containing the current thread. */
  private RegisterOperand threadOp;

  /**
   * Gets an operand whose value is the current thread.
   * The operand is reused whenever the current thread is needed, so it is
   * only created once.
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
    // call static getCurrentThread()
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
}
