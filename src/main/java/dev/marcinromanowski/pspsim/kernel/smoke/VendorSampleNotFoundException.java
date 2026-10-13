package dev.marcinromanowski.pspsim.kernel.smoke;

public class VendorSampleNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public VendorSampleNotFoundException(String missingSample) {
    super(missingSample);
  }
}
