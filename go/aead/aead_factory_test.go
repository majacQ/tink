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
	"errors"
	"fmt"
	"strings"
	"testing"

	"github.com/google/go-cmp/cmp"
	"github.com/google/go-cmp/cmp/cmpopts"
	"github.com/google/tink/go/aead"
	"github.com/google/tink/go/core/cryptofmt"
	"github.com/google/tink/go/core/registry"
	"github.com/google/tink/go/insecurecleartextkeyset"
	"github.com/google/tink/go/internal/internalregistry"
	"github.com/google/tink/go/internal/testing/stubkeymanager"
	"github.com/google/tink/go/keyset"
	"github.com/google/tink/go/monitoring"
	"github.com/google/tink/go/signature"
	"github.com/google/tink/go/subtle/random"
	"github.com/google/tink/go/testing/fakemonitoring"
	"github.com/google/tink/go/testkeyset"
	"github.com/google/tink/go/testutil"
	"github.com/google/tink/go/tink"

	"github.com/google/tink/go/aead/subtle"
	agpb "github.com/google/tink/go/proto/aes_gcm_go_proto"
	tinkpb "github.com/google/tink/go/proto/tink_go_proto"
)

func TestFactoryMultipleKeys(t *testing.T) {
	// encrypt with non-raw key
	keyset := testutil.NewTestAESGCMKeyset(tinkpb.OutputPrefixType_TINK)
	primaryKey := keyset.Key[0]
	if primaryKey.OutputPrefixType == tinkpb.OutputPrefixType_RAW {
		t.Errorf("expect a non-raw key")
	}
	keysetHandle, err := testkeyset.NewHandle(keyset)
	if err != nil {
		t.Fatalf("testkeyset.NewHandle() err = %s, want err", err)
	}
	a, err := aead.New(keysetHandle)
	if err != nil {
		t.Errorf("aead.New failed: %s", err)
	}
	expectedPrefix, err := cryptofmt.OutputPrefix(primaryKey)
	if err != nil {
		t.Errorf("cryptofmt.OutputPrefix() err = %s, want nil", err)
	}
	if err := validateAEADFactoryCipher(a, a, expectedPrefix); err != nil {
		t.Errorf("invalid cipher: %s", err)
	}

	// encrypt with a non-primary RAW key and decrypt with the keyset
	rawKey := keyset.Key[1]
	if rawKey.OutputPrefixType != tinkpb.OutputPrefixType_RAW {
		t.Errorf("expect a raw key")
	}
	keyset2 := testutil.NewKeyset(rawKey.KeyId, []*tinkpb.Keyset_Key{rawKey})
	keysetHandle2, err := testkeyset.NewHandle(keyset2)
	if err != nil {
		t.Fatalf("testkeyset.NewHandle() err = %s, want err", err)
	}
	a2, err := aead.New(keysetHandle2)
	if err != nil {
		t.Errorf("aead.New failed: %s", err)
	}
	if err := validateAEADFactoryCipher(a2, a, cryptofmt.RawPrefix); err != nil {
		t.Errorf("invalid cipher: %s", err)
	}

	// encrypt with a random key not in the keyset, decrypt with the keyset should fail
	keyset2 = testutil.NewTestAESGCMKeyset(tinkpb.OutputPrefixType_TINK)
	primaryKey = keyset2.Key[0]
	expectedPrefix, err = cryptofmt.OutputPrefix(primaryKey)
	if err != nil {
		t.Errorf("cryptofmt.OutputPrefix() err = %s, want err", err)
	}
	keysetHandle2, err = testkeyset.NewHandle(keyset2)
	if err != nil {
		t.Fatalf("testkeyset.NewHandle() err = %s, want err", err)
	}
	a2, err = aead.New(keysetHandle2)
	if err != nil {
		t.Errorf("aead.New failed: %s", err)
	}
	err = validateAEADFactoryCipher(a2, a, expectedPrefix)
	if err == nil || !strings.Contains(err.Error(), "decryption failed") {
		t.Errorf("expect decryption to fail with random key: %s", err)
	}
}

