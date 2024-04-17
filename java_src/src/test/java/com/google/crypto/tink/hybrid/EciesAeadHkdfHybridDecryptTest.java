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

package com.google.crypto.tink.hybrid;

import static com.google.common.truth.Truth.assertThat;
import static com.google.crypto.tink.internal.TinkBugException.exceptionIsBug;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.HybridEncrypt;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.aead.AesCtrHmacAeadParameters;
import com.google.crypto.tink.aead.AesGcmParameters;
import com.google.crypto.tink.daead.AesSivParameters;
import com.google.crypto.tink.hybrid.internal.testing.EciesAeadHkdfTestUtil;
import com.google.crypto.tink.hybrid.internal.testing.HybridTestVector;
import com.google.crypto.tink.internal.EllipticCurvesUtil;
import com.google.crypto.tink.subtle.EciesAeadHkdfHybridDecrypt;
import com.google.crypto.tink.subtle.EciesAeadHkdfHybridEncrypt;
import com.google.crypto.tink.subtle.EllipticCurves;
import com.google.crypto.tink.subtle.EllipticCurves.CurveType;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.subtle.Random;
import com.google.crypto.tink.testing.TestUtil;
import com.google.crypto.tink.testing.TestUtil.BytesMutation;
import com.google.crypto.tink.util.Bytes;
import com.google.crypto.tink.util.SecretBigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Unit tests for {@link EciesAeadHkdfHybridDecrypt}. */
@RunWith(Theories.class)
public class EciesAeadHkdfHybridDecryptTest {
  @Before
  public void setUp() throws GeneralSecurityException {
    HybridConfig.register();
  }

  private static final AesCtrHmacAeadParameters AES128_CTR_HMAC_SHA256_RAW =
      exceptionIsBug(
          () ->
              AesCtrHmacAeadParameters.builder()
                  .setAesKeySizeBytes(16)
                  .setHmacKeySizeBytes(32)
                  .setTagSizeBytes(16)
                  .setIvSizeBytes(16)
                  .setHashType(AesCtrHmacAeadParameters.HashType.SHA256)
                  .setVariant(AesCtrHmacAeadParameters.Variant.NO_PREFIX)
                  .build());
  private static final AesGcmParameters AES128_GCM_RAW =
      exceptionIsBug(
          () ->
              AesGcmParameters.builder()
                  .setIvSizeBytes(12)
                  .setKeySizeBytes(16)
                  .setTagSizeBytes(16)
                  .setVariant(AesGcmParameters.Variant.NO_PREFIX)
                  .build());
  private static final AesSivParameters AES256_SIV_RAW =
      exceptionIsBug(
          () ->
              AesSivParameters.builder()
                  .setKeySizeBytes(64)
                  .setVariant(AesSivParameters.Variant.NO_PREFIX)
                  .build());

  private static final ECParameterSpec toParameterSpec(EciesParameters.CurveType curveType)
      throws GeneralSecurityException {
    if (curveType == EciesParameters.CurveType.NIST_P256) {
      return EllipticCurvesUtil.NIST_P256_PARAMS;
    }
    if (curveType == EciesParameters.CurveType.NIST_P384) {
      return EllipticCurvesUtil.NIST_P384_PARAMS;
    }
    if (curveType == EciesParameters.CurveType.NIST_P521) {
      return EllipticCurvesUtil.NIST_P521_PARAMS;
    }
    throw new GeneralSecurityException("Unsupported curve type: " + curveType);
  }

