// Copyright 2020 Google LLC
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

package subtle_test

import (
	"bytes"
	"encoding/hex"
	"fmt"
	"math/rand"
	"testing"

	"golang.org/x/crypto/chacha20poly1305"
	"github.com/google/tink/go/aead/subtle"
	"github.com/google/tink/go/subtle/random"
	"github.com/google/tink/go/testutil"
)

func TestXChaCha20Poly1305EncryptDecrypt(t *testing.T) {
	for i, test := range xChaCha20Poly1305Tests {
		key, err := hex.DecodeString(test.key)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.key) err = %q, want nil", err)
		}
		pt, err := hex.DecodeString(test.plaintext)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.plaintext) err = %q, want nil", err)
		}
		aad, err := hex.DecodeString(test.aad)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.aad) err = %q, want nil", err)
		}
		nonce, err := hex.DecodeString(test.nonce)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.nonce) err = %q, want nil", err)
		}
		out, err := hex.DecodeString(test.out)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.out) err = %q, want nil", err)
		}
		tag, err := hex.DecodeString(test.tag)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.tag) err = %q, want nil", err)
		}

		x, err := subtle.NewXChaCha20Poly1305(key)
		if err != nil {
			t.Errorf("#%d, cannot create new instance of XChaCha20Poly1305: %s", i, err)
			continue
		}

		_, err = x.Encrypt(pt, aad)
		if err != nil {
			t.Errorf("#%d, unexpected encryption error: %s", i, err)
			continue
		}

		var combinedCt []byte
		combinedCt = append(combinedCt, nonce...)
		combinedCt = append(combinedCt, out...)
		combinedCt = append(combinedCt, tag...)
		if got, err := x.Decrypt(combinedCt, aad); err != nil {
			t.Errorf("#%d, unexpected decryption error: %s", i, err)
			continue
		} else if !bytes.Equal(pt, got) {
			t.Errorf("#%d, plaintext's don't match: got %x vs %x", i, got, pt)
			continue
		}
	}
}

func TestXChaCha20Poly1305EmptyAssociatedData(t *testing.T) {
	key := random.GetRandomBytes(chacha20poly1305.KeySize)
	aad := []byte{}
	badAad := []byte{1, 2, 3}

	x, err := subtle.NewXChaCha20Poly1305(key)
	if err != nil {
		t.Fatal(err)
	}

	for i := 0; i < 75; i++ {
		pt := random.GetRandomBytes(uint32(i))
		// Encrpting with aad as a 0-length array
		{
			ct, err := x.Encrypt(pt, aad)
			if err != nil {
				t.Errorf("Encrypt(%x, %x) failed", pt, aad)
				continue
			}

			if got, err := x.Decrypt(ct, aad); err != nil || !bytes.Equal(pt, got) {
				t.Errorf("Decrypt(Encrypt(pt, %x)): plaintext's don't match: got %x vs %x", aad, got, pt)
			}
			if got, err := x.Decrypt(ct, nil); err != nil || !bytes.Equal(pt, got) {
				t.Errorf("Decrypt(Encrypt(pt, nil)): plaintext's don't match: got %x vs %x", got, pt)
			}
			if _, err := x.Decrypt(ct, badAad); err == nil {
				t.Errorf("Decrypt(Encrypt(pt, %x)) = _, nil; want: _, err", badAad)
			}
		}
		// Encrpting with aad equal to null
		{
			ct, err := x.Encrypt(pt, nil)
			if err != nil {
				t.Errorf("Encrypt(%x, nil) failed", pt)
			}

			if got, err := x.Decrypt(ct, aad); err != nil || !bytes.Equal(pt, got) {
				t.Errorf("Decrypt(Encrypt(pt, %x)): plaintext's don't match: got %x vs %x; error: %v", aad, got, pt, err)
			}
			if got, err := x.Decrypt(ct, nil); err != nil || !bytes.Equal(pt, got) {
				t.Errorf("Decrypt(Encrypt(pt, nil)): plaintext's don't match: got %x vs %x; error: %v", got, pt, err)
			}
			if _, err := x.Decrypt(ct, badAad); err == nil {
				t.Errorf("Decrypt(Encrypt(pt, %x)) = _, nil; want: _, err", badAad)
			}
		}
	}
}

func TestXChaCha20Poly1305LongMessages(t *testing.T) {
	dataSize := uint32(16)
	// Encrypts and decrypts messages of size <= 8192.
	for dataSize <= 1<<24 {
		pt := random.GetRandomBytes(dataSize)
		aad := random.GetRandomBytes(dataSize / 3)
		key := random.GetRandomBytes(chacha20poly1305.KeySize)

		x, err := subtle.NewXChaCha20Poly1305(key)
		if err != nil {
			t.Fatal(err)
		}

		ct, err := x.Encrypt(pt, aad)
		if err != nil {
			t.Errorf("Encrypt(%x, %x) failed", pt, aad)
			continue
		}

		if got, err := x.Decrypt(ct, aad); err != nil || !bytes.Equal(pt, got) {
			t.Errorf("Decrypt(Encrypt(pt, %x)): plaintext's don't match: got %x vs %x; error: %v", aad, got, pt, err)
		}

		dataSize += 5 * dataSize / 11
	}
}