func TestFactoryRawKeyAsPrimary(t *testing.T) {
	keyset := testutil.NewTestAESGCMKeyset(tinkpb.OutputPrefixType_RAW)
	if keyset.Key[0].OutputPrefixType != tinkpb.OutputPrefixType_RAW {
		t.Errorf("primary key is not a raw key")
	}
	keysetHandle, err := testkeyset.NewHandle(keyset)
	if err != nil {
		t.Fatalf("testkeyset.NewHandle() err = %s, want err", err)
	}

	a, err := aead.New(keysetHandle)
	if err != nil {
		t.Errorf("cannot get primitive from keyset handle: %s", err)
	}
	if err := validateAEADFactoryCipher(a, a, cryptofmt.RawPrefix); err != nil {
		t.Errorf("invalid cipher: %s", err)
	}
}

func validateAEADFactoryCipher(encryptCipher, decryptCipher tink.AEAD, expectedPrefix string) error {
	prefixSize := len(expectedPrefix)
	// regular plaintext
	pt := random.GetRandomBytes(20)
	ad := random.GetRandomBytes(20)
	ct, err := encryptCipher.Encrypt(pt, ad)
	if err != nil {
		return fmt.Errorf("encryption failed with regular plaintext: %s", err)
	}
	decrypted, err := decryptCipher.Decrypt(ct, ad)
	if err != nil || !bytes.Equal(decrypted, pt) {
		return fmt.Errorf("decryption failed with regular plaintext: err: %s, pt: %s, decrypted: %s",
			err, pt, decrypted)
	}
	if string(ct[:prefixSize]) != expectedPrefix {
		return fmt.Errorf("incorrect prefix with regular plaintext")
	}
	if prefixSize+len(pt)+subtle.AESGCMIVSize+subtle.AESGCMTagSize != len(ct) {
		return fmt.Errorf("lengths of plaintext and ciphertext don't match with regular plaintext")
	}

	// short plaintext
	pt = random.GetRandomBytes(1)
	ct, err = encryptCipher.Encrypt(pt, ad)
	if err != nil {
		return fmt.Errorf("encryption failed with short plaintext: %s", err)
	}
	decrypted, err = decryptCipher.Decrypt(ct, ad)
	if err != nil || !bytes.Equal(decrypted, pt) {
		return fmt.Errorf("decryption failed with short plaintext: err: %s, pt: %s, decrypted: %s",
			err, pt, decrypted)
	}
	if string(ct[:prefixSize]) != expectedPrefix {
		return fmt.Errorf("incorrect prefix with short plaintext")
	}
	if prefixSize+len(pt)+subtle.AESGCMIVSize+subtle.AESGCMTagSize != len(ct) {
		return fmt.Errorf("lengths of plaintext and ciphertext don't match with short plaintext")
	}
	return nil
}

func TestFactoryWithInvalidPrimitiveSetType(t *testing.T) {
	wrongKH, err := keyset.NewHandle(signature.ECDSAP256KeyTemplate())
	if err != nil {
		t.Fatalf("failed to build *keyset.Handle: %s", err)
	}

	_, err = aead.New(wrongKH)
	if err == nil {
		t.Fatalf("calling New() with wrong *keyset.Handle should fail")
	}
}

func TestFactoryWithValidPrimitiveSetType(t *testing.T) {
	goodKH, err := keyset.NewHandle(aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("failed to build *keyset.Handle: %s", err)
	}

	_, err = aead.New(goodKH)
	if err != nil {
		t.Fatalf("calling New() with good *keyset.Handle failed: %s", err)
	}
}

