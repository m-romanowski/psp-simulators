package com.example.checkout.e2e;

interface Shop {

  enum Outcome {
    ACCEPTED,
    REFUSED,
    SERVER_ERROR,
    NO_ANSWER_AT_ALL
  }

  enum Refusal {
    AN_EXPIRED_CARD,
    AN_AUTHORISATION_PAST_ITS_WINDOW
  }

  record Answer(Outcome outcome, String providerCode, String contentType, String body) {

    static Answer accepted(String contentType, String body) {
      return new Answer(Outcome.ACCEPTED, null, contentType, body);
    }

    static Answer refused(String providerCode, String contentType, String body) {
      return new Answer(Outcome.REFUSED, providerCode, contentType, body);
    }

    static Answer serverError(String body) {
      return new Answer(Outcome.SERVER_ERROR, null, null, body);
    }

    static Answer noAnswerAtAll(String detail) {
      return new Answer(Outcome.NO_ANSWER_AT_ALL, null, null, detail);
    }
  }

  record Reconciled(String providerStatus, String capturedAmount) {}

  String providerName();

  Control control();

  void opensForBusiness(String clockPinnedTo, String callbackUrl);

  void tidiesUp();

  String aPayerAuthorisesAHoldOf(String amount);

  Answer captures(String paymentId, String amount);

  Answer refunds(String paymentId, String amount);

  Reconciled reconciles(String paymentId);

  String theCodeFor(Refusal refusal);

  String theScenarioNameFor(Refusal refusal);

  String theSimulatorsWordFor(String amount);

  String theOperationThatCaptures();

  String theEventOfAFinishedPayment();
}
