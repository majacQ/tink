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

import static com.google.crypto.tink.internal.Util.isPrefix;

import com.google.crypto.tink.AccessesPartialKey;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.hybrid.EciesPrivateKey;
import com.google.crypto.tink.hybrid.subtle.AeadOrDaead;
import com.google.crypto.tink.internal.BigIntegerEncoding;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.EllipticCurve;
import java.util.Arrays;

/**
 * ECIES encryption with HKDF-KEM (key encapsulation mechanism) and AEAD-DEM (data encapsulation
 * mechanism).
 *
 * @since 1.0.0
 */
public final class EciesAeadHkdfHybridDecrypt implements HybridDecrypt {
  private static final byte[] EMPTY_AAD = new byte[0];
  private final ECPrivateKey recipientPrivateKey;
  private final EciesHkdfRecipientKem recipientKem;
  private final String hkdfHmacAlgo;
  private final byte[] hkdfSalt;
  private final EllipticCurves.PointFormatType ecPointFormat;
  private final EciesAeadHkdfDemHelper demHelper;
  private final byte[] outputPrefix;

  private EciesAeadHkdfHybridDecrypt(
      final ECPrivateKey recipientPrivateKey,
      final byte[] hkdfSalt,
      String hkdfHmacAlgo,
      EllipticCurves.PointFormatType ecPointFormat,
      EciesAeadHkdfDemHelper demHelper,
      byte[] outputPrefix)
      throws GeneralSecurityException {
    this.recipientPrivateKey = recipientPrivateKey;
    this.recipientKem = new EciesHkdfRecipientKem(recipientPrivateKey);
    this.hkdfSalt = hkdfSalt;
    this.hkdfHmacAlgo = hkdfHmacAlgo;
    this.ecPointFormat = ecPointFormat;
    this.demHelper = demHelper;
    this.outputPrefix = outputPrefix;
  }

  @AccessesPartialKey
  public static HybridDecrypt create(EciesPrivateKey key) throws GeneralSecurityException {
    EllipticCurves.CurveType curveType =
        EciesAeadHkdfHybridEncrypt.CURVE_TYPE_CONVERTER.toProtoEnum(
            key.getParameters().getCurveType());
    ECPrivateKey recipientPrivateKey =
        EllipticCurves.getEcPrivateKey(
            curveType,
            BigIntegerEncoding.toBigEndianBytes(
                key.getNistPrivateKeyValue().getBigInteger(InsecureSecretKeyAccess.get())));
    byte[] hkdfSalt = new byte[0];
    if (key.getParameters().getSalt() != null) {
      hkdfSalt = key.getParameters().getSalt().toByteArray();
    }
    return new EciesAeadHkdfHybridDecrypt(
        recipientPrivateKey,
        hkdfSalt,
        EciesAeadHkdfHybridEncrypt.toHmacAlgo(key.getParameters().getHashType()),
        EciesAeadHkdfHybridEncrypt.POINT_FORMAT_TYPE_CONVERTER.toProtoEnum(
            key.getParameters().getNistCurvePointFormat()),
        EciesAeadHkdfHybridEncrypt.createHelper(key.getParameters().getDemParameters()),
        key.getOutputPrefix().toByteArray());
  }

  private byte[] decryptNoPrefix(final byte[] ciphertext, final byte[] contextInfo)
      throws GeneralSecurityException {
    EllipticCurve curve = recipientPrivateKey.getParams().getCurve();
    int headerSize = EllipticCurves.encodingSizeInBytes(curve, ecPointFormat);
    if (ciphertext.length < headerSize) {
      throw new GeneralSecurityException("ciphertext too short");
    }
    byte[] kemBytes = Arrays.copyOfRange(ciphertext, 0, headerSize);
    byte[] symmetricKey =
        recipientKem.generateKey(
            kemBytes,
            hkdfHmacAlgo,
            hkdfSalt,
            contextInfo,
            demHelper.getSymmetricKeySizeInBytes(),
            ecPointFormat);
    AeadOrDaead aead = demHelper.getAeadOrDaead(symmetricKey);
    return aead.decrypt(Arrays.copyOfRange(ciphertext, headerSize, ciphertext.length), EMPTY_AAD);
  }

  @Override
  public byte[] decrypt(final byte[] ciphertext, final byte[] contextInfo)
      throws GeneralSecurityException {
    if (outputPrefix.length == 0) {
      return decryptNoPrefix(ciphertext, contextInfo);
    }
    if (!isPrefix(outputPrefix, ciphertext)) {
      throw new GeneralSecurityException("Invalid ciphertext (output prefix mismatch)");
    }
    byte[] ciphertextNoPrefix =
        Arrays.copyOfRange(ciphertext, outputPrefix.length, ciphertext.length);
    return decryptNoPrefix(ciphertextNoPrefix, contextInfo);
  }
}
