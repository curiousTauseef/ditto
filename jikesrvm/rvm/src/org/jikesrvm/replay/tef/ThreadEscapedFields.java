package org.jikesrvm.replay.tef;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.replay.ReplayOptions;
import org.jikesrvm.replay.ReplayUtils;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;

import static org.jikesrvm.replay.ReplayOptions.DEBUG;

/**
 * Manages everything related to the thread-escaped fields functionality.
 * <p>
 * If a TEF file is provided by the user, the fields in the file are considered
 * the only ones worth tracing. Otherwise, most fields will conservatively be
 * considered thread-escaped.
 * <p>
 * Thread-escaped fields are assigned identifiers that enable the replay
 * subsystem to operate at object-level and field-level granularity.
 */
public final class ThreadEscapedFields {

  /**
   * Holds the number of thread-escaped fields of a type that have been given
   * an identifier.
   */
  private static class NumTEFields {

    /** Number of static thread-escaped fields. */
    public int staticFields;

    /** Number of instance thread-escaped fields. */
    public int instanceFields;

    /**
     * Constructor.
     * @param  staticFields   number of static thread-escaped fields
     * @param  instanceFields number of instance thread-escaped fields
     */
    public NumTEFields(int staticFields, int instanceFields) {
      this.staticFields = staticFields;
      this.instanceFields = instanceFields;
    }
  }

  /** Posible states of the thread-escaped fields module. */
  private static enum State {
    /** TEF module is active. */     ACTIVE,
    /** TEF module is not active. */ INACTIVE
  };

  /** Hidden constructor. */
  private ThreadEscapedFields() { }

  /** Current state of the thread-escaped fields module. */
  private static State state = State.INACTIVE;

  /** Set of all thread-escaped fields, fully qualified. */
  private static HashSet<String> teFields;

  /** Memoization cache for {@link #isFieldThreadEscaped}. */
  private static HashMap<FieldReference, Boolean> isTEFieldCache;

  /**
   * Maps types to the number of their already identified thread-escaped
   * fields.
   */
  private static HashMap<TypeReference, NumTEFields> ongoingTefIds;

  /** Maps types to their number of static thread-escaped fields. */
  private static Map<RVMType, Integer> numStaticTEFields;

  /** Maps types to their number of instance thread-escaped fields. */
  private static Map<RVMType, Integer> numInstanceTEFields;

  /** Maps fields to their identifiers. */
  private static HashMap<FieldReference, Integer> teFieldIds;

  /**
   * Reads the thread-escaped fields from the TEF file.
   * @param  filename         pathname of the TEF file
   * @throws TEFFileException if reading the thread-escaped fields fails
   */
  public static void readFromFile(String filename) throws TEFFileException {
    File file = new File(filename);
    try {
      BufferedReader stream = new BufferedReader(new FileReader(file));
      init(State.ACTIVE);
      String line;
      while ((line = stream.readLine()) != null) {
        if (!line.isEmpty() && isValidFieldSignature(line)) {
          teFields.add(line.trim());
        }
      }
      if (DEBUG && ReplayOptions.VERBOSE_TEF) {
        VM.sysWriteln("[TEF] read fields: " + teFields);
      }
    } catch (FileNotFoundException fnfe) {
      init(State.INACTIVE);
      throw new TEFFileException(String.format("Unable to open TEF file %s.",
          file.getAbsolutePath()), fnfe);
    } catch (IOException ioe) {
      throw new TEFFileException("Failed to read TEF file.", ioe);
    }
  }

  /**
   * Checks whether a given field is thread-escaped according to the provided
   * TEF file.
   * @param  fieldRef the field
   * @return          whether the field is thread-escaped
   */
  public static boolean isFieldThreadEscaped(FieldReference fieldRef) {
    if (state == State.ACTIVE) {
      // check the cache
      Boolean result = isTEFieldCache.get(fieldRef);
      if (result != null) {
        return result;
      }

      // on a cache miss, check the set of thread-escaped fields
      String sig = signatureOfField(fieldRef);
      if (teFields.contains(sig)) {
        isTEFieldCache.put(fieldRef, true);
        teFields.remove(sig);
        return true;
      } else {
        isTEFieldCache.put(fieldRef, false);
        return false;
      }
    }

    // If no static analysis has been conducted, all fields are considered
    // thread escaped. In this case, we ignore non-public, non-protected
    // fields of superclasses/interfaces which belong to ignored packages,
    // since these cannot be accessed by application code. Final fields are
    // ignored as well.
    RVMField f = fieldRef.peekResolvedField();
    if (f == null) {
      f = fieldRef.resolve();
    }
    return !f.isFinal()
        && (f.isPublic() || f.isProtected()
            || ReplayUtils.isTypeFromNonIgnoredPackage(fieldRef.getType()));
  }