  private static void testEncryptDecrypt(EciesParameters.CurveType curveType, Parameters parameters)
      throws Exception {
    KeyPair recipientKey = EllipticCurves.generateKeyPair(toParameterSpec(curveType));
    ECPublicKey recipientPublicKey = (ECPublicKey) recipientKey.getPublic();
    ECPrivateKey recipientPrivateKey = (ECPrivateKey) recipientKey.getPrivate();
    byte[] salt = Random.randBytes(8);
    byte[] plaintext = Random.randBytes(4);
    byte[] context = Random.randBytes(4);
    EciesParameters eciesParameters =
        EciesParameters.builder()
            .setCurveType(curveType)
            .setHashType(EciesParameters.HashType.SHA256)
            .setNistCurvePointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
            .setDemParameters(parameters)
            .setSalt(Bytes.copyFrom(salt))
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .build();
    EciesPublicKey eciesPublicKey =
        EciesPublicKey.createForNistCurve(
            eciesParameters, recipientPublicKey.getW(), /* idRequirement= */ null);
    EciesPrivateKey eciesPrivateKey =
        EciesPrivateKey.createForNistCurve(
            eciesPublicKey,
            SecretBigInteger.fromBigInteger(
                recipientPrivateKey.getS(), InsecureSecretKeyAccess.get()));

    HybridEncrypt hybridEncrypt = EciesAeadHkdfHybridEncrypt.create(eciesPublicKey);
    HybridDecrypt hybridDecrypt = EciesAeadHkdfHybridDecrypt.create(eciesPrivateKey);
    byte[] ciphertext = hybridEncrypt.encrypt(plaintext, context);
    byte[] decrypted = hybridDecrypt.decrypt(ciphertext, context);
    assertArrayEquals(plaintext, decrypted);
  }

  @Test
  public void testEncryptDecryptP256CtrHmac() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P256, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP384CtrHmac() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P384, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP521CtrHmac() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P521, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP256Gcm() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P256, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP384Gcm() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P384, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP512Gcm() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P521, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP256AesSiv() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P256, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP384AesSiv() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P384, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP512AesSiv() throws Exception {
    testEncryptDecrypt(EciesParameters.CurveType.NIST_P521, AES256_SIV_RAW);
  }

  private static void testEncryptDecrypt_mutatedCiphertext_throws(
      EciesParameters.CurveType curveType, Parameters parameters) throws Exception {
    KeyPair recipientKey = EllipticCurves.generateKeyPair(toParameterSpec(curveType));
    ECPublicKey recipientPublicKey = (ECPublicKey) recipientKey.getPublic();
    ECPrivateKey recipientPrivateKey = (ECPrivateKey) recipientKey.getPrivate();
    byte[] salt = Random.randBytes(8);
    byte[] plaintext = Random.randBytes(4);
    byte[] context = Random.randBytes(4);
    EciesParameters eciesParameters =
        EciesParameters.builder()
            .setCurveType(curveType)
            .setHashType(EciesParameters.HashType.SHA256)
            .setNistCurvePointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
            .setDemParameters(parameters)
            .setSalt(Bytes.copyFrom(salt))
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .build();
    EciesPublicKey eciesPublicKey =
        EciesPublicKey.createForNistCurve(
            eciesParameters, recipientPublicKey.getW(), /* idRequirement= */ null);
    EciesPrivateKey eciesPrivateKey =
        EciesPrivateKey.createForNistCurve(
            eciesPublicKey,
            SecretBigInteger.fromBigInteger(
                recipientPrivateKey.getS(), InsecureSecretKeyAccess.get()));

    HybridEncrypt hybridEncrypt = EciesAeadHkdfHybridEncrypt.create(eciesPublicKey);
    HybridDecrypt hybridDecrypt = EciesAeadHkdfHybridDecrypt.create(eciesPrivateKey);

    byte[] ciphertext = hybridEncrypt.encrypt(plaintext, context);
    for (BytesMutation mutation : TestUtil.generateMutations(ciphertext)) {
      assertThrows(
          GeneralSecurityException.class, () -> hybridDecrypt.decrypt(mutation.value, context));
      // The test takes too long in TSan and on Android, so we stop after the first case.
      if (TestUtil.isTsan() || TestUtil.isAndroid()) {
        return;
      }
    }
  }

