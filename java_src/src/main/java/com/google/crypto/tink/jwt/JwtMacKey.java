// Copyright 2023 Google LLC
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

import com.google.crypto.tink.Key;
import java.util.Optional;

/**
 * Represents a key to compute JWT using symmetric cryptography (i.e., using the {@link JwtMac}
 * interface).
 */
public abstract class JwtMacKey extends Key {
  /**
   * Returns the "kid" to be used for this key (https://www.rfc-editor.org/rfc/rfc7517#section-4.5).
   *
   * <p>Note that the "kid" is not necessarily related to Tink's "Key ID" in the keyset.
   *
   * <p>If present, this kid will be written into the {@code kid} header during {@code
   * computeMacAndEncode}. If absent, no kid will be written.
   *
   * <p>If present, and the {@code kid} header is present, the contents of the {@code kid} header
   * needs to match the return value of this function.
   *
   * <p>Note that {@code getParameters.allowKidAbsent()} specifies if omitting the {@code kid}
   * header is allowed. Of course, if {@code getParameters.allowKidAbsent()} is false, then {@code
   * getKid} must not return an empty {@link Optional}.
   */
  public abstract Optional<String> getKid();

  @Override
  public abstract JwtMacParameters getParameters();
}
