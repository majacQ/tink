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

package com.google.crypto.tink;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AesGcmParameters;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.internal.LegacyProtoParameters;
import com.google.crypto.tink.internal.MutableSerializationRegistry;
import com.google.crypto.tink.internal.ParametersSerializer;
import com.google.crypto.tink.internal.ProtoParametersSerialization;
import com.google.crypto.tink.proto.AesGcmKeyFormat;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.protobuf.ByteString;
import java.security.GeneralSecurityException;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class KeyTemplateTest {
  private static final String AES_GCM_TYPE_URL = "type.googleapis.com/google.crypto.tink.AesGcmKey";

  @BeforeClass
  public static void setUpClass() throws Exception {
    AeadConfig.register();
  }

  @Test
  public void testToParameters_aesGcm_works() throws Exception {
    AesGcmKeyFormat format = AesGcmKeyFormat.newBuilder().setKeySize(16).build();

    assertThat(
            KeyTemplate.create(
                    AES_GCM_TYPE_URL, format.toByteArray(), KeyTemplate.OutputPrefixType.RAW)
                .toParameters())
        .isEqualTo(
            AesGcmParameters.builder()
                .setKeySizeBytes(16)
                .setIvSizeBytes(12)
                .setTagSizeBytes(16)
                .setVariant(AesGcmParameters.Variant.NO_PREFIX)
                .build());

    assertThat(
            KeyTemplate.create(
                    AES_GCM_TYPE_URL, format.toByteArray(), KeyTemplate.OutputPrefixType.TINK)
                .toParameters())
        .isEqualTo(
            AesGcmParameters.builder()
                .setKeySizeBytes(16)
                .setIvSizeBytes(12)
                .setTagSizeBytes(16)
                .setVariant(AesGcmParameters.Variant.TINK)
                .build());
  }

  @Test
  public void testToParameters_unParseableAesGcm_fails() throws Exception {
    // Invalid Key size
    AesGcmKeyFormat format = AesGcmKeyFormat.newBuilder().setKeySize(17).build();
    KeyTemplate template =
        KeyTemplate.create(
            AES_GCM_TYPE_URL, format.toByteArray(), KeyTemplate.OutputPrefixType.RAW);
    assertThrows(GeneralSecurityException.class, template::toParameters);
  }

  @Test
  public void testToParameters_notRegisteredTypeUrl_givesLegacy() throws Exception {
    Parameters p =
        KeyTemplate.create("nonexistenttypeurl", new byte[] {1}, KeyTemplate.OutputPrefixType.TINK)
            .toParameters();
    assertThat(p).isInstanceOf(LegacyProtoParameters.class);
    LegacyProtoParameters parameters = (LegacyProtoParameters) p;
    assertThat(parameters.getSerialization().getKeyTemplate().getTypeUrl())
        .isEqualTo("nonexistenttypeurl");
    assertThat(parameters.getSerialization().getKeyTemplate().getValue())
        .isEqualTo(ByteString.copyFrom(new byte[] {1}));
    assertThat(parameters.getSerialization().getKeyTemplate().getOutputPrefixType())
        .isEqualTo(OutputPrefixType.TINK);
  }

  @Test
  public void testCreateFromParameters_works() throws Exception {
    assertThat(KeyTemplate.createFrom(PredefinedAeadParameters.AES128_GCM).toParameters())
        .isEqualTo(PredefinedAeadParameters.AES128_GCM);
  }

  @Test
  public void testCreateFromParameters_unparseable_throws() throws Exception {
    Parameters p =
        new Parameters() {
          @Override
          public boolean hasIdRequirement() {
            return false;
          }
        };
    KeyTemplate t = KeyTemplate.createFrom(p);
    assertThrows(RuntimeException.class, () -> t.getTypeUrl());
  }

  private static class ParametersSubclass extends Parameters {
    ParametersSubclass() {}

    @Override
    public boolean hasIdRequirement() {
      return false;
    }
  }

  private static ParametersSerializer<ParametersSubclass, ProtoParametersSerialization>
      PARAMETERS_SUBCLASS_SERIALIZER =
          ParametersSerializer.create(
              (ParametersSubclass p) ->
                  ProtoParametersSerialization.create(
                      "sometypeurl", OutputPrefixType.RAW, AesGcmKeyFormat.getDefaultInstance()),
              ParametersSubclass.class,
              ProtoParametersSerialization.class);

  @Test
  public void testCreateFromParameters_unserializableAtCreationButLaterYes_works()
      throws Exception {
    Parameters p = new ParametersSubclass();
    KeyTemplate t = KeyTemplate.createFrom(p);
    // We only do this in this test, and never use it elsewhere -- hence this global state does not
    // break anything.
    MutableSerializationRegistry.globalInstance()
        .registerParametersSerializer(PARAMETERS_SUBCLASS_SERIALIZER);
    assertThat(t.getTypeUrl()).isEqualTo("sometypeurl");
  }
}
