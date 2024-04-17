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

package com.google.crypto.tink.tinkey;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.mac.MacConfig;
import com.google.crypto.tink.mac.PredefinedMacParameters;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@code AddKeyCommand}. */
@RunWith(JUnit4.class)
public class AddKeyCommandTest {
  @BeforeClass
  public static void setUp() throws Exception {
    AeadConfig.register();
    MacConfig.register();
    KmsClientsFactory.globalInstance().addFactory(TinkeyTestKmsClient::new);
  }

  @Test
  public void testAddKey_json_works() throws Exception {
    Path path = Files.createTempDirectory(/* prefix= */ "");
    Path inputFile = Paths.get(path.toString(), "input");
    Path outputFile = Paths.get(path.toString(), "output");

    KeysetHandle inputKeyset =
        KeysetHandle.generateNew(PredefinedMacParameters.HMAC_SHA256_128BITTAG);
    String serializedKeyset =
        TinkJsonProtoKeysetFormat.serializeKeyset(inputKeyset, InsecureSecretKeyAccess.get());
    Files.write(inputFile, serializedKeyset.getBytes(UTF_8));

    Tinkey.main(
        new String[] {
          "add-key",
          "--in",
          inputFile.toString(),
          "--out",
          outputFile.toString(),
          "--key-template",
          "HMAC_SHA256_256BITTAG",
        });

    KeysetHandle handle =
        TinkJsonProtoKeysetFormat.parseKeyset(
            new String(Files.readAllBytes(outputFile), UTF_8), InsecureSecretKeyAccess.get());

    assertThat(handle.size()).isEqualTo(2);
    assertThat(handle.getAt(0).getKey().equalsKey(inputKeyset.getAt(0).getKey())).isTrue();
    assertThat(handle.getAt(0).isPrimary()).isTrue();
    assertThat(handle.getAt(1).getKey().getParameters())
        .isEqualTo(PredefinedMacParameters.HMAC_SHA256_256BITTAG);
    assertThat(handle.getAt(1).isPrimary()).isFalse();
  }

  @Test
  public void testAddKey_binary_works() throws Exception {
    Path path = Files.createTempDirectory(/* prefix= */ "");
    Path inputFile = Paths.get(path.toString(), "input");
    Path outputFile = Paths.get(path.toString(), "output");

    KeysetHandle inputKeyset =
        KeysetHandle.generateNew(PredefinedMacParameters.HMAC_SHA256_128BITTAG);
    byte[] serializedKeyset =
        TinkProtoKeysetFormat.serializeKeyset(inputKeyset, InsecureSecretKeyAccess.get());
    Files.write(inputFile, serializedKeyset);

    Tinkey.main(
        new String[] {
          "add-key",
          "--in",
          inputFile.toString(),
          "--in-format",
          "binary",
          "--out",
          outputFile.toString(),
          "--out-format",
          "binary",
          "--key-template",
          "HMAC_SHA256_256BITTAG",
        });

    KeysetHandle handle =
        TinkProtoKeysetFormat.parseKeyset(
            Files.readAllBytes(outputFile), InsecureSecretKeyAccess.get());

    assertThat(handle.size()).isEqualTo(2);
    assertThat(handle.getAt(0).getKey().equalsKey(inputKeyset.getAt(0).getKey())).isTrue();
    assertThat(handle.getAt(0).isPrimary()).isTrue();
    assertThat(handle.getAt(1).getKey().getParameters())
        .isEqualTo(PredefinedMacParameters.HMAC_SHA256_256BITTAG);
    assertThat(handle.getAt(1).isPrimary()).isFalse();
  }