func TestPrimitiveFactoryWithMonitoringAnnotationsLogsEncryptionDecryptionWithPrefix(t *testing.T) {
	defer internalregistry.ClearMonitoringClient()
	client := fakemonitoring.NewClient("fake-client")
	if err := internalregistry.RegisterMonitoringClient(client); err != nil {
		t.Fatalf("internalregistry.RegisterMonitoringClient() err = %v, want nil", err)
	}
	kh, err := keyset.NewHandle(aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("keyset.NewHandle() err = %v, want nil", err)
	}
	// Annotations are only supported throught the `insecurecleartextkeyset` API.
	buff := &bytes.Buffer{}
	if err := insecurecleartextkeyset.Write(kh, keyset.NewBinaryWriter(buff)); err != nil {
		t.Fatalf("insecurecleartextkeyset.Write() err = %v, want nil", err)
	}
	annotations := map[string]string{"foo": "bar"}
	mh, err := insecurecleartextkeyset.Read(keyset.NewBinaryReader(buff), keyset.WithAnnotations(annotations))
	if err != nil {
		t.Fatalf("insecurecleartextkeyset.Read() err = %v, want nil", err)
	}
	p, err := aead.New(mh)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	data := []byte("HELLO_WORLD")
	ad := []byte("_!")
	ct, err := p.Encrypt(data, ad)
	if err != nil {
		t.Fatalf("p.Encrypt() err = %v, want nil", err)
	}
	if _, err := p.Decrypt(ct, ad); err != nil {
		t.Fatalf("p.Decrypt() err = %v, want nil", err)
	}
	failures := client.Failures()
	if len(failures) != 0 {
		t.Errorf("len(client.Failures()) = %d, want = 0", len(failures))
	}
	got := client.Events()
	wantKeysetInfo := monitoring.NewKeysetInfo(
		annotations,
		kh.KeysetInfo().GetPrimaryKeyId(),
		[]*monitoring.Entry{
			{
				KeyID:     kh.KeysetInfo().GetPrimaryKeyId(),
				Status:    monitoring.Enabled,
				KeyType:   "tink.AesGcmKey",
				KeyPrefix: "TINK",
			},
		},
	)
	want := []*fakemonitoring.LogEvent{
		{
			KeyID:    mh.KeysetInfo().GetPrimaryKeyId(),
			NumBytes: len(data),
			Context:  monitoring.NewContext("aead", "encrypt", wantKeysetInfo),
		},
		{
			KeyID: mh.KeysetInfo().GetPrimaryKeyId(),
			// ciphertext was encrypted with a key that has TINK ouput prefix. This adds a 5 bytes prefix
			// to the ciphertext. This prefix is not included in `Log` call.
			NumBytes: len(ct) - cryptofmt.NonRawPrefixSize,
			Context:  monitoring.NewContext("aead", "decrypt", wantKeysetInfo),
		},
	}
	if cmp.Diff(got, want) != "" {
		t.Errorf("%v", cmp.Diff(got, want))
	}
}