func TestXChaCha20Poly1305ModifyCiphertext(t *testing.T) {
	for i, test := range xChaCha20Poly1305Tests {
		key, err := hex.DecodeString(test.key)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.key) err = %q, want nil", err)
		}
		pt, err := hex.DecodeString(test.plaintext)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.plaintext) err = %q, want nil", err)
		}
		aad, err := hex.DecodeString(test.aad)
		if err != nil {
			t.Fatalf("hex.DecodeString(test.aad) err = %q, want nil", err)
		}

		x, err := subtle.NewXChaCha20Poly1305(key)
		if err != nil {
			t.Fatal(err)
		}

		ct, err := x.Encrypt(pt, aad)
		if err != nil {
			t.Errorf("#%d: Encrypt failed", i)
			continue
		}

		if len(aad) > 0 {
			alterAadIdx := rand.Intn(len(aad))
			aad[alterAadIdx] ^= 0x80
			if _, err := x.Decrypt(ct, aad); err == nil {
				t.Errorf("#%d: Decrypt was successful after altering additional data", i)
				continue
			}
			aad[alterAadIdx] ^= 0x80
		}

		alterCtIdx := rand.Intn(len(ct))
		ct[alterCtIdx] ^= 0x80
		if _, err := x.Decrypt(ct, aad); err == nil {
			t.Errorf("#%d: Decrypt was successful after altering ciphertext", i)
			continue
		}
		ct[alterCtIdx] ^= 0x80
	}
}

// This is a very simple test for the randomness of the nonce.
// The test simply checks that the multiple ciphertexts of the same message are distinct.
func TestXChaCha20Poly1305RandomNonce(t *testing.T) {
	key := random.GetRandomBytes(chacha20poly1305.KeySize)
	x, err := subtle.NewXChaCha20Poly1305(key)
	if err != nil {
		t.Fatal(err)
	}

	cts := make(map[string]bool)
	pt, aad := []byte{}, []byte{}
	for i := 0; i < 1<<10; i++ {
		ct, err := x.Encrypt(pt, aad)
		ctHex := hex.EncodeToString(ct)
		if err != nil || cts[ctHex] {
			t.Errorf("TestRandomNonce failed: %v", err)
		} else {
			cts[ctHex] = true
		}
	}
}

func TestXChaCha20Poly1305WycheproofCases(t *testing.T) {
	testutil.SkipTestIfTestSrcDirIsNotSet(t)
	suite := new(AEADSuite)
	if err := testutil.PopulateSuite(suite, "xchacha20_poly1305_test.json"); err != nil {
		t.Fatalf("failed populating suite: %s", err)
	}
	for _, group := range suite.TestGroups {
		if group.KeySize/8 != chacha20poly1305.KeySize {
			continue
		}
		if group.IvSize/8 != chacha20poly1305.NonceSizeX {
			continue
		}
		for _, test := range group.Tests {
			caseName := fmt.Sprintf("%s-%s:Case-%d", suite.Algorithm, group.Type, test.CaseID)
			t.Run(caseName, func(t *testing.T) { runXChaCha20Poly1305WycheproofCase(t, test) })
		}
	}
}

func runXChaCha20Poly1305WycheproofCase(t *testing.T, tc *AEADCase) {
	var combinedCt []byte
	combinedCt = append(combinedCt, tc.Iv...)
	combinedCt = append(combinedCt, tc.Ct...)
	combinedCt = append(combinedCt, tc.Tag...)

	ca, err := subtle.NewXChaCha20Poly1305(tc.Key)
	if err != nil {
		t.Fatalf("cannot create new instance of ChaCha20Poly1305: %s", err)
	}

	_, err = ca.Encrypt(tc.Msg, tc.Aad)
	if err != nil {
		t.Fatalf("unexpected encryption error: %s", err)
	}

	decrypted, err := ca.Decrypt(combinedCt, tc.Aad)
	if err != nil {
		if tc.Result == "valid" {
			t.Errorf("unexpected error: %s", err)
		}
	} else {
		if tc.Result == "invalid" {
			t.Error("decrypted invalid")
		}
		if !bytes.Equal(decrypted, tc.Msg) {
			t.Error("incorrect decryption")
		}
	}
}

func TestPreallocatedCiphertextMemoryInXChaCha20Poly1305IsExact(t *testing.T) {
	key := random.GetRandomBytes(chacha20poly1305.KeySize)
	a, err := subtle.NewXChaCha20Poly1305(key)
	if err != nil {
		t.Fatalf("aead.NewAESGCMInsecureIV() err = %v, want nil", err)
	}
	plaintext := random.GetRandomBytes(13)
	associatedData := random.GetRandomBytes(17)

	ciphertext, err := a.Encrypt(plaintext, associatedData)
	if err != nil {
		t.Fatalf("a.Encrypt() err = %v, want nil", err)
	}
	// Encrypt() uses cipher.Overhead() to pre-allocate the memory needed store the ciphertext.
	// For ChaCha20Poly1305, the size of the allocated memory should always be exact. If this check
	// fails, the pre-allocated memory was too large or too small. If it was too small, the system had
	// to re-allocate more memory, which is expensive and should be avoided.
	if len(ciphertext) != cap(ciphertext) {
		t.Errorf("want len(ciphertext) == cap(ciphertext), got %d != %d", len(ciphertext), cap(ciphertext))
	}
}
