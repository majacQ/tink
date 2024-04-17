// Copyright 2018 Google LLC
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

package aead_test

import (
	"bytes"
	"fmt"
	"testing"

	"github.com/google/tink/go/aead"
	"github.com/google/tink/go/core/registry"
	"github.com/google/tink/go/internal/tinkerror/tinkerrortest"
	"github.com/google/tink/go/keyset"
	"github.com/google/tink/go/mac"
	"github.com/google/tink/go/testing/fakekms"
	tinkpb "github.com/google/tink/go/proto/tink_go_proto"
)

func TestKeyTemplates(t *testing.T) {
	var testCases = []struct {
		name     string
		template *tinkpb.KeyTemplate
	}{
		{
			name:     "AES128_GCM",
			template: aead.AES128GCMKeyTemplate(),
		}, {
			name:     "AES256_GCM",
			template: aead.AES256GCMKeyTemplate(),
		}, {
			name:     "AES256_GCM_NO_PREFIX",
			template: aead.AES256GCMNoPrefixKeyTemplate(),
		}, {
			name:     "AES128_GCM_SIV",
			template: aead.AES128GCMSIVKeyTemplate(),
		}, {
			name:     "AES256_GCM_SIV",
			template: aead.AES256GCMSIVKeyTemplate(),
		}, {
			name:     "AES256_GCM_SIV_NO_PREFIX",
			template: aead.AES256GCMSIVNoPrefixKeyTemplate(),
		}, {
			name:     "AES128_CTR_HMAC_SHA256",
			template: aead.AES128CTRHMACSHA256KeyTemplate(),
		}, {
			name:     "AES256_CTR_HMAC_SHA256",
			template: aead.AES256CTRHMACSHA256KeyTemplate(),
		}, {
			name:     "CHACHA20_POLY1305",
			template: aead.ChaCha20Poly1305KeyTemplate(),
		}, {
			name:     "XCHACHA20_POLY1305",
			template: aead.XChaCha20Poly1305KeyTemplate(),
		},
	}
	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			if err := testEncryptDecrypt(tc.template); err != nil {
				t.Errorf("%v", err)
			}
		})
	}
}

func TestKMSEnvelopeAEADKeyTemplate(t *testing.T) {
	fakeKmsClient, err := fakekms.NewClient("fake-kms://")
	if err != nil {
		t.Fatalf("fakekms.NewClient('fake-kms://') failed: %v", err)
	}
	registry.RegisterKMSClient(fakeKmsClient)

	fixedKeyURI := "fake-kms://CM2b3_MDElQKSAowdHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUuY3J5cHRvLnRpbmsuQWVzR2NtS2V5EhIaEIK75t5L-adlUwVhWvRuWUwYARABGM2b3_MDIAE"
	newKeyURI, err := fakekms.NewKeyURI()
	if err != nil {
		t.Fatalf("fakekms.NewKeyURI() failed: %v", err)
	}
	fixedKeyTemplate, err := aead.CreateKMSEnvelopeAEADKeyTemplate(fixedKeyURI, aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("CreateKMSEnvelopeAEADKeyTemplate() err = %v", err)
	}
	newKeyTemplate, err := aead.CreateKMSEnvelopeAEADKeyTemplate(newKeyURI, aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("CreateKMSEnvelopeAEADKeyTemplate() err = %v", err)
	}

	var testCases = []struct {
		name     string
		template *tinkpb.KeyTemplate
	}{
		{
			name:     "Fixed Fake KMS Envelope AEAD Key with AES128_GCM",
			template: fixedKeyTemplate,
		}, {
			name:     "New Fake KMS Envelope AEAD Key with AES128_GCM",
			template: newKeyTemplate,
		},
	}
	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			if tc.template.GetOutputPrefixType() != tinkpb.OutputPrefixType_RAW {
				t.Errorf("KMS envelope template %s does not use RAW prefix, found '%s'", tc.name, tc.template.GetOutputPrefixType())
			}
			if err := testEncryptDecrypt(tc.template); err != nil {
				t.Errorf("%v", err)
			}
		})
	}
}