func TestPrimitiveFactoryWithMonitoringAnnotationsLogsEncryptionDecryptionWithoutPrefix(t *testing.T) {
	defer internalregistry.ClearMonitoringClient()
	client := fakemonitoring.NewClient("fake-client")
	if err := internalregistry.RegisterMonitoringClient(client); err != nil {
		t.Fatalf("internalregistry.RegisterMonitoringClient() err = %v, want nil", err)
	}
	kh, err := keyset.NewHandle(aead.AES256GCMNoPrefixKeyTemplate())
	if err != nil {
		t.Fatalf("keyset.NewHandle() err = %v, want nil", err)
	}
	// Annotations are only supported throught the `insecurecleartextkeyset` API.
	buff := &bytes.Buffer{}
	if err := insecurecleartextkeyset.Write(kh, keyset.NewBinaryWriter(buff)); err != nil {
		t.Fatalf("insecurecleartextkeyset.Write() err = %v, want nil", err)
	}
	annotations := map[string]string{"foo": "bar"}
	mh, err := insecurecleartextkeyset.Read(keyset.NewBinaryReader(buff), keyset.WithAnnotations(annotations))
	if err != nil {
		t.Fatalf("insecurecleartextkeyset.Read() err = %v, want nil", err)
	}
	p, err := aead.New(mh)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	data := []byte("HELLO_WORLD")
	ct, err := p.Encrypt(data, nil)
	if err != nil {
		t.Fatalf("p.Encrypt() err = %v, want nil", err)
	}
	if _, err := p.Decrypt(ct, nil); err != nil {
		t.Fatalf("p.Decrypt() err = %v, want nil", err)
	}
	failures := client.Failures()
	if len(failures) != 0 {
		t.Errorf("len(client.Failures()) = %d, want = 0", len(failures))
	}
	got := client.Events()
	wantKeysetInfo := monitoring.NewKeysetInfo(
		annotations,
		kh.KeysetInfo().GetPrimaryKeyId(),
		[]*monitoring.Entry{
			{
				KeyID:     kh.KeysetInfo().GetPrimaryKeyId(),
				Status:    monitoring.Enabled,
				KeyType:   "tink.AesGcmKey",
				KeyPrefix: "RAW",
			},
		},
	)
	want := []*fakemonitoring.LogEvent{
		{
			KeyID:    mh.KeysetInfo().GetPrimaryKeyId(),
			NumBytes: len(data),
			Context:  monitoring.NewContext("aead", "encrypt", wantKeysetInfo),
		},
		{
			KeyID:    mh.KeysetInfo().GetPrimaryKeyId(),
			NumBytes: len(ct),
			Context:  monitoring.NewContext("aead", "decrypt", wantKeysetInfo),
		},
	}
	if cmp.Diff(got, want) != "" {
		t.Errorf("%v", cmp.Diff(got, want))
	}
}

func TestPrimitiveFactoryMonitoringWithAnnotatiosMultipleKeysLogsEncryptionDecryption(t *testing.T) {
	defer internalregistry.ClearMonitoringClient()
	client := fakemonitoring.NewClient("fake-client")
	if err := internalregistry.RegisterMonitoringClient(client); err != nil {
		t.Fatalf("registry.RegisterMonitoringClient() err = %v, want nil", err)
	}
	manager := keyset.NewManager()
	keyTemplates := []*tinkpb.KeyTemplate{
		aead.AES128GCMKeyTemplate(),
		aead.AES256GCMNoPrefixKeyTemplate(),
		aead.AES128CTRHMACSHA256KeyTemplate(),
		aead.XChaCha20Poly1305KeyTemplate(),
	}
	keyIDs := make([]uint32, len(keyTemplates), len(keyTemplates))
	var err error
	for i, kt := range keyTemplates {
		keyIDs[i], err = manager.Add(kt)
		if err != nil {
			t.Fatalf("manager.Add(%v) err = %v, want nil", kt, err)
		}
	}
	if err := manager.SetPrimary(keyIDs[1]); err != nil {
		t.Fatalf("manager.SetPrimary(%d) err = %v, want nil", keyIDs[1], err)
	}
	if err := manager.Disable(keyIDs[0]); err != nil {
		t.Fatalf("manager.Disable(%d) err = %v, want nil", keyIDs[0], err)
	}
	kh, err := manager.Handle()
	if err != nil {
		t.Fatalf("manager.Handle() err = %v, want nil", err)
	}
	// Annotations are only supported throught the `insecurecleartextkeyset` API.
	buff := &bytes.Buffer{}
	if err := insecurecleartextkeyset.Write(kh, keyset.NewBinaryWriter(buff)); err != nil {
		t.Fatalf("insecurecleartextkeyset.Write() err = %v, want nil", err)
	}
	annotations := map[string]string{"foo": "bar"}
	mh, err := insecurecleartextkeyset.Read(keyset.NewBinaryReader(buff), keyset.WithAnnotations(annotations))
	if err != nil {
		t.Fatalf("insecurecleartextkeyset.Read() err = %v, want nil", err)
	}
	p, err := aead.New(mh)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	failures := len(client.Failures())
	if failures != 0 {
		t.Errorf("len(client.Failures()) = %d, want 0", failures)
	}
	data := []byte("YELLOW_ORANGE")
	ct, err := p.Encrypt(data, nil)
	if err != nil {
		t.Fatalf("p.Encrypt() err = %v, want nil", err)
	}
	if _, err := p.Decrypt(ct, nil); err != nil {
		t.Fatalf("p.Decrypt() err = %v, want nil", err)
	}
	got := client.Events()
	wantKeysetInfo := monitoring.NewKeysetInfo(annotations, keyIDs[1], []*monitoring.Entry{
		{
			KeyID:     keyIDs[1],
			Status:    monitoring.Enabled,
			KeyType:   "tink.AesGcmKey",
			KeyPrefix: "RAW",
		},
		{
			KeyID:     keyIDs[2],
			Status:    monitoring.Enabled,
			KeyType:   "tink.AesCtrHmacAeadKey",
			KeyPrefix: "TINK",
		},
		{
			KeyID:     keyIDs[3],
			Status:    monitoring.Enabled,
			KeyType:   "tink.XChaCha20Poly1305Key",
			KeyPrefix: "TINK",
		},
	})
	want := []*fakemonitoring.LogEvent{
		{
			KeyID:    keyIDs[1],
			NumBytes: len(data),
			Context: monitoring.NewContext(
				"aead",
				"encrypt",
				wantKeysetInfo,
			),
		},
		{
			KeyID:    keyIDs[1],
			NumBytes: len(ct),
			Context: monitoring.NewContext(
				"aead",
				"decrypt",
				wantKeysetInfo,
			),
		},
	}
	// sort by keyID to avoid non deterministic order.
	entryLessFunc := func(a, b *monitoring.Entry) bool {
		return a.KeyID < b.KeyID
	}
	if !cmp.Equal(got, want, cmpopts.SortSlices(entryLessFunc)) {
		t.Errorf("got = %v, want = %v, with diff: %v", got, want, cmp.Diff(got, want))
	}
}

