package dev.marcinromanowski.pspsim.documentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TrailWithholdingDeclarations {

  public static final String THE_DECLARATION = "SENSITIVE_RESPONSE_FIELDS";

  public static final String THE_ROW_A_TRAIL_KEEPS =
      "dev.marcinromanowski.pspsim.kernel.audit.AuditRecord";

  public static final String THE_DIRECTIONS_A_ROW_NAMES =
      "dev.marcinromanowski.pspsim.kernel.audit.AuditDirection";

  public static final String AN_ANSWER_TO_A_CLIENT = "OUTBOUND_RESPONSE";

  public static final String THE_DELIVERY_DECLARATION = "SENSITIVE_DELIVERY_FIELDS";

  public static final String THE_BODY_A_DELIVERY_CARRIES =
      "dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload";

  public static final String HOW_A_WITHHELD_BODY_IS_READ_BACK = "sanitizedBody";

  public static final List<String> THE_DIRECTIONS_A_DELIVERY_ROW_NAMES =
      List.of("OUTBOUND_WEBHOOK_ATTEMPT", "OUTBOUND_WEBHOOK_DROPPED", "OUTBOUND_WEBHOOK_NOT_SENT");

  public static final String THE_REDACTOR_THAT_MATCHES_A_WHOLE_KEY =
      "dev.marcinromanowski.pspsim.kernel.credentials.Redaction";

  public static final String HOW_THAT_REDACTOR_IS_CALLED = "redact";

  public static final String PROVIDER_PACKAGE = "dev.marcinromanowski.pspsim.provider.";

  public static final String INITIALIZER = "<init>";

  public static final String STATIC_INITIALIZER = "<clinit>";

  public static final String THE_NAMES_OF_A_RECORD_ARE_BOOTSTRAPPED_BY =
      "java.lang.runtime.ObjectMethods";

  public static final String HOW_THOSE_NAMES_ARE_BOOTSTRAPPED = "bootstrap";

  public static final String NO_FIELD_OF_ITS_OWN = "";

  public static final String AS_AN_ENTRY_OF_ITS_OWN_DECLARATION =
      "a string of the run its own "
          + STATIC_INITIALIZER
          + " builds the value it stores into a "
          + THE_DECLARATION
          + " or a "
          + THE_DELIVERY_DECLARATION
          + " field of its own out of, which is a name that class declares withheld and not one it"
          + " publishes, and is excused in that run alone, so the same name read anywhere else in"
          + " that class, and every string that class pushes before that run, still stands against"
          + " the floor";

  public static final String AS_THE_NAME_OF_AN_ENUM_CONSTANT =
      "the first string its own "
          + STATIC_INITIALIZER
          + " pushes where it constructs an enum constant of its own, which is where the compiler"
          + " emits that constant's name and equals that name";

  public static final String WHAT_A_RUN_A_STORED_VALUE_IS_BUILT_FROM_HOLDS =
      "the string constants a class pushes with nothing between them and the store but the"
          + " instructions that shape an array of them and the one call that builds them into the"
          + " value stored, which is the one shape every declaration of this build compiles into:"
          + " a run of constant loads and a single java.util.Set.of or java.util.List.of. A string"
          + " pushed before an instruction that is none of those was consumed by that instruction"
          + " and is read as a name the class publishes";

  public static final String AS_THE_COMPONENT_NAMES_OF_ITS_OWN_RECORD =
      "the component-name list "
          + THE_NAMES_OF_A_RECORD_ARE_BOOTSTRAPPED_BY
          + " is bootstrapped with for a record of its own";

  public static final String WHERE_THE_DIRECTION_OF_A_ROW_IS_READ =
      "where the row is built, and in the methods of that same class which reach the method"
          + " building it by calls inside that class, which is how a direction handed to a"
          + " private row builder is named";

  static final String A_ROW_WRITTEN_FROM_NO_WITHHELD_BODY_IS_ONE_OF_THE_ROWS_READ =
      "a delivery row this gate reports as written from a body no module withheld is one of the"
          + " rows it read, because the count it prints is those rows less these, and a row absent"
          + " from them would make that count disagree with the rows named beside it. The row"
          + " named by nothing read is ";

  public static final String WHERE_A_WITHHELD_BODY_IS_READ_FOR_A_ROW =
      "in the method that writes the row, and in the methods of that same class which reach it by"
          + " calls inside that class, which is the same granularity the direction of a row is"
          + " read at and is why one method of a class reading the withheld body admits no other";

  public static final List<String> THE_WORDS_AN_ADDRESS_IS_NAMED_WITH =
      List.of("url", "uri", "link", "href", "location");

  public static final int THE_LONGEST_NAME_A_FIELD_HAS = 64;

  private static final Pattern NOTHING_BUT_A_FIELD_NAME = Pattern.compile("[A-Za-z0-9_.\\[\\]-]+");

  private static final Pattern A_LETTER = Pattern.compile("[A-Za-z]");

  private static final Pattern BETWEEN_WORDS = Pattern.compile("[_\\-.\\[\\]]+");

  private static final Pattern BEFORE_A_CAPITAL = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

  private TrailWithholdingDeclarations() {}

  public record AModule(
      String provider,
      List<String> theClassesThatDeclareIt,
      List<String> theFieldsItWithholds,
      List<String> theAddressShapedNamesItPublishes,
      List<String> theClassesThatAnswerAClient,
      List<String> theClassesThatAnswerAClientWithoutReadingItsOwnDeclaration,
      List<String> theRowsItBuildsWhoseDirectionNothingNames,
      List<String> theRedactorsThatConsumeIt) {

    public AModule {
      theClassesThatDeclareIt = List.copyOf(theClassesThatDeclareIt);
      theFieldsItWithholds = List.copyOf(theFieldsItWithholds);
      theAddressShapedNamesItPublishes = List.copyOf(theAddressShapedNamesItPublishes);
      theClassesThatAnswerAClient = List.copyOf(theClassesThatAnswerAClient);
      theClassesThatAnswerAClientWithoutReadingItsOwnDeclaration =
          List.copyOf(theClassesThatAnswerAClientWithoutReadingItsOwnDeclaration);
      theRowsItBuildsWhoseDirectionNothingNames =
          List.copyOf(theRowsItBuildsWhoseDirectionNothingNames);
      theRedactorsThatConsumeIt = List.copyOf(theRedactorsThatConsumeIt);
    }

    public boolean itsRedactorMatchesAWholeKey() {
      return theseRedactorsMatchAWholeKey(theRedactorsThatConsumeIt);
    }

    public boolean itsRedactorsDisagree() {
      return theseRedactorsDisagree(theRedactorsThatConsumeIt);
    }

    public boolean withholds(String name) {
      return aNameIsWithheldBy(theFieldsItWithholds, itsRedactorMatchesAWholeKey(), name);
    }

    public String howItsRedactorMatches() {
      return howTheseRedactorsMatch(theRedactorsThatConsumeIt);
    }
  }

  public static boolean theseRedactorsMatchAWholeKey(List<String> redactors) {
    return !redactors.isEmpty() && redactors.equals(List.of(theWholeKeyRedactor()));
  }

  public static boolean theseRedactorsDisagree(List<String> redactors) {
    return redactors.size() > 1 && redactors.contains(theWholeKeyRedactor());
  }

  public static String howTheseRedactorsMatch(List<String> redactors) {
    return theseRedactorsMatchAWholeKey(redactors)
        ? "a whole key, as " + theWholeKeyRedactor() + " does"
        : "not a whole key, which is how " + redactors + " reads one";
  }

  public static boolean aNameIsWithheldBy(
      List<String> theFieldsItWithholds, boolean itsRedactorMatchesAWholeKey, String name) {
    for (String withheld : theFieldsItWithholds) {
      if (withheld.equalsIgnoreCase(name)) {
        return true;
      }
      if (itsRedactorMatchesAWholeKey) {
        if (endsInTheSegment(withheld, name)) {
          return true;
        }
      } else if (theLeafOf(withheld).equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  public static boolean endsInTheSegment(String key, String segment) {
    int before = key.length() - segment.length() - 1;
    return before > 0
        && key.charAt(before) == '.'
        && key.regionMatches(true, before + 1, segment, 0, segment.length());
  }

  public record ThePositionsAConstantIsExcusedAt(
      int asAnEntryOfItsOwnDeclaration,
      int asTheNameOfAnEnumConstant,
      int asTheComponentNamesOfItsOwnRecord,
      List<String> theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway) {

    public ThePositionsAConstantIsExcusedAt {
      theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway =
          List.copyOf(theNamesOfItsOwnEnumConstantsAndRecordComponentsPublishedAnyway);
    }

    public int occurrences() {
      return asAnEntryOfItsOwnDeclaration
          + asTheNameOfAnEnumConstant
          + asTheComponentNamesOfItsOwnRecord;
    }
  }

  public record TheRowsThisBuildBuilds(
      int theMethodsThatBuildARow,
      int theMethodsThatBuildAnAnswerRow,
      List<String> theClassesThatBuildARowAndAnswerNoClient) {

    public TheRowsThisBuildBuilds {
      theClassesThatBuildARowAndAnswerNoClient =
          List.copyOf(theClassesThatBuildARowAndAnswerNoClient);
    }
  }

  public record TheBuild(
      List<AModule> modules,
      int classesRead,
      int constantsRead,
      ThePositionsAConstantIsExcusedAt excused,
      TheRowsThisBuildBuilds rows) {

    public TheBuild {
      modules = List.copyOf(modules);
    }

    public List<String> providers() {
      List<String> named = new ArrayList<>();
      for (AModule module : modules) {
        named.add(module.provider());
      }
      return List.copyOf(named);
    }
  }

  public static boolean aNameAFieldCouldHave(String constant) {
    return !constant.isEmpty()
        && constant.length() <= THE_LONGEST_NAME_A_FIELD_HAS
        && NOTHING_BUT_A_FIELD_NAME.matcher(constant).matches()
        && A_LETTER.matcher(constant).find();
  }

  public static String theLeafOf(String name) {
    String read = name;
    int bracket = read.lastIndexOf('[');
    if (bracket >= 0) {
      read = read.substring(bracket + 1);
    }
    int closing = read.indexOf(']');
    if (closing >= 0) {
      read = read.substring(0, closing);
    }
    int dot = read.lastIndexOf('.');
    return dot < 0 ? read : read.substring(dot + 1);
  }

  public static List<String> theWordsOf(String name) {
    List<String> words = new ArrayList<>();
    for (String part : BETWEEN_WORDS.split(theLeafOf(name), -1)) {
      for (String word : BEFORE_A_CAPITAL.split(part, -1)) {
        if (!word.isEmpty()) {
          words.add(word.toLowerCase(Locale.ROOT));
        }
      }
    }
    return List.copyOf(words);
  }

  public static boolean anAddressIsNamed(String constant) {
    if (!aNameAFieldCouldHave(constant)) {
      return false;
    }
    for (String word : theWordsOf(constant)) {
      if (THE_WORDS_AN_ADDRESS_IS_NAMED_WITH.contains(word)) {
        return true;
      }
    }
    return false;
  }

  public static TheBuild inTheBuild(Path classesRoot) {
    Map<String, List<String>> declaredBy = new TreeMap<>();
    Map<String, Set<String>> withheldBy = new TreeMap<>();
    Map<String, Set<String>> addressesOf = new TreeMap<>();
    Map<String, List<String>> answeringIn = new TreeMap<>();
    Map<String, List<String>> answeringWithout = new TreeMap<>();
    Map<String, List<String>> rowsWithNoDirection = new TreeMap<>();
    Map<String, Set<String>> consumersOf = new TreeMap<>();
    List<String> publishedAnyway = new ArrayList<>();
    List<String> answeringNoClient = new ArrayList<>();
    int classesRead = 0;
    int constantsRead = 0;
    int asAnEntry = 0;
    int asAnEnumConstant = 0;
    int asTheComponentNames = 0;
    int rowBuilders = 0;
    int answerRowBuilders = 0;
    for (Path compiled : classFilesUnder(classesRoot)) {
      ClassModel model = parse(compiled);
      String owner = model.thisClass().asInternalName().replace('/', '.');
      if (!aClassOfAProviderModule(owner)) {
        continue;
      }
      classesRead++;
      String provider = providerOf(owner);
      declaredBy.computeIfAbsent(provider, named -> new ArrayList<>());
      withheldBy.computeIfAbsent(provider, named -> new LinkedHashSet<>());
      addressesOf.computeIfAbsent(provider, named -> new TreeSet<>());
      answeringIn.computeIfAbsent(provider, named -> new ArrayList<>());
      answeringWithout.computeIfAbsent(provider, named -> new ArrayList<>());
      rowsWithNoDirection.computeIfAbsent(provider, named -> new ArrayList<>());
      consumersOf.computeIfAbsent(provider, named -> new TreeSet<>());
      if (declares(model)) {
        declaredBy.get(provider).add(owner);
        withheldBy.get(provider).addAll(theDeclarationBuiltIn(model));
      }
      TheConstantsOfAClass itsConstants = theConstantsOf(model);
      constantsRead += itsConstants.occurrences();
      for (AnExcusedConstant excused : itsConstants.excused()) {
        if (excused.where().equals(AS_AN_ENTRY_OF_ITS_OWN_DECLARATION)) {
          asAnEntry++;
        } else if (excused.where().equals(AS_THE_NAME_OF_AN_ENUM_CONSTANT)) {
          asAnEnumConstant++;
        } else {
          asTheComponentNames++;
        }
      }
      for (String constant : itsConstants.published()) {
        if (anAddressIsNamed(constant)) {
          addressesOf.get(provider).add(constant);
        }
      }
      for (String emitted : theNamesOfItsOwnEnumConstantsAndRecordComponents(model)) {
        if (itsConstants.published().contains(emitted)) {
          publishedAnyway.add(owner + " " + emitted);
        }
      }
      boolean readsIt = readsTheDeclarationOf(model, provider);
      consumersOf
          .computeIfAbsent(provider, named -> new TreeSet<>())
          .addAll(theRedactorsConsumingTheDeclarationOf(model, provider));
      TheRowsAClassBuilds itsRows = theRowsBuiltIn(model, AN_ANSWER_TO_A_CLIENT);
      rowBuilders += itsRows.theMethodsThatBuildARow().size();
      answerRowBuilders += itsRows.theMethodsThatBuildARowCarryingIt().size();
      rowsWithNoDirection.get(provider).addAll(itsRows.theMethodsWhoseRowCarriesNoNamedDirection());
      if (itsRows.theMethodsThatBuildARowCarryingIt().isEmpty()) {
        if (!itsRows.theMethodsThatBuildARow().isEmpty()) {
          answeringNoClient.add(
              owner
                  + " builds "
                  + itsRows.theMethodsThatBuildARow().size()
                  + " row(s), none of them carrying "
                  + AN_ANSWER_TO_A_CLIENT
                  + ": "
                  + itsRows.theDirectionsEachOfThoseRowsCarries());
        }
        continue;
      }
      answeringIn.get(provider).add(owner);
      if (!readsIt) {
        answeringWithout.get(provider).add(owner);
      }
    }
    List<AModule> modules = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : declaredBy.entrySet()) {
      String provider = entry.getKey();
      modules.add(
          new AModule(
              provider,
              entry.getValue(),
              List.copyOf(withheldBy.get(provider)),
              List.copyOf(addressesOf.get(provider)),
              answeringIn.get(provider),
              answeringWithout.get(provider),
              rowsWithNoDirection.get(provider),
              List.copyOf(consumersOf.get(provider))));
    }
    return new TheBuild(
        modules,
        classesRead,
        constantsRead,
        new ThePositionsAConstantIsExcusedAt(
            asAnEntry, asAnEnumConstant, asTheComponentNames, publishedAnyway),
        new TheRowsThisBuildBuilds(rowBuilders, answerRowBuilders, answeringNoClient));
  }

  public static boolean aClassOfAProviderModule(String owner) {
    return owner.startsWith(PROVIDER_PACKAGE) && owner.indexOf('.', PROVIDER_PACKAGE.length()) > 0;
  }

  public static String providerOf(String owner) {
    String rest = owner.substring(PROVIDER_PACKAGE.length());
    int next = rest.indexOf('.');
    return next < 0 ? rest : rest.substring(0, next);
  }

  static List<String> theDeclarationBuiltIn(ClassModel model) {
    return theDeclarationBuiltIn(model, THE_DECLARATION);
  }

  public record AStoreAMethodMakes(
      String theFieldOfItsOwnStoredInto,
      List<String> thePushesSinceTheStoreBefore,
      int whereTheRunTheStoredValueIsBuiltFromBegins,
      int whereTheNameOfAConstructionOfItsOwnSits) {

    public AStoreAMethodMakes {
      thePushesSinceTheStoreBefore = List.copyOf(thePushesSinceTheStoreBefore);
    }

    public List<String> theRunTheStoredValueIsBuiltFrom() {
      return List.copyOf(
          thePushesSinceTheStoreBefore.subList(
              whereTheRunTheStoredValueIsBuiltFromBegins, thePushesSinceTheStoreBefore.size()));
    }

    public List<String> thePushesBeforeThatRun() {
      return List.copyOf(
          thePushesSinceTheStoreBefore.subList(0, whereTheRunTheStoredValueIsBuiltFromBegins));
    }

    public boolean aConstructionOfItsOwnIsNamed(String named) {
      return whereTheNameOfAConstructionOfItsOwnSits >= 0
          && whereTheNameOfAConstructionOfItsOwnSits < thePushesSinceTheStoreBefore.size()
          && thePushesSinceTheStoreBefore
              .get(whereTheNameOfAConstructionOfItsOwnSits)
              .equals(named);
    }
  }

  public record TheStoresOfAMethod(
      List<AStoreAMethodMakes> theStoresItMakes, List<String> thePushesAfterTheLastStore) {

    public TheStoresOfAMethod {
      theStoresItMakes = List.copyOf(theStoresItMakes);
      thePushesAfterTheLastStore = List.copyOf(thePushesAfterTheLastStore);
    }
  }

  private static final Set<Opcode> THE_INSTRUCTIONS_THAT_SHAPE_AN_ARRAY_OF_THEM =
      Set.of(Opcode.ANEWARRAY, Opcode.AASTORE, Opcode.DUP);

  static TheStoresOfAMethod theStoresOf(ClassModel model, MethodModel method) {
    List<AStoreAMethodMakes> stores = new ArrayList<>();
    List<String> pushed = new ArrayList<>();
    int runBegins = 0;
    int whereTheNameOfAConstructionWouldSit = -1;
    boolean itConstructsOneOfItsOwn = false;
    boolean theRunHoldsTheCallThatBuildsIt = false;
    for (CodeElement element : instructionsOf(method)) {
      if (!(element instanceof Instruction instruction)) {
        continue;
      }
      if (instruction instanceof ConstantInstruction loaded) {
        if (loaded.constantValue() instanceof String text) {
          pushed.add(text);
        }
        continue;
      }
      if (instruction instanceof FieldInstruction stored && stored.opcode() == Opcode.PUTSTATIC) {
        stores.add(
            new AStoreAMethodMakes(
                theFieldOfItsOwnStoredInto(model, stored),
                pushed,
                runBegins,
                itConstructsOneOfItsOwn ? whereTheNameOfAConstructionWouldSit : -1));
        pushed = new ArrayList<>();
        runBegins = 0;
        whereTheNameOfAConstructionWouldSit = -1;
        itConstructsOneOfItsOwn = false;
        theRunHoldsTheCallThatBuildsIt = false;
        continue;
      }
      if (instruction instanceof InvokeInstruction invoked) {
        if (invoked.name().stringValue().equals(INITIALIZER)
            && itsOwnClassOrOneNestedInIt(model, invoked.owner())) {
          itConstructsOneOfItsOwn = true;
        }
        if (theRunHoldsTheCallThatBuildsIt) {
          runBegins = pushed.size();
          theRunHoldsTheCallThatBuildsIt = false;
        } else {
          theRunHoldsTheCallThatBuildsIt = true;
        }
        continue;
      }
      if (THE_INSTRUCTIONS_THAT_SHAPE_AN_ARRAY_OF_THEM.contains(instruction.opcode())) {
        continue;
      }
      if (instruction instanceof NewObjectInstruction made
          && itsOwnClassOrOneNestedInIt(model, made.className())) {
        whereTheNameOfAConstructionWouldSit = pushed.size();
        itConstructsOneOfItsOwn = false;
      }
      runBegins = pushed.size();
      theRunHoldsTheCallThatBuildsIt = false;
    }
    return new TheStoresOfAMethod(stores, pushed);
  }

  private static boolean itsOwnClassOrOneNestedInIt(ClassModel model, ClassEntry named) {
    String own = model.thisClass().asInternalName();
    String owner = named.asInternalName();
    return owner.equals(own) || owner.startsWith(own + "$");
  }

  static List<String> theDeclarationBuiltIn(ClassModel model, String declaration) {
    List<String> built = null;
    String storedBy = null;
    for (MethodModel method : model.methods()) {
      String storing = method.methodName().stringValue();
      for (AStoreAMethodMakes store : theStoresOf(model, method).theStoresItMakes()) {
        if (!store.theFieldOfItsOwnStoredInto().equals(declaration)) {
          continue;
        }
        List<String> entries = store.theRunTheStoredValueIsBuiltFrom();
        if (built != null) {
          throw new IllegalStateException(
              aDeclarationItStoresIntoTwice(model, declaration, storedBy, built, storing, entries));
        }
        built = entries;
        storedBy = storing;
      }
    }
    return built == null ? List.of() : built;
  }

  private static String aDeclarationItStoresIntoTwice(
      ClassModel model,
      String declaration,
      String firstMethod,
      List<String> first,
      String secondMethod,
      List<String> second) {
    String owner = model.thisClass().asInternalName().replace('/', '.');
    return owner
        + " stores into its own "
        + declaration
        + " twice, in its "
        + firstMethod
        + " building "
        + first
        + " and in its "
        + secondMethod
        + " building "
        + second
        + ", so what this module withholds depends on the order those stores run in, and reading"
        + " the one this gate happens to keep would report a module withholding fewer fields than"
        + " a class applying the declaration may hold. A declaration this gate cannot read"
        + " unambiguously is refused rather than guessed at: one class builds it once, in its "
        + STATIC_INITIALIZER
        + ", which is what declaring the field final enforces and what every declaration of this"
        + " build does";
  }

  static boolean declares(ClassModel model) {
    return declares(model, THE_DECLARATION);
  }

  static boolean declares(ClassModel model, String declaration) {
    for (FieldModel field : model.fields()) {
      if (field.fieldName().stringValue().equals(declaration)) {
        return true;
      }
    }
    return false;
  }

  public record TheRowsAClassBuilds(
      List<String> theMethodsThatBuildARow,
      List<String> theMethodsThatBuildARowCarryingIt,
      List<String> theMethodsWhoseRowCarryingItIsWrittenFromNoBodyAModuleWithheld,
      List<String> theMethodsWhoseRowCarriesNoNamedDirection,
      List<String> theDirectionsEachOfThoseRowsCarries) {

    public TheRowsAClassBuilds {
      theMethodsThatBuildARow = List.copyOf(theMethodsThatBuildARow);
      theMethodsThatBuildARowCarryingIt = List.copyOf(theMethodsThatBuildARowCarryingIt);
      theMethodsWhoseRowCarryingItIsWrittenFromNoBodyAModuleWithheld =
          List.copyOf(theMethodsWhoseRowCarryingItIsWrittenFromNoBodyAModuleWithheld);
      theMethodsWhoseRowCarriesNoNamedDirection =
          List.copyOf(theMethodsWhoseRowCarriesNoNamedDirection);
      theDirectionsEachOfThoseRowsCarries = List.copyOf(theDirectionsEachOfThoseRowsCarries);
    }
  }

  public record TheDeliveryRowsAClassKeeps(
      List<String> theMethodsThatKeepOne,
      List<String> theMethodsThatWriteItFromNoBodyAModuleWithheld) {

    public TheDeliveryRowsAClassKeeps {
      theMethodsThatKeepOne = List.copyOf(theMethodsThatKeepOne);
      theMethodsThatWriteItFromNoBodyAModuleWithheld =
          List.copyOf(theMethodsThatWriteItFromNoBodyAModuleWithheld);
    }
  }

  static TheDeliveryRowsAClassKeeps theDeliveryRowsKeptIn(ClassModel model) {
    Set<String> keeping = new LinkedHashSet<>();
    Set<String> fromNoBodyWithheld = new LinkedHashSet<>();
    for (String direction : THE_DIRECTIONS_A_DELIVERY_ROW_NAMES) {
      TheRowsAClassBuilds rows = theRowsBuiltIn(model, direction);
      keeping.addAll(rows.theMethodsThatBuildARowCarryingIt());
      fromNoBodyWithheld.addAll(
          rows.theMethodsWhoseRowCarryingItIsWrittenFromNoBodyAModuleWithheld());
    }
    return new TheDeliveryRowsAClassKeeps(List.copyOf(keeping), List.copyOf(fromNoBodyWithheld));
  }

  static TheRowsAClassBuilds theRowsBuiltIn(ClassModel model, String direction) {
    String owner = model.thisClass().asInternalName().replace('/', '.');
    Map<String, String> sites = new LinkedHashMap<>();
    Map<String, Set<String>> directionsIn = new LinkedHashMap<>();
    Map<String, Set<String>> callers = new LinkedHashMap<>();
    Set<String> readingABodyAlreadyWithheld = new LinkedHashSet<>();
    List<String> builders = new ArrayList<>();
    for (MethodModel method : model.methods()) {
      String caller = theKeyOf(method);
      sites.put(
          caller,
          owner
              + "."
              + method.methodName().stringValue()
              + method.methodTypeSymbol().displayDescriptor());
      directionsIn.put(caller, new LinkedHashSet<>());
      for (CodeElement element : instructionsOf(method)) {
        if (buildsARow(element)) {
          builders.add(caller);
        }
        if (readsABodyAlreadyWithheld(element)) {
          readingABodyAlreadyWithheld.add(caller);
        }
        if (element instanceof FieldInstruction read
            && read.opcode() == Opcode.GETSTATIC
            && read.owner().asInternalName().replace('/', '.').equals(THE_DIRECTIONS_A_ROW_NAMES)) {
          directionsIn.get(caller).add(read.name().stringValue());
        }
        for (String called : theMethodsOfItsOwnClassCalledBy(model, element)) {
          callers.computeIfAbsent(called, named -> new LinkedHashSet<>()).add(caller);
        }
      }
    }
    List<String> carrying = new ArrayList<>();
    List<String> fromNoBodyWithheld = new ArrayList<>();
    List<String> unnamed = new ArrayList<>();
    List<String> carried = new ArrayList<>();
    for (String builder : new LinkedHashSet<>(builders)) {
      Set<String> reaching = theMethodsThatReach(builder, callers);
      Set<String> itCarries = new TreeSet<>(directionsIn.get(builder));
      for (String reaches : reaching) {
        itCarries.addAll(directionsIn.getOrDefault(reaches, Set.of()));
      }
      carried.add(sites.get(builder) + " carrying " + itCarries);
      if (itCarries.contains(direction)) {
        carrying.add(sites.get(builder));
        if (!aBodyAlreadyWithheldIsReadFor(builder, reaching, readingABodyAlreadyWithheld)) {
          fromNoBodyWithheld.add(sites.get(builder));
        }
      }
      if (itCarries.isEmpty()) {
        unnamed.add(sites.get(builder));
      }
    }
    List<String> built = new ArrayList<>();
    for (String builder : new LinkedHashSet<>(builders)) {
      built.add(sites.get(builder));
    }
    return new TheRowsAClassBuilds(
        built, carrying, fromNoBodyWithheld, unnamed, List.copyOf(carried));
  }

  private static boolean aBodyAlreadyWithheldIsReadFor(
      String builder, Set<String> reaching, Set<String> readingABodyAlreadyWithheld) {
    if (readingABodyAlreadyWithheld.contains(builder)) {
      return true;
    }
    for (String reaches : reaching) {
      if (readingABodyAlreadyWithheld.contains(reaches)) {
        return true;
      }
    }
    return false;
  }

  private static boolean buildsARow(CodeElement element) {
    return element instanceof InvokeInstruction invoked
        && invoked.name().stringValue().equals(INITIALIZER)
        && invoked.owner().asInternalName().replace('/', '.').equals(THE_ROW_A_TRAIL_KEEPS);
  }

  private static List<String> theMethodsOfItsOwnClassCalledBy(
      ClassModel model, CodeElement element) {
    String own = model.thisClass().asInternalName();
    if (element instanceof InvokeInstruction invoked
        && invoked.owner().asInternalName().equals(own)) {
      return List.of(invoked.name().stringValue() + invoked.typeSymbol().descriptorString());
    }
    if (element instanceof InvokeDynamicInstruction composed) {
      List<String> handled = new ArrayList<>();
      for (LoadableConstantEntry argument : composed.invokedynamic().bootstrap().arguments()) {
        if (argument instanceof MethodHandleEntry handle
            && handle.reference().owner().asInternalName().equals(own)) {
          handled.add(
              handle.reference().name().stringValue() + handle.reference().type().stringValue());
        }
      }
      return List.copyOf(handled);
    }
    return List.of();
  }

  private static Set<String> theMethodsThatReach(String called, Map<String, Set<String>> callers) {
    Set<String> reaching = new LinkedHashSet<>();
    Deque<String> pending = new ArrayDeque<>(callers.getOrDefault(called, Set.of()));
    while (!pending.isEmpty()) {
      String caller = pending.removeFirst();
      if (reaching.add(caller)) {
        pending.addAll(callers.getOrDefault(caller, Set.of()));
      }
    }
    return reaching;
  }

  private static String theKeyOf(MethodModel method) {
    return method.methodName().stringValue() + method.methodTypeSymbol().descriptorString();
  }

  public static String theWholeKeyRedactor() {
    return THE_REDACTOR_THAT_MATCHES_A_WHOLE_KEY + "." + HOW_THAT_REDACTOR_IS_CALLED;
  }

  static List<String> theRedactorsConsumingTheDeclarationOf(ClassModel model, String provider) {
    return theRedactorsConsumingTheDeclarationOf(model, provider, THE_DECLARATION);
  }

  static List<String> theRedactorsConsumingTheDeclarationOf(
      ClassModel model, String provider, String declaration) {
    Set<String> consuming = new LinkedHashSet<>();
    for (MethodModel method : model.methods()) {
      boolean theDeclarationIsOnTheStack = false;
      for (CodeElement element : instructionsOf(method)) {
        if (element instanceof FieldInstruction read
            && read.opcode() == Opcode.GETSTATIC
            && read.name().stringValue().equals(declaration)
            && theProviderOwning(read).equals(provider)) {
          theDeclarationIsOnTheStack = true;
          continue;
        }
        if (theDeclarationIsOnTheStack && element instanceof InvokeInstruction invoked) {
          consuming.add(
              invoked.owner().asInternalName().replace('/', '.')
                  + "."
                  + invoked.name().stringValue());
          theDeclarationIsOnTheStack = false;
        }
      }
    }
    return List.copyOf(consuming);
  }

  static boolean readsTheDeclarationOf(ClassModel model, String provider) {
    return readsTheDeclarationOf(model, provider, THE_DECLARATION);
  }

  static boolean readsTheDeclarationOf(ClassModel model, String provider, String declaration) {
    for (MethodModel method : model.methods()) {
      for (CodeElement element : instructionsOf(method)) {
        if (element instanceof FieldInstruction read
            && read.opcode() == Opcode.GETSTATIC
            && read.name().stringValue().equals(declaration)
            && theProviderOwning(read).equals(provider)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String theProviderOwning(FieldInstruction read) {
    String owner = read.owner().asInternalName().replace('/', '.');
    return owner.startsWith(PROVIDER_PACKAGE) ? providerOf(owner) : "";
  }

  static List<String> theNamesOfItsOwnEnumConstantsAndRecordComponents(ClassModel model) {
    Set<String> emitted = new LinkedHashSet<>(theEnumConstantsOf(model));
    model
        .findAttribute(Attributes.record())
        .ifPresent(
            components -> {
              for (RecordComponentInfo component : components.components()) {
                emitted.add(component.name().stringValue());
              }
            });
    return List.copyOf(emitted);
  }

  static List<String> theEnumConstantsOf(ClassModel model) {
    Set<String> named = new LinkedHashSet<>();
    String own = model.thisClass().asInternalName();
    for (FieldModel field : model.fields()) {
      AccessFlags flags = field.flags();
      if (flags.has(AccessFlag.ENUM)
          && field.fieldTypeSymbol().descriptorString().equals("L" + own + ";")) {
        named.add(field.fieldName().stringValue());
      }
    }
    return List.copyOf(named);
  }

  public record AnExcusedConstant(String named, String where) {}

  public record TheConstantsOfAClass(
      List<String> published, int occurrences, List<AnExcusedConstant> excused) {

    public TheConstantsOfAClass {
      published = List.copyOf(published);
      excused = List.copyOf(excused);
    }
  }

  static TheConstantsOfAClass theConstantsOf(ClassModel model) {
    Set<String> published = new LinkedHashSet<>();
    List<AnExcusedConstant> excused = new ArrayList<>();
    int occurrences = 0;
    for (FieldModel field : model.fields()) {
      Optional<ConstantValueAttribute> constant = field.findAttribute(Attributes.constantValue());
      if (constant.isPresent() && constant.get().constant() instanceof StringEntry text) {
        occurrences++;
        published.add(text.stringValue());
      }
    }
    List<String> enumConstants = theEnumConstantsOf(model);
    for (MethodModel method : model.methods()) {
      boolean theStaticInitializer = method.methodName().stringValue().equals(STATIC_INITIALIZER);
      for (CodeElement element : instructionsOf(method)) {
        if (element instanceof ConstantInstruction loaded
            && loaded.constantValue() instanceof String text) {
          occurrences++;
          if (!theStaticInitializer) {
            published.add(text);
          }
        }
        if (element instanceof InvokeDynamicInstruction composed) {
          boolean theNamesOfARecord = theComponentNamesOfARecordAreBootstrappedBy(model, composed);
          for (String text : theTextsJoinedBy(composed)) {
            occurrences++;
            if (theNamesOfARecord) {
              excused.add(new AnExcusedConstant(text, AS_THE_COMPONENT_NAMES_OF_ITS_OWN_RECORD));
            } else {
              published.add(text);
            }
          }
        }
      }
      if (!theStaticInitializer) {
        continue;
      }
      TheStoresOfAMethod itsStores = theStoresOf(model, method);
      for (AStoreAMethodMakes store : itsStores.theStoresItMakes()) {
        published.addAll(whatIsPublishedOf(store, enumConstants, excused));
      }
      published.addAll(itsStores.thePushesAfterTheLastStore());
    }
    return new TheConstantsOfAClass(List.copyOf(published), occurrences, excused);
  }

  private static String theFieldOfItsOwnStoredInto(ClassModel model, FieldInstruction stored) {
    return stored.owner().asInternalName().equals(model.thisClass().asInternalName())
        ? stored.name().stringValue()
        : NO_FIELD_OF_ITS_OWN;
  }

  private static List<String> whatIsPublishedOf(
      AStoreAMethodMakes store, List<String> enumConstants, List<AnExcusedConstant> excused) {
    String storedInto = store.theFieldOfItsOwnStoredInto();
    if (storedInto.equals(THE_DECLARATION) || storedInto.equals(THE_DELIVERY_DECLARATION)) {
      for (String entry : store.theRunTheStoredValueIsBuiltFrom()) {
        excused.add(new AnExcusedConstant(entry, AS_AN_ENTRY_OF_ITS_OWN_DECLARATION));
      }
      return store.thePushesBeforeThatRun();
    }
    List<String> rest = new ArrayList<>(store.thePushesSinceTheStoreBefore());
    if (enumConstants.contains(storedInto) && store.aConstructionOfItsOwnIsNamed(storedInto)) {
      rest.remove(store.whereTheNameOfAConstructionOfItsOwnSits());
      excused.add(new AnExcusedConstant(storedInto, AS_THE_NAME_OF_AN_ENUM_CONSTANT));
    }
    return List.copyOf(rest);
  }

  static boolean theComponentNamesOfARecordAreBootstrappedBy(
      ClassModel model, InvokeDynamicInstruction composed) {
    MemberRefEntry bootstrap = composed.invokedynamic().bootstrap().bootstrapMethod().reference();
    if (!bootstrap
            .owner()
            .asInternalName()
            .replace('/', '.')
            .equals(THE_NAMES_OF_A_RECORD_ARE_BOOTSTRAPPED_BY)
        || !bootstrap.name().stringValue().equals(HOW_THOSE_NAMES_ARE_BOOTSTRAPPED)) {
      return false;
    }
    if (model.findAttribute(Attributes.record()).isEmpty()) {
      return false;
    }
    List<LoadableConstantEntry> arguments = composed.invokedynamic().bootstrap().arguments();
    return !arguments.isEmpty()
        && arguments.getFirst() instanceof ClassEntry whoseComponentsTheyAre
        && whoseComponentsTheyAre.asInternalName().equals(model.thisClass().asInternalName());
  }

  private static List<String> theTextsJoinedBy(InvokeDynamicInstruction composed) {
    List<String> joined = new ArrayList<>();
    for (LoadableConstantEntry argument : composed.invokedynamic().bootstrap().arguments()) {
      if (argument instanceof StringEntry text) {
        joined.add(text.stringValue());
      }
    }
    return List.copyOf(joined);
  }

  public record ADeliveringModule(
      String provider,
      List<String> theClassesThatDeclareIt,
      List<String> theFieldsItWithholds,
      List<String> theNamesTheClassesThatMintADeliveryName,
      List<String> theClassesThatMintADelivery,
      List<String> theClassesThatMintADeliveryWithoutReadingItsOwnDeclaration,
      List<String> theClassesThatCarryForwardABodyAlreadyWithheld,
      List<String> theRedactorsThatConsumeIt) {

    public ADeliveringModule {
      theClassesThatDeclareIt = List.copyOf(theClassesThatDeclareIt);
      theFieldsItWithholds = List.copyOf(theFieldsItWithholds);
      theNamesTheClassesThatMintADeliveryName =
          List.copyOf(theNamesTheClassesThatMintADeliveryName);
      theClassesThatMintADelivery = List.copyOf(theClassesThatMintADelivery);
      theClassesThatMintADeliveryWithoutReadingItsOwnDeclaration =
          List.copyOf(theClassesThatMintADeliveryWithoutReadingItsOwnDeclaration);
      theClassesThatCarryForwardABodyAlreadyWithheld =
          List.copyOf(theClassesThatCarryForwardABodyAlreadyWithheld);
      theRedactorsThatConsumeIt = List.copyOf(theRedactorsThatConsumeIt);
    }

    public boolean itsRedactorMatchesAWholeKey() {
      return theseRedactorsMatchAWholeKey(theRedactorsThatConsumeIt);
    }

    public boolean itsRedactorsDisagree() {
      return theseRedactorsDisagree(theRedactorsThatConsumeIt);
    }

    public String howItsRedactorMatches() {
      return howTheseRedactorsMatch(theRedactorsThatConsumeIt);
    }

    public boolean mintsADelivery() {
      return !theClassesThatMintADelivery.isEmpty();
    }

    public List<String> theClassesThatComposeADelivery() {
      List<String> composing = new ArrayList<>(theClassesThatMintADelivery);
      composing.removeAll(theClassesThatCarryForwardABodyAlreadyWithheld);
      return List.copyOf(composing);
    }

    public boolean composesADelivery() {
      return !theClassesThatComposeADelivery().isEmpty();
    }

    public boolean aClassThatMintsADeliveryNames(String name) {
      return theNamesTheClassesThatMintADeliveryName.contains(name);
    }

    public boolean withholdsInItsDeliveries(String name) {
      return aNameIsWithheldBy(theFieldsItWithholds, itsRedactorMatchesAWholeKey(), name);
    }
  }

  public record TheDeliveries(
      List<ADeliveringModule> modules,
      List<String> theClassesThatKeepADeliveryRow,
      List<String> theMethodsThatKeepADeliveryRow,
      List<String> theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld,
      List<String> theDirectionsARowCanName,
      int classesRead) {

    public TheDeliveries {
      modules = List.copyOf(modules);
      theClassesThatKeepADeliveryRow = List.copyOf(theClassesThatKeepADeliveryRow);
      theMethodsThatKeepADeliveryRow = List.copyOf(theMethodsThatKeepADeliveryRow);
      theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld =
          List.copyOf(theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld);
      theDirectionsARowCanName = List.copyOf(theDirectionsARowCanName);
      for (String writing : theMethodsThatKeepADeliveryRowFromABodyNoModuleWithheld) {
        if (!theMethodsThatKeepADeliveryRow.contains(writing)) {
          throw new IllegalArgumentException(
              A_ROW_WRITTEN_FROM_NO_WITHHELD_BODY_IS_ONE_OF_THE_ROWS_READ + writing);
        }
      }
    }
  }

  public static TheDeliveries theDeliveriesIn(Path classesRoot) {
    Map<String, List<String>> declaredBy = new TreeMap<>();
    Map<String, Set<String>> withheldBy = new TreeMap<>();
    Map<String, Set<String>> namedByItsMinters = new TreeMap<>();
    Map<String, List<String>> mintingIn = new TreeMap<>();
    Map<String, List<String>> mintingWithout = new TreeMap<>();
    Map<String, List<String>> carryingForward = new TreeMap<>();
    Map<String, Set<String>> consumersOf = new TreeMap<>();
    List<String> keepingADeliveryRow = new ArrayList<>();
    List<String> theRowsKept = new ArrayList<>();
    List<String> theRowsKeptFromABodyNoModuleWithheld = new ArrayList<>();
    List<String> directionsARowCanName = new ArrayList<>();
    int classesRead = 0;
    for (Path compiled : classFilesUnder(classesRoot)) {
      ClassModel model = parse(compiled);
      String owner = model.thisClass().asInternalName().replace('/', '.');
      classesRead++;
      if (owner.equals(THE_DIRECTIONS_A_ROW_NAMES)) {
        directionsARowCanName = theEnumConstantsOf(model);
      }
      TheDeliveryRowsAClassKeeps itsDeliveryRows = theDeliveryRowsKeptIn(model);
      if (!itsDeliveryRows.theMethodsThatKeepOne().isEmpty()) {
        keepingADeliveryRow.add(owner);
        theRowsKept.addAll(itsDeliveryRows.theMethodsThatKeepOne());
        theRowsKeptFromABodyNoModuleWithheld.addAll(
            itsDeliveryRows.theMethodsThatWriteItFromNoBodyAModuleWithheld());
      }
      if (!aClassOfAProviderModule(owner)) {
        continue;
      }
      String provider = providerOf(owner);
      declaredBy.computeIfAbsent(provider, named -> new ArrayList<>());
      withheldBy.computeIfAbsent(provider, named -> new LinkedHashSet<>());
      namedByItsMinters.computeIfAbsent(provider, named -> new TreeSet<>());
      mintingIn.computeIfAbsent(provider, named -> new ArrayList<>());
      mintingWithout.computeIfAbsent(provider, named -> new ArrayList<>());
      carryingForward.computeIfAbsent(provider, named -> new ArrayList<>());
      consumersOf
          .computeIfAbsent(provider, named -> new TreeSet<>())
          .addAll(theRedactorsConsumingTheDeclarationOf(model, provider, THE_DELIVERY_DECLARATION));
      if (declares(model, THE_DELIVERY_DECLARATION)) {
        declaredBy.get(provider).add(owner);
        withheldBy.get(provider).addAll(theDeclarationBuiltIn(model, THE_DELIVERY_DECLARATION));
      }
      if (!mintsADelivery(model)) {
        continue;
      }
      for (String constant : theConstantsOf(model).published()) {
        if (aNameAFieldCouldHave(constant)) {
          namedByItsMinters.get(provider).add(constant);
        }
      }
      mintingIn.get(provider).add(owner);
      if (carriesABodyAlreadyWithheld(model)) {
        carryingForward.get(provider).add(owner);
      } else if (!readsTheDeclarationOf(model, provider, THE_DELIVERY_DECLARATION)) {
        mintingWithout.get(provider).add(owner);
      }
    }
    List<ADeliveringModule> modules = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : declaredBy.entrySet()) {
      String provider = entry.getKey();
      modules.add(
          new ADeliveringModule(
              provider,
              entry.getValue(),
              List.copyOf(withheldBy.get(provider)),
              List.copyOf(namedByItsMinters.get(provider)),
              mintingIn.get(provider),
              mintingWithout.get(provider),
              carryingForward.get(provider),
              List.copyOf(consumersOf.get(provider))));
    }
    return new TheDeliveries(
        modules,
        keepingADeliveryRow,
        theRowsKept,
        theRowsKeptFromABodyNoModuleWithheld,
        directionsARowCanName,
        classesRead);
  }

  static boolean mintsADelivery(ClassModel model) {
    for (MethodModel method : model.methods()) {
      for (CodeElement element : instructionsOf(method)) {
        if (element instanceof InvokeInstruction invoked
            && invoked.name().stringValue().equals(INITIALIZER)
            && invoked
                .owner()
                .asInternalName()
                .replace('/', '.')
                .equals(THE_BODY_A_DELIVERY_CARRIES)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean carriesABodyAlreadyWithheld(ClassModel model) {
    for (MethodModel method : model.methods()) {
      for (CodeElement element : instructionsOf(method)) {
        if (readsABodyAlreadyWithheld(element)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean readsABodyAlreadyWithheld(CodeElement element) {
    return element instanceof InvokeInstruction invoked
        && invoked.name().stringValue().equals(HOW_A_WITHHELD_BODY_IS_READ_BACK)
        && invoked.owner().asInternalName().replace('/', '.').equals(THE_BODY_A_DELIVERY_CARRIES);
  }

  private static List<CodeElement> instructionsOf(MethodModel method) {
    return method
        .code()
        .map(
            code -> {
              List<CodeElement> elements = new ArrayList<>();
              code.forEach(elements::add);
              return List.copyOf(elements);
            })
        .orElse(List.of());
  }

  private static ClassModel parse(Path compiled) {
    try {
      return ClassFile.of().parse(compiled);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static List<Path> classFilesUnder(Path classesRoot) {
    if (!Files.isDirectory(classesRoot)) {
      throw new IllegalStateException(
          classesRoot
              + " is not a directory of compiled classes, so the sweep for what each provider"
              + " withholds from the audit trail would read nothing and report that every"
              + " provider withholds what it must. A sweep that found no offence and a sweep that"
              + " read no class must not print the same thing.");
    }
    try (Stream<Path> walked = Files.walk(classesRoot)) {
      return walked
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".class"))
          .sorted()
          .toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
