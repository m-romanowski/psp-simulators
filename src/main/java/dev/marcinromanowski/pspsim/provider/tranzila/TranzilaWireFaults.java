package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class TranzilaWireFaults {

  static final ProviderWireRoutes ROUTES =
      new ProviderWireRoutes(
          TranzilaApi.TRANZILA,
          List.of(
              TranzilaApiController.TRANSACTION,
              TranzilaApiController.CREATE_HANDSHAKE,
              TranzilaDirectNgPage.HOST,
              TranzilaHostedFieldsPage.HOST,
              TranzilaChallengePage.HOST));

  static final String THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD =
      "This address does not answer this HTTP method";

  static final String WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "an Error schema of error_code and message for 400, 401 and 404, and application error"
          + " codes answered at 200. It publishes no 5xx at all, and an Error body in tranzila's"
          + " field names would tell a client that tranzila answered when this simulator is what"
          + " failed";

  static final String WHAT_THE_HANDSHAKE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "a body of error_code and message whose error_code is the HTTP status, for 400 and 401"
          + " only. It publishes no 5xx at all, and a body in tranzila's field names would tell a"
          + " client that tranzila answered when this simulator is what failed";

  static final String WHAT_THE_PAGE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "nothing at all for its own failures: the payment page publishes its parameters and the"
          + " return it posts to the merchant, and no page of its own for an error, so a page in"
          + " tranzila's look would tell a payer that tranzila answered when this simulator is"
          + " what failed";

  static final String WHAT_THE_HOSTED_FIELDS_PUBLISH_FOR_A_FAILURE_INSIDE_THEM =
      "nothing for a failure of their own: the hosted fields publish their script, their charge"
          + " keys, their answer and their error table, and no answer for a failure beside them,"
          + " so a body in tranzila's field names would tell a client that tranzila answered when"
          + " this simulator is what failed";

  static final String WHAT_THE_CHALLENGE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT =
      "nothing at all: the 3DS process publishes only that the browser opens challengeUrl and is"
          + " sent to auth_3ds_redirect afterwards, and no page of its own for an error, so a page"
          + " in tranzila's look would tell a payer that tranzila answered when this simulator is"
          + " what failed";

  private TranzilaWireFaults() {}

  static ProviderWireAnswer theAnswerTo(WireFault fault, String address) {
    if (address.startsWith(TranzilaApiController.CREATE_HANDSHAKE)) {
      return theHandshakesAnswerTo(fault);
    }
    if (address.startsWith(TranzilaDirectNgPage.HOST)) {
      return thePagesAnswerTo(fault);
    }
    if (address.startsWith(TranzilaHostedFieldsPage.HOST)) {
      return theHostedFieldsAnswerTo(fault);
    }
    if (address.startsWith(TranzilaChallengePage.HOST)) {
      return theChallengesAnswerTo(fault);
    }
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
          errorSchema(
              TranzilaAnswers.NOT_FOUND,
              TranzilaErrors.RESOURCE_NOT_FOUND,
              TranzilaErrors.RESOURCE_NOT_FOUND_TEXT);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          errorSchema(
              TranzilaAnswers.METHOD_NOT_ALLOWED,
              TranzilaErrors.METHOD_NOT_ALLOWED,
              THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
    };
  }

  static ProviderWireAnswer theHandshakesAnswerTo(WireFault fault) {
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
          handshakeShape(TranzilaAnswers.NOT_FOUND, TranzilaErrors.RESOURCE_NOT_FOUND_TEXT);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          handshakeShape(
              TranzilaAnswers.METHOD_NOT_ALLOWED, THE_ADDRESS_DOES_NOT_ANSWER_THIS_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THE_HANDSHAKE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
    };
  }

  static ProviderWireAnswer thePagesAnswerTo(WireFault fault) {
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
          aPage(TranzilaAnswers.NOT_FOUND, TranzilaDirectNgPage.NO_SUCH_ADDRESS);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          aPage(TranzilaAnswers.METHOD_NOT_ALLOWED, TranzilaDirectNgPage.NO_SUCH_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THE_PAGE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
    };
  }

  static ProviderWireAnswer theHostedFieldsAnswerTo(WireFault fault) {
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
          aHostedFieldsPage(
              TranzilaAnswers.NOT_FOUND, TranzilaHostedFieldsRefusalPage.NO_SUCH_ADDRESS);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          aHostedFieldsPage(
              TranzilaAnswers.METHOD_NOT_ALLOWED, TranzilaHostedFieldsRefusalPage.NO_SUCH_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THE_HOSTED_FIELDS_PUBLISH_FOR_A_FAILURE_INSIDE_THEM);
    };
  }

  static ProviderWireAnswer theChallengesAnswerTo(WireFault fault) {
    return switch (fault) {
      case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
          aChallengePage(TranzilaAnswers.NOT_FOUND, TranzilaChallengePage.NO_SUCH_ADDRESS);
      case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
          aChallengePage(TranzilaAnswers.METHOD_NOT_ALLOWED, TranzilaChallengePage.NO_SUCH_METHOD);
      case A_FAILURE_NOTHING_ELSE_ANSWERED ->
          new ProviderWireAnswer.NothingThisContractPublishes(
              WHAT_THE_CHALLENGE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
    };
  }

  private static ProviderWireAnswer aChallengePage(int httpStatus, String said) {
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        httpStatus,
        TranzilaChallengePage.CONTENT_TYPE,
        TranzilaChallengePage.refusing(said).getBytes(StandardCharsets.UTF_8),
        TranzilaChallengePage.DEFENCES);
  }

  private static ProviderWireAnswer aHostedFieldsPage(int httpStatus, String said) {
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        httpStatus,
        TranzilaHostedFieldsPage.CONTENT_TYPE,
        TranzilaHostedFieldsRefusalPage.refusing(said).getBytes(StandardCharsets.UTF_8),
        TranzilaHostedFieldsRefusalPage.DEFENCES);
  }

  private static ProviderWireAnswer handshakeShape(int httpStatus, String message) {
    TranzilaBody body = CreateHandshake.theHandshakeError(httpStatus, message);
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        httpStatus, TranzilaFields.JSON, body.bytes());
  }

  private static ProviderWireAnswer aPage(int httpStatus, String said) {
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        httpStatus,
        TranzilaDirectNgPage.CONTENT_TYPE,
        TranzilaDirectNgPage.refusing(said).getBytes(StandardCharsets.UTF_8),
        TranzilaDirectNgPage.DEFENCES);
  }

  private static ProviderWireAnswer errorSchema(int httpStatus, String code, String message) {
    return new ProviderWireAnswer.InThisProvidersOwnWords(
        httpStatus,
        TranzilaFields.JSON,
        TranzilaJsonWriter.toBytes(TranzilaAnswers.theErrorSchema(code, message)));
  }
}