func TestPrimitiveFactoryWithMonitoringAnnotationsEncryptionFailureIsLogged(t *testing.T) {
	defer internalregistry.ClearMonitoringClient()
	client := &fakemonitoring.Client{Name: ""}
	if err := internalregistry.RegisterMonitoringClient(client); err != nil {
		t.Fatalf("internalregistry.RegisterMonitoringClient() err = %v, want nil", err)
	}
	typeURL := "TestFactoryWithMonitoringPrimitiveEncryptionFailureIsLogged"
	template := &tinkpb.KeyTemplate{
		TypeUrl:          typeURL,
		OutputPrefixType: tinkpb.OutputPrefixType_LEGACY,
	}
	km := &stubkeymanager.StubKeyManager{
		URL:  typeURL,
		Key:  &agpb.AesGcmKey{},
		Prim: &testutil.AlwaysFailingAead{Error: errors.New("failed")},
		KeyData: &tinkpb.KeyData{
			TypeUrl:         typeURL,
			KeyMaterialType: tinkpb.KeyData_SYMMETRIC,
			Value:           []byte("serialized_key"),
		},
	}
	if err := registry.RegisterKeyManager(km); err != nil {
		t.Fatalf("registry.RegisterKeyManager() err = %v, want nil", err)
	}
	kh, err := keyset.NewHandle(template)
	if err != nil {
		t.Fatalf("keyset.NewHandle() err = %v, want nil", err)
	}
	// Annotations are only supported throught the `insecurecleartextkeyset` API.
	buff := &bytes.Buffer{}
	if err := insecurecleartextkeyset.Write(kh, keyset.NewBinaryWriter(buff)); err != nil {
		t.Fatalf("insecurecleartextkeyset.Write() err = %v, want nil", err)
	}
	annotations := map[string]string{"foo": "bar"}
	mh, err := insecurecleartextkeyset.Read(keyset.NewBinaryReader(buff), keyset.WithAnnotations(annotations))
	if err != nil {
		t.Fatalf("insecurecleartextkeyset.Read() err = %v, want nil", err)
	}
	p, err := aead.New(mh)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	if _, err := p.Encrypt(nil, nil); err == nil {
		t.Fatalf("Encrypt() err = nil, want error")
	}
	got := client.Failures()
	want := []*fakemonitoring.LogFailure{
		{
			Context: monitoring.NewContext(
				"aead",
				"encrypt",
				monitoring.NewKeysetInfo(
					annotations,
					kh.KeysetInfo().GetPrimaryKeyId(),
					[]*monitoring.Entry{
						{
							KeyID:     kh.KeysetInfo().GetPrimaryKeyId(),
							Status:    monitoring.Enabled,
							KeyType:   typeURL,
							KeyPrefix: "LEGACY",
						},
					},
				),
			),
		},
	}
	if cmp.Diff(got, want) != "" {
		t.Errorf("%v", cmp.Diff(got, want))
	}
}

