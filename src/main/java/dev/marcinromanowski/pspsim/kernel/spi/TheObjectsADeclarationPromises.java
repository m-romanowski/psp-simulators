package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TheObjectsADeclarationPromises {

  public sealed interface Agreement {

    record TheDeclarationAndTheObjectsAgree() implements Agreement {}

    record TheyDisagree(String why) implements Agreement {}
  }

  static final String THE_LISTING_REFUSED_A_CURSOR_IT_NEVER_GOT =
      "the objects of this run, asked for from the first of them and refused as a cursor this"
          + " provider holds no object under";

  private record WhatTheObjectsPublish(List<String> publishingTheFacts, int offeringACard) {}

  private TheObjectsADeclarationPromises() {}

  public static Agreement of(
      SimulatorProvider module, TestRunId testRunId, List<String> objectsOfThisRun) {
    List<String> publishing = new ArrayList<>();
    int offeringACard = 0;
    for (String providerObjectId : objectsOfThisRun) {
      Optional<ProviderObjectState> held = module.state(testRunId, providerObjectId);
      if (held.isEmpty()) {
        return new Agreement.TheyDisagree(
            "provider "
                + module.id().value()
                + " was read for the object "
                + providerObjectId
                + " and holds none of that name in test run "
                + testRunId.value()
                + ", so this reading says nothing about the objects its declaration promises");
      }
      if (held.get().hostedSession() instanceof HostedSessionFacts.AnObjectAPayerFinishes) {
        publishing.add(providerObjectId);
        if (module.hostedCompletion(testRunId, providerObjectId).namesACard()) {
          offeringACard++;
        }
      }
    }
    return theDeclarationOf(
        module, testRunId, new WhatTheObjectsPublish(List.copyOf(publishing), offeringACard));
  }

  private static Agreement theDeclarationOf(
      SimulatorProvider module, TestRunId testRunId, WhatTheObjectsPublish published) {
    return switch (module.hostedPages()) {
      case HostedPageContract.NoPageAPayerFinishes _ ->
          nothingWaitsOn(module, testRunId, published);
      case HostedPageContract.ObjectsAPayerFinishes declared ->
          theObjectsItNames(module, declared, published);
    };
  }

  private static Agreement nothingWaitsOn(
      SimulatorProvider module, TestRunId testRunId, WhatTheObjectsPublish published) {
    List<String> waiting =
        theObjectsOfThisRunThatWait(module, testRunId, published.publishingTheFacts());
    if (waiting.isEmpty()) {
      return new Agreement.TheDeclarationAndTheObjectsAgree();
    }
    return new Agreement.TheyDisagree(
        "provider "
            + module.id().value()
            + " declares that its contract keeps no object waiting for a payer, and the objects "
            + waiting
            + " of test run "
            + testRunId.value()
            + " publish the facts of one, so a screen listing this provider among the ones with no"
            + " such page would say so beside an object that is waiting on one");
  }

  private static List<String> theObjectsOfThisRunThatWait(
      SimulatorProvider module, TestRunId testRunId, List<String> read) {
    List<String> waiting = new ArrayList<>(read);
    switch (module.objects(
        new ProviderObjectQuery(testRunId, null, ProviderObjectQuery.LARGEST_LIMIT))) {
      case ProviderObjectPage.NoSuchCursor _ ->
          waiting.add(THE_LISTING_REFUSED_A_CURSOR_IT_NEVER_GOT);
      case ProviderObjectPage.Listed listed -> {
        for (ProviderObjectState object : listed.objects()) {
          if (object.hostedSession() instanceof HostedSessionFacts.AnObjectAPayerFinishes
              && !answersToAnyOf(object, read)) {
            waiting.add(object.providerObjectId());
          }
        }
      }
    }
    return List.copyOf(waiting);
  }

  private static boolean answersToAnyOf(ProviderObjectState object, List<String> read) {
    for (String providerObjectId : read) {
      if (object.identity().answersTo(providerObjectId)) {
        return true;
      }
    }
    return false;
  }

  private static Agreement theObjectsItNames(
      SimulatorProvider module,
      HostedPageContract.ObjectsAPayerFinishes declared,
      WhatTheObjectsPublish published) {
    int promised = declared.theObjectsItKeepsWaiting().size();
    if (published.publishingTheFacts().size() != promised) {
      return new Agreement.TheyDisagree(
          "provider "
              + module.id().value()
              + " declares it keeps "
              + promised
              + " object(s) waiting for a payer, "
              + declared.asThisProviderNamesThem()
              + ", and "
              + published.publishingTheFacts().size()
              + " of the objects read here publish the facts of one, "
              + published.publishingTheFacts()
              + ", so a screen listing this provider among the ones that host a page would report"
              + " an object nothing behind it can show anything about");
    }
    int namingACard = declared.howManyOfThemAPayerNamesACardOn();
    if (published.offeringACard() != namingACard) {
      return new Agreement.TheyDisagree(
          "provider "
              + module.id().value()
              + " declares that a payer names a card on "
              + namingACard
              + " of the "
              + promised
              + " object(s) it keeps waiting, and "
              + published.offeringACard()
              + " of the "
              + published.publishingTheFacts().size()
              + " read here offer a payer a card to finish with, so the declaration tells a tester"
              + " to expect a card list on an object whose payer step names none, or none on an"
              + " object whose payer step names one");
    }
    return new Agreement.TheDeclarationAndTheObjectsAgree();
  }
}
