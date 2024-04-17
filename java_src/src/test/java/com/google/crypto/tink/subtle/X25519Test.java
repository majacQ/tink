// Copyright 2017 Google Inc.
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

package com.google.crypto.tink.subtle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.crypto.tink.testing.WycheproofTestUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link X25519}. */
@RunWith(JUnit4.class)
public final class X25519Test {
  /** Iteration test in Section 5.2 of RFC 7748. https://tools.ietf.org/html/rfc7748 */
  @Test
  public void testComputeSharedSecretWithRfcIteration() throws Exception {
    byte[] k = new byte[32];
    k[0] = 9;
    byte[] prevK = k;
    k = X25519.computeSharedSecret(k, prevK);
    assertEquals("422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079", Hex.encode(k));
    for (int i = 0; i < 999; i++) {
      byte[] tmp = k;
      k = X25519.computeSharedSecret(k, prevK);
      prevK = tmp;
    }
    assertEquals("684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51", Hex.encode(k));
    // Omitting 1M iteration to limit the test runtime.
  }

  /**
   * Tests against the test vectors in Section 6.1 of RFC 7748. https://tools.ietf.org/html/rfc7748
   */
  @Test
  public void testPublicFromPrivateWithRfcTestVectors() throws Exception {
    byte[] out =
        X25519.publicFromPrivate(
            Hex.decode("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"));
    assertEquals(
        "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", Hex.encode(out));

    out =
        X25519.publicFromPrivate(
            Hex.decode("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"));
    assertEquals(
        "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f", Hex.encode(out));
  }

  @Test
  public void testGeneratePrivateKeyReturnsIntentionallyMalformedKeys() {
    byte[] privateKey = X25519.generatePrivateKey();
    assertEquals(7, privateKey[0] & 7);
    assertEquals(128, privateKey[31] & 192);
  }

  private static void x25519Helper(int privateKeyLen, int peersPublicValueLen)
      throws GeneralSecurityException {
    byte[] privateKey = new byte[privateKeyLen];
    byte[] base = new byte[peersPublicValueLen];
    base[0] = 9;
    try {
      X25519.computeSharedSecret(privateKey, base);
      fail("Expected InvalidKeyException");
    } catch (InvalidKeyException expected) {
    }
  }

  @Test
  public void testX25519ThrowsIllegalArgExceptionWhenPrivateKeySizeIsLessThan32Bytes()
      throws Exception {
    x25519Helper(31, 32);
  }

  @Test
  public void testX25519ThrowsIllegalArgExceptionWhenPrivateKeySizeIsGreaterThan32Bytes()
      throws Exception {
    x25519Helper(33, 32);
  }

  @Test
  public void testX25519ThrowsIllegalArgExceptionWhenPeersPublicValueIsLessThan32Bytes()
      throws Exception {
    x25519Helper(32, 31);
  }

  @Test
  public void testX25519ThrowsIllegalArgExceptionWhenPeersPublicValueIsGreaterThan32Bytes()
      throws Exception {
    x25519Helper(32, 33);
  }

  private static void publicFromPrivateHelper(int privateKeyLen) {
    byte[] privateKey = new byte[privateKeyLen];
    try {
      X25519.publicFromPrivate(privateKey);
      fail("Expected InvalidKeyException");
    } catch (InvalidKeyException expected) {
    }
  }

  @Test
  public void testX25519PublicFromPrivateThrowsIllegalArgExWhenPrivateKeyIsLessThan32Bytes() {
    publicFromPrivateHelper(31);
  }

  @Test
  public void testX25519PublicFromPrivateThrowsIllegalArgExWhenPrivateKeyIsGreaterThan32Bytes() {
    publicFromPrivateHelper(33);
  }

  @Test
  public void testComputeSharedSecretWithWycheproofVectors() throws Exception {
    JsonObject json =
        WycheproofTestUtil.readJson("../wycheproof/testvectors/x25519_test.json");
    int errors = 0;
    int cntSkippedTests = 0;
    JsonArray testGroups = json.getAsJsonArray("testGroups");
    for (int i = 0; i < testGroups.size(); i++) {
      JsonObject group = testGroups.get(i).getAsJsonObject();
      JsonArray tests = group.getAsJsonArray("tests");
      String curve = group.get("curve").getAsString();
      for (int j = 0; j < tests.size(); j++) {
        JsonObject testcase = tests.get(j).getAsJsonObject();
        String tcId =
            String.format("testcase %d (%s)",
                testcase.get("tcId").getAsInt(), testcase.get("comment").getAsString());
        String result = testcase.get("result").getAsString();
        String hexPubKey = testcase.get("public").getAsString();
        String hexPrivKey = testcase.get("private").getAsString();
        String expectedSharedSecret = testcase.get("shared").getAsString();
        if (!curve.equals("curve25519")) {
          System.out.printf("Skipping %s, unknown curve name: %s", tcId, curve);
          cntSkippedTests++;
          continue;
        }
        try {
          String sharedSecret =
              Hex.encode(X25519.computeSharedSecret(Hex.decode(hexPrivKey), Hex.decode(hexPubKey)));
          if (result.equals("invalid")) {
            System.out.printf(
                "FAIL %s: accepting invalid parameters, shared secret: %s%n", tcId, sharedSecret);
            errors++;
          } else if (!expectedSharedSecret.equals(sharedSecret)) {
            System.out.printf(
                "FAIL %s: incorrect shared secret, computed: %s, expected: %s%n",
                tcId, sharedSecret, expectedSharedSecret);
            errors++;
          }
        } catch (GeneralSecurityException ex) {
          if (result.equals("valid")) {
            System.out.printf("FAIL %s, exception %s%n", tcId, ex);
            errors++;
          }
        } catch (Exception ex) {
          // Other exceptions typically indicate that something is wrong with the implementation.
          System.out.printf("FAIL %s, exception %s%n", tcId, ex);
          errors++;
        }
      }
    }
    System.out.printf("Number of tests skipped: %d", cntSkippedTests);
    assertEquals(0, errors);
  }
}
