package dev.marcinromanowski.pspsim.kernel.spi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class WhatAProviderAnswersOffItsRoutes {

  public static final String DIAGNOSTIC_CONTENT_TYPE = "text/plain; charset=utf-8";

  static final String AN_ADDRESS_IT_DOES_NOT_SERVE =
      "an address inside its own namespace that it does not serve";

  static final String A_METHOD_IT_DOES_NOT_ANSWER = "a method one of its addresses does not answer";

  static final String NO_ROUTE_OF_THIS_BUILD_ANSWERS_IT = "no route of this build answers it";

  static final String NO_ROUTE_ANSWERS_IT_WITH_THIS_METHOD =
      "no route of this build answers it with this HTTP method";

  private final ProviderRegistry providers;

  public WhatAProviderAnswersOffItsRoutes(ProviderRegistry providers) {
    this.providers = providers;
  }

  public Optional<WireAnswer> to(WireFault fault, String address, int statusNothingElseChose) {
    Optional<SimulatorProvider> claiming =
        ProviderWireRoutes.theDeepestClaimOn(providers.modules(), address);
    if (claiming.isPresent()) {
      return Optional.of(answeredBy(claiming.get(), fault, address, statusNothingElseChose));
    }
    return whoeverThisAddressCouldHaveBeenMeantFor(fault, address, statusNothingElseChose);
  }

  private Optional<WireAnswer> whoeverThisAddressCouldHaveBeenMeantFor(
      WireFault fault, String address, int statusNothingElseChose) {
    for (String path : fromTheDeepestSegmentUp(address)) {
      List<SimulatorProvider> publishing = whoPublishesUnder(path);
      if (publishing.isEmpty()) {
        continue;
      }
      if (publishing.size() == 1) {
        return Optional.of(answeredBy(publishing.get(0), fault, address, statusNothingElseChose));
      }
      return Optional.of(
          switch (fault) {
            case A_FAILURE_NOTHING_ELSE_ANSWERED ->
                WireAnswer.withNothingInIt(statusNothingElseChose);
            case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
                inPlainText(
                    statusNothingElseChose,
                    thePathMoreThanOneProviderPublishesUnder(
                        NO_ROUTE_OF_THIS_BUILD_ANSWERS_IT, address, path, publishing));
            case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
                inPlainText(
                    statusNothingElseChose,
                    thePathMoreThanOneProviderPublishesUnder(
                        NO_ROUTE_ANSWERS_IT_WITH_THIS_METHOD, address, path, publishing));
          });
    }
    return Optional.empty();
  }

  private List<SimulatorProvider> whoPublishesUnder(String path) {
    List<SimulatorProvider> publishing = new ArrayList<>();
    for (SimulatorProvider provider : providers.modules()) {
      if (!provider.wireRoutes().theNamespacesUnder(path).isEmpty()) {
        publishing.add(provider);
      }
    }
    return List.copyOf(publishing);
  }

  static List<String> fromTheDeepestSegmentUp(String address) {
    List<String> walked = new ArrayList<>();
    for (int at = 2; at < address.length(); at++) {
      if (address.charAt(at) == '/') {
        walked.add(address.substring(0, at));
      }
    }
    if (!address.isEmpty() && !address.endsWith("/")) {
      walked.add(address);
    }
    Collections.reverse(walked);
    return List.copyOf(walked);
  }

  static String thePathMoreThanOneProviderPublishesUnder(
      String whatWentWrong, String address, String path, List<SimulatorProvider> publishing) {
    List<String> whoAnswersWhat = new ArrayList<>();
    for (SimulatorProvider provider : publishing) {
      whoAnswersWhat.add(
          provider.id().value()
              + " answers under "
              + String.join(", ", provider.wireRoutes().theNamespacesUnder(path)));
    }
    return "no provider of this build claims the address "
        + address
        + ", and "
        + whatWentWrong
        + ". More than one provider of this build publishes under the path "
        + path
        + ": "
        + String.join("; ", whoAnswersWhat)
        + ". The address asked for is inside none of those namespaces, which are matched on"
        + " segment boundaries, so it belongs to none of those providers, and nothing in it says"
        + " which of them it was aimed at. This simulator will not pick one: an answer in the"
        + " field names and codes of the wrong one of them is indistinguishable from the right"
        + " one's to every client that logs it, and that is worse than an answer in neither's. So"
        + " this simulator answers for itself here, in text/plain, which is not a media type any"
        + " provider of this build answers a client with and can therefore never be read as one"
        + " of their envelopes. The addresses those providers do answer are the ones named above.";
  }

  private static WireAnswer answeredBy(
      SimulatorProvider provider, WireFault fault, String address, int statusNothingElseChose) {
    return switch (provider.theAnswerOffItsRoutes(fault, address)) {
      case ProviderWireAnswer.InThisProvidersOwnWords own ->
          new WireAnswer(own.httpStatus(), own.contentType(), own.body(), own.headers());
      case ProviderWireAnswer.NothingThisContractPublishes missing ->
          switch (fault) {
            case A_FAILURE_NOTHING_ELSE_ANSWERED ->
                WireAnswer.withNothingInIt(statusNothingElseChose);
            case AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE ->
                inPlainText(
                    statusNothingElseChose,
                    theGapItNames(provider, AN_ADDRESS_IT_DOES_NOT_SERVE, missing));
            case A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER ->
                inPlainText(
                    statusNothingElseChose,
                    theGapItNames(provider, A_METHOD_IT_DOES_NOT_ANSWER, missing));
          };
    };
  }

  private static WireAnswer inPlainText(int httpStatus, String said) {
    return new WireAnswer(
        httpStatus, DIAGNOSTIC_CONTENT_TYPE, said.getBytes(StandardCharsets.UTF_8));
  }

  static String theGapItNames(
      SimulatorProvider provider,
      String whatWentWrong,
      ProviderWireAnswer.NothingThisContractPublishes missing) {
    return "provider "
        + provider.id().value()
        + " publishes no answer for "
        + whatWentWrong
        + ", so this simulator has none to give and says so here in text/plain, which is not a"
        + " media type this provider answers a client with and can therefore never be read as"
        + " one of its envelopes. An envelope in the vendor's own field names and codes, invented"
        + " here because the vendor publishes none, is indistinguishable from the vendor's to"
        + " every client that logs one. What this contract does publish instead: "
        + missing.whatThisContractPublishesInstead();
  }
}
