package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class PairingScreen {

  public enum Shown {
    VALUE,
    WITHHELD_AS_SECRET,
    WITHHELD_AS_UNDECLARED,
    NOT_CONFIGURED
  }

  public record Setting(String name, Shown shown, String value, String fromField) {}

  public record Snippet(String accountId, List<Setting> settings) {

    public Snippet {
      settings = List.copyOf(settings);
    }
  }

  public record Target(String accountId, String url) {}

  public record Alias(String from, String to) {}

  public record View(
      String provider,
      String testRunId,
      String publicBaseUrl,
      String providerBaseUrl,
      String testRunHeader,
      String howARequestNamesItsAccount,
      List<Snippet> snippets,
      List<Target> callbackTargets,
      List<Alias> routeAliases,
      String suggestedRouteAliasTarget,
      String qrPayload,
      String arrivingTraffic) {

    public View {
      snippets = List.copyOf(snippets);
      callbackTargets = List.copyOf(callbackTargets);
      routeAliases = List.copyOf(routeAliases);
    }
  }

  public static final ObjectOrigin TRAFFIC_THAT_ARRIVED_FROM_AN_APPLICATION =
      ObjectOrigin.PROVIDER_API;

  public static final String NO_VALUE = "";

  public static final String NOT_A_FIELD = "";

  public static final String A_SECRET_IS_NEVER_PAIRING_DATA =
      "this screen prints the name of every field an application must set and the value of only"
          + " those a provider declares are not secret, so a snippet copied from it, and the"
          + " payload behind the code beside it, can be photographed off a monitor without"
          + " handing anyone a credential";

  private PairingScreen() {}

  public static String qrPayloadOf(String publicBaseUrl, String provider, String testRunId) {
    return withoutATrailingSlash(publicBaseUrl)
        + PairingPaths.SCREEN
        + "?provider="
        + encoded(provider)
        + "&testRunId="
        + encoded(testRunId);
  }

  public static String arrivingTrafficOf(String provider, String testRunId) {
    return PairingPaths.TRAIL
        + "?provider="
        + encoded(provider)
        + "&testRunId="
        + encoded(testRunId)
        + "&origin="
        + TRAFFIC_THAT_ARRIVED_FROM_AN_APPLICATION.name();
  }

  public static Setting settingOf(
      PairingSetting declared,
      ProviderAccountFields fields,
      String providerBaseUrl,
      String testRunId,
      String accountId,
      Map<String, List<String>> configuration) {
    return switch (declared.value()) {
      case PairingValue.Given given -> shown(declared, given.text());
      case PairingValue.TheProviderBaseUrl _ -> shown(declared, providerBaseUrl);
      case PairingValue.TheTestRunId _ -> shown(declared, testRunId);
      case PairingValue.TheTestRunHeader _ -> shown(declared, TestRunHeader.NAME);
      case PairingValue.TheAccountId _ -> shown(declared, accountId);
      case PairingValue.FromAccountField field ->
          fromAccountField(declared, fields, field.key(), configuration);
    };
  }

  public static View of(
      String provider,
      String testRunId,
      String publicBaseUrl,
      ProviderPairing pairing,
      ProviderAccountFields fields,
      List<String> accountIds,
      Function<String, Optional<DisclosedAccount>> disclosureOf,
      List<WebhookTarget> callbackTargets,
      List<RouteAlias> routeAliases) {
    String providerBaseUrl = pairing.providerBaseUrlWithin(publicBaseUrl);
    List<Snippet> snippets = new ArrayList<>();
    for (String accountId : accountIds) {
      snippets.add(
          snippetOf(
              pairing,
              fields,
              providerBaseUrl,
              testRunId,
              accountId,
              disclosureOf.apply(accountId)));
    }
    List<Target> targets = new ArrayList<>();
    for (WebhookTarget target : callbackTargets) {
      targets.add(new Target(target.accountId(), target.url()));
    }
    List<Alias> aliases = new ArrayList<>();
    for (RouteAlias alias : routeAliases) {
      aliases.add(new Alias(alias.from(), alias.to()));
    }
    return new View(
        provider,
        testRunId,
        publicBaseUrl,
        providerBaseUrl,
        TestRunHeader.NAME,
        pairing.howARequestNamesItsAccount(),
        List.copyOf(snippets),
        List.copyOf(targets),
        List.copyOf(aliases),
        pairing.apiBasePath(),
        qrPayloadOf(publicBaseUrl, provider, testRunId),
        arrivingTrafficOf(provider, testRunId));
  }

  private static Snippet snippetOf(
      ProviderPairing pairing,
      ProviderAccountFields fields,
      String providerBaseUrl,
      String testRunId,
      String accountId,
      Optional<DisclosedAccount> disclosed) {
    Map<String, List<String>> configuration =
        disclosed.map(DisclosedAccount::disclosed).orElse(Map.of());
    List<Setting> settings = new ArrayList<>();
    for (PairingSetting declared : pairing.settings()) {
      settings.add(
          settingOf(declared, fields, providerBaseUrl, testRunId, accountId, configuration));
    }
    return new Snippet(accountId, List.copyOf(settings));
  }

  private static Setting fromAccountField(
      PairingSetting declared,
      ProviderAccountFields fields,
      String key,
      Map<String, List<String>> configuration) {
    if (!fields.isShownInPlain(key)) {
      return new Setting(
          declared.name(),
          fields.field(key).isPresent() ? Shown.WITHHELD_AS_SECRET : Shown.WITHHELD_AS_UNDECLARED,
          NO_VALUE,
          key);
    }
    List<String> values = configuration.getOrDefault(key, List.of());
    if (values.isEmpty()) {
      return new Setting(declared.name(), Shown.NOT_CONFIGURED, NO_VALUE, key);
    }
    return new Setting(declared.name(), Shown.VALUE, values.get(0), key);
  }

  private static Setting shown(PairingSetting declared, String value) {
    return new Setting(declared.name(), Shown.VALUE, value, NOT_A_FIELD);
  }

  private static String withoutATrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
