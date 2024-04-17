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

package jwt_test

import (
	"testing"
	"time"

	"github.com/google/tink/go/jwt"
	"github.com/google/tink/go/keyset"

	tinkpb "github.com/google/tink/go/proto/tink_go_proto"
)

type templateTestCase struct {
	tag      string
	template *tinkpb.KeyTemplate
}

func TestJWTComputeVerifyMAC(t *testing.T) {
	expiresAt := time.Now().Add(time.Hour)
	rawJWT, err := jwt.NewRawJWT(&jwt.RawJWTOptions{
		ExpiresAt: &expiresAt,
	})
	if err != nil {
		t.Errorf("NewRawJWT() err = %v, want nil", err)
	}
	for _, tc := range []templateTestCase{
		{tag: "JWT_HS256", template: jwt.HS256Template()},
		{tag: "JWT_HS384", template: jwt.HS384Template()},
		{tag: "JWT_HS512", template: jwt.HS512Template()},
		{tag: "JWT_HS256_RAW", template: jwt.RawHS256Template()},
		{tag: "JWT_HS384_RAW", template: jwt.RawHS384Template()},
		{tag: "JWT_HS512_RAW", template: jwt.RawHS512Template()},
	} {
		t.Run(tc.tag, func(t *testing.T) {
			handle, err := keyset.NewHandle(tc.template)
			if err != nil {
				t.Errorf("keyset.NewHandle() err = %v, want nil", err)
			}
			m, err := jwt.NewMAC(handle)
			if err != nil {
				t.Errorf("jwt.NewMAC() err = %v, want nil", err)
			}
			compact, err := m.ComputeMACAndEncode(rawJWT)
			if err != nil {
				t.Errorf("m.ComputeMACAndEncode() err = %v, want nil", err)
			}
			validator, err := jwt.NewValidator(&jwt.ValidatorOpts{})
			if err != nil {
				t.Errorf("jwt.NewValidator() err = %v, want nil", err)
			}
			if _, err := m.VerifyMACAndDecode(compact, validator); err != nil {
				t.Errorf("m.VerifyMACAndDecode() err = %v, want nil", err)
			}

			// In two hours, VerifyMACAndDecode should fail with an expiration error.
			inTwoHours := time.Now().Add(time.Hour * 2)
			futureValidator, err := jwt.NewValidator(&jwt.ValidatorOpts{FixedNow: inTwoHours})
			if err != nil {
				t.Errorf("jwt.NewValidator() err = %v, want nil", err)
			}
			_, futureError := m.VerifyMACAndDecode(compact, futureValidator)
			if futureError == nil {
				t.Errorf("m.VerifyMACAndDecode(compact, futureValidator) err = nil, want error")
			}
			if !jwt.IsExpirationErr(futureError) {
				t.Errorf("jwt.IsExpirationErr(futureError) = false, want true")
			}
		})
	}
}

func TestJWTSignVerify(t *testing.T) {
	expiresAt := time.Now().Add(time.Hour)
	rawJWT, err := jwt.NewRawJWT(&jwt.RawJWTOptions{
		ExpiresAt: &expiresAt,
	})
	if err != nil {
		t.Errorf("jwt.NewRawJWT() err = %v, want nil", err)
	}
	for _, tc := range []templateTestCase{
		{tag: "JWT_ES256", template: jwt.ES256Template()},
		{tag: "JWT_ES384", template: jwt.ES384Template()},
		{tag: "JWT_ES512", template: jwt.ES512Template()},
		{tag: "JWT_ES256_RAW", template: jwt.RawES256Template()},
		{tag: "JWT_ES384_RAW", template: jwt.RawES384Template()},
		{tag: "JWT_ES512_RAW", template: jwt.RawES512Template()},
		{tag: "JWT_RS256_2048_R4", template: jwt.RS256_2048_F4_Key_Template()},
		{tag: "JWT_RS256_2048_R4_RAW", template: jwt.RawRS256_2048_F4_Key_Template()},
		{tag: "JWT_RS256_3072_R4", template: jwt.RS256_3072_F4_Key_Template()},
		{tag: "JWT_RS256_3072_R4_RAW", template: jwt.RawRS256_3072_F4_Key_Template()},
		{tag: "JWT_RS384_3072_R4", template: jwt.RS384_3072_F4_Key_Template()},
		{tag: "JWT_RS384_3072_R4_RAW", template: jwt.RawRS384_3072_F4_Key_Template()},
		{tag: "JWT_RS512_4096_R4", template: jwt.RS512_4096_F4_Key_Template()},
		{tag: "JWT_RS512_4096_R4_RAW", template: jwt.RawRS384_3072_F4_Key_Template()},
		{tag: "JWT_PS256_2048_R4", template: jwt.PS256_2048_F4_Key_Template()},
		{tag: "JWT_PS256_2048_R4_RAW", template: jwt.RawPS256_2048_F4_Key_Template()},
		{tag: "JWT_PS256_3072_R4", template: jwt.PS256_3072_F4_Key_Template()},
		{tag: "JWT_PS256_3072_R4_RAW", template: jwt.RawPS256_3072_F4_Key_Template()},
		{tag: "JWT_PS384_3072_R4", template: jwt.PS384_3072_F4_Key_Template()},
		{tag: "JWT_PS384_3072_R4_RAW", template: jwt.RawPS384_3072_F4_Key_Template()},
		{tag: "JWT_PS512_4096_R4", template: jwt.PS512_4096_F4_Key_Template()},
		{tag: "JWT_PS512_4096_R4_RAW", template: jwt.RawPS384_3072_F4_Key_Template()},
	} {
		t.Run(tc.tag, func(t *testing.T) {
			kh, err := keyset.NewHandle(tc.template)
			if err != nil {
				t.Errorf("keyset.NewHandle() err = %v, want nil", err)
			}
			signer, err := jwt.NewSigner(kh)
			if err != nil {
				t.Errorf("jwt.NewSigner() err = %v, want nil", err)
			}
			compact, err := signer.SignAndEncode(rawJWT)
			if err != nil {
				t.Errorf("signer.SignAndEncode() err = %v, want nil", err)
			}
			pubkh, err := kh.Public()
			if err != nil {
				t.Fatalf("key handle Public() err = %v, want nil", err)
			}
			verifier, err := jwt.NewVerifier(pubkh)
			if err != nil {
				t.Fatalf("jwt.NewVerifier() err = %v, want nil", err)
			}
			validator, err := jwt.NewValidator(&jwt.ValidatorOpts{})
			if err != nil {
				t.Fatalf("jwt.NewValidator() err = %v, want nil", err)
			}
			if _, err := verifier.VerifyAndDecode(compact, validator); err != nil {
				t.Errorf("verifier.VerifyAndDecode() err = %v, want nil", err)
			}

			// In two hours, VerifyAndDecode should fail with an expiration error.
			inTwoHours := time.Now().Add(time.Hour * 2)
			futureValidator, err := jwt.NewValidator(&jwt.ValidatorOpts{FixedNow: inTwoHours})
			if err != nil {
				t.Errorf("jwt.NewValidator() err = %v, want nil", err)
			}
			_, futureError := verifier.VerifyAndDecode(compact, futureValidator)
			if futureError == nil {
				t.Errorf("verifier.VerifyAndDecode(compact, futureValidator) err = nil, want error")
			}
			if !jwt.IsExpirationErr(futureError) {
				t.Errorf("jwt.IsExpirationErr(expirationError) = false, want true")
			}
		})
	}
}