// Tests that two KMSEnvelopeAEAD keys that use the same KEK and DEK template
// should be able to decrypt each other's ciphertexts.
func TestKMSEnvelopeAEADKeyTemplateMultipleKeysSameKEK(t *testing.T) {
	fakeKmsClient, err := fakekms.NewClient("fake-kms://")
	if err != nil {
		t.Fatalf("fakekms.NewClient('fake-kms://') failed: %v", err)
	}
	registry.RegisterKMSClient(fakeKmsClient)

	fixedKeyURI := "fake-kms://CM2b3_MDElQKSAowdHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUuY3J5cHRvLnRpbmsuQWVzR2NtS2V5EhIaEIK75t5L-adlUwVhWvRuWUwYARABGM2b3_MDIAE"
	template1, err := aead.CreateKMSEnvelopeAEADKeyTemplate(fixedKeyURI, aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("CreateKMSEnvelopeAEADKeyTemplate() err = %v", err)
	}
	template2, err := aead.CreateKMSEnvelopeAEADKeyTemplate(fixedKeyURI, aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("CreateKMSEnvelopeAEADKeyTemplate() err = %v", err)
	}

	handle1, err := keyset.NewHandle(template1)
	if err != nil {
		t.Fatalf("keyset.NewHandle(template1) failed: %v", err)
	}
	aead1, err := aead.New(handle1)
	if err != nil {
		t.Fatalf("aead.New(handle) failed: %v", err)
	}

	handle2, err := keyset.NewHandle(template2)
	if err != nil {
		t.Fatalf("keyset.NewHandle(template2) failed: %v", err)
	}
	aead2, err := aead.New(handle2)
	if err != nil {
		t.Fatalf("aead.New(handle) failed: %v", err)
	}

	plaintext := []byte("some data to encrypt")
	aad := []byte("extra data to authenticate")

	ciphertext, err := aead1.Encrypt(plaintext, aad)
	if err != nil {
		t.Fatalf("encryption failed, error: %v", err)
	}
	decrypted, err := aead2.Decrypt(ciphertext, aad)
	if err != nil {
		t.Fatalf("decryption failed, error: %v", err)
	}
	if !bytes.Equal(plaintext, decrypted) {
		t.Fatalf("decrypted data doesn't match plaintext, got: %q, want: %q", decrypted, plaintext)
	}
}

// This test shows how migrate away from CreateKMSEnvelopeAEADKeyTemplate.
func TestMigrateFromCreateKMSEnvelopeAEADKeyTemplateToNewKMSEnvelopeAEAD2(t *testing.T) {
	kmsClient, err := fakekms.NewClient("fake-kms://")
	if err != nil {
		t.Fatalf("fakekms.NewClient('fake-kms://') failed: %v", err)
	}
	kekURI := "fake-kms://CM2b3_MDElQKSAowdHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUuY3J5cHRvLnRpbmsuQWVzR2NtS2V5EhIaEIK75t5L-adlUwVhWvRuWUwYARABGM2b3_MDIAE"

	// This code:
	registry.RegisterKMSClient(kmsClient)
	kmsEnvelopeAEADTemplate, err := aead.CreateKMSEnvelopeAEADKeyTemplate(kekURI, aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("CreateKMSEnvelopeAEADKeyTemplate() failed: %v", err)
	}
	handle, err := keyset.NewHandle(kmsEnvelopeAEADTemplate)
	if err != nil {
		t.Fatalf("keyset.NewHandle(kmsEnvelopeAEADTemplate) failed: %v", err)
	}
	aead1, err := aead.New(handle)
	if err != nil {
		t.Fatalf("aead.New(handle) failed: %v", err)
	}
	// can be replace by this:
	kekAEAD, err := kmsClient.GetAEAD(kekURI)
	if err != nil {
		t.Fatalf("kmsClient.GetAEAD(kekURI) failed: %v", err)
	}
	aead2 := aead.NewKMSEnvelopeAEAD2(aead.AES128GCMKeyTemplate(), kekAEAD)

	// Check that aead1 and aead2 are compatible.
	plaintext := []byte("plaintext")
	associatedData := []byte("associatedData")

	ciphertext, err := aead1.Encrypt(plaintext, associatedData)
	if err != nil {
		t.Fatalf("aead1.Encrypt(plaintext, associatedData) failed: %v", err)
	}
	decrypted, err := aead2.Decrypt(ciphertext, associatedData)
	if err != nil {
		t.Fatalf("aead2.Decrypt(ciphertext, associatedData) failed: %v", err)
	}
	if !bytes.Equal(plaintext, decrypted) {
		t.Fatalf("decrypted data doesn't match plaintext, got: %q, want: %q", decrypted, plaintext)
	}
}

