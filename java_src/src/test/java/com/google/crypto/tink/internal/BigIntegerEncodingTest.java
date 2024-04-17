// Copyright 2022 Google LLC
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

package com.google.crypto.tink.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BigIntegerEncodingTest {

  @Test
  public void toBigEndianBytes() throws Exception {
    assertThat(BigIntegerEncoding.toBigEndianBytes(BigInteger.ZERO))
        .isEqualTo(new byte[] {(byte) 0x00});
    assertThat(BigIntegerEncoding.toBigEndianBytes(BigInteger.ONE))
        .isEqualTo(new byte[] {(byte) 0x01});
    assertThat(BigIntegerEncoding.toBigEndianBytes(BigInteger.valueOf(127)))
        .isEqualTo(new byte[] {(byte) 0x7F});
    // The most significant bit of the first byte is used to encode the sign of the number.
    // Therefore, 128 needs to be encoded with two bytes.
    assertThat(BigIntegerEncoding.toBigEndianBytes(BigInteger.valueOf(128)))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x80});
    assertThat(BigIntegerEncoding.toBigEndianBytes(BigInteger.valueOf(255)))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0xFF});
    assertThat(BigIntegerEncoding.toBigEndianBytes(BigInteger.valueOf(256)))
        .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x00});
    assertThat(BigIntegerEncoding.toBigEndianBytes(BigInteger.valueOf(258)))
        .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x02});
  }

  @Test
  public void toUnsignedBigEndianBytes() throws Exception {
    // The first byte is always zero.
    assertThat(BigIntegerEncoding.toUnsignedBigEndianBytes(BigInteger.ZERO))
        .isEqualTo(new byte[] {});
    assertThat(BigIntegerEncoding.toUnsignedBigEndianBytes(BigInteger.ONE))
        .isEqualTo(new byte[] {(byte) 0x01});
    assertThat(BigIntegerEncoding.toUnsignedBigEndianBytes(BigInteger.valueOf(127)))
        .isEqualTo(new byte[] {(byte) 0x7F});
    assertThat(BigIntegerEncoding.toUnsignedBigEndianBytes(BigInteger.valueOf(128)))
        .isEqualTo(new byte[] {(byte) 0x80});
    assertThat(BigIntegerEncoding.toUnsignedBigEndianBytes(BigInteger.valueOf(255)))
        .isEqualTo(new byte[] {(byte) 0xFF});
    assertThat(BigIntegerEncoding.toUnsignedBigEndianBytes(BigInteger.valueOf(256)))
        .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x00});
    assertThat(BigIntegerEncoding.toUnsignedBigEndianBytes(BigInteger.valueOf(258)))
        .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x02});
  }

  @Test
  public void toBigEndianBytesOfFixedLength_success() throws Exception {
    assertThat(BigIntegerEncoding.toBigEndianBytesOfFixedLength(BigInteger.ZERO, /* length= */ 0))
        .isEqualTo(new byte[] {});
    assertThat(BigIntegerEncoding.toBigEndianBytesOfFixedLength(BigInteger.ZERO, /* length= */ 1))
        .isEqualTo(new byte[] {(byte) 0x00});
    assertThat(BigIntegerEncoding.toBigEndianBytesOfFixedLength(BigInteger.ZERO, /* length= */ 2))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x00});
    assertThat(BigIntegerEncoding.toBigEndianBytesOfFixedLength(BigInteger.ONE, /* length= */ 1))
        .isEqualTo(new byte[] {(byte) 0x01});
    assertThat(BigIntegerEncoding.toBigEndianBytesOfFixedLength(BigInteger.ONE, /* length= */ 2))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x01});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(127), /* length= */ 1))
        .isEqualTo(new byte[] {(byte) 0x7F});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(127), /* length= */ 2))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x7F});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(127), /* length= */ 3))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x00, (byte) 0x7F});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(128), /* length= */ 1))
        .isEqualTo(new byte[] {(byte) 0x80});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(128), /* length= */ 2))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x80});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(128), /* length= */ 3))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x00, (byte) 0x80});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(255), /* length= */ 1))
        .isEqualTo(new byte[] {(byte) 0xFF});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(255), /* length= */ 2))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0xFF});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(255), /* length= */ 3))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x00, (byte) 0xFF});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(256), /* length= */ 2))
        .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x00});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(258), /* length= */ 2))
        .isEqualTo(new byte[] {(byte) 0x01, (byte) 0x02});
    assertThat(
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(258), /* length= */ 4))
        .isEqualTo(new byte[] {(byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x02});
  }

  @Test
  public void toBigEndianBytesOfFixedLength_failWhenIntegerIsNegative() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(-1), /* length= */ 2));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(-42), /* length= */ 2));
  }

  @Test
  public void toBigEndianBytesOfFixedLength_failWhenIntegerIsTooLargerForLength() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> BigIntegerEncoding.toBigEndianBytesOfFixedLength(BigInteger.ONE, /* length= */ 0));
    assertThrows(
        GeneralSecurityException.class,
        () ->
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(256), /* length= */ 1));
    assertThrows(
        GeneralSecurityException.class,
        () ->
            BigIntegerEncoding.toBigEndianBytesOfFixedLength(
                BigInteger.valueOf(256 * 256), /* length= */ 2));
  }

  @Test
  public void fromUnsignedBigEndianBytes() throws Exception {
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {(byte) 0x00}))
        .isEqualTo(BigInteger.ZERO);
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {(byte) 0x01}))
        .isEqualTo(BigInteger.ONE);
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {(byte) 0x7F}))
        .isEqualTo(BigInteger.valueOf(127));
    // The input should be interpreted as an unsigned integers. So 0x80 is 128.
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {(byte) 0x80}))
        .isEqualTo(BigInteger.valueOf(128));
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {(byte) 0xFF}))
        .isEqualTo(BigInteger.valueOf(255));
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {(byte) 0x01, (byte) 0x00}))
        .isEqualTo(BigInteger.valueOf(256));
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {(byte) 0x01, (byte) 0x02}))
        .isEqualTo(BigInteger.valueOf(258));
    // leading zeros are ignored
    assertThat(
            BigIntegerEncoding.fromUnsignedBigEndianBytes(
                new byte[] {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x02}))
        .isEqualTo(BigInteger.valueOf(258));
    // the empty array is decoded to 0.
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(new byte[] {}))
        .isEqualTo(BigInteger.ZERO);
  }

  @Test
  public void toBigEndianBytes_failsForNegativeNumbers() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> BigIntegerEncoding.toBigEndianBytes(BigInteger.valueOf(-1)));
  }

  @Test
  public void toUnsignedBigEndianBytes_failWhenIntegerIsNegative() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> BigIntegerEncoding.toBigEndianBytes(BigInteger.valueOf(-1)));
  }

  @Test
  public void fromUnsignedBigEndianBytes_canParseByteEncodedBigInteger() throws Exception {
    for (int i = 0; i < 1000; i = i + 37) {
      BigInteger bigInt = BigInteger.valueOf(i);
      assertThat(
              BigIntegerEncoding.fromUnsignedBigEndianBytes(
                  BigIntegerEncoding.toUnsignedBigEndianBytes(bigInt)))
          .isEqualTo(bigInt);
      assertThat(
              BigIntegerEncoding.fromUnsignedBigEndianBytes(
                  BigIntegerEncoding.toBigEndianBytes(bigInt)))
          .isEqualTo(bigInt);
    }
  }

  private BigInteger fromSignedBigEndianBytes(byte[] bytes) {
    // the constructor of BigInteger takes a two's-complement big-endian byte array as input.
    return new BigInteger(bytes);
  }

  @Test
  public void toBigEndianBytes_canBeParsedAsSignedOrUnsigned() throws Exception {
    byte[] encoded0 = BigIntegerEncoding.toBigEndianBytes(BigInteger.ZERO);
    assertThat(encoded0).hasLength(1);
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(encoded0)).isEqualTo(BigInteger.ZERO);
    assertThat(fromSignedBigEndianBytes(encoded0)).isEqualTo(BigInteger.ZERO);

    // 10 is a 1-byte unsigned integer with the most significant bit 0.
    byte[] encoded10 = BigIntegerEncoding.toBigEndianBytes(BigInteger.TEN);
    assertThat(encoded10).hasLength(1);
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(encoded10)).isEqualTo(BigInteger.TEN);
    assertThat(fromSignedBigEndianBytes(encoded10)).isEqualTo(BigInteger.TEN);

    // 130 is a 1-byte unsigned integer with the most significant bit 1.
    BigInteger bigInt130 = BigInteger.valueOf(130);
    byte[] encoded130 = BigIntegerEncoding.toBigEndianBytes(bigInt130);
    assertThat(encoded130).hasLength(2);
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(encoded130)).isEqualTo(bigInt130);
    assertThat(fromSignedBigEndianBytes(encoded130)).isEqualTo(bigInt130);

    // 30000 is a 2-byte unsigned integer with the most significant bit 0.
    BigInteger bigInt30k = BigInteger.valueOf(30000);
    byte[] encoded30k = BigIntegerEncoding.toBigEndianBytes(bigInt30k);
    assertThat(encoded30k).hasLength(2);
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(encoded30k)).isEqualTo(bigInt30k);
    assertThat(fromSignedBigEndianBytes(encoded30k)).isEqualTo(bigInt30k);

    // 60000 is a 2-byte unsigned integer with the most significant bit 1.
    BigInteger bigInt60k = BigInteger.valueOf(60000);
    byte[] encoded60k = BigIntegerEncoding.toBigEndianBytes(bigInt60k);
    assertThat(encoded60k).hasLength(3);
    assertThat(BigIntegerEncoding.fromUnsignedBigEndianBytes(encoded60k)).isEqualTo(bigInt60k);
    assertThat(fromSignedBigEndianBytes(encoded60k)).isEqualTo(bigInt60k);
  }
}
