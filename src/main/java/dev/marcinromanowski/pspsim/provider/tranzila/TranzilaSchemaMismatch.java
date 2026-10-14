package dev.marcinromanowski.pspsim.provider.tranzila;

final class TranzilaSchemaMismatch extends RuntimeException {

  private static final long serialVersionUID = 1L;

  TranzilaSchemaMismatch(String why) {
    super(why, null, false, false);
  }

  String why() {
    return String.valueOf(getMessage());
  }
}
