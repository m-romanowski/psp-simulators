package dev.marcinromanowski.pspsim.kernel.spi;

public class UnknownProviderObjectException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownProviderObjectException(String kindAndScope) {
    super(kindAndScope);
  }
}