  @Test
  public void testAddKey_binaryEncrypted_works() throws Exception {
    Path path = Files.createTempDirectory(/* prefix= */ "");
    Path inputFile = Paths.get(path.toString(), "input");
    Path outputFile = Paths.get(path.toString(), "output");
    Path credentialFile = Paths.get(path.toString(), "credentials");
    TinkeyTestKmsClient.createCredentialFile(credentialFile);

    KeysetHandle masterKeyAeadKeyset =
        KeysetHandle.generateNew(PredefinedAeadParameters.AES128_GCM);
    Aead masterKeyAead = masterKeyAeadKeyset.getPrimitive(Aead.class);
    String masterKeyUri = TinkeyTestKmsClient.createKeyUri(masterKeyAeadKeyset);

    KeysetHandle inputKeyset =
        KeysetHandle.generateNew(PredefinedMacParameters.HMAC_SHA256_128BITTAG);
    byte[] serializedKeyset =
        TinkProtoKeysetFormat.serializeEncryptedKeyset(inputKeyset, masterKeyAead, new byte[] {});
    Files.write(inputFile, serializedKeyset);

    Tinkey.main(
        new String[] {
          "add-key",
          "--in",
          inputFile.toString(),
          "--in-format",
          "binary",
          "--out",
          outputFile.toString(),
          "--out-format",
          "binary",
          "--key-template",
          "HMAC_SHA256_256BITTAG",
          "--master-key-uri",
          masterKeyUri,
          "--credential",
          credentialFile.toString()
        });

    KeysetHandle handle =
        TinkProtoKeysetFormat.parseEncryptedKeyset(
            Files.readAllBytes(outputFile), masterKeyAead, new byte[] {});

    assertThat(handle.size()).isEqualTo(2);
    assertThat(handle.getAt(0).getKey().equalsKey(inputKeyset.getAt(0).getKey())).isTrue();
    assertThat(handle.getAt(0).isPrimary()).isTrue();
    assertThat(handle.getAt(1).getKey().getParameters())
        .isEqualTo(PredefinedMacParameters.HMAC_SHA256_256BITTAG);
    assertThat(handle.getAt(1).isPrimary()).isFalse();
  }

  @Test
  public void testAddKey_jsonEncrypted_works() throws Exception {
    Path path = Files.createTempDirectory(/* prefix= */ "");
    Path inputFile = Paths.get(path.toString(), "input");
    Path outputFile = Paths.get(path.toString(), "output");
    Path credentialFile = Paths.get(path.toString(), "credentials");
    TinkeyTestKmsClient.createCredentialFile(credentialFile);

    KeysetHandle masterKeyAeadKeyset =
        KeysetHandle.generateNew(PredefinedAeadParameters.AES128_GCM);
    Aead masterKeyAead = masterKeyAeadKeyset.getPrimitive(Aead.class);
    String masterKeyUri = TinkeyTestKmsClient.createKeyUri(masterKeyAeadKeyset);

    KeysetHandle inputKeyset =
        KeysetHandle.generateNew(PredefinedMacParameters.HMAC_SHA256_128BITTAG);
    String serializedKeyset =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            inputKeyset, masterKeyAead, new byte[] {});
    Files.write(inputFile, serializedKeyset.getBytes(UTF_8));

    Tinkey.main(
        new String[] {
          "add-key",
          "--in",
          inputFile.toString(),
          "--in-format",
          "json",
          "--out",
          outputFile.toString(),
          "--out-format",
          "json",
          "--key-template",
          "HMAC_SHA256_256BITTAG",
          "--master-key-uri",
          masterKeyUri,
          "--credential",
          credentialFile.toString()
        });

    KeysetHandle handle =
        TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
            new String(Files.readAllBytes(outputFile), UTF_8), masterKeyAead, new byte[] {});

    assertThat(handle.size()).isEqualTo(2);
    assertThat(handle.getAt(0).getKey().equalsKey(inputKeyset.getAt(0).getKey())).isTrue();
    assertThat(handle.getAt(0).isPrimary()).isTrue();
    assertThat(handle.getAt(1).getKey().getParameters())
        .isEqualTo(PredefinedMacParameters.HMAC_SHA256_256BITTAG);
    assertThat(handle.getAt(1).isPrimary()).isFalse();
  }

  @Test
  public void testAddKey_notValidKeyset_fails() throws Exception {
    Path path = Files.createTempDirectory(/* prefix= */ "");
    Path inputFile = Paths.get(path.toString(), "input");
    Path outputFile = Paths.get(path.toString(), "output");
    Files.write(inputFile, new byte[] {});

    assertThrows(
        Exception.class,
        () ->
            Tinkey.main(
                new String[] {
                  "add-key",
                  "--in",
                  inputFile.toString(),
                  "--in-format",
                  "binary",
                  "--out",
                  outputFile.toString(),
                  "--out-format",
                  "binary",
                  "--key-template",
                  "HMAC_SHA256_256BITTAG",
                }));
  }
}
