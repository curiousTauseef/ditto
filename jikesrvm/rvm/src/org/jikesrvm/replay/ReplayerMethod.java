package org.jikesrvm.replay;

import org.jikesrvm.classloader.RVMMethod;

/**
 * Special methods whose tracing requires runtime stack analysis.
 */
public enum ReplayerMethod {

  /** JNI monitor enter method. */
  JNI_MONITORENTER("org.jikesrvm.jni.JNIFunctions", "MonitorEnter"),
  /** JNI monitor exit method. */
  JNI_MONITOREXIT ("org.jikesrvm.jni.JNIFunctions", "MonitorExit");

  /** Name of the class to which the method belongs. */
  private final String className;

  /** Name of the method. */
  private final String methodName;

  /**
   * Private constructor.
   * @param  className  name of the class to which the method belongs
   * @param  methodName name of the method
   */
  private ReplayerMethod(String className, String methodName) {
    this.className = className;
    this.methodName = methodName;
  }

  /**
   * Gets the name of the class to which the method belongs.
   * @return class name
   */
  public String getClassName() {
    return className;
  }

  /**
   * Gets the name of the method.
   * @return method name
   */
  public String getMethodName() {
    return methodName;
  }

  /**
   * Checks whether this method matches the given
   * {@link org.jikesrvm.classloader.RVMMethod}.
   * There is a match if both the class name and method name are the same.
   * @param rvmMethod rvm method with which to match
   * @return          whether this replayer method matches the rvm method
   */
  public boolean matches(RVMMethod rvmMethod) {
    return rvmMethod.getName().toString().equals(methodName)
        && rvmMethod.getDeclaringClass().getDescriptor()
                    .classNameFromDescriptor().equals(className);
  }
}
