package dev.marcinromanowski.pspsim.kernel.ids.internal;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;

public final class SecureRandomIdGenerator implements IdGenerator {

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private final RandomGenerator random = new SecureRandom();

  @Override
  public String hex(int length) {
    requirePositive(length);
    StringBuilder id = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      id.append(HEX[random.nextInt(HEX.length)]);
    }
    return id.toString();
  }

  @Override
  public String digits(int length) {
    requirePositive(length);
    StringBuilder id = new StringBuilder(length);
    id.append((char) ('1' + random.nextInt(9)));
    for (int i = 1; i < length; i++) {
      id.append((char) ('0' + random.nextInt(10)));
    }
    return id.toString();
  }

  private static void requirePositive(int length) {
    if (length < 1) {
      throw new IllegalArgumentException("identifier length must be positive");
    }
  }
}
