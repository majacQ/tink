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

import com.google.crypto.tink.CryptoFormat;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.PrimitiveWrapper;
import com.google.crypto.tink.hybrid.internal.LegacyFullHybridDecrypt;
import com.google.crypto.tink.internal.LegacyProtoKey;
import com.google.crypto.tink.internal.MonitoringUtil;
import com.google.crypto.tink.internal.MutableMonitoringRegistry;
import com.google.crypto.tink.internal.MutablePrimitiveRegistry;
import com.google.crypto.tink.internal.PrimitiveConstructor;
import com.google.crypto.tink.internal.PrimitiveRegistry;
import com.google.crypto.tink.internal.PrimitiveSet;
import com.google.crypto.tink.monitoring.MonitoringClient;
import com.google.crypto.tink.monitoring.MonitoringKeysetInfo;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 * The implementation of {@code PrimitiveWrapper<HybridDecrypt>}.
 *
 * <p>The returned primitive works with a keyset (rather than a single key). To decrypt, the
 * primitive uses the prefix of the ciphertext to efficiently select the right key in the set. If
 * the keys associated with the prefix do not work, the primitive tries all keys with {@link
 * com.google.crypto.tink.proto.OutputPrefixType#RAW}.
 */
public class HybridDecryptWrapper implements PrimitiveWrapper<HybridDecrypt, HybridDecrypt> {

  private static final HybridDecryptWrapper WRAPPER = new HybridDecryptWrapper();
  private static final PrimitiveConstructor<LegacyProtoKey, HybridDecrypt>
      LEGACY_PRIMITIVE_CONSTRUCTOR =
          PrimitiveConstructor.create(
              LegacyFullHybridDecrypt::create, LegacyProtoKey.class, HybridDecrypt.class);

  private static class WrappedHybridDecrypt implements HybridDecrypt {
    private final PrimitiveSet<HybridDecrypt> primitives;

    private final MonitoringClient.Logger decLogger;

    public WrappedHybridDecrypt(final PrimitiveSet<HybridDecrypt> primitives) {
      this.primitives = primitives;
      if (primitives.hasAnnotations()) {
        MonitoringClient client = MutableMonitoringRegistry.globalInstance().getMonitoringClient();
        MonitoringKeysetInfo keysetInfo = MonitoringUtil.getMonitoringKeysetInfo(primitives);
        this.decLogger = client.createLogger(keysetInfo, "hybrid_decrypt", "decrypt");
      } else {
        this.decLogger = MonitoringUtil.DO_NOTHING_LOGGER;
      }
    }

    @Override
    public byte[] decrypt(final byte[] ciphertext, final byte[] contextInfo)
        throws GeneralSecurityException {
      if (ciphertext.length > CryptoFormat.NON_RAW_PREFIX_SIZE) {
        byte[] prefix = Arrays.copyOfRange(ciphertext, 0, CryptoFormat.NON_RAW_PREFIX_SIZE);
        List<PrimitiveSet.Entry<HybridDecrypt>> entries = primitives.getPrimitive(prefix);
        for (PrimitiveSet.Entry<HybridDecrypt> entry : entries) {
          try {
            byte[] output = entry.getFullPrimitive().decrypt(ciphertext, contextInfo);
            decLogger.log(entry.getKeyId(), ciphertext.length);
            return output;
          } catch (GeneralSecurityException e) {
            continue;
          }
        }
      }
      // Let's try all RAW keys.
      List<PrimitiveSet.Entry<HybridDecrypt>> entries = primitives.getRawPrimitives();
      for (PrimitiveSet.Entry<HybridDecrypt> entry : entries) {
        try {
          byte[] output = entry.getFullPrimitive().decrypt(ciphertext, contextInfo);
          decLogger.log(entry.getKeyId(), ciphertext.length);
          return output;
        } catch (GeneralSecurityException e) {
          continue;
        }
      }
      // nothing works.
      decLogger.logFailure();
      throw new GeneralSecurityException("decryption failed");
    }
  }

  HybridDecryptWrapper() {}

  @Override
  public HybridDecrypt wrap(final PrimitiveSet<HybridDecrypt> primitives) {
    return new WrappedHybridDecrypt(primitives);
  }

  @Override
  public Class<HybridDecrypt> getPrimitiveClass() {
    return HybridDecrypt.class;
  }

  @Override
  public Class<HybridDecrypt> getInputPrimitiveClass() {
    return HybridDecrypt.class;
  }

  /**
   * Register the wrapper within the registry.
   *
   * <p>This is required for calls to {@link Keyset.getPrimitive} with a {@link HybridDecrypt}
   * argument.
   */
  public static void register() throws GeneralSecurityException {
    MutablePrimitiveRegistry.globalInstance().registerPrimitiveWrapper(WRAPPER);
    MutablePrimitiveRegistry.globalInstance()
        .registerPrimitiveConstructor(LEGACY_PRIMITIVE_CONSTRUCTOR);
  }

  /**
   * registerToInternalPrimitiveRegistry is a non-public method (it takes an argument of an
   * internal-only type) registering an instance of {@code HybridDecryptWrapper} to the provided
   * {@code PrimitiveRegistry.Builder}.
   */
  public static void registerToInternalPrimitiveRegistry(
      PrimitiveRegistry.Builder primitiveRegistryBuilder) throws GeneralSecurityException {
    primitiveRegistryBuilder.registerPrimitiveWrapper(WRAPPER);
  }
}
