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
  CUSTOMER;

  private static final Set<String> PROPERTIES_OF_PAYMENT_INTENT =
      Set.of(
          "allowed_payment_method_types",
          "amount",
          "amount_capturable",
          "amount_details",
          "amount_received",
          "application",
          "application_fee_amount",
          "automatic_payment_methods",
          "canceled_at",
          "cancellation_reason",
          "capture_method",
          "client_secret",
          "confirmation_method",
          "created",
          "currency",
          "customer",
          "customer_account",
          "description",
          "excluded_payment_method_types",
          "hooks",
          "id",
          "last_payment_error",
          "latest_charge",
          "livemode",
          "managed_payments",
          "metadata",
          "next_action",
          "object",
          "on_behalf_of",
          "payment_details",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_options",
          "payment_method_types",
          "payment_record",
          "presentment_details",
          "processing",
          "receipt_email",
          "review",
          "setup_future_usage",
          "shipping",
          "statement_descriptor",
          "statement_descriptor_suffix",
          "status",
          "transfer_data",
          "transfer_group");

  private static final Set<String> PROPERTIES_OF_SETUP_INTENT =
      Set.of(
          "allowed_payment_method_types",
          "application",
          "attach_to_self",
          "automatic_payment_methods",
          "cancellation_reason",
          "client_secret",
          "created",
          "customer",
          "customer_account",
          "description",
          "excluded_payment_method_types",
          "flow_directions",
          "id",
          "last_setup_error",
          "latest_attempt",
          "livemode",
          "managed_payments",
          "mandate",
          "metadata",
          "next_action",
          "object",
          "on_behalf_of",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_options",
          "payment_method_types",
          "single_use_mandate",
          "status",
          "usage");

  private static final Set<String> PROPERTIES_OF_PAYMENT_METHOD =
      Set.of(
          "acss_debit",
          "affirm",
          "afterpay_clearpay",
          "alipay",
          "allow_redisplay",
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
          "created",
          "crypto",
          "custom",
          "customer",
          "customer_account",
          "customer_balance",
          "eps",
          "fpx",
          "giropay",
          "grabpay",
          "id",
          "ideal",
          "interac_present",
          "kakao_pay",
          "klarna",
          "konbini",
          "kr_card",
          "link",
          "livemode",
          "mb_way",
          "metadata",
          "mobilepay",
          "multibanco",
          "naver_pay",
          "nz_bank_account",
          "object",
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
          "type",
          "upi",
          "us_bank_account",
          "wechat_pay",
          "zip");

  private static final Set<String> PROPERTIES_OF_CHARGE =
      Set.of(
          "amount",
          "amount_captured",
          "amount_refunded",
          "application",
          "application_fee",
          "application_fee_amount",
          "balance_transaction",
          "billing_details",
          "calculated_statement_descriptor",
          "captured",
          "created",
          "currency",
          "customer",
          "description",
          "disputed",
          "failure_balance_transaction",
          "failure_code",
          "failure_message",
          "fraud_details",
          "id",
          "livemode",
          "metadata",
          "object",
          "on_behalf_of",
          "outcome",
          "paid",
          "payment_intent",
          "payment_method",
          "payment_method_details",
          "presentment_details",
          "radar_options",
          "receipt_email",
          "receipt_number",
          "receipt_url",
          "refunded",
          "refunds",
          "review",
          "shipping",
          "source_transfer",
          "statement_descriptor",
          "statement_descriptor_suffix",
          "status",
          "transfer",
          "transfer_data",
          "transfer_group");

  private static final Set<String> PROPERTIES_OF_REFUND =
      Set.of(
          "amount",
          "balance_transaction",
          "charge",
          "created",
          "currency",
          "customer",
          "customer_account",
          "description",
          "destination_details",
          "failure_balance_transaction",
          "failure_reason",
          "id",
          "instructions_email",
          "metadata",
          "next_action",
          "object",
          "payment_intent",
          "payment_method",
          "pending_reason",
          "presentment_details",
          "reason",
          "receipt_number",
          "source_transfer_reversal",
          "status",
          "transfer_reversal");

  private static final Set<String> PROPERTIES_OF_CUSTOMER =
      Set.of(
          "address",
          "balance",
          "business_name",
          "cash_balance",
          "created",
          "currency",
          "customer_account",
          "default_source",
          "delinquent",
          "description",
          "discount",
          "email",
          "id",
          "individual_name",
          "invoice_credit_balance",
          "invoice_prefix",
          "invoice_settings",
          "livemode",
          "metadata",
          "name",
          "next_invoice_sequence",
          "object",
          "phone",
          "preferred_locales",
          "shipping",
          "sources",
          "subscriptions",
          "tax",
          "tax_exempt",
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
      Map.of(
          "customer", StripeRelation.A_CUSTOMER,
          "payment_intent", StripeRelation.A_PAYMENT_INTENT);

  private static final Map<String, StripeRelation> HELD_ON_REFUND =
      Map.of(
          "charge", StripeRelation.A_CHARGE,
          "payment_intent", StripeRelation.A_PAYMENT_INTENT);

  private static final Map<String, StripeIncludable> INCLUDED_ON_CHARGE =
      Map.of("refunds", StripeIncludable.THE_REFUNDS_OF_THE_CHARGE);

  private static final Map<String, StripeIncludable> INCLUDED_ON_CUSTOMER =
      Map.of(
          "cash_balance", StripeIncludable.THE_CASH_BALANCE,
          "invoice_credit_balance", StripeIncludable.THE_INVOICE_CREDIT_BALANCE,
          "sources", StripeIncludable.THE_SOURCES,
          "subscriptions", StripeIncludable.THE_SUBSCRIPTIONS,
          "tax", StripeIncludable.THE_TAX_DETAILS,
          "tax_ids", StripeIncludable.THE_TAX_IDS);

  boolean publishes(String property) {
    return switch (this) {
      case PAYMENT_INTENT -> PROPERTIES_OF_PAYMENT_INTENT.contains(property);
      case SETUP_INTENT -> PROPERTIES_OF_SETUP_INTENT.contains(property);
      case PAYMENT_METHOD -> PROPERTIES_OF_PAYMENT_METHOD.contains(property);
      case CHARGE -> PROPERTIES_OF_CHARGE.contains(property);
      case REFUND -> PROPERTIES_OF_REFUND.contains(property);
      case CUSTOMER -> PROPERTIES_OF_CUSTOMER.contains(property);
    };
  }

  Optional<StripeRelation> holds(String property) {
    return Optional.ofNullable(
        switch (this) {
          case PAYMENT_INTENT -> HELD_ON_PAYMENT_INTENT.get(property);
          case SETUP_INTENT -> HELD_ON_SETUP_INTENT.get(property);
          case PAYMENT_METHOD -> HELD_ON_PAYMENT_METHOD.get(property);
          case CHARGE -> HELD_ON_CHARGE.get(property);
          case REFUND -> HELD_ON_REFUND.get(property);
          case CUSTOMER -> null;
        });
  }

  Optional<StripeIncludable> includes(String property) {
    return Optional.ofNullable(
        switch (this) {
          case CHARGE -> INCLUDED_ON_CHARGE.get(property);
          case CUSTOMER -> INCLUDED_ON_CUSTOMER.get(property);
          case PAYMENT_INTENT, SETUP_INTENT, PAYMENT_METHOD, REFUND -> null;
        });
  }
}