// Testing deprecated function, ignoring GoDeprecated.
func TestCreateKMSEnvelopeAEADKeyTemplateCompatibleWithKMSEnevelopeAEADKeyTemplate(t *testing.T) {
	fakeKmsClient, err := fakekms.NewClient("fake-kms://")
	if err != nil {
		t.Fatalf("fakekms.NewClient('fake-kms://') failed: %v", err)
	}
	registry.RegisterKMSClient(fakeKmsClient)

	fixedKeyURI := "fake-kms://CM2b3_MDElQKSAowdHlwZS5nb29nbGVhcGlzLmNvbS9nb29nbGUuY3J5cHRvLnRpbmsuQWVzR2NtS2V5EhIaEIK75t5L-adlUwVhWvRuWUwYARABGM2b3_MDIAE"
	template1, err := aead.CreateKMSEnvelopeAEADKeyTemplate(fixedKeyURI, aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("CreateKMSEnvelopeAEADKeyTemplate() err = %v", err)
	}
	template2 := aead.KMSEnvelopeAEADKeyTemplate(fixedKeyURI, aead.AES128GCMKeyTemplate())

	handle1, err := keyset.NewHandle(template1)
	if err != nil {
		t.Fatalf("keyset.NewHandle(template1) failed: %v", err)
	}
	aead1, err := aead.New(handle1)
	if err != nil {
		t.Fatalf("aead.New(handle) failed: %v", err)
	}

	handle2, err := keyset.NewHandle(template2)
	if err != nil {
		t.Fatalf("keyset.NewHandle(template2) failed: %v", err)
	}
	aead2, err := aead.New(handle2)
	if err != nil {
		t.Fatalf("aead.New(handle) failed: %v", err)
	}

	plaintext := []byte("some data to encrypt")
	aad := []byte("extra data to authenticate")

	ciphertext, err := aead1.Encrypt(plaintext, aad)
	if err != nil {
		t.Fatalf("encryption failed, error: %v", err)
	}
	decrypted, err := aead2.Decrypt(ciphertext, aad)
	if err != nil {
		t.Fatalf("decryption failed, error: %v", err)
	}
	if !bytes.Equal(plaintext, decrypted) {
		t.Fatalf("decrypted data doesn't match plaintext, got: %q, want: %q", decrypted, plaintext)
	}
}

// Testing deprecated function, ignoring GoDeprecated.
func TestKMSEnvelopeAEADKeyTemplateFails(t *testing.T) {
	keyURI, err := fakekms.NewKeyURI()
	if err != nil {
		t.Fatalf("fakekms.NewKeyURI() err = %v", err)
	}
	invalidTemplate := &tinkpb.KeyTemplate{
		// String fields cannot contain invalid UTF-8 characters.
		TypeUrl: "\xff",
	}
	var template *tinkpb.KeyTemplate
	err = tinkerrortest.RecoverFromFail(func() {
		template = aead.KMSEnvelopeAEADKeyTemplate(keyURI, invalidTemplate)
	})
	if err == nil {
		t.Errorf("aead.KMSEnvelopAEADKeyTemplate() err = nil, want non-nil")
	}
	t.Logf("template: %+v", template)
}

