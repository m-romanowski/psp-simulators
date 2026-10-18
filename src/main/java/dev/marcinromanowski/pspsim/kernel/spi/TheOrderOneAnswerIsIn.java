package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.List;

public final class TheOrderOneAnswerIsIn {

  public static final String ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN =
      "one answer for several identifiers is in the order those identifiers were asked in: each"
          + " object the run holds under one of them appears once, at the place of the earliest"
          + " asked identifier it answers to. The order asked is the only order either caller of"
          + " this answer expresses, since both walk the identifiers they asked for and resolve"
          + " each one by scanning this list, and it is the order that reading one identifier at a"
          + " time produces, so a provider that never learns to answer in bulk obeys it without"
          + " doing anything while the providers that read their own stores no longer each answer in"
          + " whatever order those stores return. The order is observable in one way: which object"
          + " a caller resolves an identifier to when two of them answer to it. Two such objects"
          + " sit at that identifier's place in the order the provider read them, which is the"
          + " order its single read walks the same stores in, so a caller resolves that identifier"
          + " to the object a single read of it answers, unless one of the two was already placed"
          + " under an earlier asked identifier it also answers to, where the place that earlier"
          + " identifier asked for and the order the provider read the two in cannot both be kept";

  public static final String ONE_OBJECT_IS_READ_ONCE_HOWEVER_MANY_OF_ITS_NAMES_ARE_ASKED =
      "a screen names one object under every identifier it answers to and asks for all of them at"
          + " once, and an object already read under one of its names is the same object under the"
          + " rest, so reading it again costs a second read and answers a second record of it"
          + " whose every value derived from the clock has moved on";

  private TheOrderOneAnswerIsIn() {}

  public static List<ProviderObjectState> theObjectsAskedFor(
      List<String> asked, List<ProviderObjectState> read) {
    List<ProviderObjectState> answer = new ArrayList<>();
    for (String identifier : asked) {
      for (ProviderObjectState state : read) {
        if (state.identity().answersTo(identifier) && !answer.contains(state)) {
          answer.add(state);
        }
      }
    }
    return List.copyOf(answer);
  }

  public static boolean oneOfTheseAlreadyAnswersTo(
      List<ProviderObjectState> read, String identifier) {
    for (ProviderObjectState state : read) {
      if (state.identity().answersTo(identifier)) {
        return true;
      }
    }
    return false;
  }
}
