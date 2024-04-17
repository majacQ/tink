// Copyright 2020 Google LLC
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
///////////////////////////////////////////////////////////////////////////////
#ifndef TINK_PRF_HMAC_PRF_KEY_MANAGER_H_
#define TINK_PRF_HMAC_PRF_KEY_MANAGER_H_

#include <algorithm>
#include <cstdint>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/status/status.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "tink/core/key_type_manager.h"
#include "tink/core/template_util.h"
#include "tink/input_stream.h"
#include "tink/internal/fips_utils.h"
#include "tink/key_manager.h"
#include "tink/prf/prf_set.h"
#include "tink/subtle/common_enums.h"
#include "tink/subtle/prf/prf_set_util.h"
#include "tink/subtle/random.h"
#include "tink/subtle/stateful_hmac_boringssl.h"
#include "tink/util/constants.h"
#include "tink/util/enums.h"
#include "tink/util/errors.h"
#include "tink/util/protobuf_helper.h"
#include "tink/util/secret_data.h"
#include "tink/util/status.h"
#include "tink/util/statusor.h"
#include "tink/util/validation.h"
#include "proto/hmac_prf.pb.h"
#include "proto/tink.pb.h"

namespace crypto {
namespace tink {

class HmacPrfKeyManager
    : public KeyTypeManager<google::crypto::tink::HmacPrfKey,
                            google::crypto::tink::HmacPrfKeyFormat, List<Prf>> {
 public:
  class PrfFactory : public PrimitiveFactory<Prf> {
    crypto::tink::util::StatusOr<std::unique_ptr<Prf>> Create(
        const google::crypto::tink::HmacPrfKey& key) const override {
      crypto::tink::subtle::HashType hash =
          util::Enums::ProtoToSubtle(key.params().hash());
      absl::optional<uint64_t> max_output_length = MaxOutputLength(hash);
      if (!max_output_length.has_value()) {
        return util::Status(
            absl::StatusCode::kInvalidArgument,
            absl::StrCat("Unknown hash when constructing HMAC PRF ",
                         HashType_Name(key.params().hash())));
      }
      return subtle::CreatePrfFromStatefulMacFactory(
          absl::make_unique<subtle::StatefulHmacBoringSslFactory>(
              hash, *max_output_length,
              util::SecretDataFromStringView(key.key_value())));
    }
  };

  HmacPrfKeyManager()
      : KeyTypeManager(absl::make_unique<HmacPrfKeyManager::PrfFactory>()) {}

  uint32_t get_version() const override { return 0; }

  google::crypto::tink::KeyData::KeyMaterialType key_material_type()
      const override {
    return google::crypto::tink::KeyData::SYMMETRIC;
  }

  const std::string& get_key_type() const override { return key_type_; }

  crypto::tink::util::Status ValidateKey(
      const google::crypto::tink::HmacPrfKey& key) const override;

  crypto::tink::util::Status ValidateKeyFormat(
      const google::crypto::tink::HmacPrfKeyFormat& key_format) const override;

  crypto::tink::util::StatusOr<google::crypto::tink::HmacPrfKey> CreateKey(
      const google::crypto::tink::HmacPrfKeyFormat& key_format) const override;

  util::StatusOr<google::crypto::tink::HmacPrfKey> DeriveKey(
      const google::crypto::tink::HmacPrfKeyFormat& hmac_prf_key_format,
      InputStream* input_stream) const override;

  internal::FipsCompatibility FipsStatus() const override {
    return internal::FipsCompatibility::kRequiresBoringCrypto;
  }

 private:
  static absl::optional<uint64_t> MaxOutputLength(subtle::HashType hash_type);

  util::Status ValidateParams(
      const google::crypto::tink::HmacPrfParams& params) const;

  const std::string key_type_ = absl::StrCat(
      kTypeGoogleapisCom, google::crypto::tink::HmacPrfKey().GetTypeName());
};

}  // namespace tink
}  // namespace crypto

#endif  // TINK_PRF_HMAC_PRF_KEY_MANAGER_H_
