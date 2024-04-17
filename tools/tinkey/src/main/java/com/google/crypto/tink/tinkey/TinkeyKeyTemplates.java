// Copyright 2021 Google LLC
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

import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.keyderivation.PrfBasedKeyDerivationParameters;
import com.google.crypto.tink.prf.PrfParameters;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains key templates that should be part of Tinkey but are not defined in the corresponding
 * {@link KeyTypeManager#keyFormats()}.
 *
 * <p>For example, key template {@code HKDF_SHA256_DERIVES_AES128_GCM} requires key template {@code
 * AES128_GCM} to exist in the {@link Registry}, which requires {@link AeadConfig#register()} to
 * have been called. Since {@link KeyDerivationConfig} does not automatically call {@link
 * AeadConfig#register()}, {@code HKDF_SHA256_DERIVES_AES128_GCM} cannot be defined in {@link
 * PrfBasedDeriverKeyManager#keyFormats()}.
 */
final class TinkeyKeyTemplates {
  private static KeyTemplate createPrfBasedKeyTemplate(
      KeyTemplate prfTemplate, KeyTemplate derivedKeyTemplate) throws GeneralSecurityException {
    return KeyTemplate.createFrom(
        PrfBasedKeyDerivationParameters.builder()
            .setPrfParameters((PrfParameters) prfTemplate.toParameters())
            .setDerivedKeyParameters(derivedKeyTemplate.toParameters())
            .build());
  }

  public static final Map<String, KeyTemplate> get() throws GeneralSecurityException {
    Map<String, KeyTemplate> result = new HashMap<>();
    result.put(
        "HKDF_SHA256_DERIVES_AES128_GCM",
        createPrfBasedKeyTemplate(KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("AES128_GCM")));
    result.put(
        "HKDF_SHA256_DERIVES_AES256_GCM",
        createPrfBasedKeyTemplate(KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("AES256_GCM")));
    result.put(
        "HKDF_SHA256_DERIVES_HMAC_SHA256_128BITTAG",
        createPrfBasedKeyTemplate(
            KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("HMAC_SHA256_128BITTAG")));
    result.put(
        "HKDF_SHA256_DERIVES_HMAC_SHA256_PRF",
        createPrfBasedKeyTemplate(
            KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("HMAC_SHA256_PRF")));
    result.put(
        "HKDF_SHA256_DERIVES_AES256_GCM_HKDF_1MB",
        createPrfBasedKeyTemplate(
            KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("AES256_GCM_HKDF_1MB")));
    result.put(
        "HKDF_SHA256_DERIVES_ED25519",
        createPrfBasedKeyTemplate(KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("ED25519")));
    result.put(
        "HKDF_SHA256_DERIVES_XCHACHA20_POLY1305",
        createPrfBasedKeyTemplate(
            KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("XCHACHA20_POLY1305")));
    result.put(
        "HKDF_SHA256_DERIVES_AES256_SIV",
        createPrfBasedKeyTemplate(KeyTemplates.get("HKDF_SHA256"), KeyTemplates.get("AES256_SIV")));
    return Collections.unmodifiableMap(result);
  }

  private TinkeyKeyTemplates() {}
}
