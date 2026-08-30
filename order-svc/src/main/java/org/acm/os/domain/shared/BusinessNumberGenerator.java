package org.acm.os.domain.shared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class BusinessNumberGenerator {
  private BusinessNumberGenerator() {}

  public static String generate(String prefix) {
    return prefix + UUID.randomUUID().toString().replace("-", "");
  }

  public static String deterministic(String prefix, String value) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(prefix);
      for (int index = 0; index < 16; index++) {
        result.append("%02x".formatted(hash[index]));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
