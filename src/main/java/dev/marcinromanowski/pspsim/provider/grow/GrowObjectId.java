package dev.marcinromanowski.pspsim.provider.grow;

final class GrowObjectId {

  static final String PROCESS = "PROCESS";

  static final String TRANSACTION = "TRANSACTION";

  static final String ONE_ID_NAMES_TWO_OBJECTS =
      "a grow processId and a grow transactionId are drawn from one id namespace per test run so"
          + " that neither read surface can answer the wrong object, and this test run holds a"
          + " payment process and a transaction under one providerObjectId";

  private GrowObjectId() {}
}
