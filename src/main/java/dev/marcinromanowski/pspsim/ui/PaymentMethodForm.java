package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredCard;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public final class PaymentMethodForm {

  public static final String SUBMITTED_ID_PREFIX = "providerId.";

  public static final String A_FIXTURE_HOLDS_NO_CARD_NUMBER =
      "this form asks for the last four digits, the brand, the expiry and the funding, and for no"
          + " card number and no verification code: the simulator stores neither anywhere, so a"
          + " seeded card has neither to leak";

  public record Field(
      String key,
      String label,
      String whatItIsFor,
      boolean required,
      boolean secret,
      String value,
      boolean refused,
      @Nullable String theHintTheBoxShows) {

    public Field(
        String key,
        String label,
        String whatItIsFor,
        boolean required,
        boolean secret,
        String value,
        boolean refused) {
      this(key, label, whatItIsFor, required, secret, value, refused, null);
    }
  }

  public record Refused(String key, String instead) {}

  private enum WhenItIsRefusedEmpty {
    ALWAYS,
    ONLY_WHEN_THE_TYPE_DESCRIBES_A_CARD,
    NEVER
  }

  private record Asked(
      String key, String label, String whatItIsFor, WhenItIsRefusedEmpty refusedEmpty) {}

  private static final List<Asked> THE_FIXTURE_THIS_SIMULATOR_OWNS =
      List.of(
          new Asked(
              "merchantId",
              "account",
              "The account this test run configures that the seeded method belongs to. The seed is"
                  + " refused as merchantId without one, and a provider that resolves a stored"
                  + " method through the account's own credential can answer for no other.",
              WhenItIsRefusedEmpty.ALWAYS),
          new Asked(
              "ownerId",
              "owner",
              "The customer this simulator files the method under. It is this simulator's own"
                  + " name for the owner and not a provider's, so a provider that publishes no"
                  + " customer of its own still has somewhere to put one.",
              WhenItIsRefusedEmpty.ALWAYS),
          new Asked(
              "type",
              "type",
              "What kind of instrument is being seeded. It decides whether the card boxes below"
                  + " are read at all: a type that describes no card is refused for carrying"
                  + " one.",
              WhenItIsRefusedEmpty.ALWAYS),
          new Asked(
              "alias",
              "alias",
              "The name a test names this fixture by. One test run claims an alias once, so"
                  + " seeding the same alias twice is answered as a conflict rather than"
                  + " replacing the first.",
              WhenItIsRefusedEmpty.ALWAYS),
          new Asked(
              "brand",
              "card brand",
              "The brand a payer would read off the card. The simulator stores it and matches"
                  + " nothing on it; it is refused as card.brand when the type describes a card"
                  + " and it is empty.",
              WhenItIsRefusedEmpty.ONLY_WHEN_THE_TYPE_DESCRIBES_A_CARD),
          new Asked(
              "last4",
              "last four",
              "The last four digits of the card. They are all of the number this simulator keeps,"
                  + " and they are refused as card.last4 when the type describes a card and they"
                  + " are empty.",
              WhenItIsRefusedEmpty.ONLY_WHEN_THE_TYPE_DESCRIBES_A_CARD),
          new Asked(
              "expMonth",
              "expiry month",
              "The month the card expires in, as a whole number. It is refused as card.expMonth"
                  + " when the type describes a card and it is empty.",
              WhenItIsRefusedEmpty.ONLY_WHEN_THE_TYPE_DESCRIBES_A_CARD),
          new Asked(
              "expYear",
              "expiry year",
              "The year the card expires in, as a whole number. It is refused as card.expYear"
                  + " when the type describes a card and it is empty.",
              WhenItIsRefusedEmpty.ONLY_WHEN_THE_TYPE_DESCRIBES_A_CARD),
          new Asked(
              "funding",
              "funding",
              "Whether the card draws on credit, on a balance or on something else. The simulator"
                  + " stores it and matches nothing on it; it is refused as card.funding when the"
                  + " type describes a card and it is empty.",
              WhenItIsRefusedEmpty.ONLY_WHEN_THE_TYPE_DESCRIBES_A_CARD),
          new Asked(
              "defaultScenario",
              "default scenario",
              "The scenario every payment this fixture drives follows unless the payment names"
                  + " another. Leaving it empty seeds a method no scenario is written against,"
                  + " which the provider then answers for as it answers for any other.",
              WhenItIsRefusedEmpty.NEVER));

  public record View(
      String provider,
      String testRunId,
      String merchantId,
      String ownerId,
      String type,
      String fixtureAlias,
      String brand,
      String last4,
      String expMonth,
      String expYear,
      String funding,
      String defaultScenario,
      List<Field> providerIds,
      List<Refused> keysItRefuses,
      List<String> typesItAccepts,
      List<String> scenariosAlreadyWritten) {

    public View {
      providerIds = List.copyOf(providerIds);
      keysItRefuses = List.copyOf(keysItRefuses);
      typesItAccepts = List.copyOf(typesItAccepts);
      scenariosAlreadyWritten = List.copyOf(scenariosAlreadyWritten);
    }

    public boolean readsNoProviderNativeId() {
      return providerIds.isEmpty();
    }

    public boolean theTypeChosenDescribesACard() {
      return PaymentMethodKind.of(type).filter(PaymentMethodKind::describesACard).isPresent();
    }

    public Field fieldNamed(String key) {
      for (Asked asked : THE_FIXTURE_THIS_SIMULATOR_OWNS) {
        if (asked.key().equals(key)) {
          return new Field(
              asked.key(),
              asked.label(),
              asked.whatItIsFor(),
              switch (asked.refusedEmpty()) {
                case ALWAYS -> true;
                case ONLY_WHEN_THE_TYPE_DESCRIBES_A_CARD -> theTypeChosenDescribesACard();
                case NEVER -> false;
              },
              false,
              "",
              false);
        }
      }
      throw new IllegalArgumentException(
          "this form asks for no field named "
              + key
              + ", and a screen that drew one would print a label and a badge for a box the seed"
              + " never reads");
    }

    public List<Field> everyFieldThisFormAsksFor() {
      List<Field> asked = new ArrayList<>();
      for (Asked own : THE_FIXTURE_THIS_SIMULATOR_OWNS) {
        asked.add(fieldNamed(own.key()));
      }
      asked.addAll(providerIds);
      return List.copyOf(asked);
    }

    public int fieldsThisFormRequires() {
      int required = 0;
      for (Field field : everyFieldThisFormAsksFor()) {
        if (field.required()) {
          required++;
        }
      }
      return required;
    }

    public int fieldsThisFormHas() {
      return everyFieldThisFormAsksFor().size();
    }
  }

  private PaymentMethodForm() {}

  public static View of(
      String provider,
      String testRunId,
      ProviderPaymentMethodFields declared,
      Map<String, String> submitted,
      List<ScenarioRule> alreadyWritten,
      @Nullable String refusedProperty) {
    List<Field> fields = new ArrayList<>();
    for (ConfigurableField field : declared.fields()) {
      fields.add(
          new Field(
              field.key(),
              field.label(),
              field.whatItIsFor(),
              field.required(),
              field.secret(),
              field.secret() ? "" : submitted.getOrDefault(SUBMITTED_ID_PREFIX + field.key(), ""),
              ("providerIds." + field.key()).equals(refusedProperty),
              field.example()));
    }
    List<Refused> refused = new ArrayList<>();
    for (String key : ProviderPaymentMethodFields.THE_PUBLISHED_KEYS) {
      if (declared.field(key).isEmpty()) {
        refused.add(new Refused(key, declared.theRefusalOf(key)));
      }
    }
    List<String> scenarioIds = new ArrayList<>();
    for (ScenarioRule rule : alreadyWritten) {
      scenarioIds.add(rule.scenarioId());
    }
    return new View(
        provider,
        testRunId,
        submitted.getOrDefault("merchantId", ""),
        submitted.getOrDefault("ownerId", ""),
        submitted.getOrDefault("type", PaymentMethodKind.CARD.publishedName()),
        submitted.getOrDefault("alias", ""),
        submitted.getOrDefault("brand", ""),
        submitted.getOrDefault("last4", ""),
        submitted.getOrDefault("expMonth", ""),
        submitted.getOrDefault("expYear", ""),
        submitted.getOrDefault("funding", ""),
        submitted.getOrDefault("defaultScenario", ""),
        List.copyOf(fields),
        List.copyOf(refused),
        theTypesItAccepts(),
        List.copyOf(scenarioIds));
  }

  public static ActionVerdict.Refused theRefusalOfAFixtureThisSimulatorCannotRead() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED));
  }

  public static ActionVerdict.Refused theRefusalOfAnUnconfirmedRemoval() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET));
  }

  public static DeclaredPaymentMethodFixture fixtureOf(
      String testRunId, Map<String, String> submitted) {
    String type = submitted.getOrDefault("type", "");
    return new DeclaredPaymentMethodFixture(
        testRunId,
        typedOrNothing(submitted.get("merchantId")),
        typedOrNothing(submitted.get("ownerId")),
        typedOrNothing(type),
        typedOrNothing(submitted.get("alias")),
        cardOf(type, submitted),
        providerIdsOf(submitted),
        typedOrNothing(submitted.get("defaultScenario")));
  }

  public static SequencedMap<String, String> providerIdsOf(Map<String, String> parameters) {
    LinkedHashMap<String, String> named = new LinkedHashMap<>();
    for (Map.Entry<String, String> parameter : parameters.entrySet()) {
      String name = parameter.getKey();
      if (name.startsWith(SUBMITTED_ID_PREFIX) && !parameter.getValue().isBlank()) {
        named.put(name.substring(SUBMITTED_ID_PREFIX.length()), parameter.getValue().strip());
      }
    }
    return Collections.unmodifiableSequencedMap(named);
  }

  private static @Nullable DeclaredCard cardOf(String type, Map<String, String> submitted) {
    if (!PaymentMethodKind.CARD.publishedName().equals(type)) {
      return null;
    }
    return new DeclaredCard(
        typedOrNothing(submitted.get("brand")),
        typedOrNothing(submitted.get("last4")),
        wholeNumberOrNothing("card.expMonth", submitted.get("expMonth")),
        wholeNumberOrNothing("card.expYear", submitted.get("expYear")),
        typedOrNothing(submitted.get("funding")));
  }

  private static List<String> theTypesItAccepts() {
    List<String> names = new ArrayList<>();
    for (PaymentMethodKind kind : PaymentMethodKind.values()) {
      names.add(kind.publishedName());
    }
    return List.copyOf(names);
  }

  private static @Nullable String typedOrNothing(@Nullable String typed) {
    if (typed == null || typed.isBlank()) {
      return null;
    }
    return typed.strip();
  }

  private static @Nullable Integer wholeNumberOrNothing(String property, @Nullable String typed) {
    String named = typedOrNothing(typed);
    if (named == null) {
      return null;
    }
    try {
      return Integer.valueOf(named);
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException(
          property
              + " must be a whole number, and this form sent "
              + named
              + "; a screen that dropped it would have asked the control API for a card with no"
              + " expiry and shown you the refusal for a missing field instead of a typed one");
    }
  }
}
