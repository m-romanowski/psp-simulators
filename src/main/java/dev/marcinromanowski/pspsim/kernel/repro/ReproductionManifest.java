package dev.marcinromanowski.pspsim.kernel.repro;

import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ReproductionManifest(
    @Nullable List<CarriedContent> replayable, @Nullable List<CarriedContent> readable) {

  public static final String ACCOUNTS = "accounts";

  public static final String WEBHOOK_TARGETS = "webhookTargets";

  public static final String ROUTE_ALIASES = "routeAliases";

  public static final String SETTINGS = "settings";

  public static final String CLOCK = "clock";

  public static final String FIXTURES = "fixtures";

  public static final String SCENARIO_RULES = "scenarioRules";

  public static final String STATE_SNAPSHOT = "stateSnapshot";

  public static final String LEDGER = "ledger";

  public static final String TRAIL = "trail";

  public static final String PROVIDER_EXCHANGES = "providerExchanges";

  public static final String CALLBACKS = "callbacks";

  public static final String CALLBACK_ATTEMPTS = "callbackAttempts";

  public static final String AN_IMPORT_WRITES_IT =
      "an import writes this into the target run, so a bundle taken back leaves the run holding it";

  public static final String A_SIGNED_MESSAGE_VERIFIES_AGAINST_THE_KEY_THAT_MADE_IT =
      "read it, do not replay it: a recorded message carries the signature the key of the run that"
          + " made it produced, and a bundle carries no key, so the same bytes under another"
          + " simulator's key are a signature that verifies against nothing while looking correct";

  public static final String AN_OBJECT_IS_MINTED_BY_THE_EXCHANGE_THAT_CREATED_IT =
      "read it, do not replay it: a provider object and its ledger are written by the exchange that"
          + " created them, and no import creates one, so this says what the run held and is not a"
          + " state another run can be put into";

  public static final String A_TRAIL_IS_WHAT_THIS_SIMULATOR_DID =
      "read it, do not replay it: a trail row is this simulator's own record of an exchange it"
          + " served, and writing one into another simulator's trail would file an event that"
          + " simulator never served";

  public ReproductionManifest {
    replayable = replayable == null ? null : List.copyOf(replayable);
    readable = readable == null ? null : List.copyOf(readable);
  }

  public static ReproductionManifest of(
      @Nullable RunBundle setup, @Nullable ReproductionEvidence evidence) {
    return new ReproductionManifest(theSetupItWritesBack(setup), theEvidenceItOnlyReads(evidence));
  }

  public List<CarriedContent> theContentsAnImportWrites() {
    List<CarriedContent> carried = replayable;
    return carried == null ? List.of() : carried;
  }

  public List<CarriedContent> theContentsNoImportWrites() {
    List<CarriedContent> carried = readable;
    return carried == null ? List.of() : carried;
  }

  public List<String> theContentNamesAnImportWrites() {
    return named(theContentsAnImportWrites());
  }

  public List<String> theContentNamesNoImportWrites() {
    return named(theContentsNoImportWrites());
  }

  private static List<String> named(List<CarriedContent> contents) {
    List<String> names = new ArrayList<>();
    for (CarriedContent content : contents) {
      names.add(content.content());
    }
    return List.copyOf(names);
  }

  private static List<CarriedContent> theSetupItWritesBack(@Nullable RunBundle setup) {
    List<CarriedContent> carried = new ArrayList<>();
    if (setup != null) {
      carried.add(written(ACCOUNTS, setup.carriedAccounts().size()));
      carried.add(written(WEBHOOK_TARGETS, setup.carriedWebhooks().size()));
      carried.add(written(ROUTE_ALIASES, setup.carriedRouteAliases().size()));
      carried.add(written(SETTINGS, setup.carriedSettings().size()));
      carried.add(written(CLOCK, setup.clock() == null ? 0 : 1));
      carried.add(written(FIXTURES, setup.carriedFixtures().size()));
      carried.add(written(SCENARIO_RULES, setup.carriedRules().size()));
    }
    return List.copyOf(carried);
  }

  private static List<CarriedContent> theEvidenceItOnlyReads(
      @Nullable ReproductionEvidence evidence) {
    List<CarriedContent> carried = new ArrayList<>();
    if (evidence != null) {
      carried.add(
          new CarriedContent(
              STATE_SNAPSHOT,
              evidence.object() == null ? 0 : 1,
              AN_OBJECT_IS_MINTED_BY_THE_EXCHANGE_THAT_CREATED_IT));
      carried.add(
          new CarriedContent(
              LEDGER,
              evidence.theOperationsItCarries(),
              AN_OBJECT_IS_MINTED_BY_THE_EXCHANGE_THAT_CREATED_IT));
      carried.add(
          new CarriedContent(
              TRAIL, evidence.carriedTrail().size(), A_TRAIL_IS_WHAT_THIS_SIMULATOR_DID));
      carried.add(
          new CarriedContent(
              PROVIDER_EXCHANGES,
              evidence.theExchangesThatSayWhatWasOnTheWire(),
              A_SIGNED_MESSAGE_VERIFIES_AGAINST_THE_KEY_THAT_MADE_IT));
      carried.add(
          new CarriedContent(
              CALLBACKS,
              evidence.carriedCallbacks().size(),
              A_SIGNED_MESSAGE_VERIFIES_AGAINST_THE_KEY_THAT_MADE_IT));
      carried.add(
          new CarriedContent(
              CALLBACK_ATTEMPTS,
              evidence.theAttemptsItCarries(),
              A_SIGNED_MESSAGE_VERIFIES_AGAINST_THE_KEY_THAT_MADE_IT));
    }
    return List.copyOf(carried);
  }

  private static CarriedContent written(String content, int carried) {
    return new CarriedContent(content, carried, AN_IMPORT_WRITES_IT);
  }
}
