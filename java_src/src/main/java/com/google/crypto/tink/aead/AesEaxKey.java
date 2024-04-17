// Copyright 2022 Google LLC
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

package com.google.crypto.tink.aead;

import com.google.crypto.tink.AccessesPartialKey;
import com.google.crypto.tink.Key;
import com.google.crypto.tink.internal.OutputPrefixUtil;
import com.google.crypto.tink.util.Bytes;
import com.google.crypto.tink.util.SecretBytes;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.RestrictedApi;
import java.security.GeneralSecurityException;
import java.util.Objects;
import javax.annotation.Nullable;

/** Represents an AES-EAX key used for computing AEAD. */
@Immutable
public final class AesEaxKey extends AeadKey {
  private final AesEaxParameters parameters;
  private final SecretBytes keyBytes;
  private final Bytes outputPrefix;
  @Nullable private final Integer idRequirement;

  /** Builder for AesEaxKey. */
  public static class Builder {
    @Nullable private AesEaxParameters parameters = null;
    @Nullable private SecretBytes keyBytes = null;
    @Nullable private Integer idRequirement = null;

    private Builder() {}

    @CanIgnoreReturnValue
    public Builder setParameters(AesEaxParameters parameters) {
      this.parameters = parameters;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setKeyBytes(SecretBytes keyBytes) {
      this.keyBytes = keyBytes;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIdRequirement(@Nullable Integer idRequirement) {
      this.idRequirement = idRequirement;
      return this;
    }

    private Bytes getOutputPrefix() {
      if (parameters.getVariant() == AesEaxParameters.Variant.NO_PREFIX) {
        return OutputPrefixUtil.EMPTY_PREFIX;
      }
      if (parameters.getVariant() == AesEaxParameters.Variant.CRUNCHY) {
        return OutputPrefixUtil.getLegacyOutputPrefix(idRequirement);
      }
      if (parameters.getVariant() == AesEaxParameters.Variant.TINK) {
        return OutputPrefixUtil.getTinkOutputPrefix(idRequirement);
      }
      throw new IllegalStateException(
          "Unknown AesEaxParameters.Variant: " + parameters.getVariant());
    }

    public AesEaxKey build() throws GeneralSecurityException {
      if (parameters == null || keyBytes == null) {
        throw new GeneralSecurityException("Cannot build without parameters and/or key material");
      }

      if (parameters.getKeySizeBytes() != keyBytes.size()) {
        throw new GeneralSecurityException("Key size mismatch");
      }

      if (parameters.hasIdRequirement() && idRequirement == null) {
        throw new GeneralSecurityException(
            "Cannot create key without ID requirement with parameters with ID requirement");
      }

      if (!parameters.hasIdRequirement() && idRequirement != null) {
        throw new GeneralSecurityException(
            "Cannot create key with ID requirement with parameters without ID requirement");
      }
      Bytes outputPrefix = getOutputPrefix();
      return new AesEaxKey(parameters, keyBytes, outputPrefix, idRequirement);
    }
  }

  private AesEaxKey(
      AesEaxParameters parameters,
      SecretBytes keyBytes,
      Bytes outputPrefix,
      @Nullable Integer idRequirement) {
    this.parameters = parameters;
    this.keyBytes = keyBytes;
    this.outputPrefix = outputPrefix;
    this.idRequirement = idRequirement;
  }

  @RestrictedApi(
      explanation = "Accessing parts of keys can produce unexpected incompatibilities, annotate the function with @AccessesPartialKey",
      link = "https://developers.google.com/tink/design/access_control#accessing_partial_keys",
      allowedOnPath = ".*Test\\.java",
      allowlistAnnotations = {AccessesPartialKey.class})
  public static Builder builder() {
    return new Builder();
  }

  /** Returns the underlying key bytes. */
  @RestrictedApi(
      explanation = "Accessing parts of keys can produce unexpected incompatibilities, annotate the function with @AccessesPartialKey",
      link = "https://developers.google.com/tink/design/access_control#accessing_partial_keys",
      allowedOnPath = ".*Test\\.java",
      allowlistAnnotations = {AccessesPartialKey.class})
  public SecretBytes getKeyBytes() {
    return keyBytes;
  }

  @Override
  public Bytes getOutputPrefix() {
    return outputPrefix;
  }

  @Override
  public AesEaxParameters getParameters() {
    return parameters;
  }

  @Override
  @Nullable
  public Integer getIdRequirementOrNull() {
    return idRequirement;
  }

  @Override
  public boolean equalsKey(Key o) {
    if (!(o instanceof AesEaxKey)) {
      return false;
    }
    AesEaxKey that = (AesEaxKey) o;
    // Since outputPrefix is a function of parameters, we can ignore it here.
    return that.parameters.equals(parameters)
        && that.keyBytes.equalsSecretBytes(keyBytes)
        && Objects.equals(that.idRequirement, idRequirement);
  }
}
