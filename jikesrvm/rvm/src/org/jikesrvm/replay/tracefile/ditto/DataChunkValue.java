package org.jikesrvm.replay.tracefile.ditto;

import org.jikesrvm.SizeConstants;
import org.vmmagic.pragma.Inline;

/**
 * Encode and decode utilities for different values stored in data chunks of
 * a Ditto trace file.
 * <p>
 * There are four types of values that need to be included in Ditto data chunks:
 * <ul>
 *   <li>Free run values are encoded by using the most negative quarter of the
 *   range of each type {@code [Type.MIN_VALUE, (Type.MIN_VALUE >> 1)]}. As an
 *   example, a free run stored as a byte can have a maximum value of 64. If the
 *   free run's value is 6, then it will be encoded as {@code -64-6=-70}.</li>
 *   <li>Clock values and load count values are store without any encoding, but
 *   cannot use the range reserved for free run values. As such, the range used
 *   by these values is {@code [(Type.MIN_VALUE >> 1), Type.MAX_VALUE]}.</li>
 *   <li>Marks identify chains of free run, clock and load count values that
 *   are stored using the same type. As such, each mark must identify the
 *   type and length of its value chain. These two properties are encoded in
 *   a single short value.</li>
 * </ul>
 */
public final class DataChunkValue {

  /** Maximum value that can be encoded in a byte. */
  public static final byte   BYTE_MAX_VALUE     = Byte.MAX_VALUE;
  /** Minimum value that can be encoded in a byte. */
  public static final byte   BYTE_MIN_VALUE     = Byte.MIN_VALUE >> 1;
  /** Maximum free run length that can be encoded in a byte. */
  public static final byte   BYTE_MAX_FREE_RUN  = -BYTE_MIN_VALUE;

  /** Maximum value that can be encoded in a short. */
  public static final short  SHORT_MAX_VALUE    = Short.MAX_VALUE;
  /** Minimum value that can be encoded in a short. */
  public static final short  SHORT_MIN_VALUE    = Short.MIN_VALUE >> 1;
  /** Maximum free run length that can be encoded in a short. */
  public static final short  SHORT_MAX_FREE_RUN = -SHORT_MIN_VALUE;

  /** Maximum value that can be encoded in an int. */
  public static final int    INT_MAX_VALUE      = Integer.MAX_VALUE;
  /** Minimum value that can be encoded in an int. */
  public static final int    INT_MIN_VALUE      = Integer.MIN_VALUE >> 1;
  /** Maximum free run length that can be encoded in an int. */
  public static final int    INT_MAX_FREE_RUN   = -INT_MIN_VALUE;

  /** Maximum value that can be encoded in a long. */
  public static final long   LONG_MAX_VALUE     = Long.MAX_VALUE;
  /** Minimum value that can be encoded in a long. */
  public static final long   LONG_MIN_VALUE     = Long.MIN_VALUE >> 1;
  /** Maximum free run length that can be encoded in a long. */
  public static final long   LONG_MAX_FREE_RUN  = -LONG_MIN_VALUE;

  /** Size of a mark. */
  public static final int MARK_SIZE = SizeConstants.BYTES_IN_SHORT;

  /** Number of bytes that the value length is left-shifted in a mark. */
  private static final int MARK_VALUE_LEN_SHIFT = 13;
  /** Mask to obtain the size of a mark. */
  private static final short MARK_SIZE_MASK = 0x1fff;
  /** Mask to obtain the value length of a mark (after right shifting). */
  private static final short MARK_VALUE_LEN_MASK = 0x0007;

  /** Hidden constructor. */
  private DataChunkValue() { }

  ///
  /// Free run encoders and decoders
  ///

  /**
   * Encodes a free run in a byte value.
   * @param  freeRun free run length
   * @return         byte-encoded free run
   */
  @Inline
  public static byte encodeByteFreeRun(long freeRun) {
    return (byte)(-freeRun + BYTE_MIN_VALUE);
  }

