package dev.marcinromanowski.pspsim.kernel.smoke;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.concurrent.atomic.AtomicInteger;

public final class CountingIds implements IdGenerator {

  private final AtomicInteger minted = new AtomicInteger();

  @Override
  public String hex(int length) {
    String drawn = Integer.toHexString(minted.incrementAndGet());
    return "0".repeat(Math.max(0, length - drawn.length())) + drawn;
  }

  @Override
  public String digits(int length) {
    String drawn = String.valueOf(minted.incrementAndGet());
    return "1".repeat(Math.max(0, length - drawn.length())) + drawn;
  }
}
