package dev.marcinromanowski.pspsim.kernel.smoke;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record Evidence(EvidenceClass kind, @Nullable LocalDate probed, boolean excerpt) {

  public Evidence {
    if (kind == null) {
      throw new MalformedVendorSampleException("an evidence names its class");
    }
    if ((kind == EvidenceClass.LIVE_PROBE) != (probed != null)) {
      throw new MalformedVendorSampleException(
          "a live probe, and nothing else, names the day it was made");
    }
    if (excerpt && kind == EvidenceClass.PUBLISHED_RULE) {
      throw new MalformedVendorSampleException("a published rule has no answer to excerpt");
    }
  }

  public static Evidence of(EvidenceClass kind) {
    return new Evidence(kind, null, false);
  }

  public static Evidence probedOn(LocalDate day) {
    return new Evidence(EvidenceClass.LIVE_PROBE, day, false);
  }

  public Evidence excerpted() {
    return new Evidence(kind, probed, true);
  }

  public String rendered() {
    return kind.spelling()
        + (probed == null ? "" : " on " + probed)
        + (excerpt ? ", an excerpt" : "");
  }
}
