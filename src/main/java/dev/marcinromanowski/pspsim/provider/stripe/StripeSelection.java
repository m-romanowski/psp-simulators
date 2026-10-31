package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

record StripeSelection(
    @Nullable Boolean automatic,
    @Nullable StripeRedirects allowRedirects,
    @Nullable List<String> allowed,
    @Nullable List<String> excluded,
    @Nullable String configuration,
    List<StripePaymentMethodType> offered) {

  static final String NO_TYPE_IS_LEFT =
      "None of the payment method types this request selects is one this simulator can offer"
          + " with the rest of the request, so no payment method could be used with this";

  static final String AN_INTENT_OFFERS_A_TYPE =
      "provider stripe answers payment_method_types as the list of types an intent can use, and"
          + " this simulator refuses a selection that leaves none, so a stored intent that offers"
          + " no type would be one no request could have created";

  static final String ALLOWED_TYPES_STAND_ALONE =
      "provider stripe publishes allowed_payment_method_types as mutually exclusive with"
          + " automatic_payment_methods, excluded_payment_method_types and"
          + " payment_method_configuration, so a stored intent carrying it beside any of them"
          + " would be one no request could have created";

  static final String REDIRECTS_ARE_A_SETTING_OF_AUTOMATIC_PAYMENT_METHODS =
      "provider stripe publishes allow_redirects inside automatic_payment_methods, so a stored"
          + " intent carrying it without automatic_payment_methods would carry a setting of a"
          + " hash it never had";

  static final String DYNAMIC_SETTINGS_NEED_DYNAMIC_SELECTION =
      "excluded_payment_method_types and payment_method_configuration narrow or choose the set"
          + " dynamic payment methods compute, so a stored intent carrying one without"
          + " automatic_payment_methods enabled would narrow a set it never computed";

  StripeSelection {
    offered = List.copyOf(offered);
    allowed = allowed == null ? null : List.copyOf(allowed);
    excluded = excluded == null ? null : List.copyOf(excluded);
    if (offered.isEmpty()) {
      throw new IllegalArgumentException(AN_INTENT_OFFERS_A_TYPE);
    }
    if (allowed != null && (automatic != null || excluded != null || configuration != null)) {
      throw new IllegalArgumentException(ALLOWED_TYPES_STAND_ALONE);
    }
    if (allowRedirects != null && automatic == null) {
      throw new IllegalArgumentException(REDIRECTS_ARE_A_SETTING_OF_AUTOMATIC_PAYMENT_METHODS);
    }
    if (!Boolean.TRUE.equals(automatic) && (excluded != null || configuration != null)) {
      throw new IllegalArgumentException(DYNAMIC_SETTINGS_NEED_DYNAMIC_SELECTION);
    }
  }

  static StripeSelection dynamic(
      @Nullable StripeRedirects allowRedirects,
      @Nullable List<String> excluded,
      @Nullable String configuration,
      List<StripePaymentMethodType> offered) {
    return new StripeSelection(true, allowRedirects, null, excluded, configuration, offered);
  }

  static StripeSelection allowedOnly(List<String> allowed, List<StripePaymentMethodType> offered) {
    return new StripeSelection(null, null, allowed, null, null, offered);
  }

  static StripeSelection listed(
      @Nullable Boolean automatic,
      @Nullable StripeRedirects allowRedirects,
      List<StripePaymentMethodType> offered) {
    return new StripeSelection(automatic, allowRedirects, null, null, null, offered);
  }

  boolean dynamic() {
    return Boolean.TRUE.equals(automatic);
  }

  boolean offers(StripePaymentMethodType type) {
    return offered.contains(type);
  }

  String theParameterThatChoseIt() {
    if (allowed != null) {
      return StripeFields.ALLOWED_PAYMENT_METHOD_TYPES;
    }
    if (automatic != null) {
      return StripeFields.AUTOMATIC_PAYMENT_METHODS;
    }
    return StripeFields.PAYMENT_METHOD_TYPES;
  }

  List<String> offeredWireValues() {
    List<String> values = new ArrayList<>();
    for (StripePaymentMethodType type : offered) {
      values.add(type.wireValue());
    }
    return List.copyOf(values);
  }

  String offeredAsProse() {
    return String.join(", ", offeredWireValues());
  }
}
