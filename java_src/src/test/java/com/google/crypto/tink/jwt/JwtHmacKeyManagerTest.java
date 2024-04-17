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
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.jwt;

import static com.google.common.truth.Truth.assertThat;
import static com.google.crypto.tink.internal.TinkBugException.exceptionIsBug;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.Key;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.TinkProtoKeysetFormat;
import com.google.crypto.tink.internal.KeyManagerRegistry;
import com.google.crypto.tink.proto.JwtHmacKey;
import com.google.crypto.tink.proto.JwtHmacKey.CustomKid;
import com.google.crypto.tink.proto.KeyData;
import com.google.crypto.tink.proto.Keyset;
import com.google.crypto.tink.subtle.Base64;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.subtle.PrfHmacJce;
import com.google.crypto.tink.subtle.PrfMac;
import com.google.crypto.tink.testing.TestUtil;
import com.google.crypto.tink.util.SecretBytes;
import com.google.gson.JsonObject;
import com.google.protobuf.ExtensionRegistryLite;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.TreeSet;
import javax.crypto.spec.SecretKeySpec;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.theories.DataPoint;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Unit tests for {@link JwtHmacKeyManager}. */
@RunWith(Theories.class)
public class JwtHmacKeyManagerTest {

  @BeforeClass
  public static void setUp() throws Exception {
    JwtMacConfig.register();
  }

  @Test
  public void testKeyManagerRegistered() throws Exception {
    assertThat(
            KeyManagerRegistry.globalInstance()
                .getUntypedKeyManager("type.googleapis.com/google.crypto.tink.JwtHmacKey"))
        .isNotNull();
  }

  @DataPoint public static final String JWT_HS256 = "JWT_HS256";
  @DataPoint public static final String JWT_HS384 = "JWT_HS384";
  @DataPoint public static final String JWT_HS512 = "JWT_HS512";
  @DataPoint public static final String JWT_HS256_RAW = "JWT_HS256_RAW";


  @DataPoints("templateNames")
  public static final String[] KEY_TEMPLATES =
      new String[] {
        "JWT_HS256_RAW", "JWT_HS256", "JWT_HS384_RAW", "JWT_HS384", "JWT_HS512_RAW", "JWT_HS512",
      };

  @Theory
  public void testTemplates(@FromDataPoints("templateNames") String templateName) throws Exception {
    KeysetHandle h = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    assertThat(h.size()).isEqualTo(1);
    assertThat(h.getAt(0).getKey().getParameters())
        .isEqualTo(KeyTemplates.get(templateName).toParameters());
  }

  @Test
  public void createKey_multipleTimes() throws Exception {
    JwtHmacParameters parameters =
        JwtHmacParameters.builder()
            .setKeySizeBytes(32)
            .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
            .setAlgorithm(JwtHmacParameters.Algorithm.HS256)
            .build();
    int numKeys = 100;
    Set<String> keys = new TreeSet<>();
    for (int i = 0; i < numKeys; ++i) {
      KeysetHandle handle = KeysetHandle.generateNew(parameters);
      com.google.crypto.tink.jwt.JwtHmacKey jwtHmacKey =
          (com.google.crypto.tink.jwt.JwtHmacKey) handle.getAt(0).getKey();
      keys.add(Hex.encode(jwtHmacKey.getKeyBytes().toByteArray(InsecureSecretKeyAccess.get())));
    }
    assertThat(keys).hasSize(numKeys);
  }