  /**
   * Obtain the number of static thread-escaped fields of a given type.
   * @param  type the type
   * @return      number of static thread-escaped fields of the type
   */
  @Inline
  public static int getNumberOfStaticTEFields(RVMType type) {
    // check the cache
    Integer n = numStaticTEFields.get(type);
    if (n != null) {
      return n;
    }

    // Attention: getInstanceFields() returns fields from supertypes,
    // but getStaticFields() does not. We do all the work manually.

    int num = 0;
    Set<RVMField> myTefFields = new HashSet<RVMField>();
    // iterate from the given type through all of its super classes
    for (RVMClass k = type.asClass(); k != null; k = k.getSuperClass()) {
      // check the static fields of the class
      for (RVMField f : k.getStaticFields()) {
        if (!myTefFields.contains(f)
            && isFieldThreadEscaped(f.getMemberRef().asFieldReference())) {
          num++;
          myTefFields.add(f);
        }
      }
      // iterate through all the interfaces implemented by the class
      for (RVMClass i: k.getAllImplementedInterfaces()) {
        for (RVMClass si = i; i != null; i = i.getSuperClass()) {
          // check the static fields of the interfaces
          for (RVMField f : si.getStaticFields()) {
            if (!myTefFields.contains(f)
                && isFieldThreadEscaped(f.getMemberRef().asFieldReference())) {
              num++;
              myTefFields.add(f);
            }
          }
        }
      }
    }

    // if the replay subsystem is running without object-level granularity,
    // treat the instance fields as static fields
    if (!ReplayOptions.INDIVIDUAL_OBJECTS) {
      for (RVMField f : type.getInstanceFields()) {
        if (isFieldThreadEscaped(f.getMemberRef().asFieldReference())) {
          num++;
        }
      }
    }

    // if the replay subsystem is running without field-level granularity,
    // merge all the static fields into one
    if (!ReplayOptions.INDIVIDUAL_FIELDS) {
      num = num > 1 ? 1 : num;
    }

    // cache the result
    numStaticTEFields.put(type, num);
    if (DEBUG && ReplayOptions.VERBOSE_TEF) {
      VM.sysWriteln("[TEF] number of static TE fields for " + type
          + " is ", num);
    }
    return num;
  }

  /**
   * Obtain the number of instance thread-escaped fields of the type of a given
   * object.
   * @param  obj the object
   * @return     number of instance thread-escaped fields of the type of the
   *             object
   */
  @Inline
  public static int getNumberOfInstanceTEFields(Object obj) {
    // get the type of the object
    RVMType type = JikesRVMSupport.getTypeForClass(obj.getClass());

    // if the type is an array, the number of instance fields is the minimum
    // of the length length of obj and the max-array-fields replay subsystem
    // configuration option
    if (type.isArrayType()) {
      int n = Magic.getArrayLength(obj);
      if (n > ReplayOptions.MAX_ARRAY_FIELDS) {
        n = ReplayOptions.MAX_ARRAY_FIELDS;
      }
      if (DEBUG && ReplayOptions.VERBOSE_TEF) {
        VM.sysWriteln("[TEF] number of array TE fields for instance of "
            + type + " is " + n);
      }
      return n;
    }

    // for non-array types, start by checking the cache
    Integer n = numInstanceTEFields.get(type);
    if (n != null) {
      return n;
    }

    // on a cache-miss, count the type's thread-escaped instance fields
    int num = 0;
    // if the replay subsystem is not running with object-level granularity,
    // all fields are considered static. thus, the number of instance
    // thread-escaped fields is 0
    if (ReplayOptions.INDIVIDUAL_OBJECTS) {
      for (RVMField f : type.getInstanceFields()) {
        if (isFieldThreadEscaped(f.getMemberRef().asFieldReference())) {
          num++;
        }
      }
      // if the replay subsystem is not running with field-level granularity,
      // merge all instance thread-escaped fields into one
      if (!ReplayOptions.INDIVIDUAL_FIELDS) {
        num = num > 1 ? 1 : num;
      }
    }

    // cache the result
    numInstanceTEFields.put(type, num);
    if (DEBUG && ReplayOptions.VERBOSE_TEF) {
      VM.sysWriteln("[TEF] number of TE fields for instance of " + type
          + " is " + num);
    }
    return num;
  }

