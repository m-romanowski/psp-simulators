package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.spi.BundleAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunBundleScreenTest {

  private static final String A_SECRET_A_TESTER_TYPED = "sk_test_typedIntoTheBox";

  private static final String A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK = "apiSecret";

  private static final ProviderAccountFields THE_FIELDS_THIS_PROVIDER_DECLARES =
      new ProviderAccountFields(
          ProviderId.of("twokeys"),
          List.of(
              new ConfigurableField(
                  "secretKey",
                  "the key",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              ConfigurableField.aBlockOfManyLines(
                  A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK,
                  "the gateway key",
                  "a sentence a test does not read",
                  true,
                  true,
                  "-----BEGIN PRIVATE KEY-----",
                  List.of("-----END PRIVATE KEY-----"))));

  private static final RunBundle A_BUNDLE =
      new RunBundle(null, "run-a", "stripe", null, null, null, null, null, null, null, null);

  @Test
  void an_empty_box_is_unreadable_rather_than_read_as_a_bundle_carrying_nothing() {
    assertThat(RunBundleScreen.pastedInto(null, document -> A_BUNDLE))
        .isInstanceOfSatisfying(
            RunBundleScreen.Pasted.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .isEqualTo(RunBundleScreen.A_BUNDLE_IS_WHAT_THIS_BOX_TAKES));
    assertThat(RunBundleScreen.pastedInto("   ", document -> A_BUNDLE))
        .isInstanceOf(RunBundleScreen.Pasted.Unreadable.class);
  }

  @Test
  void a_box_the_reader_cannot_parse_is_unreadable_and_says_so_without_the_text_it_held() {
    assertThat(
            RunBundleScreen.pastedInto(
                "{not json " + A_SECRET_A_TESTER_TYPED,
                document -> {
                  throw new IllegalStateException("no");
                }))
        .isInstanceOfSatisfying(
            RunBundleScreen.Pasted.Unreadable.class,
            unreadable -> assertThat(unreadable.message()).doesNotContain(A_SECRET_A_TESTER_TYPED));
  }

  @Test
  void a_box_the_reader_parses_is_the_bundle_it_produced() {
    assertThat(RunBundleScreen.pastedInto("{}", document -> A_BUNDLE))
        .isEqualTo(new RunBundleScreen.Pasted.Bundle(A_BUNDLE));
  }

  @Test
  void a_box_with_nothing_typed_in_it_supplies_no_secret_at_all() {
    assertThat(RunBundleScreen.theSecretsTypedInto(null, THE_FIELDS_THIS_PROVIDER_DECLARES))
        .isEqualTo(new RunBundleScreen.Supplied.Secrets(Map.of()));
    assertThat(RunBundleScreen.theSecretsTypedInto("  \n \n", THE_FIELDS_THIS_PROVIDER_DECLARES))
        .isEqualTo(new RunBundleScreen.Supplied.Secrets(Map.of()));
  }

  @Test
  void one_line_per_field_supplies_one_value_to_the_account_that_line_names() {
    RunBundleScreen.Supplied supplied =
        RunBundleScreen.theSecretsTypedInto(
            "merchant-1.secretKey=" + A_SECRET_A_TESTER_TYPED + "\nmerchant-2.secretKey=other",
            THE_FIELDS_THIS_PROVIDER_DECLARES);

    assertThat(supplied)
        .isEqualTo(
            new RunBundleScreen.Supplied.Secrets(
                Map.of(
                    "merchant-1",
                    Map.of("secretKey", A_SECRET_A_TESTER_TYPED),
                    "merchant-2",
                    Map.of("secretKey", "other"))));
  }

  @Test
  void two_fields_of_one_account_are_supplied_together() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1.secretKey=a\nmerchant-1.webhook.signing.secret=b",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .isEqualTo(
            new RunBundleScreen.Supplied.Secrets(
                Map.of("merchant-1", Map.of("secretKey", "a", "webhook.signing.secret", "b"))));
  }

  @Test
  void the_box_takes_a_block_only_where_the_provider_drawing_it_declares_one() {
    assertThat(
            RunBundleScreen.theSecretsBoxTakesABlockOfManyLines(THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as("this provider draws one of its two fields as a block of many lines")
        .isTrue();
    assertThat(
            RunBundleScreen.theSecretsBoxTakesABlockOfManyLines(
                new ProviderAccountFields(
                    ProviderId.of("onekey"),
                    List.of(
                        new ConfigurableField(
                            "secretKey",
                            "the key",
                            "a sentence a test does not read",
                            true,
                            true,
                            List.of(),
                            "a value a test does not read")))))
        .as(
            "a provider all of whose fields are one line long must not have its tester offered a"
                + " value that runs on, because no field of it would take one")
        .isFalse();
  }

  @Test
  void a_key_its_provider_takes_as_a_block_keeps_every_line_a_tester_pasted_under_it() {
    String aKeyAsItComesOutOfAFile =
        "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAg==\n"
            + "-----END PRIVATE KEY-----";

    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1." + A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK + "=" + aKeyAsItComesOutOfAFile,
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "a tester pastes a key the way it comes out of a file, and this provider draws that"
                + " field as a block of many lines, so the lines under the first one are the rest"
                + " of that one value and not lines this box could not read. The second of them"
                + " ends in the padding of its own base64, which is the character this box splits"
                + " a line on, so a box reading it as a new secret would address an account"
                + " nobody named")
        .isEqualTo(
            new RunBundleScreen.Supplied.Secrets(
                Map.of(
                    "merchant-1",
                    Map.of(A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK, aKeyAsItComesOutOfAFile))));
  }

  @Test
  void a_line_under_a_block_that_has_already_ended_is_refused_and_never_welded_onto_the_key() {
    String aKeyAsItComesOutOfAFile =
        "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAg==\n"
            + "-----END PRIVATE KEY-----";

    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1."
                    + A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK
                    + "="
                    + aKeyAsItComesOutOfAFile
                    + "\nmerchant-1_secretKey=sk_test_a_dot_mistyped_as_an_underscore",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "the block ended on the line its provider names it ends with, so the line under it is"
                + " a line this box cannot read rather than more of the key: welding it on would"
                + " store a tester's second secret inside their first one and report success")
        .isInstanceOfSatisfying(
            RunBundleScreen.Supplied.Unreadable.class,
            unreadable -> assertThat(unreadable.message()).contains("line 4"));
  }

  @Test
  void a_block_a_tester_flattened_onto_one_line_is_taken_as_it_stands() {
    String flattened =
        "-----BEGIN PRIVATE KEY-----MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAg==-----END"
            + " PRIVATE KEY-----";

    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1." + A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK + "=" + flattened,
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "a key whose own last line is already inside the one line it was pasted on has"
                + " reached its end, and this provider's reader takes a key with no line breaks,"
                + " so the box has nothing left to wait for")
        .isEqualTo(
            new RunBundleScreen.Supplied.Secrets(
                Map.of("merchant-1", Map.of(A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK, flattened))));
  }

  @Test
  void a_one_line_value_for_a_block_is_left_to_the_provider_rather_than_held_open_by_this_box() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1." + A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK + "=not a key at all",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "nothing ran on under this line, so the box has read one whole value and the"
                + " provider is the one that says whether it is a key")
        .isEqualTo(
            new RunBundleScreen.Supplied.Secrets(
                Map.of(
                    "merchant-1",
                    Map.of(A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK, "not a key at all"))));
  }

  @Test
  void a_block_that_never_reaches_the_line_it_ends_with_is_refused_by_the_line_it_opened_on() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1.secretKey=sk_test_one\nmerchant-1."
                    + A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK
                    + "=-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAg==",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "a key pasted without its last line would otherwise swallow every line under it to"
                + " the end of the box, so the refusal names the line the block opened on and the"
                + " line it was waiting for")
        .isInstanceOfSatisfying(
            RunBundleScreen.Supplied.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .contains("line 2")
                    .contains("-----END PRIVATE KEY-----"));
  }

  @Test
  void a_line_this_box_cannot_read_under_a_key_that_is_no_block_says_where_the_mistake_may_be() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1.apisecret=-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg==",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "the field on the first line is misspelled, so this provider draws no block for it"
                + " and the second line is refused: a tester reading only the number of the"
                + " second line would go looking for a mistake that is on the first")
        .isInstanceOfSatisfying(
            RunBundleScreen.Supplied.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .contains("line 2")
                    .contains("apisecret")
                    .contains("not one this provider takes as a block of many lines"));
  }

  @Test
  void a_space_around_the_equals_sign_of_an_address_is_read_as_the_field_it_names() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1.secretKey = sk_test_typed_with_spaces",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as("a tester who lines their secrets up with spaces has named the field all the same")
        .isEqualTo(
            new RunBundleScreen.Supplied.Secrets(
                Map.of("merchant-1", Map.of("secretKey", " sk_test_typed_with_spaces"))));
  }

  @Test
  void a_key_no_provider_draws_as_a_block_is_refused_by_the_number_of_the_line_that_ran_on() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1.secretKey=sk_test_one\nsk_test_two", THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "nothing but a block its provider declares runs on, so a second line under a plain"
                + " key is the typo it looks like rather than half of a value")
        .isInstanceOf(RunBundleScreen.Supplied.Unreadable.class);
  }

  @Test
  void a_line_naming_another_secret_under_a_block_is_part_of_the_block_until_it_ends() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1."
                    + A_KEY_ITS_PROVIDER_TAKES_AS_A_BLOCK
                    + "=-----BEGIN PRIVATE KEY-----\nmerchant-2.secretKey=sk_test_two",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .as(
            "this box read the second line as a new secret until a block was made to end on the"
                + " line its provider names, which is what a key's own base64 lines need; the"
                + " block that never ended is now refused rather than cut short wherever a line"
                + " happened to look like an address")
        .isInstanceOfSatisfying(
            RunBundleScreen.Supplied.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message())
                    .contains("line 1")
                    .contains("-----END PRIVATE KEY-----"));
  }

  @Test
  void a_value_carrying_an_equals_sign_keeps_every_character_after_the_first_one() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1.secretKey=a=b=c", THE_FIELDS_THIS_PROVIDER_DECLARES))
        .isEqualTo(
            new RunBundleScreen.Supplied.Secrets(
                Map.of("merchant-1", Map.of("secretKey", "a=b=c"))));
  }

  @Test
  void the_shortest_line_that_names_an_account_a_field_and_a_value_is_read_as_all_three() {
    assertThat(RunBundleScreen.theSecretsTypedInto("m.k=v", THE_FIELDS_THIS_PROVIDER_DECLARES))
        .isEqualTo(new RunBundleScreen.Supplied.Secrets(Map.of("m", Map.of("k", "v"))));
  }

  @Test
  void a_dot_that_falls_after_the_equals_sign_names_no_field_and_the_line_is_refused() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant=secret.key", THE_FIELDS_THIS_PROVIDER_DECLARES))
        .isInstanceOf(RunBundleScreen.Supplied.Unreadable.class);
  }

  @Test
  void a_line_naming_no_account_no_field_or_no_value_is_refused_by_its_number_and_never_its_text() {
    for (String line :
        List.of(
            "=" + A_SECRET_A_TESTER_TYPED,
            "secretKey=" + A_SECRET_A_TESTER_TYPED,
            "merchant-1.secretKey",
            "merchant-1.=" + A_SECRET_A_TESTER_TYPED,
            ".secretKey=" + A_SECRET_A_TESTER_TYPED,
            "merchant-1.secretKey=")) {
      assertThat(RunBundleScreen.theSecretsTypedInto(line, THE_FIELDS_THIS_PROVIDER_DECLARES))
          .isInstanceOfSatisfying(
              RunBundleScreen.Supplied.Unreadable.class,
              unreadable -> {
                assertThat(unreadable.message()).startsWith("secrets line 1: ");
                assertThat(unreadable.message())
                    .contains(RunBundleScreen.A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE);
                assertThat(unreadable.message()).doesNotContain(A_SECRET_A_TESTER_TYPED);
              });
    }
  }

  @Test
  void the_refusal_of_a_later_line_names_that_line_and_not_the_first_one() {
    assertThat(
            RunBundleScreen.theSecretsTypedInto(
                "merchant-1.secretKey=a\n\nmerchant-2.secretKey\n",
                THE_FIELDS_THIS_PROVIDER_DECLARES))
        .isInstanceOfSatisfying(
            RunBundleScreen.Supplied.Unreadable.class,
            unreadable -> assertThat(unreadable.message()).startsWith("secrets line 3: "));
  }

  @Test
  void the_names_of_what_was_typed_are_the_account_and_the_field_and_never_the_value() {
    RunBundleScreen.Supplied supplied =
        RunBundleScreen.theSecretsTypedInto(
            "merchant-1.secretKey=" + A_SECRET_A_TESTER_TYPED, THE_FIELDS_THIS_PROVIDER_DECLARES);

    assertThat(RunBundleScreen.theSecretNamesOf(supplied)).containsExactly("merchant-1.secretKey");
  }

  @Test
  void a_box_that_could_not_be_read_names_no_secret_at_all() {
    assertThat(
            RunBundleScreen.theSecretNamesOf(
                RunBundleScreen.theSecretsTypedInto("nonsense", THE_FIELDS_THIS_PROVIDER_DECLARES)))
        .isEmpty();
  }

  @Test
  void the_refusal_a_screen_files_names_the_bundle_it_did_not_take_back() {
    assertThat(RunBundleScreen.theRefusalOfABundleItDidNotTakeBack().subject())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                BundleAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
    assertThat(RunBundleScreen.theRefusalOfABundleItDidNotTakeBack().httpStatus()).isEqualTo(422);
  }
}
