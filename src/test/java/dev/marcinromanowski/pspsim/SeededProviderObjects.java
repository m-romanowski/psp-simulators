package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class SeededProviderObjects {

  public static final Instant SEEDED_AT = Instant.parse("2026-10-08T09:00:00Z");

  private final JdbcClient jdbc;

  public SeededProviderObjects(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public static String theGrowTransactionIdOf(int held) {
    return "90000" + held;
  }

  public static String theAzericardIntRefOf(int held) {
    return "intref-" + held;
  }

  public static String theStripeIntentIdOf(int held) {
    return "pi_sim_one_query_" + held;
  }

  public static String theWorldlinePaymentIdOf(int held) {
    return "300000000" + held + "_0";
  }

  public static String theAutraPaymentIdOf(int held) {
    return "pay-one-query-" + held;
  }

  public void aGrowTransaction(TestRunId run, int held) {
    String transactionId = theGrowTransactionIdOf(held);
    jdbc.sql(
            """
            insert into grow_transaction
                (test_run_id, kind, transaction_id, transaction_token, merchant_id, process_id,
                 refunded_transaction_id, asmachta, payment_date, description, payer_full_name,
                 payer_phone, payer_email, card_suffix, card_type, card_type_code, card_brand,
                 card_brand_code, card_exp, amount, origin, created_at, updated_at)
            values (?, 'TOKEN_PAYMENT', ?, ?, 'merchant-1', null, null, '1234', '01/01/2026',
                    'one seat', 'Dana Levi', '0500000000', null, '1111', 'Local', '1', 'Visa',
                    '3', '1230', ?, 'PROVIDER_API', ?, ?)
            """)
        .param(run.value())
        .param(transactionId)
        .param("token-" + held)
        .param(amountOf(held))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .update();
    for (int sequence = 1; sequence <= 2; sequence++) {
      jdbc.sql(
              """
              insert into grow_transaction_operation
                  (test_run_id, transaction_id, seq, kind, amount, closes_authorization,
                   created_object_id, native_status, normalized_status, created_at)
              values (?, ?, ?, ?, ?, null, null, '2', 'SUCCEEDED', ?)
              """)
          .param(run.value())
          .param(transactionId)
          .param(Integer.valueOf(sequence))
          .param(sequence == 1 ? "AUTHORIZATION" : "CAPTURE")
          .param(sequence == 1 ? amountOf(held) : halfOf(held))
          .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
          .update();
    }
  }

  public void anAzericardPayment(TestRunId run, int held) {
    String intRef = theAzericardIntRefOf(held);
    jdbc.sql(
            """
            insert into azericard_payment
                (test_run_id, int_ref, terminal_id, merchant_id, order_id, trtype, amount,
                 currency, approval, rrn, action_code, response_code, origin, created_at,
                 updated_at)
            values (?, ?, 'terminal-1', 'merchant-1', ?, '0', ?, 'AZN', '123456', ?, '0', '00',
                    'PROVIDER_API', ?, ?)
            """)
        .param(run.value())
        .param(intRef)
        .param("2024011510000" + held)
        .param(amountOf(held))
        .param("rrn00000000" + held)
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .update();
    for (int sequence = 1; sequence <= 2; sequence++) {
      jdbc.sql(
              """
              insert into azericard_payment_operation
                  (test_run_id, int_ref, seq, kind, amount, closes_authorization,
                   created_object_id, native_status, normalized_status, created_at)
              values (?, ?, ?, ?, ?, null, null, '0/00', 'SUCCEEDED', ?)
              """)
          .param(run.value())
          .param(intRef)
          .param(Integer.valueOf(sequence))
          .param(sequence == 1 ? "AUTHORIZATION" : "CAPTURE")
          .param(sequence == 1 ? amountOf(held) : halfOf(held))
          .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
          .update();
    }
  }

  public void aStripeIntent(TestRunId run, int held) {
    String intentId = theStripeIntentIdOf(held);
    jdbc.sql(
            """
            insert into stripe_payment_intent
                (test_run_id, intent_id, account_id, amount, currency, capture_method, status,
                 payment_method, customer, receipt_email, client_secret, last_payment_error,
                 cancellation_reason, capture_expires_at, settles_at, settles_with, origin,
                 created_at, updated_at)
            values (?, ?, 'acct_one_query', ?, 'usd', 'manual', 'requires_capture', null, null,
                    null, ?, null, null, ?, null, null, 'PROVIDER_API', ?, ?)
            """)
        .param(run.value())
        .param(intentId)
        .param(minorUnitsOf(held))
        .param(intentId + "_secret")
        .param(SEEDED_AT.plusSeconds(604800L).atOffset(ZoneOffset.UTC))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .update();
    for (int sequence = 1; sequence <= 2; sequence++) {
      jdbc.sql(
              """
              insert into stripe_payment_intent_operation
                  (test_run_id, intent_id, seq, kind, amount, closes_authorization,
                   created_object_id, native_status, normalized_status, created_at)
              values (?, ?, ?, ?, ?, null, null, 'requires_capture', 'SUCCEEDED', ?)
              """)
          .param(run.value())
          .param(intentId)
          .param(Integer.valueOf(sequence))
          .param(sequence == 1 ? "AUTHORIZATION" : "CAPTURE")
          .param(sequence == 1 ? minorUnitsOf(held) : halfTheMinorUnitsOf(held))
          .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
          .update();
    }
    jdbc.sql(
            """
            insert into stripe_charge
                (test_run_id, charge_id, intent_id, account_id, amount, currency,
                 payment_method, status, created_at)
            values (?, ?, ?, 'acct_one_query', ?, 'usd', null, 'succeeded', ?)
            """)
        .param(run.value())
        .param("ch_sim_one_query_" + held)
        .param(intentId)
        .param(minorUnitsOf(held))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .update();
  }

  public void aWorldlinePayment(TestRunId run, int held) {
    String paymentId = theWorldlinePaymentIdOf(held);
    jdbc.sql(
            """
            insert into worldline_payment
                (test_run_id, payment_id, account_id, merchant_id, merchant_reference,
                 merchant_customer_id, token, hosted_tokenization_id, card_number, expiry_date,
                 amount, currency_code, authorization_mode, native_status, status_category,
                 status_code, authorization_closed, origin, created_at, updated_at)
            values (?, ?, 'acct-one-query', 'merchant-1', null, null, null, null, null, null,
                    ?, 'PLN', 'PRE_AUTHORIZATION', 'PENDING_CAPTURE', 'PENDING_MERCHANT', 5,
                    false, 'PROVIDER_API', ?, ?)
            """)
        .param(run.value())
        .param(paymentId)
        .param(Long.valueOf(minorUnitsOf(held)))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .update();
    for (int payIdSub = 0; payIdSub <= 1; payIdSub++) {
      jdbc.sql(
              """
              insert into worldline_payment_identifier
                  (test_run_id, identifier, payment_id, payid_sub, minted_at)
              values (?, ?, ?, ?, ?)
              """)
          .param(run.value())
          .param("300000000" + held + "_" + payIdSub)
          .param(paymentId)
          .param(Integer.valueOf(payIdSub))
          .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
          .update();
    }
    for (int sequence = 1; sequence <= 2; sequence++) {
      jdbc.sql(
              """
              insert into worldline_payment_operation
                  (test_run_id, payment_id, seq, kind, amount, closes_authorization,
                   created_object_id, native_status, normalized_status, created_at)
              values (?, ?, ?, ?, ?, null, null, 'PENDING_CAPTURE', 'SUCCEEDED', ?)
              """)
          .param(run.value())
          .param(paymentId)
          .param(Integer.valueOf(sequence))
          .param(sequence == 1 ? "AUTHORIZATION" : "CAPTURE")
          .param(sequence == 1 ? minorUnitsOf(held) : halfTheMinorUnitsOf(held))
          .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
          .update();
    }
  }

  public void anAutraPayment(TestRunId run, int held) {
    String paymentId = theAutraPaymentIdOf(held);
    jdbc.sql(
            """
            insert into autra_payment
                (test_run_id, payment_id, account_id, document_id, order_id, payment_type,
                 transaction_type, amount, currency, installments, native_status,
                 authorization_code, rrn, tracking_number, slug_token, slug_stored_card,
                 hold_released_at, origin, created_at, updated_at)
            values (?, ?, 'merchant-1', '25055662000190', null, 'PREAUTH', 'CREDIT', ?, 'BRL',
                    1, 'ACCEPTED', 'A1B2C3', '112676199769', null, null, null, null,
                    'PROVIDER_API', ?, ?)
            """)
        .param(run.value())
        .param(paymentId)
        .param(amountOf(held))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
        .update();
    for (int sequence = 0; sequence <= 1; sequence++) {
      jdbc.sql(
              """
              insert into autra_payment_event
                  (test_run_id, payment_id, seq, event_type, status, amount, error_code, msg, at)
              values (?, ?, ?, ?, 'ACCEPTED', ?, null, null, ?)
              """)
          .param(run.value())
          .param(paymentId)
          .param(Integer.valueOf(sequence))
          .param(sequence == 0 ? "PREAUTH" : "CAPTURE")
          .param(sequence == 0 ? amountOf(held) : halfOf(held))
          .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
          .update();
    }
    for (int sequence = 1; sequence <= 2; sequence++) {
      jdbc.sql(
              """
              insert into autra_payment_operation
                  (test_run_id, payment_id, seq, kind, amount, closes_authorization,
                   created_object_id, native_status, normalized_status, created_at)
              values (?, ?, ?, ?, ?, null, null, 'ACCEPTED', 'SUCCEEDED', ?)
              """)
          .param(run.value())
          .param(paymentId)
          .param(Integer.valueOf(sequence))
          .param(sequence == 1 ? "AUTHORIZATION" : "CAPTURE")
          .param(sequence == 1 ? amountOf(held) : halfOf(held))
          .param(SEEDED_AT.atOffset(ZoneOffset.UTC))
          .update();
    }
  }

  private static String amountOf(int held) {
    return held + "00.00";
  }

  private static String halfOf(int held) {
    return held + "0.00";
  }

  private static String minorUnitsOf(int held) {
    return held + "0000";
  }

  private static String halfTheMinorUnitsOf(int held) {
    return held + "000";
  }
}
