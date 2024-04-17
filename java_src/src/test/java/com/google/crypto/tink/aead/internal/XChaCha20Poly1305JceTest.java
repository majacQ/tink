// Copyright 2018 Google Inc.
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

package com.google.crypto.tink.aead.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.aead.XChaCha20Poly1305Key;
import com.google.crypto.tink.aead.XChaCha20Poly1305Parameters;
import com.google.crypto.tink.config.TinkFips;
import com.google.crypto.tink.internal.Util;
import com.google.crypto.tink.subtle.Bytes;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.subtle.Random;
import com.google.crypto.tink.testing.TestUtil;
import com.google.crypto.tink.util.SecretBytes;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashSet;
import javax.crypto.AEADBadTagException;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for XChaCha20Poly1305Jce. */
@RunWith(JUnit4.class)
public class XChaCha20Poly1305JceTest {

  private static final int KEY_SIZE = 32;

  private static class XChaCha20Poly1305TestVector {
    public byte[] key;
    public byte[] nonce;
    public byte[] plaintext;
    public byte[] aad;
    public byte[] ciphertext;
    public byte[] tag;

    public XChaCha20Poly1305TestVector(
        String key, String nonce, String plaintext, String aad, String ciphertext, String tag) {
      this.key = Hex.decode(key);
      this.nonce = Hex.decode(nonce);
      this.plaintext = Hex.decode(plaintext);
      this.aad = Hex.decode(aad);
      this.ciphertext = Hex.decode(ciphertext);
      this.tag = Hex.decode(tag);
    }
  }

  private static final XChaCha20Poly1305TestVector[] xChaCha20Poly1305TestVectors = {
    // From libsodium's test/default/aead_xchacha20poly1305.c
    // see test/default/aead_xchacha20poly1305.exp for ciphertext values.
    new XChaCha20Poly1305TestVector(
        "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
        "07000000404142434445464748494a4b0000000000000000",
        "4c616469657320616e642047656e746c656d656e206f662074686520636c617373206f66202739393a20496620"
            + "4920636f756c64206f6666657220796f75206f6e6c79206f6e652074697020666f722074686520667574"
            + "7572652c2073756e73637265656e20776f756c642062652069742e",
        "50515253c0c1c2c3c4c5c6c7",
        "453c0693a7407f04ff4c56aedb17a3c0a1afff01174930fc22287c33dbcf0ac8b89ad929530a1bb3ab5e69f24c"
            + "7f6070c8f840c9abb4f69fbfc8a7ff5126faeebbb55805ee9c1cf2ce5a57263287aec5780f04ec324c35"
            + "14122cfc3231fc1a8b718a62863730a2702bb76366116bed09e0fd",
        "5c6d84b6b0c1abaf249d5dd0f7f5a7ea"),
    new XChaCha20Poly1305TestVector(
        "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
        "07000000404142434445464748494a4b0000000000000000",
        "4c616469657320616e642047656e746c656d656e206f662074686520636c617373206f66202739393a20496620"
            + "4920636f756c64206f6666657220796f75206f6e6c79206f6e652074697020666f722074686520667574"
            + "7572652c2073756e73637265656e20776f756c642062652069742e",
        "" /* empty aad */,
        "453c0693a7407f04ff4c56aedb17a3c0a1afff01174930fc22287c33dbcf0ac8b89ad929530a1bb3ab5e69f24c"
            + "7f6070c8f840c9abb4f69fbfc8a7ff5126faeebbb55805ee9c1cf2ce5a57263287aec5780f04ec324c35"
            + "14122cfc3231fc1a8b718a62863730a2702bb76366116bed09e0fd",
        "d4c860b7074be894fac9697399be5cc1"),
    // From  https://tools.ietf.org/html/draft-arciszewski-xchacha-01.
    new XChaCha20Poly1305TestVector(
        "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f",
        "404142434445464748494a4b4c4d4e4f5051525354555657",
        "4c616469657320616e642047656e746c656d656e206f662074686520636c617373206f66202739393a20496620"
            + "4920636f756c64206f6666657220796f75206f6e6c79206f6e652074697020666f722074686520667574"
            + "7572652c2073756e73637265656e20776f756c642062652069742e",
        "50515253c0c1c2c3c4c5c6c7",
        "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb731c7f1b0b4aa6440bf3a82f4e"
            + "da7e39ae64c6708c54c216cb96b72e1213b4522f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc36948"
            + "8f76b2383565d3fff921f9664c97637da9768812f615c68b13b52e",
        "c0875924c1c7987947deafd8780acf49")
  };

