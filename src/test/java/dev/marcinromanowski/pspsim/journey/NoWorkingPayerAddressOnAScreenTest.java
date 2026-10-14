package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoWorkingPayerAddressOnAScreenTest {

  private static final String A_GROW_SCREEN = "/ui/objects?provider=grow&testRunId=r";

  private static final String AN_AUTRA_SCREEN = "/ui/objects?provider=autra&testRunId=r";

  private static final String A_WORLDLINE_SCREEN = "/ui/objects?provider=worldline&testRunId=r";

  private static final String A_PAGE_OF_A_SCREEN =
      "<html><body><table><tr><td>merchantId</td><td>m-1</td></tr>";

  @Test
  void an_address_of_this_simulators_own_page_is_refused_and_named() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    A_GROW_SCREEN,
                    A_PAGE_OF_A_SCREEN + "<td>http://localhost:8080/far?l=abc123</td>"))
        .as(
            "a tester reading this object is handed an address that opens the payer page of this"
                + " very payment, which is what the wizard promises never happens")
        .hasMessageContaining("http://localhost:8080/far?l=abc123")
        .hasMessageContaining("/ui/objects?provider=grow");
  }

  @Test
  void an_address_kept_on_a_row_and_printed_as_a_path_is_refused_the_same_way() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    AN_AUTRA_SCREEN,
                    A_PAGE_OF_A_SCREEN + "<td>/tokenize/sessions/a-session?token=t-1</td>"))
        .as(
            "a path of this build opens the same page as an absolute address of it, so where the"
                + " value came from is not a difference this sweep can be fooled by")
        .hasMessageContaining("/tokenize/sessions/a-session?token=t-1");
  }

  @Test
  void an_authority_with_no_scheme_is_this_simulators_own_address_too() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    A_WORLDLINE_SCREEN,
                    A_PAGE_OF_A_SCREEN
                        + "<td>localhost:8080//hostedtokenization/tokenization/form/t-1</td>"))
        .as(
            "worldline answers the address of its page a second time as an authority and a path"
                + " with no scheme, and that opens the page exactly as the absolute one does")
        .hasMessageContaining("localhost:8080//hostedtokenization/tokenization/form/t-1");
  }

  @Test
  void a_merchants_own_address_ending_where_a_route_of_this_build_does_is_refused_as_well() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    A_GROW_SCREEN,
                    A_PAGE_OF_A_SCREEN + "<td>https://merchant.example/far?l=abc123</td>"))
        .as(
            "grow answers successUrl, cancelUrl and notifyUrl, which are the merchant's own"
                + " addresses, and one of them ending where this build's route does is refused"
                + " here too, because whose host a rendered value carries cannot be told without"
                + " the address this simulator publishes itself under; a floor over the host was"
                + " measured to read grow's live leak as somebody else's address and pass it")
        .hasMessageContaining("https://merchant.example/far?l=abc123");
  }

  @Test
  void the_route_as_the_wizard_promises_it_is_no_address_and_is_read() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    A_GROW_SCREEN,
                    A_PAGE_OF_A_SCREEN
                        + "<p>This simulator serves the payer's page itself, at /far so a payer"
                        + " reaches it in a browser here.</p>"))
        .as(
            "the wizard names the route to say where the page is, which is the promise itself and"
                + " opens no payment")
        .doesNotThrowAnyException();
  }

  @Test
  void the_route_a_wizard_promises_a_page_at_is_read_though_a_sentence_follows_it() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    AN_AUTRA_SCREEN,
                    A_PAGE_OF_A_SCREEN
                        + "<p>This simulator serves the payer's page itself, at"
                        + " /tokenize/sessions/{hostedCardSessionId}, so a payer reaches it in a"
                        + " browser here and never on the vendor's own host.</p>"))
        .as(
            "the segment of a path variable reads as any run of characters that is not a"
                + " separator, so the comma of that sentence stands where an identifier would and"
                + " the route reads as an address of itself; a value carrying a brace is the"
                + " route as declared and a browser asking for a segment spelled"
                + " {hostedCardSessionId} reaches nothing this build mints")
        .doesNotThrowAnyException();
  }

  @Test
  void a_route_another_provider_declares_is_read_on_this_provider_s_screen() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    A_GROW_SCREEN,
                    A_PAGE_OF_A_SCREEN + "<td>/tokenize/sessions/a-session?token=t-1</td>"))
        .as(
            "a screen is swept for the pages its own provider serves, because a path answered for"
                + " another provider's object carries no token this one mints")
        .doesNotThrowAnyException();
  }

  @Test
  void a_page_this_simulator_serves_a_payer_is_not_a_screen_and_is_not_swept() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    "/far?l=abc123", "<form action=\"/far?l=abc123\" method=\"post\">"))
        .as(
            "the payer's own page carries its own address as the action of the form a payer"
                + " submits, which is the page working and not a screen handing anything over")
        .doesNotThrowAnyException();
  }

  @Test
  void a_screen_read_for_no_provider_is_not_swept() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    "/ui/configuration", A_PAGE_OF_A_SCREEN + "<td>/far?l=abc</td>"))
        .as("a screen of no provider is swept for no provider's pages")
        .doesNotThrowAnyException();
  }

  @Test
  void a_provider_registered_as_serving_no_payer_page_is_swept_for_none() {
    assertThatCode(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    "/ui/objects?provider=stripe&testRunId=r",
                    A_PAGE_OF_A_SCREEN + "<td>pi_123</td>"))
        .as(
            "stripe declares NoPageThisSimulatorServes, so it has no page of its own to hand"
                + " over, and the registration is what says so rather than a missing entry")
        .doesNotThrowAnyException();
  }

  @Test
  void a_provider_in_neither_register_is_refused_rather_than_swept_for_nothing() {
    assertThatThrownBy(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
                    "/ui/objects?provider=sixth&testRunId=r", A_PAGE_OF_A_SCREEN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stands neither among the providers that serve a payer page");
  }

  @Test
  void a_screen_that_answered_no_body_is_refused_rather_than_swept_for_no_value() {
    assertThatThrownBy(
            () -> NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(A_GROW_SCREEN, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("answered no body at all");
  }

  @Test
  void a_screen_that_printed_nothing_is_refused_the_same_way() {
    assertThatThrownBy(
            () ->
                NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(A_GROW_SCREEN, "   "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("printed no value at all");
  }

  @Test
  void every_provider_of_this_build_stands_in_one_register_or_the_other() {
    assertThat(NoWorkingPayerAddressOnAScreen.THE_PAGES_EACH_PROVIDER_SERVES.keySet())
        .as(
            "a provider in neither register has its screens refused above rather than swept for"
                + " no route, so the ones that serve a page are named here")
        .containsExactly("autra", "azericard", "grow", "tranzila", "worldline");
    assertThat(NoWorkingPayerAddressOnAScreen.THE_PROVIDERS_THAT_SERVE_NO_PAYER_PAGE)
        .containsExactlyInAnyOrder("stripe");
    assertThat(NoWorkingPayerAddressOnAScreen.THE_PAGES_EACH_PROVIDER_SERVES.values())
        .allSatisfy(routes -> assertThat(routes).isNotEmpty())
        .flatExtracting(List::copyOf)
        .hasSize(5);
  }

  @Test
  void the_register_of_pages_is_not_a_map_a_test_can_write_into() {
    assertThatThrownBy(
            () ->
                NoWorkingPayerAddressOnAScreen.THE_PAGES_EACH_PROVIDER_SERVES.put(
                    "sixth", List.of("/sixth/payer")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void the_provider_a_screen_is_read_for_is_the_one_its_own_query_names() {
    assertThat(NoWorkingPayerAddressOnAScreen.theProviderNamedIn(A_GROW_SCREEN)).isEqualTo("grow");
    assertThat(
            NoWorkingPayerAddressOnAScreen.theProviderNamedIn(
                "/ui/objects?testRunId=r&provider=autra"))
        .isEqualTo("autra");
    assertThat(NoWorkingPayerAddressOnAScreen.theProviderNamedIn("/ui/objects")).isNull();
  }
}