  /**
   * Obtains the type-unique identifier used for a given fields.
   * Assigns an id to the field if it has not been assigned one yet.
   * @param  fieldRef the field
   * @param  isStatic whether the field is static
   * @return          identifier of the field
   */
  public static int getOrCreateIdForTEField(FieldReference fieldRef,
                                            boolean isStatic) {
    // if the replay subsystem is not running with field-level granularity,
    // all fields use the same identifier
    if (!ReplayOptions.INDIVIDUAL_FIELDS) {
      return 0;
    }

    // check the cache
    Integer cachedId = teFieldIds.get(fieldRef);
    if (cachedId != null) {
      return cachedId;
    }

    TypeReference typeRef = fieldRef.getType();
    NumTEFields tefIds = ongoingTefIds.get(typeRef);
    // if this is the first field of its type to require an id, initialize
    // the id generator for the type
    if (tefIds == null) {
      if (isStatic || !ReplayOptions.INDIVIDUAL_OBJECTS) {
        ongoingTefIds.put(typeRef, new NumTEFields(1, 0));
      } else {
        ongoingTefIds.put(typeRef, new NumTEFields(0, 1));
      }
      teFieldIds.put(fieldRef, 0);
      if (DEBUG && ReplayOptions.VERBOSE_TEF) {
        VM.sysWriteln("[TEF] id 0 for " + (isStatic ? "static" : "")
            + " field " + fieldRef);
      }
      return 0;
    }

    // otherwise, assign the next id to the field
    int id;
    if (isStatic || !ReplayOptions.INDIVIDUAL_OBJECTS) {
      id = tefIds.staticFields;
      tefIds.staticFields++;
    } else {
      id = tefIds.instanceFields;
      tefIds.instanceFields++;
    }
    teFieldIds.put(fieldRef, id);
    if (DEBUG && ReplayOptions.VERBOSE_TEF) {
      VM.sysWriteln("[TEF] id " + id + " for " + (isStatic ? "static" : "")
          + " field " + fieldRef + " when tefIds=<" + tefIds.staticFields
          + ", " + tefIds.instanceFields + ">");
    }
    return id;
  }

  /**
   * Gets the set of thread-escaped fields, as provided by the TEF file.
   * @return set of thread-escaped fields
   */
  public static HashSet<String> getTEFields() {
    return teFields;
  }

  /**
   * Initializes the TEF module.
   * @param state the state of the module
   */
  private static void init(State state)  {
    ThreadEscapedFields.state = state;
    if (state == State.ACTIVE) {
      teFields = new HashSet<String>();
      isTEFieldCache = new HashMap<FieldReference, Boolean>();
    }
    teFieldIds = new HashMap<FieldReference, Integer>();
    ongoingTefIds = new HashMap<TypeReference, NumTEFields>();
    numStaticTEFields = new HashMap<RVMType, Integer>();
    numInstanceTEFields = new HashMap<RVMType, Integer>();
  }

  /**
   * Generates the fully qualified signature of a given field.
   * @param  field the field
   * @return       fully qualified signature of the field
   */
  private static String signatureOfField(FieldReference field) {
    return field.getType().getName() + "" + field.getName();
  }

  /**
   * Checks whether a given string is a valid fully qualified field signature.
   * @param  str string to check
   * @return     whether str is a valid field signature
   */
  private static boolean isValidFieldSignature(String str) {
    int l = str.indexOf(';');
    return l >= 0 && l < str.length();
  }
}