  /**
   * Decodes a free run stored in a byte value.
   * @param  freeRun byte-encoded free run
   * @return         free run length
   */
  @Inline
  public static long decodeByteFreeRun(byte freeRun) {
    return -(freeRun - BYTE_MIN_VALUE);
  }

  /**
   * Checks whether a given byte value encodes a free run.
   * @param  b byte value
   * @return   whether the byte value encodes a free run
   */
  @Inline
  public static boolean isByteFreeRun(byte b) {
    return b < BYTE_MIN_VALUE;
  }

  /**
   * Encodes a free run in a short value.
   * @param  freeRun free run length
   * @return         short-encoded free run
   */
  @Inline
  public static short encodeShortFreeRun(long freeRun) {
    return (short)(-freeRun + SHORT_MIN_VALUE);
  }

  /**
   * Decodes a free run stored in a short value.
   * @param  freeRun short-encoded ree run
   * @return         free run length
   */
  @Inline
  public static long decodeShortFreeRun(short freeRun) {
    return -(freeRun - SHORT_MIN_VALUE);
  }

  /**
   * Checks whether a given short value encodes a free run.
   * @param  s short value
   * @return   whether the short value encodes a free run
   */
  @Inline
  public static boolean isShortFreeRun(short s) {
    return s < SHORT_MIN_VALUE;
  }

  /**
   * Encodes a free run in an int value.
   * @param  freeRun free run length
   * @return         int-encoded free run
   */
  @Inline
  public static int encodeIntFreeRun(long freeRun) {
    return (int)(-freeRun + INT_MIN_VALUE);
  }

  /**
   * Decodes a free run stored in an int value.
   * @param  freeRun int-encoded free run
   * @return         free run length
   */
  @Inline
  public static long decodeIntFreeRun(int freeRun) {
    return -(freeRun - INT_MIN_VALUE);
  }

  /**
   * Checks whether an int value encodes a free run.
   * @param  i int value
   * @return   whether the int value encodes a free run
   */
  @Inline
  public static boolean isIntFreeRun(int i) {
    return i < INT_MIN_VALUE;
  }

  /**
   * Encodes a free run in a long value.
   * @param  freeRun free run length
   * @return         long-encoded free run
   */
  @Inline
  public static long encodeLongFreeRun(long freeRun) {
    return -freeRun + LONG_MIN_VALUE;
  }

  /**
   * Decodes a free run stored in a long value.
   * @param  freeRun long-encoded free run
   * @return         free run length
   */
  @Inline
  public static long decodeLongFreeRun(long freeRun) {
    return -(freeRun - LONG_MIN_VALUE);
  }

  /**
   * Checks whether a given long value encodes a free run.
   * @param  l long value
   * @return   whether the long value encodes a free run
   */
  @Inline
  public static boolean isLongFreeRun(long l) {
    return l < LONG_MIN_VALUE;
  }


  ///
  /// Mark encoders and decoders
  ///

  /**
   * Encodes a mark in a short value.
   * @param  valueLength length of the values in the mark's chain
   * @param  size        size of the mark's chain
   * @return             short-encoded mark
   */
  @Inline
  public static short makeMark(int valueLength, int size) {
    return (short) (size + ((valueLength - 1) << MARK_VALUE_LEN_SHIFT));
  }

  /**
   * Obtains the length of values in a mark's chain.
   * @param  mark short-encoded mark
   * @return      length of values in the mark's chain
   */
  @Inline
  public static int getMarkValueLength(short mark) {
    return ((mark >> MARK_VALUE_LEN_SHIFT) & MARK_VALUE_LEN_MASK) + 1;
  }

  /**
   * Obtains the size of a mark's chain.
   * @param  mark short-encoded mark
   * @return      size of the mark's chain
   */
  @Inline
  public static int getMarkSize(short mark) {
    return mark & MARK_SIZE_MASK;
  }
}
