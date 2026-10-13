package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ListPayments {

  static final int SMALLEST_PAGE = 1;

  static final int THE_PAGE_A_CALL_THAT_NAMES_NONE_GETS = 1;

  static final int SMALLEST_LIMIT = 1;

  static final int LARGEST_LIMIT = 200;

  static final int THE_LIMIT_A_CALL_THAT_NAMES_NONE_GETS = 50;

  static final String A_PAGE_STARTS_AT_ONE =
      "page is the page of the listing to answer, counted from "
          + SMALLEST_PAGE
          + "; provider autra publishes no upper bound for it and a page past the end answers an"
          + " empty items with hasMore false";

  static final String A_LIMIT_IS_ONE_TO_TWO_HUNDRED =
      "limit is how many payments one page carries, from "
          + SMALLEST_LIMIT
          + " to "
          + LARGEST_LIMIT
          + ", and defaults to "
          + THE_LIMIT_A_CALL_THAT_NAMES_NONE_GETS;

  static final String A_TYPE_IS_A_SALE_OR_A_HOLD =
      "type selects what a payment is rather than what it carries, and provider autra enumerates"
          + " SALE and PREAUTH and nothing else; it is one value and not a comma-separated list,"
          + " unlike status";

  static final String A_STATUS_IS_ONE_THE_VENDOR_PUBLISHES =
      "status is a \"lista separada por virgula (ex.: ACCEPTED,CAPTURED)\" and takes the seventeen"
          + " values this route names and nothing else, because provider autra publishes the rule"
          + " for anything else on the same line: \"Valor desconhecido -> 400 INVALID_STATUS\"."
          + " An open reservation answers ACCEPTED here, which is the second of the seventeen, so"
          + " a client that read a payment back can filter for the status it was told. A card"
          + " payment of this simulator is stored at five of the seventeen - ACCEPTED, CAPTURED,"
          + " VOIDED, PARTIALLY_VOIDED and DECLINED - so twelve match nothing, and those twelve"
          + " are the vendor's own; an eighteenth of this simulator's invention would answer 200"
          + " where autra answers 400 and so would be the one way to tell the two apart";

  private final AutraPayments payments;

  ListPayments(AutraPayments payments) {
    this.payments = payments;
  }

  AutraOperationResult handle(
      AutraApiCall call, TestRunId testRunId, AutraMerchant merchant, Instant now) {
    AutraOperationResult refused = AutraMerchantDocument.theRefusalOf(call, merchant);
    if (refused != null) {
      return refused;
    }
    AutraOperationResult asked = theQueryRefusalOf(call);
    if (asked != null) {
      return asked;
    }
    AutraPaymentWindow.Asked window = AutraPaymentWindow.of(call, now);
    if (window instanceof AutraPaymentWindow.Asked.Refused unusable) {
      return AutraAnswers.refusedRequest(unusable.code(), unusable.msg());
    }
    return answered(
        call, testRunId, merchant, ((AutraPaymentWindow.Asked.Between) window).window());
  }

  private AutraOperationResult answered(
      AutraApiCall call, TestRunId testRunId, AutraMerchant merchant, AutraPaymentWindow window) {
    int page = thePageIn(call).orElse(THE_PAGE_A_CALL_THAT_NAMES_NONE_GETS);
    int limit = theLimitIn(call).orElse(THE_LIMIT_A_CALL_THAT_NAMES_NONE_GETS);
    List<AutraPayment> matched =
        theMatchesAmong(payments.ofMerchant(testRunId, merchant.documentId()), call, window);
    long skipped = (long) (page - 1) * limit;
    int from = (int) Math.min(skipped, matched.size());
    int until = (int) Math.min(skipped + limit, matched.size());
    List<AutraPayment> onThisPage = matched.subList(from, until);
    Map<String, List<AutraPaymentEvent>> timelines =
        AutraPaymentTimelines.keyedByPayment(
            payments.everyEventOf(testRunId, AutraPaymentTimelines.theIdentifiersOf(onThisPage)));
    List<AutraJson> items = new ArrayList<>();
    for (AutraPayment payment : onThisPage) {
      items.add(
          AutraPaymentRead.theItemOfAListing(
              payment, AutraPaymentTimelines.theTimelineOf(timelines, payment.paymentId())));
    }
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.ITEMS, AutraJson.array(items));
    answer.put(AutraFields.PAGE, AutraJson.number(Integer.toString(page)));
    answer.put(AutraFields.LIMIT, AutraJson.number(Integer.toString(limit)));
    answer.put(AutraFields.HAS_MORE, AutraJson.flag(matched.size() > skipped + limit));
    answer.put(AutraFields.FROM, AutraJson.text(window.fromAsAutraWritesIt()));
    answer.put(AutraFields.TO, AutraJson.text(window.toAsAutraWritesIt()));
    return AutraOperationResult.succeeded(
        null, AutraBody.of(AutraAnswers.OK, AutraJson.object(answer)));
  }

  private static List<AutraPayment> theMatchesAmong(
      List<AutraPayment> held, AutraApiCall call, AutraPaymentWindow window) {
    List<String> statuses = theStatusesIn(call);
    Optional<String> type = call.queryParameter(AutraFields.TYPE);
    List<AutraPayment> matched = new ArrayList<>();
    for (AutraPayment payment : held) {
      if (matches(payment, window, statuses, type)) {
        matched.add(payment);
      }
    }
    return matched;
  }

  private static boolean matches(
      AutraPayment payment,
      AutraPaymentWindow window,
      List<String> statuses,
      Optional<String> type) {
    return window.holds(payment.createdAt())
        && (statuses.isEmpty() || statuses.contains(payment.nativeStatus()))
        && type.map(asked -> asked.equals(payment.type())).orElse(Boolean.TRUE).booleanValue();
  }

  private static @Nullable AutraOperationResult theQueryRefusalOf(AutraApiCall call) {
    for (String status : theStatusesIn(call)) {
      if (!AutraStatus.isOneTheVendorHasPublished(status)) {
        return AutraAnswers.refusedRequest(
            AutraErrors.INVALID_STATUS, A_STATUS_IS_ONE_THE_VENDOR_PUBLISHES);
      }
    }
    Optional<String> type = call.queryParameter(AutraFields.TYPE);
    if (type.isPresent() && !AutraTimeline.isATypeAPaymentIs(type.get())) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_TYPE, A_TYPE_IS_A_SALE_OR_A_HOLD);
    }
    if (call.queryParameter(AutraFields.PAGE).isPresent() && thePageIn(call).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_PAGE, A_PAGE_STARTS_AT_ONE);
    }
    if (call.queryParameter(AutraFields.LIMIT).isPresent() && theLimitIn(call).isEmpty()) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_LIMIT, A_LIMIT_IS_ONE_TO_TWO_HUNDRED);
    }
    return null;
  }

  private static List<String> theStatusesIn(AutraApiCall call) {
    Optional<String> asked = call.queryParameter(AutraFields.STATUS);
    if (asked.isEmpty()) {
      return List.of();
    }
    List<String> named = new ArrayList<>();
    for (String one : asked.get().split(",", -1)) {
      named.add(one.trim());
    }
    return List.copyOf(named);
  }

  private static Optional<Integer> thePageIn(AutraApiCall call) {
    return aWholeNumberIn(call, AutraFields.PAGE).filter(page -> page.intValue() >= SMALLEST_PAGE);
  }

  private static Optional<Integer> theLimitIn(AutraApiCall call) {
    return aWholeNumberIn(call, AutraFields.LIMIT)
        .filter(limit -> limit.intValue() >= SMALLEST_LIMIT && limit.intValue() <= LARGEST_LIMIT);
  }

  private static Optional<Integer> aWholeNumberIn(AutraApiCall call, String name) {
    Optional<String> asked = call.queryParameter(name);
    if (asked.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Integer.valueOf(asked.get()));
    } catch (NumberFormatException notANumber) {
      return Optional.empty();
    }
  }
}
