package dev.marcinromanowski.pspsim.provider.autra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AutraPaymentRead {

  private AutraPaymentRead() {}

  static AutraJson.JsonObject theWholePaymentWithItsTimeline(
      AutraPayment payment, List<AutraPaymentEvent> timeline) {
    Map<String, AutraJson> answer = theItemALookupAndAListingShare(payment, timeline);
    answer.put(AutraFields.EVENTS, AutraJson.array(theTimelineOf(timeline)));
    return AutraJson.object(answer);
  }

  static AutraJson.JsonObject theItemOfAListing(
      AutraPayment payment, List<AutraPaymentEvent> timeline) {
    return AutraJson.object(theItemALookupAndAListingShare(payment, timeline));
  }

  private static Map<String, AutraJson> theItemALookupAndAListingShare(
      AutraPayment payment, List<AutraPaymentEvent> timeline) {
    Map<String, AutraJson> answer = new LinkedHashMap<>();
    answer.put(AutraFields.PAYMENT_ID_UPPERCASE_D, AutraJson.text(payment.paymentId()));
    String orderId = payment.orderId();
    if (orderId != null) {
      answer.put(AutraFields.ORDER_ID, AutraJson.text(orderId));
    }
    answer.put(AutraFields.TYPE, AutraJson.text(payment.type()));
    answer.put(AutraFields.STATUS, AutraJson.text(payment.nativeStatus()));
    answer.put(AutraFields.VOID_RETRYABLE, AutraJson.flag(isWorthAVoidNow(payment)));
    answer.put(AutraFields.AMOUNT, AutraJson.numberOrText(payment.amount().value()));
    answer.put(AutraFields.REFUNDED_AMOUNT, AutraJson.numberOrText(payment.refundedAmount()));
    answer.put(AutraFields.CURRENCY, AutraJson.text(payment.currency()));
    answer.put(AutraFields.DOCUMENT_ID, AutraJson.text(payment.documentId()));
    answer.put(AutraFields.TRANSACTION_TYPE, AutraJson.text(payment.transactionType()));
    answer.put(AutraFields.TRANSACTION_METHOD, AutraJson.text(payment.transactionMethod()));
    answer.put(
        AutraFields.INSTALLMENTS, AutraJson.number(Integer.toString(payment.installments())));
    answer.put(AutraFields.AUTHORIZATION_CODE, AutraJson.text(payment.authorizationCode()));
    answer.put(AutraFields.RRN, AutraJson.text(payment.rrn()));
    AutraAcquirerTransaction.underThePrimaryNameAlone(answer, payment);
    List<AutraJson> refusals = theRefusalsIn(timeline);
    if (!refusals.isEmpty()) {
      answer.put(AutraFields.ERRORS, AutraJson.array(refusals));
    }
    answer.put(
        AutraFields.CREATED_AT,
        AutraJson.text(CreatePixCheckout.asAutraWritesIt(payment.createdAt())));
    answer.put(
        AutraFields.UPDATED_AT,
        AutraJson.text(CreatePixCheckout.asAutraWritesIt(payment.updatedAt())));
    return answer;
  }

  static boolean isWorthAVoidNow(AutraPayment payment) {
    return payment.isAReservationOrSaleNothingHasClosed()
        && ReverseOrRelease.theRemainingBalanceOf(payment).signum() > 0;
  }

  private static List<AutraJson> theRefusalsIn(List<AutraPaymentEvent> timeline) {
    List<AutraJson> refusals = new ArrayList<>();
    for (AutraPaymentEvent event : timeline) {
      if (event.reportsARefusal()) {
        refusals.add(
            AutraErrors.item(event.theCodeItWasRefusedWith(), event.theMessageItWasRefusedWith()));
      }
    }
    return refusals;
  }

  private static List<AutraJson> theTimelineOf(List<AutraPaymentEvent> timeline) {
    List<AutraJson> events = new ArrayList<>();
    for (AutraPaymentEvent event : timeline) {
      events.add(oneEventOf(event));
    }
    return events;
  }

  private static AutraJson.JsonObject oneEventOf(AutraPaymentEvent event) {
    Map<String, AutraJson> member = new LinkedHashMap<>();
    member.put(AutraFields.TYPE, AutraJson.text(event.type()));
    member.put(AutraFields.STATUS, AutraJson.text(event.status()));
    member.put(AutraFields.AMOUNT, AutraJson.numberOrText(event.amount().value()));
    member.put(AutraFields.AT, AutraJson.text(CreatePixCheckout.asAutraWritesIt(event.at())));
    if (event.reportsARefusal()) {
      member.put(AutraFields.ERROR_CODE, AutraJson.text(event.theCodeItWasRefusedWith()));
      member.put(AutraFields.MSG, AutraJson.text(event.theMessageItWasRefusedWith()));
    }
    return AutraJson.object(member);
  }
}
