// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

#ifndef TINK_JWT_JWT_SIGNATURE_PARAMETERS_H_
#define TINK_JWT_JWT_SIGNATURE_PARAMETERS_H_

#include "tink/parameters.h"

namespace crypto {
namespace tink {

// Describes a JWT signature key pair without the randomly chosen key material.
class JwtSignatureParameters : public Parameters {
  // Returns true if verification is allowed for tokens without a `kid` header.
  virtual bool AllowKidAbsent() const = 0;
};

}  // namespace tink
}  // namespace crypto


#endif  // TINK_JWT_JWT_SIGNATURE_PARAMETERS_H_
