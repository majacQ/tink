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

package com.google.crypto.tink.jwt;

import com.google.crypto.tink.PrimitiveWrapper;
import com.google.crypto.tink.internal.MutablePrimitiveRegistry;
import com.google.crypto.tink.internal.PrimitiveSet;
import com.google.errorprone.annotations.Immutable;
import java.security.GeneralSecurityException;
import java.util.List;

/** The implementation of {@code PrimitiveWrapper<JwtPublicKeyVerify>}. */
class JwtPublicKeyVerifyWrapper
    implements PrimitiveWrapper<JwtPublicKeyVerify, JwtPublicKeyVerify> {

  private static final JwtPublicKeyVerifyWrapper WRAPPER = new JwtPublicKeyVerifyWrapper();

  @Immutable
  private static class WrappedJwtPublicKeyVerify implements JwtPublicKeyVerify {

    @SuppressWarnings("Immutable")
    private final PrimitiveSet<JwtPublicKeyVerify> primitives;

    public WrappedJwtPublicKeyVerify(PrimitiveSet<JwtPublicKeyVerify> primitives) {
      this.primitives = primitives;
    }

    @Override
    public VerifiedJwt verifyAndDecode(String compact, JwtValidator validator)
        throws GeneralSecurityException {
      GeneralSecurityException interestingException = null;
      for (List<PrimitiveSet.Entry<JwtPublicKeyVerify>> entries : primitives.getAll()) {
        for (PrimitiveSet.Entry<JwtPublicKeyVerify> entry : entries) {
          try {
            return entry.getFullPrimitive().verifyAndDecode(compact, validator);
          } catch (GeneralSecurityException e) {
            if (e instanceof JwtInvalidException) {
              // Keep this exception so that we are able to throw a meaningful message in the end
              interestingException = e;
            }
            // Ignored as we want to continue verification with other raw keys.
          }
        }
      }
      if (interestingException != null) {
        throw interestingException;
      }
      throw new GeneralSecurityException("invalid JWT");
    }
  }

  @Override
  public JwtPublicKeyVerify wrap(final PrimitiveSet<JwtPublicKeyVerify> primitives)
      throws GeneralSecurityException {
    return new WrappedJwtPublicKeyVerify(primitives);
  }

  @Override
  public Class<JwtPublicKeyVerify> getPrimitiveClass() {
    return JwtPublicKeyVerify.class;
  }

  @Override
  public Class<JwtPublicKeyVerify> getInputPrimitiveClass() {
    return JwtPublicKeyVerify.class;
  }

  /**
   * Register the wrapper within the registry.
   *
   * <p>This is required for calls to {@link Keyset.getPrimitive} with a {@link JwtPublicKeyVerify}
   * argument.
   */
  public static void register() throws GeneralSecurityException {
    MutablePrimitiveRegistry.globalInstance().registerPrimitiveWrapper(WRAPPER);
  }
}
