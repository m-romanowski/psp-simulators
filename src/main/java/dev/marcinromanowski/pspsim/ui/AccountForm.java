package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.AccountProperty;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationRefusal;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.FieldShape;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class AccountForm {

  public static final String SUBMITTED_UNIT_PREFIX = "unitOf.";

  public static final String A_CALLBACK_TARGET_BELONGS_TO_A_NAMED_ACCOUNT =
      "a callback target is configured for one account, because that is what a provider looks it"
          + " up by when it has a callback to post, so name the account identity above before"
          + " naming a callback target for it";

  public static final String A_SECRET_IS_NEVER_HANDED_BACK =
      "retype this; the simulator never hands a secret back to a screen, so a refusal empties it";

  public static final String THE_IDENTITY_NAMES_THE_ACCOUNT =
      "The name this test run knows the account by. Every screen, every fixture and every callback"
          + " target names the account by it, and the configuration call this form makes is"
          + " refused without one. Naming an account this run already configures replaces that"
          + " account whole.";

  public static final String WHERE_THIS_ACCOUNT_S_CALLBACKS_GO =
      "Where this provider posts the callbacks the payments of this account cause. A provider"
          + " refuses to post one to an address this test run does not configure here, so a run"
          + " with no callback target configured leaves every callback unaddressable. Leaving this"
          + " empty configures none, and configuring this account again without it removes the one"
          + " it had, because naming an account replaces that account whole.";

  public static final String AN_IDENTITY_A_TESTER_WOULD_NAME = "merchant-1";

  public static final String A_CALLBACK_TARGET_A_TESTER_WOULD_NAME =
      "https://merchant.example/callbacks";

  public record Choice(String value, String whatItMeans, boolean chosen) {}

  public record Field(
      String key,
      String label,
      String whatItIsFor,
      boolean required,
      boolean secret,
      String value,
      boolean mustBeRetyped,
      boolean refused,
      List<Choice> choices,
      @Nullable String example,
      List<ADurationInWords.Offered> units,
      FieldShape shape) {

    public Field(
        String key,
        String label,
        String whatItIsFor,
        boolean required,
        boolean secret,
        String value,
        boolean mustBeRetyped,
        boolean refused,
        String example) {
      this(
          key,
          label,
          whatItIsFor,
          required,
          secret,
          value,
          mustBeRetyped,
          refused,
          List.of(),
          example,
          List.of(),
          FieldShape.FREE_TEXT);
    }

    public Field {
      choices = List.copyOf(choices);
      units = List.copyOf(units);
    }

    public boolean isTypedIn() {
      return choices.isEmpty() && units.isEmpty() && !isABlockOfManyLines();
    }

    public boolean isPickedFromAList() {
      return !choices.isEmpty();
    }

    public boolean isASpanOfTime() {
      return !units.isEmpty();
    }

    public boolean isABlockOfManyLines() {
      return shape == FieldShape.A_BLOCK_OF_MANY_LINES;
    }

    public @Nullable String theHintTheBoxShows() {
      return example;
    }

    public String theUnitItIsCountedIn() {
      return "the unit " + label + " is counted in";
    }

    public String theParameterItArrivesUnder() {
      return TheParameterATestersValueArrivesUnder.onAFormControlFor(key);
    }

    public String theUnitParameterItArrivesUnder() {
      return SUBMITTED_UNIT_PREFIX + key;
    }
  }

  public record View(
      String provider,
      String testRunId,
      String accountId,
      List<Field> fields,
      List<String> accountsAlreadyConfigured,
      String callbackUrl,
      List<String> callbackTargetsAlreadyConfigured,
      boolean secretsMustBeRetyped) {

    public View {
      fields = List.copyOf(fields);
      accountsAlreadyConfigured = List.copyOf(accountsAlreadyConfigured);
      callbackTargetsAlreadyConfigured = List.copyOf(callbackTargetsAlreadyConfigured);
    }

    public Field accountIdentity() {
      return new Field(
          "accountId",
          "account identity",
          THE_IDENTITY_NAMES_THE_ACCOUNT,
          true,
          false,
          accountId,
          false,
          false,
          AN_IDENTITY_A_TESTER_WOULD_NAME);
    }

    public Field callbackTarget() {
      return new Field(
          "callbackUrl",
          "callback target for this account",
          WHERE_THIS_ACCOUNT_S_CALLBACKS_GO,
          false,
          false,
          callbackUrl,
          false,
          false,
          A_CALLBACK_TARGET_A_TESTER_WOULD_NAME);
    }

    public String theRefusalAsTheBoxesOnThisScreenAreNamed(ConfigurationRefusal refusal) {
      String key = refusal.field();
      if (key == null) {
        return refusal.message();
      }
      for (Field box : everyFieldThisFormAsksFor()) {
        if (box.key().equals(key)) {
          return AccountProperty.theRefusalWithTheFieldNamed(refusal.message(), box.label());
        }
      }
      return refusal.message();
    }

    public List<Field> everyFieldThisFormAsksFor() {
      List<Field> asked = new ArrayList<>();
      asked.add(accountIdentity());
      asked.addAll(fields);
      asked.add(callbackTarget());
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

    public boolean asksForASecret() {
      for (Field field : everyFieldThisFormAsksFor()) {
        if (field.secret()) {
          return true;
        }
      }
      return false;
    }
  }

  private AccountForm() {}

  public static View of(
      String provider,
      String testRunId,
      @Nullable String accountId,
      ProviderAccountFields declared,
      Map<String, String> submitted,
      Map<String, String> unitsPicked,
      List<String> accountsAlreadyConfigured,
      @Nullable String callbackUrl,
      List<String> callbackTargetsAlreadyConfigured,
      @Nullable ConfigurationRefusal refusal) {
    List<Field> fields = new ArrayList<>();
    boolean refused = refusal != null;
    String fieldRefused = refusal == null ? null : refusal.field();
    for (ConfigurableField field : declared.fields()) {
      String typed = submitted.getOrDefault(field.key(), "");
      if (field.holdsADuration()) {
        fields.add(
            theSpanOfTimeItAsksFor(
                field, typed, unitsPicked.get(field.key()), field.key().equals(fieldRefused)));
        continue;
      }
      fields.add(
          new Field(
              field.key(),
              field.label(),
              field.whatItIsFor(),
              field.required(),
              field.secret(),
              field.secret() ? "" : typed,
              refused && field.secret(),
              field.key().equals(fieldRefused),
              theChoicesItOffers(field, typed),
              field.example(),
              List.of(),
              field.shape()));
    }
    return new View(
        provider,
        testRunId,
        accountId == null ? "" : accountId,
        List.copyOf(fields),
        accountsAlreadyConfigured,
        callbackUrl == null ? "" : callbackUrl,
        callbackTargetsAlreadyConfigured,
        refused && !declared.secretKeys().isEmpty());
  }

  private static Field theSpanOfTimeItAsksFor(
      ConfigurableField field, String typed, @Nullable String unitPicked, boolean refused) {
    Optional<ADurationInWords.Unit> picked = ADurationInWords.theUnitNamed(unitPicked);
    Optional<ADurationInWords> written = ADurationInWords.read(typed);
    Optional<ADurationInWords> itIsNaturallyCountedIn = ADurationInWords.read(field.example());
    ADurationInWords.Unit unit =
        picked
            .or(() -> written.map(ADurationInWords::unit))
            .or(() -> itIsNaturallyCountedIn.map(ADurationInWords::unit))
            .orElse(ADurationInWords.THE_UNIT_A_FORM_FALLS_BACK_ON);
    return new Field(
        field.key(),
        field.label(),
        field.whatItIsFor(),
        field.required(),
        field.secret(),
        picked.isPresent() ? typed : written.map(AccountForm::howMany).orElse(""),
        false,
        refused,
        List.of(),
        itIsNaturallyCountedIn.map(AccountForm::howMany).orElse(null),
        ADurationInWords.theUnitsOffered(unit),
        field.shape());
  }

  private static String howMany(ADurationInWords span) {
    return Long.toString(span.amount());
  }

  private static List<Choice> theChoicesItOffers(ConfigurableField field, String typed) {
    List<Choice> offered = new ArrayList<>();
    for (AcceptedValue accepted : field.acceptedValues()) {
      offered.add(
          new Choice(accepted.value(), accepted.whatItMeans(), accepted.value().equals(typed)));
    }
    return List.copyOf(offered);
  }

  public static Map<String, String> submittedUnitsOf(Map<String, String> parameters) {
    Map<String, String> named = new LinkedHashMap<>();
    for (Map.Entry<String, String> parameter : parameters.entrySet()) {
      String name = parameter.getKey();
      if (name.startsWith(SUBMITTED_UNIT_PREFIX)) {
        named.put(name.substring(SUBMITTED_UNIT_PREFIX.length()), parameter.getValue());
      }
    }
    return Map.copyOf(named);
  }

  private static @Nullable String theValueItSends(
      ConfigurableField field, @Nullable String typed, @Nullable String unitPicked) {
    if (!field.holdsADuration()) {
      return typed;
    }
    return ADurationInWords.theIsoFormOf(typed, unitPicked);
  }

  public static ActionVerdict.Refused theRefusalOfACallbackTargetForNoAccount() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            ConfigurationAdmission.THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD));
  }

  public static boolean namesACallbackTargetForNoAccount(
      @Nullable String accountId, @Nullable String callbackUrl) {
    return callbackUrl != null
        && !callbackUrl.isBlank()
        && (accountId == null || accountId.isBlank());
  }

  public static DeclaredConfiguration configurationOf(
      String testRunId,
      @Nullable String accountId,
      ProviderAccountFields declared,
      Map<String, String> submitted,
      Map<String, String> unitsPicked,
      @Nullable String callbackUrl) {
    Map<String, String> configuration = new LinkedHashMap<>();
    for (ConfigurableField field : declared.fields()) {
      String typed =
          theValueItSends(field, submitted.get(field.key()), unitsPicked.get(field.key()));
      if (typed != null && !typed.isBlank()) {
        configuration.put(field.key(), typed.strip());
      }
    }
    return new DeclaredConfiguration(
        testRunId,
        null,
        List.of(new DeclaredAccount(theIdentityAsked(accountId), Map.copyOf(configuration))),
        theCallbackTargetAsked(accountId, callbackUrl),
        null,
        null);
  }

  private static @Nullable List<DeclaredWebhookTarget> theCallbackTargetAsked(
      @Nullable String accountId, @Nullable String callbackUrl) {
    if (callbackUrl == null || callbackUrl.isBlank()) {
      return null;
    }
    return List.of(new DeclaredWebhookTarget(theIdentityAsked(accountId), callbackUrl.strip()));
  }

  private static @Nullable String theIdentityAsked(@Nullable String accountId) {
    if (accountId == null || accountId.isBlank()) {
      return null;
    }
    return accountId.strip();
  }
}
