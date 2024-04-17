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

package com.google.crypto.tink.mac.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.crypto.tink.internal.testing.Asserts.assertEqualWhenValueParsed;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.Key;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.internal.MutableSerializationRegistry;
import com.google.crypto.tink.internal.ProtoKeySerialization;
import com.google.crypto.tink.internal.ProtoParametersSerialization;
import com.google.crypto.tink.internal.testing.KeyWithSerialization;
import com.google.crypto.tink.internal.testing.ParametersWithSerialization;
import com.google.crypto.tink.mac.AesCmacKey;
import com.google.crypto.tink.mac.AesCmacParameters;
import com.google.crypto.tink.proto.AesCmacParams;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.proto.KeyTemplate;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.crypto.tink.util.SecretBytes;
import com.google.protobuf.ByteString;
import java.security.GeneralSecurityException;
import javax.annotation.Nullable;
import org.junit.BeforeClass;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Test for AesCmacProtoSerialization. */
@RunWith(Theories.class)
@SuppressWarnings("UnnecessarilyFullyQualified") // Fully specifying proto types is more readable
public final class AesCmacProtoSerializationTest {
  private static final String TYPE_URL = "type.googleapis.com/google.crypto.tink.AesCmacKey";

  private static final SecretBytes AES_KEY_32 = SecretBytes.randomBytes(32);
  private static final SecretBytes AES_KEY_16 = SecretBytes.randomBytes(16);
  private static final ByteString AES_KEY_AS_BYTE_STRING_32 =
      ByteString.copyFrom(AES_KEY_32.toByteArray(InsecureSecretKeyAccess.get()));
  private static final ByteString AES_KEY_AS_BYTE_STRING_16 =
      ByteString.copyFrom(AES_KEY_16.toByteArray(InsecureSecretKeyAccess.get()));

  private static final MutableSerializationRegistry registry = new MutableSerializationRegistry();

  @BeforeClass
  public static void setUp() throws Exception {
    AesCmacProtoSerialization.register(registry);
  }

