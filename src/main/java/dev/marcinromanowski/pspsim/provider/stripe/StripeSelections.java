package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class StripeSelections {

  static final int LONGEST_CONFIGURATION_ID = 100;

  static final String ENABLED = "enabled";

  static final String ALLOW_REDIRECTS = "allow_redirects";

  static final String THE_SHAPE_OF_AUTOMATIC_PAYMENT_METHODS =
      "a hash carrying "
          + ENABLED
          + " (true or false) and optionally "
          + ALLOW_REDIRECTS
          + " (always or never)";

  static final String THE_SHAPE_OF_A_CONFIGURATION =
      "the id of a payment method configuration, of at most "
          + LONGEST_CONFIGURATION_ID
          + " characters";

  sealed interface Outcome {

    record Selected(StripeSelection selection) implements Outcome {}

    record Refused(StripeError error) implements Outcome {}
  }

  enum Endpoint {
    CREATE_PAYMENT_INTENT("creating a PaymentIntent", true, true, false, false),
    CONFIRM_PAYMENT_INTENT("confirming a PaymentIntent", false, true, true, false),
    CREATE_SETUP_INTENT("creating a SetupIntent", true, true, false, true),
    CONFIRM_SETUP_INTENT("confirming a SetupIntent", false, false, false, true);

    private final String doing;
    private final boolean readsTheDynamicSettings;
    private final boolean readsTheExclusions;
    private final boolean readsAnEmptyExclusionAsUnset;
    private final boolean forASetup;

    Endpoint(
        String doing,
        boolean readsTheDynamicSettings,
        boolean readsTheExclusions,
        boolean readsAnEmptyExclusionAsUnset,
        boolean forASetup) {
      this.doing = doing;
      this.readsTheDynamicSettings = readsTheDynamicSettings;
      this.readsTheExclusions = readsTheExclusions;
      this.readsAnEmptyExclusionAsUnset = readsAnEmptyExclusionAsUnset;
      this.forASetup = forASetup;
    }

    private boolean refusesAWrittenList() {
      return this != CONFIRM_SETUP_INTENT;
    }
  }

  private StripeSelections() {}

  static Outcome onCreatingAPayment(StripeCoreRequest request, String currency) {
    return chosen(request, Endpoint.CREATE_PAYMENT_INTENT, currency, null);
  }

  static Outcome onConfirmingAPayment(StripeCoreRequest request, StripePaymentIntent intent) {
    return chosen(request, Endpoint.CONFIRM_PAYMENT_INTENT, intent.currency(), intent.selection());
  }

  static Outcome onCreatingASetup(StripeCoreRequest request) {
    return chosen(request, Endpoint.CREATE_SETUP_INTENT, null, null);
  }

  static Outcome onConfirmingASetup(StripeCoreRequest request, StripeSetupIntent intent) {
    return chosen(request, Endpoint.CONFIRM_SETUP_INTENT, null, intent.selection());
  }

  private static Outcome chosen(
      StripeCoreRequest request,
      Endpoint endpoint,
      @Nullable String currency,
      @Nullable StripeSelection stored) {
    Asked asked = new Asked();
    StripeError unreadable = read(request, endpoint, asked);
    if (unreadable == null) {
      unreadable = exclusivityOf(asked, stored);
    }
    if (unreadable != null) {
      return new Outcome.Refused(unreadable);
    }
    List<String> allowed = asked.allowed;
    if (allowed != null) {
      return allowedOnly(allowed, endpoint, currency);
    }
    List<String> listed = asked.listed;
    if (listed != null) {
      return listed(listed, asked.automatic, asked.allowRedirects, endpoint, currency);
    }
    if (stored != null) {
      List<String> excluded = asked.excluded;
      if (excluded == null && !(asked.exclusionUnset && stored.dynamic())) {
        return new Outcome.Selected(stored);
      }
      return dynamic(stored.allowRedirects(), excluded, stored.configuration(), endpoint, currency);
    }
    if (Boolean.FALSE.equals(asked.automatic)) {
      return listed(
          List.of(StripePaymentMethodType.CARD.wireValue()),
          false,
          asked.allowRedirects,
          endpoint,
          currency);
    }
    return dynamic(asked.allowRedirects, asked.excluded, asked.configuration, endpoint, currency);
  }

  private static @Nullable StripeError read(
      StripeCoreRequest request, Endpoint endpoint, Asked asked) {
    StripeForm form = request.form();
    if (endpoint.refusesAWrittenList() && form.carries(StripeFields.PAYMENT_METHOD_TYPES)) {
      return StripeErrors.paymentMethodTypesNoLongerSupported();
    }
    StripeParameter written = request.writtenPaymentMethodTypes();
    if (written != null) {
      asked.listed = theScalarsOf(written);
      if (asked.listed == null) {
        return unreadableList(StripeFields.PAYMENT_METHOD_TYPES);
      }
    }
    StripeError refused =
        readList(
            form,
            StripeFields.ALLOWED_PAYMENT_METHOD_TYPES,
            StripePublishedPaymentMethodTypes.ALLOWED,
            values -> asked.allowed = values);
    if (endpoint.readsAnEmptyExclusionAsUnset
        && isEmptyText(form.parameters().get(StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES))) {
      asked.exclusionUnset = true;
    } else if (refused == null && endpoint.readsTheExclusions) {
      refused =
          readList(
              form,
              StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES,
              StripePublishedPaymentMethodTypes.EXCLUDED,
              values -> asked.excluded = values);
    }
    if (refused == null && endpoint.readsTheDynamicSettings) {
      refused = readAutomatic(form, asked);
    }
    if (refused == null && endpoint.readsTheDynamicSettings) {
      refused = readConfiguration(form, asked);
    }
    return refused;
  }

  private static @Nullable StripeError readList(
      StripeForm form, String name, List<String> published, ListReceiver into) {
    if (!form.carries(name)) {
      return null;
    }
    List<String> values = form.listOf(name);
    if (values == null) {
      return unreadableList(name);
    }
    for (int index = 0; index < values.size(); index++) {
      if (!published.contains(values.get(index))) {
        return StripeErrors.invalidValue(
            name + "[" + index + "]", StripePublishedPaymentMethodTypes.oneOf(published));
      }
    }
    into.receive(withoutRepeats(values));
    return null;
  }

  private static @Nullable StripeError readAutomatic(StripeForm form, Asked asked) {
    if (!form.carries(StripeFields.AUTOMATIC_PAYMENT_METHODS)) {
      return null;
    }
    if (!(form.parameters().get(StripeFields.AUTOMATIC_PAYMENT_METHODS)
        instanceof StripeParameter.Hashed hashed)) {
      return StripeErrors.unreadableParameter(
          StripeFields.AUTOMATIC_PAYMENT_METHODS, THE_SHAPE_OF_AUTOMATIC_PAYMENT_METHODS);
    }
    String enabledName = StripeFields.AUTOMATIC_PAYMENT_METHODS + "[" + ENABLED + "]";
    StripeParameter enabled = hashed.entries().get(ENABLED);
    if (enabled == null) {
      return StripeErrors.missingRequiredParam(enabledName);
    }
    Boolean flag =
        enabled instanceof StripeParameter.Scalar scalar ? StripeFlag.of(scalar.value()) : null;
    if (flag == null) {
      return StripeErrors.unreadableParameter(enabledName, StripeFlag.THE_VALUES_IT_ACCEPTS);
    }
    asked.automatic = flag;
    StripeParameter redirects = hashed.entries().get(ALLOW_REDIRECTS);
    if (redirects == null) {
      return null;
    }
    Optional<StripeRedirects> named =
        redirects instanceof StripeParameter.Scalar scalar
            ? StripeRedirects.of(scalar.value())
            : Optional.empty();
    if (named.isEmpty()) {
      return StripeErrors.invalidValue(
          StripeFields.AUTOMATIC_PAYMENT_METHODS + "[" + ALLOW_REDIRECTS + "]",
          StripePublishedPaymentMethodTypes.oneOf(StripeRedirects.published()));
    }
    asked.allowRedirects = named.get();
    return null;
  }

  private static @Nullable StripeError readConfiguration(StripeForm form, Asked asked) {
    if (!form.carries(StripeFields.PAYMENT_METHOD_CONFIGURATION)) {
      return null;
    }
    String configuration = form.first(StripeFields.PAYMENT_METHOD_CONFIGURATION);
    if (configuration == null || configuration.length() > LONGEST_CONFIGURATION_ID) {
      return StripeErrors.unreadableParameter(
          StripeFields.PAYMENT_METHOD_CONFIGURATION, THE_SHAPE_OF_A_CONFIGURATION);
    }
    asked.configuration = configuration;
    return null;
  }

  private static @Nullable StripeError exclusivityOf(
      Asked asked, @Nullable StripeSelection stored) {
    if (asked.allowed != null) {
      String other = theFirstSentBesideTheAllowedTypes(asked);
      return other == null
          ? null
          : StripeErrors.parametersExclusive(StripeFields.ALLOWED_PAYMENT_METHOD_TYPES, other);
    }
    if (asked.listed != null) {
      if (Boolean.TRUE.equals(asked.automatic)) {
        return StripeErrors.parametersExclusive(
            StripeFields.PAYMENT_METHOD_TYPES, StripeFields.AUTOMATIC_PAYMENT_METHODS);
      }
      return aDynamicSettingBeside(asked, StripeFields.PAYMENT_METHOD_TYPES);
    }
    if (Boolean.FALSE.equals(asked.automatic)) {
      return aDynamicSettingBeside(asked, StripeFields.AUTOMATIC_PAYMENT_METHODS);
    }
    if (stored != null && asked.excluded != null && !stored.dynamic()) {
      return StripeErrors.parametersExclusive(
          StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES, stored.theParameterThatChoseIt());
    }
    return null;
  }

  private static @Nullable String theFirstSentBesideTheAllowedTypes(Asked asked) {
    if (asked.listed != null) {
      return StripeFields.PAYMENT_METHOD_TYPES;
    }
    if (asked.excluded != null) {
      return StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES;
    }
    if (asked.automatic != null) {
      return StripeFields.AUTOMATIC_PAYMENT_METHODS;
    }
    if (asked.configuration != null) {
      return StripeFields.PAYMENT_METHOD_CONFIGURATION;
    }
    return null;
  }

  private static @Nullable StripeError aDynamicSettingBeside(Asked asked, String switchedOffBy) {
    if (asked.excluded != null) {
      return StripeErrors.parametersExclusive(
          StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES, switchedOffBy);
    }
    if (asked.configuration != null) {
      return StripeErrors.parametersExclusive(
          StripeFields.PAYMENT_METHOD_CONFIGURATION, switchedOffBy);
    }
    return null;
  }

  private static Outcome allowedOnly(
      List<String> allowed, Endpoint endpoint, @Nullable String currency) {
    List<StripePaymentMethodType> offered = new ArrayList<>();
    for (String value : allowed) {
      StripePaymentMethodType.of(value)
          .filter(type -> type.presentsIn(currency))
          .ifPresent(offered::add);
    }
    if (offered.isEmpty()) {
      return nothingLeft(endpoint, StripeFields.ALLOWED_PAYMENT_METHOD_TYPES);
    }
    return new Outcome.Selected(StripeSelection.allowedOnly(allowed, offered));
  }

  private static Outcome listed(
      List<String> listed,
      @Nullable Boolean automatic,
      @Nullable StripeRedirects allowRedirects,
      Endpoint endpoint,
      @Nullable String currency) {
    List<StripePaymentMethodType> offered = new ArrayList<>();
    for (String value : withoutRepeats(listed)) {
      Optional<StripePaymentMethodType> type =
          StripePaymentMethodType.of(value).filter(named -> named.presentsIn(currency));
      if (type.isEmpty()) {
        return new Outcome.Refused(
            StripeErrors.paymentMethodTypeNotSupported(value, endpoint.doing));
      }
      offered.add(type.get());
    }
    if (offered.isEmpty()) {
      return nothingLeft(endpoint, StripeFields.PAYMENT_METHOD_TYPES);
    }
    return new Outcome.Selected(StripeSelection.listed(automatic, allowRedirects, offered));
  }

  private static Outcome dynamic(
      @Nullable StripeRedirects allowRedirects,
      @Nullable List<String> excluded,
      @Nullable String configuration,
      Endpoint endpoint,
      @Nullable String currency) {
    List<StripePaymentMethodType> offered = new ArrayList<>();
    for (StripePaymentMethodType type : StripePaymentMethodType.values()) {
      boolean excludedByName = excluded != null && excluded.contains(type.wireValue());
      boolean redirectsRefused = type.redirects() && allowRedirects == StripeRedirects.NEVER;
      if (!excludedByName && !redirectsRefused && type.presentsIn(currency)) {
        offered.add(type);
      }
    }
    if (offered.isEmpty()) {
      return nothingLeft(endpoint, StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES);
    }
    return new Outcome.Selected(
        StripeSelection.dynamic(allowRedirects, excluded, configuration, offered));
  }

  private static Outcome nothingLeft(Endpoint endpoint, String param) {
    return new Outcome.Refused(
        endpoint.forASetup
            ? StripeErrors.noPaymentMethodTypeLeftForTheSetup(param)
            : StripeErrors.noPaymentMethodTypeLeftForThePayment(param));
  }

  private static @Nullable List<String> theScalarsOf(StripeParameter parameter) {
    Optional<List<StripeParameter>> elements = StripeParameter.elementsOf(parameter);
    if (elements.isEmpty()) {
      return null;
    }
    List<String> values = new ArrayList<>();
    for (StripeParameter element : elements.get()) {
      if (!(element instanceof StripeParameter.Scalar scalar)) {
        return null;
      }
      values.add(scalar.value());
    }
    return List.copyOf(values);
  }

  private static boolean isEmptyText(@Nullable StripeParameter sent) {
    return sent instanceof StripeParameter.Scalar scalar && scalar.value().isEmpty();
  }

  private static StripeError unreadableList(String name) {
    return StripeErrors.unreadableParameter(name, StripeForm.theListShapesOf(name));
  }

  private static List<String> withoutRepeats(List<String> values) {
    List<String> kept = new ArrayList<>();
    for (String value : values) {
      if (!kept.contains(value)) {
        kept.add(value);
      }
    }
    return List.copyOf(kept);
  }

  @FunctionalInterface
  private interface ListReceiver {
    void receive(List<String> values);
  }

  private static final class Asked {
    private @Nullable List<String> listed;
    private @Nullable Boolean automatic;
    private @Nullable StripeRedirects allowRedirects;
    private @Nullable List<String> allowed;
    private @Nullable List<String> excluded;
    private boolean exclusionUnset;
    private @Nullable String configuration;
  }
}
