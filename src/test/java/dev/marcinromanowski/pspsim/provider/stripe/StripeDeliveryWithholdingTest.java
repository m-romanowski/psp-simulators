package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripeDeliveryWithholdingTest {

  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final String ACCOUNT = "merchant-1";

  private static final String AN_INTENT = "pi_sim_0000000000000001";

  private static final String A_REFUND = "re_sim_0000000000000001";

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String THE_CLIENT_SECRET = AN_INTENT + "_secret_0000000000000000000001";

  private static final String AN_EPHEMERAL_KEY_SECRET = "ek_sim_00000000000000000000000000000001";

  private static final String THE_ONE_PATH_A_DELIVERY_WITHHOLDS = "data.object.client_secret";

  private static final String A_DEEPER_PATH = "data.object.latest_charge.client_secret";

  private static final String A_PATH_INSIDE_AN_ARRAY = "data.object.associated_objects.secret";

  @Test
  void
      no_event_this_build_delivers_keys_a_withheld_name_under_any_path_but_the_one_its_delivery_declaration_names() {
    List<StripeJson> dataObjects = everyObjectAnEventOfThisBuildReports();
    Set<String> keysRead = new TreeSet<>();
    Set<String> keysWhoseLeafThisModuleWithholds = new TreeSet<>();
    Set<String> valuesThatReachedTheTrailInTheClear = new TreeSet<>();
    int bodiesRead = 0;

    for (StripeJson dataObject : dataObjects) {
      for (StripeEventType reporting : StripeEventType.values()) {
        Map<String, List<String>> flattened =
            StripeEvent.of("evt_sim_0000000000000001", reporting, dataObject, NOW).flatten();
        Map<String, List<String>> theRowATesterReads =
            Redaction.redact(flattened, StripeFields.SENSITIVE_DELIVERY_FIELDS);
        bodiesRead++;
        keysRead.addAll(flattened.keySet());
        for (String key : flattened.keySet()) {
          if (!StripeFields.SENSITIVE_RESPONSE_FIELDS.contains(StripeFields.theLeafOf(key))) {
            continue;
          }
          keysWhoseLeafThisModuleWithholds.add(key);
          if (theRowATesterReads.get(key).contains(THE_CLIENT_SECRET)) {
            valuesThatReachedTheTrailInTheClear.add(key);
          }
        }
      }
    }

    assertThat(bodiesRead)
        .as(
            "the denominator of this sweep: every object the two subjects of "
                + StripeEventType.thePublishedNames()
                + " report, over every capture method, every status, every decline, every"
                + " cancellation reason, a scheduled outcome landed and not landed, and every"
                + " nullable member of the view filled and empty, each reported under every"
                + " published event type")
        .isEqualTo(dataObjects.size() * StripeEventType.values().length);
    assertThat(keysRead)
        .as("the sweep reached the member the one declared path names, so it is no empty sweep")
        .contains(THE_ONE_PATH_A_DELIVERY_WITHHOLDS);
    assertThat(keysWhoseLeafThisModuleWithholds)
        .as(
            "read of "
                + bodiesRead
                + " flattened event bodies carrying "
                + keysRead.size()
                + " distinct keys between them: the delivery declaration of this module names one"
                + " whole path, and this is the measurement that nothing it delivers keys a"
                + " client_secret or a secret under any other path, so the one path it names"
                + " covers every name it withholds")
        .containsExactly(THE_ONE_PATH_A_DELIVERY_WITHHOLDS);
    assertThat(valuesThatReachedTheTrailInTheClear)
        .as("no value of a withheld name survives into the row a tester reads")
        .isEmpty();
  }

  @Test
  void
      the_delivery_declaration_meets_one_whole_path_and_no_other_path_the_same_name_could_sit_at() {
    Map<String, List<String>> deeperThanTheDeclaredPath = new LinkedHashMap<>();
    deeperThanTheDeclaredPath.put(THE_ONE_PATH_A_DELIVERY_WITHHOLDS, List.of(THE_CLIENT_SECRET));
    deeperThanTheDeclaredPath.put(A_DEEPER_PATH, List.of(THE_CLIENT_SECRET));
    deeperThanTheDeclaredPath.put(A_PATH_INSIDE_AN_ARRAY, List.of(AN_EPHEMERAL_KEY_SECRET));
    deeperThanTheDeclaredPath.put(StripeFields.SECRET, List.of(AN_EPHEMERAL_KEY_SECRET));

    Map<String, List<String>> theRowATesterReads =
        Redaction.redact(deeperThanTheDeclaredPath, StripeFields.SENSITIVE_DELIVERY_FIELDS);

    assertThat(theRowATesterReads)
        .as(
            "the limit the sweep above makes safe: this module hands its delivery declaration to"
                + " a redactor that matches a whole key, so the one path it declares is met at"
                + " that path alone, and the same name at another depth or on its own would be"
                + " delivered in the clear. No event of this build keys one there, and the sweep"
                + " is what holds that open")
        .containsEntry(THE_ONE_PATH_A_DELIVERY_WITHHOLDS, List.of(Redaction.REDACTED))
        .containsEntry(A_DEEPER_PATH, List.of(THE_CLIENT_SECRET))
        .containsEntry(A_PATH_INSIDE_AN_ARRAY, List.of(AN_EPHEMERAL_KEY_SECRET))
        .containsEntry(StripeFields.SECRET, List.of(AN_EPHEMERAL_KEY_SECRET));
  }

  @Test
  void every_path_a_delivery_of_this_module_withholds_is_a_name_its_responses_withhold_as_well() {
    for (String declared : StripeFields.SENSITIVE_DELIVERY_FIELDS) {
      assertThat(StripeFields.SENSITIVE_RESPONSE_FIELDS)
          .as(
              "the two directions of this module are matched differently, so a path declared"
                  + " delivered-withheld whose leaf this module does not withhold from a response"
                  + " would be a name only one direction calls a secret: %s",
              declared)
          .contains(StripeFields.theLeafOf(declared));
    }
  }

  private static List<StripeJson> everyObjectAnEventOfThisBuildReports() {
    List<StripeJson> reported = new ArrayList<>();
    for (StripeCaptureMethod method : StripeCaptureMethod.values()) {
      for (StripeIntentStatus status : StripeIntentStatus.values()) {
        for (StripeDecline lastPaymentError : everyDeclineAndNone()) {
          for (StripeCancellationReason reason : everyCancellationReasonAndNone()) {
            for (boolean filled : new boolean[] {false, true}) {
              for (StripeDecline settlesWith : everyDeclineAndNone()) {
                for (Instant settlesAt : new Instant[] {null, NOW.minusSeconds(1)}) {
                  reported.add(
                      StripeIntentView.of(
                          anIntent(
                              method,
                              status,
                              lastPaymentError,
                              reason,
                              filled,
                              settlesAt,
                              settlesWith),
                          NOW));
                }
              }
            }
          }
        }
      }
    }
    for (StripeRefundStatus status : StripeRefundStatus.values()) {
      for (StripeRefundReason reason : everyRefundReasonAndNone()) {
        for (boolean charged : new boolean[] {false, true}) {
          reported.add(StripeRefundView.of(aRefund(status, reason, charged)));
        }
      }
    }
    return List.copyOf(reported);
  }

  private static List<@Nullable StripeDecline> everyDeclineAndNone() {
    List<@Nullable StripeDecline> all = new ArrayList<>(List.of(StripeDecline.values()));
    all.add(null);
    return all;
  }

  private static List<@Nullable StripeCancellationReason> everyCancellationReasonAndNone() {
    List<@Nullable StripeCancellationReason> all =
        new ArrayList<>(List.of(StripeCancellationReason.values()));
    all.add(null);
    return all;
  }

  private static List<@Nullable StripeRefundReason> everyRefundReasonAndNone() {
    List<@Nullable StripeRefundReason> all = new ArrayList<>(List.of(StripeRefundReason.values()));
    all.add(null);
    return all;
  }

  private static StripePaymentIntent anIntent(
      StripeCaptureMethod method,
      StripeIntentStatus status,
      @Nullable StripeDecline lastPaymentError,
      @Nullable StripeCancellationReason reason,
      boolean filled,
      @Nullable Instant settlesAt,
      @Nullable StripeDecline settlesWith) {
    return new StripePaymentIntent(
        E2E_RUN,
        AN_INTENT,
        ACCOUNT,
        Amount.of("1099"),
        "pln",
        method,
        status,
        filled ? "pm_card_visa" : null,
        filled ? "cus_sim_1" : null,
        filled ? "payer@example.test" : null,
        THE_CLIENT_SECRET,
        filled ? "ch_sim_0000000000000001" : null,
        lastPaymentError,
        reason,
        NOW.plusSeconds(600),
        settlesAt,
        settlesWith,
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static StripeRefund aRefund(
      StripeRefundStatus status, @Nullable StripeRefundReason reason, boolean charged) {
    return new StripeRefund(
        E2E_RUN,
        A_REFUND,
        AN_INTENT,
        charged ? "ch_sim_0000000000000001" : null,
        ACCOUNT,
        Amount.of("1099"),
        "pln",
        reason,
        status,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }
}
