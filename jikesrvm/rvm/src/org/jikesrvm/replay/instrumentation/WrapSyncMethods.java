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

/**
 * An OPT compiler phase that wraps call to certain synchronization methods
 * in calls to the replay subsystem.
 * <p>
 * The instrumented synchronization methods are: {@link java.lang.Thread#wait},
 * {@link java.lang.Thread#suspend}, and
 * {@link java.util.concurrent.locks.LockSupport#park}.
 * <p>
 * Before each call to these synchronization methods, a call to
 * {@link SyncEntrypoints#TRACE_NEXT_SYNC_METHOD} is injected, to inform the
 * replay subsystem that the next synchronization method to be executed by the
 * current thread is to be traced.
 */
public final class WrapSyncMethods extends CompilerPhase {

  /** Name of the {@link java.lang.Thread#wait} method, as an atom. */
  public static final Atom WAIT_METHOD_NAME =
      Atom.findOrCreateAsciiAtom("wait");

  /** Name of the {@link java.lang.Thread#suspend} method, as an atom. */
  public static final Atom SUSPEND_METHOD_NAME =
      Atom.findOrCreateAsciiAtom("suspend");

  /**
   * Name of the {@link java.util.concurrent.locks.LockSupport#park} method,
   * as an atom.
   */
  public static final Atom PARK_METHOD_NAME =
      Atom.findOrCreateAsciiAtom("park");

  /**
   * Name of the {@link java.util.concurrent.locks.LockSupport} class, as an
   * atom.
   */
  public static final Atom LOCK_SUPPORT_NAME =
      Atom.findOrCreateAsciiAtom("Ljava/util/concurrent/locks/LockSupport;");

  @Override
  public String getName() {
    return "Wrap Synchronization Methods";
  }

  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public boolean shouldPerform(OptOptions options) {
    return ReplayManager.isEnabled()
        && ReplayManager.getReplayMode().wrapsSynchronization();
  }

  @Override
  public void perform(IR ir) {
    // bypass if this method is not to be instrumented
    if (!ReplayManager.shouldWrapSync(ir.getMethod())) {
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
          Atom methodName = methodRef.getName();
          if ((typeRef == TypeReference.JavaLangObject
               && methodName == WAIT_METHOD_NAME)
              || (typeRef == TypeReference.JavaLangThread
                  && methodName == SUSPEND_METHOD_NAME)
              || (typeRef.getName() == LOCK_SUPPORT_NAME
                  && methodName == PARK_METHOD_NAME)) {
            addTraceNextSyncMethodCall(i);
          }
        }
      }
    }

  }

  /**
   * Injects a call to {@link SyncEntrypoints#TRACE_NEXT_SYNC_METHOD} before
   * the given instruction.
   * @param inst instruction before which to inject the call
   */
  private void addTraceNextSyncMethodCall(Instruction inst) {
    NormalMethod m = SyncEntrypoints.TRACE_NEXT_SYNC_METHOD;
    Instruction call = Call.create0(
      Operators.CALL,
      null,
      new AddressConstantOperand(m.getOffset()),
      MethodOperand.STATIC(m),
      null);
    call.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;
    inst.insertBefore(call);
  }
}