func TestPrimitiveFactoryWithMonitoringAnnotationsDecryptionFailureIsLogged(t *testing.T) {
	defer internalregistry.ClearMonitoringClient()
	client := fakemonitoring.NewClient("fake-client")
	if err := internalregistry.RegisterMonitoringClient(client); err != nil {
		t.Fatalf("internalregistry.RegisterMonitoringClient() err = %v, want nil", err)
	}
	kh, err := keyset.NewHandle(aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("keyset.NewHandle() err = %v, want nil", err)
	}
	// Annotations are only supported throught the `insecurecleartextkeyset` API.
	buff := &bytes.Buffer{}
	if err := insecurecleartextkeyset.Write(kh, keyset.NewBinaryWriter(buff)); err != nil {
		t.Fatalf("insecurecleartextkeyset.Write() err = %v, want nil", err)
	}
	annotations := map[string]string{"foo": "bar"}
	mh, err := insecurecleartextkeyset.Read(keyset.NewBinaryReader(buff), keyset.WithAnnotations(annotations))
	if err != nil {
		t.Fatalf("insecurecleartextkeyset.Read() err = %v, want nil", err)
	}
	p, err := aead.New(mh)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	if _, err := p.Decrypt([]byte("invalid_data"), nil); err == nil {
		t.Fatalf("Decrypt() err = nil, want error")
	}
	got := client.Failures()
	want := []*fakemonitoring.LogFailure{
		{
			Context: monitoring.NewContext(
				"aead",
				"decrypt",
				monitoring.NewKeysetInfo(
					annotations,
					kh.KeysetInfo().GetPrimaryKeyId(),
					[]*monitoring.Entry{
						{
							KeyID:     kh.KeysetInfo().GetPrimaryKeyId(),
							Status:    monitoring.Enabled,
							KeyType:   "tink.AesGcmKey",
							KeyPrefix: "TINK",
						},
					},
				),
			),
		},
	}
	if cmp.Diff(got, want) != "" {
		t.Errorf("%v", cmp.Diff(got, want))
	}
}

