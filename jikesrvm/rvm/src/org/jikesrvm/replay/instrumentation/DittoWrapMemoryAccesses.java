package org.jikesrvm.replay.instrumentation;

import java.lang.reflect.Constructor;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.tef.ThreadEscapedFields;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * An OPT compiler phase that wraps both static and instance memory access
 * operations in calls to the replay sub-system when recording or replaying
 * in Ditto mode.
 * <p>
 * All memory access instructions are wrapped in two calls to handlers of
 * the replay sub-system. The before handler receives 3 arguments: the current
 * thread state, the target object, and the target field id. The after handler
 * only receives the current thread state.
 * <p>
 * The field id is obtained by calling
 * {@link org.jikesrvm.replay.tef.ThreadEscapedFields#getOrCreateIdForTEField}
 * if the field is an instance or static field. If the field belongs to an
 * array, the id is the index of the field (limited by the maximum number
 * of array fields as per the replay sub-system's configuration).
 * <p>
 * The current thread state is obtained by getting the current thread and
 * accessing its replay state field. The resulting state is reused as far as
 * possible.
 */
public final class DittoWrapMemoryAccesses extends CompilerPhase {

  /** The IR on which the phase is being performed. */
  private IR ir;

  @Override
  public String getName() {
    return "Wrap Memory Accesses For Ditto";
  }

  /** Constructor for this compiler phase. */
  private static final Constructor<CompilerPhase> CONSTRUCTOR =
      getCompilerPhaseConstructor(DittoWrapMemoryAccesses.class);

  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return CONSTRUCTOR;
  }

  @Override
  public void perform(IR ir) {
    MemoryAccessInstructionsIterator it
        = new MemoryAccessInstructionsIterator(ir);
    if (!it.hasNext()) {
      return;
    }

    this.ir = ir;
    DefUse.computeDU(ir);

    // iterate through all instructions and instrument them according to the
    // algorithm described in the DittoWrapMemoryAccesses documentation
    Instruction i;
    while (it.hasNext()) {
      i = it.next();

      NormalMethod beforeMethod, afterMethod;
      Operand objOp, fieldIdOp;

      switch (i.getOpcode()) {
        case Operators.REF_ALOAD_opcode:
        case Operators.BYTE_ALOAD_opcode:
        case Operators.UBYTE_ALOAD_opcode:
        case Operators.USHORT_ALOAD_opcode:
        case Operators.DOUBLE_ALOAD_opcode:
        case Operators.FLOAT_ALOAD_opcode:
        case Operators.LONG_ALOAD_opcode:
        case Operators.SHORT_ALOAD_opcode:
        case Operators.INT_ALOAD_opcode: {
          if (DEBUG && ReplayOptions.VERBOSE_INSTRUMENTATION) {
            VM.sysWriteln("Instrumented aload in " + i.position.getMethod()
                          + " (line " + i.bcIndex + ")");
          }

          beforeMethod = MemoryAccessEntrypoints.beforeInstanceLoad;
          afterMethod = MemoryAccessEntrypoints.afterLoad;
          objOp = ALoad.getArray(i).copy();
          fieldIdOp = arrayIndexToFieldId(i, ALoad.getIndex(i).copy());

          i = wrap(i, beforeMethod, afterMethod, objOp, fieldIdOp);
          break;
        }

        case Operators.REF_ASTORE_opcode:
        case Operators.BYTE_ASTORE_opcode:
        case Operators.DOUBLE_ASTORE_opcode:
        case Operators.FLOAT_ASTORE_opcode:
        case Operators.LONG_ASTORE_opcode:
        case Operators.SHORT_ASTORE_opcode:
        case Operators.INT_ASTORE_opcode: {
          if (DEBUG && ReplayOptions.VERBOSE_INSTRUMENTATION) {
            VM.sysWriteln("Instrumented astore in " + i.position.getMethod()
                          + " (line " + i.bcIndex + ")");
          }

          beforeMethod = MemoryAccessEntrypoints.beforeInstanceStore;
          afterMethod = MemoryAccessEntrypoints.afterStore;
          objOp = AStore.getArray(i).copy();
          fieldIdOp = arrayIndexToFieldId(i, AStore.getIndex(i).copy());

          i = wrap(i, beforeMethod, afterMethod, objOp, fieldIdOp);
          break;
        }

        case Operators.GETSTATIC_opcode: {
          FieldReference fieldRef = GetStatic.getLocation(i).getFieldRef();
          if (DEBUG && ReplayOptions.VERBOSE_INSTRUMENTATION) {
            VM.sysWriteln("Instrumented getstatic of " + fieldRef
                          + " in " + i.position.getMethod());
          }

          beforeMethod = MemoryAccessEntrypoints.beforeStaticLoad;
          afterMethod = MemoryAccessEntrypoints.afterLoad;
          objOp = new IntConstantOperand(fieldRef.getType().getId());
          fieldIdOp = new IntConstantOperand(
            ThreadEscapedFields.getOrCreateIdForTEField(fieldRef, true));

          i = wrap(i, beforeMethod, afterMethod, objOp, fieldIdOp);
          break;
        }

        case Operators.PUTSTATIC_opcode: {
          FieldReference fieldRef = PutStatic.getLocation(i).getFieldRef();
          if (DEBUG && ReplayOptions.VERBOSE_INSTRUMENTATION) {
            VM.sysWriteln("Instrumented putstatic of " + fieldRef
                          + " in " + i.position.getMethod());
          }

          beforeMethod = MemoryAccessEntrypoints.beforeStaticStore;
          afterMethod = MemoryAccessEntrypoints.afterStore;
          objOp = new IntConstantOperand(fieldRef.getType().getId());
          fieldIdOp = new IntConstantOperand(
            ThreadEscapedFields.getOrCreateIdForTEField(fieldRef, true));

          i = wrap(i, beforeMethod, afterMethod, objOp, fieldIdOp);
          break;
        }

        case Operators.GETFIELD_opcode: {
          FieldReference fieldRef = GetField.getLocation(i).getFieldRef();
          if (DEBUG && ReplayOptions.VERBOSE_INSTRUMENTATION) {
            VM.sysWriteln("Instrumented getfield of " + fieldRef
                          + " in " + i.position.getMethod());
          }

          // if object-level tracing is disabled, treat as a GETSTATIC_opcode
          if (ReplayOptions.INDIVIDUAL_OBJECTS) {
            beforeMethod = MemoryAccessEntrypoints.beforeInstanceLoad;
            objOp = GetField.getRef(i).copy();
          } else {
            beforeMethod = MemoryAccessEntrypoints.beforeStaticLoad;
            objOp = new IntConstantOperand(
                GetField.getRef(i).getType().getId());
          }
          afterMethod = MemoryAccessEntrypoints.afterLoad;
          fieldIdOp = new IntConstantOperand(
              ThreadEscapedFields.getOrCreateIdForTEField(fieldRef, false));

          i = wrap(i, beforeMethod, afterMethod, objOp, fieldIdOp);
          break;
        }

        case Operators.PUTFIELD_opcode: {
          FieldReference fieldRef = PutField.getLocation(i).getFieldRef();
          if (DEBUG && ReplayOptions.VERBOSE_INSTRUMENTATION) {
            VM.sysWriteln("Instrumented putfield of " + fieldRef
                          + " in " + i.position.getMethod());
          }

          // if object-level tracing is disabled, treat as a PUTSTATIC_opcode
          if (ReplayOptions.INDIVIDUAL_OBJECTS) {
            beforeMethod = MemoryAccessEntrypoints.beforeInstanceStore;
            objOp = PutField.getRef(i).copy();
          } else {
            beforeMethod = MemoryAccessEntrypoints.beforeStaticStore;
            objOp = new IntConstantOperand(
                PutField.getRef(i).getType().getId());
          }
          afterMethod = MemoryAccessEntrypoints.afterStore;
          fieldIdOp = new IntConstantOperand(
              ThreadEscapedFields.getOrCreateIdForTEField(fieldRef, false));

          i = wrap(i, beforeMethod, afterMethod, objOp, fieldIdOp);
          break;
        }

        default:
          break;
      }
    }

    // if the instrumentation requires the current thread state and/or the
    // maximum number of array fields, insert instructions to get them
    i = it.getFirstInstructionAfterPrologue();
    if (threadStateOp != null) {
      i = addCurrentThreadStateGetField(i);
    }
    if (maxArrayFieldsOp != null) {
      addMaxArrayFieldsGetStatic(i);
    }
  }

  /**
   * Wraps a given instruction with two static call instructions to a before and
   * an after method.
   * <p>
   * The before method takes the current thread state, an object and a field id
   * as arguments, while the after method simply takes the current thread state.
   * @param  inst      instruction to wrap
   * @param  beforeM   before method
   * @param  afterM    after method
   * @param  objOp     object argument to {@code beforeM}
   * @param  fieldIdOp field id argument to {@code beforeM}
   * @return           the last injected instruction after wrapping {@code inst}
   */
  private Instruction wrap(Instruction inst,
                           NormalMethod beforeM, NormalMethod afterM,
                           Operand objOp, Operand fieldIdOp) {
    // current thread state is needed as the 3rd argument to the before method
    RegisterOperand threadStateOp = getOrCreateThreadStateOp();

    // call static beforeM(threadStateOp, objOp, fieldIdOp)
    Instruction beforeCall = Call.create3(
      Operators.CALL,
      null,
      new AddressConstantOperand(beforeM.getOffset()),
      MethodOperand.STATIC(beforeM),
      new TrueGuardOperand(),
      threadStateOp.copyD2U(),
      objOp,
      fieldIdOp);
    beforeCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    // call static afterM(threadStateOp)
    Instruction afterCall = Call.create1(
      Operators.CALL,
      null,
      new AddressConstantOperand(afterM.getOffset()),
      MethodOperand.STATIC(afterM),
      new TrueGuardOperand(),
      threadStateOp.copyD2U());
    afterCall.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    inst.insertBefore(beforeCall);
    inst.insertAfter(afterCall);

    return afterCall;
  }


  ///
  /// Current thread state
  ///

  /** The operand containing the current thread state. */
  private RegisterOperand threadStateOp = null;

  /**
   * Gets an operand whose value is the current thread state.
   * The operand is reused whenever the current thread state is needed, so it
   * is only created once.
   * @return operand with the current thread state
   */
  private RegisterOperand getOrCreateThreadStateOp() {
    if (threadStateOp == null) {
      TypeReference threadStateType = MemoryAccessEntrypoints.threadStateType;
      threadStateOp = ir.regpool.makeTemp(threadStateType);
    }
    return threadStateOp;
  }

  /**
   * Creates instructions to obtain the current thread state and place it in
   * the operand returned by {@link #getOrCreateThreadStateOp}. These
   * instructions are injected after a given instruction.
   * @param  inst the instruction after which to inject the new ones
   * @return      the last injected instruction
   */
  private Instruction addCurrentThreadStateGetField(Instruction inst) {
    // start by getting the current thread:
    //   threadOp = call static getCurrentThread()
    RegisterOperand threadOp = ir.regpool.makeTemp(TypeReference.Thread);
    NormalMethod m = MemoryAccessEntrypoints.GET_CURRENT_THREAD;
    Instruction call = Call.create0(
      Operators.CALL,
      threadOp,
      new AddressConstantOperand(m.getOffset()),
      MethodOperand.STATIC(m),
      new TrueGuardOperand());
    call.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    // then, get the thread's state
    //   threadStateOp = get field threadOp.threadState
    RVMField f = MemoryAccessEntrypoints.threadState;
    Instruction get = GetField.create(
      Operators.GETFIELD,
      threadStateOp,
      threadOp.copyD2U(),
      new AddressConstantOperand(f.getOffset()),
      new LocationOperand(f),
      new TrueGuardOperand());
    get.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    inst.insertAfter(call);
    call.insertAfter(get);
    return get;
  }


  ///
  /// Array fields
  ///

  /** The operand containing the maximum number of array fields. */
  private RegisterOperand maxArrayFieldsOp = null;

  /**
   * Gets an operand whose value is the maximum number of array fields, as
   * per the configuration of the replay sub-system.
   * The operand is reused whenever the meximum number of array fields is
   * needed, so it is only created once.
   * @return operand with the maximum number of array fields
   */
  private RegisterOperand getOrCreateMaxArrayFieldsOp() {
    if (maxArrayFieldsOp == null) {
      maxArrayFieldsOp = ir.regpool.makeTempInt();
    }
    return maxArrayFieldsOp;
  }

  /**
   * Creates instructions to obtain the maximum number of array fields and place
   * it in the operand returned by {@link #getOrCreateMaxArrayFieldsOp}. These
   * instructions are injected after a given instruction.
   * @param  inst the instuction after which to inject the new ones
   * @return      the last injected instruction
   */
  private Instruction addMaxArrayFieldsGetStatic(Instruction inst) {
    RVMField field = MemoryAccessEntrypoints.MAX_ARRAY_FIELDS;
    Instruction getStatic = GetStatic.create(
      Operators.GETSTATIC,
      maxArrayFieldsOp,
      new AddressConstantOperand(field.getOffset()),
      new LocationOperand(field));
    getStatic.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;

    inst.insertAfter(getStatic);
    return getStatic;
  }

  /**
   * Creates an operand containing the field id to be used when tracing a given
   * array memory access, by making sure that the maximum number of array fields
   * is respected. When the calculation must take place in runtime, an
   * instruction is injected to do so.
   * <p> Optimization: if the array index of {@code inst} is constant, the
   * field id is calculated at compile time and no instructions are injected.
   * @param  inst    instruction before which to inject required instrutions
   * @param  indexOp operand containing the value of the index used to access
   *                 the array in the memory access operation
   * @return         operand with the field id
   */
  private Operand arrayIndexToFieldId(Instruction inst, Operand indexOp) {
    // if the index operand is not constant, the calculation of the field id
    // must be performed at runtime
    if (!indexOp.isIntConstant()) {
      RegisterOperand fieldOp = ir.regpool.makeTempInt();
      Instruction map = GuardedBinary.create(
          Operators.INT_REM,
          fieldOp,
          indexOp,
          getOrCreateMaxArrayFieldsOp().copyD2U(),
          new TrueGuardOperand());
      map.bcIndex = OptConstants.REPLAY_INSTRUMENTATION_BCI;
      inst.insertBefore(map);
      return fieldOp.copyD2U();
    }

    // if the index operand is constant, we can calculate a compile-time
    // constant field id as well
    int fId = indexOp.asIntConstant().value % ReplayOptions.MAX_ARRAY_FIELDS;
    return new IntConstantOperand(fId);
  }
}