  @Test
  public void testHs256Template() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    assertThat(template.toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(32)
                .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS256)
                .build());
  }

  @Test
  public void testHs256Template_function() throws Exception {
    assertThat(JwtHmacKeyManager.hs256Template().toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(32)
                .setKidStrategy(JwtHmacParameters.KidStrategy.IGNORED)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS256)
                .build());
  }

  @Test
  public void testHs384Template() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS384");
    assertThat(template.toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(48)
                .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS384)
                .build());
  }

  @Test
  public void testHs384Template_function() throws Exception {
    assertThat(JwtHmacKeyManager.hs384Template().toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(48)
                .setKidStrategy(JwtHmacParameters.KidStrategy.IGNORED)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS384)
                .build());
  }

  @Test
  public void testHs512Template() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS512");
    assertThat(template.toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(64)
                .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS512)
                .build());
  }

  @Test
  public void testHs512Template_function() throws Exception {
    assertThat(JwtHmacKeyManager.hs512Template().toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(64)
                .setKidStrategy(JwtHmacParameters.KidStrategy.IGNORED)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS512)
                .build());
  }

  @Test
  public void testHs256RawTemplate() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256_RAW");
    assertThat(template.toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(32)
                .setKidStrategy(JwtHmacParameters.KidStrategy.IGNORED)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS256)
                .build());
  }

  @Test
  public void testHs384RawTemplate() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS384_RAW");
    assertThat(template.toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(48)
                .setKidStrategy(JwtHmacParameters.KidStrategy.IGNORED)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS384)
                .build());
  }

  @Test
  public void testHs512RawTemplate() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS512_RAW");
    assertThat(template.toParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(64)
                .setKidStrategy(JwtHmacParameters.KidStrategy.IGNORED)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS512)
                .build());
  }

  @Test
  public void testKeyTemplatesWork() throws Exception {
    Parameters p = KeyTemplates.get("JWT_HS256").toParameters();
    assertThat(KeysetHandle.generateNew(p).getAt(0).getKey().getParameters()).isEqualTo(p);

    p = KeyTemplates.get("JWT_HS384").toParameters();
    assertThat(KeysetHandle.generateNew(p).getAt(0).getKey().getParameters()).isEqualTo(p);

    p = KeyTemplates.get("JWT_HS512").toParameters();
    assertThat(KeysetHandle.generateNew(p).getAt(0).getKey().getParameters()).isEqualTo(p);

    p = KeyTemplates.get("JWT_HS512_RAW").toParameters();
    assertThat(KeysetHandle.generateNew(p).getAt(0).getKey().getParameters()).isEqualTo(p);
  }

  @Test
  public void createKeysetHandle_works() throws Exception {
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get("JWT_HS256"));
    Key key = handle.getAt(0).getKey();
    assertThat(key).isInstanceOf(com.google.crypto.tink.jwt.JwtHmacKey.class);
    com.google.crypto.tink.jwt.JwtHmacKey jwtHmacKey = (com.google.crypto.tink.jwt.JwtHmacKey) key;
    assertThat(jwtHmacKey.getParameters())
        .isEqualTo(
            JwtHmacParameters.builder()
                .setKeySizeBytes(32)
                .setAlgorithm(JwtHmacParameters.Algorithm.HS256)
                .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                .build());
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void createSignVerify_success(String templateNames) throws Exception {
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get(templateNames));
    JwtMac primitive = handle.getPrimitive(JwtMac.class);
    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String signedCompact = primitive.computeMacAndEncode(rawToken);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    VerifiedJwt verifiedToken = primitive.verifyMacAndDecode(signedCompact, validator);
    assertThat(verifiedToken.getJwtId()).isEqualTo("jwtId");
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void createSignVerifyDifferentKey_throw(String templateNames) throws Exception {
    KeyTemplate template = KeyTemplates.get(templateNames);
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac primitive = handle.getPrimitive(JwtMac.class);
    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String compact = primitive.computeMacAndEncode(rawToken);

    KeysetHandle otherHandle = KeysetHandle.generateNew(template);
    JwtMac otherPrimitive = otherHandle.getPrimitive(JwtMac.class);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();
    assertThrows(
        GeneralSecurityException.class,
        () -> otherPrimitive.verifyMacAndDecode(compact, validator));
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void createSignVerify_modifiedHeader_throw(String templateNames) throws Exception {
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get(templateNames));
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    String jwtId = "user123";
    RawJwt unverified = RawJwt.newBuilder().setJwtId(jwtId).withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    String[] parts = compact.split("\\.", -1);
    byte[] header = Base64.urlSafeDecode(parts[0]);

    for (TestUtil.BytesMutation mutation : TestUtil.generateMutations(header)) {
      String modifiedHeader = Base64.urlSafeEncode(mutation.value);
      String modifiedToken = modifiedHeader + "." + parts[1] + "." + parts[2];

      assertThrows(
          GeneralSecurityException.class, () -> mac.verifyMacAndDecode(modifiedToken, validator));
    }
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void createSignVerify_modifiedPayload_throw(String templateNames) throws Exception {
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get(templateNames));
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    String jwtId = "user123";
    RawJwt unverified = RawJwt.newBuilder().setJwtId(jwtId).withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    String[] parts = compact.split("\\.", -1);
    byte[] payload = Base64.urlSafeDecode(parts[1]);

    for (TestUtil.BytesMutation mutation : TestUtil.generateMutations(payload)) {
      String modifiedPayload = Base64.urlSafeEncode(mutation.value);
      String modifiedToken = parts[0] + "." + modifiedPayload + "." + parts[2];

      assertThrows(
          GeneralSecurityException.class, () -> mac.verifyMacAndDecode(modifiedToken, validator));
    }
  }

  // Note: we use Theory as a parametrized test -- different from what the Theory framework intends.
  @Theory
  public void verify_modifiedSignature_shouldThrow(String templateNames) throws Exception {
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get(templateNames));
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    String jwtId = "user123";
    RawJwt unverified = RawJwt.newBuilder().setJwtId(jwtId).withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    String[] parts = compact.split("\\.", -1);
    byte[] signature = Base64.urlSafeDecode(parts[1]);

    for (TestUtil.BytesMutation mutation : TestUtil.generateMutations(signature)) {
      String modifiedSignature = Base64.urlSafeEncode(mutation.value);
      String modifiedToken = parts[0] + "." + parts[1] + "." + modifiedSignature;

      assertThrows(
          GeneralSecurityException.class, () -> mac.verifyMacAndDecode(modifiedToken, validator));
    }
  }

  @Test
  public void computeVerify_canGetData() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    String issuer = "google";
    String audience = "mybank";
    String jwtId = "user123";
    double amount = 0.1;
    RawJwt unverified =
        RawJwt.newBuilder()
            .setTypeHeader("myType")
            .setIssuer(issuer)
            .addAudience(audience)
            .setJwtId(jwtId)
            .addNumberClaim("amount", amount)
            .withoutExpiration()
            .build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator =
        JwtValidator.newBuilder()
            .expectTypeHeader("myType")
            .expectIssuer(issuer)
            .expectAudience(audience)
            .allowMissingExpiration()
            .build();
    VerifiedJwt token = mac.verifyMacAndDecode(compact, validator);

    assertThat(token.getTypeHeader()).isEqualTo("myType");
    assertThat(token.getNumberClaim("amount")).isEqualTo(amount);
    assertThat(token.getIssuer()).isEqualTo(issuer);
    assertThat(token.getAudiences()).containsExactly(audience);
    assertThat(token.getJwtId()).isEqualTo(jwtId);
  }

  @Test
  public void verify_expired_shouldThrow() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    Clock clock1 = Clock.systemUTC();
    // This token expires in 1 minute in the future.
    RawJwt token =
        RawJwt.newBuilder()
            .setExpiration(clock1.instant().plus(Duration.ofMinutes(1)))
            .build();
    String compact = mac.computeMacAndEncode(token);

    // Move the clock to 2 minutes in the future.
    Clock clock2 = Clock.offset(clock1, Duration.ofMinutes(2));
    JwtValidator validator = JwtValidator.newBuilder().setClock(clock2).build();

    assertThrows(JwtInvalidException.class, () -> mac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void verify_notExpired_success() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    Clock clock = Clock.systemUTC();
    // This token expires in 1 minute in the future.
    Instant expiration = clock.instant().plus(Duration.ofMinutes(1));
    RawJwt unverified =
        RawJwt.newBuilder().setExpiration(expiration).build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator = JwtValidator.newBuilder().build();
    VerifiedJwt token = mac.verifyMacAndDecode(compact, validator);

    assertThat(token.getExpiration()).isEqualTo(unverified.getExpiration());
  }

  @Test
  public void verify_notExpired_clockSkew_success() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    Clock clock1 = Clock.systemUTC();
    // This token expires in 1 minutes in the future.
    Instant expiration = clock1.instant().plus(Duration.ofMinutes(1));
    RawJwt unverified =
        RawJwt.newBuilder().setExpiration(expiration).build();
    String compact = mac.computeMacAndEncode(unverified);

    // A clock skew of 1 minute is allowed.
    JwtValidator validator = JwtValidator.newBuilder().setClockSkew(Duration.ofMinutes(1)).build();
    VerifiedJwt token = mac.verifyMacAndDecode(compact, validator);

    assertThat(token.getExpiration()).isEqualTo(unverified.getExpiration());
  }

  @Test
  public void verify_before_shouldThrow() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    Clock clock = Clock.systemUTC();
    // This token cannot be used until 1 minute in the future.
    Instant notBefore = clock.instant().plus(Duration.ofMinutes(1));
    RawJwt unverified =
        RawJwt.newBuilder().setNotBefore(notBefore).withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);

    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    assertThrows(JwtInvalidException.class, () -> mac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void validate_notBefore_success() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    Clock clock1 = Clock.systemUTC();
    // This token cannot be used until 1 minute in the future.
    Instant notBefore = clock1.instant().plus(Duration.ofMinutes(1));
    RawJwt unverified =
        RawJwt.newBuilder().setNotBefore(notBefore).withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);

    // Move the clock to 2 minutes in the future.
    Clock clock2 = Clock.offset(clock1, Duration.ofMinutes(2));
    JwtValidator validator =
        JwtValidator.newBuilder().allowMissingExpiration().setClock(clock2).build();
    VerifiedJwt token = mac.verifyMacAndDecode(compact, validator);

    assertThat(token.getNotBefore()).isEqualTo(unverified.getNotBefore());
  }

  @Test
  public void validate_notBefore_clockSkew_success() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    Clock clock1 = Clock.systemUTC();
    // This token cannot be used until 1 minute in the future.
    Instant notBefore = clock1.instant().plus(Duration.ofMinutes(1));
    RawJwt unverified =
        RawJwt.newBuilder().setNotBefore(notBefore).withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);

    // A clock skew of 1 minute is allowed.
    JwtValidator validator =
        JwtValidator.newBuilder()
            .allowMissingExpiration()
            .setClockSkew(Duration.ofMinutes(1))
            .build();
    VerifiedJwt token = mac.verifyMacAndDecode(compact, validator);

    assertThat(token.getNotBefore()).isEqualTo(unverified.getNotBefore());
  }

  @Test
  public void verify_noAudienceInJwt_shouldThrow() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    RawJwt unverified = RawJwt.newBuilder().withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator =
        JwtValidator.newBuilder().allowMissingExpiration().expectAudience("foo").build();

    assertThrows(JwtInvalidException.class, () -> mac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void verify_noAudienceInValidator_shouldThrow() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    RawJwt unverified =
        RawJwt.newBuilder().addAudience("foo").withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    assertThrows(JwtInvalidException.class, () -> mac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void verify_wrongAudience_shouldThrow() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    RawJwt unverified =
        RawJwt.newBuilder().addAudience("foo").withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator =
        JwtValidator.newBuilder().allowMissingExpiration().expectAudience("bar").build();

    assertThrows(JwtInvalidException.class, () -> mac.verifyMacAndDecode(compact, validator));
  }

  @Test
  public void verify_audience_success() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    RawJwt unverified =
        RawJwt.newBuilder().addAudience("foo").withoutExpiration().build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator =
        JwtValidator.newBuilder().allowMissingExpiration().expectAudience("foo").build();
    VerifiedJwt token = mac.verifyMacAndDecode(compact, validator);

    assertThat(token.getAudiences()).containsExactly("foo");
  }

  @Test
  public void verify_multipleAudiences_success() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    JwtMac mac = handle.getPrimitive(JwtMac.class);

    RawJwt unverified =
        RawJwt.newBuilder()
            .addAudience("foo")
            .addAudience("bar").withoutExpiration()
            .build();
    String compact = mac.computeMacAndEncode(unverified);
    JwtValidator validator =
        JwtValidator.newBuilder().allowMissingExpiration().expectAudience("bar").build();
    VerifiedJwt token = mac.verifyMacAndDecode(compact, validator);

    assertThat(token.getAudiences()).containsExactly("foo", "bar");
  }

  private static String generateSignedCompact(PrfMac mac, JsonObject header, JsonObject payload)
      throws GeneralSecurityException {
    String payloadBase64 = Base64.urlSafeEncode(payload.toString().getBytes(UTF_8));
    String headerBase64 = Base64.urlSafeEncode(header.toString().getBytes(UTF_8));
    String unsignedCompact = headerBase64 + "." + payloadBase64;
    String signature = Base64.urlSafeEncode(mac.computeMac(unsignedCompact.getBytes(UTF_8)));
    return unsignedCompact + "." + signature;
  }

  @Test
  public void createSignVerifyRaw_withDifferentHeaders() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256_RAW");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    com.google.crypto.tink.jwt.JwtHmacKey key =
        (com.google.crypto.tink.jwt.JwtHmacKey) handle.getAt(0).getKey();

    byte[] keyValue = key.getKeyBytes().toByteArray(InsecureSecretKeyAccess.get());
    SecretKeySpec keySpec = new SecretKeySpec(keyValue, "HMAC");
    PrfHmacJce prf = new PrfHmacJce("HMACSHA256", keySpec);
    PrfMac rawPrimitive = new PrfMac(prf, prf.getMaxOutputLength());
    JwtMac primitive = handle.getPrimitive(JwtMac.class);

    JsonObject payload = new JsonObject();
    payload.addProperty("jti", "jwtId");
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    // Normal, valid signed compact.
    JsonObject normalHeader = new JsonObject();
    normalHeader.addProperty("alg", "HS256");
    String normalSignedCompact = generateSignedCompact(rawPrimitive, normalHeader, payload);
    Object unused = primitive.verifyMacAndDecode(normalSignedCompact, validator);

    // valid token, with "typ" set in the header
    JsonObject goodHeader = new JsonObject();
    goodHeader.addProperty("alg", "HS256");
    goodHeader.addProperty("typ", "typeHeader");
    String goodSignedCompact = generateSignedCompact(rawPrimitive, goodHeader, payload);
    unused =
        primitive.verifyMacAndDecode(
            goodSignedCompact,
            JwtValidator.newBuilder()
                .expectTypeHeader("typeHeader")
                .allowMissingExpiration()
                .build());

    // invalid token with an empty header
    JsonObject emptyHeader = new JsonObject();
    String emptyHeaderSignedCompact = generateSignedCompact(rawPrimitive, emptyHeader, payload);
    assertThrows(
        GeneralSecurityException.class,
        () -> primitive.verifyMacAndDecode(emptyHeaderSignedCompact, validator));

    // invalid token with a valid but incorrect algorithm in the header
    JsonObject badAlgoHeader = new JsonObject();
    badAlgoHeader.addProperty("alg", "RS256");
    String badAlgoSignedCompact = generateSignedCompact(rawPrimitive, badAlgoHeader, payload);
    assertThrows(
        GeneralSecurityException.class,
        () -> primitive.verifyMacAndDecode(badAlgoSignedCompact, validator));

    // for raw keys without customKid, the validation should work even if a "kid" header is present.
    JsonObject headerWithUnknownKid = new JsonObject();
    headerWithUnknownKid.addProperty("alg", "HS256");
    headerWithUnknownKid.addProperty("kid", "unknown");
    String tokenWithUnknownKid = generateSignedCompact(
        rawPrimitive, headerWithUnknownKid, payload);
    unused = primitive.verifyMacAndDecode(tokenWithUnknownKid, validator);
  }

  @Test
  public void createSignVerifyTink_withDifferentHeaders() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    com.google.crypto.tink.jwt.JwtHmacKey key =
        (com.google.crypto.tink.jwt.JwtHmacKey) handle.getAt(0).getKey();

    byte[] keyValue = key.getKeyBytes().toByteArray(InsecureSecretKeyAccess.get());
    SecretKeySpec keySpec = new SecretKeySpec(keyValue, "HMAC");
    PrfHmacJce prf = new PrfHmacJce("HMACSHA256", keySpec);
    PrfMac rawPrimitive = new PrfMac(prf, prf.getMaxOutputLength());
    JwtMac primitive = handle.getPrimitive(JwtMac.class);
    String kid = key.getKid().get();

    JsonObject payload = new JsonObject();
    payload.addProperty("jti", "jwtId");
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    // Normal, valid signed compact.
    JsonObject normalHeader = new JsonObject();
    normalHeader.addProperty("alg", "HS256");
    normalHeader.addProperty("kid", kid);
    String normalToken = generateSignedCompact(rawPrimitive, normalHeader, payload);
    Object unused = primitive.verifyMacAndDecode(normalToken, validator);

    // valid token, with "typ" set in the header
    JsonObject headerWithTyp = new JsonObject();
    headerWithTyp.addProperty("alg", "HS256");
    headerWithTyp.addProperty("typ", "typeHeader");
    headerWithTyp.addProperty("kid", kid);
    String tokenWithTyp = generateSignedCompact(rawPrimitive, headerWithTyp, payload);
    unused =
        primitive.verifyMacAndDecode(
            tokenWithTyp,
            JwtValidator.newBuilder()
                .expectTypeHeader("typeHeader")
                .allowMissingExpiration()
                .build());

    // invalid token without algorithm
    JsonObject headerWithoutAlg = new JsonObject();
    headerWithoutAlg.addProperty("kid", kid);
    String tokenWithoutAlg = generateSignedCompact(rawPrimitive, headerWithoutAlg, payload);
    assertThrows(
        GeneralSecurityException.class,
        () -> primitive.verifyMacAndDecode(tokenWithoutAlg, validator));

    // invalid token with a valid but incorrect algorithm in the header
    JsonObject headerWithBadAlg = new JsonObject();
    headerWithBadAlg.addProperty("alg", "RS256");
    headerWithBadAlg.addProperty("kid", kid);
    String tokenWithBadAlg = generateSignedCompact(rawPrimitive, headerWithBadAlg, payload);
    assertThrows(
        GeneralSecurityException.class,
        () -> primitive.verifyMacAndDecode(tokenWithBadAlg, validator));

    // token with an unknown "kid" in the header is valid
    JsonObject headerWithUnknownKid = new JsonObject();
    headerWithUnknownKid.addProperty("alg", "HS256");
    headerWithUnknownKid.addProperty("kid", "unknown");
    String tokenWithUnknownKid = generateSignedCompact(
        rawPrimitive, headerWithUnknownKid, payload);
    assertThrows(
        GeneralSecurityException.class,
        () -> primitive.verifyMacAndDecode(tokenWithUnknownKid, validator));
  }

  private static KeysetHandle getRfc7515ExampleKeysetHandle() throws Exception {
    String keyValue =
        "AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow";
    JwtHmacParameters parameters =
        JwtHmacParameters.builder()
            .setKeySizeBytes(64)
            .setKidStrategy(JwtHmacParameters.KidStrategy.IGNORED)
            .setAlgorithm(JwtHmacParameters.Algorithm.HS256)
            .build();
    com.google.crypto.tink.jwt.JwtHmacKey newKey =
        com.google.crypto.tink.jwt.JwtHmacKey.builder()
            .setParameters(parameters)
            .setKeyBytes(
                SecretBytes.copyFrom(Base64.urlSafeDecode(keyValue), InsecureSecretKeyAccess.get()))
            .build();
    return KeysetHandle.newBuilder()
        .addEntry(KeysetHandle.importKey(newKey).withFixedId(123).makePrimary())
        .build();
  }

  // Test vectors copied from https://tools.ietf.org/html/rfc7515#appendix-A.1.
  @Test
  public void verify_rfc7515TestVector_shouldThrow() throws Exception {
    KeysetHandle handle = getRfc7515ExampleKeysetHandle();
    JwtMac primitive = handle.getPrimitive(JwtMac.class);

    // The sample token has expired since 2011-03-22.
    String compact =
        "eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9."
            + "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQo"
            + "gImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ."
            + "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    JwtValidator validator = JwtValidator.newBuilder().build();
    assertThrows(JwtInvalidException.class, () -> primitive.verifyMacAndDecode(compact, validator));
  }

  // Test vectors copied from https://tools.ietf.org/html/rfc7515#appendix-A.1.
  @Test
  public void verify_rfc7515TestVector_fixedClock_success() throws Exception {
    KeysetHandle handle = getRfc7515ExampleKeysetHandle();
    JwtMac primitive = handle.getPrimitive(JwtMac.class);

    // The sample token has expired since 2011-03-22T18:43:00Z.
    String compact =
        "eyJ0eXAiOiJKV1QiLA0KICJhbGciOiJIUzI1NiJ9."
            + "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQo"
            + "gImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ."
            + "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

    // One minute earlier than the expiration time of the sample token.
    String instant = "2011-03-22T18:42:00Z";
    Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    JwtValidator validator =
        JwtValidator.newBuilder()
            .expectTypeHeader("JWT")
            .expectIssuer("joe")
            .setClock(clock)
            .build();

    VerifiedJwt token = primitive.verifyMacAndDecode(compact, validator);

    assertThat(token.getIssuer()).isEqualTo("joe");
    assertThat(token.getBooleanClaim("http://example.com/is_root")).isTrue();
  }

  /* Create a new keyset handle with the "custom_kid" value set. */
  private KeysetHandle withCustomKid(KeysetHandle keysetHandle, String customKid)
      throws Exception {
    com.google.crypto.tink.jwt.JwtHmacKey key =
        (com.google.crypto.tink.jwt.JwtHmacKey) keysetHandle.getAt(0).getKey();

    JwtHmacParameters newParameters =
        JwtHmacParameters.builder()
            .setKeySizeBytes(key.getParameters().getKeySizeBytes())
            .setKidStrategy(JwtHmacParameters.KidStrategy.CUSTOM)
            .setAlgorithm(key.getParameters().getAlgorithm())
            .build();
    com.google.crypto.tink.jwt.JwtHmacKey newKey =
        com.google.crypto.tink.jwt.JwtHmacKey.builder()
            .setParameters(newParameters)
            .setCustomKid(customKid)
            .setKeyBytes(key.getKeyBytes())
            .build();

    return KeysetHandle.newBuilder()
        .addEntry(KeysetHandle.importKey(newKey).withFixedId(123).makePrimary())
        .build();
  }

  @Test
  public void macWithCustomKid() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256_RAW");
    KeysetHandle handleWithoutKid = KeysetHandle.generateNew(template);
    KeysetHandle handleWithKid =
        withCustomKid(handleWithoutKid, "Lorem ipsum dolor sit amet, consectetur adipiscing elit");
    JwtMac jwtMacWithoutKid = handleWithoutKid.getPrimitive(JwtMac.class);
    JwtMac jwtMacWithKid = handleWithKid.getPrimitive(JwtMac.class);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String compactWithKid = jwtMacWithKid.computeMacAndEncode(rawToken);
    String compactWithoutKid = jwtMacWithoutKid.computeMacAndEncode(rawToken);

    // Verify the kid in the header
    String jsonHeaderWithKid = JwtFormat.splitSignedCompact(compactWithKid).header;
    String kid = JsonUtil.parseJson(jsonHeaderWithKid).get("kid").getAsString();
    assertThat(kid).isEqualTo("Lorem ipsum dolor sit amet, consectetur adipiscing elit");
    String jsonHeaderWithoutKid = JwtFormat.splitSignedCompact(compactWithoutKid).header;
    assertThat(JsonUtil.parseJson(jsonHeaderWithoutKid).has("kid")).isFalse();

    // Even if custom_kid is set, we don't require a "kid" in the header.
    assertThat(jwtMacWithKid.verifyMacAndDecode(compactWithKid, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(jwtMacWithoutKid.verifyMacAndDecode(compactWithKid, validator).getJwtId())
        .isEqualTo("jwtId");

    assertThat(jwtMacWithKid.verifyMacAndDecode(compactWithoutKid, validator).getJwtId())
        .isEqualTo("jwtId");
    assertThat(jwtMacWithoutKid.verifyMacAndDecode(compactWithoutKid, validator).getJwtId())
        .isEqualTo("jwtId");
  }

  @Test
  public void macWithWrongCustomKid() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256_RAW");
    KeysetHandle handleWithoutKid = KeysetHandle.generateNew(template);
    KeysetHandle handleWithKid = withCustomKid(handleWithoutKid, "kid");
    KeysetHandle handleWithWrongKid = withCustomKid(handleWithoutKid, "wrong kid");
    JwtMac jwtMacWithKid = handleWithKid.getPrimitive(JwtMac.class);
    JwtMac jwtMacWithWrongKid = handleWithWrongKid.getPrimitive(JwtMac.class);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String compactWithKid = jwtMacWithKid.computeMacAndEncode(rawToken);

    assertThrows(
        JwtInvalidException.class,
        () -> jwtMacWithWrongKid.verifyMacAndDecode(compactWithKid, validator));
  }

  @Test
  public void keyWithKid_tokenWithoutKid() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256_RAW");
    KeysetHandle handleWithoutKid = KeysetHandle.generateNew(template);

    com.google.crypto.tink.jwt.JwtHmacKey key =
        (com.google.crypto.tink.jwt.JwtHmacKey) handleWithoutKid.getAt(0).getKey();

    JwtHmacParameters newParameters =
        JwtHmacParameters.builder()
            .setKeySizeBytes(key.getParameters().getKeySizeBytes())
            .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
            .setAlgorithm(key.getParameters().getAlgorithm())
            .build();
    com.google.crypto.tink.jwt.JwtHmacKey newKey =
        com.google.crypto.tink.jwt.JwtHmacKey.builder()
            .setParameters(newParameters)
            .setIdRequirement(0x22446688)
            .setKeyBytes(key.getKeyBytes())
            .build();

    KeysetHandle handleWithTinkKid =
        KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.importKey(newKey).withFixedId(0x22446688).makePrimary())
            .build();

    JwtMac jwtMacWithKid = handleWithTinkKid.getPrimitive(JwtMac.class);
    JwtMac jwtMacWithoutKid = handleWithoutKid.getPrimitive(JwtMac.class);
    JwtValidator validator = JwtValidator.newBuilder().allowMissingExpiration().build();

    RawJwt rawToken = RawJwt.newBuilder().setJwtId("jwtId").withoutExpiration().build();
    String compactWithKid = jwtMacWithoutKid.computeMacAndEncode(rawToken);

    assertThrows(
        GeneralSecurityException.class,
        () -> jwtMacWithKid.verifyMacAndDecode(compactWithKid, validator));
  }

  @Test
  public void macWithTinkKeyAndCustomKid_fails() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256");
    KeysetHandle handle = KeysetHandle.generateNew(template);

    // Create a new handle with the "kid" value set.
    Keyset keyset =
        Keyset.parseFrom(
            TinkProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get()),
            ExtensionRegistryLite.getEmptyRegistry());

    JwtHmacKey hmacKey =
        JwtHmacKey.parseFrom(
            keyset.getKey(0).getKeyData().getValue(), ExtensionRegistryLite.getEmptyRegistry());
    JwtHmacKey hmacKeyWithKid =
        hmacKey.toBuilder()
            .setCustomKid(
                CustomKid.newBuilder()
                    .setValue("Lorem ipsum dolor sit amet, consectetur adipiscing elit")
                    .build())
            .build();
    KeyData keyDataWithKid =
        keyset.getKey(0).getKeyData().toBuilder().setValue(hmacKeyWithKid.toByteString()).build();
    Keyset.Key keyWithKid = keyset.getKey(0).toBuilder().setKeyData(keyDataWithKid).build();
    byte[] serializeKeysetWithKid = keyset.toBuilder().setKey(0, keyWithKid).build().toByteArray();
    // A key with OutputPrefixType TINK and a KID value set needs to be rejected either when parsing
    // or when we call getPrimitive.
    assertThrows(
        GeneralSecurityException.class,
        () ->
            TinkProtoKeysetFormat.parseKeyset(serializeKeysetWithKid, InsecureSecretKeyAccess.get())
                .getPrimitive(JwtMac.class));
  }

  @Test
  public void serializeAndDeserialize_works() throws Exception {
    KeyTemplate template = KeyTemplates.get("JWT_HS256_RAW");
    KeysetHandle handle = KeysetHandle.generateNew(template);
    byte[] serialized =
        TinkProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get());
    KeysetHandle parsed =
        TinkProtoKeysetFormat.parseKeyset(serialized, InsecureSecretKeyAccess.get());
    assertThat(parsed.equalsKeyset(handle)).isTrue();
  }

  @Theory
  public void createKeyWithRejectedParameters_throws(
      @FromDataPoints("KeyManager rejected") JwtHmacParameters params) throws Exception {
    assertThrows(GeneralSecurityException.class, () -> KeysetHandle.generateNew(params));
  }

  @Theory
  public void createPrimitiveWithRejectedParameters_throws(
      @FromDataPoints("KeyManager rejected") JwtHmacParameters params) throws Exception {
    com.google.crypto.tink.jwt.JwtHmacKey key =
        com.google.crypto.tink.jwt.JwtHmacKey.builder()
            .setParameters(params)
            .setKeyBytes(SecretBytes.randomBytes(params.getKeySizeBytes()))
            .setIdRequirement(123)
            .build();
    KeysetHandle handle =
        KeysetHandle.newBuilder().addEntry(KeysetHandle.importKey(key).makePrimary()).build();
    assertThrows(GeneralSecurityException.class, () -> handle.getPrimitive(JwtMac.class));
  }

  private static JwtHmacParameters[] createRejectedParameters() {
    return exceptionIsBug(
        () ->
            new JwtHmacParameters[] {
              // Key Sizes below the minimum are rejected.
              JwtHmacParameters.builder()
                  .setKeySizeBytes(31)
                  .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                  .setAlgorithm(JwtHmacParameters.Algorithm.HS256)
                  .build(),
              JwtHmacParameters.builder()
                  .setKeySizeBytes(47)
                  .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                  .setAlgorithm(JwtHmacParameters.Algorithm.HS384)
                  .build(),
              JwtHmacParameters.builder()
                  .setKeySizeBytes(63)
                  .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                  .setAlgorithm(JwtHmacParameters.Algorithm.HS512)
                  .build(),
              JwtHmacParameters.builder()
                  .setKeySizeBytes(32)
                  .setKidStrategy(JwtHmacParameters.KidStrategy.BASE64_ENCODED_KEY_ID)
                  .setAlgorithm(JwtHmacParameters.Algorithm.HS512)
                  .build(),
            });
  }

  @DataPoints("KeyManager rejected")
  public static final JwtHmacParameters[] RECJECTED_PARAMETERS = createRejectedParameters();
}
