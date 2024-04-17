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

#ifndef TINK_HYBRID_HYBRID_PRIVATE_KEY_H_
#define TINK_HYBRID_HYBRID_PRIVATE_KEY_H_

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "tink/hybrid/hybrid_parameters.h"
#include "tink/hybrid/hybrid_public_key.h"
#include "tink/key.h"
#include "tink/private_key.h"

namespace crypto {
namespace tink {

// Represents the decryption function for a hybrid encryption primitive.
class HybridPrivateKey : public PrivateKey {
 public:
  const HybridPublicKey& GetPublicKey() const override = 0;

  // Returns the bytes prefixed to every ciphertext generated by the
  // corresponding public key.
  //
  // In order to make key rotation more efficient, Tink allows every hybrid
  // private key to have an associated ciphertext output prefix. When decrypting
  // a ciphertext, only keys with a matching prefix have to be tried.
  //
  // See https://developers.google.com/tink/wire-format#tink_output_prefix for
  // more background information on Tink output prefixes.
  absl::string_view GetOutputPrefix() const {
    return GetPublicKey().GetOutputPrefix();
  }

  absl::optional<int> GetIdRequirement() const override {
    return GetPublicKey().GetIdRequirement();
  }

  const HybridParameters& GetParameters() const override {
    return GetPublicKey().GetParameters();
  }

  bool operator==(const Key& other) const override = 0;
};

}  // namespace tink
}  // namespace crypto

#endif  // TINK_HYBRID_HYBRID_PRIVATE_KEY_H_
