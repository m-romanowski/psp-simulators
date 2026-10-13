package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.documentation.HeaderAbsenceConvention.ACallAnAbsenceIsReadThrough;
import dev.marcinromanowski.pspsim.documentation.HeaderAbsenceConvention.AReaderOfOneNamedHeader;
import dev.marcinromanowski.pspsim.documentation.HeaderAbsenceConvention.AnAbsenceClaimed;
import dev.marcinromanowski.pspsim.documentation.HeaderAbsenceConvention.AnAbsenceReadThroughACall;
import dev.marcinromanowski.pspsim.documentation.HeaderAbsenceConvention.AnAssertion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeaderAbsenceConventionTest {

  private static final Path WHERE_THIS_SWEEPS_OWN_SPECIMENS_ARE_WRITTEN =
      Path.of(
          "src",
          "test",
          "java",
          "dev",
          "marcinromanowski",
          "pspsim",
          "documentation",
          "HeaderAbsenceConventionTest.java");

  private static final Path WHERE_THE_SWEEP_ITSELF_IS_WRITTEN =
      Path.of(
          "src",
          "test",
          "java",
          "dev",
          "marcinromanowski",
          "pspsim",
          "documentation",
          "HeaderAbsenceConvention.java");

  private static final int THE_FEWEST_TREES_A_WORKING_WALK_FINDS = 5;

  private static final int THE_FEWEST_SOURCES_A_WORKING_WALK_READS = 1000;

  private static final int THE_FEWEST_ASSERTIONS_A_WORKING_SWEEP_READS = 10000;

  private static final int THE_FEWEST_ABSENCES_OF_A_KEY_A_WORKING_SWEEP_STILL_SEES = 50;

  private static final int THE_FEWEST_HEADER_ABSENCES_THIS_SUITE_CLAIMS = 19;

  private static final int THE_FEWEST_ABSENCES_A_WORKING_SWEEP_STILL_READS_THROUGH_A_CALL = 40;

  private static final int THE_FEWEST_ABSENCES_OF_ANY_VALUE_A_WORKING_SWEEP_STILL_SEES = 1500;

  private static final String THE_TREE_THIS_BUILD_IS_CONFIGURED_IN =
      Path.of("build-logic", "src").toString();

  private static final String A_SPECIMEN = "Specimen.java";

  private static final String AN_ABSENCE_READ_THROUGH_A_CALL_OF_NO_DECLARED_KIND =
      """
      class Specimen {
        void a_replay_is_not_announced() {
          assertThat(reused.headerCalled("Idempotent-Replayed")).isNull();
        }
      }
      """;

  private static final String AN_ABSENCE_READ_THROUGH_EVERY_DECLARED_KIND_OF_CALL =
      """
      class Specimen {
        void nothing_was_carried_and_nothing_named_was_carried_either() {
          assertThat(reused.headers()).isEmpty();
          assertThat(row.sanitizedHeaders()).isEmpty();
          assertThat(attempt.carriedHeaders()).isEmpty();
          assertThat(attempt.sentHeaders()).isNull();
          assertThat(payload.providerHeaders()).isEmpty();
          assertThat(declared.match().header()).isEmpty();
          assertThat(answered.contentType()).isEmpty();
          assertThat(headerNamesOf(page, "aud_1")).isEmpty();
          assertThat(answer.header("Stripe-Signature")).isNull();
          assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
          assertThat(first.headers().allValues("Cache-Control")).isEmpty();
          assertThat(answered.getHeaders().getContentType()).isNull();
          assertThat(attempt.contentTypeSent()).isNull();
        }
      }
      """;

  private static final String AN_ABSENCE_WHOSE_SUBJECT_NAMES_A_HEADER_BUT_READS_NONE =
      """
      class Specimen {
        void nothing_here_reads_a_header_though_the_word_is_written() {
          assertThat(row.get("sent_headers")).isNull();
          assertThat(unmatchedHeader.resolvedTestRun()).isEmpty();
          assertThat(AutraMerchantDocument.theRefusalOf(inTheHeader(DOCUMENT), MERCHANT)).isNull();
          assertThat(StoredAttemptHeaders.theWireOf(null, json)).isNull();
        }
      }
      """;

  private static final String AN_ABSENCE_SPELLED_ONE_WAY =
      """
      class Specimen {
        void a_replay_is_not_announced() {
          assertThat(reused.headers()).doesNotContainKey("Idempotent-Replayed");
        }
      }
      """;

  private static final String AN_ABSENCE_CLAIMED_UNDER_EVERY_SPELLING =
      """
      class Specimen {
        void a_replay_is_not_announced() {
          assertThat(reused.headers())
              .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
        }
      }
      """;

  private static final String AN_ABSENCE_OVER_A_MAP_THAT_IS_NOT_HEADERS =
      """
      class Specimen {
        void a_declined_authorization_names_no_approval() {
          assertThat(state.relatedObjects()).doesNotContainKey("approval");
          assertThat(sentFields()).doesNotContainKey("P_SIGN");
        }
      }
      """;

  private static final String A_CHAIN_WRITTEN_OVER_FOUR_LINES =
      """
      class Specimen {
        void a_whole_answer_ends_no_connection() {
          assertThat(lastAudit().sanitizedHeaders())
              .as("a whole answer ends no connection and says so in the audit")
              .doesNotContainKey(DeliveredHeaders.CONNECTION)
              .containsEntry(DeliveredHeaders.CONTENT_TYPE, List.of(JSON));
        }
      }
      """;

  private static final String A_VALUE_THAT_CARRIES_A_SEMICOLON =
      """
      class Specimen {
        void a_redirect_declares_nothing() {
          assertThat(answer.headers())
              .containsEntry(
                  "Content-Security-Policy",
                  List.of("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'"))
              .doesNotContainKey("Cache-Control");
        }
      }
      """;

  private static final String THE_PLURAL_AND_THE_ENTRY_FORMS =
      """
      class Specimen {
        void a_server_to_server_answer_declares_nothing_a_browser_applies() {
          assertThat(page.headers()).doesNotContainKeys("Content-Security-Policy", "Cache-Control");
          assertThat(page.headers()).doesNotContainEntry("Cache-Control", List.of("no-store"));
        }
      }
      """;

  private static final String A_CLAIM_WRITTEN_INSIDE_A_LAMBDA =
      """
      class Specimen {
        void no_value_of_a_row_reads_as_withheld() {
          assertThat(response.headers())
              .allSatisfy((name, values) -> assertThat(theFieldsOf(name)).doesNotContainKey("x"));
          assertThat(rows)
              .allSatisfy(row -> assertThat(row.sanitizedHeaders()).doesNotContainKey("Connection"));
        }
      }
      """;

  private static final String A_SPECIMEN_WRITTEN_INSIDE_A_STRING =
      """
      class Specimen {
        void the_shape_this_sweep_looks_for_is_written_out_here() {
          String written = "assertThat(reused.headers()).doesNotContainKey(\\"Idempotent-Replayed\\")";
          String alsoWritten =
              \"\"\"
              assertThat(response.headers()).doesNotContainKey("Connection");
              \"\"\";
          assertThat(written).contains(alsoWritten);
        }
      }
      """;

  @Test
  void an_absence_spelled_one_way_over_a_map_whose_subject_names_headers_is_a_finding() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(
                A_SPECIMEN, AN_ABSENCE_SPELLED_ONE_WAY))
        .extracting(
            AnAbsenceClaimed::file,
            AnAbsenceClaimed::line,
            AnAbsenceClaimed::subject,
            AnAbsenceClaimed::call,
            AnAbsenceClaimed::named)
        .containsExactly(
            tuple(
                A_SPECIMEN, 3, "reused.headers()", "doesNotContainKey", "\"Idempotent-Replayed\""));
  }

  @Test
  void an_absence_claimed_under_every_spelling_is_no_finding_and_is_counted_as_the_claim_made() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(
                A_SPECIMEN, AN_ABSENCE_CLAIMED_UNDER_EVERY_SPELLING))
        .as("the shared form is the form this sweep exists to leave alone")
        .isEmpty();
    assertThat(
            HeaderAbsenceConvention.theAbsencesClaimedUnderEverySpellingIn(
                A_SPECIMEN, AN_ABSENCE_CLAIMED_UNDER_EVERY_SPELLING))
        .extracting(AnAbsenceClaimed::subject, AnAbsenceClaimed::call)
        .containsExactly(tuple("reused.headers()", "doesNotHave"));
  }

  @Test
  void an_absence_over_a_map_of_fields_a_provider_spells_itself_is_no_finding() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(
                A_SPECIMEN, AN_ABSENCE_OVER_A_MAP_THAT_IS_NOT_HEADERS))
        .as(
            "a map of body fields, related objects or migration columns is spelled the one way"
                + " its own author chose, which is the author's to choose and not a client's to"
                + " vary, so this sweep is about header maps and reports no other")
        .isEmpty();
    assertThat(
            HeaderAbsenceConvention.theAbsencesOfAKeySpelledOneWayAnywhereIn(
                A_SPECIMEN, AN_ABSENCE_OVER_A_MAP_THAT_IS_NOT_HEADERS))
        .as(
            "the same two claims are read as absences of a key, and only the subject tells them"
                + " from a header map, so a sweep reporting none of these read nothing")
        .hasSize(2);
  }

  @Test
  void a_chain_written_over_four_lines_is_read_to_its_last_link_and_reports_its_first() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(
                A_SPECIMEN, A_CHAIN_WRITTEN_OVER_FOUR_LINES))
        .extracting(AnAbsenceClaimed::line, AnAbsenceClaimed::subject, AnAbsenceClaimed::named)
        .containsExactly(tuple(3, "lastAudit().sanitizedHeaders()", "DeliveredHeaders.CONNECTION"));
  }

  @Test
  void a_semicolon_written_inside_a_value_does_not_end_the_chain_it_is_written_in() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(
                A_SPECIMEN, A_VALUE_THAT_CARRIES_A_SEMICOLON))
        .extracting(AnAbsenceClaimed::named)
        .containsExactly("\"Cache-Control\"");
  }

  @Test
  void the_plural_form_and_the_entry_form_claim_the_same_absence_and_are_read_the_same_way() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(
                A_SPECIMEN, THE_PLURAL_AND_THE_ENTRY_FORMS))
        .extracting(AnAbsenceClaimed::call)
        .containsExactly("doesNotContainKeys", "doesNotContainEntry");
  }

  @Test
  void a_claim_written_inside_a_lambda_is_read_against_the_subject_of_its_own_assertion() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(
                A_SPECIMEN, A_CLAIM_WRITTEN_INSIDE_A_LAMBDA))
        .extracting(AnAbsenceClaimed::line, AnAbsenceClaimed::subject)
        .containsExactly(tuple(6, "row.sanitizedHeaders()"));
  }

  @Test
  void a_specimen_written_inside_a_string_or_a_text_block_is_no_claim_this_sweep_reads() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesOfAKeySpelledOneWayAnywhereIn(
                A_SPECIMEN, A_SPECIMEN_WRITTEN_INSIDE_A_STRING))
        .as(
            "this sweep masks every string and text block, which is how it reads its own"
                + " specimens as specimens; a claim this file writes out in words is not a claim"
                + " this build makes")
        .isEmpty();
    assertThat(
            HeaderAbsenceConvention.theAssertionsIn(A_SPECIMEN, A_SPECIMEN_WRITTEN_INSIDE_A_STRING))
        .as("the one assertion written outside a literal there is still read")
        .extracting(AnAssertion::subject)
        .containsExactly("written");
  }

  @Test
  void every_absence_of_a_header_this_build_asserts_is_claimed_under_every_spelling_of_its_name() {
    Map<String, List<Path>> byTheTreeItIsIn =
        HeaderAbsenceConvention.everyJavaSourceByTheTreeItIsIn();
    List<Path> swept = new ArrayList<>();
    byTheTreeItIsIn.values().forEach(swept::addAll);
    List<AnAssertion> read = new ArrayList<>();
    List<AnAbsenceClaimed> spelledOneWay = new ArrayList<>();
    List<AnAbsenceClaimed> spelledOneWayOverAnyMap = new ArrayList<>();
    List<AnAbsenceClaimed> claimedUnderEverySpelling = new ArrayList<>();
    for (Path source : swept) {
      String file = source.toString();
      String written = HeaderAbsenceConvention.read(source);
      read.addAll(HeaderAbsenceConvention.theAssertionsIn(file, written));
      spelledOneWay.addAll(HeaderAbsenceConvention.theAbsencesSpelledOneWayIn(file, written));
      spelledOneWayOverAnyMap.addAll(
          HeaderAbsenceConvention.theAbsencesOfAKeySpelledOneWayAnywhereIn(file, written));
      claimedUnderEverySpelling.addAll(
          HeaderAbsenceConvention.theAbsencesClaimedUnderEverySpellingIn(file, written));
    }

    assertThat(byTheTreeItIsIn)
        .as(
            "the trees are not listed here but found by walking this repository for directories"
                + " named \"src\", so a module added later is swept without this file being"
                + " touched; finding fewer than the trees this repository has means the walk"
                + " stopped rather than the repository shrank")
        .hasSizeGreaterThanOrEqualTo(THE_FEWEST_TREES_A_WORKING_WALK_FINDS);
    assertThat(theTreesCarryingNoJavaIn(byTheTreeItIsIn))
        .as(
            "the one tree of this repository that carries no Java is the build this project is"
                + " configured in, which is written in Kotlin; any other tree reading as empty is"
                + " a walk that stopped inside it and reports the same zero either way")
        .containsExactly(THE_TREE_THIS_BUILD_IS_CONFIGURED_IN);
    assertThat(swept)
        .as(
            "a walk that reads almost no source has stopped walking rather than found a"
                + " repository with almost none")
        .hasSizeGreaterThanOrEqualTo(THE_FEWEST_SOURCES_A_WORKING_WALK_READS)
        .contains(WHERE_THE_SWEEP_ITSELF_IS_WRITTEN, WHERE_THIS_SWEEPS_OWN_SPECIMENS_ARE_WRITTEN);
    assertThat(read)
        .as(
            "a sweep of "
                + swept.size()
                + " sources that reads almost no assertion has stopped reading rather than found"
                + " a suite that asserts almost nothing")
        .hasSizeGreaterThanOrEqualTo(THE_FEWEST_ASSERTIONS_A_WORKING_SWEEP_READS);
    assertThat(spelledOneWayOverAnyMap)
        .as(
            "the call this rule bans over a header map is the call this suite writes over every"
                + " other kind of map, so a sweep that finds none of those cannot tell a clean"
                + " tree from a matcher that has stopped matching")
        .hasSizeGreaterThanOrEqualTo(THE_FEWEST_ABSENCES_OF_A_KEY_A_WORKING_SWEEP_STILL_SEES);
    assertThat(claimedUnderEverySpelling)
        .as(
            "every absence of a header this suite claims is claimed through the one shared form,"
                + " so this is the count of those claims and it is read here as a floor: a site"
                + " that stops claiming it lowers this number, and lowering it is a decision"
                + " someone takes here rather than one a sweep passes over")
        .hasSizeGreaterThanOrEqualTo(THE_FEWEST_HEADER_ABSENCES_THIS_SUITE_CLAIMS);
    assertThat(spelledOneWay)
        .as(
            HeaderAbsenceConvention.THE_RULE
                + " Each finding below is the file, the line, the subject the chain is written"
                + " over, the call that claims the absence and the single spelling it names.")
        .isEmpty();
  }

  @Test
  void an_absence_read_through_a_call_of_no_declared_kind_is_a_finding() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesReadThroughACallOfNoDeclaredKindIn(
                A_SPECIMEN, AN_ABSENCE_READ_THROUGH_A_CALL_OF_NO_DECLARED_KIND))
        .extracting(
            AnAbsenceReadThroughACall::line,
            AnAbsenceReadThroughACall::subject,
            AnAbsenceReadThroughACall::claim)
        .as(
            "headerCalled is the shape a new reader of one named header arrives as, and nobody"
                + " has said what the case of a name does to it")
        .containsExactly(tuple(3, "reused.headerCalled(\"Idempotent-Replayed\")", "isNull"));
  }

  @Test
  void an_absence_read_through_a_call_of_either_declared_kind_is_no_finding_and_is_read_as_one() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesReadThroughACallOfNoDeclaredKindIn(
                A_SPECIMEN, AN_ABSENCE_READ_THROUGH_EVERY_DECLARED_KIND_OF_CALL))
        .as("every call this specimen reads through is one of the two kinds this rule declares")
        .isEmpty();
    assertThat(
            HeaderAbsenceConvention.theAbsencesOfAHeaderReadThroughACallIn(
                A_SPECIMEN, AN_ABSENCE_READ_THROUGH_EVERY_DECLARED_KIND_OF_CALL))
        .as(
            "a sweep reporting no finding over this specimen has either classified all thirteen"
                + " or read none of them, and only the count tells the two apart")
        .hasSize(13);
  }

  @Test
  void an_absence_whose_subject_names_a_header_without_reading_one_is_no_claim_of_this_rule() {
    assertThat(
            HeaderAbsenceConvention.theAbsencesOfAHeaderReadThroughACallIn(
                A_SPECIMEN, AN_ABSENCE_WHOSE_SUBJECT_NAMES_A_HEADER_BUT_READS_NONE))
        .as(
            "a stored column spelled sent_headers, a local name carrying the word, a header"
                + " named inside an argument and a reader of a stored row are each a subject"
                + " whose own last call and whose receiver's last call read no header")
        .isEmpty();
    assertThat(
            HeaderAbsenceConvention.theAssertionsIn(
                A_SPECIMEN, AN_ABSENCE_WHOSE_SUBJECT_NAMES_A_HEADER_BUT_READS_NONE))
        .as("all four are still read as assertions, so the four zeroes above are decisions")
        .hasSize(4);
  }

  @Test
  void the_call_a_subject_is_built_from_is_the_last_one_it_names_at_the_depth_it_is_written_at() {
    assertThat(
            HeaderAbsenceConvention.theCallsASubjectIsBuiltFrom(
                "new CheckResponse(200, Map.of(), \"{}\").header(\"Stripe-Signature\")"))
        .extracting(
            ACallAnAbsenceIsReadThrough::call, ACallAnAbsenceIsReadThrough::itIsPassedAnArgument)
        .containsExactly(tuple("CheckResponse", true), tuple("header", true));
    assertThat(
            HeaderAbsenceConvention.theCallsASubjectIsBuiltFrom(
                "AutraMerchantDocument.theRefusalOf(inTheHeader(DOCUMENT), MERCHANT)"))
        .as("a call written inside an argument is not a call the subject is built from")
        .extracting(ACallAnAbsenceIsReadThrough::call)
        .containsExactly("theRefusalOf");
    assertThat(
            HeaderAbsenceConvention.theCallsASubjectIsBuiltFrom(
                "((ScenarioForm.Read.Declared) read).match().header()"))
        .as("a cast opens a group that names no call, and the calls after it are still read")
        .extracting(ACallAnAbsenceIsReadThrough::call)
        .containsExactly("match", "header");
    assertThat(HeaderAbsenceConvention.theCallsASubjectIsBuiltFrom("headers"))
        .as("a subject that names no call is a subject this rule reads nothing out of")
        .isEmpty();
  }

  @Test
  void every_reader_of_one_named_header_this_rule_rests_on_is_pinned_by_a_test_that_exists() {
    for (AReaderOfOneNamedHeader pinned :
        HeaderAbsenceConvention.THE_READERS_AN_ABSENCE_OF_ONE_NAMED_HEADER_IS_READ_THROUGH) {
      String written = HeaderAbsenceConvention.read(Path.of(pinned.pinnedIn()));

      assertThat(HeaderAbsenceConvention.theTestNamed(pinned.pinnedBy(), written))
          .as(
              "%s is the call an absence of one named header is read through and %s is named here"
                  + " as the test that proves it answers the same for another spelling of that"
                  + " name, so a register entry pointing at a test that is not in %s is a"
                  + " register that has stopped describing this build",
              pinned.call(), pinned.pinnedBy(), pinned.pinnedIn())
          .isTrue();
    }
  }

  @Test
  void every_absence_of_a_header_this_build_reads_through_a_call_reads_through_a_declared_one() {
    List<Path> swept = new ArrayList<>();
    HeaderAbsenceConvention.everyJavaSourceByTheTreeItIsIn().values().forEach(swept::addAll);
    List<AnAbsenceReadThroughACall> readThroughACall = new ArrayList<>();
    List<AnAbsenceReadThroughACall> ofNoDeclaredKind = new ArrayList<>();
    int absencesOfAnyValue = 0;
    for (Path source : swept) {
      String file = source.toString();
      String written = HeaderAbsenceConvention.read(source);
      readThroughACall.addAll(
          HeaderAbsenceConvention.theAbsencesOfAHeaderReadThroughACallIn(file, written));
      ofNoDeclaredKind.addAll(
          HeaderAbsenceConvention.theAbsencesReadThroughACallOfNoDeclaredKindIn(file, written));
      absencesOfAnyValue += theAbsencesOfAnyValueIn(file, written);
    }

    assertThat(absencesOfAnyValue)
        .as(
            "the calls this rule reads are a handful of the ones this suite claims a value absent"
                + " over, so a sweep that finds almost none of those has stopped matching rather"
                + " than found a suite that claims almost nothing absent")
        .isGreaterThanOrEqualTo(THE_FEWEST_ABSENCES_OF_ANY_VALUE_A_WORKING_SWEEP_STILL_SEES);
    assertThat(readThroughACall)
        .as(
            "an absence of a header read through a call is the shape this rule is about, and a"
                + " sweep of %d sources finding almost none of them has stopped reading subjects"
                + " rather than found a suite that reads almost no header",
            swept.size())
        .hasSizeGreaterThanOrEqualTo(
            THE_FEWEST_ABSENCES_A_WORKING_SWEEP_STILL_READS_THROUGH_A_CALL);
    assertThat(theCallsEveryKindIsDeclaredFor(readThroughACall))
        .as(
            "each call below is declared as one an absence names no spelling through or as a"
                + " reader of one named header this build pins, and the declarations are read"
                + " back against the build here so that neither list outlives what it describes")
        .isNotEmpty();
    assertThat(ofNoDeclaredKind)
        .as(
            HeaderAbsenceConvention.THE_RULE_OVER_AN_ABSENCE_READ_THROUGH_A_CALL
                + " Each finding below is the file, the line, the subject the chain is written"
                + " over, the call it is read through and the claim made of it.")
        .isEmpty();
  }

  private static int theAbsencesOfAnyValueIn(String file, String written) {
    int claimed = 0;
    for (AnAssertion assertion : HeaderAbsenceConvention.theAssertionsIn(file, written)) {
      for (HeaderAbsenceConvention.AChainLink link : assertion.chain()) {
        if (HeaderAbsenceConvention.THE_ASSERTIONS_THAT_CLAIM_A_VALUE_IS_ABSENT.contains(
            link.call())) {
          claimed++;
          break;
        }
      }
    }
    return claimed;
  }

  private static List<ACallAnAbsenceIsReadThrough> theCallsEveryKindIsDeclaredFor(
      List<AnAbsenceReadThroughACall> readThroughACall) {
    List<ACallAnAbsenceIsReadThrough> declared = new ArrayList<>();
    for (AnAbsenceReadThroughACall read : readThroughACall) {
      if (!declared.contains(read.through())) {
        declared.add(read.through());
      }
    }
    return List.copyOf(declared);
  }

  private static List<String> theTreesCarryingNoJavaIn(Map<String, List<Path>> byTheTreeItIsIn) {
    List<String> carryingNone = new ArrayList<>();
    byTheTreeItIsIn.forEach(
        (tree, sources) -> {
          if (sources.isEmpty()) {
            carryingNone.add(tree);
          }
        });
    return List.copyOf(carryingNone);
  }

  @Test
  void the_specimens_this_sweep_is_read_back_against_are_the_only_ones_in_its_own_two_sources() {
    for (Path source :
        List.of(WHERE_THIS_SWEEPS_OWN_SPECIMENS_ARE_WRITTEN, WHERE_THE_SWEEP_ITSELF_IS_WRITTEN)) {
      String written = HeaderAbsenceConvention.read(source);

      assertThat(
              HeaderAbsenceConvention.theAbsencesOfAKeySpelledOneWayAnywhereIn(
                  source.toString(), written))
          .as(
              "%s is swept like every other source and contributes no finding of its own, because"
                  + " every shape it names is written there inside a string, a text block or a"
                  + " list of call names; the specimen tests of this class are what prove the"
                  + " shape is read at all",
              source)
          .isEmpty();
    }
  }
}
