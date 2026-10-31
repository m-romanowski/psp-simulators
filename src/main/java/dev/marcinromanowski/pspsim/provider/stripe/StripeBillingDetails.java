package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record StripeBillingDetails(
    @Nullable String city,
    @Nullable String country,
    @Nullable String line1,
    @Nullable String line2,
    @Nullable String postalCode,
    @Nullable String state,
    @Nullable String email,
    @Nullable String name,
    @Nullable String phone,
    @Nullable String taxId) {

  static final int LONGEST_VALUE = 5000;

  static final String ADDRESS = "address";

  static final List<String> THE_ADDRESS_MEMBERS =
      List.of("city", "country", "line1", "line2", "postal_code", "state");

  static final List<String> THE_PERSON_MEMBERS = List.of("email", "name", "phone", "tax_id");

  static final StripeBillingDetails NONE =
      new StripeBillingDetails(null, null, null, null, null, null, null, null, null, null);

  static final String A_VALUE_IS_AS_LONG_AS_STRIPE_ACCEPTS =
      "provider stripe publishes every billing_details value as a string of at most "
          + LONGEST_VALUE
          + " characters";

  StripeBillingDetails {
    for (String value :
        new String[] {city, country, line1, line2, postalCode, state, email, name, phone, taxId}) {
      if (value != null && value.length() > LONGEST_VALUE) {
        throw new IllegalArgumentException(A_VALUE_IS_AS_LONG_AS_STRIPE_ACCEPTS);
      }
    }
  }

  @Nullable String addressMember(String member) {
    return switch (member) {
      case "city" -> city;
      case "country" -> country;
      case "line1" -> line1;
      case "line2" -> line2;
      case "postal_code" -> postalCode;
      case "state" -> state;
      default -> throw new IllegalArgumentException("no billing address member " + member);
    };
  }

  @Nullable String personMember(String member) {
    return switch (member) {
      case "email" -> email;
      case "name" -> name;
      case "phone" -> phone;
      case "tax_id" -> taxId;
      default -> throw new IllegalArgumentException("no billing details member " + member);
    };
  }

  static StripeBillingDetails of(Map<String, String> sent) {
    return new StripeBillingDetails(
        sent.get("city"),
        sent.get("country"),
        sent.get("line1"),
        sent.get("line2"),
        sent.get("postal_code"),
        sent.get("state"),
        sent.get("email"),
        sent.get("name"),
        sent.get("phone"),
        sent.get("tax_id"));
  }
}