  static AesCmacParameters createAesCmacParameters(
      int keySize, int tagSize, AesCmacParameters.Variant variant) {
    try {
      return AesCmacParameters.builder().setKeySizeBytes(keySize).setTagSizeBytes(tagSize).setVariant(
          variant).build();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  static AesCmacKey createKey(
      int keySize,
      int tagSize,
      AesCmacParameters.Variant variant,
      SecretBytes aesKey,
      @Nullable Integer idRequirement)
      throws GeneralSecurityException {
    return AesCmacKey.builder()
        .setParameters(createAesCmacParameters(keySize, tagSize, variant))
        .setAesKeyBytes(aesKey)
        .setIdRequirement(idRequirement)
        .build();
  }

  static com.google.crypto.tink.proto.AesCmacKeyFormat createProtoFormat(int keySize, int tagSize) {
    return com.google.crypto.tink.proto.AesCmacKeyFormat.newBuilder()
        .setKeySize(keySize)
        .setParams(AesCmacParams.newBuilder().setTagSize(tagSize))
        .build();
  }

  static com.google.crypto.tink.proto.AesCmacKey createProtoKey(int tagSize, ByteString aesKey) {
    return com.google.crypto.tink.proto.AesCmacKey.newBuilder()
        .setVersion(0)
        .setKeyValue(aesKey)
        .setParams(AesCmacParams.newBuilder().setTagSize(tagSize))
        .build();
  }

  @DataPoints("validParameters")
  public static final ParametersWithSerialization[] VALID_PARAMETERS =
      new ParametersWithSerialization[] {
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 16, /*tagSize=*/ 16, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 16, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 16, /*tagSize=*/ 16, AesCmacParameters.Variant.CRUNCHY),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.CRUNCHY,
                createProtoFormat(/*keySize=*/ 16, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 16, /*tagSize=*/ 16, AesCmacParameters.Variant.LEGACY),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.LEGACY,
                createProtoFormat(/*keySize=*/ 16, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 16, /*tagSize=*/ 16, AesCmacParameters.Variant.NO_PREFIX),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.RAW,
                createProtoFormat(/*keySize=*/ 16, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 16, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 16, AesCmacParameters.Variant.CRUNCHY),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.CRUNCHY,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 16, AesCmacParameters.Variant.LEGACY),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.LEGACY,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 16, AesCmacParameters.Variant.NO_PREFIX),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.RAW,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 16))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 10, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 10))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 11, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 11))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 12, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 12))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 13, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 13))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 14, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 14))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 15, AesCmacParameters.Variant.TINK),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.TINK,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 15))),
        new ParametersWithSerialization(
            createAesCmacParameters(
                /*keySize=*/ 32, /*tagSize=*/ 11, AesCmacParameters.Variant.NO_PREFIX),
            ProtoParametersSerialization.create(
                TYPE_URL,
                OutputPrefixType.RAW,
                createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 11))),
      };

  @DataPoints("invalidParameters")
  public static final ProtoParametersSerialization[] INVALID_PARAMETERS =
      new ProtoParametersSerialization[] {
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 9)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 7)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 16, /*tagSize=*/ 17)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 16, /*tagSize=*/ 19)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 32)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 1, /*tagSize=*/ 10)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ -1, /*tagSize=*/ 10)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 20, /*tagSize=*/ 10)),
        ProtoParametersSerialization.create(
            TYPE_URL, OutputPrefixType.RAW, createProtoFormat(/*keySize=*/ 390, /*tagSize=*/ 10)),
        ProtoParametersSerialization.create(
            TYPE_URL,
            OutputPrefixType.UNKNOWN_PREFIX,
            createProtoFormat(/*keySize=*/ 32, /*tagSize=*/ 16)),
        // Proto messages start with a VarInt, which always ends with a byte with most
        // significant bit unset. 0x80 is hence invalid.
        ProtoParametersSerialization.create(
            KeyTemplate.newBuilder()
                .setTypeUrl(TYPE_URL)
                .setOutputPrefixType(OutputPrefixType.RAW)
                .setValue(ByteString.copyFrom(new byte[] {(byte) 0x80}))
                .build()),
      };

  @Theory
  public void testSerializeParameters(
      @FromDataPoints("validParameters") ParametersWithSerialization pair) throws Exception {

    ProtoParametersSerialization serializedParameters =
        registry.serializeParameters(pair.getParameters(), ProtoParametersSerialization.class);

    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesCmacKeyFormat.parser(),
        serializedParameters,
        pair.getSerializedParameters());
  }

  @Theory
  public void testParseValidParameters(
      @FromDataPoints("validParameters") ParametersWithSerialization pair) throws Exception {
    Parameters parsed = registry.parseParameters(pair.getSerializedParameters());
    assertThat(parsed).isEqualTo(pair.getParameters());
  }

  @Theory
  public void testParseInvalidParameters_fails(
      @FromDataPoints("invalidParameters") ProtoParametersSerialization serializedParameters)
      throws Exception {
    assertThrows(
        GeneralSecurityException.class, () -> registry.parseParameters(serializedParameters));
  }

  private static KeyWithSerialization[] createValidKeys() {
    try {
      return new KeyWithSerialization[] {
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 32,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.TINK,
                AES_KEY_32,
                /*idRequirement=*/ 1479),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_32).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.TINK,
                /*idRequirement=*/ 1479)),
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 32,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.CRUNCHY,
                AES_KEY_32,
                /*idRequirement=*/ 1479),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_32).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.CRUNCHY,
                /*idRequirement=*/ 1479)),
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 32,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.LEGACY,
                AES_KEY_32,
                /*idRequirement=*/ 1479),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_32).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.LEGACY,
                /*idRequirement=*/ 1479)),
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 32,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.NO_PREFIX,
                AES_KEY_32,
                /*idRequirement=*/ null),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_32).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.RAW,
                /*idRequirement=*/ null)),
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 16,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.TINK,
                AES_KEY_16,
                /*idRequirement=*/ 1479),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_16).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.TINK,
                /*idRequirement=*/ 1479)),
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 16,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.CRUNCHY,
                AES_KEY_16,
                /*idRequirement=*/ 1479),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_16).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.CRUNCHY,
                /*idRequirement=*/ 1479)),
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 16,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.LEGACY,
                AES_KEY_16,
                /*idRequirement=*/ 1479),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_16).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.LEGACY,
                /*idRequirement=*/ 1479)),
        new KeyWithSerialization(
            createKey(
                /*keySize=*/ 16,
                /*tagSize=*/ 16,
                AesCmacParameters.Variant.NO_PREFIX,
                AES_KEY_16,
                /*idRequirement=*/ null),
            ProtoKeySerialization.create(
                TYPE_URL,
                createProtoKey(/*tagSize=*/ 16, AES_KEY_AS_BYTE_STRING_16).toByteString(),
                KeyMaterialType.SYMMETRIC,
                OutputPrefixType.RAW,
                /*idRequirement=*/ null)),
      };
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private static ProtoKeySerialization[] createInvalidKeys() {
    try {
      return new ProtoKeySerialization[] {
        // Bad Version Number (1)
        ProtoKeySerialization.create(
            TYPE_URL,
            createProtoKey(16, AES_KEY_AS_BYTE_STRING_32).toBuilder()
                .setVersion(1)
                .build()
                .toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
        // Unknown prefix
        ProtoKeySerialization.create(
            TYPE_URL,
            createProtoKey(16, AES_KEY_AS_BYTE_STRING_16).toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.UNKNOWN_PREFIX,
            1479),
        // Bad Tag Length (9)
        ProtoKeySerialization.create(
            TYPE_URL,
            createProtoKey(9, AES_KEY_AS_BYTE_STRING_32).toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
        // Bad Tag Length (17)
        ProtoKeySerialization.create(
            TYPE_URL,
            createProtoKey(17, AES_KEY_AS_BYTE_STRING_16).toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
        // Bad Key Length (31)
        ProtoKeySerialization.create(
            TYPE_URL,
            createProtoKey(16, ByteString.copyFrom(new byte[31])).toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
        // Bad Key Length (64)
        ProtoKeySerialization.create(
            TYPE_URL,
            createProtoKey(16, ByteString.copyFrom(new byte[64])).toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
        // Invalid proto encoding
        ProtoKeySerialization.create(
            TYPE_URL,
            // Proto messages start with a VarInt, which always ends with a byte with most
            // significant bit unset. 0x80 is hence invalid.
            ByteString.copyFrom(new byte[] {(byte) 0x80}),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
        // Wrong Type URL -- not sure if this should be tested; this won't even get to the code
        // under test.
        ProtoKeySerialization.create(
            "WrongTypeUrl",
            createProtoKey(16, AES_KEY_AS_BYTE_STRING_32).toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
      };
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  @DataPoints("validKeys")
  public static final KeyWithSerialization[] VALID_KEYS = createValidKeys();

  @DataPoints("invalidKeys")
  public static final ProtoKeySerialization[] INVALID_KEYS = createInvalidKeys();

  @Theory
  public void testSerializeKeys(@FromDataPoints("validKeys") KeyWithSerialization pair)
      throws Exception {
    ProtoKeySerialization tinkFormatSerialized =
        registry.serializeKey(
            pair.getKey(), ProtoKeySerialization.class, InsecureSecretKeyAccess.get());
    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesCmacKey.parser(),
        tinkFormatSerialized,
        pair.getSerialization());
  }

  @Theory
  public void testParseKeys(@FromDataPoints("validKeys") KeyWithSerialization pair)
      throws Exception {
    Key parsed = registry.parseKey(pair.getSerialization(), InsecureSecretKeyAccess.get());
    assertThat(parsed.equalsKey(pair.getKey())).isTrue();
  }

  @Theory
  public void testSerializeKeys_noAccess_throws(
      @FromDataPoints("validKeys") KeyWithSerialization pair) throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> registry.serializeKey(pair.getKey(), ProtoKeySerialization.class, null));
  }

  @Theory
  public void testParseKeys_noAccess_throws(@FromDataPoints("validKeys") KeyWithSerialization pair)
      throws Exception {
    assertThrows(
        GeneralSecurityException.class, () -> registry.parseKey(pair.getSerialization(), null));
  }

  @Theory
  public void testParseInvalidKeys_throws(
      @FromDataPoints("invalidKeys") ProtoKeySerialization serialization) throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> registry.parseKey(serialization, InsecureSecretKeyAccess.get()));
  }
}
