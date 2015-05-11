package org.jikesrvm.replay.tracefile.leap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jikesrvm.replay.tef.ThreadEscapedFields;

/**
 * Maps field names to field ids and vice-versa.
 * <p>
 * Only the fields being traced are assigned ids.
 */
public final class FieldNameIdMap {

  /** The field id used for all array fields. */
  public static final int ARRAY_ID = 0;

  /** Maps field names to field ids. */
  private static Map<String, Integer> nameToIdTable;

  /** Maps field ids to field names. */
  private static Map<Integer, String> idToNameTable;

  /** Number of fields with assigned ids. */
  private static int nFields;

  /** Private constructor. */
  private FieldNameIdMap() { }

  /** Initializes the module. */
  public static void init() {
    nameToIdTable = new HashMap<String, Integer>();
    idToNameTable = new HashMap<Integer, String>();
    nameToIdTable.put("array", ARRAY_ID);
    idToNameTable.put(ARRAY_ID, "array");
    nFields = 1;
    HashSet<String> teFields = ThreadEscapedFields.getTEFields();
    for (String tef : teFields) {
      nameToIdTable.put(tef, nFields);
      idToNameTable.put(nFields, tef);
      nFields++;
    }
  }

  /**
   * Gets the field name that corresponds to a given field id.
   * @param  id field id
   * @return    field name.
   */
  public static String getNameForId(int id) {
    return idToNameTable.get(id);
  }

  /**
   * Gets the field id that corresponds to a given field name.
   * @param  name field name
   * @return      field id
   */
  public static int getIdForName(String name) {
    return nameToIdTable.get(name);
  }

  /**
   * Gets the number of fields with assigned ids.
   * @return number of fields
   */
  public static int getNumberOfFields() {
    return nFields;
  }
}
