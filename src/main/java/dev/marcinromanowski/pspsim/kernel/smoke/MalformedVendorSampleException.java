package dev.marcinromanowski.pspsim.kernel.smoke;

public class MalformedVendorSampleException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MalformedVendorSampleException(String sampleAndDefect) {
    super(sampleAndDefect);
  }
}
