package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class StripeSelectionView {

  private StripeSelectionView() {}

  static StripeJson allowed(StripeSelection selection) {
    return listOrNull(selection.allowed());
  }

  static StripeJson automatic(StripeSelection selection) {
    Boolean automatic = selection.automatic();
    if (automatic == null) {
      return StripeJson.nothing();
    }
    StripeRedirects redirects = selection.allowRedirects();
    if (redirects == null) {
      return StripeJson.object(StripeSelections.ENABLED, StripeJson.bool(automatic));
    }
    return StripeJson.object(
        StripeSelections.ALLOW_REDIRECTS,
        StripeJson.text(redirects.wireValue()),
        StripeSelections.ENABLED,
        StripeJson.bool(automatic));
  }

  static StripeJson excluded(StripeSelection selection) {
    return listOrNull(selection.excluded());
  }

  static StripeJson configurationDetails(StripeSelection selection) {
    String configuration = selection.configuration();
    if (configuration == null) {
      return StripeJson.nothing();
    }
    return StripeJson.object("id", StripeJson.text(configuration), "parent", StripeJson.nothing());
  }

  static StripeJson offered(StripeSelection selection) {
    return listOf(selection.offeredWireValues());
  }

  private static StripeJson listOrNull(@Nullable List<String> values) {
    return values == null ? StripeJson.nothing() : listOf(values);
  }

  private static StripeJson listOf(List<String> values) {
    List<StripeJson> elements = new ArrayList<>();
    for (String value : values) {
      elements.add(StripeJson.text(value));
    }
    return StripeJson.array(elements);
  }
}