func TestFactoryWithMonitoringMultiplePrimitivesLogOperations(t *testing.T) {
	defer internalregistry.ClearMonitoringClient()
	client := &fakemonitoring.Client{Name: ""}
	if err := internalregistry.RegisterMonitoringClient(client); err != nil {
		t.Fatalf("internalregistry.RegisterMonitoringClient() err = %v, want nil", err)
	}
	kh1, err := keyset.NewHandle(aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("keyset.NewHandle() err = %v, want nil", err)
	}
	// Annotations are only supported throught the `insecurecleartextkeyset` API.
	buff := &bytes.Buffer{}
	if err := insecurecleartextkeyset.Write(kh1, keyset.NewBinaryWriter(buff)); err != nil {
		t.Fatalf("insecurecleartextkeyset.Write() err = %v, want nil", err)
	}
	annotations := map[string]string{"foo": "bar"}
	mh1, err := insecurecleartextkeyset.Read(keyset.NewBinaryReader(buff), keyset.WithAnnotations(annotations))
	if err != nil {
		t.Fatalf("insecurecleartextkeyset.Read() err = %v, want nil", err)
	}
	p1, err := aead.New(mh1)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	kh2, err := keyset.NewHandle(aead.AES128CTRHMACSHA256KeyTemplate())
	if err != nil {
		t.Fatalf("keyset.NewHandle() err = %v, want nil", err)
	}
	buff.Reset()
	if err := insecurecleartextkeyset.Write(kh2, keyset.NewBinaryWriter(buff)); err != nil {
		t.Fatalf("insecurecleartextkeyset.Write() err = %v, want nil", err)
	}
	mh2, err := insecurecleartextkeyset.Read(keyset.NewBinaryReader(buff), keyset.WithAnnotations(annotations))
	if err != nil {
		t.Fatalf("insecurecleartextkeyset.Read() err = %v, want nil", err)
	}
	p2, err := aead.New(mh2)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	d1 := []byte("YELLOW_ORANGE")
	if _, err := p1.Encrypt(d1, nil); err != nil {
		t.Fatalf("p1.Encrypt() err = %v, want nil", err)
	}
	d2 := []byte("ORANGE_BLUE")
	if _, err := p2.Encrypt(d2, nil); err != nil {
		t.Fatalf("p2.Encrypt() err = %v, want nil", err)
	}
	got := client.Events()
	want := []*fakemonitoring.LogEvent{
		{
			KeyID:    kh1.KeysetInfo().GetPrimaryKeyId(),
			NumBytes: len(d1),
			Context: monitoring.NewContext(
				"aead",
				"encrypt",
				monitoring.NewKeysetInfo(
					annotations,
					kh1.KeysetInfo().GetPrimaryKeyId(),
					[]*monitoring.Entry{
						{
							KeyID:     kh1.KeysetInfo().GetPrimaryKeyId(),
							Status:    monitoring.Enabled,
							KeyType:   "tink.AesGcmKey",
							KeyPrefix: "TINK",
						},
					},
				),
			),
		},
		{
			KeyID:    kh2.KeysetInfo().GetPrimaryKeyId(),
			NumBytes: len(d2),
			Context: monitoring.NewContext(
				"aead",
				"encrypt",
				monitoring.NewKeysetInfo(
					annotations,
					kh2.KeysetInfo().GetPrimaryKeyId(),
					[]*monitoring.Entry{
						{
							KeyID:     kh2.KeysetInfo().GetPrimaryKeyId(),
							Status:    monitoring.Enabled,
							KeyType:   "tink.AesCtrHmacAeadKey",
							KeyPrefix: "TINK",
						},
					},
				),
			),
		},
	}
	if !cmp.Equal(got, want) {
		t.Errorf("got = %v, want = %v, with diff: %v", got, want, cmp.Diff(got, want))
	}
}

func TestPrimitiveFactoryEncryptDecryptWithoutAnnotationsDoesNothing(t *testing.T) {
	defer internalregistry.ClearMonitoringClient()
	client := fakemonitoring.NewClient("fake-client")
	if err := internalregistry.RegisterMonitoringClient(client); err != nil {
		t.Fatalf("internalregistry.RegisterMonitoringClient() err = %v, want nil", err)
	}
	kh, err := keyset.NewHandle(aead.AES128GCMKeyTemplate())
	if err != nil {
		t.Fatalf("keyset.NewHandle() err = %v, want nil", err)
	}
	p, err := aead.New(kh)
	if err != nil {
		t.Fatalf("aead.New() err = %v, want nil", err)
	}
	data := []byte("YELLOW_ORANGE")
	ct, err := p.Encrypt(data, nil)
	if err != nil {
		t.Fatalf("p.Encrypt() err = %v, want nil", err)
	}
	if _, err := p.Decrypt(ct, nil); err != nil {
		t.Fatalf("p.Decrypt() err = %v, want nil", err)
	}
	got := client.Events()
	if len(got) != 0 {
		t.Errorf("len(client.Events()) = %d, want 0", len(got))
	}
	failures := len(client.Failures())
	if failures != 0 {
		t.Errorf("len(client.Failures()) = %d, want 0", failures)
	}
}
