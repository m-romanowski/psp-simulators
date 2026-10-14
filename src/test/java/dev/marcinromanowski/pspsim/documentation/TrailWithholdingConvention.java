package dev.marcinromanowski.pspsim.documentation;

import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.ADeliveringModule;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.AModule;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.TheBuild;
import dev.marcinromanowski.pspsim.documentation.TrailWithholdingDeclarations.TheDeliveries;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class TrailWithholdingConvention {

  public static final String THE_RULE =
      "Every provider module of this build declares, in one class of its own package, the"
          + " response fields a row of the audit trail keeps a redacted form of instead of the"
          + " value the client read, under the name "
          + TrailWithholdingDeclarations.THE_DECLARATION
          + ", and every class of that module which writes a row naming "
          + TrailWithholdingDeclarations.AN_ANSWER_TO_A_CLIENT
          + " reads its own module's declaration, and no class of it keeps a trail row without"
          + " naming the direction that row carries. The floor every declaration stands on is"
          + " the address of the payer page this simulator serves: a name a provider publishes"
          + " that reads as an address is either withheld or registered here against the provider"
          + " that publishes it, with the reason that address is no secret of that provider, so"
          + " widening one module never widens the others and a reader can see in one place what"
          + " each module answers in the clear. A provider that tells a tester this simulator"
          + " serves the payer's page and withholds nothing is registered the same way, because"
          + " an empty declaration beside a page this simulator hosts is the one case no address"
          + " can report.";

  public static final String THE_DELIVERY_RULE =
      "The body this simulator delivers to a tester's own endpoint is kept as a row of the same"
          + " trail, under one of "
          + TrailWithholdingDeclarations.THE_DIRECTIONS_A_DELIVERY_ROW_NAMES
          + ", so every provider module that mints one declares, in one class of its own package,"
          + " the fields that row keeps a redacted form of, under the name "
          + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
          + ", and every class of that module which composes one reads its own module's"
          + " declaration, while a class minting a delivery out of a body another class already"
          + " withheld composes no field of its own and is held to no declaration. What such a"
          + " declaration withholds is either a field of its own or a registered decision that"
          + " this body holds nothing to withhold, because an empty declaration beside a body a"
          + " module composes is the one case no field can report. The floor here is structural"
          + " and names no word: a module that composes a delivery applies its own declaration,"
          + " and what it withholds is non-empty or recorded.";

  public static final String WHY_A_DELIVERY_IS_FOUND_BY_THE_BODY_IT_MINTS =
      "the row of a delivery is kept by the kernel and never by the provider, so no class of a"
          + " provider module names a delivery direction at all and this rule cannot find a"
          + " minting module the way the response rule finds an answering class. What a module"
          + " does own is the body it hands over: it mints a "
          + TrailWithholdingDeclarations.THE_BODY_A_DELIVERY_CARRIES
          + ", whose withheld form is the body every one of those three rows is written from, so"
          + " minting one is the fact this rule reads. A class that mints a delivery out of a body"
          + " another class already withheld, by reading it back through "
          + TrailWithholdingDeclarations.HOW_A_WITHHELD_BODY_IS_READ_BACK
          + ", composes no field of its own and owes this rule no declaration";

  public static final String HOW_AN_EMPTY_DELIVERY_IS_ADMITTED =
      "a module whose delivered body holds nothing to withhold is admitted by registering it in"
          + " TrailWithholdingConvention.THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD with"
          + " the reason every field of that body is one this build keeps readable. Deciding that"
          + " a delivered body needs no withholding is the thing this gate is for, so the"
          + " registration is the decision and this gate only reports that nobody made it. Such a"
          + " module's application of its own declaration is carried by this gate alone: an empty"
          + " declaration redacts nothing, so no row it writes differs from the row the same"
          + " class would write applying nothing at all, and no unit test of a row can tell the"
          + " two apart";

  public static final String HOW_A_FIELD_IS_DELIVERED_IN_THE_CLEAR =
      "a field a module delivers readable on purpose is recorded in"
          + " TrailWithholdingConvention.THE_FIELDS_A_MODULE_DELIVERS_IN_THE_CLEAR against the"
          + " module that delivers it. Nothing is required to be recorded there and this gate"
          + " invents no floor over a name's words, so a recording is a claim rather than a"
          + " permission: it is read back against the module, and it is an offence once no class"
          + " of that module which mints a delivery names that constant, or the module withholds"
          + " it after all, so a reason cannot outlive the decision it was written for. The key a"
          + " body is finally published under may be composed at run time and reaches this gate"
          + " as no constant at all, so what is read back is the name a minting class of that"
          + " module holds, and the row a unit test reads is what states the key";

  public static final String WHY_AN_ADDRESS_IS_THE_FLOOR =
      "the address of a payer page this simulator serves is published under a name whose own"
          + " words say it is an address, and for some of the providers that serve such a"
          + " page it carries the one-time token a payer completes a payment with. A floor over"
          + " every name that reads like a token or a secret would be a guess: those words name"
          + " account settings, environment variables, persona identifiers and database columns"
          + " across these modules far more often than they name a field an answer carries";

  public static final String HOW_AN_ADDRESS_IS_ADMITTED =
      "a name that reads as an address and is not withheld is admitted by registering it in"
          + " TrailWithholdingConvention.THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR against"
          + " the provider that publishes it, with the reason it is no secret of that provider."
          + " Deciding that a published address needs no withholding is the thing this gate is"
          + " for, so the registration is the decision and this gate only reports that nobody"
          + " made it";

  public record AnAddressInTheClear(String provider, String named, String why) {}

  public record ADeliveryWithNothingToWithhold(String provider, String why) {}

  public record AFieldDeliveredInTheClear(String provider, String named, String why) {}

  public static final List<ADeliveryWithNothingToWithhold>
      THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD =
          List.of(
              new ADeliveryWithNothingToWithhold(
                  "autra",
                  "the Pix event provider autra publishes carries no field this build withholds in"
                      + " any direction: an event id and type, the moment it occurred, the Pix id"
                      + " and order id, the operation's subject and product type, the slug and"
                      + " muid of a financial transaction, the payoutId a payout keys that same"
                      + " slug under, a status, an amount, a currency and a settlement date, and"
                      + " userDocument,"
                      + " which is recorded below as delivered in the clear. The response"
                      + " direction of this module withholds a transactional token, two spellings"
                      + " of an access token and the address of the hosted card page, and this"
                      + " body carries none of the four"),
              new ADeliveryWithNothingToWithhold(
                  "azericard",
                  "the callback azericard posts to a merchant's own endpoint carries the amount,"
                      + " the currency, the ORDER, the ACTION and RC the gateway answered, the"
                      + " approval code, the RRN, the INT_REF, the payer's EMAIL, the TERMINAL,"
                      + " the TRTYPE, the TIMESTAMP, the NONCE, the EXT_NET_REF, a CARD carrying"
                      + " the masked form this gateway prints rather than the number its request"
                      + " declaration withholds, and TOKEN and P_SIGN, both recorded below as"
                      + " delivered in the clear. The request direction of this module withholds"
                      + " the cardholder information and the card data a merchant sends, and this"
                      + " body carries neither"));

  public static final List<AFieldDeliveredInTheClear> THE_FIELDS_A_MODULE_DELIVERS_IN_THE_CLEAR =
      List.of(
          new AFieldDeliveredInTheClear(
              "autra",
              "userDocument",
              "the half of the routing pair (tenantId, userDocument) this contract holds,"
                  + " which provider autra prints in its own published Pix event examples."
                  + " This build withholds documentId in no direction: the response"
                  + " declaration of this module names a transactional token, two access"
                  + " tokens and a hosted page address and never the document. Treating a"
                  + " payer's CPF or CNPJ as a secret is a product decision for the operator,"
                  + " and it would be taken in every direction of this module at once rather"
                  + " than in the delivery alone"),
          new AFieldDeliveredInTheClear(
              "azericard",
              "TOKEN",
              "the 28-character handle this simulator mints for the card a registration stored,"
                  + " which is that stored card's own ProviderObjectIdentity, is the cursor its"
                  + " object is placed under, and is the one field"
                  + " AzericardObjectActions.A_STORED_CARD_IS_NOT_A_TRANSACTION tells a tester to"
                  + " pay with. Withholding it from this row removes nothing and costs a tester"
                  + " the one row they could read it from: the request declaration of this module"
                  + " names the card data and never the TOKEN, so the token-route call a tester"
                  + " makes with it keeps it in full one row later, and"
                  + " AzericardProvider.relatedObjectsOf hands it to the object screen, which"
                  + " redacts nothing. Treating it as a secret in every direction, as grow treats"
                  + " its own card token, would take from a tester the handle this product offers"
                  + " for addressing a stored card by its id"),
          new AFieldDeliveredInTheClear(
              "azericard",
              "P_SIGN",
              "the signature this gateway makes over the callback with the gateway's own"
                  + " private key, which a merchant verifies with the gateway's public key, so"
                  + " a reader of the trail computes nothing from it that the holder of that"
                  + " public key cannot already compute. This module also keeps the merchant's"
                  + " own P_SIGN readable on the way in, because its request declaration names"
                  + " the card data and the cardholder information and never the MAC, and the"
                  + " work a tester does against this contract is comparing the MAC it sent"
                  + " with the MAC it was answered: a trail that redacted one side of that"
                  + " comparison would withhold the one field this gateway is tested for"));

  public record APageWithNoAddressToWithhold(String provider, String why) {}

  public static final List<AnAddressInTheClear> THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR =
      List.of(
          new AnAddressInTheClear(
              "autra",
              "AUTRA_BASE_URL",
              "the name of the pairing setting a tester copies into their own application, whose"
                  + " value is this simulator's base address; it names no object and reaches a"
                  + " trail row under no field"),
          new AnAddressInTheClear(
              "azericard",
              "AZERICARD_GATEWAY_URL",
              "the name of the pairing setting a tester copies into their own application, whose"
                  + " value is this simulator's base address; it names no object and reaches a"
                  + " trail row under no field"),
          new AnAddressInTheClear(
              "azericard",
              "MERCH_URL",
              "a field the merchant sends in its own request, naming the merchant's own site;"
                  + " azericard answers it back in nothing, and this build mints no part of it"),
          new AnAddressInTheClear(
              "grow",
              "GROW_BASE_URL",
              "the name of the pairing setting a tester copies into their own application, whose"
                  + " value is this simulator's base address; it names no object and reaches a"
                  + " trail row under no field"),
          new AnAddressInTheClear(
              "grow",
              "Location",
              "the header grow's hosted page answers a payer's browser with, carrying the"
                  + " successUrl or cancelUrl the merchant supplied on createPaymentProcess; the"
                  + " rows that page writes carry no headers at all"),
          new AnAddressInTheClear(
              "grow",
              "cancelUrl",
              "a field the merchant sends on createPaymentProcess, naming the merchant's own"
                  + " site; grow answers it back in nothing"),
          new AnAddressInTheClear(
              "grow",
              "cancel_url",
              "the column that merchant-supplied address is stored under in this build's own"
                  + " database, which a trail row is never written from"),
          new AnAddressInTheClear(
              "grow",
              "hosted_link",
              "the column the one-time link to grow's payer page is stored under in this build's"
                  + " own database; what an answer carries it under is data.url, which this"
                  + " module withholds"),
          new AnAddressInTheClear(
              "grow",
              "notifyUrl",
              "a field the merchant sends on createPaymentProcess, naming the merchant's own"
                  + " callback target; grow answers it back in nothing"),
          new AnAddressInTheClear(
              "grow",
              "notify_url",
              "the column that merchant-supplied address is stored under in this build's own"
                  + " database, which a trail row is never written from"),
          new AnAddressInTheClear(
              "grow",
              "successUrl",
              "a field the merchant sends on createPaymentProcess, naming the merchant's own"
                  + " site; grow answers it back in nothing"),
          new AnAddressInTheClear(
              "grow",
              "success_url",
              "the column that merchant-supplied address is stored under in this build's own"
                  + " database, which a trail row is never written from"),
          new AnAddressInTheClear(
              "stripe",
              "doc_url",
              "the address of stripe's own public documentation for an error code, which every"
                  + " stripe error object carries and which is the same for every account and"
                  + " every test run"),
          new AnAddressInTheClear(
              "stripe",
              "url",
              "the field a stripe list object names its own route in, /v1/payment_methods: the"
                  + " address of the call the client just made, which the same trail row already"
                  + " keeps in its path"),
          new AnAddressInTheClear(
              "tranzila",
              "TRANZILA_API_BASE_URL",
              "the name of the pairing setting a tester copies into their own application, whose"
                  + " value is this simulator's base address; it names no object and reaches a"
                  + " trail row under no field"),
          new AnAddressInTheClear(
              "tranzila",
              "TRANZILA_API_V2_BASE_URL",
              "the name of the pairing setting a tester copies into their own application, whose"
                  + " value is this simulator's address of the handshake version; it names no"
                  + " object and reaches a trail row under no field"),
          new AnAddressInTheClear(
              "tranzila",
              "TRANZILA_DIRECTNG_BASE_URL",
              "the name of the pairing setting a tester copies into their own application, whose"
                  + " value is this simulator's address of the payment page host; it names no"
                  + " object and reaches a trail row under no field"),
          new AnAddressInTheClear(
              "tranzila",
              "fail_url",
              "the column the merchant-supplied fail_url_address is stored under in this build's"
                  + " own database, which a trail row is never written from"),
          new AnAddressInTheClear(
              "tranzila",
              "fail_url_address",
              "a parameter the merchant sends to the payment page, naming the merchant's own site;"
                  + " the page posts the payer's browser there and answers it back in no field"),
          new AnAddressInTheClear(
              "tranzila",
              "notify_url",
              "the column the merchant-supplied notify_url_address is stored under in this build's"
                  + " own database, which a trail row is never written from"),
          new AnAddressInTheClear(
              "tranzila",
              "notify_url_address",
              "a parameter the merchant sends to the payment page, naming the merchant's own"
                  + " callback target; tranzila posts the notify there and answers it back in no"
                  + " field"),
          new AnAddressInTheClear(
              "tranzila",
              "success_url",
              "the column the merchant-supplied success_url_address is stored under in this"
                  + " build's own database, which a trail row is never written from"),
          new AnAddressInTheClear(
              "tranzila",
              "success_url_address",
              "a parameter the merchant sends to the payment page, naming the merchant's own site;"
                  + " the page posts the payer's browser there and answers it back in no field"),
          new AnAddressInTheClear(
              "worldline",
              "Location",
              "the header worldline answers a created payment's own resource address in, built"
                  + " from the merchant id and the payment id the same trail row keeps as the"
                  + " object it names"),
          new AnAddressInTheClear(
              "worldline",
              "WORLDLINE_BASE_URL",
              "the name of the pairing setting a tester copies into their own application, whose"
                  + " value is this simulator's base address; it names no object and reaches a"
                  + " trail row under no field"));

  public static final List<APageWithNoAddressToWithhold> THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS =
      List.of(
          new APageWithNoAddressToWithhold(
              "azericard",
              "a payer reaches azericard's page because the merchant posts their browser to the"
                  + " gateway address it configured, so this module answers no address for that"
                  + " page in any response: what a merchant may not forge is the P_SIGN it"
                  + " computes over the fields, not a secret inside a link"));

  private TrailWithholdingConvention() {}

  public record Verdict(List<String> offences, List<String> read) {

    public Verdict {
      offences = List.copyOf(offences);
      read = List.copyOf(read);
    }
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "this gate takes the directory of compiled main classes and was given " + List.of(args));
    }
    Path classes = Path.of(args[0]);
    Verdict verdict = verdictOn(classes);
    verdict
        .read()
        .forEach(line -> System.out.println("verifyEveryProviderWithholdsWhatItAnswers: " + line));
    if (verdict.offences().isEmpty()) {
      return;
    }
    verdict.offences().forEach(offence -> System.out.println("  - " + offence));
    throw new IllegalStateException(
        THE_RULE
            + " "
            + verdict.offences().size()
            + " place(s) break it, each named above. A module is found by the package it compiles"
            + " into, its declaration by the field name every module shares, and the names it"
            + " publishes by the string constants of its own compiled classes, so a sixth"
            + " provider is held to this rule by compiling rather than by an edit here. Three"
            + " positions are excused, each at the occurrence it sits at and nowhere else in that"
            + " class: "
            + TrailWithholdingDeclarations.AS_AN_ENTRY_OF_ITS_OWN_DECLARATION
            + ", where that run is "
            + TrailWithholdingDeclarations.WHAT_A_RUN_A_STORED_VALUE_IS_BUILT_FROM_HOLDS
            + "; "
            + TrailWithholdingDeclarations.AS_THE_NAME_OF_AN_ENUM_CONSTANT
            + ", read of the construction that store is made from and of the position the name"
            + " sits at in it, so a store into an enum constant its own initializer constructs"
            + " nowhere, and one whose construction begins with another string, excuse nothing;"
            + " and "
            + TrailWithholdingDeclarations.AS_THE_COMPONENT_NAMES_OF_ITS_OWN_RECORD
            + ", which the compiler emits three times for one record, once for each of the"
            + " equals, the hashCode and the toString it bootstraps through "
            + TrailWithholdingDeclarations.THE_NAMES_OF_A_RECORD_ARE_BOOTSTRAPPED_BY
            + ". Wherever else that class names one of them, it is read against the floor. A"
            + " declaration is read from what a class stores into a field of its own: a string"
            + " pushed into a "
            + TrailWithholdingDeclarations.THE_DECLARATION
            + " field another class owns declares nothing here and is read as a name the storing"
            + " class publishes, and a class storing into its own declaration twice is refused"
            + " above rather than read at one of the two stores. A final field is what makes that"
            + " second store impossible, and every declaration of this build is declared final,"
            + " so the refusal stands against a module that would declare it otherwise. The"
            + " direction a row carries is read "
            + TrailWithholdingDeclarations.WHERE_THE_DIRECTION_OF_A_ROW_IS_READ
            + ", so a class that builds an inbound row in one method and names an answer to a"
            + " client in an unrelated one is examined for no answer, and a row that no method"
            + " of its own class naming a direction reaches is refused above rather than passed"
            + " over. The body a delivery row is written from is read the same way, "
            + TrailWithholdingDeclarations.WHERE_A_WITHHELD_BODY_IS_READ_FOR_A_ROW
            + ", so a fourth writer in a class whose other three read it is refused above rather"
            + " than admitted on theirs. "
            + theShapesThisRuleMissesOnPurpose().size()
            + " shapes are missed on purpose. "
            + String.join(" ", theShapesThisRuleMissesOnPurpose())
            + " "
            + THE_DELIVERY_RULE);
  }

  private static List<String> theShapesThisRuleMissesOnPurpose() {
    return List.of(
        "A name a module composes at run time reaches this gate as no constant at all, and so"
            + " does the key a name is finally published under, so a declaration that names a"
            + " key its own body no longer carries is caught by the unit test that reads the"
            + " row, never here.",
        "A declaration a module reads through another module's class is counted as unread.",
        "A string the one call that builds the value of a declaration consumes for anything other"
            + " than an entry of it is read as an entry of that declaration, because telling one"
            + " argument of that call from another needs the operand stack this gate does not"
            + " simulate.",
        "Where the construction of an enum constant of its own begins with a string equal to that"
            + " constant's name for a purpose of its own, that one occurrence is excused and every"
            + " other one read, because telling the name the compiler emits there from an argument"
            + " that equals it needs the same operand stack.",
        "A row's headers are outside this rule altogether: a declaration is applied to the body"
            + " a module hands the trail.",
        "A class that mints a delivery out of a body it read back already withheld is held to no"
            + " declaration of its own, and whether the body it finally hands over is that one"
            + " is a fact about that site which a unit test of the row states.");
  }

  public static Verdict verdictOn(Path classes) {
    TheBuild build = TrailWithholdingDeclarations.inTheBuild(classes);
    List<AModule> modules = build.modules();
    if (modules.isEmpty()) {
      throw new IllegalStateException(
          "no class under "
              + classes
              + " compiles into "
              + TrailWithholdingDeclarations.PROVIDER_PACKAGE
              + ", so this gate would examine no provider and report that every provider it"
              + " examined withholds what it must. Every provider this simulator serves compiles into"
              + " that package, so a run that reads no module there has a broken classpath, not a"
              + " clean tree. "
              + build.classesRead()
              + " provider class file(s) were read.");
    }
    TheDeliveries deliveries = TrailWithholdingDeclarations.theDeliveriesIn(classes);
    refuseASweepThatKeptNoDeliveryRow(deliveries, classes);
    List<String> serving = PayerPageAddresses.inTheBuild(classes).providersThatDeclareAPage();
    List<String> offences = new ArrayList<>(theOffencesOf(modules));
    offences.addAll(theModulesThatServeAPageAndWithholdNothing(modules, serving));
    offences.addAll(theRegistrationsNoModuleUses(modules));
    offences.addAll(theDirectionsNoRowCanName(deliveries));
    offences.addAll(theOffencesOfTheDeliveries(deliveries));
    offences.addAll(theDeliveryRegistrationsNoModuleUses(deliveries.modules()));

    List<String> read = new ArrayList<>();
    read.add(
        "read "
            + build.classesRead()
            + " compiled provider classes carrying "
            + build.constantsRead()
            + " string constant occurrence(s), and examined the "
            + modules.size()
            + " provider module(s) they compile into: "
            + build.providers());
    read.add(
        "excused "
            + build.excused().occurrences()
            + " of those occurrence(s) at the position the compiler emitted them, never across a"
            + " class: "
            + build.excused().asAnEntryOfItsOwnDeclaration()
            + " read as "
            + TrailWithholdingDeclarations.AS_AN_ENTRY_OF_ITS_OWN_DECLARATION
            + ", "
            + build.excused().asTheNameOfAnEnumConstant()
            + " as "
            + TrailWithholdingDeclarations.AS_THE_NAME_OF_AN_ENUM_CONSTANT
            + ", "
            + build.excused().asTheComponentNamesOfItsOwnRecord()
            + " as "
            + TrailWithholdingDeclarations.AS_THE_COMPONENT_NAMES_OF_ITS_OWN_RECORD
            + "; every other occurrence of those names is read, which leaves "
            + build
                .excused()
                .theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway()
                .size()
            + " name(s) a class names as an enum constant or a record component of its own and"
            + " publishes somewhere else as well: "
            + build.excused().theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway());
    read.add(
        "read "
            + howManyFieldsAreWithheld(modules)
            + " withheld response field(s) across those modules, declared as "
            + TrailWithholdingDeclarations.THE_DECLARATION
            + " by "
            + theClassesThatDeclareIt(modules));
    read.add(
        "examined "
            + howManyAddressShapedNames(modules)
            + " published name(s) that read as an address, against the words "
            + TrailWithholdingDeclarations.THE_WORDS_AN_ADDRESS_IS_NAMED_WITH
            + ": "
            + theAddressShapedNames(modules));
    read.add(
        THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR.size()
            + " of those address(es) are registered as no secret of the module that publishes"
            + " them, over "
            + theModulesThatRegisterAnAddress().size()
            + " module(s), and "
            + THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS.size()
            + " module(s) serve a payer page whose address nothing answers: "
            + theModulesWhoseAddressNothingAnswers());
    read.add(
        "each module's declaration is compared the way that module's own redactor compares a key,"
            + " read of the "
            + TrailWithholdingDeclarations.THE_DECLARATION
            + " of each module: "
            + theMatchingOf(modules)
            + ", and of the "
            + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
            + " of each module that declares one: "
            + theMatchingOfTheDeliveriesOf(deliveries.modules()));
    read.add(
        build.rows().theMethodsThatBuildARow()
            + " method(s) across those modules build a "
            + TrailWithholdingDeclarations.THE_ROW_A_TRAIL_KEEPS
            + ", of which "
            + build.rows().theMethodsThatBuildAnAnswerRow()
            + " build one carrying "
            + TrailWithholdingDeclarations.AN_ANSWER_TO_A_CLIENT
            + ", read "
            + TrailWithholdingDeclarations.WHERE_THE_DIRECTION_OF_A_ROW_IS_READ
            + ", which is "
            + howManyAnswerAClient(modules)
            + " class(es), of which "
            + howManyAnswerAClientReadingTheirOwnDeclaration(modules)
            + " read their own module's declaration: "
            + theClassesThatAnswerAClient(modules)
            + ". "
            + build.rows().theClassesThatBuildARowAndAnswerNoClient().size()
            + " class(es) build a row that carries no answer to a client and are examined for"
            + " none: "
            + build.rows().theClassesThatBuildARowAndAnswerNoClient());
    read.add(
        "the providers that tell a tester this simulator serves the payer's page are "
            + serving
            + ", and the floor each one's declaration stands on is the address of that page, because "
            + WHY_AN_ADDRESS_IS_THE_FLOOR);
    read.add(
        "read "
            + deliveries.classesRead()
            + " compiled class file(s) for the delivery direction, of which "
            + deliveries.theClassesThatKeepADeliveryRow().size()
            + " keep(s) a row naming one of "
            + TrailWithholdingDeclarations.THE_DIRECTIONS_A_DELIVERY_ROW_NAMES
            + " in "
            + deliveries.theMethodsThatKeepADeliveryRow().size()
            + " method(s) of their own, of which "
            + howManyKeepADeliveryRowFromABodyAModuleWithheld(deliveries)
            + " write(s) it from a body a module withheld, read "
            + TrailWithholdingDeclarations.WHERE_A_WITHHELD_BODY_IS_READ_FOR_A_ROW
            + ": "
            + deliveries.theMethodsThatKeepADeliveryRow()
            + ", and "
            + TrailWithholdingDeclarations.THE_DIRECTIONS_A_ROW_NAMES
            + " names "
            + deliveries.theDirectionsARowCanName());
    read.add(
        theModulesThatMintADelivery(deliveries.modules()).size()
            + " of the "
            + deliveries.modules().size()
            + " provider module(s) mint a delivery: "
            + theModulesThatMintADelivery(deliveries.modules())
            + ", withholding "
            + howManyDeliveredFieldsAreWithheld(deliveries.modules())
            + " delivered field(s) between them, declared as "
            + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
            + " by "
            + theClassesThatDeclareTheDelivery(deliveries.modules()));
    read.add(
        theClassesThatMintADelivery(deliveries.modules())
            + " mint the body a delivery row is written from, and "
            + theClassesThatCarryForwardABodyAlreadyWithheld(deliveries.modules())
            + " mint one out of a body another class already withheld, because "
            + WHY_A_DELIVERY_IS_FOUND_BY_THE_BODY_IT_MINTS);
    read.add(
        THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD.size()
            + " module(s) deliver a body registered as holding nothing to withhold: "
            + theModulesWhoseDeliveredBodyHoldsNothingToWithhold()
            + ", and "
            + THE_FIELDS_A_MODULE_DELIVERS_IN_THE_CLEAR.size()
            + " field(s) are recorded as delivered in the clear: "
            + theFieldsRecordedAsDeliveredInTheClear());
    return new Verdict(offences, read);
  }

  static List<String> theOffencesOf(List<AModule> modules) {
    List<String> offences = new ArrayList<>();
    for (AModule module : modules) {
      offences.addAll(theOffenceOf(module));
    }
    return List.copyOf(offences);
  }

  static List<String> theOffenceOf(AModule module) {
    List<String> offences = new ArrayList<>();
    if (module.theClassesThatDeclareIt().isEmpty()) {
      offences.add(
          "provider "
              + module.provider()
              + " declares no "
              + TrailWithholdingDeclarations.THE_DECLARATION
              + " in any class of its own package, so nothing names the fields its answers are"
              + " written to the trail redacted, and a tester reads on a screen whatever it"
              + " answered");
    }
    if (module.theClassesThatDeclareIt().size() > 1) {
      offences.add(
          "provider "
              + module.provider()
              + " declares "
              + TrailWithholdingDeclarations.THE_DECLARATION
              + " in "
              + module.theClassesThatDeclareIt()
              + ", so what it withholds is spread over more than one class and a class applying"
              + " one of them withholds less than the module declares");
    }
    for (String answering : module.theClassesThatAnswerAClientWithoutReadingItsOwnDeclaration()) {
      offences.add(
          answering
              + " writes a row naming "
              + TrailWithholdingDeclarations.AN_ANSWER_TO_A_CLIENT
              + " and reads no "
              + TrailWithholdingDeclarations.THE_DECLARATION
              + " of provider "
              + module.provider()
              + ", so this module declares what it withholds and this class answers around it."
              + " What this gate reads is the read, not the reach: whether the body that class"
              + " hands the trail is one the declaration's keys can match is a fact about that"
              + " one site, and a unit test of the row it writes is what states it");
    }
    for (String silent : module.theRowsItBuildsWhoseDirectionNothingNames()) {
      offences.add(
          silent
              + " builds a "
              + TrailWithholdingDeclarations.THE_ROW_A_TRAIL_KEEPS
              + " and no method of its own class that names a direction of "
              + TrailWithholdingDeclarations.THE_DIRECTIONS_A_ROW_NAMES
              + " reaches it, so this gate cannot tell what that row carries and would leave"
              + " provider "
              + module.provider()
              + " out of the set it examines. A row is built where its direction is named, or by a"
              + " method the one naming the direction calls");
    }
    if (module.itsRedactorsDisagree()) {
      offences.add(
          "provider "
              + module.provider()
              + " hands its "
              + TrailWithholdingDeclarations.THE_DECLARATION
              + " to "
              + module.theRedactorsThatConsumeIt()
              + ", which do not match a key the same way, so what this module withholds depends"
              + " on which of them wrote the row and this gate could only guess which matching"
              + " its declaration was written for");
    }
    for (String named : module.theAddressShapedNamesItPublishes()) {
      if (module.withholds(named) || registered(module.provider(), named)) {
        continue;
      }
      offences.add(
          "provider "
              + module.provider()
              + " publishes the name "
              + named
              + ", which reads as an address, and neither withholds it under a key its own"
              + " redactor matches ("
              + module.howItsRedactorMatches()
              + ") nor registers it: "
              + HOW_AN_ADDRESS_IS_ADMITTED);
    }
    return List.copyOf(offences);
  }

  static boolean registered(String provider, String named) {
    for (AnAddressInTheClear admitted : THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR) {
      if (admitted.provider().equals(provider) && admitted.named().equals(named)) {
        return true;
      }
    }
    return false;
  }

  static List<String> theModulesThatServeAPageAndWithholdNothing(
      List<AModule> modules, List<String> serving) {
    List<String> offences = new ArrayList<>();
    for (AModule module : modules) {
      if (!serving.contains(module.provider()) || !module.theFieldsItWithholds().isEmpty()) {
        continue;
      }
      if (registeredAsAnsweringNoAddress(module.provider())) {
        continue;
      }
      offences.add(
          "provider "
              + module.provider()
              + " tells a tester this simulator serves the payer's page and withholds no response"
              + " field at all, so either the field carrying that page's address reaches the trail"
              + " in the clear or this module answers no address and nothing here says so."
              + " Register it in"
              + " TrailWithholdingConvention.THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS with the"
              + " reason a payer reaches that page without an address this module answers");
    }
    return List.copyOf(offences);
  }

  static boolean registeredAsAnsweringNoAddress(String provider) {
    for (APageWithNoAddressToWithhold registered : THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS) {
      if (registered.provider().equals(provider)) {
        return true;
      }
    }
    return false;
  }

  static List<String> theRegistrationsNoModuleUses(List<AModule> modules) {
    List<String> offences = new ArrayList<>();
    for (AnAddressInTheClear admitted : THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR) {
      if (!someModulePublishes(modules, admitted)) {
        offences.add(
            admitted.named()
                + " is registered as an address provider "
                + admitted.provider()
                + " may answer in the clear, and that provider publishes no such name, so this"
                + " registration admits a name nothing asks for and would go on admitting it"
                + " after the module stopped publishing it. Registered because: "
                + admitted.why());
      }
    }
    for (APageWithNoAddressToWithhold registered : THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS) {
      if (!someModuleServesAPageAndWithholdsNothing(modules, registered.provider())) {
        offences.add(
            "provider "
                + registered.provider()
                + " is registered as serving a payer page whose address nothing answers, and it"
                + " now withholds a response field of its own or compiles into no module here, so"
                + " this registration stands for a decision nobody is making. Registered because: "
                + registered.why());
      }
    }
    return List.copyOf(offences);
  }

  static boolean someModulePublishes(List<AModule> modules, AnAddressInTheClear admitted) {
    for (AModule module : modules) {
      if (module.provider().equals(admitted.provider())
          && module.theAddressShapedNamesItPublishes().contains(admitted.named())) {
        return true;
      }
    }
    return false;
  }

  static boolean someModuleServesAPageAndWithholdsNothing(List<AModule> modules, String provider) {
    for (AModule module : modules) {
      if (module.provider().equals(provider)) {
        return module.theFieldsItWithholds().isEmpty();
      }
    }
    return false;
  }

  static List<String> theModulesThatRegisterAnAddress() {
    TreeSet<String> named = new TreeSet<>();
    for (AnAddressInTheClear admitted : THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR) {
      named.add(admitted.provider());
    }
    return List.copyOf(named);
  }

  static List<String> theModulesWhoseAddressNothingAnswers() {
    TreeSet<String> named = new TreeSet<>();
    for (APageWithNoAddressToWithhold registered : THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS) {
      named.add(registered.provider());
    }
    return List.copyOf(named);
  }

  static List<String> theMatchingOf(List<AModule> modules) {
    List<String> read = new ArrayList<>();
    for (AModule module : modules) {
      read.add(module.provider() + " matches " + module.howItsRedactorMatches());
    }
    return List.copyOf(read);
  }

  static List<String> theMatchingOfTheDeliveriesOf(List<ADeliveringModule> modules) {
    List<String> read = new ArrayList<>();
    for (ADeliveringModule module : modules) {
      if (module.theClassesThatDeclareIt().isEmpty()) {
        continue;
      }
      read.add(module.provider() + " matches " + module.howItsRedactorMatches());
    }
    return List.copyOf(read);
  }

  static int howManyFieldsAreWithheld(List<AModule> modules) {
    int counted = 0;
    for (AModule module : modules) {
      counted += module.theFieldsItWithholds().size();
    }
    return counted;
  }

  static int howManyAddressShapedNames(List<AModule> modules) {
    int counted = 0;
    for (AModule module : modules) {
      counted += module.theAddressShapedNamesItPublishes().size();
    }
    return counted;
  }

  static int howManyAnswerAClient(List<AModule> modules) {
    int counted = 0;
    for (AModule module : modules) {
      counted += module.theClassesThatAnswerAClient().size();
    }
    return counted;
  }

  static int howManyAnswerAClientReadingTheirOwnDeclaration(List<AModule> modules) {
    int counted = 0;
    for (AModule module : modules) {
      counted +=
          module.theClassesThatAnswerAClient().size()
              - module.theClassesThatAnswerAClientWithoutReadingItsOwnDeclaration().size();
    }
    return counted;
  }

  static int howManyKeepADeliveryRowFromABodyAModuleWithheld(TheDeliveries deliveries) {
    return deliveries.theMethodsThatKeepADeliveryRow().size()
        - deliveries.theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld().size();
  }

  static List<String> theAddressShapedNames(List<AModule> modules) {
    List<String> named = new ArrayList<>();
    for (AModule module : modules) {
      for (String address : module.theAddressShapedNamesItPublishes()) {
        named.add(module.provider() + " " + address);
      }
    }
    return List.copyOf(named);
  }

  static List<String> theClassesThatDeclareIt(List<AModule> modules) {
    List<String> named = new ArrayList<>();
    for (AModule module : modules) {
      named.addAll(module.theClassesThatDeclareIt());
    }
    return List.copyOf(named);
  }

  static void refuseASweepThatKeptNoDeliveryRow(TheDeliveries deliveries, Path classes) {
    if (!deliveries.theClassesThatKeepADeliveryRow().isEmpty()) {
      return;
    }
    throw new IllegalStateException(
        "no class under "
            + classes
            + " keeps a trail row naming any of "
            + TrailWithholdingDeclarations.THE_DIRECTIONS_A_DELIVERY_ROW_NAMES
            + ", so this rule would examine no delivery and report that every module withholds"
            + " what it delivers. This simulator delivers a callback for most of its"
            + " providers, so a run that reads no delivery row has a broken classpath, not a clean"
            + " tree. "
            + deliveries.classesRead()
            + " class file(s) were read.");
  }

  static List<String> theDirectionsNoRowCanName(TheDeliveries deliveries) {
    List<String> offences = new ArrayList<>();
    for (String named : TrailWithholdingDeclarations.THE_DIRECTIONS_A_DELIVERY_ROW_NAMES) {
      if (deliveries.theDirectionsARowCanName().contains(named)) {
        continue;
      }
      offences.add(
          "this rule reads a delivery row by the direction "
              + named
              + ", and "
              + TrailWithholdingDeclarations.THE_DIRECTIONS_A_ROW_NAMES
              + " names no such direction, only "
              + deliveries.theDirectionsARowCanName()
              + ", so a rule written against that name would match no row and report that every"
              + " module withholds what it delivers. Name the direction a delivery row carries, or"
              + " drop the one that was renamed");
    }
    return List.copyOf(offences);
  }

  static List<String> theOffencesOfTheDeliveries(TheDeliveries deliveries) {
    List<String> offences = new ArrayList<>();
    for (String keeping : deliveries.theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld()) {
      offences.add(
          keeping
              + " keeps a trail row naming one of "
              + TrailWithholdingDeclarations.THE_DIRECTIONS_A_DELIVERY_ROW_NAMES
              + " and never reads "
              + TrailWithholdingDeclarations.THE_BODY_A_DELIVERY_CARRIES
              + "."
              + TrailWithholdingDeclarations.HOW_A_WITHHELD_BODY_IS_READ_BACK
              + " in itself or in any method of its own class that reaches it, so the body it"
              + " writes into that row is not the one the provider withheld and every "
              + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
              + " of this build decorates nothing at that row. A delivery row is written from the"
              + " withheld body the module handed over, never from the bytes on the wire, and this"
              + " is read of each writing method rather than of the class it sits in, because one"
              + " method of a class reading the withheld body says nothing about the next");
    }
    for (ADeliveringModule module : deliveries.modules()) {
      offences.addAll(theOffenceOfTheDeliveryOf(module));
    }
    return List.copyOf(offences);
  }

  static List<String> theOffenceOfTheDeliveryOf(ADeliveringModule module) {
    List<String> offences = new ArrayList<>();
    if (!module.mintsADelivery()) {
      if (!module.theClassesThatDeclareIt().isEmpty()) {
        offences.add(
            "provider "
                + module.provider()
                + " declares "
                + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
                + " in "
                + module.theClassesThatDeclareIt()
                + " and mints no "
                + TrailWithholdingDeclarations.THE_BODY_A_DELIVERY_CARRIES
                + " anywhere in its own package, so that declaration is withheld from no row and"
                + " names fields a reader would take for a decision this module is making");
      }
      return List.copyOf(offences);
    }
    if (module.theClassesThatDeclareIt().isEmpty()) {
      offences.add(
          "provider "
              + module.provider()
              + " mints the body of a delivery in "
              + module.theClassesThatMintADelivery()
              + " and declares no "
              + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
              + " in any class of its own package, so nothing names the fields the delivery rows"
              + " of this module keep redacted, and a tester reads on a screen whatever it"
              + " delivered");
    }
    if (module.theClassesThatDeclareIt().size() > 1) {
      offences.add(
          "provider "
              + module.provider()
              + " declares "
              + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
              + " in "
              + module.theClassesThatDeclareIt()
              + ", so what it withholds from a delivery is spread over more than one class and a"
              + " class applying one of them withholds less than the module declares");
    }
    if (module.itsRedactorsDisagree()) {
      offences.add(
          "provider "
              + module.provider()
              + " hands its "
              + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
              + " to "
              + module.theRedactorsThatConsumeIt()
              + ", which do not match a key the same way, so what this module withholds from a"
              + " delivery depends on which of them wrote that row, and a name recorded as"
              + " delivered in the clear could only be read back against a matching this gate"
              + " picked between them");
    }
    for (String minting : module.theClassesThatMintADeliveryWithoutReadingItsOwnDeclaration()) {
      offences.add(
          minting
              + " mints a "
              + TrailWithholdingDeclarations.THE_BODY_A_DELIVERY_CARRIES
              + " and reads no "
              + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
              + " of provider "
              + module.provider()
              + ", so this module declares what a delivery withholds and this class delivers"
              + " around it. What this rule reads is the read, not the reach: whether the body"
              + " that class hands over is one the declaration's keys can match is a fact about"
              + " that one site, and a unit test of the row it writes is what states it");
    }
    if (module.composesADelivery()
        && module.theFieldsItWithholds().isEmpty()
        && !registeredAsDeliveringNothingToWithhold(module.provider())) {
      offences.add(
          "provider "
              + module.provider()
              + " composes the body of a delivery in "
              + module.theClassesThatComposeADelivery()
              + " and its "
              + TrailWithholdingDeclarations.THE_DELIVERY_DECLARATION
              + " withholds no field at all, so either a field of that body reaches the trail in"
              + " the clear or every field of it is one this build keeps readable and nothing here"
              + " says so: "
              + HOW_AN_EMPTY_DELIVERY_IS_ADMITTED);
    }
    return List.copyOf(offences);
  }

  static boolean registeredAsDeliveringNothingToWithhold(String provider) {
    for (ADeliveryWithNothingToWithhold registered :
        THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD) {
      if (registered.provider().equals(provider)) {
        return true;
      }
    }
    return false;
  }

  static List<String> theDeliveryRegistrationsNoModuleUses(List<ADeliveringModule> modules) {
    List<String> offences = new ArrayList<>();
    for (ADeliveryWithNothingToWithhold registered :
        THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD) {
      if (someModuleDeliversNothingToWithhold(modules, registered.provider())) {
        continue;
      }
      offences.add(
          "provider "
              + registered.provider()
              + " is registered as delivering a body that holds nothing to withhold, and it now"
              + " withholds a delivered field of its own, composes the body of no delivery at all"
              + " or compiles into no module here, so this registration stands for a decision"
              + " nobody is making. Registered because: "
              + registered.why());
    }
    for (AFieldDeliveredInTheClear recorded : THE_FIELDS_A_MODULE_DELIVERS_IN_THE_CLEAR) {
      if (someModuleDeliversItInTheClear(modules, recorded)) {
        continue;
      }
      offences.add(
          recorded.named()
              + " is recorded as a field provider "
              + recorded.provider()
              + " delivers in the clear, and no class of it that mints a delivery names that"
              + " constant, or its delivery declaration withholds it after all, so this reason"
              + " has outlived the decision it was written for and a reader would take it for the"
              + " decision still being made. "
              + HOW_A_FIELD_IS_DELIVERED_IN_THE_CLEAR
              + ". Recorded because: "
              + recorded.why());
    }
    return List.copyOf(offences);
  }

  static boolean someModuleDeliversNothingToWithhold(
      List<ADeliveringModule> modules, String provider) {
    for (ADeliveringModule module : modules) {
      if (module.provider().equals(provider)) {
        return module.composesADelivery() && module.theFieldsItWithholds().isEmpty();
      }
    }
    return false;
  }

  static boolean someModuleDeliversItInTheClear(
      List<ADeliveringModule> modules, AFieldDeliveredInTheClear recorded) {
    for (ADeliveringModule module : modules) {
      if (module.provider().equals(recorded.provider())) {
        return module.aClassThatMintsADeliveryNames(recorded.named())
            && !module.withholdsInItsDeliveries(recorded.named());
      }
    }
    return false;
  }

  static List<String> theModulesThatMintADelivery(List<ADeliveringModule> modules) {
    List<String> named = new ArrayList<>();
    for (ADeliveringModule module : modules) {
      if (module.mintsADelivery()) {
        named.add(module.provider());
      }
    }
    return List.copyOf(named);
  }

  static int howManyDeliveredFieldsAreWithheld(List<ADeliveringModule> modules) {
    int counted = 0;
    for (ADeliveringModule module : modules) {
      counted += module.theFieldsItWithholds().size();
    }
    return counted;
  }

  static List<String> theClassesThatDeclareTheDelivery(List<ADeliveringModule> modules) {
    List<String> named = new ArrayList<>();
    for (ADeliveringModule module : modules) {
      named.addAll(module.theClassesThatDeclareIt());
    }
    return List.copyOf(named);
  }

  static List<String> theClassesThatMintADelivery(List<ADeliveringModule> modules) {
    List<String> named = new ArrayList<>();
    for (ADeliveringModule module : modules) {
      named.addAll(module.theClassesThatMintADelivery());
    }
    return List.copyOf(named);
  }

  static List<String> theClassesThatCarryForwardABodyAlreadyWithheld(
      List<ADeliveringModule> modules) {
    List<String> named = new ArrayList<>();
    for (ADeliveringModule module : modules) {
      named.addAll(module.theClassesThatCarryForwardABodyAlreadyWithheld());
    }
    return List.copyOf(named);
  }

  static List<String> theModulesWhoseDeliveredBodyHoldsNothingToWithhold() {
    TreeSet<String> named = new TreeSet<>();
    for (ADeliveryWithNothingToWithhold registered :
        THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD) {
      named.add(registered.provider());
    }
    return List.copyOf(named);
  }

  static List<String> theFieldsRecordedAsDeliveredInTheClear() {
    List<String> named = new ArrayList<>();
    for (AFieldDeliveredInTheClear recorded : THE_FIELDS_A_MODULE_DELIVERS_IN_THE_CLEAR) {
      named.add(recorded.provider() + " " + recorded.named());
    }
    return List.copyOf(named);
  }

  static List<String> theClassesThatAnswerAClient(List<AModule> modules) {
    List<String> named = new ArrayList<>();
    for (AModule module : modules) {
      named.addAll(module.theClassesThatAnswerAClient());
    }
    return List.copyOf(named);
  }
}