func TestCreateKMSEnvelopeAEADKeyTemplateFails(t *testing.T) {
	keyURI, err := fakekms.NewKeyURI()
	if err != nil {
		t.Fatalf("fakekms.NewKeyURI() err = %v", err)
	}
	invalidTemplate := &tinkpb.KeyTemplate{
		// String fields cannot contain invalid UTF-8 characters.
		TypeUrl: "\xff",
	}
	if _, err := aead.CreateKMSEnvelopeAEADKeyTemplate(keyURI, invalidTemplate); err == nil {
		t.Errorf("aead.CreateKMSEnvelopAEADKeyTemplate(keyURI, invalidTemplate) err = nil, want non-nil")
	}
}

func TestCreateKMSEnvelopeAEADKeyTemplateWithUnsupportedTemplateFails(t *testing.T) {
	keyURI, err := fakekms.NewKeyURI()
	if err != nil {
		t.Fatalf("fakekms.NewKeyURI() err = %v", err)
	}
	unsupportedTemplate := mac.HMACSHA256Tag128KeyTemplate()
	if _, err := aead.CreateKMSEnvelopeAEADKeyTemplate(keyURI, unsupportedTemplate); err == nil {
		t.Errorf("aead.CreateKMSEnvelopAEADKeyTemplate(keyURI, unsupportedTemplate) err = nil, want non-nil")
	}
}

func testEncryptDecrypt(template *tinkpb.KeyTemplate) error {
	handle, err := keyset.NewHandle(template)
	if err != nil {
		return fmt.Errorf("keyset.NewHandle(template) failed: %v", err)
	}
	primitive, err := aead.New(handle)
	if err != nil {
		return fmt.Errorf("aead.New(handle) failed: %v", err)
	}

	var testInputs = []struct {
		plaintext []byte
		aad1      []byte
		aad2      []byte
	}{
		{
			plaintext: []byte("some data to encrypt"),
			aad1:      []byte("extra data to authenticate"),
			aad2:      []byte("extra data to authenticate"),
		}, {
			plaintext: []byte("some data to encrypt"),
			aad1:      []byte(""),
			aad2:      []byte(""),
		}, {
			plaintext: []byte("some data to encrypt"),
			aad1:      nil,
			aad2:      nil,
		}, {
			plaintext: []byte(""),
			aad1:      nil,
			aad2:      nil,
		}, {
			plaintext: nil,
			aad1:      []byte("extra data to authenticate"),
			aad2:      []byte("extra data to authenticate"),
		}, {
			plaintext: nil,
			aad1:      []byte(""),
			aad2:      []byte(""),
		}, {
			plaintext: nil,
			aad1:      nil,
			aad2:      nil,
		}, {
			plaintext: []byte("some data to encrypt"),
			aad1:      []byte(""),
			aad2:      nil,
		}, {
			plaintext: []byte("some data to encrypt"),
			aad1:      nil,
			aad2:      []byte(""),
		},
	}
	for _, ti := range testInputs {
		ciphertext, err := primitive.Encrypt(ti.plaintext, ti.aad1)
		if err != nil {
			return fmt.Errorf("encryption failed, error: %v", err)
		}
		decrypted, err := primitive.Decrypt(ciphertext, ti.aad2)
		if err != nil {
			return fmt.Errorf("decryption failed, error: %v", err)
		}
		if !bytes.Equal(ti.plaintext, decrypted) {
			return fmt.Errorf("decrypted data doesn't match plaintext, got: %q, want: %q", decrypted, ti.plaintext)
		}
	}
	return nil
}
