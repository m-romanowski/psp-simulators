package dev.marcinromanowski.pspsim.documentation;

import dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class HeaderAbsenceConvention {

  public static final String THE_SHARED_FORM = "aHeaderOfThisNameUnderAnySpelling";

  public static final String THE_ASSERTION_THE_SHARED_FORM_IS_READ_BY = "doesNotHave";

  public static final List<String> THE_ASSERTIONS_THAT_CLAIM_A_KEY_IS_ABSENT_BY_ITS_SPELLING =
      List.of(
          "doesNotContainKey",
          "doesNotContainKeys",
          "doesNotContainEntry",
          "doesNotContainEntries");

  public static final String WHAT_A_SUBJECT_NAMING_A_MAP_OF_HEADERS_READS = "header";

  public static final List<String> THE_ASSERTIONS_THAT_CLAIM_A_VALUE_IS_ABSENT =
      List.of("isNull", "isEmpty", "isNullOrEmpty", "isBlank");

  public static final List<String> WHAT_A_CALL_READING_A_HEADER_READS =
      List.of("header", "contenttype");

  public static final List<ACallAnAbsenceIsReadThrough>
      THE_CALLS_AN_ABSENCE_IS_READ_THROUGH_WITHOUT_NAMING_A_SPELLING =
          List.of(
              new ACallAnAbsenceIsReadThrough("headers", false),
              new ACallAnAbsenceIsReadThrough("sanitizedHeaders", false),
              new ACallAnAbsenceIsReadThrough("carriedHeaders", false),
              new ACallAnAbsenceIsReadThrough("sentHeaders", false),
              new ACallAnAbsenceIsReadThrough("providerHeaders", false),
              new ACallAnAbsenceIsReadThrough("header", false),
              new ACallAnAbsenceIsReadThrough("contentType", false),
              new ACallAnAbsenceIsReadThrough("headerNamesOf", true));

  public static final List<ACallAnAbsenceIsReadThrough>
      THE_CALLS_THAT_ANSWER_WITH_WHAT_THIS_BUILD_CLAIMS_ABOUT_HEADERS =
          List.of(
              new ACallAnAbsenceIsReadThrough("theAbsencesSpelledOneWayIn", true),
              new ACallAnAbsenceIsReadThrough("theAbsencesClaimedUnderEverySpellingIn", true),
              new ACallAnAbsenceIsReadThrough("theAbsencesOfAKeySpelledOneWayAnywhereIn", true),
              new ACallAnAbsenceIsReadThrough("theAbsencesOfAHeaderReadThroughACallIn", true),
              new ACallAnAbsenceIsReadThrough(
                  "theAbsencesReadThroughACallOfNoDeclaredKindIn", true));

  public static final List<AReaderOfOneNamedHeader>
      THE_READERS_AN_ABSENCE_OF_ONE_NAMED_HEADER_IS_READ_THROUGH =
          List.of(
              new AReaderOfOneNamedHeader(
                  "header",
                  true,
                  "src/test/java/dev/marcinromanowski/pspsim/kernel/smoke/CheckResponseTest.java",
                  "a_header_is_found_whatever_case_it_was_written_in"),
              new AReaderOfOneNamedHeader(
                  "header",
                  true,
                  "src/test/java/dev/marcinromanowski/pspsim/provider/autra/AutraWireRequestTest.java",
                  "a_header_named_in_any_casing_is_found_and_a_blank_one_is_read_as_absent"),
              new AReaderOfOneNamedHeader(
                  "header",
                  true,
                  "src/test/java/dev/marcinromanowski/pspsim/provider/worldline/WorldlineWireRequestTest.java",
                  "a_header_named_in_any_casing_is_found_and_a_blank_one_is_read_as_absent"),
              new AReaderOfOneNamedHeader(
                  "header",
                  true,
                  "src/test/java/dev/marcinromanowski/pspsim/kernel/webhook/LocalMerchantServerTest.java",
                  "a_callback_header_carried_under_another_spelling_is_still_the_one_this_reader"
                      + "_answers_with"),
              new AReaderOfOneNamedHeader(
                  "firstValue",
                  true,
                  "src/test/java/dev/marcinromanowski/pspsim/TheJdkReadsAHeaderUnderEverySpellingTest.java",
                  "a_header_carried_under_another_spelling_is_still_the_first_value_this_reader"
                      + "_answers_with"),
              new AReaderOfOneNamedHeader(
                  "allValues",
                  true,
                  "src/test/java/dev/marcinromanowski/pspsim/TheJdkReadsAHeaderUnderEverySpellingTest.java",
                  "a_header_carried_under_another_spelling_is_still_every_value_this_reader"
                      + "_answers_with"),
              new AReaderOfOneNamedHeader(
                  "getContentType",
                  false,
                  "src/test/java/dev/marcinromanowski/pspsim/SpringReadsAContentTypeUnderEverySpellingTest.java",
                  "a_media_type_carried_under_another_spelling_is_still_the_one_spring_reads_off"
                      + "_an_answer"),
              new AReaderOfOneNamedHeader(
                  "getContentType",
                  false,
                  "src/test/java/dev/marcinromanowski/pspsim/SpringReadsAContentTypeUnderEverySpellingTest.java",
                  "a_media_type_set_under_another_spelling_is_still_the_one_a_mock_answer_reads"
                      + "_back"),
              new AReaderOfOneNamedHeader(
                  "contentTypeSent",
                  false,
                  "src/test/java/dev/marcinromanowski/pspsim/kernel/webhook/WebhookAttemptTest.java",
                  "the_content_type_is_read_whatever_case_the_header_name_was_recorded_under"));

  public static final String THE_RULE_OVER_AN_ABSENCE_READ_THROUGH_A_CALL =
      "No test of this build claims a header is absent by reading it through a call nobody has"
          + " said what the case of a name does to. "
          + TheSpellingsOfAHeaderName.ONE_HEADER_ANSWERS_TO_EVERY_SPELLING_OF_ITS_NAME
          + " An absence written as isNull or isEmpty over a value says nothing about headers in"
          + " its own text, so the subject is again where this is readable, and here it is read"
          + " narrowly: the call the subject ends with, or the call its receiver ends with, is a"
          + " call whose name reads as a header. That leaves out a subject naming a header only"
          + " inside a string, a local name or an argument, which is why a stored column called"
          + " sent_headers and a refusal of a document carried in a header are no claims of this"
          + " rule. Every such call is then one of three kinds, and which kind it is is a fact"
          + " about the call rather than about any claim written over it. A call that answers"
          + " with every header a message carried, or with the one header this build's own"
          + " record holds as a field, names no spelling when it is read: an absence over it is"
          + " the whole collection being empty or the field being unset, which is a stronger"
          + " claim than naming one spelling rather than a weaker one. A call that answers with"
          + " what this build claims about headers rather than with a header names no spelling"
          + " either, which is what this sweep's own readers answer with and why its own result"
          + " assertions are no claims about a wire. A call that answers with the one header a"
          + " claim names folds the case of that name or the claim is not the claim it reads as,"
          + " and that folding is a property of the call, so each such call is named here beside"
          + " a test that proves it answers the same for another spelling. A source sweep reads"
          + " a call's name and not the type it is called on, so four readers named header are"
          + " four entries and a fifth reader of that name would pass unpinned; what this rule"
          + " does catch is an absence read through a name nobody has classified, which is what"
          + " a new reader arrives as.";

  public static final String THE_RULE =
      "No test of this build claims a header is absent from a map of headers by naming one"
          + " spelling of that header's name. "
          + TheSpellingsOfAHeaderName.ONE_HEADER_ANSWERS_TO_EVERY_SPELLING_OF_ITS_NAME
          + " The claim is made the one way, TheSpellingsOfAHeaderName."
          + THE_SHARED_FORM
          + " read by AssertJ's "
          + THE_ASSERTION_THE_SHARED_FORM_IS_READ_BY
          + ", and the AssertJ calls that claim a key is absent by its spelling alone,"
          + " doesNotContainKey and its three siblings, are findings wherever the subject of the"
          + " chain they are written in names headers. The subject is where this rule is"
          + " readable: a map of headers reached through a name that says nothing about headers"
          + " is the one such map neither this sweep nor a gate over the compiled classes can"
          + " tell from a map of body fields, scenario presets or migration columns, each of"
          + " which has a vocabulary its own author chose and spells one consistent way. A"
          + " presence asserted by one spelling is left alone, because a presence claimed of a"
          + " spelling that is not carried fails rather than passing for the wrong reason. This"
          + " sweep reads the source rather than the compiled classes because the subject it"
          + " decides on is a name a reader of the test sees, which survives into bytecode only"
          + " where it happens to be a method name, and it masks every string and text block so"
          + " that a specimen written in the banned shape is read as the specimen it is; a"
          + " comment could hide no claim from it either way, because verifyNoComments leaves"
          + " none anywhere in this tree.";

  private static final Pattern WHERE_AN_ASSERTION_BEGINS = Pattern.compile("\\bassertThat\\s*\\(");

  private static final Pattern WHAT_A_CHAIN_LINK_READS =
      Pattern.compile("\\.\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

  private static final String WHAT_EVERY_MODULE_KEEPS_ITS_SOURCES_IN = "src";

  private static final List<String> WHAT_GIT_IGNORES_OR_THIS_BUILD_GENERATES =
      List.of(
          ".git",
          ".claude",
          ".tmp",
          ".idea",
          ".vscode",
          ".settings",
          ".gradle",
          "build",
          "bin",
          "out");

  private static final Path THIS_REPOSITORY = Path.of("");

  public record AChainLink(String call, String argument) {}

  public record ACallAnAbsenceIsReadThrough(String call, boolean itIsPassedAnArgument) {

    public boolean itsNameReadsAsAHeader() {
      String written = call.toLowerCase(Locale.ROOT);
      for (String read : WHAT_A_CALL_READING_A_HEADER_READS) {
        if (written.contains(read)) {
          return true;
        }
      }
      return false;
    }
  }

  public record AReaderOfOneNamedHeader(
      String call, boolean itIsPassedAnArgument, String pinnedIn, String pinnedBy) {

    public ACallAnAbsenceIsReadThrough asTheCallItIs() {
      return new ACallAnAbsenceIsReadThrough(call, itIsPassedAnArgument);
    }
  }

  public record AnAbsenceReadThroughACall(
      String file, int line, String subject, ACallAnAbsenceIsReadThrough through, String claim) {}

  public record AnAssertion(String file, int line, String subject, List<AChainLink> chain) {

    public boolean itsSubjectNamesAMapOfHeaders() {
      return subject
          .toLowerCase(Locale.ROOT)
          .contains(WHAT_A_SUBJECT_NAMING_A_MAP_OF_HEADERS_READS);
    }
  }

  public record AnAbsenceClaimed(
      String file, int line, String subject, String call, String named) {}

  private HeaderAbsenceConvention() {}

  public static List<AnAssertion> theAssertionsIn(String file, String source) {
    boolean[] insideALiteral = whereTheLiteralsOf(source);
    List<AnAssertion> read = new ArrayList<>();
    Matcher begins = WHERE_AN_ASSERTION_BEGINS.matcher(source);
    while (begins.find()) {
      if (insideALiteral[begins.start()]) {
        continue;
      }
      int opened = begins.end() - 1;
      int closed = whereItCloses(source, insideALiteral, opened);
      if (closed < 0) {
        continue;
      }
      read.add(
          new AnAssertion(
              file,
              whichLineItIsOn(source, begins.start()),
              source.substring(opened + 1, closed),
              theChainAfter(source, insideALiteral, closed)));
    }
    return List.copyOf(read);
  }

  public static List<AnAbsenceClaimed> theAbsencesSpelledOneWayIn(String file, String source) {
    return theAbsencesIn(file, source, THE_ASSERTIONS_THAT_CLAIM_A_KEY_IS_ABSENT_BY_ITS_SPELLING);
  }

  public static List<AnAbsenceClaimed> theAbsencesClaimedUnderEverySpellingIn(
      String file, String source) {
    List<AnAbsenceClaimed> claimed = new ArrayList<>();
    for (AnAbsenceClaimed absence :
        theAbsencesIn(file, source, List.of(THE_ASSERTION_THE_SHARED_FORM_IS_READ_BY))) {
      if (absence.named().contains(THE_SHARED_FORM)) {
        claimed.add(absence);
      }
    }
    return List.copyOf(claimed);
  }

  public static List<AnAbsenceClaimed> theAbsencesOfAKeySpelledOneWayAnywhereIn(
      String file, String source) {
    List<AnAbsenceClaimed> everywhere = new ArrayList<>();
    for (AnAssertion assertion : theAssertionsIn(file, source)) {
      for (AChainLink link : assertion.chain()) {
        if (THE_ASSERTIONS_THAT_CLAIM_A_KEY_IS_ABSENT_BY_ITS_SPELLING.contains(link.call())) {
          everywhere.add(asAnAbsence(assertion, link));
        }
      }
    }
    return List.copyOf(everywhere);
  }

  public static List<AnAbsenceReadThroughACall> theAbsencesOfAHeaderReadThroughACallIn(
      String file, String source) {
    List<AnAbsenceReadThroughACall> read = new ArrayList<>();
    for (AnAssertion assertion : theAssertionsIn(file, source)) {
      String claimed = theFirstClaimThatAValueIsAbsentIn(assertion);
      if (claimed == null) {
        continue;
      }
      List<ACallAnAbsenceIsReadThrough> built = theCallsASubjectIsBuiltFrom(assertion.subject());
      if (built.isEmpty()) {
        continue;
      }
      ACallAnAbsenceIsReadThrough through = built.get(built.size() - 1);
      boolean itsReceiverReadsAHeader =
          built.size() > 1 && built.get(built.size() - 2).itsNameReadsAsAHeader();
      if (!through.itsNameReadsAsAHeader() && !itsReceiverReadsAHeader) {
        continue;
      }
      read.add(
          new AnAbsenceReadThroughACall(
              assertion.file(),
              assertion.line(),
              asOneLine(assertion.subject()),
              through,
              claimed));
    }
    return List.copyOf(read);
  }

  public static List<AnAbsenceReadThroughACall> theAbsencesReadThroughACallOfNoDeclaredKindIn(
      String file, String source) {
    List<AnAbsenceReadThroughACall> ofNoDeclaredKind = new ArrayList<>();
    for (AnAbsenceReadThroughACall read : theAbsencesOfAHeaderReadThroughACallIn(file, source)) {
      if (!theKindOfCallItIsWasDeclared(read.through())) {
        ofNoDeclaredKind.add(read);
      }
    }
    return List.copyOf(ofNoDeclaredKind);
  }

  public static boolean theKindOfCallItIsWasDeclared(ACallAnAbsenceIsReadThrough through) {
    if (THE_CALLS_AN_ABSENCE_IS_READ_THROUGH_WITHOUT_NAMING_A_SPELLING.contains(through)
        || THE_CALLS_THAT_ANSWER_WITH_WHAT_THIS_BUILD_CLAIMS_ABOUT_HEADERS.contains(through)) {
      return true;
    }
    for (AReaderOfOneNamedHeader pinned :
        THE_READERS_AN_ABSENCE_OF_ONE_NAMED_HEADER_IS_READ_THROUGH) {
      if (pinned.asTheCallItIs().equals(through)) {
        return true;
      }
    }
    return false;
  }

  public static boolean theTestNamed(String method, String source) {
    return Pattern.compile("@Test\\s+void\\s+" + Pattern.quote(method) + "\\s*\\(")
        .matcher(source)
        .find();
  }

  public static List<ACallAnAbsenceIsReadThrough> theCallsASubjectIsBuiltFrom(String subject) {
    boolean[] insideALiteral = whereTheLiteralsOf(subject);
    List<ACallAnAbsenceIsReadThrough> built = new ArrayList<>();
    int at = 0;
    while (at < subject.length()) {
      if (insideALiteral[at] || subject.charAt(at) != '(') {
        at++;
        continue;
      }
      int closed = whereItCloses(subject, insideALiteral, at);
      if (closed < 0) {
        return List.copyOf(built);
      }
      String called = theIdentifierEndingAt(subject, at);
      if (called != null) {
        built.add(
            new ACallAnAbsenceIsReadThrough(called, !subject.substring(at + 1, closed).isBlank()));
      }
      at = closed + 1;
    }
    return List.copyOf(built);
  }

  private static @Nullable String theFirstClaimThatAValueIsAbsentIn(AnAssertion assertion) {
    for (AChainLink link : assertion.chain()) {
      if (THE_ASSERTIONS_THAT_CLAIM_A_VALUE_IS_ABSENT.contains(link.call())) {
        return link.call();
      }
    }
    return null;
  }

  private static @Nullable String theIdentifierEndingAt(String subject, int at) {
    int from = at;
    while (from > 0 && Character.isJavaIdentifierPart(subject.charAt(from - 1))) {
      from--;
    }
    return from == at ? null : subject.substring(from, at);
  }

  public static Map<String, List<Path>> everyJavaSourceByTheTreeItIsIn() {
    Map<String, List<Path>> byTheTreeItIsIn = new LinkedHashMap<>();
    for (Path tree : everySourceTreeOfThisRepository()) {
      byTheTreeItIsIn.put(tree.toString(), everyJavaSourceUnder(tree));
    }
    return Map.copyOf(byTheTreeItIsIn);
  }

  public static String read(Path source) {
    try {
      return Files.readString(source);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static List<AnAbsenceClaimed> theAbsencesIn(
      String file, String source, List<String> theCallsItIsClaimedBy) {
    List<AnAbsenceClaimed> claimed = new ArrayList<>();
    for (AnAssertion assertion : theAssertionsIn(file, source)) {
      if (!assertion.itsSubjectNamesAMapOfHeaders()) {
        continue;
      }
      for (AChainLink link : assertion.chain()) {
        if (theCallsItIsClaimedBy.contains(link.call())) {
          claimed.add(asAnAbsence(assertion, link));
        }
      }
    }
    return List.copyOf(claimed);
  }

  private static AnAbsenceClaimed asAnAbsence(AnAssertion assertion, AChainLink link) {
    return new AnAbsenceClaimed(
        assertion.file(),
        assertion.line(),
        asOneLine(assertion.subject()),
        link.call(),
        asOneLine(link.argument()));
  }

  private static String asOneLine(String written) {
    return String.join(" ", written.trim().split("\\s+"));
  }

  private static List<AChainLink> theChainAfter(String source, boolean[] insideALiteral, int from) {
    List<AChainLink> chain = new ArrayList<>();
    Matcher reads = WHAT_A_CHAIN_LINK_READS.matcher(source);
    int depth = 0;
    int at = from + 1;
    while (at < source.length()) {
      if (!insideALiteral[at]) {
        char written = source.charAt(at);
        if (written == '(') {
          depth++;
        } else if (written == ')') {
          depth--;
          if (depth < 0) {
            return List.copyOf(chain);
          }
        } else if (written == ';' && depth == 0) {
          return List.copyOf(chain);
        } else if (written == '.' && depth == 0) {
          reads.region(at, source.length());
          if (reads.lookingAt()) {
            int opened = reads.end() - 1;
            int closed = whereItCloses(source, insideALiteral, opened);
            if (closed < 0) {
              return List.copyOf(chain);
            }
            chain.add(new AChainLink(reads.group(1), source.substring(opened + 1, closed)));
            at = closed;
          }
        }
      }
      at++;
    }
    return List.copyOf(chain);
  }

  private static int whereItCloses(String source, boolean[] insideALiteral, int opened) {
    int depth = 0;
    for (int at = opened; at < source.length(); at++) {
      if (insideALiteral[at]) {
        continue;
      }
      if (source.charAt(at) == '(') {
        depth++;
      } else if (source.charAt(at) == ')') {
        depth--;
        if (depth == 0) {
          return at;
        }
      }
    }
    return -1;
  }

  private static boolean[] whereTheLiteralsOf(String source) {
    boolean[] insideALiteral = new boolean[source.length() + 1];
    int at = 0;
    while (at < source.length()) {
      if (source.startsWith("\"\"\"", at)) {
        int ended = source.indexOf("\"\"\"", at + 3);
        int after = ended < 0 ? source.length() : ended + 3;
        markFrom(insideALiteral, at, after);
        at = after;
      } else if (source.charAt(at) == '"' || source.charAt(at) == '\'') {
        int after = whereTheQuotedTextEnds(source, at);
        markFrom(insideALiteral, at, after);
        at = after;
      } else {
        at++;
      }
    }
    return insideALiteral;
  }

  private static int whereTheQuotedTextEnds(String source, int opened) {
    char quote = source.charAt(opened);
    int at = opened + 1;
    while (at < source.length()) {
      char written = source.charAt(at);
      if (written == '\\') {
        at += 2;
        continue;
      }
      if (written == quote) {
        return at + 1;
      }
      if (written == '\n') {
        return at;
      }
      at++;
    }
    return source.length();
  }

  private static void markFrom(boolean[] insideALiteral, int from, int to) {
    for (int at = from; at < to && at < insideALiteral.length; at++) {
      insideALiteral[at] = true;
    }
  }

  private static int whichLineItIsOn(String source, int at) {
    int line = 1;
    for (int on = 0; on < at; on++) {
      if (source.charAt(on) == '\n') {
        line++;
      }
    }
    return line;
  }

  private static List<Path> everySourceTreeOfThisRepository() {
    try (Stream<Path> walked = Files.walk(THIS_REPOSITORY)) {
      return walked
          .filter(Files::isDirectory)
          .filter(each -> each.getFileName() != null)
          .filter(
              each -> WHAT_EVERY_MODULE_KEEPS_ITS_SOURCES_IN.equals(each.getFileName().toString()))
          .filter(HeaderAbsenceConvention::nothingOnItsWayIsIgnored)
          .sorted()
          .toList();
    } catch (IOException noRepositoryToWalk) {
      throw new UncheckedIOException(noRepositoryToWalk);
    }
  }

  private static boolean nothingOnItsWayIsIgnored(Path each) {
    for (Path segment : each) {
      if (WHAT_GIT_IGNORES_OR_THIS_BUILD_GENERATES.contains(segment.toString())) {
        return false;
      }
    }
    return true;
  }

  private static List<Path> everyJavaSourceUnder(Path tree) {
    try (Stream<Path> walked = Files.walk(tree)) {
      return walked
          .filter(Files::isRegularFile)
          .filter(file -> file.getFileName().toString().endsWith(".java"))
          .sorted()
          .toList();
    } catch (IOException noSourceTreeToRead) {
      throw new UncheckedIOException(noSourceTreeToRead);
    }
  }
}
