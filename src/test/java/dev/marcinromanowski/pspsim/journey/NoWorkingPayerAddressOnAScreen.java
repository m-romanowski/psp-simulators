package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.fail;

import dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.Set;
import java.util.regex.Pattern;

public final class NoWorkingPayerAddressOnAScreen {

  public static final SequencedMap<String, List<String>> THE_PAGES_EACH_PROVIDER_SERVES =
      theDeclaredPages();

  public static final SequencedMap<String, List<String>> THE_ROUTES_A_PAYER_SUBMITS_A_PAGE_TO =
      theSubmissionRoutes();

  public static final Set<String> THE_PROVIDERS_THAT_SERVE_NO_PAYER_PAGE = Set.of("stripe");

  public static final String A_SCREEN_OF_THIS_SIMULATOR = "/ui";

  private static final String THE_PROVIDER_A_SCREEN_IS_READ_FOR = "provider=";

  private static final Pattern WHAT_SEPARATES_ONE_VALUE_FROM_THE_NEXT =
      Pattern.compile("[\\s\"'<>]+");

  private static final String WHY_AN_ADDRESS_OF_THAT_PAGE_IS_NOT_HANDED_OVER =
      "the wizard tells a tester, in the provider's own words, that this simulator serves the"
          + " payer's page itself and that the link the provider answers with is never printed on"
          + " a screen, so the payer step on the object's own screen is how a payment is"
          + " finished. A screen that prints an address of this simulator's own payer page breaks"
          + " that promise where a tester reads it, and does so without any constant of the build"
          + " naming the address, which is the shape verifyNoPayerUrlAmongRelatedObjects reads"
          + " bytecode for and cannot see: an address kept on a persisted row reaches a screen as"
          + " no constant at all. This reads the page a tester was actually served, so it reads"
          + " values of a run, and a value a tester typed into a field this screen reads back is"
          + " refused here the same way a value the module composed is, and so is a merchant's"
          + " own address that happens to end where a route of this build does, because whose"
          + " host a rendered value carries cannot be told without the address this simulator"
          + " publishes itself under, which a journey does not read. A floor over the host was"
          + " measured to be worse than none: providers mint the address from"
          + " simulator.public-base-url, which is localhost:8080 while a journey's own server"
          + " answers on a port of the run, so comparing the host read grow's live leak as"
          + " somebody else's address and passed it. A value carrying the brace of a path"
          + " variable is the route as it is declared and not an address: the wizard prints that"
          + " route in the sentence promising the page, the segment of a variable reads as any"
          + " run of characters that is not a separator so the comma of that sentence stands"
          + " where an identifier would, and a browser asking for a segment spelled"
          + " {hostedCardSessionId} reaches nothing this build mints. The build refuses a module"
          + " naming its own route whichever side of a put it lands on, which is where that"
          + " belongs.";

  private NoWorkingPayerAddressOnAScreen() {}

  public static void refuseAnAddressOfAPayerPageOn(String path, String body) {
    if (!path.startsWith(A_SCREEN_OF_THIS_SIMULATOR)) {
      return;
    }
    String provider = theProviderNamedIn(path);
    if (provider == null) {
      return;
    }
    if (THE_PROVIDERS_THAT_SERVE_NO_PAYER_PAGE.contains(provider)) {
      return;
    }
    List<String> routes = THE_PAGES_EACH_PROVIDER_SERVES.get(provider);
    if (routes == null) {
      throw new IllegalArgumentException(
          "the screen "
              + path
              + " was read for provider "
              + provider
              + ", which stands neither among the providers that serve a payer page "
              + THE_PAGES_EACH_PROVIDER_SERVES.keySet()
              + " nor among those registered as serving none "
              + THE_PROVIDERS_THAT_SERVE_NO_PAYER_PAGE
              + ". A screen read for a provider in neither would be swept for no route and"
              + " report that it carries no payer address");
    }
    if (body == null) {
      throw new IllegalStateException(
          "the screen "
              + path
              + " answered no body at all, so this sweep would read nothing and report that the"
              + " page carries no payer address. A sweep that found none and a sweep that read"
              + " nothing must not pass the same way");
    }
    List<String> swept = new ArrayList<>(routes);
    swept.addAll(THE_ROUTES_A_PAYER_SUBMITS_A_PAGE_TO.getOrDefault(provider, List.of()));
    List<String> printed = theValuesPrintedOn(body);
    if (printed.isEmpty()) {
      throw new IllegalStateException(
          "the screen "
              + path
              + " printed no value at all, so this sweep read nothing and would report that the"
              + " page carries no payer address");
    }
    for (String route : swept) {
      for (String value : printed) {
        if (value.equals(route) || aRouteAsItIsDeclared(value)) {
          continue;
        }
        if (RelatedObjectAddressConvention.anAddressOfThePageAt(route, value)) {
          fail(
              "the screen "
                  + path
                  + " prints "
                  + value
                  + ", which is an address of the payer page this build serves at "
                  + route
                  + " for provider "
                  + provider
                  + ". "
                  + WHY_AN_ADDRESS_OF_THAT_PAGE_IS_NOT_HANDED_OVER);
        }
      }
    }
  }

  static boolean aRouteAsItIsDeclared(String value) {
    return value.indexOf('{') >= 0 || value.indexOf('}') >= 0;
  }

  static String theProviderNamedIn(String path) {
    int named = path.indexOf(THE_PROVIDER_A_SCREEN_IS_READ_FOR);
    if (named < 0) {
      return null;
    }
    String after = path.substring(named + THE_PROVIDER_A_SCREEN_IS_READ_FOR.length());
    int ends = after.indexOf('&');
    return ends < 0 ? after : after.substring(0, ends);
  }

  private static List<String> theValuesPrintedOn(String rendered) {
    return WHAT_SEPARATES_ONE_VALUE_FROM_THE_NEXT
        .splitAsStream(rendered)
        .filter(value -> !value.isBlank())
        .toList();
  }

  private static SequencedMap<String, List<String>> theSubmissionRoutes() {
    SequencedMap<String, List<String>> declared = new LinkedHashMap<>();
    declared.put(
        "tranzila",
        List.of(
            "/tranzila/3ds/challenge/{trackId}",
            "/tranzila/directng/{terminal}/payer/{pageId}",
            "/tranzila/directng/{terminal}/payer/{pageId}/wallet/{wallet}",
            "/tranzila/hf/assets/js/hfields_depend/controlfields.html",
            "/tranzila/hf/assets/js/hfields_depend/genfield.php",
            "/tranzila/hf/process/ajax_process_transaction"));
    return Collections.unmodifiableSequencedMap(declared);
  }

  private static SequencedMap<String, List<String>> theDeclaredPages() {
    SequencedMap<String, List<String>> declared = new LinkedHashMap<>();
    declared.put("autra", List.of("/tokenize/sessions/{hostedCardSessionId}"));
    declared.put("azericard", List.of("/azericard/payer"));
    declared.put("grow", List.of("/far"));
    declared.put("tranzila", List.of("/tranzila/directng/{terminal}/iframenew.php"));
    declared.put(
        "worldline", List.of("/hostedtokenization/tokenization/form/{hostedTokenizationId}"));
    return Collections.unmodifiableSequencedMap(declared);
  }
}
