// Copyright 2017 Google LLC
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

package com.google.crypto.tink.mac;

import com.google.crypto.tink.proto.AesCmacKeyFormat;
import com.google.crypto.tink.proto.AesCmacParams;
import com.google.crypto.tink.proto.HashType;
import com.google.crypto.tink.proto.HmacKeyFormat;
import com.google.crypto.tink.proto.HmacParams;
import com.google.crypto.tink.proto.KeyTemplate;
import com.google.crypto.tink.proto.OutputPrefixType;

/**
 * Pre-generated {@link KeyTemplate} for {@link com.google.crypto.tink.Mac}.
 *
 * <p>We recommend to avoid this class in order to keep dependencies small.
 *
 * <ul>
 *   <li>Using this class adds a dependency on protobuf. We hope that eventually it is possible to
 *       use Tink without a dependency on protobuf.
 *   <li>Using this class adds a dependency on classes for all involved key types.
 * </ul>
 *
 * These dependencies all come from static class member variables, which are initialized when the
 * class is loaded. This implies that static analysis and code minimization tools (such as proguard)
 * cannot remove the usages either.
 *
 * <p>Instead, we recommend to use {@code KeysetHandle.generateEntryFromParametersName} or {@code
 * KeysetHandle.generateEntryFromParameters}.
 *
 * <p>One can use these templates to generate new {@link com.google.crypto.tink.proto.Keyset} with
 * {@link com.google.crypto.tink.KeysetHandle}. To generate a new keyset that contains a single
 * {@link com.google.crypto.tink.proto.HmacKey}, one can do:
 *
 * <pre>{@code
 * MacConfig.register();
 * KeysetHandle handle = KeysetHandle.generateNew(MacKeyTemplates.HMAC_SHA256_128BITTAG);
 * Mac mac = handle.getPrimitive(Mac.class);
 * }</pre>
 *
 * @since 1.0.0
 * @deprecated Use PredefinedMacParameters instead.
 */
@Deprecated
public final class MacKeyTemplates {
  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.HmacKey} with the following parameters:
   *
   * <ul>
   *   <li>Key size: 32 bytes
   *   <li>Tag size: 16 bytes
   *   <li>Hash function: SHA256
   *   <li>OutputPrefixType: TINK
   * </ul>
   */
  public static final KeyTemplate HMAC_SHA256_128BITTAG =
      createHmacKeyTemplate(32, 16, HashType.SHA256);

  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.HmacKey} with the following parameters:
   *
   * <ul>
   *   <li>Key size: 32 bytes
   *   <li>Tag size: 32 bytes
   *   <li>Hash function: SHA256
   *   <li>OutputPrefixType: TINK
   * </ul>
   */
  public static final KeyTemplate HMAC_SHA256_256BITTAG =
      createHmacKeyTemplate(32, 32, HashType.SHA256);

  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.HmacKey} with the following parameters:
   *
   * <ul>
   *   <li>Key size: 64 bytes
   *   <li>Tag size: 32 bytes
   *   <li>Hash function: SHA512
   *   <li>OutputPrefixType: TINK
   * </ul>
   */
  public static final KeyTemplate HMAC_SHA512_256BITTAG =
      createHmacKeyTemplate(64, 32, HashType.SHA512);

  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.HmacKey} with the following parameters:
   *
   * <ul>
   *   <li>Key size: 64 bytes
   *   <li>Tag size: 64 bytes
   *   <li>Hash function: SHA512
   *   <li>OutputPrefixType: TINK
   * </ul>
   */
  public static final KeyTemplate HMAC_SHA512_512BITTAG =
      createHmacKeyTemplate(64, 64, HashType.SHA512);

  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.CmacKey} with the following parameters:
   *
   * <ul>
   *   <li>Key size: 32 bytes
   *   <li>Tag size: 16 bytes
   *   <li>OutputPrefixType: TINK
   * </ul>
   */
  public static final KeyTemplate AES_CMAC =
      KeyTemplate.newBuilder()
          .setValue(
              AesCmacKeyFormat.newBuilder()
                  .setKeySize(32)
                  .setParams(AesCmacParams.newBuilder().setTagSize(16).build())
                  .build()
                  .toByteString())
          .setTypeUrl("type.googleapis.com/google.crypto.tink.AesCmacKey")
          .setOutputPrefixType(OutputPrefixType.TINK)
          .build();

  /**
   * @return a {@link KeyTemplate} containing a {@link HmacKeyFormat} with some specified
   *     parameters.
   */
  public static KeyTemplate createHmacKeyTemplate(int keySize, int tagSize, HashType hashType) {
    HmacParams params = HmacParams.newBuilder()
        .setHash(hashType)
        .setTagSize(tagSize)
        .build();
    HmacKeyFormat format = HmacKeyFormat.newBuilder()
        .setParams(params)
        .setKeySize(keySize)
        .build();
    return KeyTemplate.newBuilder()
        .setValue(format.toByteString())
        .setTypeUrl(HmacKeyManager.getKeyType())
        .setOutputPrefixType(OutputPrefixType.TINK)
        .build();
  }

  private MacKeyTemplates() {}
}
