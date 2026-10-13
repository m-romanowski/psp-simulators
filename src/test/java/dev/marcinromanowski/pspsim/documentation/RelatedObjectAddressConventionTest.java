package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention.ACallWithNoBodyToRead;
import dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention.Code;
import dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention.Reading;
import dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention.Site;
import dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention.TheClosure;
import dev.marcinromanowski.pspsim.documentation.RelatedObjectAddressConvention.TheCompiledBuild;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelatedObjectAddressConventionTest {

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final String A_PAGE = "/far";

  private static final String A_PAGE_WITH_A_VARIABLE =
      "/hostedtokenization/tokenization/form/{hostedTokenizationId}";

  private static final String A_STATE_IS_BUILT =
      "(Ldev/marcinromanowski/pspsim/provider/sixth/SixthPayment;)Ldev/marcinromanowski/pspsim/kernel/spi/ProviderObjectState;";

  private static final String A_VALUE_IS_HANDED_OVER = "()Ljava/util/Map;";

  private static final String AN_ADDRESS_IS_MINTED = "()Ljava/lang/String;";

  private static final Site BUILDS_A_STATE =
      new Site(
          "dev.marcinromanowski.pspsim.provider.sixth.SixthProvider", "stateOf", A_STATE_IS_BUILT);

  private static final Site HANDS_VALUES_OVER =
      new Site(
          "dev.marcinromanowski.pspsim.provider.sixth.SixthProvider",
          "relatedObjectsOf",
          A_VALUE_IS_HANDED_OVER);

  private static final Site MINTS_THE_ADDRESS =
      new Site(
          "dev.marcinromanowski.pspsim.provider.sixth.SixthPage", "urlFor", AN_ADDRESS_IS_MINTED);

  private static final Site A_SEVENTH_MODULE_BUILDS_A_STATE =
      new Site(
          "dev.marcinromanowski.pspsim.provider.seventh.SeventhProvider",
          "stateOf",
          A_STATE_IS_BUILT);

  private static final Site THE_REPOSITORY_OF_THIS_BUILD =
      new Site(
          "dev.marcinromanowski.pspsim.provider.sixth.SixthPayments",
          "timelineOf",
          "()Ljava/util/List;");

  private static final Site THE_STATES_CONSTRUCTOR =
      new Site(
          RelatedObjectAddressConvention.THE_STATE_OF_A_PROVIDER_OBJECT,
          PayerPageAddresses.INITIALIZER,
          "(Ljava/util/Map;)V");

  private static final PayerPageAddresses.Declared THE_PAGE_THE_SIXTH_MODULE_DECLARES =
      new PayerPageAddresses.Declared(
          "sixth", A_PAGE, "dev.marcinromanowski.pspsim.provider.sixth.SixthOrigination");

  private static final PayerPageAddresses.Declared THE_PAGE_A_SEVENTH_MODULE_DECLARES =
      new PayerPageAddresses.Declared(
          "seventh",
          "/azericard/payer",
          "dev.marcinromanowski.pspsim.provider.seventh.SeventhOrigination");

  @Test
  void a_value_that_is_the_address_of_a_payer_page_is_told_apart_from_one_that_is_not() {
    assertThat(RelatedObjectAddressConvention.anAddressOfThePageAt(A_PAGE, "/far?hostedLink="))
        .as("the constant woven into GrowHostedPage.urlFor, which is the route and its query name")
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE_WITH_A_VARIABLE, "/hostedtokenization/tokenization/form/"))
        .as("the constant CreateHostedTokenization.theHostedTokenizationUrlOf concatenates")
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE_WITH_A_VARIABLE, "//hostedtokenization/tokenization/form/"))
        .as(
            "the same path behind the double slash thePartialRedirectUrlOf reproduces, which the"
                + " vendor's own example publishes")
        .isTrue();
    assertThat(RelatedObjectAddressConvention.anAddressOfThePageAt(A_PAGE, "/farmers"))
        .as("a route that merely starts the same is not this page")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE_WITH_A_VARIABLE, "/hostedtokenization"))
        .as("the namespace above the page is not the page")
        .isFalse();
    assertThat(RelatedObjectAddressConvention.anAddressOfThePageAt(A_PAGE, "merchantId"))
        .as(
            "the name a map entry is given is read like every other constant of the closure and"
                + " this one carries no address; a module naming an entry after its own route is"
                + " refused by the same reading")
        .isFalse();
  }

  @Test
  void a_value_that_only_begins_with_a_declared_route_is_a_path_this_build_answers_on_nowhere() {
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                "/azericard/payer", "/azericard/payer/x"))
        .as(
            "the route is /azericard/payer and this build answers /azericard/payer/x nowhere, so a"
                + " structured value that merely starts with a declared route is no address of"
                + " that page")
        .isFalse();
    assertThat(RelatedObjectAddressConvention.anAddressOfThePageAt(A_PAGE, "/far/beyond"))
        .as("grow's /far is the shortest route declared here and the deepest trap for this floor")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE_WITH_A_VARIABLE,
                "/hostedtokenization/tokenization/form/2f4d08d6ddfc411e/beyond"))
        .as("a path variable's segment is one segment, and a path beyond it is another route")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE_WITH_A_VARIABLE,
                "http://localhost:8080//hostedtokenization/tokenization/form/2f4d08d6ddfc411e"))
        .as("the whole address of a page served at a route ending in a path variable is one")
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE, "http://localhost:8080/far"))
        .as("the address a payer follows, with the authority this build answers on in front")
        .isTrue();
    assertThat(RelatedObjectAddressConvention.anAddressOfThePageAt(A_PAGE, "/far#card"))
        .as("a fragment begins where the route ends, so the route is the whole of the path")
        .isTrue();
  }

  @Test
  void a_route_named_inside_a_sentence_this_build_carries_is_not_an_address_handed_over() {
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(A_PAGE, "pedido #9 /far from done"))
        .as(
            "a refusal message or a declaration naming the route in prose is read by this gate"
                + " like any other constant, and only the part of a value that runs up to the"
                + " route without a space, a quote or an angle bracket makes that route an"
                + " address: a sentence mentioning it does not")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE_WITH_A_VARIABLE,
                "its own response example carries \"preprod.direct.worldline-solutions.com"
                    + "//hostedtokenization/tokenization/form/2f4d08\" in it"))
        .as("the vendor's published example, quoted inside this build's own prose")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.anAddressOfThePageAt(
                A_PAGE_WITH_A_VARIABLE,
                "http://localhost:8080//hostedtokenization/tokenization/form/2f4d08"))
        .as("the same address with nothing but an address leading up to it is one")
        .isTrue();
  }

  @Test
  void the_shapes_of_value_this_control_surface_already_carries_are_no_address() {
    List<String> routes = List.of(A_PAGE, A_PAGE_WITH_A_VARIABLE, "/azericard/payer");

    for (String carried :
        List.of(
            "https://loja.exemplo/pedido/9",
            "paid at shop.example/basket",
            "12/30",
            "FINAL_AUTHORIZATION",
            "00020126580014BR.GOV.BCB.PIX01abc")) {
      for (String route : routes) {
        assertThat(RelatedObjectAddressConvention.anAddressOfThePageAt(route, carried))
            .as(
                "none of these is the address of a page this build serves, and a value of this"
                    + " shape a client sent never reaches this gate at all, which reads the"
                    + " constants compiled into this build and no runtime value: "
                    + carried)
            .isFalse();
      }
    }
  }

  @Test
  void a_literal_woven_into_a_concatenation_is_read_out_of_the_recipe_it_is_baked_into() {
    assertThat(
            RelatedObjectAddressConvention.theConstantsWovenInto(
                "\u0001/hostedtokenization/tokenization/form/\u0001"))
        .as("publicBaseUrl + THE_PUBLISHED_FORM_PATH + hostedTokenizationId, as javac weaves it")
        .containsExactly("/hostedtokenization/tokenization/form/");
    assertThat(
            RelatedObjectAddressConvention.theConstantsWovenInto(
                "\u0001\u0001//hostedtokenization/tokenization/form/\u0001"))
        .as("the authority, the path, the slash and the form path thePartialRedirectUrlOf joins")
        .containsExactly("//hostedtokenization/tokenization/form/");
    assertThat(RelatedObjectAddressConvention.theConstantsWovenInto("\u0001/far?hostedLink=\u0001"))
        .containsExactly("/far?hostedLink=");
    assertThat(RelatedObjectAddressConvention.theConstantsWovenInto("\u0002 and \u0001"))
        .as("a constant too large for the recipe is passed beside it and leaves this marker")
        .containsExactly(" and ");
    assertThat(RelatedObjectAddressConvention.theConstantsWovenInto("\u0001\u0001"))
        .as("a concatenation of two values and no literal of its own reads as no value")
        .isEmpty();
  }

  @Test
  void a_method_building_the_state_of_a_provider_object_is_found_by_the_constructor_it_calls() {
    assertThat(
            RelatedObjectAddressConvention.aStateIsBuiltIn(
                new Code(List.of(THE_STATES_CONSTRUCTOR), List.of())))
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.aStateIsBuiltIn(
                new Code(
                    List.of(
                        new Site(
                            RelatedObjectAddressConvention.THE_STATE_OF_A_PROVIDER_OBJECT,
                            "relatedObjects",
                            A_VALUE_IS_HANDED_OVER)),
                    List.of())))
        .as("reading a state back is not building one")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.aStateIsBuiltIn(
                new Code(
                    List.of(
                        new Site(
                            "dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts$AnObjectAPayerFinishes",
                            PayerPageAddresses.INITIALIZER,
                            "()V")),
                    List.of())))
        .as("another record of the same package is not the state this rule is about")
        .isFalse();
  }

  @Test
  void a_provider_module_is_named_only_by_a_class_that_sits_inside_one() {
    assertThat(
            RelatedObjectAddressConvention.aModuleIsNamedIn(
                "dev.marcinromanowski.pspsim.provider.grow.GrowProvider"))
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.aModuleIsNamedIn(
                "dev.marcinromanowski.pspsim.provider.package-info"))
        .as("the package declaration of the provider package belongs to no module")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.aModuleIsNamedIn(
                "dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep"))
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.moduleOf(
                "dev.marcinromanowski.pspsim.provider.worldline.CreateHosted"))
        .isEqualTo("worldline");
  }

  @Test
  void the_walk_out_of_a_state_reads_every_value_behind_it_and_names_the_way_it_got_there() {
    TheCompiledBuild build = aModuleThatHandsOverTheAddressOfItsPage();

    TheClosure closure = RelatedObjectAddressConvention.theClosureOf(build);

    assertThat(closure.methods())
        .as("the walk follows a call from the state into the map and on into the page")
        .containsExactly(BUILDS_A_STATE, HANDS_VALUES_OVER, MINTS_THE_ADDRESS);
    assertThat(closure.readings())
        .as("every value read along the way is kept with the way it was reached")
        .anySatisfy(
            reading -> {
              assertThat(reading.value()).isEqualTo("/far?token=");
              assertThat(reading.where()).isEqualTo(MINTS_THE_ADDRESS);
              assertThat(reading.howItIsReached())
                  .containsExactly(BUILDS_A_STATE, HANDS_VALUES_OVER, MINTS_THE_ADDRESS);
              assertThat(reading.theModuleThatHandsItOver())
                  .as("the module the way to it started in is the module it is charged to")
                  .isEqualTo("sixth");
            });
    assertThat(closure.callsNotFollowed())
        .as(
            "the call into the repository interface of this build and the call into the state's"
                + " own constructor each name a method no body was read for here")
        .containsExactly(
            new ACallWithNoBodyToRead(BUILDS_A_STATE, THE_STATES_CONSTRUCTOR),
            new ACallWithNoBodyToRead(BUILDS_A_STATE, THE_REPOSITORY_OF_THIS_BUILD));
    assertThat(closure.theCallsNotFollowedAsTheyRead())
        .as("the read line names every one of them, so its count is checkable")
        .containsExactly(
            "dev.marcinromanowski.pspsim.provider.sixth.SixthProvider.stateOf calls"
                + " dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState.<init>(Ljava/util/Map;)V",
            "dev.marcinromanowski.pspsim.provider.sixth.SixthProvider.stateOf calls"
                + " dev.marcinromanowski.pspsim.provider.sixth.SixthPayments.timelineOf()Ljava/util/List;");
  }

  @Test
  void a_call_out_of_this_build_is_not_walked_into_and_is_not_counted_as_one_left_unfollowed() {
    SequencedMap<Site, Code> methods = new LinkedHashMap<>();
    methods.put(
        BUILDS_A_STATE,
        new Code(
            List.of(
                THE_STATES_CONSTRUCTOR,
                new Site("java.util.LinkedHashMap", PayerPageAddresses.INITIALIZER, "()V")),
            List.of()));

    TheClosure closure =
        RelatedObjectAddressConvention.theClosureOf(
            new TheCompiledBuild(
                methods,
                List.of(BUILDS_A_STATE),
                List.of(BUILDS_A_STATE),
                List.of("sixth"),
                List.of()));

    assertThat(closure.methods()).containsExactly(BUILDS_A_STATE);
    assertThat(closure.callsNotFollowed())
        .as(
            "the kernel's own constructor is a method of this build whose body was not read here"
                + " and is counted, while the JDK's is outside this build and is not")
        .containsExactly(new ACallWithNoBodyToRead(BUILDS_A_STATE, THE_STATES_CONSTRUCTOR));
  }

  @Test
  void a_value_read_along_no_way_at_all_would_be_charged_to_no_module_and_swept_for_no_route() {
    assertThatThrownBy(() -> new Reading(MINTS_THE_ADDRESS, "/far?token=", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            RelatedObjectAddressConvention.A_READING_NAMES_THE_STATE_IT_WAS_REACHED_FROM);
  }

  @Test
  void an_address_a_state_is_built_out_of_is_an_offence_naming_the_route_and_the_whole_way() {
    TheClosure closure =
        RelatedObjectAddressConvention.theClosureOf(aModuleThatHandsOverTheAddressOfItsPage());

    List<String> offences =
        RelatedObjectAddressConvention.theAddressesAmongThem(
            closure, List.of(THE_PAGE_THE_SIXTH_MODULE_DECLARES));

    assertThat(offences).hasSize(1);
    assertThat(offences.get(0))
        .contains("SixthPage.urlFor")
        .contains("/far?token=")
        .contains(A_PAGE)
        .contains("dev.marcinromanowski.pspsim.provider.sixth.SixthOrigination")
        .contains("SixthProvider.stateOf")
        .contains("SixthProvider.relatedObjectsOf");
  }

  @Test
  void a_value_no_declared_page_is_served_at_is_no_offence_however_much_of_a_path_it_looks_like() {
    TheClosure closure =
        RelatedObjectAddressConvention.theClosureOf(aModuleThatHandsOverTheAddressOfItsPage());

    assertThat(
            RelatedObjectAddressConvention.theAddressesAmongThem(
                closure,
                List.of(
                    new PayerPageAddresses.Declared(
                        "sixth",
                        "/azericard/payer",
                        "dev.marcinromanowski.pspsim.provider.sixth.SixthOther"))))
        .as("the page this module mints is not the page that route declares")
        .isEmpty();
    assertThat(RelatedObjectAddressConvention.theAddressesAmongThem(closure, List.of()))
        .as("a build that declared no page sweeps for nothing, which verdictOn refuses outright")
        .isEmpty();
  }

  @Test
  void a_module_is_held_to_the_route_it_declares_itself_and_to_no_other_module_s_route() {
    TheClosure closure =
        RelatedObjectAddressConvention.theClosureOf(aModuleThatHandsOverTheAddressOfItsPage());

    assertThat(
            RelatedObjectAddressConvention.theAddressesAmongThem(
                closure, List.of(THE_PAGE_A_SEVENTH_MODULE_DECLARES)))
        .as(
            "the sixth module reading a route a seventh declares is read here and is no offence:"
                + " the path it names is answered for another provider's object and carries no"
                + " token this module mints")
        .isEmpty();
    assertThat(
            RelatedObjectAddressConvention.theAddressesAmongThem(
                closure,
                List.of(THE_PAGE_A_SEVENTH_MODULE_DECLARES, THE_PAGE_THE_SIXTH_MODULE_DECLARES)))
        .as("and the route it declares itself is the one it is charged with")
        .hasSize(1)
        .allSatisfy(offence -> assertThat(offence).contains("provider sixth declares at /far"));
  }

  @Test
  void two_modules_handing_the_same_value_over_are_each_charged_with_their_own_route() {
    SequencedMap<Site, Code> methods = new LinkedHashMap<>();
    methods.put(
        BUILDS_A_STATE, new Code(List.of(THE_STATES_CONSTRUCTOR, MINTS_THE_ADDRESS), List.of()));
    methods.put(
        A_SEVENTH_MODULE_BUILDS_A_STATE,
        new Code(List.of(THE_STATES_CONSTRUCTOR, MINTS_THE_ADDRESS), List.of()));
    methods.put(MINTS_THE_ADDRESS, new Code(List.of(), List.of("/azericard/payer")));
    TheCompiledBuild build =
        new TheCompiledBuild(
            methods,
            List.of(BUILDS_A_STATE, A_SEVENTH_MODULE_BUILDS_A_STATE),
            List.of(BUILDS_A_STATE, A_SEVENTH_MODULE_BUILDS_A_STATE),
            List.of("seventh", "sixth"),
            List.of());

    TheClosure closure = RelatedObjectAddressConvention.theClosureOf(build);

    assertThat(closure.readings())
        .as(
            "the one method both modules reach is read once for each of them, so neither module's"
                + " values are charged to the other's routes by the order the walk happened in")
        .extracting(Reading::theModuleThatHandsItOver)
        .containsExactlyInAnyOrder("seventh", "sixth");
    assertThat(
            RelatedObjectAddressConvention.theAddressesAmongThem(
                closure, List.of(THE_PAGE_A_SEVENTH_MODULE_DECLARES)))
        .hasSize(1)
        .allSatisfy(
            offence -> assertThat(offence).contains("SeventhProvider.stateOf").contains("seventh"));
  }

  @Test
  void a_provider_module_that_builds_no_state_leaves_this_gate_nothing_of_it_to_read() {
    SequencedMap<Site, Code> methods = new LinkedHashMap<>();
    methods.put(BUILDS_A_STATE, new Code(List.of(THE_STATES_CONSTRUCTOR), List.of()));

    List<String> offences =
        RelatedObjectAddressConvention.theModulesThisGateReadsNothingOf(
            new TheCompiledBuild(
                methods,
                List.of(BUILDS_A_STATE),
                List.of(BUILDS_A_STATE),
                List.of("seventh", "sixth"),
                List.of()));

    assertThat(offences).hasSize(1);
    assertThat(offences.get(0))
        .contains("seventh")
        .contains(RelatedObjectAddressConvention.THE_STATE_OF_A_PROVIDER_OBJECT);
  }

  @Test
  void a_state_built_in_a_method_whose_body_was_never_read_would_be_walked_out_of_in_silence() {
    assertThatThrownBy(
            () ->
                new TheCompiledBuild(
                    new LinkedHashMap<>(),
                    List.of(BUILDS_A_STATE),
                    List.of(BUILDS_A_STATE),
                    List.of("sixth"),
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            RelatedObjectAddressConvention.A_STATE_IS_BUILT_IN_A_METHOD_THIS_BUILD_READ);
  }

  @Test
  void the_compiled_build_hands_no_payer_page_address_to_the_state_of_any_provider_object() {
    TheCompiledBuild build = RelatedObjectAddressConvention.compiledUnder(COMPILED_MAIN);

    assertThat(build.providerModules())
        .as("every provider module compiled here is read")
        .containsExactly("autra", "azericard", "grow", "stripe", "worldline");
    assertThat(build.theModulesThatBuildAState())
        .as("and every one of them builds a state this gate can walk out of")
        .isEqualTo(build.providerModules());
    assertThat(build.whereAProviderBuildsAState())
        .as(
            "worldline builds the state of a tokenization session, which is where the two"
                + " addresses of its payer page were handed over before this rule existed")
        .anySatisfy(
            site -> {
              assertThat(site.owner())
                  .isEqualTo("dev.marcinromanowski.pspsim.provider.worldline.WorldlineProvider");
              assertThat(site.name()).isEqualTo("stateOfSession");
            });
    assertThat(build.theStatesBuiltBy("worldline"))
        .as("the states of one module are the seeds of that module's own walk and of no other")
        .isNotEmpty()
        .allSatisfy(
            site ->
                assertThat(site.owner())
                    .startsWith(PayerPageAddresses.PROVIDER_PACKAGE + "worldline."));
    assertThat(RelatedObjectAddressConvention.verdictOn(COMPILED_MAIN).offences())
        .as("the build under test keeps the rule this gate enforces")
        .isEmpty();
  }

  @Test
  void the_routes_this_gate_charges_a_module_with_are_the_ones_that_module_declares() {
    PayerPageAddresses.TheBuild pages = PayerPageAddresses.inTheBuild(COMPILED_MAIN);
    TheCompiledBuild build = RelatedObjectAddressConvention.compiledUnder(COMPILED_MAIN);

    assertThat(RelatedObjectAddressConvention.theRoutesEachModuleDeclares(pages.declared()))
        .as("the read line prints this map, so a reader checks every route against its module")
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "autra",
                List.of("/tokenize/sessions/{hostedCardSessionId}"),
                "azericard",
                List.of("/azericard/payer"),
                "grow",
                List.of(A_PAGE),
                "worldline",
                List.of(A_PAGE_WITH_A_VARIABLE)));
    assertThat(RelatedObjectAddressConvention.theModulesThatDeclareNoPageOfTheirOwn(build, pages))
        .as("stripe builds a state and serves no payer page, so its values are swept for nothing")
        .containsExactly("stripe");
  }

  @Test
  void a_build_that_declares_no_payer_page_is_a_broken_classpath(@TempDir Path nothingCompiled) {
    assertThatCode(() -> RelatedObjectAddressConvention.verdictOn(nothingCompiled))
        .as("the first floor verdictOn stands on, reached by a tree with no class in it at all")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(RelatedObjectAddressConvention.NO_PROVIDER_DECLARES_A_PAYER_PAGE);
    assertThatThrownBy(
            () ->
                RelatedObjectAddressConvention.refuseABuildThatDeclaresNoPayerPage(
                    nothingCompiled, new PayerPageAddresses.TheBuild(List.of(), Set.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(RelatedObjectAddressConvention.NO_PROVIDER_DECLARES_A_PAYER_PAGE);
  }

  @Test
  void a_build_that_builds_no_provider_object_state_is_a_broken_classpath(
      @TempDir Path nothingCompiled) {
    assertThatCode(
            () ->
                RelatedObjectAddressConvention.refuseABuildThatBuildsNoProviderObjectState(
                    nothingCompiled,
                    new TheCompiledBuild(
                        new LinkedHashMap<>(), List.of(), List.of(), List.of("sixth"), List.of())))
        .as(
            "a tree that declares a page and builds no state never reaches the first floor, so"
                + " this one is called where it stands rather than through verdictOn")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(RelatedObjectAddressConvention.NO_PROVIDER_MODULE_BUILDS_A_STATE)
        .hasMessageContaining(nothingCompiled.toString());
  }

  @Test
  void a_path_that_is_no_directory_of_compiled_classes_is_read_as_no_class_rather_than_walked(
      @TempDir Path nothingCompiled) throws Exception {
    Path aFileRatherThanADirectory = Files.createFile(nothingCompiled.resolve("classes"));

    assertThatThrownBy(
            () -> RelatedObjectAddressConvention.compiledUnder(aFileRatherThanADirectory))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(RelatedObjectAddressConvention.A_PATH_THIS_GATE_READS_NO_CLASS_UNDER);
    assertThatCode(
            () -> RelatedObjectAddressConvention.verdictOn(nothingCompiled.resolve("absent")))
        .as("verdictOn reads the classes first, so the same floor answers a path it cannot walk")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(RelatedObjectAddressConvention.A_PATH_THIS_GATE_READS_NO_CLASS_UNDER);
  }

  private static final String AN_ENUM_OF_THIS_BUILD =
      "dev.marcinromanowski.pspsim.provider.grow.GrowTransactionKind";

  private static final String AN_ENUM_NESTED_IN_A_CLASS_OF_THIS_BUILD =
      "dev.marcinromanowski.pspsim.kernel.money.Ledger$RefundExtent";

  private static final String A_CLASS_OF_THIS_BUILD_THAT_IS_NO_ENUM =
      "dev.marcinromanowski.pspsim.provider.grow.GrowProvider";

  private static final String A_RECORD_OF_THIS_BUILD_THAT_IS_NO_ENUM =
      RelatedObjectAddressConvention.THE_STATE_OF_A_PROVIDER_OBJECT;

  private static final String THE_SPECIMEN_MODULE = "dev.marcinromanowski.pspsim.provider.specimen";

  private static final String THE_PAGE_THE_SPECIMEN_MODULE_SERVES = "/specimen/payer";

  @Test
  void an_owner_this_build_compiled_as_an_enum_is_one_whose_superclass_is_java_lang_enum() {
    assertThat(
            RelatedObjectAddressConvention.compiledAsAnEnum(
                theCompiledFormOf(AN_ENUM_OF_THIS_BUILD)))
        .as("the enum whose ordinal() grow reads three times inside this closure")
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.compiledAsAnEnum(
                theCompiledFormOf(AN_ENUM_NESTED_IN_A_CLASS_OF_THIS_BUILD)))
        .as("an enum nested in a class of the kernel is compiled as one in its own class file")
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.compiledAsAnEnum(
                theCompiledFormOf(A_CLASS_OF_THIS_BUILD_THAT_IS_NO_ENUM)))
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.compiledAsAnEnum(
                theCompiledFormOf(A_RECORD_OF_THIS_BUILD_THAT_IS_NO_ENUM)))
        .as(
            "a record of the same package as the state this rule is about extends"
                + " java.lang.Record, and the provider class that builds that state extends"
                + " java.lang.Object")
        .isFalse();
  }

  @Test
  void the_calls_java_lang_enum_declares_final_are_name_and_ordinal_at_their_own_descriptors() {
    assertThat(
            RelatedObjectAddressConvention.aMethodJavaLangEnumDeclaresFinal(
                new Site(AN_ENUM_OF_THIS_BUILD, "name", AN_ADDRESS_IS_MINTED)))
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.aMethodJavaLangEnumDeclaresFinal(
                new Site(AN_ENUM_OF_THIS_BUILD, "ordinal", "()I")))
        .isTrue();
    assertThat(
            RelatedObjectAddressConvention.aMethodJavaLangEnumDeclaresFinal(
                new Site(AN_ENUM_OF_THIS_BUILD, "name", "(Ljava/lang/String;)Ljava/lang/String;")))
        .as(
            "java.lang.Enum declares name() final and leaves name(String) free, so an enum may"
                + " declare that overload, leave it abstract and mint an address in each"
                + " constant's own body")
        .isFalse();
    assertThat(
            RelatedObjectAddressConvention.aMethodJavaLangEnumDeclaresFinal(
                new Site(
                    AN_ENUM_OF_THIS_BUILD, "theAddressOfThePageItServes", AN_ADDRESS_IS_MINTED)))
        .isFalse();
  }

  @Test
  void the_reads_of_what_an_enum_inherits_are_the_only_calls_this_closure_stops_at_in_this_build() {
    TheCompiledBuild build = RelatedObjectAddressConvention.compiledUnder(COMPILED_MAIN);

    TheClosure closure = RelatedObjectAddressConvention.theClosureOf(build);

    assertThat(closure.callsReadingWhatJavaLangEnumDeclaresFinal())
        .as("the ten reads the read line counted as unfollowed are read for what they are")
        .isNotEmpty()
        .allSatisfy(
            call -> {
              assertThat(build.anEnumOfThisBuild(call.called().owner())).isTrue();
              assertThat(
                      RelatedObjectAddressConvention.aMethodJavaLangEnumDeclaresFinal(
                          call.called()))
                  .isTrue();
            });
    assertThat(closure.theCallsReadingWhatAnEnumInheritsAsTheyRead())
        .contains(
            "dev.marcinromanowski.pspsim.provider.worldline.WorldlineProvider.relatedObjectsOf calls"
                + " dev.marcinromanowski.pspsim.provider.worldline.WorldlineAuthorizationMode.name()Ljava/lang/String;");
    assertThat(closure.callsNotFollowed())
        .as(
            "no call of this build's closure stops at a method compiled under another owner any"
                + " more, so the shape this leaves open is reached by nothing here; a call"
                + " appearing in this list is an address this gate would not see, and the"
                + " specimen module below states what one looks like")
        .isEmpty();
  }

  @Test
  void an_address_minted_behind_a_declaration_carrying_no_body_is_named_as_a_call_not_followed(
      @TempDir Path into) throws Exception {
    Path classes =
        aProviderModuleCompiledFrom(
            into, theSpecimenThatMintsAnAddressBehindEveryKindOfDeclaration());

    TheCompiledBuild build = RelatedObjectAddressConvention.compiledUnder(classes);
    TheClosure closure = RelatedObjectAddressConvention.theClosureOf(build);

    assertThat(build.theEnumsOfThisBuild())
        .as(
            "the body a constant of that enum carries is compiled as a class of its own, which"
                + " extends the enum rather than java.lang.Enum and overrides what the enum left"
                + " abstract, so it is no owner whose calls this gate may excuse")
        .containsExactly(THE_SPECIMEN_MODULE + ".SpecimenPayerPageKind");
    assertThat(closure.theCallsNotFollowedAsTheyRead())
        .as(
            "an interface declaration and the abstract method an enum leaves to its constants"
                + " each hide a method this build compiles under another owner, and the name() an"
                + " interface declares itself is no name() java.lang.Enum declares final")
        .containsExactlyInAnyOrder(
            THE_SPECIMEN_MODULE
                + ".SpecimenProvider.stateOf calls "
                + THE_SPECIMEN_MODULE
                + ".SpecimenPayerPages.theAddressOfThePageItServes()Ljava/lang/String;",
            THE_SPECIMEN_MODULE
                + ".SpecimenProvider.stateOf calls "
                + THE_SPECIMEN_MODULE
                + ".SpecimenPayerPages.name()Ljava/lang/String;",
            THE_SPECIMEN_MODULE
                + ".SpecimenProvider.stateOf calls "
                + THE_SPECIMEN_MODULE
                + ".SpecimenPayerPageKind.theAddressOfThePageItServes()Ljava/lang/String;",
            THE_SPECIMEN_MODULE
                + ".SpecimenProvider.stateOf calls "
                + THE_SPECIMEN_MODULE
                + ".SpecimenPayerPageKind.name(Ljava/lang/String;)Ljava/lang/String;");
    assertThat(closure.theCallsReadingWhatAnEnumInheritsAsTheyRead())
        .as("what the enum inherits is read off the same owner and counted apart from those")
        .containsExactlyInAnyOrder(
            THE_SPECIMEN_MODULE
                + ".SpecimenProvider.stateOf calls "
                + THE_SPECIMEN_MODULE
                + ".SpecimenPayerPageKind.name()Ljava/lang/String;",
            THE_SPECIMEN_MODULE
                + ".SpecimenProvider.stateOf calls "
                + THE_SPECIMEN_MODULE
                + ".SpecimenPayerPageKind.ordinal()I");
    assertThat(
            RelatedObjectAddressConvention.theAddressesAmongThem(
                closure,
                List.of(
                    new PayerPageAddresses.Declared(
                        "specimen",
                        THE_PAGE_THE_SPECIMEN_MODULE_SERVES,
                        THE_SPECIMEN_MODULE + ".SpecimenOrigination"))))
        .as(
            "the four addresses behind those declarations are missed, which is the shape this"
                + " gate states it misses, and the one minted in a method of the module's own"
                + " class is caught in the same sweep, so an empty verdict here would be a sweep"
                + " that read nothing rather than a build that hides nothing")
        .hasSize(1)
        .allSatisfy(
            offence ->
                assertThat(offence)
                    .contains("SpecimenProviderThatMintsItInTheOpen.theAddressOfThePageItServes")
                    .contains(THE_PAGE_THE_SPECIMEN_MODULE_SERVES));
  }

  @Test
  void the_count_of_shapes_this_gate_misses_is_the_length_of_the_list_its_refusal_joins() {
    String printed = RelatedObjectAddressConvention.theShapesThisGateMissesOnPurpose();

    assertThat(printed)
        .as("the count cannot disagree with the list beside it, because it is that list's length")
        .startsWith(
            RelatedObjectAddressConvention.THE_SHAPES_THIS_GATE_MISSES.size()
                + " shapes are missed on purpose. ");
    assertThat(RelatedObjectAddressConvention.THE_SHAPES_THIS_GATE_MISSES)
        .allSatisfy(shape -> assertThat(printed).contains(shape));
    assertThat(RelatedObjectAddressConvention.how())
        .as("the refusal a failing run throws carries every one of them")
        .contains(printed)
        .contains(
            RelatedObjectAddressConvention.AN_ADDRESS_MINTED_BEHIND_A_DECLARATION_CARRYING_NO_BODY);
  }

  private static ClassModel theCompiledFormOf(String binaryName) {
    try {
      return ClassFile.of().parse(COMPILED_MAIN.resolve(binaryName.replace('.', '/') + ".class"));
    } catch (IOException unreadable) {
      throw new AssertionError(unreadable);
    }
  }

  private static Map<String, String> theSpecimenThatMintsAnAddressBehindEveryKindOfDeclaration() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "ProviderObjectState.java",
        """
        package dev.marcinromanowski.pspsim.kernel.spi;

        import java.util.Map;

        public record ProviderObjectState(Map<String, String> relatedObjects) {}
        """);
    sources.put(
        "SpecimenPayerPages.java",
        """
        package dev.marcinromanowski.pspsim.provider.specimen;

        public interface SpecimenPayerPages {

          String theAddressOfThePageItServes();

          String name();
        }
        """);
    sources.put(
        "SpecimenPayerPage.java",
        """
        package dev.marcinromanowski.pspsim.provider.specimen;

        public final class SpecimenPayerPage implements SpecimenPayerPages {

          @Override
          public String theAddressOfThePageItServes() {
            return "/specimen/payer";
          }

          @Override
          public String name() {
            return "/specimen/payer";
          }
        }
        """);
    sources.put(
        "SpecimenPayerPageKind.java",
        """
        package dev.marcinromanowski.pspsim.provider.specimen;

        public enum SpecimenPayerPageKind {
          THE_PAGE_THIS_MODULE_HOSTS {
            @Override
            public String theAddressOfThePageItServes() {
              return "/specimen/payer";
            }

            @Override
            public String name(String of) {
              return "/specimen/payer";
            }
          };

          public abstract String theAddressOfThePageItServes();

          public abstract String name(String of);
        }
        """);
    sources.put(
        "SpecimenProvider.java",
        """
        package dev.marcinromanowski.pspsim.provider.specimen;

        import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
        import java.util.LinkedHashMap;

        public final class SpecimenProvider {

          public ProviderObjectState stateOf(SpecimenPayerPages pages, SpecimenPayerPageKind kind) {
            LinkedHashMap<String, String> related = new LinkedHashMap<>();
            related.put("thePageBehindAnInterface", pages.theAddressOfThePageItServes());
            related.put("whatThatInterfaceIsNamed", pages.name());
            related.put("thePageBehindAnAbstractConstant", kind.theAddressOfThePageItServes());
            related.put("whatThatPageIsNamedFor", kind.name("the page a payer finishes on"));
            related.put("whatTheKindIsCalled", kind.name());
            related.put("whereTheKindStands", String.valueOf(kind.ordinal()));
            return new ProviderObjectState(related);
          }
        }
        """);
    sources.put(
        "SpecimenProviderThatMintsItInTheOpen.java",
        """
        package dev.marcinromanowski.pspsim.provider.specimen;

        import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
        import java.util.LinkedHashMap;

        public final class SpecimenProviderThatMintsItInTheOpen {

          public ProviderObjectState stateOf() {
            LinkedHashMap<String, String> related = new LinkedHashMap<>();
            related.put("thePageItPublishes", theAddressOfThePageItServes());
            return new ProviderObjectState(related);
          }

          private String theAddressOfThePageItServes() {
            return "/specimen/payer";
          }
        }
        """);
    return sources;
  }

  private static Path aProviderModuleCompiledFrom(Path into, Map<String, String> sources)
      throws IOException {
    Path sourceRoot = Files.createDirectories(into.resolve("source"));
    List<Path> written = new ArrayList<>();
    for (Map.Entry<String, String> source : sources.entrySet()) {
      Path file = sourceRoot.resolve(source.getKey());
      Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
      written.add(file);
    }
    Path classes = Files.createDirectories(into.resolve("classes"));
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler)
        .as(
            "this case compiles a provider module of its own, because the live tree mints no"
                + " address behind a declaration carrying no body, so it needs the compiler of"
                + " the JDK it runs on")
        .isNotNull();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager files =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      assertThat(
              compiler
                  .getTask(
                      null,
                      files,
                      diagnostics,
                      List.of("-d", classes.toString()),
                      null,
                      files.getJavaFileObjectsFromPaths(written))
                  .call())
          .as("the module this case compiles must compile: %s", diagnostics.getDiagnostics())
          .isTrue();
    }
    return classes;
  }

  private static TheCompiledBuild aModuleThatHandsOverTheAddressOfItsPage() {
    SequencedMap<Site, Code> methods = new LinkedHashMap<>();
    methods.put(
        BUILDS_A_STATE,
        new Code(
            List.of(HANDS_VALUES_OVER, THE_STATES_CONSTRUCTOR, THE_REPOSITORY_OF_THIS_BUILD),
            List.of("sixth")));
    methods.put(
        HANDS_VALUES_OVER,
        new Code(List.of(MINTS_THE_ADDRESS), List.of("merchantId", "thePageAPayerFinishes")));
    methods.put(MINTS_THE_ADDRESS, new Code(List.of(), List.of("/far?token=")));
    return new TheCompiledBuild(
        methods, List.of(BUILDS_A_STATE), List.of(BUILDS_A_STATE), List.of("sixth"), List.of());
  }
}
