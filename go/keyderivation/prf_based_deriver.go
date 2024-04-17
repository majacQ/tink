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

package keyderivation

import (
	"errors"
	"fmt"

	"github.com/google/tink/go/internal/internalregistry"
	"github.com/google/tink/go/keyderivation/internal/streamingprf"
	"github.com/google/tink/go/keyset"
	tinkpb "github.com/google/tink/go/proto/tink_go_proto"
)

const hkdfPRFTypeURL = "type.googleapis.com/google.crypto.tink.HkdfPrfKey"

// prfBasedDeriver uses prf and the Tink registry to derive a keyset handle as
// described by derivedKeyTemplate.
type prfBasedDeriver struct {
	prf                streamingprf.StreamingPRF
	derivedKeyTemplate *tinkpb.KeyTemplate
}

// Asserts that prfBasedDeriver implements the KeysetDeriver interface.
var _ KeysetDeriver = (*prfBasedDeriver)(nil)

func newPRFBasedDeriver(prfKeyData *tinkpb.KeyData, derivedKeyTemplate *tinkpb.KeyTemplate) (*prfBasedDeriver, error) {
	// Obtain Streaming PRF from PRF key data.
	if prfKeyData == nil {
		return nil, errors.New("PRF key data is nil")
	}
	if prfKeyData.GetTypeUrl() != hkdfPRFTypeURL {
		return nil, fmt.Errorf("PRF key data with type URL %q is not supported", prfKeyData.GetTypeUrl())
	}
	// For HKDF PRF keys, create a local instance of the HKDF Streaming PRF key
	// manager and obtain the Streaming PRF interface through it, instead of
	// obtaining it through the registry. This allows us to keep the HKDF
	// Streaming PRF key manager out of the registry for smoother deprecation.
	//
	// TODO(b/260619626): Remove this once PRF and Streaming PRF share the same
	// type URL and registry.Primitive() can return multiple interfaces per
	// primitive.
	hkdfStreamingPRFKeyManager := streamingprf.HKDFStreamingPRFKeyManager{}
	p, err := hkdfStreamingPRFKeyManager.Primitive(prfKeyData.GetValue())
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve StreamingPRF primitive from key manager: %v", err)
	}
	prf, ok := p.(streamingprf.StreamingPRF)
	if !ok {
		return nil, errors.New("primitive is not StreamingPRF")
	}

	// Validate derived key template.
	if !internalregistry.CanDeriveKeys(derivedKeyTemplate.GetTypeUrl()) {
		return nil, errors.New("derived key template is not a derivable key type")
	}

	return &prfBasedDeriver{
		prf:                prf,
		derivedKeyTemplate: derivedKeyTemplate,
	}, nil
}

func (p *prfBasedDeriver) DeriveKeyset(salt []byte) (*keyset.Handle, error) {
	randomness, err := p.prf.Compute(salt)
	if err != nil {
		return nil, fmt.Errorf("compute randomness from PRF failed: %v", err)
	}
	keyData, err := internalregistry.DeriveKey(p.derivedKeyTemplate, randomness)
	if err != nil {
		return nil, fmt.Errorf("derive key failed: %v", err)
	}
	// Fill in placeholder values for key ID, status, and output prefix type.
	// These will be populated with the correct values in the keyset deriver
	// factory. This is acceptable because the keyset as-is will never leave Tink,
	// and the user only interacts via the keyset deriver factory.
	var primaryKeyID uint32 = 0
	return keysetHandle(&tinkpb.Keyset{
		PrimaryKeyId: primaryKeyID,
		Key: []*tinkpb.Keyset_Key{
			&tinkpb.Keyset_Key{
				KeyData:          keyData,
				Status:           tinkpb.KeyStatusType_UNKNOWN_STATUS,
				KeyId:            primaryKeyID,
				OutputPrefixType: tinkpb.OutputPrefixType_UNKNOWN_PREFIX,
			},
		},
	})
}
