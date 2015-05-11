package org.jikesrvm.replay.instrumentation;

import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.replay.ReplayManager;
import org.jikesrvm.replay.ReplayUtils;

/**
 * An OPT compiler phase that wraps calls to {@link java.lang.Thread#join} in
 * calls to the replay sub-system.
 * <p>
 * Calls to {@link java.lang.Thread#join} are followed by an injected call
 * to {@link SyncEntrypoints#THREAD_JOINED}.
 */
public final class WrapJoin extends CompilerPhase {

  /** Name of the {@link java.lang.Thread#join} method, as an atom. */
  private static final Atom JOIN_NAME = Atom.findOrCreateAsciiAtom("join");

  @Override
  public String getName() {
    return "Wrap Joins";
  }

  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public boolean shouldPerform(OptOptions options) {
    return ReplayManager.isEnabled();
  }

  @Override
  public void perform(IR ir) {
    // don't instrument ignored methods
    if (!ReplayUtils.isMethodFromNonIgnoredPackage(ir.getMethod())) {
      return;
    }

    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder();
         bb != null;
         bb = bb.nextBasicBlockInCodeOrder()) {
      for (Instruction i = bb.firstInstruction();
           i != bb.lastInstruction();
           i = i.nextInstructionInCodeOrder()) {

        if (i.getOpcode() == Operators.CALL_opcode) {
          MethodReference methodRef = Call.getMethod(i).getMemberRef()
                                                       .asMethodReference();
          TypeReference typeRef = methodRef.getType();
          if (typeRef == TypeReference.JavaLangThread
              && methodRef.getName() == JOIN_NAME) {
            i = addThreadJoinedCall(i);
          }
        }
      }
    }
  }

  /**
   * Injects a call to {@link SyncEntrypoints#THREAD_JOINED} after the given
   * instruction.
   * @param  inst instruction after which to inject the call
   * @return      injected call instruction
   */
  private Instruction addThreadJoinedCall(Instruction inst) {
    NormalMethod m = SyncEntrypoints.THREAD_JOINED;
    Instruction call = Call.create1(
      Operators.CALL,
      null,
      new AddressConstantOperand(m.getOffset()),
      MethodOperand.STATIC(m),
      null,
      Call.getParam(inst, 0));
    call.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;
    inst.insertAfter(call);
    return call;
  }
}
