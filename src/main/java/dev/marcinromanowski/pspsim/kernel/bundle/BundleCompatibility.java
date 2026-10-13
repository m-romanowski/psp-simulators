package dev.marcinromanowski.pspsim.kernel.bundle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record BundleCompatibility(
    @Nullable List<String> accountFields,
    @Nullable List<String> paymentMethodFields,
    @Nullable List<String> settings,
    @Nullable List<String> actionTypes) {

  public static final String ACCOUNT_FIELDS = "compatibility.accountFields";

  public static final String PAYMENT_METHOD_FIELDS = "compatibility.paymentMethodFields";

  public static final String SETTINGS = "compatibility.settings";

  public static final String ACTION_TYPES = "compatibility.actionTypes";

  public static final String A_TERM_THIS_BUILD_NO_LONGER_DECLARES =
      "a bundle names the vocabulary it was written against, and this build no longer declares this"
          + " term, so nothing here could read the entries that used it; the refusal names the term"
          + " that moved rather than failing later on a value that spelled it";

  public static final String A_BUNDLE_NAMES_WHAT_IT_WAS_WRITTEN_AGAINST =
      "compatibility must carry the vocabulary this bundle was written against, because a bundle"
          + " that names none cannot be checked against this build at all, and an unchecked bundle"
          + " fails on the first value whose term moved rather than on the term";

  public BundleCompatibility {
    accountFields = accountFields == null ? null : List.copyOf(accountFields);
    paymentMethodFields = paymentMethodFields == null ? null : List.copyOf(paymentMethodFields);
    settings = settings == null ? null : List.copyOf(settings);
    actionTypes = actionTypes == null ? null : List.copyOf(actionTypes);
  }

  public List<String> carriedAccountFields() {
    return carried(accountFields);
  }

  public List<String> carriedPaymentMethodFields() {
    return carried(paymentMethodFields);
  }

  public List<String> carriedSettings() {
    return carried(settings);
  }

  public List<String> carriedActionTypes() {
    return carried(actionTypes);
  }

  public List<BundleRefusal> theTermsThisBuildNoLongerDeclares(BundleCompatibility declaredHere) {
    List<BundleRefusal> refused = new ArrayList<>();
    everyTermOf(
        ACCOUNT_FIELDS, carriedAccountFields(), declaredHere.carriedAccountFields(), refused);
    everyTermOf(
        PAYMENT_METHOD_FIELDS,
        carriedPaymentMethodFields(),
        declaredHere.carriedPaymentMethodFields(),
        refused);
    everyTermOf(SETTINGS, carriedSettings(), declaredHere.carriedSettings(), refused);
    everyTermOf(ACTION_TYPES, carriedActionTypes(), declaredHere.carriedActionTypes(), refused);
    return List.copyOf(refused);
  }

  private static void everyTermOf(
      String group,
      List<String> writtenAgainst,
      List<String> declaredHere,
      List<BundleRefusal> refused) {
    Set<String> here = new LinkedHashSet<>(declaredHere);
    for (String term : writtenAgainst) {
      if (!here.contains(term)) {
        refused.add(new BundleRefusal(group + "." + term, A_TERM_THIS_BUILD_NO_LONGER_DECLARES));
      }
    }
  }

  private static List<String> carried(@Nullable List<String> terms) {
    return terms == null ? List.of() : terms;
  }
}
