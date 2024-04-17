// Copyright 2023 Google LLC
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

package com.google.crypto.tink.daead.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.crypto.tink.internal.testing.Asserts.assertEqualWhenValueParsed;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.Key;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.daead.AesSivKey;
import com.google.crypto.tink.daead.AesSivParameters;
import com.google.crypto.tink.internal.MutableSerializationRegistry;
import com.google.crypto.tink.internal.ProtoKeySerialization;
import com.google.crypto.tink.internal.ProtoParametersSerialization;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.crypto.tink.util.SecretBytes;
import com.google.protobuf.ByteString;
import java.security.GeneralSecurityException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Test for AesSivProtoSerialization. */
@RunWith(Theories.class)
@SuppressWarnings("UnnecessarilyFullyQualified") // Fully specifying proto types is more readable
public final class AesSivProtoSerializationTest {
  private static final String TYPE_URL = "type.googleapis.com/google.crypto.tink.AesSivKey";

  private static final SecretBytes KEY_BYTES_32 = SecretBytes.randomBytes(32);
  private static final ByteString KEY_BYTES_32_AS_BYTE_STRING =
      ByteString.copyFrom(KEY_BYTES_32.toByteArray(InsecureSecretKeyAccess.get()));

  private static final MutableSerializationRegistry registry = new MutableSerializationRegistry();

  @BeforeClass
  public static void setUp() throws Exception {
    AesSivProtoSerialization.register(registry);
  }

  @Test
  public void registerTwice() throws Exception {
    MutableSerializationRegistry registry = new MutableSerializationRegistry();
    AesSivProtoSerialization.register(registry);
    AesSivProtoSerialization.register(registry);
  }

  @Test
  public void serializeParseParameters_noPrefix() throws Exception {
    AesSivParameters parameters =
        AesSivParameters.builder()
            .setKeySizeBytes(32)
            .setVariant(AesSivParameters.Variant.NO_PREFIX)
            .build();

    ProtoParametersSerialization serialization =
        ProtoParametersSerialization.create(
            "type.googleapis.com/google.crypto.tink.AesSivKey",
            OutputPrefixType.RAW,
            com.google.crypto.tink.proto.AesSivKeyFormat.newBuilder().setKeySize(32).build());

    ProtoParametersSerialization serialized =
        registry.serializeParameters(parameters, ProtoParametersSerialization.class);
    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesSivKeyFormat.parser(), serialized, serialization);