  @Test
  public void testEncryptDecryptP256CtrHmac_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P256, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP384CtrHmac_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P384, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP521CtrHmac_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P521, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP256Gcm_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P256, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP384Gcm_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P384, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP512Gcm_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P521, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP256AesSiv_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P256, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP384AesSiv_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P384, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP512AesSiv_mutatedCiphertext_throws() throws Exception {
    testEncryptDecrypt_mutatedCiphertext_throws(
        EciesParameters.CurveType.NIST_P521, AES256_SIV_RAW);
  }

  private static void testEncryptDecrypt_mutatedContext_throws(
      EciesParameters.CurveType curveType, Parameters parameters) throws Exception {
    KeyPair recipientKey = EllipticCurves.generateKeyPair(toParameterSpec(curveType));
    ECPublicKey recipientPublicKey = (ECPublicKey) recipientKey.getPublic();
    ECPrivateKey recipientPrivateKey = (ECPrivateKey) recipientKey.getPrivate();
    byte[] salt = Random.randBytes(8);
    byte[] plaintext = Random.randBytes(4);
    byte[] context = Random.randBytes(4);
    EciesParameters eciesParameters =
        EciesParameters.builder()
            .setCurveType(curveType)
            .setHashType(EciesParameters.HashType.SHA256)
            .setNistCurvePointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
            .setDemParameters(parameters)
            .setSalt(Bytes.copyFrom(salt))
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .build();
    EciesPublicKey eciesPublicKey =
        EciesPublicKey.createForNistCurve(
            eciesParameters, recipientPublicKey.getW(), /* idRequirement= */ null);
    EciesPrivateKey eciesPrivateKey =
        EciesPrivateKey.createForNistCurve(
            eciesPublicKey,
            SecretBigInteger.fromBigInteger(
                recipientPrivateKey.getS(), InsecureSecretKeyAccess.get()));

    HybridEncrypt hybridEncrypt = EciesAeadHkdfHybridEncrypt.create(eciesPublicKey);
    HybridDecrypt hybridDecrypt = EciesAeadHkdfHybridDecrypt.create(eciesPrivateKey);

    byte[] ciphertext = hybridEncrypt.encrypt(plaintext, context);
    for (BytesMutation mutation : TestUtil.generateMutations(context)) {
      // The test takes too long in TSan and on Android, so we stop after the first case.
      assertThrows(
          GeneralSecurityException.class, () -> hybridDecrypt.decrypt(ciphertext, mutation.value));
      if (TestUtil.isTsan() || TestUtil.isAndroid()) {
        return;
      }
    }
  }

