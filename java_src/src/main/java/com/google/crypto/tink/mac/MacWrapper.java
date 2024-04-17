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

import com.google.crypto.tink.CryptoFormat;
import com.google.crypto.tink.Mac;
import com.google.crypto.tink.PrimitiveWrapper;
import com.google.crypto.tink.internal.LegacyProtoKey;
import com.google.crypto.tink.internal.MonitoringUtil;
import com.google.crypto.tink.internal.MutableMonitoringRegistry;
import com.google.crypto.tink.internal.MutablePrimitiveRegistry;
import com.google.crypto.tink.internal.PrimitiveConstructor;
import com.google.crypto.tink.internal.PrimitiveRegistry;
import com.google.crypto.tink.internal.PrimitiveSet;
import com.google.crypto.tink.mac.internal.LegacyFullMac;
import com.google.crypto.tink.monitoring.MonitoringClient;
import com.google.crypto.tink.monitoring.MonitoringKeysetInfo;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

/**
 * MacWrapper is the implementation of PrimitiveWrapper for the Mac primitive.
 *
 * <p>The returned primitive works with a keyset (rather than a single key). To compute a MAC tag,
 * it uses the primary key in the keyset, and prepends to the tag a certain prefix associated with
 * the primary key. To verify a tag, the primitive uses the prefix of the tag to efficiently select
 * the right key in the set. If the keys associated with the prefix do not validate the tag, the
 * primitive tries all keys with {@link com.google.crypto.tink.proto.OutputPrefixType#RAW}.
 */
public class MacWrapper implements PrimitiveWrapper<Mac, Mac> {

  private static final MacWrapper WRAPPER = new MacWrapper();
  private static final PrimitiveConstructor<LegacyProtoKey, Mac>
      LEGACY_FULL_MAC_PRIMITIVE_CONSTRUCTOR =
          PrimitiveConstructor.create(LegacyFullMac::create, LegacyProtoKey.class, Mac.class);

  private static class WrappedMac implements Mac {
    private final PrimitiveSet<Mac> primitives;
    private final MonitoringClient.Logger computeLogger;
    private final MonitoringClient.Logger verifyLogger;

    private WrappedMac(PrimitiveSet<Mac> primitives) {
      this.primitives = primitives;
      if (primitives.hasAnnotations()) {
        MonitoringClient client = MutableMonitoringRegistry.globalInstance().getMonitoringClient();
        MonitoringKeysetInfo keysetInfo = MonitoringUtil.getMonitoringKeysetInfo(primitives);
        computeLogger = client.createLogger(keysetInfo, "mac", "compute");
        verifyLogger = client.createLogger(keysetInfo, "mac", "verify");
      } else {
        computeLogger = MonitoringUtil.DO_NOTHING_LOGGER;
        verifyLogger = MonitoringUtil.DO_NOTHING_LOGGER;
      }
    }

    @Override
    public byte[] computeMac(final byte[] data) throws GeneralSecurityException {
      try {
        byte[] output = primitives.getPrimary().getFullPrimitive().computeMac(data);
        computeLogger.log(primitives.getPrimary().getKeyId(), data.length);
        return output;
      } catch (GeneralSecurityException e) {
        computeLogger.logFailure();
        throw e;
      }
    }

    @Override
    public void verifyMac(final byte[] mac, final byte[] data) throws GeneralSecurityException {
      if (mac.length <= CryptoFormat.NON_RAW_PREFIX_SIZE) {
        // This also rejects raw MAC with size of 4 bytes or fewer. Those MACs are
        // clearly insecure, thus should be discouraged.
        verifyLogger.logFailure();
        throw new GeneralSecurityException("tag too short");
      }
      byte[] prefix = Arrays.copyOf(mac, CryptoFormat.NON_RAW_PREFIX_SIZE);
      List<PrimitiveSet.Entry<Mac>> entries = primitives.getPrimitive(prefix);
      for (PrimitiveSet.Entry<Mac> entry : entries) {
        try {
          entry.getFullPrimitive().verifyMac(mac, data);
          verifyLogger.log(entry.getKeyId(), data.length);
          // If there is no exception, the MAC is valid and we can return.
          return;
        } catch (GeneralSecurityException e) {
          // Ignored as we want to continue verification with the remaining keys.
        }
      }

      // None "non-raw" key matched, so let's try the raw keys (if any exist).
      entries = primitives.getRawPrimitives();
      for (PrimitiveSet.Entry<Mac> entry : entries) {
        try {
          entry.getFullPrimitive().verifyMac(mac, data);
          verifyLogger.log(entry.getKeyId(), data.length);
          // If there is no exception, the MAC is valid and we can return.
          return;
        } catch (GeneralSecurityException ignored) {
          // Ignored as we want to continue verification with other raw keys.
        }
      }
      // nothing works.
      verifyLogger.logFailure();
      throw new GeneralSecurityException("invalid MAC");
    }
  }

  MacWrapper() {}

  @Override
  public Mac wrap(final PrimitiveSet<Mac> primitives) throws GeneralSecurityException {
    return new WrappedMac(primitives);
  }

  @Override
  public Class<Mac> getPrimitiveClass() {
    return Mac.class;
  }

  @Override
  public Class<Mac> getInputPrimitiveClass() {
    return Mac.class;
  }

  static void register() throws GeneralSecurityException {
    MutablePrimitiveRegistry.globalInstance().registerPrimitiveWrapper(WRAPPER);
    MutablePrimitiveRegistry.globalInstance()
        .registerPrimitiveConstructor(LEGACY_FULL_MAC_PRIMITIVE_CONSTRUCTOR);
  }

  /**
   * registerToInternalPrimitiveRegistry is a non-public method (it takes an argument of an
   * internal-only type) registering an instance of {@code MacWrapper} to the provided {@code
   * PrimitiveRegistry.Builder}.
   */
  public static void registerToInternalPrimitiveRegistry(
      PrimitiveRegistry.Builder primitiveRegistryBuilder) throws GeneralSecurityException {
    primitiveRegistryBuilder.registerPrimitiveWrapper(WRAPPER);
  }
}
