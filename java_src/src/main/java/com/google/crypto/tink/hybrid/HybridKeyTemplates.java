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

package com.google.crypto.tink.hybrid;

import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.google.crypto.tink.proto.EcPointFormat;
import com.google.crypto.tink.proto.EciesAeadDemParams;
import com.google.crypto.tink.proto.EciesAeadHkdfKeyFormat;
import com.google.crypto.tink.proto.EciesAeadHkdfParams;
import com.google.crypto.tink.proto.EciesHkdfKemParams;
import com.google.crypto.tink.proto.EllipticCurveType;
import com.google.crypto.tink.proto.HashType;
import com.google.crypto.tink.proto.KeyTemplate;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.protobuf.ByteString;

/**
 * Pre-generated {@link KeyTemplate} for {@link HybridDecrypt} and {@link HybridEncrypt} primitives.
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
 * {@link KeysetHandle#generateNew}. To generate a new keyset that contains a single {@link
 * com.google.crypto.tink.proto.EciesAeadHkdfPrivateKey}, one can do:
 *
 * <pre>{@code
 * HybridConfig.register();
 * KeysetHandle handle = KeysetHandle.generateNew(
 *     HybridKeyTemplates.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM);
 * HybridDecrypt decrypter = handle.getPrimitive(HybridDecrypt.class);
 * HybridEncrypt encrypter = handle.getPublicKeysetHandle().getPrimitive(HybridDecrypt.class);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class HybridKeyTemplates {
  private static final byte[] EMPTY_SALT = new byte[0];
  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.EciesAeadHkdfPrivateKey} with the following parameters:
   *
   * <ul>
   *   <li>KEM: ECDH over NIST P-256
   *   <li>DEM: AES128-GCM
   *   <li>KDF: HKDF-HMAC-SHA256 with an empty salt
   * </ul>
   *
   * <p>Unlike other key templates that use AES-GCM, the instances of {@link HybridDecrypt}
   * generated by this key template has no limitation on Android KitKat (API level 19). They might
   * not work in older versions though.
   */
  public static final KeyTemplate ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM =
      createEciesAeadHkdfKeyTemplate(
          EllipticCurveType.NIST_P256,
          HashType.SHA256,
          EcPointFormat.UNCOMPRESSED,
          AeadKeyTemplates.AES128_GCM,
          OutputPrefixType.TINK,
          EMPTY_SALT);

  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.EciesAeadHkdfPrivateKey} with the following parameters:
   *
   * <ul>
   *   <li>KEM: ECDH over NIST P-256
   *   <li>DEM: AES128-GCM
   *   <li>KDF: HKDF-HMAC-SHA256 with an empty salt
   *   <li>EC Point Format: Compressed
   *   <li>OutputPrefixType: RAW
   * </ul>
   *
   * <p>Unlike other key templates that use AES-GCM, the instances of {@link HybridDecrypt}
   * generated by this key template has no limitation on Android KitKat (API level 19). They might
   * not work in older versions though.
   */
  public static final KeyTemplate ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM_COMPRESSED_WITHOUT_PREFIX =
      createEciesAeadHkdfKeyTemplate(
          EllipticCurveType.NIST_P256,
          HashType.SHA256,
          EcPointFormat.COMPRESSED,
          AeadKeyTemplates.AES128_GCM,
          OutputPrefixType.RAW,
          EMPTY_SALT);

  /**
   * A {@link KeyTemplate} that generates new instances of {@link
   * com.google.crypto.tink.proto.EciesAeadHkdfPrivateKey} with the following parameters:
   *
   * <ul>
   *   <li>KEM: ECDH over NIST P-256
   *   <li>DEM: AES128-CTR-HMAC-SHA256 with the following parameters
   *       <ul>
   *         <li>AES key size: 16 bytes
   *         <li>AES CTR IV size: 16 bytes
   *         <li>HMAC key size: 32 bytes
   *         <li>HMAC tag size: 16 bytes
   *       </ul>
   *   <li>KDF: HKDF-HMAC-SHA256 with an empty salt
   * </ul>
   */
  public static final KeyTemplate ECIES_P256_HKDF_HMAC_SHA256_AES128_CTR_HMAC_SHA256 =
      createEciesAeadHkdfKeyTemplate(
          EllipticCurveType.NIST_P256,
          HashType.SHA256,
          EcPointFormat.UNCOMPRESSED,
          AeadKeyTemplates.AES128_CTR_HMAC_SHA256,
          OutputPrefixType.TINK,
          EMPTY_SALT);

  /**
   * @return a {@link KeyTemplate} containing a {@link EciesAeadHkdfKeyFormat}.
   * @deprecated Use EciesParameters instead.
   */
  @Deprecated
  public static KeyTemplate createEciesAeadHkdfKeyTemplate(
      EllipticCurveType curve,
      HashType hashType,
      EcPointFormat ecPointFormat,
      KeyTemplate demKeyTemplate,
      OutputPrefixType outputPrefixType,
      byte[] salt) {
    EciesAeadHkdfKeyFormat format = EciesAeadHkdfKeyFormat.newBuilder()
        .setParams(
            createEciesAeadHkdfParams(curve, hashType, ecPointFormat, demKeyTemplate, salt))
        .build();
    return KeyTemplate.newBuilder()
        .setTypeUrl(EciesAeadHkdfPrivateKeyManager.getKeyType())
        .setOutputPrefixType(outputPrefixType)
        .setValue(format.toByteString())
        .build();
  }

  /**
   * @return a {@link EciesAeadHkdfParams} with the specified parameters.
   * @deprecated Use EciesParameters instead.
   */
  @Deprecated
  public static EciesAeadHkdfParams createEciesAeadHkdfParams(
      EllipticCurveType curve,
      HashType hashType,
      EcPointFormat ecPointFormat,
      KeyTemplate demKeyTemplate,
      byte[] salt) {
    EciesHkdfKemParams kemParams = EciesHkdfKemParams.newBuilder()
        .setCurveType(curve)
        .setHkdfHashType(hashType)
        .setHkdfSalt(ByteString.copyFrom(salt))
        .build();
    EciesAeadDemParams demParams = EciesAeadDemParams.newBuilder()
        .setAeadDem(demKeyTemplate)
        .build();
    return EciesAeadHkdfParams.newBuilder()
        .setKemParams(kemParams)
        .setDemParams(demParams)
        .setEcPointFormat(ecPointFormat)
        .build();
  }

  private HybridKeyTemplates() {}
}