  @Test
  public void testEncryptDecryptP256CtrHmac_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(
        EciesParameters.CurveType.NIST_P256, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP384CtrHmac_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(
        EciesParameters.CurveType.NIST_P384, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP521CtrHmac_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(EciesParameters.CurveType.NIST_P521, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP256Gcm_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(EciesParameters.CurveType.NIST_P256, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP384Gcm_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(EciesParameters.CurveType.NIST_P384, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP512Gcm_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(EciesParameters.CurveType.NIST_P521, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP256AesSiv_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(EciesParameters.CurveType.NIST_P256, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP384AesSiv_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(EciesParameters.CurveType.NIST_P384, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP512AesSiv_mutatedContext_throws() throws Exception {
    testEncryptDecrypt_mutatedContext_throws(EciesParameters.CurveType.NIST_P521, AES256_SIV_RAW);
  }

  private static void testEncryptDecrypt_mutatedSalt_throws(
      EciesParameters.CurveType curveType, Parameters parameters) throws Exception {
    KeyPair recipientKey = EllipticCurves.generateKeyPair(toParameterSpec(curveType));
    ECPublicKey recipientPublicKey = (ECPublicKey) recipientKey.getPublic();
    ECPrivateKey recipientPrivateKey = (ECPrivateKey) recipientKey.getPrivate();
    byte[] salt = Random.randBytes(8);
    byte[] plaintext = Random.randBytes(4);
    byte[] context = Random.randBytes(4);
    EciesParameters eciesParameters =
        EciesParameters.builder()
            .setCurveType(curveType)
            .setHashType(EciesParameters.HashType.SHA256)
            .setNistCurvePointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
            .setDemParameters(parameters)
            .setSalt(Bytes.copyFrom(salt))
            .setVariant(EciesParameters.Variant.NO_PREFIX)
            .build();
    EciesPublicKey eciesPublicKey =
        EciesPublicKey.createForNistCurve(
            eciesParameters, recipientPublicKey.getW(), /* idRequirement= */ null);

    HybridEncrypt hybridEncrypt = EciesAeadHkdfHybridEncrypt.create(eciesPublicKey);
    byte[] ciphertext = hybridEncrypt.encrypt(plaintext, context);

    for (int bytes = 0; bytes < salt.length; bytes++) {
      for (int bit = 0; bit < 8; bit++) {
        byte[] modifiedSalt = Arrays.copyOf(salt, salt.length);
        modifiedSalt[bytes] ^= (byte) (1 << bit);
        EciesParameters modifiedEciesParameters =
            EciesParameters.builder()
                .setCurveType(curveType)
                .setHashType(EciesParameters.HashType.SHA256)
                .setNistCurvePointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
                .setDemParameters(parameters)
                .setSalt(Bytes.copyFrom(modifiedSalt))
                .setVariant(EciesParameters.Variant.NO_PREFIX)
                .build();
        EciesPublicKey modifiedEciesPublicKey =
            EciesPublicKey.createForNistCurve(
                modifiedEciesParameters, recipientPublicKey.getW(), /* idRequirement= */ null);
        EciesPrivateKey modifiedEciesPrivateKey =
            EciesPrivateKey.createForNistCurve(
                modifiedEciesPublicKey,
                SecretBigInteger.fromBigInteger(
                    recipientPrivateKey.getS(), InsecureSecretKeyAccess.get()));

        HybridDecrypt hybridDecrypt = EciesAeadHkdfHybridDecrypt.create(modifiedEciesPrivateKey);
        assertThrows(
            GeneralSecurityException.class, () -> hybridDecrypt.decrypt(ciphertext, context));
        // The test takes too long in TSan and on Android, so we stop after the first case.
        if (TestUtil.isTsan() || TestUtil.isAndroid()) {
          return;
        }
      }
    }
  }

  @Test
  public void testEncryptDecryptP256CtrHmac_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(
        EciesParameters.CurveType.NIST_P256, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP384CtrHmac_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(
        EciesParameters.CurveType.NIST_P384, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP521CtrHmac_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(
        EciesParameters.CurveType.NIST_P521, AES128_CTR_HMAC_SHA256_RAW);
  }

  @Test
  public void testEncryptDecryptP256Gcm_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(EciesParameters.CurveType.NIST_P256, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP384Gcm_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(EciesParameters.CurveType.NIST_P384, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP512Gcm_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(EciesParameters.CurveType.NIST_P521, AES128_GCM_RAW);
  }

  @Test
  public void testEncryptDecryptP256AesSiv_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(EciesParameters.CurveType.NIST_P256, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP384AesSiv_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(EciesParameters.CurveType.NIST_P384, AES256_SIV_RAW);
  }

  @Test
  public void testEncryptDecryptP512AesSiv_mutatedSalt_throws() throws Exception {
    testEncryptDecrypt_mutatedSalt_throws(EciesParameters.CurveType.NIST_P521, AES256_SIV_RAW);
  }

  /** Test vector for hybrid decryption. */
  public static class HybridDecryptionTestVector {
    public byte[] key;
    public byte[] ciphertext;
    public byte[] context;
    public byte[] plaintext;

    public HybridDecryptionTestVector(
        String key, String ciphertext, String context, String plaintext) {
      this.key = Hex.decode(key);
      this.ciphertext = Hex.decode(ciphertext);
      this.context = context.getBytes(UTF_8);
      this.plaintext = plaintext.getBytes(UTF_8);
    }
  }

  // These are the same test vectors used to test the c++ implementation in
  // //third_party/tink/cc/hybrid/ecies_aead_hkdf_hybrid_decrypt_test.cc.
  private static final HybridDecryptionTestVector[] aesSivDemHybridTestVectors = {
    new HybridDecryptionTestVector(
        "32588172ed65830571bb83748f7fddd383323208a7825c80a71bef846333eb02",
        "0401b11f8c9bafe30ae13f8bd15528714e752631a4328bf146009068e99489c8e9fae1ec39e3fe9994723711417fcab2af4b3c9b60117d47d33d35175c87b483b8935a73312940d1fbf8da3944a89b5e8b",
        "some context info",
        ""),
    new HybridDecryptionTestVector(
        "32588172ed65830571bb83748f7fddd383323208a7825c80a71bef846333eb02",
        "040230023d1547b55af5a735a7f460722612126d7539d7cd0f677d308b29c6f52a964e66e7b0cb44cff1673df9e2c793f1477ca755807bfbeadcae1ab20b45ecb1501ca5e3f5b0626d3ca40aa5d010443d506e4df90b",
        "some context info",
        "hello"),
    new HybridDecryptionTestVector(
        "32588172ed65830571bb83748f7fddd383323208a7825c80a71bef846333eb02",
        "0441ddd246cea0825bd68bddff05cec54a4ee678da35b2f5cfbbb32e5350bdd817214bfb7b5ed5528131bde56916062cfbd8b9952d9e0907a6e87e1de54db5df3aaccddd328efcf7771ce061e647488f66b8c11a9fca171dcff813e90b44b2739573f9f23b60202491870c7ff8aaf0ae46838e48f17f8dc1ad55b67809699dd31eb6ca50dfa9beeee32d30bdc00a1eb1d8b0cbcedbe50b1e24619cc5e79042f25f49e2c2d5a35c79e833c0d68e31a93da4173aacd0428b367594ed4636763d16c23e4f8c115d44bddc83bcefcaea13587238ce8b7a5d5fad53beeb59aaa1d7483eb4bac93ed50ed4d3e9fd5af760283fd38080b58744b73212a36039179ce6f96ef1ecaa05b5186967d81c06b9cd91140dfbd54084ddcfd941527719848a2eecb84278f6a0fe9357a3964f87222fcd16a12a353e1f64fd45dc227a4a2112da6f61269f22f16b41e68eadf0b6b3a48c67b9e7e3ec1c66eecce50dda8ecbce99d3778299aa28741b7247fbc46a1b8a908dc23943c2dd17210a270bb12b096c2c6a00400a95c62894a15b9fc44e709d27348f2f2644a786cd9e96caf42ea9b949f76e85e6f7365e15fa2902e851222c025f6c208269d799fcfc4c0b37aba8979ed9e6ccf543c217ee0b6ad05f0e3ffb92943d308c801b25efedab5bf93a733bdae611132d774d4b9ee4fb5e88ae63014315ae9571039a8c8c7020e2b3a1bbd4235b65af94771c8417c87fd6cab423b82a557f60a99ae7402dba205e05136dd34f0026fce87899d4b9819cc2b2ba686512d62c41a1e3a667a705ea45404aafa489cd7f53f42455fff3f9b22f960d12a2587efd6ed0fa3e00dd4645face1b2f1268e6019be70999eab00f0aeff3cb0e77b7c4a1ab1fdf15d00c4eedd7b75e8cf5c90119346894089ee0299d58f1d7ebac9b592da2325a5a738ea2baecc1468670f5aec880bce32efecfb2a7c5ad3ae4096b0a07aa9bfe6cbaf53da6757377bb692e55ec8caf5f0af28dafdc42e1d6e5893140945a853f56652c575b99d64399aad2d042948575134c8fe638fb0b80ac3a0f08a60f3aa817fe0a24c1fffee6933bd72ea460e0b241d3f5d98b2321ee25d8c0302353fcfd41bce964d73ff670422864506cc56f3470362c90144586ccbfc8e5e6fefbb70429b0a517e4b1badb449cd11092790aba6e19b914899872f4fb481c8dc47a33422fc05072ac99c958e40dae53d96ebd87cfbde67a0f050203a89e487da5e03364951830e43771d36abfbe8f5a7da8e7aa891f36a68dbe9a3b0e3dfbd1afd6327a3ced4a5cd8a5b256fef46d200df4af2e2da4dbb786ea0404bb968b6d961e4fc76f89e70ad7c9e11d6aee6526b75b399811f73c053a29582ba9295ea4d5a8fffb5a8ccbac008d291dd60e2041371acfc4c432a0ae0fcd8fa25c9551123c95da64caa134edaee5893e19c3c76075bef419c09681a67f4ede6f28d747b53afd61ddc937d7de96a22c7db10ad8700cade888de5d6f450c15d796978ddb5e6a52e5044e90247c988686d992105c85f6d198e2de859330f973ded4d7e5d90de57051dbaf0db0febd4cf9d44da155e55293b0930f89c1d21cc227eba9615ca47cce41d16eaddb5bf5dc9bc8477df5cf21f460b83241e7d0fa3707f9d2b322b9aaa42747d0653168b095ca0a83f38426688f6f10143cbd1b84c08583b09ed6192c7366ecc23af528fc2e8c585560f9bd0fcc255b82fc70723a92506bb475ebc1f5ae34a902bf2aa75997ed90a54762c8e83720833b2fd607eee1beb347a75d3bd0f174ed450a72cce79f1be426de9d6f1a6feff052674af141b3cea89f8e749118392e9533c62ddad870e60d509fd7abfa0bc33c2774b29a0170089b30d82047d6e130c49f6965f9871d1928b7f13e3e40ad8e3dc85195f4b312f9f6d8e4158aca23a611f6c6c798983555139942536f6ac59bbd6cc88b9933f22e81429e835bfd4fec27c67520d64a0ad8fd7feb6a3fbe52dc56cbbf59644b0fad0c462ed02ffbf7258e4b94bdedefb187fbdb729a0d56a36e876ac76de766eed416f39ab4e8b1982b8d0a87cd33182ae81ecf1d1d5202cc3e82c5762646d15db5f13cde3e81c83715195f9af9f27e01e1829ce529fa0f715db1f5d227bb201c7c127ea8d0e9c21739c7e9c6a0d8d5a1aaea5216c549f3715f889e583555ac1bfd77339f3eff1bee75ee2fc45457f5c3ffe9401b8b67f5bb3f305f3269fe6153ba34de3fa90016c76811cd54b4b49b17b244b1a4f6edfa2eaf46e2819aded26005b4ed712e8b700ae7b6123fa2c179640ee523f864360d116ee243f13c66d2cd61d422709648d905ab17edf0d0075d2fed443889e15344069b69b2d3d8273f197f8468baf167074bf6dfdeea5871f0c0652ab2801f394ef6fbf841e8072c8bf65026d85d441ca61e78785a2e7ca1e743640fecd6dfad8b77adcbb8bcb8ce8532ad0cd8b3e51269c26ad037545273f756c1a5511925408a5045af469ca947f9a3f5457bcc325d05291a192abe75b4da7c97a61adc2fa247984edb5a03285f1c3b99f13f6a22f007029faffdd38b62f7bf909ce602e4e06ab1ec4543013d354d0dd86d8933a53c17ead02faf0cc740d7191fe475be2f7940c234f8c73420774a7213fd2a477847527172c02a54928de5fde5f15616760e6f7ff3c03a233aec880a939d9f1ca68be7f474fd13184fe8f6deb0c4ea01617ea207d5d765d067fddba58b94f3b59d5996e9f5434f483e2f0079c48050f3ba941b589294c41a0f350451d566fe58a9c9688cc3a75da314ff4b3473eeac58664c5922ae4efae850fe0f7f11dcc089bc0b4df9a64547a35b2559f4a4a3e7d3782d850997baa589534921becde8dc3f76380ae36bd9730956aae9f59b121d8ae4dbbc586c6b45ad9d5c17cf6821b746177bc9fcb727db3f4aa190688c48826421de5ebcd429e0d9b479e66e676e8f9a3b4bd92621f47357a7b1b27942121f5a6e0087e4192a5f8cf4da942cc9d86eac5e",
        "some context info",
        "08b8b2b733424243760fe426a4b54908632110a66c2f6591eabd3345e3e4eb98fa6e264bf09efe12ee50f8f54e9f77b1e355f6c50544e23fb1433ddf73be84d879de7c0046dc4996d9e773f4bc9efe5738829adb26c81b37c93a1b270b20329d658675fc6ea534e0810a4432826bf58c941efb65d57a338bbd2e26640f89ffbc1a858efcb8550ee3a5e1998bd177e93a7363c344fe6b199ee5d02e82d522c4feba15452f80288a821a579116ec6dad2b3b310da903401aa62100ab5d1a36553e06203b33890cc9b832f79ef80560ccb9a39ce767967ed628c6ad573cb116dbefefd75499da96bd68a8a97b928a8bbc103b6621fcde2beca1231d206be6cd9ec7aff6f6c94fcd7204ed3455c68c83f4a41da4af2b74ef5c53f1d8ac70bdcb7ed185ce81bd84359d44254d95629e9855a94a7c1958d1f8ada5d0532ed8a5aa3fb2d17ba70eb6248e594e1a2297acbbb39d502f1a8c6eb6f1ce22b3de1a1f40cc24554119a831a9aad6079cad88425de6bde1a9187ebb6092cf67bf2b13fd65f27088d78b7e883c8759d2c4f5c65adb7553878ad575f9fad878e80a0c9ba63bcbcc2732e69485bbc9c90bfbd62481d9089beccf80cfe2df16a2cf65bd92dd597b0707e0917af48bbb75fed413d238f5555a7a569d80c3414a8d0859dc65a46128bab27af87a71314f318c782b23ebfe808b82b0ce26401d2e22f04d83d1255dc51addd3b75a2b1ae0784504df543af8969be3ea7082ff7fc9888c144da2af58429ec96031dbcad3dad9af0dcbaaaf268cb8fcffead94f3c7ca495e056a9b47acdb751fb73e666c6c655ade8297297d07ad1ba5e43f1bca32301651339e22904cc8c42f58c30c04aafdb038dda0847dd988dcda6f3bfd15c4b4c4525004aa06eeff8ca61783aacec57fb3d1f92b0fe2fd1a85f6724517b65e614ad6808d6f6ee34dff7310fdc82aebfd904b01e1dc54b2927094b2db68d6f903b68401adebf5a7e08d78ff4ef5d63653a65040cf9bfd4aca7984a74d37145986780fc0b16ac451649de6188a7dbdf191f64b5fc5e2ab47b57f7f7276cd419c17a3ca8e1b939ae49e488acba6b965610b5480109c8b17b80e1b7b750dfc7598d5d5011fd2dcc5600a32ef5b52a1ecc820e308aa342721aac0943bf6686b64b2579376504ccc493d97e6aed3fb0f9cd71a43dd497f01f17c0e2cb3797aa2a2f256656168e6c496afc5fb93246f6b1116398a346f1a641f3b041e989f7914f90cc2c7fff357876e506b50d334ba77c225bc307ba537152f3f1610e4eafe595f6d9d90d11faa933a15ef1369546868a7f3a45a96768d40fd9d03412c091c6315cf4fde7cb68606937380db2eaaa707b4c4185c32eddcdd306705e4dc1ffc872eeee475a64dfac86aba41c0618983f8741c5ef68d3a101e8a3b8cac60c905c15fc910840b94c00a0b9d0")
  };

  @Test
  public void decryptWithTestVectors() throws Exception {
    for (HybridDecryptionTestVector vector : aesSivDemHybridTestVectors) {

      ECPrivateKey recipientPrivateKey =
          EllipticCurves.getEcPrivateKey(CurveType.NIST_P256, vector.key);
      EciesParameters eciesParameters =
          EciesParameters.builder()
              .setCurveType(EciesParameters.CurveType.NIST_P256)
              .setHashType(EciesParameters.HashType.SHA256)
              .setNistCurvePointFormat(EciesParameters.PointFormat.UNCOMPRESSED)
              .setVariant(EciesParameters.Variant.NO_PREFIX)
              .setDemParameters(AES256_SIV_RAW)
              .build();
      ECPoint publicPoint =
          EllipticCurvesUtil.multiplyByGenerator(
              recipientPrivateKey.getS(), EllipticCurvesUtil.NIST_P256_PARAMS);

      EciesPublicKey eciesPublicKey =
          EciesPublicKey.createForNistCurve(
              eciesParameters, publicPoint, /* idRequirement= */ null);
      EciesPrivateKey privateKey =
          EciesPrivateKey.createForNistCurve(
              eciesPublicKey,
              SecretBigInteger.fromBigInteger(
                  recipientPrivateKey.getS(), InsecureSecretKeyAccess.get()));

      HybridDecrypt hybridDecrypt = EciesAeadHkdfHybridDecrypt.create(privateKey);

      byte[] decrypted = hybridDecrypt.decrypt(vector.ciphertext, vector.context);
      assertArrayEquals(vector.plaintext, decrypted);
    }
  }

  @DataPoints("testVectors")
  public static final HybridTestVector[] HYBRID_TEST_VECTORS =
      EciesAeadHkdfTestUtil.createEciesTestVectors();

  @Theory
  public void test_decryptCiphertext_works(@FromDataPoints("testVectors") HybridTestVector v)
      throws Exception {
    HybridDecrypt hybridDecrypt =
        EciesAeadHkdfHybridDecrypt.create((EciesPrivateKey) v.getPrivateKey());
    byte[] plaintext = hybridDecrypt.decrypt(v.getCiphertext(), v.getContextInfo());
    assertThat(Hex.encode(plaintext)).isEqualTo(Hex.encode(v.getPlaintext()));
  }

  @Theory
  public void test_decryptWrongContextInfo_throws(@FromDataPoints("testVectors") HybridTestVector v)
      throws Exception {
    HybridDecrypt hybridDecrypt =
        EciesAeadHkdfHybridDecrypt.create((EciesPrivateKey) v.getPrivateKey());
    byte[] contextInfo = v.getContextInfo();
    if (contextInfo.length > 0) {
      contextInfo[0] ^= 1;
    } else {
      contextInfo = new byte[] {1};
    }
    // local variables referenced from a lambda expression must be final or effectively final
    final byte[] contextInfoCopy = Arrays.copyOf(contextInfo, contextInfo.length);
    assertThrows(
        GeneralSecurityException.class,
        () -> hybridDecrypt.decrypt(v.getCiphertext(), contextInfoCopy));
  }

  @Theory
  public void test_encryptThenDecryptMessage_works(
      @FromDataPoints("testVectors") HybridTestVector v) throws Exception {
    EciesPrivateKey eciesPrivateKey = (EciesPrivateKey) v.getPrivateKey();
    HybridDecrypt hybridDecrypt = EciesAeadHkdfHybridDecrypt.create(eciesPrivateKey);
    HybridEncrypt hybridEncrypt = EciesAeadHkdfHybridEncrypt.create(eciesPrivateKey.getPublicKey());
    byte[] ciphertext = hybridEncrypt.encrypt(v.getPlaintext(), v.getContextInfo());
    byte[] plaintext = hybridDecrypt.decrypt(ciphertext, v.getContextInfo());
    assertThat(Hex.encode(plaintext)).isEqualTo(Hex.encode(v.getPlaintext()));
  }
}
