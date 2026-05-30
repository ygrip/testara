package io.github.ygrip.testara.agent.safety;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecretRedactionGuardTest {

  @Test
  void redactsKeyValueSecrets() {
    String input = "password = mySecret123\nusername = admin";
    String result = SecretRedactionGuard.redact(input);
    assertTrue(result.contains("[REDACTED]"), "Should redact password value");
    assertFalse(result.contains("mySecret123"), "Should not contain original secret");
    assertTrue(result.contains("username = admin"), "Should NOT redact non-secret key");
  }

  @Test
  void redactsJsonSecrets() {
    String input = "{\"api-key\": \"sk-abc123\", \"name\": \"test\"}";
    String result = SecretRedactionGuard.redact(input);
    assertTrue(result.contains("[REDACTED]"), "Should redact api-key value");
    assertFalse(result.contains("sk-abc123"), "Should not contain original API key");
    assertTrue(result.contains("\"name\": \"test\""), "Should preserve non-secret fields");
  }

  @Test
  void redactsBearerTokens() {
    String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc123";
    String result = SecretRedactionGuard.redact(input);
    assertTrue(result.contains("[REDACTED]"), "Should redact bearer token");
    assertFalse(result.contains("eyJhbGci"), "Should not contain JWT payload");
  }

  @Test
  void redactsAwsKeys() {
    String input = "access key: AKIAIOSFODNN7EXAMPLE";
    String result = SecretRedactionGuard.redact(input);
    assertTrue(result.contains("[REDACTED]"), "Should redact AWS key");
    assertFalse(result.contains("AKIAIOSFODNN7EXAMPLE"), "Should not contain AWS key");
  }

  @Test
  void redactsPemBlocks() {
    String input = """
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCAmMwggJfAgEAAoGBAN...
        -----END PRIVATE KEY-----""";
    String result = SecretRedactionGuard.redact(input);
    assertTrue(result.contains("[REDACTED]"), "Should redact PEM block");
    assertFalse(result.contains("MIIEvQ"), "Should not contain key material");
  }

  @Test
  void redactsJdbcCredentials() {
    String input = "jdbc:mysql://admin:secretPass@localhost:3306/db";
    String result = SecretRedactionGuard.redact(input);
    assertTrue(result.contains("[REDACTED]"), "Should redact JDBC credentials");
    assertFalse(result.contains("secretPass"), "Should not contain password");
  }

  @Test
  void handlesNullAndEmpty() {
    assertNull(SecretRedactionGuard.redact(null));
    assertEquals("", SecretRedactionGuard.redact(""));
    assertEquals("   ", SecretRedactionGuard.redact("   "));
  }

  @Test
  void detectsSecrets() {
    // Full PEM block (requires BEGIN and END)
    String pemBlock = "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkq\n-----END PRIVATE KEY-----";
    assertTrue(SecretRedactionGuard.containsSecrets(pemBlock),
        "Should detect complete PEM block");
    // Clean strings
    assertFalse(SecretRedactionGuard.containsSecrets("hello world"));
    assertFalse(SecretRedactionGuard.containsSecrets(""));
  }

  @Test
  void redactPasswordEqualsValue() {
    String result = SecretRedactionGuard.redact("password=secret123");
    assertTrue(result.contains("[REDACTED]"), "Should redact password=value");
    assertFalse(result.contains("secret123"), "Should not contain original value");
  }

  @Test
  void redactBearerToken() {
    String result = SecretRedactionGuard.redact("Bearer abc123def456");
    assertTrue(result.contains("[REDACTED]"), "Should redact bearer token");
    assertFalse(result.contains("abc123def456"), "Should not contain original token");
  }

  @Test
  void sanitizeAggressivePassCatchesStubbornSecrets() {
    // Exact key=value patterns are caught in first pass
    String input = "password=abc123\ntoken=xyz789";
    String result = SecretRedactionGuard.sanitize(input);
    assertFalse(result.contains("abc123"), "password value should be redacted");
    assertFalse(result.contains("xyz789"), "token value should be redacted");
  }

  @Test
  void sanitizeDoesNotRedactNonSecrets() {
    // Words containing 'key' or 'secret' as substrings should NOT be redacted
    String input = "my_secret_key is a variable name\npublic_key = someValue";
    String result = SecretRedactionGuard.sanitize(input);
    assertTrue(result.contains("my_secret_key"), "substrings of secret-key words should be preserved");
    assertTrue(result.contains("public_key"), "non-secret keys should be preserved");
  }
}
