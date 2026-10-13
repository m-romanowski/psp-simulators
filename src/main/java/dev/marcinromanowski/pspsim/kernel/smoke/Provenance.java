package dev.marcinromanowski.pspsim.kernel.smoke;

import org.jspecify.annotations.Nullable;

public record Provenance(
    String sample, Evidence evidence, String source, String reference, @Nullable String note) {

  public Provenance {
    if (sample == null || sample.isBlank()) {
      throw new IllegalArgumentException("sample must name the vendor sample it describes");
    }
    if (evidence == null) {
      throw new MalformedVendorSampleException(sample + " names no evidence class");
    }
    if (source == null || source.isBlank()) {
      throw new MalformedVendorSampleException(sample + " names no source");
    }
    if (reference == null || reference.isBlank()) {
      throw new MalformedVendorSampleException(sample + " names no reference");
    }
    if (note != null && note.isBlank()) {
      throw new MalformedVendorSampleException(sample + " gives a note with no text");
    }
  }

  public Provenance(String sample, Evidence evidence, String source, String reference) {
    this(sample, evidence, source, reference, null);
  }

  public String rendered() {
    return sample
        + ": "
        + evidence.rendered()
        + ", "
        + source
        + " ("
        + reference
        + ")"
        + (note == null ? "" : "; " + note);
  }

  @Override
  public String toString() {
    return rendered();
  }
}
