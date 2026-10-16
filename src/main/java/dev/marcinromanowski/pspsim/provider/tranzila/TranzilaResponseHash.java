package dev.marcinromanowski.pspsim.provider.tranzila;

final class TranzilaResponseHash {

  private TranzilaResponseHash() {}

  static String of(String json, String secret) {
    return TranzilaAccessToken.hexOfTheHmac(secret, json);
  }
}