  @Test
  public void testXChaCha20Poly1305TestVectors() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    for (XChaCha20Poly1305TestVector test : xChaCha20Poly1305TestVectors) {
      Aead cipher =
          XChaCha20Poly1305Jce.create(
              XChaCha20Poly1305Key.create(
                  SecretBytes.copyFrom(test.key, InsecureSecretKeyAccess.get())));
      byte[] message =
          cipher.decrypt(Bytes.concat(test.nonce, test.ciphertext, test.tag), test.aad);
      assertThat(message).isEqualTo(test.plaintext);
    }
  }

  public Aead createInstance(byte[] key) throws GeneralSecurityException {
    return XChaCha20Poly1305Jce.create(
        XChaCha20Poly1305Key.create(SecretBytes.copyFrom(key, InsecureSecretKeyAccess.get())));
  }

  @Test
  public void throwsIllegalArgExpWhenKeyLenIsGreaterThan32() throws GeneralSecurityException {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    assertThrows(GeneralSecurityException.class, () -> createInstance(new byte[KEY_SIZE + 1]));
  }

  @Test
  public void testSnufflePoly1305ThrowsIllegalArgExpWhenKeyLenIsLessThan32()
      throws GeneralSecurityException {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    assertThrows(GeneralSecurityException.class, () -> createInstance(new byte[KEY_SIZE - 1]));
  }

  @Test
  public void testDecryptThrowsGeneralSecurityExpWhenCiphertextIsTooShort()
      throws GeneralSecurityException {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    Aead cipher = createInstance(new byte[KEY_SIZE]);
    GeneralSecurityException e =
        assertThrows(
            GeneralSecurityException.class, () -> cipher.decrypt(new byte[27], new byte[1]));
    assertThat(e).hasMessageThat().containsMatch("ciphertext too short");
  }

  @Test
  public void testEncryptDecrypt() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    Aead aead = createInstance(Random.randBytes(KEY_SIZE));
    for (int i = 0; i < 100; i++) {
      byte[] message = Random.randBytes(i);
      byte[] aad = Random.randBytes(i);
      byte[] ciphertext = aead.encrypt(message, aad);
      byte[] decrypted = aead.decrypt(ciphertext, aad);
      assertArrayEquals(message, decrypted);
    }
  }

  @Test
  public void testNullPlaintextOrCiphertext() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    Aead aead = createInstance(Random.randBytes(KEY_SIZE));
    byte[] aad = new byte[] {1, 2, 3};
    assertThrows(
        NullPointerException.class,
        () -> {
          byte[] unused = aead.encrypt(null, aad);
        });
    assertThrows(
        NullPointerException.class,
        () -> {
          byte[] unused = aead.encrypt(null, null);
        });
    assertThrows(
        NullPointerException.class,
        () -> {
          byte[] unused = aead.decrypt(null, aad);
        });
    assertThrows(
        NullPointerException.class,
        () -> {
          byte[] unused = aead.decrypt(null, null);
        });
  }

  @Test
  public void testLongMessages() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    if (TestUtil.isAndroid() || TestUtil.isTsan()) {
      System.out.println("testLongMessages doesn't work on Android and under tsan, skipping");
      return;
    }
    int dataSize = 16;
    while (dataSize <= (1 << 24)) {
      byte[] plaintext = Random.randBytes(dataSize);
      byte[] aad = Random.randBytes(dataSize / 3);
      byte[] key = Random.randBytes(KEY_SIZE);
      Aead aead = createInstance(key);
      byte[] ciphertext = aead.encrypt(plaintext, aad);
      byte[] decrypted = aead.decrypt(ciphertext, aad);
      assertArrayEquals(plaintext, decrypted);
      dataSize += 5 * dataSize / 11;
    }
  }

  @Test
  public void testModifyCiphertext() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    byte[] key = Random.randBytes(KEY_SIZE);
    Aead aead = createInstance(key);
    byte[] aad = Random.randBytes(16);
    byte[] message = Random.randBytes(32);
    byte[] ciphertext = aead.encrypt(message, aad);

    // Flipping bits
    for (int b = 0; b < ciphertext.length; b++) {
      for (int bit = 0; bit < 8; bit++) {
        byte[] modified = Arrays.copyOf(ciphertext, ciphertext.length);
        modified[b] ^= (byte) (1 << bit);
        assertThrows(
            AEADBadTagException.class,
            () -> {
              byte[] unused = aead.decrypt(modified, aad);
            });
      }
    }

    // Truncate the message.
    for (int length = 0; length < ciphertext.length; length++) {
      byte[] modified = Arrays.copyOf(ciphertext, length);
      assertThrows(
          GeneralSecurityException.class,
          () -> {
            byte[] unused = aead.decrypt(modified, aad);
          });
    }

    // Modify AAD
    for (int b = 0; b < aad.length; b++) {
      for (int bit = 0; bit < 8; bit++) {
        byte[] modified = Arrays.copyOf(aad, aad.length);
        modified[b] ^= (byte) (1 << bit);
        assertThrows(
            AEADBadTagException.class,
            () -> {
              byte[] unused = aead.decrypt(ciphertext, modified);
            });
      }
    }
  }

  /**
   * This is a very simple test for the randomness of the nonce. The test simply checks that the
   * multiple ciphertexts of the same message are distinct.
   */
  @Test
  public void testRandomNonce() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    if (TestUtil.isTsan()) {
      System.out.println("testRandomNonce takes too long under tsan, skipping");
      return;
    }
    byte[] key = Random.randBytes(KEY_SIZE);
    Aead aead = createInstance(key);
    byte[] message = new byte[0];
    byte[] aad = new byte[0];
    HashSet<String> ciphertexts = new HashSet<>();
    final int samples = 1 << 17;
    for (int i = 0; i < samples; i++) {
      byte[] ct = aead.encrypt(message, aad);
      String ctHex = Hex.encode(ct);
      assertFalse(ciphertexts.contains(ctHex));
      ciphertexts.add(ctHex);
    }
    assertEquals(samples, ciphertexts.size());
  }

  /**
   * Test case taken from Wycheproof (testcase 5)
   * https://github.com/google/wycheproof/blob/b063b4aedae951c69df014cd25fa6d69ae9e8cb9/testvectors/xchacha20_poly1305_test.json#L69
   */
  @Test
  public void testWithXChaCha20Poly1305Key_noPrefix_works() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    byte[] plaintext = Hex.decode("e1");
    byte[] associatedData = Hex.decode("6384f4714ff18c18");
    byte[] keyBytes =
        Hex.decode("697c197c9e0023c8eee42ddf08c12c46718a436561b0c66d998c81879f7cb74c");
    XChaCha20Poly1305Key key =
        XChaCha20Poly1305Key.create(SecretBytes.copyFrom(keyBytes, InsecureSecretKeyAccess.get()));
    Aead aead = XChaCha20Poly1305Jce.create(key);
    byte[] ciphertext = aead.encrypt(plaintext, associatedData);
    assertThat(ciphertext).hasLength(41); // msg (1) + iv(24) + tag(16)

    assertThat(aead.decrypt(ciphertext, associatedData)).isEqualTo(plaintext);

    byte[] fixedCiphertext =
        Hex.decode(
            "cd78f4533c94648feacd5aef0291b00b454ee3dcdb76dcc8b0e5e35f5332f91bdd2d28e59d68a0b141");
    assertThat(aead.decrypt(fixedCiphertext, associatedData)).isEqualTo(plaintext);
  }

  @Test
  public void testWithXChaCha20Poly1305Key_tinkPrefix_works() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    byte[] plaintext = Hex.decode("e1");
    byte[] associatedData = Hex.decode("6384f4714ff18c18");
    byte[] keyBytes =
        Hex.decode("697c197c9e0023c8eee42ddf08c12c46718a436561b0c66d998c81879f7cb74c");
    XChaCha20Poly1305Key key =
        XChaCha20Poly1305Key.create(
            XChaCha20Poly1305Parameters.Variant.TINK,
            SecretBytes.copyFrom(keyBytes, InsecureSecretKeyAccess.get()),
            0x99887766);
    Aead aead = XChaCha20Poly1305Jce.create(key);
    byte[] ciphertext = aead.encrypt(plaintext, associatedData);
    assertThat(ciphertext).hasLength(46); // prefix(5) + msg(1) + iv(24) + tag(16)

    assertThat(aead.decrypt(ciphertext, associatedData)).isEqualTo(plaintext);

    byte[] fixedCiphertext =
        Hex.decode(
            "0199887766cd78f4533c94648feacd5aef0291b00b454ee3dcdb76dcc8b0e5e35f5332f91bdd2d28e59d68a0b141");
    assertThat(aead.decrypt(fixedCiphertext, associatedData)).isEqualTo(plaintext);
  }

  @Test
  public void testWithXChaCha20Poly1305Key_crunchyPrefix_works() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(XChaCha20Poly1305Jce.isSupported());

    byte[] plaintext = Hex.decode("e1");
    byte[] associatedData = Hex.decode("6384f4714ff18c18");
    byte[] keyBytes =
        Hex.decode("697c197c9e0023c8eee42ddf08c12c46718a436561b0c66d998c81879f7cb74c");
    XChaCha20Poly1305Key key =
        XChaCha20Poly1305Key.create(
            XChaCha20Poly1305Parameters.Variant.CRUNCHY,
            SecretBytes.copyFrom(keyBytes, InsecureSecretKeyAccess.get()),
            0x99887766);
    Aead aead = XChaCha20Poly1305Jce.create(key);
    byte[] ciphertext = aead.encrypt(plaintext, associatedData);
    assertThat(ciphertext).hasLength(46); // prefix(5) + msg(1) + iv(24) + tag(16)

    assertThat(aead.decrypt(ciphertext, associatedData)).isEqualTo(plaintext);

    byte[] fixedCiphertext =
        Hex.decode(
            "0099887766cd78f4533c94648feacd5aef0291b00b454ee3dcdb76dcc8b0e5e35f5332f91bdd2d28e59d68a0b141");
    assertThat(aead.decrypt(fixedCiphertext, associatedData)).isEqualTo(plaintext);
  }

  @Test
  public void testIsSupportedOnNewerAndroidVersions() throws Exception {
    Assume.assumeFalse(TinkFips.useOnlyFips());
    Assume.assumeTrue(TestUtil.isAndroid());
    Integer androidApiLevel = Util.getAndroidApiLevel();
    assertThat(XChaCha20Poly1305Jce.isSupported()).isEqualTo(androidApiLevel >= 29);
  }

  @Test
  public void testCreateFailsIfNotSupported() throws Exception {
    Assume.assumeFalse(XChaCha20Poly1305Jce.isSupported());

    XChaCha20Poly1305Key key = XChaCha20Poly1305Key.create(SecretBytes.randomBytes(32));
    assertThrows(GeneralSecurityException.class, () -> XChaCha20Poly1305Jce.create(key));
  }

  @Test
  public void getChaCha20Nonce_works() {
    byte[] nonce = Hex.decode("000102030405060708091011121314151617181920212223");
    byte[] expected = Hex.decode("000000001617181920212223");
    assertThat(XChaCha20Poly1305Jce.getChaCha20Nonce(nonce)).isEqualTo(expected);
  }
}
