package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

enum StripeExpandable {
  PAYMENT_INTENT,
  SETUP_INTENT,
  PAYMENT_METHOD,
  CHARGE,
  REFUND,
  CUSTOMER,
  EPHEMERAL_KEY;

  private static final Set<String> PUBLISHED_ON_PAYMENT_INTENT =
      Set.of(
          "amount_details",
          "application",
          "automatic_payment_methods",
          "customer",
          "hooks",
          "last_payment_error",
          "latest_charge",
          "managed_payments",
          "next_action",
          "on_behalf_of",
          "payment_details",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_options",
          "payment_record",
          "presentment_details",
          "processing",
          "review",
          "shipping",
          "transfer_data");

  private static final Set<String> PUBLISHED_ON_SETUP_INTENT =
      Set.of(
          "application",
          "automatic_payment_methods",
          "customer",
          "last_setup_error",
          "latest_attempt",
          "managed_payments",
          "mandate",
          "next_action",
          "on_behalf_of",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_options",
          "single_use_mandate");

  private static final Set<String> PUBLISHED_ON_PAYMENT_METHOD =
      Set.of(
          "acss_debit",
          "affirm",
          "afterpay_clearpay",
          "alipay",
          "alma",
          "amazon_pay",
          "au_becs_debit",
          "bacs_debit",
          "bancontact",
          "billie",
          "billing_details",
          "bizum",
          "blik",
          "boleto",
          "card",
          "card_present",
          "cashapp",
          "crypto",
          "custom",
          "customer",
          "customer_balance",
          "eps",
          "fpx",
          "giropay",
          "grabpay",
          "ideal",
          "interac_present",
          "kakao_pay",
          "klarna",
          "konbini",
          "kr_card",
          "link",
          "mb_way",
          "mobilepay",
          "multibanco",
          "naver_pay",
          "nz_bank_account",
          "oxxo",
          "p24",
          "pay_by_bank",
          "payco",
          "paynow",
          "paypal",
          "paypay",
          "payto",
          "pix",
          "promptpay",
          "radar_options",
          "revolut_pay",
          "samsung_pay",
          "satispay",
          "scalapay",
          "sepa_debit",
          "sequra",
          "sofort",
          "sunbit",
          "swish",
          "twint",
          "upi",
          "us_bank_account",
          "wechat_pay",
          "zip");

  private static final Set<String> PUBLISHED_ON_CHARGE =
      Set.of(
          "application",
          "application_fee",
          "balance_transaction",
          "billing_details",
          "customer",
          "failure_balance_transaction",
          "fraud_details",
          "on_behalf_of",
          "outcome",
          "payment_intent",
          "payment_method_details",
          "presentment_details",
          "radar_options",
          "refunds",
          "review",
          "shipping",
          "source_transfer",
          "transfer",
          "transfer_data");

  private static final Set<String> PUBLISHED_ON_REFUND =
      Set.of(
          "balance_transaction",
          "charge",
          "customer",
          "destination_details",
          "failure_balance_transaction",
          "next_action",
          "payment_intent",
          "payment_method",
          "presentment_details",
          "source_transfer_reversal",
          "transfer_reversal");

  private static final Set<String> PUBLISHED_ON_CUSTOMER =
      Set.of(
          "address",
          "cash_balance",
          "default_source",
          "discount",
          "invoice_settings",
          "shipping",
          "sources",
          "subscriptions",
          "tax",
          "tax_ids",
          "test_clock");

  private static final Map<String, StripeRelation> HELD_ON_PAYMENT_INTENT =
      Map.of(
          "customer", StripeRelation.A_CUSTOMER,
          "payment_method", StripeRelation.A_PAYMENT_METHOD,
          "latest_charge", StripeRelation.A_CHARGE);

  private static final Map<String, StripeRelation> HELD_ON_SETUP_INTENT =
      Map.of(
          "customer", StripeRelation.A_CUSTOMER,
          "payment_method", StripeRelation.A_PAYMENT_METHOD);

  private static final Map<String, StripeRelation> HELD_ON_PAYMENT_METHOD =
      Map.of("customer", StripeRelation.A_CUSTOMER);

  private static final Map<String, StripeRelation> HELD_ON_CHARGE =
      Map.of("payment_intent", StripeRelation.A_PAYMENT_INTENT);

  private static final Map<String, StripeRelation> HELD_ON_REFUND =
      Map.of(
          "charge", StripeRelation.A_CHARGE,
          "payment_intent", StripeRelation.A_PAYMENT_INTENT);

  boolean publishes(String field) {
    return switch (this) {
      case PAYMENT_INTENT -> PUBLISHED_ON_PAYMENT_INTENT.contains(field);
      case SETUP_INTENT -> PUBLISHED_ON_SETUP_INTENT.contains(field);
      case PAYMENT_METHOD -> PUBLISHED_ON_PAYMENT_METHOD.contains(field);
      case CHARGE -> PUBLISHED_ON_CHARGE.contains(field);
      case REFUND -> PUBLISHED_ON_REFUND.contains(field);
      case CUSTOMER -> PUBLISHED_ON_CUSTOMER.contains(field);
      case EPHEMERAL_KEY -> false;
    };
  }

  Optional<StripeRelation> holds(String field) {
    return Optional.ofNullable(
        switch (this) {
          case PAYMENT_INTENT -> HELD_ON_PAYMENT_INTENT.get(field);
          case SETUP_INTENT -> HELD_ON_SETUP_INTENT.get(field);
          case PAYMENT_METHOD -> HELD_ON_PAYMENT_METHOD.get(field);
          case CHARGE -> HELD_ON_CHARGE.get(field);
          case REFUND -> HELD_ON_REFUND.get(field);
          case CUSTOMER, EPHEMERAL_KEY -> null;
        });
  }
}
