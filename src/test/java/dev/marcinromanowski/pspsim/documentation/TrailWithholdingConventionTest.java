package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.documentation.TrailWithholdingConvention.ADeliveryWithNothingToWithhold;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingConvention.AFieldDeliveredInTheClear;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingConvention.APageWithNoAddressToWithhold;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingConvention.AnAddressInTheClear;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.ADeliveringModule;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.AModule;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.TheBuild;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.TheDeliveries;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.ThePositionsAConstantIsExcusedAt;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrailWithholdingConventionTest {

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final String A_CLASS_THAT_KEEPS_A_ROW = "a.Dispatcher";

  private static final String A_ROW_IS_WRITTEN_IN =
      A_CLASS_THAT_KEEPS_A_ROW + ".record(WebhookDelivery,WebhookPayload)void";

  private static final List<String> THE_MODULES_THIS_SIMULATOR_SERVES =
      List.of("autra", "azericard", "grow", "stripe", "tranzila", "worldline");

  private static final List<String> THE_CLASSES_THAT_ANSWER_A_CLIENT =
      List.of(
          "dev.marcinromanowski.pspsim.provider.autra.AutraApi",
          "dev.marcinromanowski.pspsim.provider.autra.PayerOnTheHostedCardPage",
          "dev.marcinromanowski.pspsim.provider.azericard.AzericardGateway",
          "dev.marcinromanowski.pspsim.provider.azericard.AzericardPayerCheckout",
          "dev.marcinromanowski.pspsim.provider.grow.GrowHostedCheckout",
          "dev.marcinromanowski.pspsim.provider.grow.GrowLightApi",
          "dev.marcinromanowski.pspsim.provider.stripe.StripeApi",
          "dev.marcinromanowski.pspsim.provider.tranzila.TranzilaApi",
          "dev.marcinromanowski.pspsim.provider.tranzila.TranzilaDirectNg",
          "dev.marcinromanowski.pspsim.provider.tranzila.TranzilaHostedFields",
          "dev.marcinromanowski.pspsim.provider.worldline.WorldlineApi",
          "dev.marcinromanowski.pspsim.provider.worldline.WorldlineHostedTokenizationCheckout");

  private static final List<String> THE_ADDRESSES_THIS_BUILD_PUBLISHES =
      List.of(
          "autra AUTRA_BASE_URL",
          "autra hostedCardUrl",
          "azericard AZERICARD_GATEWAY_URL",
          "azericard MERCH_URL",
          "grow GROW_BASE_URL",
          "grow Location",
          "grow cancelUrl",
          "grow cancel_url",
          "grow hosted_link",
          "grow notifyUrl",
          "grow notify_url",
          "grow successUrl",
          "grow success_url",
          "grow url",
          "stripe doc_url",
          "stripe url",
          "tranzila TRANZILA_API_BASE_URL",
          "tranzila TRANZILA_API_V2_BASE_URL",
          "tranzila TRANZILA_DIRECTNG_BASE_URL",
          "tranzila TRANZILA_HF_BASE_URL",
          "tranzila TRANZILA_REPORT_BASE_URL",
          "tranzila fail_url",
          "tranzila fail_url_address",
          "tranzila notify_url",
          "tranzila notify_url_address",
          "tranzila success_url",
          "tranzila success_url_address",
          "worldline Location",
          "worldline WORLDLINE_BASE_URL",
          "worldline hostedTokenizationUrl",
          "worldline partialRedirectUrl");

  private static final int THE_NAMES_EXCUSED_SOMEWHERE_AND_PUBLISHED_ELSEWHERE = 90;

  private static final String A_PROVIDER_PACKAGE_OF_ITS_OWN =
      "dev.marcinromanowski.pspsim.provider.sixth";

  private static final String THE_DIRECTIONS_A_ROW_NAMES =
      """
      package dev.marcinromanowski.pspsim.kernel.audit;

      public enum AuditDirection {
        INBOUND_REQUEST,
        OUTBOUND_RESPONSE
      }
      """;

  private static final String THE_ROW_A_TRAIL_KEEPS =
      """
      package dev.marcinromanowski.pspsim.kernel.audit;

      public record AuditRecord(AuditDirection direction, String body) {}
      """;

  private static final String THE_ROW_A_TRAIL_KEEPS_IN_A_FIXTURE =
      """
      package dev.marcinromanowski.pspsim.kernel.audit;

      import java.util.Map;

      public record AuditRecord(AuditDirection direction, Map<String, String> body) {}
      """;

  private static final MethodTypeDesc THE_SHAPE_OF_THAT_BOOTSTRAP =
      MethodTypeDesc.ofDescriptor(
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
              + "Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;"
              + "[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;");

  private static final String THE_DECLARATION_OF_A_MODULE_OF_ITS_OWN =
      """
      package dev.marcinromanowski.pspsim.provider.sixth;

      import java.util.Set;

      public final class SixthFields {

        public static final Set<String> SENSITIVE_RESPONSE_FIELDS =
            Set.of("data.processToken");

        private SixthFields() {}
      }
      """;

  private static final String WHOLE_KEY = TrailWithholdingDeclarations.theWholeKeyRedactor();

  private static final String A_LEAF = "a.SixthFields.withholding";

  private static AModule aModule(String provider, List<String> declaredIn, List<String> withheld) {
    return new AModule(
        provider,
        declaredIn,
        withheld,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(WHOLE_KEY));
  }

  private static AModule publishing(
      String provider, List<String> withheld, List<String> published, String redactor) {
    return new AModule(
        provider,
        List.of("a." + provider + "Fields"),
        withheld,
        published,
        List.of(),
        List.of(),
        List.of(),
        List.of(redactor));
  }

  private static AModule answering(
      List<String> inAll, List<String> withoutTheDeclaration, List<String> namingNoDirection) {
    return new AModule(
        "sixth",
        List.of("a.SixthFields"),
        List.of("hostedPageUrl"),
        List.of(),
        inAll,
        withoutTheDeclaration,
        namingNoDirection,
        List.of(WHOLE_KEY));
  }

  private static AModule theModuleOf(String provider) {
    return TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN).modules().stream()
        .filter(module -> module.provider().equals(provider))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void the_compiled_build_holds_every_module_and_every_one_of_them_declares_what_it_withholds() {
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN);

    assertThat(build.providers())
        .as(
            "the first denominator of this gate: a sweep that matched no module would pass"
                + " forever, so the modules it examined are named one by one")
        .containsExactlyElementsOf(THE_MODULES_THIS_SIMULATOR_SERVES);
    assertThat(build.classesRead())
        .as("the classes the sweep read, which no clean tree brings below a hundred")
        .isGreaterThan(100);
    assertThat(build.constantsRead())
        .as(
            "the string constant occurrences it read them for, every one of them placed, which is"
                + " the denominator of the floor")
        .isGreaterThan(1000);
    assertThat(TrailWithholdingConvention.theClassesThatDeclareIt(build.modules()))
        .containsExactly(
            "dev.marcinromanowski.pspsim.provider.autra.AutraFields",
            "dev.marcinromanowski.pspsim.provider.azericard.AzericardFields",
            "dev.marcinromanowski.pspsim.provider.grow.GrowFields",
            "dev.marcinromanowski.pspsim.provider.stripe.StripeFields",
            "dev.marcinromanowski.pspsim.provider.tranzila.TranzilaFields",
            "dev.marcinromanowski.pspsim.provider.worldline.WorldlineFields");
    assertThat(TrailWithholdingConvention.howManyFieldsAreWithheld(build.modules()))
        .as("the second denominator: the fields these declarations name between them")
        .isEqualTo(34);
    assertThat(TrailWithholdingConvention.verdictOn(COMPILED_MAIN).offences())
        .as("the build under test keeps the rule this gate enforces")
        .isEmpty();
  }

  @Test
  void the_fields_each_module_withholds_are_read_out_of_its_own_compiled_declaration() {
    assertThat(theModuleOf("autra").theFieldsItWithholds())
        .containsExactlyInAnyOrder(
            "transactionalToken", "accessToken", "access_token", "hostedCardUrl");
    assertThat(theModuleOf("grow").theFieldsItWithholds())
        .containsExactlyInAnyOrder(
            "data.processToken",
            "data.url",
            "data.transactionToken",
            "data.transactions.transactionToken",
            "data.customField.cField1");
    assertThat(theModuleOf("worldline").theFieldsItWithholds())
        .containsExactlyInAnyOrder("hostedTokenizationUrl", "partialRedirectUrl");
    assertThat(theModuleOf("stripe").theFieldsItWithholds())
        .containsExactlyInAnyOrder("client_secret", "secret");
    assertThat(theModuleOf("tranzila").theFieldsItWithholds())
        .containsExactlyInAnyOrder(
            "transaction_result.token",
            "original_request.card_number",
            "original_request.card_holder_id",
            "TranzilaTK",
            "myid",
            "transaction_response.token",
            "card_number",
            "cvv",
            "card_holder_id",
            "credit_card_number",
            "card_holder_id_number",
            "identity_number",
            "transaction_response.user_form_data.card_number",
            "transaction_response.user_form_data.cvv",
            "transaction_response.user_form_data.card_holder_id",
            "transaction_response.user_form_data.myid",
            "transaction_response.user_form_data.credit_card_number",
            "transaction_response.user_form_data.card_holder_id_number",
            "transaction_response.user_form_data.identity_number",
            "transactions.credit_card_token",
            "transactions.credit_card_owner_id");
    assertThat(theModuleOf("azericard").theFieldsItWithholds())
        .as("azericard answers no address for the page it serves and withholds nothing")
        .isEmpty();
  }

  @Test
  void every_class_that_writes_an_answer_row_reads_its_own_modules_declaration() {
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN);

    assertThat(TrailWithholdingConvention.theClassesThatAnswerAClient(build.modules()))
        .as("the third denominator: the classes that write a row naming an answer to a client")
        .containsExactlyElementsOf(THE_CLASSES_THAT_ANSWER_A_CLIENT);
    assertThat(TrailWithholdingConvention.howManyAnswerAClient(build.modules())).isEqualTo(12);
    assertThat(build.modules())
        .allSatisfy(
            module ->
                assertThat(module.theClassesThatAnswerAClientWithoutReadingItsOwnDeclaration())
                    .isEmpty())
        .allSatisfy(
            module ->
                assertThat(module.theRowsItBuildsWhoseDirectionNothingNames())
                    .as(
                        "every class of %s that keeps a trail row names the direction it carries,"
                            + " so none of them falls out of the set above",
                        module.provider())
                    .isEmpty());
  }

  @Test
  void every_published_name_that_reads_as_an_address_is_withheld_or_registered() {
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN);

    assertThat(TrailWithholdingConvention.theAddressShapedNames(build.modules()))
        .as(
            "the fourth denominator: the names the floor was compared against, named one by one"
                + " so a sweep that stopped matching could not pass as a clean tree")
        .containsExactlyElementsOf(THE_ADDRESSES_THIS_BUILD_PUBLISHES);
    assertThat(TrailWithholdingConvention.howManyAddressShapedNames(build.modules())).isEqualTo(31);
    assertThat(TrailWithholdingConvention.THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR)
        .as("the registry is the whole of what any module answers in the clear")
        .hasSize(27)
        .allSatisfy(admitted -> assertThat(admitted.why()).isNotBlank());
    assertThat(TrailWithholdingConvention.theModulesThatRegisterAnAddress())
        .containsExactlyElementsOf(THE_MODULES_THIS_SIMULATOR_SERVES);
    assertThat(TrailWithholdingConvention.THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR)
        .extracting(AnAddressInTheClear::named)
        .as("the four addresses of a payer page this simulator serves are withheld, not admitted")
        .doesNotContain("hostedCardUrl", "hostedTokenizationUrl", "partialRedirectUrl", "data.url");
  }

  @Test
  void the_read_lines_name_the_modules_the_fields_the_addresses_and_the_answering_classes() {
    List<String> read = TrailWithholdingConvention.verdictOn(COMPILED_MAIN).read();

    assertThat(read).hasSize(12);
    assertThat(read.get(0))
        .contains(
            "examined the " + THE_MODULES_THIS_SIMULATOR_SERVES.size() + " provider module(s)");
    assertThat(read.get(1))
        .as("the denominator of the exclusion: what was excused, where, and what it left read")
        .contains("at the position the compiler emitted them, never across a class")
        .contains(
            "read as a string of the run its own <clinit> builds the value it stores into a"
                + " SENSITIVE_RESPONSE_FIELDS")
        .contains("leaves " + THE_NAMES_EXCUSED_SOMEWHERE_AND_PUBLISHED_ELSEWHERE + " name(s)")
        .contains("dev.marcinromanowski.pspsim.provider.autra.AutraPixEvent FINANCIAL_TRANSACTION");
    assertThat(read.get(2)).contains("read 34 withheld response field(s)");
    assertThat(read.get(3)).contains("examined 31 published name(s) that read as an address");
    assertThat(read.get(4)).contains("27 of those address(es) are registered").contains("over 6");
    assertThat(read.get(5))
        .as("the response declaration of stripe is the one this build hands to no whole-key reader")
        .contains("stripe matches not a whole key")
        .contains("worldline matches a whole key")
        .as(
            "and the delivery declaration of that same module is handed to the whole-key redactor,"
                + " which is the fact a read of the response declaration alone printed wrong")
        .contains(
            "of each module that declares one: [autra matches a whole key, as "
                + WHOLE_KEY
                + " does, azericard matches a whole key, as "
                + WHOLE_KEY
                + " does, grow matches a whole key, as "
                + WHOLE_KEY
                + " does, stripe matches a whole key, as "
                + WHOLE_KEY
                + " does, tranzila matches a whole key, as "
                + WHOLE_KEY
                + " does]");
    assertThat(read.get(6))
        .contains("18 method(s) across those modules build a")
        .contains("of which 12 build one carrying OUTBOUND_RESPONSE")
        .contains("which is 12 class(es)")
        .as("the line reports how many of them read it rather than claiming all of them do")
        .contains("of which 12 read their own module's declaration")
        .contains("0 class(es) build a row that carries no answer to a client");
    assertThat(read.get(7)).contains("[autra, azericard, grow, tranzila, worldline]");
    assertThat(read.get(8))
        .contains("of which 1 keep(s) a row naming one of")
        .as("the line counts the methods that write a delivery row, not the classes they sit in")
        .contains("in 3 method(s) of their own")
        .as("and reports how many of those methods write it from a withheld body")
        .contains("of which 3 write(s) it from a body a module withheld")
        .contains(
            "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher"
                + ".record(WebhookDelivery,WebhookAttempt,WebhookPayload)void")
        .contains(
            "names [INBOUND_REQUEST, OUTBOUND_RESPONSE, OUTBOUND_WEBHOOK_ATTEMPT,"
                + " OUTBOUND_WEBHOOK_DROPPED, OUTBOUND_WEBHOOK_NOT_SENT]");
    assertThat(read.get(9))
        .contains(
            "5 of the 6 provider module(s) mint a delivery: [autra, azericard, grow, stripe,"
                + " tranzila]")
        .contains("withholding 13 delivered field(s)");
    assertThat(read.get(10))
        .contains(
            "[dev.marcinromanowski.pspsim.provider.stripe.StripeWebhookRendering] mint one out of a body");
    assertThat(read.get(11))
        .contains(
            "2 module(s) deliver a body registered as holding nothing to withhold: [autra,"
                + " azericard]")
        .contains("3 field(s) are recorded as delivered in the clear:")
        .contains("[autra userDocument, azericard TOKEN, azericard P_SIGN]");
  }

  @Test
  void a_build_holding_no_provider_module_is_a_broken_classpath_rather_than_a_clean_tree(
      @TempDir Path nothingCompiled) {
    assertThatThrownBy(() -> TrailWithholdingConvention.verdictOn(nothingCompiled))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("so this gate would examine no provider");
  }

  @Test
  void a_directory_of_no_classes_at_all_is_a_sweep_that_read_nothing() {
    assertThatThrownBy(
            () -> TrailWithholdingDeclarations.inTheBuild(Path.of("no-such-classes-root")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not a directory of compiled classes");
  }

  @Test
  void a_module_that_declares_nothing_names_no_field_its_answers_are_written_redacted() {
    AModule module = aModule("sixth", List.of(), List.of());

    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .singleElement()
        .asString()
        .contains("provider sixth declares no SENSITIVE_RESPONSE_FIELDS");
  }

  @Test
  void a_module_that_declares_it_twice_leaves_a_class_withholding_less_than_the_module_does() {
    AModule module =
        aModule("sixth", List.of("a.SixthFields", "a.SixthMoreFields"), List.of("hostedPageUrl"));

    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .singleElement()
        .asString()
        .contains("declares SENSITIVE_RESPONSE_FIELDS in [a.SixthFields, a.SixthMoreFields]");
  }

  @Test
  void a_class_answering_a_client_around_its_own_declaration_is_named_with_that_class() {
    AModule module =
        answering(List.of("a.SixthApi", "a.SixthPage"), List.of("a.SixthPage"), List.of());

    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .singleElement()
        .asString()
        .startsWith("a.SixthPage writes a row naming OUTBOUND_RESPONSE")
        .contains("reads no SENSITIVE_RESPONSE_FIELDS of provider sixth")
        .contains("the read, not the reach");
  }

  @Test
  void a_row_no_method_of_its_class_names_a_direction_for_would_drop_it_out_of_the_sweep() {
    AModule module =
        answering(List.of(), List.of(), List.of("a.SixthRows.record(AuditDirection)AuditRecord"));

    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .singleElement()
        .asString()
        .startsWith(
            "a.SixthRows.record(AuditDirection)AuditRecord builds a"
                + " dev.marcinromanowski.pspsim.kernel.audit.AuditRecord")
        .contains("no method of its own class that names a direction")
        .contains("would leave provider sixth out of the set it examines");
  }

  @Test
  void a_declaration_handed_to_two_redactors_that_match_differently_is_read_by_neither_rule() {
    AModule disagreeing =
        new AModule(
            "sixth",
            List.of("a.SixthFields"),
            List.of("hostedPageUrl"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(A_LEAF, WHOLE_KEY));

    assertThat(disagreeing.itsRedactorsDisagree()).isTrue();
    assertThat(TrailWithholdingConvention.theOffenceOf(disagreeing))
        .singleElement()
        .asString()
        .contains("hands its SENSITIVE_RESPONSE_FIELDS to")
        .contains("do not match a key the same way");
  }

  @Test
  void a_whole_key_module_is_met_by_a_declared_key_and_never_by_a_bare_name_inside_one() {
    AModule declaringThePath =
        publishing("sixth", List.of("data.hostedPageUrl"), List.of("hostedPageUrl"), WHOLE_KEY);
    AModule declaringAnother =
        publishing("sixth", List.of("data.processToken"), List.of("hostedPageUrl"), WHOLE_KEY);
    AModule declaringABracket =
        publishing("sixth", List.of("data[hostedPageUrl]"), List.of("hostedPageUrl"), WHOLE_KEY);

    assertThat(declaringThePath.itsRedactorMatchesAWholeKey()).isTrue();
    assertThat(declaringThePath.withholds("hostedPageUrl"))
        .as("Redaction matches the whole key, and data.hostedPageUrl is that whole key")
        .isTrue();
    assertThat(TrailWithholdingConvention.theOffenceOf(declaringThePath)).isEmpty();
    assertThat(declaringABracket.withholds("hostedPageUrl"))
        .as(
            "data[hostedPageUrl] is a key of its own, and Redaction would never match it against"
                + " a body keyed hostedPageUrl")
        .isFalse();
    assertThat(TrailWithholdingConvention.theOffenceOf(declaringAnother))
        .singleElement()
        .asString()
        .contains("publishes the name hostedPageUrl, which reads as an address")
        .contains("a whole key, as " + WHOLE_KEY + " does");
  }

  @Test
  void a_module_whose_own_redactor_matches_a_leaf_is_met_by_the_leaf_of_what_it_withholds() {
    AModule leafBased =
        publishing("sixth", List.of("data[hostedPageUrl]"), List.of("hostedPageUrl"), A_LEAF);

    assertThat(leafBased.itsRedactorMatchesAWholeKey()).isFalse();
    assertThat(leafBased.itsRedactorsDisagree()).isFalse();
    assertThat(leafBased.withholds("hostedPageUrl"))
        .as(
            "a module whose redactor reads the leaf of what it withholds is met by that leaf; no"
                + " module of this build has that redactor, so this states the rule and not a"
                + " provider: stripe's two redactors read the leaf of the published key and the"
                + " member name, neither of which this synthetic declaration would reach")
        .isTrue();
    assertThat(TrailWithholdingConvention.theOffenceOf(leafBased)).isEmpty();
  }

  @Test
  void every_module_of_this_build_is_compared_the_way_its_own_redactor_compares() {
    assertThat(theModuleOf("stripe").theRedactorsThatConsumeIt())
        .containsExactly("dev.marcinromanowski.pspsim.provider.stripe.StripeFields.withholding");
    assertThat(theModuleOf("stripe").itsRedactorMatchesAWholeKey()).isFalse();
    for (String provider : List.of("autra", "azericard", "grow", "worldline")) {
      assertThat(theModuleOf(provider).theRedactorsThatConsumeIt())
          .as("the redactors provider %s hands its declaration to", provider)
          .containsExactly(WHOLE_KEY);
      assertThat(theModuleOf(provider).itsRedactorMatchesAWholeKey()).isTrue();
      assertThat(theModuleOf(provider).itsRedactorsDisagree()).isFalse();
    }
    assertThat(theModuleOf("grow").withholds("url"))
        .as("grow redacts a flattened body, and data.url is the whole key url is published under")
        .isTrue();
    assertThat(theModuleOf("autra").withholds("hostedCardUrl")).isTrue();
    assertThat(TrailWithholdingDeclarations.endsInTheSegment("data.url", "url")).isTrue();
    assertThat(TrailWithholdingDeclarations.endsInTheSegment("data[url]", "url")).isFalse();
    assertThat(TrailWithholdingDeclarations.endsInTheSegment("url", "url")).isFalse();
    assertThat(TrailWithholdingDeclarations.endsInTheSegment("notaurl", "url")).isFalse();
  }

  @Test
  void a_registration_admits_one_name_for_one_module_and_never_for_its_siblings() {
    AModule registered = publishing("grow", List.of(), List.of("successUrl"), WHOLE_KEY);
    AModule sibling = publishing("stripe", List.of(), List.of("successUrl"), WHOLE_KEY);

    assertThat(TrailWithholdingConvention.registered("grow", "successUrl")).isTrue();
    assertThat(TrailWithholdingConvention.registered("stripe", "successUrl")).isFalse();
    assertThat(TrailWithholdingConvention.theOffenceOf(registered)).isEmpty();
    assertThat(TrailWithholdingConvention.theOffenceOf(sibling))
        .singleElement()
        .asString()
        .contains("provider stripe publishes the name successUrl");
  }

  @Test
  void a_registration_the_module_no_longer_publishes_admits_a_name_nothing_asks_for() {
    AModule module = aModule("grow", List.of("a.GrowFields"), List.of());

    assertThat(TrailWithholdingConvention.theRegistrationsNoModuleUses(List.of(module)))
        .as(
            "a build holding grow alone, publishing no address, leaves all 27 addresses and the"
                + " one page registered against nothing")
        .hasSize(28)
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .contains(
                        "successUrl is registered as an address provider grow may answer in the"
                            + " clear")
                    .contains("publishes no such name"));
    assertThat(
            TrailWithholdingConvention.theRegistrationsNoModuleUses(
                TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN).modules()))
        .isEmpty();
  }

  @Test
  void a_module_serving_a_payer_page_and_withholding_nothing_is_registered_or_refused() {
    AModule withholdingNothing = aModule("sixth", List.of("a.SixthFields"), List.of());
    AModule azericard = aModule("azericard", List.of("a.AzericardFields"), List.of());

    assertThat(
            TrailWithholdingConvention.theModulesThatServeAPageAndWithholdNothing(
                List.of(withholdingNothing), List.of("sixth")))
        .singleElement()
        .asString()
        .contains("provider sixth tells a tester this simulator serves the payer's page");
    assertThat(
            TrailWithholdingConvention.theModulesThatServeAPageAndWithholdNothing(
                List.of(azericard), List.of("azericard")))
        .as("azericard is registered, with the reason a merchant opens that page itself")
        .isEmpty();
    assertThat(
            TrailWithholdingConvention.theModulesThatServeAPageAndWithholdNothing(
                List.of(withholdingNothing), List.of()))
        .as("a module serving no payer page owes this rule nothing")
        .isEmpty();
  }

  @Test
  void the_registration_of_a_page_whose_address_nothing_answers_names_one_module_and_a_reason() {
    assertThat(TrailWithholdingConvention.THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS)
        .hasSize(1)
        .allSatisfy(registered -> assertThat(registered.why()).isNotBlank())
        .extracting(APageWithNoAddressToWithhold::provider)
        .containsExactly("azericard");
    assertThat(TrailWithholdingConvention.registeredAsAnsweringNoAddress("azericard")).isTrue();
    assertThat(TrailWithholdingConvention.registeredAsAnsweringNoAddress("worldline")).isFalse();
    assertThat(
            TrailWithholdingConvention.someModuleServesAPageAndWithholdsNothing(
                TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN).modules(), "azericard"))
        .isTrue();
  }

  @Test
  void a_registration_of_a_module_that_now_withholds_a_field_stands_for_no_decision() {
    AModule azericard = aModule("azericard", List.of("a.AzericardFields"), List.of("TOKEN"));

    assertThat(TrailWithholdingConvention.theRegistrationsNoModuleUses(List.of(azericard)))
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .contains("provider azericard is registered as serving a payer page")
                    .contains("stands for a decision nobody is making"));
  }

  @Test
  void a_name_reads_as_an_address_only_where_an_address_word_is_a_word_of_its_own() {
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("hostedCardUrl")).isTrue();
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("hosted_card_url")).isTrue();
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("data.url")).isTrue();
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("data[hostedLink]")).isTrue();
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("Location")).isTrue();
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("card.securityCode"))
        .as("uri inside securityCode is no word of its own")
        .isFalse();
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("curlyBrace"))
        .as("url inside curly is no word of its own")
        .isFalse();
    assertThat(TrailWithholdingDeclarations.anAddressIsNamed("client_secret")).isFalse();
  }

  @Test
  void only_a_constant_that_could_be_a_field_name_is_compared_against_the_floor() {
    assertThat(TrailWithholdingDeclarations.aNameAFieldCouldHave("hostedCardUrl")).isTrue();
    assertThat(TrailWithholdingDeclarations.aNameAFieldCouldHave("data[cardToken]")).isTrue();
    assertThat(TrailWithholdingDeclarations.aNameAFieldCouldHave("its link is the hostedCardUrl"))
        .as("a sentence naming a field is prose, not a name an answer is keyed under")
        .isFalse();
    assertThat(TrailWithholdingDeclarations.aNameAFieldCouldHave("https://example.test/far?l=1"))
        .isFalse();
    assertThat(TrailWithholdingDeclarations.aNameAFieldCouldHave("_____")).isFalse();
    assertThat(TrailWithholdingDeclarations.aNameAFieldCouldHave("")).isFalse();
  }

  @Test
  void a_name_is_read_down_to_the_leaf_a_provider_keys_its_answer_under() {
    assertThat(TrailWithholdingDeclarations.theLeafOf("data.transactions.transactionToken"))
        .isEqualTo("transactionToken");
    assertThat(TrailWithholdingDeclarations.theLeafOf("data[processToken]"))
        .isEqualTo("processToken");
    assertThat(TrailWithholdingDeclarations.theLeafOf("url")).isEqualTo("url");
    assertThat(TrailWithholdingDeclarations.theWordsOf("hosted_tokenization_url"))
        .containsExactly("hosted", "tokenization", "url");
    assertThat(TrailWithholdingDeclarations.theWordsOf("partialRedirectUrl"))
        .containsExactly("partial", "redirect", "url");
  }

  @Test
  void a_class_of_the_provider_package_itself_belongs_to_no_module() {
    assertThat(
            TrailWithholdingDeclarations.aClassOfAProviderModule(
                "dev.marcinromanowski.pspsim.provider.grow.GrowFields"))
        .isTrue();
    assertThat(
            TrailWithholdingDeclarations.aClassOfAProviderModule(
                "dev.marcinromanowski.pspsim.provider.package-info"))
        .as("the package's own synthetic class would otherwise read as a provider of its own")
        .isFalse();
    assertThat(
            TrailWithholdingDeclarations.aClassOfAProviderModule(
                "dev.marcinromanowski.pspsim.kernel.audit.AuditRecord"))
        .isFalse();
    assertThat(
            TrailWithholdingDeclarations.providerOf(
                "dev.marcinromanowski.pspsim.provider.stripe.StripeApi"))
        .isEqualTo("stripe");
  }

  @Test
  void the_offences_of_every_module_are_gathered_rather_than_the_first_module_reported_alone() {
    List<AModule> modules =
        List.of(
            aModule("sixth", List.of(), List.of()),
            new AModule(
                "seventh",
                List.of("a.SeventhFields"),
                List.of(),
                List.of("hostedPageUrl"),
                List.of("a.SeventhApi"),
                List.of("a.SeventhApi"),
                List.of(),
                List.of(WHOLE_KEY)));

    List<String> offences = TrailWithholdingConvention.theOffencesOf(modules);

    assertThat(offences).hasSize(3);
    assertThat(offences.get(0)).contains("provider sixth");
    assertThat(offences.get(1)).startsWith("a.SeventhApi");
    assertThat(offences.get(2)).contains("provider seventh publishes the name hostedPageUrl");
  }

  @Test
  void the_names_excused_where_the_compiler_emitted_them_are_counted_by_the_position_they_sit_at() {
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN);
    ThePositionsAConstantIsExcusedAt excused = build.excused();

    assertThat(excused.asAnEntryOfItsOwnDeclaration())
        .as(
            "the 34 response entries and the 13 delivered entries the declarations of this build"
                + " name, each excused in the initializer that stores it")
        .isEqualTo(47);
    assertThat(excused.asTheNameOfAnEnumConstant()).isEqualTo(206);
    assertThat(excused.asTheComponentNamesOfItsOwnRecord()).isEqualTo(591);
    assertThat(excused.occurrences())
        .as(
            "the denominator of this exclusion, against the %s occurrences read",
            build.constantsRead())
        .isEqualTo(844);
    assertThat(excused.theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway())
        .as(
            "what a class-wide exclusion dropped and this one reads: the names these classes name"
                + " as an enum constant or a record component of their own and publish somewhere"
                + " else as well")
        .hasSize(THE_NAMES_EXCUSED_SOMEWHERE_AND_PUBLISHED_ELSEWHERE)
        .contains(
            "dev.marcinromanowski.pspsim.provider.autra.AutraPixEvent FINANCIAL_TRANSACTION",
            "dev.marcinromanowski.pspsim.provider.azericard.AzericardResponse desc",
            "dev.marcinromanowski.pspsim.provider.stripe.StripeError code")
        .allSatisfy(
            named ->
                assertThat(
                        TrailWithholdingDeclarations.anAddressIsNamed(
                            named.substring(named.indexOf(' ') + 1)))
                    .as(
                        "%s is read against the floor now that it is no longer dropped class-wide,"
                            + " and no name this exclusion was hiding reads as an address",
                        named)
                    .isFalse());
  }

  @Test
  void every_row_this_build_builds_is_counted_where_it_is_built_and_not_once_for_its_class() {
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(COMPILED_MAIN);

    assertThat(build.rows().theMethodsThatBuildARow())
        .as("the denominator of the answering set: the methods that build a row at all")
        .isEqualTo(18);
    assertThat(build.rows().theMethodsThatBuildAnAnswerRow())
        .as(
            "the recordOutbound methods that build a row of their own, and the six shared"
                + " builders a method naming the direction reaches: three of them from a"
                + " recordOutbound, autra's card page from its own outbound, and tranzila's payment"
                + " page and hosted fields from their answers")
        .isEqualTo(12);
    assertThat(build.rows().theClassesThatBuildARowAndAnswerNoClient())
        .as("no class of this build builds a row that carries no answer to a client")
        .isEmpty();
    assertThat(TrailWithholdingConvention.howManyAnswerAClient(build.modules())).isEqualTo(12);
  }

  @Test
  void a_name_a_class_emits_for_a_thing_of_its_own_is_read_wherever_else_that_class_names_it(
      @TempDir Path fixture) throws IOException {
    Path classes = aProviderModuleCompiledFrom(fixture, theModuleThatPublishesWhatItEmits());
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(module.theAddressShapedNamesItPublishes())
        .as(
            "overriddenPageUrl is pushed into a SENSITIVE_RESPONSE_FIELDS field another class"
                + " of this module declares, which is no declaration of the class pushing it;"
                + " hostedPageUrl is this record's own component name and HOSTED_LINK its own enum"
                + " constant's name, and each is published a second time where the compiler"
                + " emitted neither; data.url is an entry of this class's own declaration and is"
                + " published again outside the initializer that builds it")
        .containsExactly("HOSTED_LINK", "data.url", "hostedPageUrl", "overriddenPageUrl");
    assertThat(module.theAddressShapedNamesItPublishes())
        .as(
            "redirectUri is named by nothing but the component-name list of its own record, and"
                + " HOSTED_URI by nothing but the construction of its own enum constant")
        .doesNotContain("redirectUri", "HOSTED_URI");
    assertThat(build.excused().asAnEntryOfItsOwnDeclaration()).isEqualTo(1);
    assertThat(build.excused().asTheNameOfAnEnumConstant()).isEqualTo(2);
    assertThat(build.excused().asTheComponentNamesOfItsOwnRecord()).isEqualTo(6);
    assertThat(build.excused().theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway())
        .containsExactly(
            A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthAnswer hostedPageUrl",
            A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthRouteNamingItself HOSTED_LINK");
    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .as("data.url is withheld by the declaration it is an entry of; the other three are not")
        .hasSize(3)
        .anySatisfy(offence -> assertThat(offence).contains("publishes the name HOSTED_LINK"))
        .anySatisfy(offence -> assertThat(offence).contains("publishes the name hostedPageUrl"))
        .anySatisfy(
            offence -> assertThat(offence).contains("publishes the name overriddenPageUrl"));
  }

  @Test
  void a_row_carries_the_direction_named_where_it_is_built_or_by_a_method_that_reaches_it(
      @TempDir Path fixture) throws IOException {
    Path classes = aProviderModuleCompiledFrom(fixture, theModuleThatBuildsRowsInFiveShapes());
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(module.theClassesThatAnswerAClient())
        .as(
            "SixthInboundOnly names OUTBOUND_RESPONSE in a method that reaches no row it builds,"
                + " and SixthRowsFromElsewhere is handed its direction by another class, so"
                + " neither answers a client; the two that do are the one naming the direction"
                + " where it builds the row and the one naming it in the method that calls the"
                + " builder")
        .containsExactly(
            A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthAnsweringInOneMethod",
            A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthAnsweringThroughAHelper");
    assertThat(module.theClassesThatAnswerAClientWithoutReadingItsOwnDeclaration())
        .containsExactly(A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthAnsweringInOneMethod");
    assertThat(module.theRowsItBuildsWhoseDirectionNothingNames())
        .as(
            "the row SixthRowsFromElsewhere builds is named by no method of its own class, and"
                + " the INBOUND_REQUEST it names elsewhere reaches that row from nowhere; the"
                + " second row SixthBuildingTwoRowsOfItsOwn builds is handed its direction the"
                + " same way, and the INBOUND_REQUEST its first row carries is no direction for"
                + " that one")
        .containsExactly(
            A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthBuildingTwoRowsOfItsOwn.theRowItIsHandedTheDirectionOf(AuditDirection,"
                + "String)AuditRecord",
            A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthRowsFromElsewhere.record(AuditDirection,String)AuditRecord");
    assertThat(build.rows().theMethodsThatBuildARow()).isEqualTo(6);
    assertThat(build.rows().theMethodsThatBuildAnAnswerRow()).isEqualTo(2);
    assertThat(build.rows().theClassesThatBuildARowAndAnswerNoClient())
        .as(
            "this line reads every row of such a class one by one: the two rows"
                + " SixthBuildingTwoRowsOfItsOwn builds carry different directions, which a union"
                + " over the class would print against both of them")
        .containsExactly(
            A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthBuildingTwoRowsOfItsOwn builds 2 row(s), none of them carrying"
                + " OUTBOUND_RESPONSE: ["
                + A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthBuildingTwoRowsOfItsOwn.theRequestItKeeps(String)AuditRecord carrying"
                + " [INBOUND_REQUEST], "
                + A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthBuildingTwoRowsOfItsOwn.theRowItIsHandedTheDirectionOf(AuditDirection,"
                + "String)AuditRecord carrying []]",
            A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthInboundOnly builds 1 row(s), none of them carrying OUTBOUND_RESPONSE: ["
                + A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthInboundOnly.theRowItKeeps(String)AuditRecord carrying [INBOUND_REQUEST]]",
            A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthRowsFromElsewhere builds 1 row(s), none of them carrying"
                + " OUTBOUND_RESPONSE: ["
                + A_PROVIDER_PACKAGE_OF_ITS_OWN
                + ".SixthRowsFromElsewhere.record(AuditDirection,String)AuditRecord carrying []]");
    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .hasSize(3)
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthAnsweringInOneMethod")
                    .contains("writes a row naming OUTBOUND_RESPONSE"))
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthRowsFromElsewhere.record")
                    .contains("no method of its own class that names a direction")
                    .contains("reaches it"))
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(
                        A_PROVIDER_PACKAGE_OF_ITS_OWN
                            + ".SixthBuildingTwoRowsOfItsOwn.theRowItIsHandedTheDirectionOf")
                    .contains("no method of its own class that names a direction"));
  }

  @Test
  void a_row_carries_the_direction_of_a_method_that_reaches_it_through_a_handle_or_two_hops(
      @TempDir Path fixture) throws IOException {
    Path classes =
        aProviderModuleCompiledFrom(fixture, theModuleThatReachesItsRowBuilderWithoutCallingIt());
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(module.theClassesThatAnswerAClient())
        .as(
            "SixthAnsweringThroughAMethodHandle names OUTBOUND_RESPONSE in the method that hands"
                + " its row builder over as a method reference and never calls it, so the only"
                + " edge from the name to the row is the handle in the bootstrap arguments;"
                + " SixthAnsweringTwoHopsAway names it two calls above the builder, so the only"
                + " edge is a walk through the caller of its caller")
        .containsExactly(
            A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthAnsweringThroughAMethodHandle",
            A_PROVIDER_PACKAGE_OF_ITS_OWN + ".SixthAnsweringTwoHopsAway");
    assertThat(module.theRowsItBuildsWhoseDirectionNothingNames())
        .as("neither row would be read as carrying a direction without those two edges")
        .isEmpty();
    assertThat(build.rows().theMethodsThatBuildARow()).isEqualTo(2);
    assertThat(build.rows().theMethodsThatBuildAnAnswerRow()).isEqualTo(2);
    assertThat(build.rows().theClassesThatBuildARowAndAnswerNoClient()).isEmpty();
    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .as("both classes read the declaration of their own module where they build the row")
        .isEmpty();
  }

  @Test
  void a_declaration_is_what_a_class_stores_into_a_field_of_its_own_and_never_into_anothers(
      @TempDir Path fixture) throws IOException {
    Path classes =
        aProviderModuleCompiledFrom(fixture, theModuleStoringIntoAnotherClassesFieldOfThatName());
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(module.theFieldsItWithholds())
        .as(
            "SixthFields stores data.nothing into its own declaration and hostedPageUrl into a"
                + " field of that name another class owns, which declares nothing for this module"
                + " and must not stand in for the declaration this one makes")
        .containsExactly("data.nothing");
    assertThat(build.excused().asAnEntryOfItsOwnDeclaration())
        .as("the entry of its own declaration is excused; the set it stores elsewhere is not")
        .isEqualTo(1);
    assertThat(module.theAddressShapedNamesItPublishes()).containsExactly("hostedPageUrl");
    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .as(
            "a module publishing a payer page address in the clear is refused here; read as this"
                + " module's declaration, that same store would have withheld it and reported"
                + " nothing")
        .singleElement()
        .asString()
        .contains("publishes the name hostedPageUrl");
  }

  @Test
  void a_class_storing_into_its_own_declaration_twice_is_refused_rather_than_read_at_one_store(
      @TempDir Path fixture) throws IOException {
    Path classes =
        aProviderModuleCompiledFrom(fixture, theModuleStoringIntoItsOwnDeclarationTwice());

    assertThatThrownBy(() -> TrailWithholdingDeclarations.inTheBuild(classes))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stores into its own SENSITIVE_RESPONSE_FIELDS twice")
        .hasMessageContaining("in its <clinit> building [data.nothing]")
        .hasMessageContaining("in its <clinit> building [hostedPageUrl]")
        .hasMessageContaining("refused rather than guessed at");
  }

  @Test
  void a_declaration_widened_by_a_method_of_its_own_class_is_refused_the_same_way(
      @TempDir Path fixture) throws IOException {
    Path classes =
        aProviderModuleCompiledFrom(fixture, theModuleWideningItsOwnDeclarationAtRunTime());

    assertThatCode(() -> TrailWithholdingDeclarations.inTheBuild(classes))
        .as(
            "the store this gate would read is the one its initializer makes, and the set the"
                + " module holds after widen() ran is another, so neither can be read as what the"
                + " module withholds")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stores into its own SENSITIVE_RESPONSE_FIELDS twice")
        .hasMessageContaining("in its <clinit> building []")
        .hasMessageContaining("in its widen building [hostedPageUrl]");
  }

  @Test
  void a_component_name_list_is_excused_only_where_a_record_of_that_class_is_bootstrapped(
      @TempDir Path fixture) throws IOException {
    Path classes = aProviderModuleCompiledFrom(fixture, theModuleWhoseDeclarationIsAllItCompiles());
    aClassBootstrappingComponentNames(
        classes, "SixthNamingWhatItIsNot", "SixthNamingWhatItIsNot", "hostedPageUrl", false);
    aClassBootstrappingComponentNames(
        classes, "SixthNamingAnothersComponents", "SixthElsewhere", "anotherPageUrl", true);
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(build.excused().asTheComponentNamesOfItsOwnRecord())
        .as(
            "SixthNamingWhatItIsNot bootstraps a component-name list and carries no record of its"
                + " own, and SixthNamingAnothersComponents is a record bootstrapping the names of"
                + " another class's, so neither list is the one the compiler emits for a record of"
                + " its own and neither is excused")
        .isZero();
    assertThat(module.theAddressShapedNamesItPublishes())
        .as("both names are read against the floor like any other string those classes name")
        .containsExactly("anotherPageUrl", "hostedPageUrl");
    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .hasSize(2)
        .anySatisfy(offence -> assertThat(offence).contains("publishes the name hostedPageUrl"))
        .anySatisfy(offence -> assertThat(offence).contains("publishes the name anotherPageUrl"));
  }

  @Test
  void the_entries_of_a_declaration_are_the_run_it_is_built_from_and_not_a_string_pushed_before_it(
      @TempDir Path fixture) throws IOException {
    Path classes =
        aProviderModuleCompiledFrom(fixture, theModulePushingANameBeforeTheStoreOfItsDeclaration());
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(module.theFieldsItWithholds())
        .as(
            "the store into this declaration is built from one call over one constant, and"
                + " hostedPageUrl was consumed by the put before it: read as an entry it would"
                + " report this module withholding a field no redactor of it can match")
        .containsExactly("data.nothing");
    assertThat(build.excused().asAnEntryOfItsOwnDeclaration()).isEqualTo(1);
    assertThat(module.theAddressShapedNamesItPublishes())
        .as("so the name the initializer pushed for the put stands against the floor")
        .containsExactly("hostedPageUrl");
    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .singleElement()
        .asString()
        .contains("provider sixth publishes the name hostedPageUrl")
        .contains("which reads as an address");
  }

  @Test
  void a_declaration_of_eleven_fields_is_read_whole_though_the_compiler_builds_it_in_an_array(
      @TempDir Path fixture) throws IOException {
    Path classes =
        aProviderModuleCompiledFrom(fixture, theModuleDeclaringMoreFieldsThanOneCallTakes());
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(module.theFieldsItWithholds())
        .as(
            "eleven entries are more than java.util.Set.of takes one by one, so the compiler"
                + " builds an array of them: a run that ended at the first array store would read"
                + " this declaration as its last entry alone")
        .hasSize(11)
        .contains("data.one", "data.hostedPageUrl");
    assertThat(build.excused().asAnEntryOfItsOwnDeclaration()).isEqualTo(11);
    assertThat(module.theAddressShapedNamesItPublishes())
        .as("the address among them is declared withheld and is no name this module publishes")
        .isEmpty();
    assertThat(TrailWithholdingConvention.theOffenceOf(module)).isEmpty();
  }

  @Test
  void a_name_is_excused_as_an_enum_constants_own_only_where_its_own_construction_begins_with_it(
      @TempDir Path fixture) throws IOException {
    Path classes = aProviderModuleCompiledFrom(fixture, theModuleWhoseDeclarationIsAllItCompiles());
    aClassStoringEnumConstantsItConstructsElsewhere(classes);
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    AModule module = theModuleOf(build, "sixth");

    assertThat(build.excused().asTheNameOfAnEnumConstant())
        .as(
            "HOSTED_URL is stored into an enum constant of its own that this initializer"
                + " constructs nowhere, and A_HOSTED_LINK into one whose construction begins with"
                + " another string, so neither name sits where the compiler emits a constant's"
                + " name and neither is excused")
        .isZero();
    assertThat(module.theAddressShapedNamesItPublishes())
        .as("both are read against the floor like any other string that class names")
        .containsExactly("A_HOSTED_LINK", "HOSTED_URL");
    assertThat(build.excused().theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway())
        .containsExactly(
            A_PROVIDER_PACKAGE_OF_ITS_OWN + "." + AN_ENUM_IT_NEVER_CONSTRUCTS + " HOSTED_URL",
            A_PROVIDER_PACKAGE_OF_ITS_OWN + "." + AN_ENUM_IT_NEVER_CONSTRUCTS + " A_HOSTED_LINK");
    assertThat(TrailWithholdingConvention.theOffenceOf(module))
        .hasSize(2)
        .anySatisfy(offence -> assertThat(offence).contains("publishes the name HOSTED_URL"))
        .anySatisfy(offence -> assertThat(offence).contains("publishes the name A_HOSTED_LINK"));
  }

  private static AModule theModuleOf(TheBuild build, String provider) {
    return build.modules().stream()
        .filter(module -> module.provider().equals(provider))
        .findFirst()
        .orElseThrow();
  }

  private static final List<ADeliveringModule> THE_MODULES_REGISTRATIONS_ARE_WRITTEN_AGAINST =
      List.of(
          minting("autra", List.of("a.AutraFields"), List.of()),
          minting("azericard", List.of("a.AzericardFields"), List.of()));

  private static final List<String> THE_MODULES_THAT_MINT_A_DELIVERY =
      List.of("autra", "azericard", "grow", "stripe", "tranzila");

  private static ADeliveringModule delivering(
      String provider,
      List<String> declaredIn,
      List<String> withheld,
      List<String> minting,
      List<String> mintingWithout,
      List<String> carryingForward) {
    return deliveringAndCompared(
        provider,
        declaredIn,
        withheld,
        minting,
        mintingWithout,
        carryingForward,
        List.of(WHOLE_KEY));
  }

  private static ADeliveringModule deliveringAndCompared(
      String provider,
      List<String> declaredIn,
      List<String> withheld,
      List<String> minting,
      List<String> mintingWithout,
      List<String> carryingForward,
      List<String> redactors) {
    return new ADeliveringModule(
        provider,
        declaredIn,
        withheld,
        List.of("userDocument", "TOKEN", "P_SIGN"),
        minting,
        mintingWithout,
        carryingForward,
        redactors);
  }

  private static ADeliveringModule minting(
      String provider, List<String> declaredIn, List<String> withheld) {
    return delivering(
        provider, declaredIn, withheld, List.of("a.SixthCallback"), List.of(), List.of());
  }

  private static List<ADeliveringModule> theModulesWithAzericardDeclaring(
      List<String> withheld, List<String> redactors) {
    return List.of(
        minting("autra", List.of("a.AutraFields"), List.of()),
        deliveringAndCompared(
            "azericard",
            List.of("a.AzericardFields"),
            withheld,
            List.of("a.AzericardCallback"),
            List.of(),
            List.of(),
            redactors));
  }

  private static TheDeliveries sweeping(
      List<ADeliveringModule> modules, List<String> rowsKept, List<String> rowsUnwithheld) {
    return new TheDeliveries(
        modules,
        rowsKept.isEmpty() ? List.of() : List.of(A_CLASS_THAT_KEEPS_A_ROW),
        rowsKept,
        rowsUnwithheld,
        TrailWithholdingDeclarations.THE_DIRECTIONS_A_DELIVERY_ROW_NAMES,
        1);
  }

  private static ADeliveringModule theDeliveryOf(String provider) {
    return TrailWithholdingDeclarations.theDeliveriesIn(COMPILED_MAIN).modules().stream()
        .filter(module -> module.provider().equals(provider))
        .findFirst()
        .orElseThrow();
  }

  private static Map<String, String> theModuleThatPublishesWhatItEmits() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "SixthFields.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.Map;
        import java.util.Set;

        public final class SixthFields {

          public static Set<String> SENSITIVE_RESPONSE_FIELDS = Set.of("data.url");

          private SixthFields() {}

          public static Map<String, String> theColumnItStoresThatUnder(String value) {
            return Map.of("data.url", value);
          }
        }
        """);
    sources.put(
        "SixthAnswer.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.Map;

        public record SixthAnswer(String hostedPageUrl) {

          public Map<String, String> asABody() {
            return Map.of("hostedPageUrl", hostedPageUrl);
          }
        }
        """);
    sources.put(
        "SixthOverridingTheDeclaration.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.Set;

        public final class SixthOverridingTheDeclaration {

          static {
            SixthFields.SENSITIVE_RESPONSE_FIELDS = Set.of("overriddenPageUrl");
          }

          private SixthOverridingTheDeclaration() {}
        }
        """);
    sources.put(
        "SixthReceipt.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        public record SixthReceipt(String redirectUri) {}
        """);
    sources.put(
        "SixthRouteNamingItself.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        public enum SixthRouteNamingItself {
          HOSTED_LINK("HOSTED_LINK");

          private final String subject;

          SixthRouteNamingItself(String subject) {
            this.subject = subject;
          }

          public String subject() {
            return subject;
          }
        }
        """);
    sources.put(
        "SixthRouteNamingItsPath.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        public enum SixthRouteNamingItsPath {
          HOSTED_URI("/hosted/uri");

          private final String path;

          SixthRouteNamingItsPath(String path) {
            this.path = path;
          }

          public String path() {
            return path;
          }
        }
        """);
    return sources;
  }

  private static Map<String, String> theModuleThatBuildsRowsInFiveShapes() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("AuditDirection.java", THE_DIRECTIONS_A_ROW_NAMES);
    sources.put("AuditRecord.java", THE_ROW_A_TRAIL_KEEPS);
    sources.put("SixthFields.java", THE_DECLARATION_OF_A_MODULE_OF_ITS_OWN);
    sources.put(
        "SixthAnsweringInOneMethod.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;

        public final class SixthAnsweringInOneMethod {

          public AuditRecord theAnswerItKeeps(String body) {
            return new AuditRecord(AuditDirection.OUTBOUND_RESPONSE, body);
          }
        }
        """);
    sources.put(
        "SixthAnsweringThroughAHelper.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;

        public final class SixthAnsweringThroughAHelper {

          public AuditRecord outbound(String body) {
            return record(AuditDirection.OUTBOUND_RESPONSE, body);
          }

          private AuditRecord record(AuditDirection direction, String body) {
            return new AuditRecord(direction, SixthFields.SENSITIVE_RESPONSE_FIELDS + body);
          }
        }
        """);
    sources.put(
        "SixthInboundOnly.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;

        public final class SixthInboundOnly {

          public AuditRecord theRowItKeeps(String body) {
            return new AuditRecord(AuditDirection.INBOUND_REQUEST, body);
          }

          public AuditDirection whatAnAnswerIsCalled() {
            return AuditDirection.OUTBOUND_RESPONSE;
          }
        }
        """);
    sources.put(
        "SixthRowsFromElsewhere.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;

        public final class SixthRowsFromElsewhere {

          public AuditRecord record(AuditDirection direction, String body) {
            return new AuditRecord(direction, body);
          }

          public AuditDirection whatARequestIsCalled() {
            return AuditDirection.INBOUND_REQUEST;
          }
        }
        """);
    sources.put(
        "SixthBuildingTwoRowsOfItsOwn.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;

        public final class SixthBuildingTwoRowsOfItsOwn {

          public AuditRecord theRequestItKeeps(String body) {
            return new AuditRecord(AuditDirection.INBOUND_REQUEST, body);
          }

          public AuditRecord theRowItIsHandedTheDirectionOf(AuditDirection direction, String body) {
            return new AuditRecord(direction, body);
          }
        }
        """);
    return sources;
  }

  private static Map<String, String> theModuleThatReachesItsRowBuilderWithoutCallingIt() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("AuditDirection.java", THE_DIRECTIONS_A_ROW_NAMES);
    sources.put("AuditRecord.java", THE_ROW_A_TRAIL_KEEPS);
    sources.put("SixthFields.java", THE_DECLARATION_OF_A_MODULE_OF_ITS_OWN);
    sources.put(
        "SixthAnsweringThroughAMethodHandle.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
        import java.util.function.BiFunction;

        public final class SixthAnsweringThroughAMethodHandle {

          public AuditRecord outbound(String body) {
            return theRowItDefers(this::record).apply(AuditDirection.OUTBOUND_RESPONSE, body);
          }

          private AuditRecord record(AuditDirection direction, String body) {
            return new AuditRecord(direction, SixthFields.SENSITIVE_RESPONSE_FIELDS + body);
          }

          private static BiFunction<AuditDirection, String, AuditRecord> theRowItDefers(
              BiFunction<AuditDirection, String, AuditRecord> deferred) {
            return deferred;
          }
        }
        """);
    sources.put(
        "SixthAnsweringTwoHopsAway.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;

        public final class SixthAnsweringTwoHopsAway {

          public AuditRecord outbound(String body) {
            return theRowItKeeps(AuditDirection.OUTBOUND_RESPONSE, body);
          }

          private AuditRecord theRowItKeeps(AuditDirection direction, String body) {
            return record(direction, body);
          }

          private AuditRecord record(AuditDirection direction, String body) {
            return new AuditRecord(direction, SixthFields.SENSITIVE_RESPONSE_FIELDS + body);
          }
        }
        """);
    return sources;
  }

  private static Map<String, String> theModuleStoringIntoAnotherClassesFieldOfThatName() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "Elsewhere.java",
        """
        package dev.marcinromanowski.pspsim.elsewhere;

        import java.util.Set;

        public final class Elsewhere {

          public static Set<String> SENSITIVE_RESPONSE_FIELDS = Set.of();

          private Elsewhere() {}
        }
        """);
    sources.put(
        "SixthFields.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import dev.marcinromanowski.pspsim.elsewhere.Elsewhere;
        import java.util.Set;

        public final class SixthFields {

          public static final Set<String> SENSITIVE_RESPONSE_FIELDS = Set.of("data.nothing");

          static {
            Elsewhere.SENSITIVE_RESPONSE_FIELDS = Set.of("hostedPageUrl");
          }

          private SixthFields() {}
        }
        """);
    sources.put(
        "SixthAnswerBody.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.Map;

        public final class SixthAnswerBody {

          private SixthAnswerBody() {}

          public static Map<String, String> theBodyItAnswers(String address) {
            return Map.of("hostedPageUrl", address);
          }
        }
        """);
    return sources;
  }

  private static Map<String, String> theModuleStoringIntoItsOwnDeclarationTwice() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "SixthFields.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.Set;

        public final class SixthFields {

          public static Set<String> SENSITIVE_RESPONSE_FIELDS = Set.of("data.nothing");

          static {
            SENSITIVE_RESPONSE_FIELDS = Set.of("hostedPageUrl");
          }

          private SixthFields() {}
        }
        """);
    return sources;
  }

  private static Map<String, String> theModuleWideningItsOwnDeclarationAtRunTime() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "SixthFields.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.Set;

        public final class SixthFields {

          public static Set<String> SENSITIVE_RESPONSE_FIELDS = Set.of();

          private SixthFields() {}

          public static void widen() {
            SENSITIVE_RESPONSE_FIELDS = Set.of("hostedPageUrl");
          }
        }
        """);
    return sources;
  }

  private static Map<String, String> theModuleWhoseDeclarationIsAllItCompiles() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("SixthFields.java", THE_DECLARATION_OF_A_MODULE_OF_ITS_OWN);
    return sources;
  }

  private static Map<String, String> theModulePushingANameBeforeTheStoreOfItsDeclaration() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "SixthFields.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.HashMap;
        import java.util.Map;
        import java.util.Set;

        public final class SixthFields {

          public static final Map<String, String> THE_PAGES_IT_SERVES = new HashMap<>();

          public static final Set<String> SENSITIVE_RESPONSE_FIELDS;

          static {
            THE_PAGES_IT_SERVES.put("hostedPageUrl", "/sixth/hosted/page");
            SENSITIVE_RESPONSE_FIELDS = Set.of("data.nothing");
          }

          private SixthFields() {}
        }
        """);
    return sources;
  }

  private static Map<String, String> theModuleDeclaringMoreFieldsThanOneCallTakes() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put(
        "SixthFields.java",
        """
        package dev.marcinromanowski.pspsim.provider.sixth;

        import java.util.Set;

        public final class SixthFields {

          public static final Set<String> SENSITIVE_RESPONSE_FIELDS =
              Set.of(
                  "data.one",
                  "data.two",
                  "data.three",
                  "data.four",
                  "data.five",
                  "data.six",
                  "data.seven",
                  "data.eight",
                  "data.nine",
                  "data.ten",
                  "data.hostedPageUrl");

          private SixthFields() {}
        }
        """);
    return sources;
  }

  private static final String AN_ENUM_IT_NEVER_CONSTRUCTS = "SixthRoutesItStoresFromElsewhere";

  private static final String THE_PAGE_THAT_NAME_KEYS = "/sixth/hosted/page";

  private static final String THE_LINK_THAT_NAME_KEYS = "/sixth/hosted/link";

  private static void aClassStoringEnumConstantsItConstructsElsewhere(Path classes)
      throws IOException {
    ClassDesc own = ClassDesc.of(A_PROVIDER_PACKAGE_OF_ITS_OWN + "." + AN_ENUM_IT_NEVER_CONSTRUCTS);
    int ofAnEnumConstant =
        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL | ClassFile.ACC_ENUM;
    byte[] forged =
        ClassFile.of()
            .build(
                own,
                builder -> {
                  builder.withField("HOSTED_URL", own, ofAnEnumConstant);
                  builder.withField("A_HOSTED_LINK", own, ofAnEnumConstant);
                  builder.withMethodBody(
                      TrailWithholdingDeclarations.STATIC_INITIALIZER,
                      MethodTypeDesc.of(ConstantDescs.CD_void),
                      ClassFile.ACC_STATIC,
                      code ->
                          code.ldc("HOSTED_URL")
                              .ldc(THE_PAGE_THAT_NAME_KEYS)
                              .invokestatic(
                                  ClassDesc.of("java.util.Map"),
                                  "of",
                                  MethodTypeDesc.ofDescriptor(
                                      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;"),
                                  true)
                              .pop()
                              .aconst_null()
                              .putstatic(own, "HOSTED_URL", own)
                              .new_(own)
                              .dup()
                              .ldc(THE_LINK_THAT_NAME_KEYS)
                              .ldc("A_HOSTED_LINK")
                              .invokespecial(
                                  own,
                                  TrailWithholdingDeclarations.INITIALIZER,
                                  MethodTypeDesc.ofDescriptor(
                                      "(Ljava/lang/String;Ljava/lang/String;)V"))
                              .putstatic(own, "A_HOSTED_LINK", own)
                              .return_());
                });
    Path into =
        Files.createDirectories(classes.resolve(A_PROVIDER_PACKAGE_OF_ITS_OWN.replace('.', '/')));
    Files.write(into.resolve(AN_ENUM_IT_NEVER_CONSTRUCTS + ".class"), forged);
  }

  private static void aClassBootstrappingComponentNames(
      Path classes, String named, String whoseComponents, String names, boolean carryingARecord)
      throws IOException {
    byte[] forged =
        ClassFile.of()
            .build(
                ClassDesc.of(A_PROVIDER_PACKAGE_OF_ITS_OWN + "." + named),
                builder -> {
                  if (carryingARecord) {
                    builder.with(
                        RecordAttribute.of(
                            List.of(RecordComponentInfo.of("nothing", ConstantDescs.CD_String))));
                  }
                  builder.withMethodBody(
                      "theNamesItBootstraps",
                      MethodTypeDesc.of(ConstantDescs.CD_Object),
                      ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                      code ->
                          code.invokedynamic(
                                  DynamicCallSiteDesc.of(
                                      MethodHandleDesc.ofMethod(
                                          DirectMethodHandleDesc.Kind.STATIC,
                                          ClassDesc.of(
                                              TrailWithholdingDeclarations
                                                  .THE_NAMES_OF_A_RECORD_ARE_BOOTSTRAPPED_BY),
                                          TrailWithholdingDeclarations
                                              .HOW_THOSE_NAMES_ARE_BOOTSTRAPPED,
                                          THE_SHAPE_OF_THAT_BOOTSTRAP),
                                      "toString",
                                      MethodTypeDesc.of(ConstantDescs.CD_Object),
                                      ClassDesc.of(
                                          A_PROVIDER_PACKAGE_OF_ITS_OWN + "." + whoseComponents),
                                      names))
                              .areturn());
                });
    Path into =
        Files.createDirectories(classes.resolve(A_PROVIDER_PACKAGE_OF_ITS_OWN.replace('.', '/')));
    Files.write(into.resolve(named + ".class"), forged);
  }

  private static final String A_KERNEL_CLASS_THAT_KEEPS_DELIVERY_ROWS =
      "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveryRows";

  private static final String THE_DIRECTIONS_A_DELIVERY_ROW_NAMES_IN_A_FIXTURE =
      """
      package dev.marcinromanowski.pspsim.kernel.audit;

      public enum AuditDirection {
        INBOUND_REQUEST,
        OUTBOUND_RESPONSE,
        OUTBOUND_WEBHOOK_ATTEMPT,
        OUTBOUND_WEBHOOK_DROPPED,
        OUTBOUND_WEBHOOK_NOT_SENT
      }
      """;

  private static final String THE_BODY_A_DELIVERY_CARRIES_IN_A_FIXTURE =
      """
      package dev.marcinromanowski.pspsim.kernel.webhook;

      import java.util.Map;

      public record WebhookPayload(Map<String, String> wireBody, Map<String, String> withheld) {

        public Map<String, String> sanitizedBody() {
          return withheld;
        }
      }
      """;

  private static Map<String, String> theKernelThatKeepsDeliveryRowsInThreeShapes() {
    Map<String, String> sources = new LinkedHashMap<>();
    sources.put("AuditDirection.java", THE_DIRECTIONS_A_DELIVERY_ROW_NAMES_IN_A_FIXTURE);
    sources.put("AuditRecord.java", THE_ROW_A_TRAIL_KEEPS_IN_A_FIXTURE);
    sources.put("WebhookPayload.java", THE_BODY_A_DELIVERY_CARRIES_IN_A_FIXTURE);
    sources.put(
        "WebhookDeliveryRows.java",
        """
        package dev.marcinromanowski.pspsim.kernel.webhook;

        import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
        import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
        import java.util.Map;

        public final class WebhookDeliveryRows {

          public AuditRecord theRowOfTheBodyItWithheld(WebhookPayload sending) {
            return new AuditRecord(
                AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT, sending.sanitizedBody());
          }

          public AuditRecord theRowOfTheWireBytes(WebhookPayload sending) {
            return new AuditRecord(AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT, sending.wireBody());
          }

          public AuditRecord theDroppedRow(WebhookPayload withheld) {
            return theRowItIsHandedAWithheldBodyFor(withheld.sanitizedBody());
          }

          private AuditRecord theRowItIsHandedAWithheldBodyFor(Map<String, String> body) {
            return new AuditRecord(AuditDirection.OUTBOUND_WEBHOOK_DROPPED, body);
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
            "these cases compile a provider module of their own, because the live tree holds none"
                + " of the shapes they state, so this test needs the compiler of the JDK it runs"
                + " on")
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

  @Test
  void the_compiled_build_mints_five_deliveries_and_every_one_of_them_declares_what_it_withholds() {
    TheDeliveries deliveries = TrailWithholdingDeclarations.theDeliveriesIn(COMPILED_MAIN);

    assertThat(TrailWithholdingConvention.theModulesThatMintADelivery(deliveries.modules()))
        .as(
            "the first denominator of the delivery direction: worldline mints none, and a sweep"
                + " that matched no minting module would pass forever")
        .containsExactlyElementsOf(THE_MODULES_THAT_MINT_A_DELIVERY);
    assertThat(deliveries.classesRead())
        .as("the class files this sweep read, kernel and provider alike")
        .isGreaterThan(1000);
    assertThat(TrailWithholdingConvention.theClassesThatMintADelivery(deliveries.modules()))
        .containsExactly(
            "dev.marcinromanowski.pspsim.provider.autra.AutraPixWebhook",
            "dev.marcinromanowski.pspsim.provider.azericard.AzericardCallback",
            "dev.marcinromanowski.pspsim.provider.grow.GrowCallback",
            "dev.marcinromanowski.pspsim.provider.stripe.StripeWebhookRendering",
            "dev.marcinromanowski.pspsim.provider.stripe.StripeWebhooks",
            "dev.marcinromanowski.pspsim.provider.tranzila.TranzilaNotify");
    assertThat(
            TrailWithholdingConvention.theClassesThatCarryForwardABodyAlreadyWithheld(
                deliveries.modules()))
        .as("the one class that mints a delivery out of a body another class already withheld")
        .containsExactly("dev.marcinromanowski.pspsim.provider.stripe.StripeWebhookRendering");
    assertThat(TrailWithholdingConvention.theClassesThatDeclareTheDelivery(deliveries.modules()))
        .containsExactly(
            "dev.marcinromanowski.pspsim.provider.autra.AutraFields",
            "dev.marcinromanowski.pspsim.provider.azericard.AzericardFields",
            "dev.marcinromanowski.pspsim.provider.grow.GrowFields",
            "dev.marcinromanowski.pspsim.provider.stripe.StripeFields",
            "dev.marcinromanowski.pspsim.provider.tranzila.TranzilaFields");
    assertThat(TrailWithholdingConvention.howManyDeliveredFieldsAreWithheld(deliveries.modules()))
        .as("the second denominator: the fields these declarations name between them")
        .isEqualTo(13);
    assertThat(deliveries.theClassesThatKeepADeliveryRow())
        .containsExactly("dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher");
    assertThat(deliveries.theMethodsThatKeepADeliveryRow())
        .as(
            "the three writers of that one class, each read for the withheld body on its own,"
                + " because one method of a class reading it admits no other")
        .containsExactlyInAnyOrder(
            "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher"
                + ".record(WebhookDelivery,WebhookAttempt,WebhookPayload)void",
            "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher.recordTheDeliveryTheProviderWouldNotSend"
                + "(WebhookDelivery,Instant,String)void",
            "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher.recordTheDeliveryTheRuleWithheld"
                + "(WebhookDelivery,Instant,String)void");
    assertThat(deliveries.theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld()).isEmpty();
    assertThat(TrailWithholdingConvention.theOffencesOfTheDeliveries(deliveries))
        .as("the build under test keeps the rule this part of the gate enforces")
        .isEmpty();
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(deliveries.modules()))
        .isEmpty();
    assertThat(TrailWithholdingConvention.theDirectionsNoRowCanName(deliveries)).isEmpty();
  }

  @Test
  void the_fields_each_module_withholds_from_a_delivery_are_read_out_of_its_own_declaration() {
    assertThat(theDeliveryOf("grow").theFieldsItWithholds())
        .containsExactlyInAnyOrder(
            "data[customFields][cField1]",
            "data[processToken]",
            "data[transactionToken]",
            "data[cardToken]");
    assertThat(theDeliveryOf("azericard").theFieldsItWithholds())
        .as(
            "registered as a body whose every field this build keeps readable, TOKEN and P_SIGN"
                + " recorded as the two it keeps readable on purpose")
        .isEmpty();
    assertThat(theDeliveryOf("azericard").theNamesTheClassesThatMintADeliveryName())
        .as(
            "the names read back against a recording are the ones its minting class holds, so a"
                + " constant only AzericardFields declares is no evidence of a delivery")
        .contains("TOKEN", "P_SIGN", "CARD")
        .doesNotContain("CVC2", "M_INFO", "MERCH_URL");
    assertThat(theDeliveryOf("stripe").theFieldsItWithholds())
        .containsExactly("data.object.client_secret");
    assertThat(theDeliveryOf("autra").theFieldsItWithholds())
        .as("registered as a body that holds nothing this build withholds")
        .isEmpty();
    assertThat(theDeliveryOf("tranzila").theFieldsItWithholds())
        .containsExactlyInAnyOrder(
            "TranzilaTK",
            "card_number",
            "cvv",
            "card_holder_id",
            "myid",
            "credit_card_number",
            "card_holder_id_number",
            "identity_number");
    assertThat(theDeliveryOf("worldline").theClassesThatMintADelivery())
        .as("worldline mints no delivery at all, so it owes this rule nothing")
        .isEmpty();
    assertThat(theDeliveryOf("worldline").theClassesThatDeclareIt()).isEmpty();
  }

  @Test
  void every_module_is_compared_in_its_deliveries_the_way_its_own_delivery_redactor_compares() {
    assertThat(theDeliveryOf("stripe").theRedactorsThatConsumeIt())
        .as(
            "the one module whose two directions are compared differently: its response"
                + " declaration is handed to its own withholding methods and its delivery"
                + " declaration to the whole-key redactor, so a read of the response declaration"
                + " alone printed the wrong matching for every delivered name of it")
        .containsExactly(WHOLE_KEY);
    assertThat(theDeliveryOf("stripe").itsRedactorMatchesAWholeKey()).isTrue();
    assertThat(theModuleOf("stripe").itsRedactorMatchesAWholeKey()).isFalse();
    for (String provider : List.of("autra", "azericard", "grow", "tranzila")) {
      assertThat(theDeliveryOf(provider).theRedactorsThatConsumeIt())
          .as("the redactors provider %s hands its delivery declaration to", provider)
          .containsExactly(WHOLE_KEY);
      assertThat(theDeliveryOf(provider).itsRedactorsDisagree()).isFalse();
    }
    assertThat(theDeliveryOf("worldline").theRedactorsThatConsumeIt())
        .as("worldline declares no delivery and hands none to anything")
        .isEmpty();
    assertThat(
            TrailWithholdingConvention.theMatchingOfTheDeliveriesOf(
                TrailWithholdingDeclarations.theDeliveriesIn(COMPILED_MAIN).modules()))
        .as("the line names each module that declares one, and no module that declares none")
        .hasSize(5)
        .allSatisfy(matching -> assertThat(matching).contains("matches a whole key"));
  }

  @Test
  void a_name_recorded_as_delivered_in_the_clear_is_read_back_the_way_that_delivery_is_compared() {
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                theModulesWithAzericardDeclaring(List.of("data.TOKEN"), List.of(WHOLE_KEY))))
        .as(
            "the whole-key redactor matches a declared path ending in the segment recorded, so a"
                + " module declaring data.TOKEN withholds the TOKEN it records in the clear")
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(
                        "TOKEN is recorded as a field provider azericard delivers in the clear")
                    .contains("withholds it after all"));
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                theModulesWithAzericardDeclaring(List.of("data[TOKEN]"), List.of(A_LEAF))))
        .as(
            "a module whose delivery redactor reads the leaf of every key is met by that leaf, so"
                + " the same recording is read back against data[TOKEN] as well")
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(
                        "TOKEN is recorded as a field provider azericard delivers in the clear")
                    .contains("withholds it after all"));
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                theModulesWithAzericardDeclaring(List.of("data[TOKEN]"), List.of(WHOLE_KEY))))
        .as(
            "and data[TOKEN] is a key of its own to the whole-key redactor, which is the same"
                + " reading the response direction makes of a bracketed key")
        .noneSatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(
                        "TOKEN is recorded as a field provider azericard delivers in the clear")
                    .contains("withholds it after all"));
  }

  @Test
  void a_delivery_declaration_handed_to_two_redactors_that_match_differently_is_read_by_neither() {
    ADeliveringModule disagreeing =
        deliveringAndCompared(
            "sixth",
            List.of("a.SixthFields"),
            List.of("TOKEN"),
            List.of("a.SixthCallback"),
            List.of(),
            List.of(),
            List.of(A_LEAF, WHOLE_KEY));

    assertThat(disagreeing.itsRedactorsDisagree()).isTrue();
    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(disagreeing))
        .singleElement()
        .asString()
        .contains("hands its SENSITIVE_DELIVERY_FIELDS to")
        .contains("do not match a key the same way");
  }

  @Test
  void a_module_minting_a_delivery_and_declaring_nothing_names_no_field_its_rows_withhold() {
    ADeliveringModule module = minting("sixth", List.of(), List.of());

    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(module))
        .hasSize(2)
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .contains("provider sixth mints the body of a delivery in [a.SixthCallback]")
                    .contains("declares no SENSITIVE_DELIVERY_FIELDS in any class"))
        .anySatisfy(offence -> assertThat(offence).contains("withholds no field at all"));
  }

  @Test
  void a_module_declaring_what_it_delivers_in_two_classes_leaves_a_class_withholding_less() {
    ADeliveringModule module =
        minting("sixth", List.of("a.SixthFields", "a.SixthEvent"), List.of("TOKEN"));

    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(module))
        .singleElement()
        .asString()
        .contains("declares SENSITIVE_DELIVERY_FIELDS in [a.SixthFields, a.SixthEvent]")
        .contains("spread over more than one class");
  }

  @Test
  void a_class_minting_a_delivery_around_its_own_declaration_is_named_with_that_class() {
    ADeliveringModule module =
        delivering(
            "sixth",
            List.of("a.SixthFields"),
            List.of("TOKEN"),
            List.of("a.SixthCallback", "a.SixthEvents"),
            List.of("a.SixthEvents"),
            List.of());

    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(module))
        .singleElement()
        .asString()
        .startsWith(
            "a.SixthEvents mints a dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload")
        .contains("reads no SENSITIVE_DELIVERY_FIELDS of provider sixth")
        .contains("the read, not the reach");
  }

  @Test
  void a_class_minting_a_delivery_out_of_a_body_already_withheld_owes_no_declaration_of_its_own() {
    ADeliveringModule carryingForward =
        delivering(
            "sixth",
            List.of("a.SixthFields"),
            List.of("TOKEN"),
            List.of("a.SixthRendering"),
            List.of(),
            List.of("a.SixthRendering"));

    ADeliveringModule carryingForwardAndWithholdingNothing =
        delivering(
            "sixth",
            List.of("a.SixthFields"),
            List.of(),
            List.of("a.SixthRendering"),
            List.of(),
            List.of("a.SixthRendering"));

    assertThat(carryingForward.mintsADelivery()).isTrue();
    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(carryingForward)).isEmpty();
    assertThat(carryingForwardAndWithholdingNothing.mintsADelivery()).isTrue();
    assertThat(carryingForwardAndWithholdingNothing.composesADelivery())
        .as("every minting class of it carries a body another class already withheld")
        .isFalse();
    assertThat(
            TrailWithholdingConvention.theOffenceOfTheDeliveryOf(
                carryingForwardAndWithholdingNothing))
        .as(
            "a module that composes no body of its own owes no field and no registration, and"
                + " requiring one would make it register a decision about fields it composes"
                + " nothing of")
        .isEmpty();
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                List.of(
                    delivering(
                        "autra",
                        List.of("a.AutraFields"),
                        List.of(),
                        List.of("a.AutraRendering"),
                        List.of(),
                        List.of("a.AutraRendering")))))
        .as("so the registration of a module that now only carries a body forward stands alone")
        .anySatisfy(
            offence -> assertThat(offence).contains("composes the body of no delivery at all"));
  }

  @Test
  void a_module_withholding_nothing_from_a_body_it_mints_is_registered_or_refused() {
    ADeliveringModule withholdingNothing = minting("sixth", List.of("a.SixthFields"), List.of());
    ADeliveringModule autra = minting("autra", List.of("a.AutraFields"), List.of());

    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(withholdingNothing))
        .singleElement()
        .asString()
        .contains("provider sixth composes the body of a delivery in [a.SixthCallback]")
        .contains("withholds no field at all")
        .contains("the registration is the decision");
    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(autra))
        .as("autra is registered, with the reason every field of that body is readable here")
        .isEmpty();
    assertThat(TrailWithholdingConvention.registeredAsDeliveringNothingToWithhold("autra"))
        .isTrue();
    assertThat(TrailWithholdingConvention.registeredAsDeliveringNothingToWithhold("grow"))
        .isFalse();
  }

  @Test
  void a_declaration_of_a_module_that_mints_no_delivery_withholds_nothing_from_any_row() {
    ADeliveringModule declaringWithoutDelivering =
        delivering(
            "sixth", List.of("a.SixthFields"), List.of("TOKEN"), List.of(), List.of(), List.of());

    assertThat(declaringWithoutDelivering.mintsADelivery()).isFalse();
    assertThat(TrailWithholdingConvention.theOffenceOfTheDeliveryOf(declaringWithoutDelivering))
        .singleElement()
        .asString()
        .contains("declares SENSITIVE_DELIVERY_FIELDS in [a.SixthFields] and mints no")
        .contains("withheld from no row");
    assertThat(
            TrailWithholdingConvention.theOffenceOfTheDeliveryOf(
                delivering("sixth", List.of(), List.of(), List.of(), List.of(), List.of())))
        .as("a module that neither declares nor delivers owes this rule nothing")
        .isEmpty();
  }

  @Test
  void a_registration_of_a_module_that_now_withholds_a_delivered_field_stands_for_no_decision() {
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                List.of(minting("autra", List.of("a.AutraFields"), List.of("userDocument")))))
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .contains(
                        "provider autra is registered as delivering a body that holds nothing to"
                            + " withhold")
                    .contains("stands for a decision nobody is making"));
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                List.of(
                    delivering(
                        "autra",
                        List.of("a.AutraFields"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()))))
        .as("a module that stopped minting a delivery leaves that registration standing alone")
        .anySatisfy(
            offence -> assertThat(offence).contains("composes the body of no delivery at all"));
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                THE_MODULES_REGISTRATIONS_ARE_WRITTEN_AGAINST))
        .as(
            "the registered case: both modules every registration is written against, each"
                + " delivering and withholding what its registration says")
        .isEmpty();
  }

  @Test
  void a_field_recorded_as_delivered_in_the_clear_is_published_by_that_module_and_withheld_by_it() {
    ADeliveringModule withholdingItAfterAll =
        new ADeliveringModule(
            "azericard",
            List.of("a.AzericardFields"),
            List.of("P_SIGN"),
            List.of("userDocument", "P_SIGN"),
            List.of("a.AzericardCallback"),
            List.of(),
            List.of(),
            List.of(WHOLE_KEY));
    ADeliveringModule namingItInNoMintingClass =
        new ADeliveringModule(
            "azericard",
            List.of("a.AzericardFields"),
            List.of(),
            List.of("userDocument"),
            List.of("a.AzericardCallback"),
            List.of(),
            List.of(),
            List.of(WHOLE_KEY));

    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                List.of(withholdingItAfterAll)))
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(
                        "P_SIGN is recorded as a field provider azericard delivers in the clear")
                    .contains("withholds it after all"));
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                List.of(namingItInNoMintingClass)))
        .as(
            "the class that mints this module's delivery names the constant nowhere, so the"
                + " recording is read back as outlived however many other classes of the module"
                + " declare it")
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(
                        "P_SIGN is recorded as a field provider azericard delivers in the clear")
                    .contains("no class of it that mints a delivery names that constant"));
    assertThat(
            TrailWithholdingConvention.theDeliveryRegistrationsNoModuleUses(
                THE_MODULES_REGISTRATIONS_ARE_WRITTEN_AGAINST))
        .as(
            "the recorded case: azericard's callback names TOKEN and P_SIGN and withholds"
                + " neither")
        .isEmpty();
    assertThat(TrailWithholdingConvention.THE_FIELDS_A_MODULE_DELIVERS_IN_THE_CLEAR)
        .hasSize(3)
        .allSatisfy(recorded -> assertThat(recorded.why()).isNotBlank())
        .extracting(AFieldDeliveredInTheClear::named)
        .containsExactly("userDocument", "TOKEN", "P_SIGN");
    assertThat(TrailWithholdingConvention.THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD)
        .hasSize(2)
        .allSatisfy(registered -> assertThat(registered.why()).isNotBlank())
        .extracting(ADeliveryWithNothingToWithhold::provider)
        .containsExactly("autra", "azericard");
  }

  @Test
  void a_delivery_row_is_read_for_the_withheld_body_at_the_method_that_writes_it(
      @TempDir Path fixture) throws IOException {
    Path classes =
        aProviderModuleCompiledFrom(fixture, theKernelThatKeepsDeliveryRowsInThreeShapes());

    TheDeliveries deliveries = TrailWithholdingDeclarations.theDeliveriesIn(classes);

    assertThat(deliveries.theClassesThatKeepADeliveryRow())
        .as("one class writes all three rows, which is why a reading per class proves nothing")
        .containsExactly(A_KERNEL_CLASS_THAT_KEEPS_DELIVERY_ROWS);
    assertThat(deliveries.theMethodsThatKeepADeliveryRow())
        .containsExactlyInAnyOrder(
            A_KERNEL_CLASS_THAT_KEEPS_DELIVERY_ROWS
                + ".theRowOfTheBodyItWithheld(WebhookPayload)"
                + "AuditRecord",
            A_KERNEL_CLASS_THAT_KEEPS_DELIVERY_ROWS
                + ".theRowItIsHandedAWithheldBodyFor(Map)"
                + "AuditRecord",
            A_KERNEL_CLASS_THAT_KEEPS_DELIVERY_ROWS
                + ".theRowOfTheWireBytes(WebhookPayload)"
                + "AuditRecord");
    assertThat(deliveries.theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld())
        .as(
            "the first reads the withheld body where it writes the row, the second is reached by"
                + " the method that reads it, and the third takes the bytes on the wire: a"
                + " reading per class would admit the third on the first two")
        .containsExactly(
            A_KERNEL_CLASS_THAT_KEEPS_DELIVERY_ROWS
                + ".theRowOfTheWireBytes(WebhookPayload)"
                + "AuditRecord");
    assertThat(
            TrailWithholdingConvention.howManyKeepADeliveryRowFromABodyAModuleWithheld(deliveries))
        .as("the printed count is the rows read less the rows written from no withheld body")
        .isEqualTo(2);
    assertThat(TrailWithholdingConvention.theOffencesOfTheDeliveries(deliveries))
        .singleElement()
        .asString()
        .startsWith(A_KERNEL_CLASS_THAT_KEEPS_DELIVERY_ROWS + ".theRowOfTheWireBytes")
        .contains("in itself or in any method of its own class that reaches it");
  }

  @Test
  void a_delivery_row_written_from_a_body_no_module_withheld_decorates_every_declaration() {
    TheDeliveries writingTheWireBytes =
        sweeping(List.of(), List.of(A_ROW_IS_WRITTEN_IN), List.of(A_ROW_IS_WRITTEN_IN));

    assertThat(TrailWithholdingConvention.theOffencesOfTheDeliveries(writingTheWireBytes))
        .singleElement()
        .asString()
        .startsWith(A_ROW_IS_WRITTEN_IN + " keeps a trail row naming one of")
        .contains(
            "never reads dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload.sanitizedBody")
        .contains("decorates nothing");
    assertThat(
            TrailWithholdingConvention.theOffencesOfTheDeliveries(
                sweeping(List.of(), List.of(A_ROW_IS_WRITTEN_IN), List.of())))
        .as("a kernel that writes the row from the withheld body breaks nothing")
        .isEmpty();
  }

  @Test
  void a_row_reported_as_written_from_no_withheld_body_is_one_of_the_rows_this_sweep_read() {
    assertThatCode(
            () ->
                new TheDeliveries(
                    List.of(),
                    List.of(A_CLASS_THAT_KEEPS_A_ROW),
                    List.of(),
                    List.of(A_ROW_IS_WRITTEN_IN),
                    TrailWithholdingDeclarations.THE_DIRECTIONS_A_DELIVERY_ROW_NAMES,
                    1))
        .as("the printed count is the rows read less these, so a row absent from them lies")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            TrailWithholdingDeclarations
                .A_ROW_WRITTEN_FROM_NO_WITHHELD_BODY_IS_ONE_OF_THE_ROWS_READ)
        .hasMessageContaining(A_ROW_IS_WRITTEN_IN);
  }

  @Test
  void a_direction_this_rule_reads_that_no_row_can_name_would_match_no_row_at_all() {
    TheDeliveries renamed =
        new TheDeliveries(
            List.of(),
            List.of(A_CLASS_THAT_KEEPS_A_ROW),
            List.of(A_ROW_IS_WRITTEN_IN),
            List.of(),
            List.of("INBOUND_REQUEST", "OUTBOUND_RESPONSE", "OUTBOUND_WEBHOOK_ATTEMPT"),
            1);

    assertThat(TrailWithholdingConvention.theDirectionsNoRowCanName(renamed))
        .hasSize(2)
        .anySatisfy(
            offence ->
                assertThat(offence)
                    .startsWith(
                        "this rule reads a delivery row by the direction OUTBOUND_WEBHOOK_DROPPED")
                    .contains("names no such direction"));
  }

  @Test
  void a_build_whose_classes_keep_no_delivery_row_is_a_broken_classpath_rather_than_a_clean_tree() {
    TheDeliveries readNothing = sweeping(List.of(), List.of(), List.of());

    assertThatThrownBy(
            () ->
                TrailWithholdingConvention.refuseASweepThatKeptNoDeliveryRow(
                    readNothing, COMPILED_MAIN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("keeps a trail row naming any of")
        .hasMessageContaining("1 class file(s) were read");
    assertThatCode(
            () ->
                TrailWithholdingConvention.refuseASweepThatKeptNoDeliveryRow(
                    sweeping(List.of(), List.of(A_ROW_IS_WRITTEN_IN), List.of()), COMPILED_MAIN))
        .doesNotThrowAnyException();
  }
}