    Parameters parsed = registry.parseParameters(serialization);
    assertThat(parsed).isEqualTo(parameters);
  }

  @Test
  public void serializeParseParameters_tink() throws Exception {
    AesSivParameters parameters =
        AesSivParameters.builder()
            .setKeySizeBytes(32)
            .setVariant(AesSivParameters.Variant.TINK)
            .build();

    ProtoParametersSerialization serialization =
        ProtoParametersSerialization.create(
            "type.googleapis.com/google.crypto.tink.AesSivKey",
            OutputPrefixType.TINK,
            com.google.crypto.tink.proto.AesSivKeyFormat.newBuilder().setKeySize(32).build());

    ProtoParametersSerialization serialized =
        registry.serializeParameters(parameters, ProtoParametersSerialization.class);
    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesSivKeyFormat.parser(), serialized, serialization);

    Parameters parsed = registry.parseParameters(serialization);
    assertThat(parsed).isEqualTo(parameters);
  }

  @Test
  public void serializeParseParameters_crunchy() throws Exception {
    AesSivParameters parameters =
        AesSivParameters.builder()
            .setKeySizeBytes(32)
            .setVariant(AesSivParameters.Variant.CRUNCHY)
            .build();

    ProtoParametersSerialization serialization =
        ProtoParametersSerialization.create(
            "type.googleapis.com/google.crypto.tink.AesSivKey",
            OutputPrefixType.CRUNCHY,
            com.google.crypto.tink.proto.AesSivKeyFormat.newBuilder().setKeySize(32).build());

    ProtoParametersSerialization serialized =
        registry.serializeParameters(parameters, ProtoParametersSerialization.class);
    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesSivKeyFormat.parser(), serialized, serialization);

    Parameters parsed = registry.parseParameters(serialization);
    assertThat(parsed).isEqualTo(parameters);
  }

  @Test
  public void serializeParseKey_noPrefix() throws Exception {
    AesSivParameters parameters =
        AesSivParameters.builder()
            .setKeySizeBytes(32)
            .setVariant(AesSivParameters.Variant.NO_PREFIX)
            .build();

    AesSivKey key = AesSivKey.builder().setParameters(parameters).setKeyBytes(KEY_BYTES_32).build();

    com.google.crypto.tink.proto.AesSivKey protoAesSivKey =
        com.google.crypto.tink.proto.AesSivKey.newBuilder()
            .setVersion(0)
            .setKeyValue(KEY_BYTES_32_AS_BYTE_STRING)
            .build();

    ProtoKeySerialization serialization =
        ProtoKeySerialization.create(
            "type.googleapis.com/google.crypto.tink.AesSivKey",
            protoAesSivKey.toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.RAW,
            /* idRequirement= */ null);

    ProtoKeySerialization serialized =
        registry.serializeKey(key, ProtoKeySerialization.class, InsecureSecretKeyAccess.get());
    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesSivKey.parser(), serialized, serialization);

    Key parsed = registry.parseKey(serialization, InsecureSecretKeyAccess.get());
    assertThat(parsed.equalsKey(key)).isTrue();
  }

  @Test
  public void serializeParseKey_tink() throws Exception {
    AesSivParameters parameters =
        AesSivParameters.builder()
            .setKeySizeBytes(32)
            .setVariant(AesSivParameters.Variant.TINK)
            .build();

    AesSivKey key =
        AesSivKey.builder()
            .setParameters(parameters)
            .setKeyBytes(KEY_BYTES_32)
            .setIdRequirement(123)
            .build();

    com.google.crypto.tink.proto.AesSivKey protoAesSivKey =
        com.google.crypto.tink.proto.AesSivKey.newBuilder()
            .setVersion(0)
            .setKeyValue(KEY_BYTES_32_AS_BYTE_STRING)
            .build();

    ProtoKeySerialization serialization =
        ProtoKeySerialization.create(
            "type.googleapis.com/google.crypto.tink.AesSivKey",
            protoAesSivKey.toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            /* idRequirement= */ 123);

    ProtoKeySerialization serialized =
        registry.serializeKey(key, ProtoKeySerialization.class, InsecureSecretKeyAccess.get());
    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesSivKey.parser(), serialized, serialization);

    Key parsed = registry.parseKey(serialization, InsecureSecretKeyAccess.get());
    assertThat(parsed.equalsKey(key)).isTrue();
  }

  @Test
  public void serializeParseKey_crunchy() throws Exception {
    AesSivParameters parameters =
        AesSivParameters.builder()
            .setKeySizeBytes(32)
            .setVariant(AesSivParameters.Variant.CRUNCHY)
            .build();

    AesSivKey key =
        AesSivKey.builder()
            .setParameters(parameters)
            .setKeyBytes(KEY_BYTES_32)
            .setIdRequirement(123)
            .build();

    com.google.crypto.tink.proto.AesSivKey protoAesSivKey =
        com.google.crypto.tink.proto.AesSivKey.newBuilder()
            .setVersion(0)
            .setKeyValue(KEY_BYTES_32_AS_BYTE_STRING)
            .build();

    ProtoKeySerialization serialization =
        ProtoKeySerialization.create(
            "type.googleapis.com/google.crypto.tink.AesSivKey",
            protoAesSivKey.toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.CRUNCHY,
            /* idRequirement= */ 123);

    ProtoKeySerialization serialized =
        registry.serializeKey(key, ProtoKeySerialization.class, InsecureSecretKeyAccess.get());
    assertEqualWhenValueParsed(
        com.google.crypto.tink.proto.AesSivKey.parser(), serialized, serialization);

    Key parsed = registry.parseKey(serialization, InsecureSecretKeyAccess.get());
    assertThat(parsed.equalsKey(key)).isTrue();
  }

  @Test
  public void parseKey_legacy() throws Exception {
    ProtoKeySerialization serialization =
        ProtoKeySerialization.create(
            TYPE_URL,
            com.google.crypto.tink.proto.AesSivKey.newBuilder()
                .setVersion(0)
                .setKeyValue(KEY_BYTES_32_AS_BYTE_STRING)
                .build()
                .toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.LEGACY,
            1479);
    // Legacy keys are parsed to crunchy
    Key parsed = registry.parseKey(serialization, InsecureSecretKeyAccess.get());
    assertThat(((AesSivParameters) parsed.getParameters()).getVariant())
        .isEqualTo(AesSivParameters.Variant.CRUNCHY);
  }

  @Test
  public void testParseKeys_noAccess_throws() throws Exception {
    com.google.crypto.tink.proto.AesSivKey protoAesSivKey =
        com.google.crypto.tink.proto.AesSivKey.newBuilder()
            .setVersion(0)
            .setKeyValue(KEY_BYTES_32_AS_BYTE_STRING)
            .build();
    ProtoKeySerialization serialization =
        ProtoKeySerialization.create(
            "type.googleapis.com/google.crypto.tink.AesSivKey",
            protoAesSivKey.toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            /* idRequirement= */ 123);
    assertThrows(GeneralSecurityException.class, () -> registry.parseKey(serialization, null));
  }

  @Test
  public void testSerializeKeys_noAccess_throws() throws Exception {
    AesSivParameters parameters =
        AesSivParameters.builder()
            .setKeySizeBytes(32)
            .setVariant(AesSivParameters.Variant.TINK)
            .build();
    AesSivKey key =
        AesSivKey.builder()
            .setParameters(parameters)
            .setKeyBytes(KEY_BYTES_32)
            .setIdRequirement(123)
            .build();
    assertThrows(
        GeneralSecurityException.class,
        () -> registry.serializeKey(key, ProtoKeySerialization.class, null));
  }

  @DataPoints("invalidParametersSerializations")
  public static final ProtoParametersSerialization[] INVALID_PARAMETERS_SERIALIZATIONS =
      new ProtoParametersSerialization[] {
        // Bad version number
        ProtoParametersSerialization.create(
            TYPE_URL,
            OutputPrefixType.RAW,
            com.google.crypto.tink.proto.AesSivKeyFormat.newBuilder()
                .setVersion(1)
                .setKeySize(32)
                .build()),
        // Bad key size
        ProtoParametersSerialization.create(
            TYPE_URL,
            OutputPrefixType.RAW,
            com.google.crypto.tink.proto.AesSivKeyFormat.newBuilder().setKeySize(16).build()),
        // Unknown output prefix
        ProtoParametersSerialization.create(
            TYPE_URL,
            OutputPrefixType.UNKNOWN_PREFIX,
            com.google.crypto.tink.proto.AesSivKeyFormat.newBuilder().setKeySize(32).build()),
      };

  @Theory
  public void testParseInvalidParameters_fails(
      @FromDataPoints("invalidParametersSerializations")
          ProtoParametersSerialization serializedParameters)
      throws Exception {
    assertThrows(
        GeneralSecurityException.class, () -> registry.parseParameters(serializedParameters));
  }

  private static ProtoKeySerialization[] createInvalidKeySerializations() {
    try {
      return new ProtoKeySerialization[] {
        // Bad Version Number (1)
        ProtoKeySerialization.create(
            TYPE_URL,
            com.google.crypto.tink.proto.AesSivKey.newBuilder()
                .setVersion(1)
                .setKeyValue(KEY_BYTES_32_AS_BYTE_STRING)
                .build()
                .toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
        // Unknown prefix
        ProtoKeySerialization.create(
            TYPE_URL,
            com.google.crypto.tink.proto.AesSivKey.newBuilder()
                .setVersion(0)
                .setKeyValue(KEY_BYTES_32_AS_BYTE_STRING)
                .build()
                .toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.UNKNOWN_PREFIX,
            1479),
        // Bad Key Length
        ProtoKeySerialization.create(
            TYPE_URL,
            com.google.crypto.tink.proto.AesSivKey.newBuilder()
                .setVersion(0)
                .setKeyValue(ByteString.copyFrom(new byte[8]))
                .build()
                .toByteString(),
            KeyMaterialType.SYMMETRIC,
            OutputPrefixType.TINK,
            1479),
      };
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  @DataPoints("invalidKeySerializations")
  public static final ProtoKeySerialization[] INVALID_KEY_SERIALIZATIONS =
      createInvalidKeySerializations();

  @Theory
  public void testParseInvalidKeys_throws(
      @FromDataPoints("invalidKeySerializations") ProtoKeySerialization serialization)
      throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> registry.parseKey(serialization, InsecureSecretKeyAccess.get()));
  }
}
