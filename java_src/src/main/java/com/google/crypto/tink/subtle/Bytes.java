// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.subtle;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Helper methods that deal with byte arrays.
 *
 * @since 1.0.0
 */
public final class Bytes {
  /**
   * Best effort fix-timing array comparison.
   *
   * @return true if two arrays are equal.
   */
  public static final boolean equal(final byte[] x, final byte[] y) {
    return MessageDigest.isEqual(x, y);
  }

  /**
   * Returns the concatenation of the input arrays in a single array. For example, {@code concat(new
   * byte[] {a, b}, new byte[] {}, new byte[] {c}} returns the array {@code {a, b, c}}.
   *
   * @return a single array containing all the values from the source arrays, in order
   */
  public static byte[] concat(byte[]... chunks) throws GeneralSecurityException {
    int length = 0;
    for (byte[] chunk : chunks) {
      if (length > Integer.MAX_VALUE - chunk.length) {
        throw new GeneralSecurityException("exceeded size limit");
      }
      length += chunk.length;
    }
    byte[] res = new byte[length];
    int pos = 0;
    for (byte[] chunk : chunks) {
      System.arraycopy(chunk, 0, res, pos, chunk.length);
      pos += chunk.length;
    }
    return res;
  }

  /**
   * Computes the xor of two byte arrays, specifying offsets and the length to xor.
   *
   * @return a new byte[] of length len.
   */
  public static final byte[] xor(
      final byte[] x, int offsetX, final byte[] y, int offsetY, int len) {
    if (len < 0 || x.length - len < offsetX || y.length - len < offsetY) {
      throw new IllegalArgumentException(
          "That combination of buffers, offsets and length to xor result in out-of-bond accesses.");
    }
    byte[] res = new byte[len];
    for (int i = 0; i < len; i++) {
      res[i] = (byte) (x[i + offsetX] ^ y[i + offsetY]);
    }
    return res;
  }

  /**
   * Computes the xor of two byte buffers, specifying the length to xor, and stores the result to
   * {@code output}.
   */
  public static final void xor(ByteBuffer output, ByteBuffer x, ByteBuffer y, int len) {
    if (len < 0 || x.remaining() < len || y.remaining() < len || output.remaining() < len) {
      throw new IllegalArgumentException(
          "That combination of buffers, offsets and length to xor result in out-of-bond accesses.");
    }
    for (int i = 0; i < len; i++) {
      output.put((byte) (x.get() ^ y.get()));
    }
  }

  /**
   * Computes the xor of two byte arrays of equal size.
   *
   * @return a new byte[] of length x.length.
   */
  public static final byte[] xor(final byte[] x, final byte[] y) {
    if (x.length != y.length) {
      throw new IllegalArgumentException("The lengths of x and y should match.");
    }
    return xor(x, 0, y, 0, x.length);
  }

  /**
   * xors b to the end of a.
   *
   * @return a new byte[] of length x.length.
   */
  public static final byte[] xorEnd(final byte[] a, final byte[] b) {
    if (a.length < b.length) {
      throw new IllegalArgumentException("xorEnd requires a.length >= b.length");
    }
    int paddingLength = a.length - b.length;
    byte[] res = Arrays.copyOf(a, a.length);
    for (int i = 0; i < b.length; i++) {
      res[paddingLength + i] ^= b[i];
    }
    return res;
  }

  /**
   * Transforms a passed value to a LSB first byte array with the size of the specified capacity
   *
   * @param capacity size of the resulting byte array
   * @param value that should be represented as a byte array. 0 <= value < 256^capacity.
   */
  public static byte[] intToByteArray(int capacity, int value) {
    if ((capacity > 4) || (capacity < 0)) {
      throw new IllegalArgumentException("capacity must be between 0 and 4");
    }
    // Check that 0 <= value < 256^capacity.
    // For capacity == 4, all positive values are valid.
    if (value < 0 || (capacity < 4 && (value >= 1 << (8 * capacity)))) {
      throw new IllegalArgumentException("value too large");
    }
    final byte[] result = new byte[capacity];
    for (int i = 0; i < capacity; i++) {
      result[i] = (byte) ((value >> (8 * i)) & 0xFF);
    }
    return result;
  }

  /**
   * Transforms a passed LSB first byte array to an int
   *
   * @param bytes that should be transformed to a byte array
   */
  public static int byteArrayToInt(byte[] bytes) {
    return byteArrayToInt(bytes, bytes.length);
  }

  /**
   * Transforms a passed LSB first byte array to an int
   *
   * @param bytes that should be transformed to a byte array
   * @param length amount of the passed {@code bytes} that should be transformed
   */
  public static int byteArrayToInt(byte[] bytes, int length) {
    return byteArrayToInt(bytes, 0, length);
  }

  /**
   * Transforms a passed LSB first byte array to an int
   *
   * @param bytes that should be transformed to a byte array
   * @param offset start index to start the transformation
   * @param length amount of the passed {@code bytes} that should be transformed
   */
  public static int byteArrayToInt(byte[] bytes, int offset, int length) {
    if ((length > 4) || (length < 0)) {
      throw new IllegalArgumentException("length must be between 0 and 4");
    }
    if (offset < 0 || offset + length > bytes.length) {
      throw new IllegalArgumentException("offset and length are out of bounds");
    }
    int value = 0;
    for (int i = 0; i < length; i++) {
      value += (bytes[i + offset] & 0xFF) << (i * 8);
    }
    return value;
  }

  private Bytes() {}
}
