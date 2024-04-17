/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package walkthrough;

// [START tink_walkthrough_create_keyset]
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.security.GeneralSecurityException;

// [START_EXCLUDE]
/** Example that creates a keyset and returns a {@link KeysetHandle}. */
final class CreateKeysetExample {
  private CreateKeysetExample() {}
  // [END_EXCLUDE]

  /**
   * Creates a keyset with a single AES128-GCM key and return a handle to it.
   *
   * <p>Prerequisites for this example:
   *
   * <ul>
   *   <li>Register AEAD implementations of Tink.
   * </ul>
   *
   * @return a new {@link KeysetHandle} with a single AES128-GCM key.
   * @throws GeneralSecurityException if any error occours.
   */
  static KeysetHandle createAes128GcmKeyset() throws GeneralSecurityException {
    // Tink provides pre-baked sets of parameters. For example, here we use AES128-GCM's template.
    // This will generate a new keyset with only *one* key and return a keyset handle to it.
    return KeysetHandle.generateNew(PredefinedAeadParameters.AES128_GCM);
  }
  // [END tink_walkthrough_create_keyset]
}
