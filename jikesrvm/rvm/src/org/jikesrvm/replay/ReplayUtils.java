package org.jikesrvm.replay;

import java.lang.reflect.Proxy;

import org.jikesrvm.SizeConstants;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.StackBrowser;
import org.vmmagic.pragma.Inline;

/**
 * Contains utility methods used by the replay subsystem.
 */
public final class ReplayUtils {

  /**
   * Checks whether a given method belongs to a class from a non-ignored
   * package.
   * @param method the method to check
   * @return       whether the method belongs to a class from a non-ignored
   *               package
   */
  public static boolean isMethodFromNonIgnoredPackage(RVMMethod method) {
    RVMClass klass = method.getDeclaringClass();
    String className = klass.getDescriptor().classNameFromDescriptor();

    if (isClassFromNonIgnoredPackage(className)) {
      // a proxy class is only from a non-ignored package if all the interfaces
      // it implements are from non-ignore packages
      if (className.startsWith("$Proxy")
          && Proxy.isProxyClass(klass.getClassForType())) {
        for (Class<?> i : klass.getClassForType().getInterfaces()) {
          if (!isClassFromNonIgnoredPackage(i.getName())) {
            return false;
          }
        }
      }
      return true;
    }

    return false;
  }

  /**
   * Analyses the stack to find out if the caller of a given
   * {@link org.jikesrvm.replay.ReplayerMethod} is from a non-ignored package.
   * @param rMethod the method for which the caller is to be found
   * @return        whether the caller of the method is from a non-ignored
   *                package
   */
  public static boolean wasCalledFromNonIgnoredPackage(ReplayerMethod rMethod) {
    RVMMethod caller = findCallerOfMethod(rMethod);
    return caller != null && isMethodFromNonIgnoredPackage(caller);
  }

  /**
   * Checks whether a given type is from a non-ignored package.
   * @param typeRef the type to check
   * @return        whether the type belongs to a non-ignored package
   */
  public static boolean isTypeFromNonIgnoredPackage(TypeReference typeRef) {
    String name = typeRef.getName().toString();
    if (name.charAt(0) == 'L') {
      String classname = name.substring(1, name.length() - 1).replace('/', '.');
      return isClassFromNonIgnoredPackage(classname);
    }
    return false;
  }

  /**
   * Checks whether a given class name (including packages) belongs to a
   * non-ignored package.
   * @param className the class name to check
   * @return          whether the class belongs to a non-ignored package
   */
  private static boolean isClassFromNonIgnoredPackage(String className) {
    // hard-coded ignored packages
    for (final String ignoredPackage: ReplayOptions.IGNORED_PACKAGES) {
      if (className.startsWith(ignoredPackage)) {
        return false;
      }
    }

    // user-provided ignored packages
    if (ReplayOptions.EXTRA_IGNORED_PACKAGES != null) {
      for (final String ignoredPackage: ReplayOptions.EXTRA_IGNORED_PACKAGES) {
        if (className.startsWith(ignoredPackage)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Finds the {@link org.jikesrvm.classloader.RVMMethod} instance of the method
   * that called a given {@link org.jikesrvm.replay.ReplayerMethod}, by
   * analyzing the current stack.
   * @param rMethod the method for which the caller is to be found
   * @return the caller of {@code rMethod} if found; {@code null} otherwise
   */
  private static RVMMethod findCallerOfMethod(ReplayerMethod rMethod) {
    StackBrowser stack = new StackBrowser();

    VM.disableGC();
    stack.init();
    for (;;) {
      RVMMethod curMethod = stack.getMethod();
      if (rMethod.matches(curMethod)) {
        break;
      }
      if (!stack.hasMoreFrames()) {
        VM.enableGC();
        return null;
      }
      stack.up();
    }
    if (stack.hasMoreFrames()) {
      stack.up();
      VM.enableGC();
      return stack.getMethod();
    }
    VM.enableGC();
    return null;
  }

  /**
   * Calculates the number of bytes of the smallest integer-like type (byte,
   * short, int, or long) needed to store a given value.
   * @param value the value to store
   * @return the size of the smallest integer-like type that can hold value
   */
  public static byte minBytesToStore(long value) {
    if (value <= Byte.MAX_VALUE) {
      return SizeConstants.BYTES_IN_BYTE;
    } else if (value <= Short.MAX_VALUE) {
      return SizeConstants.BYTES_IN_SHORT;
    } else if (value <= Integer.MAX_VALUE) {
      return SizeConstants.BYTES_IN_INT;
    } else {
      return SizeConstants.BYTES_IN_LONG;
    }
  }

  /**
   * Calculates the maximum of three numbers.
   * @param  a 1st number
   * @param  b 2nd number
   * @param  c 3rd number
   * @return   maximum number
   */
  @Inline
  public static long max(long a, long b, long c) {
    return a > b ? (a > c ? a : c) : (b > c ? b : c);
  }

  /**
   * Calculates the maximum of two numbers.
   * @param  a 1st number
   * @param  b 2nd number
   * @return   maximum number
   */
  @Inline
  public static long max(long a, long b) {
    return a > b ? a : b;
  }

  /**
   * Gets the class of a type from the type's identifier.
   * @param typeId id of the type
   * @return class of the type
   */
  @Inline
  public static Class<?> classOfType(int typeId) {
    TypeReference ref = TypeReference.getTypeRef(typeId);
    RVMClass klass = ref.peekType().asClass();
    Class<?> c = klass.getClassForType();
    return c;
  }

  /** Hidden constructor. */
  private ReplayUtils() { }
}
