# Stripe Simulator Contract

## What This File Covers

The vendor contract for the Stripe `/v1` API as this simulator serves it: customers, ephemeral keys, SetupIntents,
PaymentIntents (manual-capture hold, off-session charge, multicapture), charges, refunds, PaymentMethods, signed
webhooks and errors. For each route it records what Stripe publishes, what the simulator emits, the error codes and
messages, and where the simulator deviates.

Evidence classes, per `README.md`, "Evidence Classes":

- **rule**: prose or a table on a Stripe page,
- **example**: a sample on a Stripe page,
- **probe**: observed against Stripe itself. The only one is a `POST /v1/payment_intents` carrying no API key, probed
  2026-09-28, because every other probe needs an account,
- **decision**: a choice made by this simulator,
- **inference**: read off the wording, stated nowhere.

All pages were fetched on 2026-09-11.

### Routes

| Route                                                  | What the simulator does                                                          | Key deviations and choices                                                                                     |
|--------------------------------------------------------|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `POST /v1/customers`                                   | creates a customer. Reads `email` only                                           | the object has `id`, `object`, `created`, `email`, `livemode` and nothing else                                  |
| `GET /v1/customers/{id}`                               | retrieves, including a deleted customer                                          | a deleted customer is returned as the three-field deleted object                                               |
| `POST /v1/customers/{id}`                              | updates `email`                                                                  | a blank `email=` is treated as absent. A deleted customer is refused `400` with no `code`                      |
| `DELETE /v1/customers/{id}`                            | deletes, and detaches every PaymentMethod of the customer                        | no `Idempotency-Key`. A second delete is refused `400` with no `code`                                           |
| `POST /v1/ephemeral_keys`                              | mints a key for a `customer`, expiring after 1 hour                              | `customer` is the only resource served, else `parameter_missing`. The key reaches only the two customer read routes |
| `POST /v1/setup_intents`                               | creates at `requires_payment_method`, or `requires_confirmation` with a `payment_method` | `payment_method_types` refused with `parameter_unknown`                                                 |
| `POST /v1/setup_intents/{id}/confirm`                  | attaches the method to the customer and ends at `succeeded`, or fails to `requires_payment_method` | the only SetupIntent operation that takes scenarios. No SetupIntent events                      |
| `POST /v1/payment_intents`                             | creates, and optionally confirms inline (`confirm=true`)                          | `payment_method_types` ignored. No `expand[]`                                                                  |
| `GET /v1/payment_intents/{id}`                         | current state                                                                    | no `expand[]`. No scenarios                                                                                    |
| `POST /v1/payment_intents/{id}/confirm`                | authorizes or charges, and mints a charge                                        | no `requires_action` path: authentication-required is a `402` decline                                          |
| `POST /v1/payment_intents/{id}/capture`                | full, partial and multicapture (`final_capture`)                                 | 50 non-final captures, then an `invalid_request_error` on `final_capture` with no `code`                      |
| `POST /v1/payment_intents/{id}/cancel`                 | cancels                                                                          | `processing` is cancellable although no card reaches it in Stripe                                              |
| `GET /v1/charges/{id}`                                 | charge read                                                                      | 12 fields only. Not on the control read surface                                                                |
| `POST /v1/refunds`                                     | refund by `payment_intent` or `charge`                                           | both sent, or neither, is refused. The resting status is set by `refundStatus`                                 |
| `GET /v1/payment_methods`                              | list, filtered by `customer` and `type`                                          | an unknown customer or type gives an empty list. No paging                                                     |
| `GET /v1/customers/{id}/payment_methods`               | list for one customer                                                            | an unknown customer is `resource_missing`                                                                      |
| `GET /v1/payment_methods/{id}`                         | retrieve, with `expand[]=customer`                                               | the only route with expansion                                                                                  |
| `POST /v1/payment_methods/{id}/detach`                 | detaches permanently                                                             | a second detach, and a detach of a method never attached, are both refused                                     |

Account settings: the `sk_test_` secret key (required), `captureWindow` (required ISO-8601 duration of at least one
microsecond, with no default),
`refundStatus` (default `succeeded`), `webhookSecret` (`whsec_`), `webhookSignature` (`VALID` or `CORRUPTED`) and
`webhookTimestampAge` (ISO-8601).

### Sources

| Page                                                  | URL                                                                   | Sections used                                                                                   |
|-------------------------------------------------------|-----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| Authentication                                        | https://docs.stripe.com/api/authentication                            | "Your API Key"                                                                                  |
| Errors                                                | https://docs.stripe.com/api/errors                                    | "HTTP Status Code Summary", "Error Types", "Attributes"                                         |
| Error codes                                           | https://docs.stripe.com/error-codes                                   |                                                                                                 |
| Decline codes                                         | https://docs.stripe.com/declines/codes                                | "Card decline codes"                                                                            |
| Advanced error handling                               | https://docs.stripe.com/error-low-level                               | "Errors in HTTP", "Idempotency", "The Stripe-Should-Retry header", "HTTP Status Code Reference" |
| Idempotent requests                                   | https://docs.stripe.com/api/idempotent_requests                       |                                                                                                 |
| PaymentIntent and SetupIntent lifecycle               | https://docs.stripe.com/payments/paymentintents/lifecycle             | "Lifecycle"                                                                                     |
| PaymentIntent object, create, confirm, capture, cancel | https://docs.stripe.com/api/payment_intents/object, .../create, .../confirm, .../capture, .../cancel |                                                             |
| Multicapture                                          | https://docs.stripe.com/payments/multicapture                         | "Availability", "Capture the PaymentIntent", "Final capture", "Webhooks"                        |
| SetupIntent object, confirm                           | https://docs.stripe.com/api/setup_intents/object, https://docs.stripe.com/api/setup_intents/confirm |                                                   |
| Ephemeral key object, create                          | https://docs.stripe.com/api/ephemeral_keys/object, https://docs.stripe.com/api/ephemeral_keys/create |                                           |
| PaymentMethod object, list, customer list, detach     | https://docs.stripe.com/api/payment_methods/object, .../list, .../customer_list, .../detach |                                                           |
| Delete a customer                                     | https://docs.stripe.com/api/customers/delete                          |                                                                                                 |
| Charge object                                         | https://docs.stripe.com/api/charges/object                            |                                                                                                 |
| Refund object, create                                 | https://docs.stripe.com/api/refunds/object, https://docs.stripe.com/api/refunds/create |                                                                                |
| Event object, event types                             | https://docs.stripe.com/api/events/object, https://docs.stripe.com/api/events/types |                                                                                   |
| Webhooks                                              | https://docs.stripe.com/webhooks                                      | "Verify webhook signatures manually", "Preventing replay attacks", "Event delivery behaviors", "Handle duplicate events" |
| Testing                                               | https://docs.stripe.com/testing                                       | "Simulate a declined payment", "Test 3D Secure authentication"                                  |
| Error handling                                        | https://docs.stripe.com/error-handling                                | "Catch exceptions", "Types of error and responses", "Payment errors"                            |
| Payments without card authentication                  | https://docs.stripe.com/payments/without-card-authentication          | "Make a payment [Server-side]"                                                                  |
| Save and reuse                                        | https://docs.stripe.com/payments/save-and-reuse                       | "Setup Intents API"                                                                             |

## Authentication and Transport

- Stripe's base is `https://api.stripe.com/v1/...` (example). The simulator's base is configurable.
- "The Stripe API authenticates requests using HTTP Basic Auth. Provide your API key as the basic auth username value.
  You don't need to provide a password." Bearer is published "for example, for a cross-origin request" (rule). The
  simulator accepts both unconditionally (decision).
- Test keys start with `sk_test_` (rule). Live mode publishes `sk_live_`, `rk_live_` and `pk_`, none of which the
  simulator needs. Any configured `sk_test_*` key is accepted (decision).
- "Calls made over plain HTTP fail. API requests without authentication also fail." (rule). The simulator does not
  enforce TLS. A missing or unknown key is `401` with `invalid_request_error` and no `code`.
- Probe (2026-09-28, `POST /v1/payment_intents` with no key): `401`, `Content-Type: application/json`, `WWW-Authenticate: Basic realm="Stripe"`,
  `invalid_request_error`, and the message kept verbatim in Stripe's sample `stripe/no-api-key`. The simulator emits the
  same message: "You did not provide an API key. You need to provide your API key in the Authorization header, using
  Bearer auth (e.g. 'Authorization: Bearer YOUR_SECRET_KEY'). See https://stripe.com/docs/api#authentication for
  details, or we can help at https://support.stripe.com/.". An unknown key gets "No valid API key provided."
- Every response carries `Request-Id`, not `Stripe-Request-Id` (rule: "Each API request has an associated request
  identifier. You can find this value in the response headers, under `Request-Id`."), so that one exchange can be named
  to the vendor: "provide the request identifier when you contact us about a specific request." The only published shape is "string beginning with `req`", for errors. The simulator sends `req_sim_` followed by 24
  hex characters (decision).
- Request bodies are form-encoded, with bracket notation such as `payment_method_options[card][request_multicapture]`
  and `expand[]`. This is published only through the curl examples, and no page names a `Content-Type`. The simulator
  reads `application/x-www-form-urlencoded` bodies only: a body of any other type, JSON included, supplies no fields.
  `GET` parameters come from the query string. Responses are JSON.

## Idempotency

| Rule (published)                                                                                                                                                  | Simulator                                                   |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| Header `Idempotency-Key`, up to 255 characters, V4 UUIDs suggested                                                                                                | a longer key is `400` `invalid_request_error`, no `code`    |
| "saving the resulting status code and body of the first request ... regardless of whether it succeeds or fails ... including `500` errors"                        | replays the stored status and body                          |
| "keys expire out of the system after 24 hours". Keys may be pruned once "at least 24 hours old", and "We generate a new request if a key is reused after the original is pruned." | 24-hour window                                              |
| a replay carries `Idempotent-Replayed: true`                                                                                                                      | same                                                        |
| a reused key with a different endpoint or parameters: `idempotency_error`                                                                                         | `400`, `idempotency_error`, no `code` (the status is a decision) |
| a concurrent same key: `409`, `idempotency_key_in_use`, "The idempotency key provided is currently being used in another request. This occurs if your integration is making duplicate requests simultaneously." | same. Retryable                |
| "We save results only after the execution of an endpoint begins". Validation failures, `429`, a `401` with no key, and "most `400`s that sent invalid parameters" are not cached | validation failures are not cached     |
| "All `POST` requests accept idempotency keys. Don't send idempotency keys in `GET` and `DELETE` requests because it has no effect."                               | `POST` only. `DELETE /v1/customers/{id}` takes no key       |
| `Stripe-Should-Retry: true`/`false`, absent when unknown                                                                                                          | not emitted                                                 |

A key is scoped to an account. Stripe publishes nothing wider.

## Where the Outcome Lives

- Statuses (rule): `200`, `400`, `401`, `402` "The parameters were valid but the request failed", `403`, `404`, `409`,
  `424`, `429`, `500`/`502`/`503`/`504`.
- The errors page groups "a failed charge, or a declined card" under the `4xx` range.
- A decline is `402` with an error body, **and** the PaymentIntent stays retrievable at `requires_payment_method` (rule,
  "Setup Intents API": "When a payment attempt fails, the request also fails with a 402 HTTP status code and the status
  of the PaymentIntent is `requires_payment_method`").
- Non-terminal states (`requires_action`, `requires_payment_method` after a failure, `processing`) are `200` responses,
  and the body must be read.
- Confirm (rule): "If payment fails, the PaymentIntent transitions to the `requires_payment_method` status or the
  `canceled` status if the confirmation limit is reached. If payment succeeds, the PaymentIntent will transition to the
  `succeeded` status (or `requires_capture`, if `capture_method` is set to `manual`)."
- Stripe publishes no status-per-code table. "With this library, you don't need to check for non-200 HTTP responses."
  The statuses in "Required Error Shape" are marked by evidence.

## Minimum API Surface

### Customers

Published:

- `POST /v1/customers` accepts `email` and returns `id`, `object=customer`, `email`.
- An unknown id is `resource_missing`, "The ID provided isn't valid. Either the resource doesn't exist, or an ID for a
  different resource has been provided."
- `DELETE` returns `{"id": ..., "object": "customer", "deleted": true}` (example). "If the customer ID does not exist,
  this call raises an error." "Permanently deletes a customer. It cannot be undone." "Also immediately cancels any
  active subscriptions on the customer" (subscriptions are out of scope).
- "Unlike other objects, deleted customers can still be retrieved through the API in order to be able to track their
  history. Deleting customers removes all credit card details and prevents any further operations to be performed".

| Simulator decision                                                                                                                                                         |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| The object is `id`, `object`, `created`, `email`, `livemode`                                                                                                              |
| Only `email` is read, on create and on update. A blank value leaves the stored email unchanged                                                                            |
| `GET` of a deleted customer returns the deleted object                                                                                                                    |
| Native status is `customer`, or `customer deleted=true` once deleted. Normalized `SUCCEEDED` or `CANCELLED`                                                                |
| "removes all credit card details": deletion detaches every PaymentMethod of the customer                                                                                  |
| "prevents any further operations": update and a second delete are refused with `400` `invalid_request_error`, no `code`. `POST /v1/payment_intents` still accepts any `customer` value |
| A fixture's `providerIds.customerId` records a customer under the fixture's `merchantId`. It is refused if no stripe account of the run matches, if it is longer than 128 characters — the width a PaymentIntent of this simulator carries its `customer` in (`StripeCustomer.LONGEST_CUSTOMER_ID`) — or if it begins with `pi_sim_`, `re_sim_`, `seti_sim_`, `ch_sim_` or `ephkey_sim_` (`A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID`). `cus_sim_` is **not** refused here, because that is the prefix a customer's own id carries and a seeded customer is one |
| No scenario decisions and no events                                                                                                                                       |

### Ephemeral Keys

`POST /v1/ephemeral_keys`. Published parameters: `customer`, `issuing_card`, `nonce`, `verification_session`, all
optional. "Creates a short-lived API key for a given resource". Published response fields: `id`,
`object=ephemeral_key`, `associated_objects`, `created`, `expires`, `livemode`, `secret`.

- In the example, `expires` is 3600 seconds after `created`. No TTL is stated in prose. The simulator uses 1 hour
  (inference).
- Shapes, from the one sample the object page publishes (`https://docs.stripe.com/api/ephemeral_keys/object`): `id` is
  `ephkey_` and 24 alphanumeric characters, `"id": "ephkey_1Mr6PhLkdIwHu7ix9Al0eucz"`, and `secret` is `ek_test_` and
  82 base64url characters,
  `"secret": "ek_test_YWNjdF8xTTJKVGtMa2RJd0h1N2l4LDJTWXVEUUY2c0hZTllRc0dGb2hVanBXRktQTlZoNHc_00TkfmS8Az"`, whose
  first segment base64url-decodes to `acct_1M2JTkLkdIwHu7ix,2SYuDQF6sHYNYQsGFohUjpWFKPNVh4w`: the secret is keyed to
  the account and carries no trace of the key's own `id`. Stripe's own Android SDK carries the same two prefixes in
  hand-written fixtures, `"id": "ephkey_123"` beside `"secret": "ek_test_123"`
  (`payments-core/src/test/java/com/stripe/android/EphemeralKeyFixtures.kt`), and its parser reads `secret` as an
  opaque string. Prose states no shape for either field: "The key's secret. You can use this value to make authorized
  requests to the Stripe API."
- The secret is a short-lived API key, sent as `Authorization: Bearer`, so it wears the key-family prefix and **not**
  the `{id}_secret_{random}` grammar Stripe publishes for an intent's `client_secret` (`StripeClientSecret`). The
  simulator mints `ek_sim_` and 32 hexadecimal characters, drawn apart from the key's `id`
  (`StripeEphemeralKeySecret`): `ek_` with `sim` where the published sample writes `test`, as every minted token of
  this contract marks itself. The width and the hexadecimal alphabet are the simulator's, Stripe publishing no rule
  for either (decision).
- The `id` carries the stem Stripe publishes for the object and not the one it publishes for the secret: the simulator
  mints `ephkey_sim_` and 16 hexadecimal characters (`CreateEphemeralKey.ID_PREFIX`), the width and the alphabet every
  minted id of this contract carries. The marking replaces the random part Stripe writes after the stem and never the
  stem itself, because the stem is what a client reads to know which kind of object it holds.
  `V82__stripe_ephemeral_key_id_carries_the_stem_stripe_publishes.sql` holds the two stems apart:
  `stripe_ephemeral_key_is_not_a_key_secret` forbids an `ek_sim_` `id`, and the four
  `stripe_<table>_is_not_an_ephemeral_key` constraints forbid `ephkey_sim_` from the customer, payment method, setup
  intent and charge tables. It deletes a row of either shape before it constrains, the way
  `V57__stripe_ephemeral_key.sql` does: a key lives an hour and is resolved by its secret, so an `id` the migration
  renamed would be one no client was ever handed. Nothing parses the `id`: lookup is by the primary key or by the
  secret.
- `Stripe-Version` is not a published parameter. Mobile guides send it. The simulator ignores it.
- The simulator serves only `customer`. With none, the answer is `400` `parameter_missing` on `customer`.
- The key authenticates `GET /v1/customers/{id}` and `GET /v1/customers/{id}/payment_methods` for its own customer
  only. Anything else is `401`, and so is an expired key.

### SetupIntents

Published:

- Create accepts `customer`, `usage` (`on_session`/`off_session`, "If not provided, this value defaults to
  `off_session`"), `payment_method`, metadata, and the selection parameters `automatic_payment_methods`,
  `allowed_payment_method_types`, `excluded_payment_method_types` and `payment_method_configuration`.
  `payment_method_types` is not a create parameter (re-fetched with `?query=payment_method_types`). It appears only on
  the object.
- Status enum: `requires_payment_method`, `requires_confirmation`, `requires_action`, `processing`, `canceled`,
  `succeeded`. There is no `failed`: "If the setup fails, the SetupIntent's status returns to
  `requires_payment_method`", with the failure in `last_setup_error`, "The error encountered in the previous SetupIntent
  confirmation."
- Confirm accepts `payment_method`. On `customer`: "If present, the SetupIntent's payment method will be attached to the
  Customer on successful setup. Payment methods attached to other Customers cannot be used with this SetupIntent."
- `client_secret`: "Used for client-side retrieval using a publishable key".
- A `GET` for a single SetupIntent is published. It is not served here.

Failure scenarios: unknown SetupIntent, a method of another customer, `authentication_required`, declined or expired
card.

| Simulator decision                                                                                                                                                  |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Created with a `payment_method`: `requires_confirmation`. Created without: `requires_payment_method`. `requires_action`, `processing` and `canceled` are never reached |
| Confirm writes the attachment and `succeeded` atomically. It is the only way a method gains a customer outside fixtures                                             |
| Confirm refuses before writing: an unknown method (`resource_missing`), a detached method ("Detachment is permanent and irreversible"), another customer's method (the published sentence above), a deleted customer ("prevents any further operations to be performed"), and an unknown `customer` (`resource_missing` on `customer`) |
| Create accepts any `customer`                                                                                                                                       |
| `payment_method_types` on create is refused with `parameter_unknown`, which names the four accepted parameters. On the object it is `["card", "paypal"]`. The four selection parameters narrow nothing |
| `client_secret` appears on the wire only. It is withheld from audit and screens, and no publishable-key surface exists                                              |
| Object: `id`, `object`, `client_secret`, `created`, `customer`, `last_setup_error`, `livemode`, `payment_method`, `payment_method_types`, `status`, `usage`            |
| An error embeds a truncated `setup_intent`: `id`, `object`, `customer`, `last_setup_error`, `payment_method`, `status`                                                |
| Only confirm takes scenario decisions (the `CARD_ADD_SUCCESS` and `CARD_ADD_FAILED` presets)                                                                        |
| No `setup_intent.*` events. Scheduling one is refused by name                                                                                                       |
| The PayPal payer fields stay null, because a SetupIntent neither authorizes nor settles                                                                             |
| The UI wizard runs either create alone, or create and confirm server-side with the secret key                                                                       |

### PaymentIntents

Published:

- Status enum: `requires_payment_method`, `requires_confirmation`, `requires_action`, `processing`, `requires_capture`,
  `canceled`, `succeeded`. Before `2019-02-11`, `requires_source` and `requires_source_action` were used. The simulator
  never emits them.
- `capture_method`: `automatic`, `automatic_async` (the default) and `manual`. The simulator treats the two automatic
  values alike.
- `payment_method_types` is not a create parameter, only an object field: "The list of payment method types (e.g. card)
  that this PaymentIntent is allowed to use". Stripe publishes no deprecation note, so whether create still accepts it is
  unknown. The simulator ignores it on create.

`POST /v1/payment_intents`:

- Accepts `amount`, `currency`, `capture_method=manual`, `customer`, `receipt_email`, `payment_method`, metadata, the
  four selection parameters, `confirm` and `off_session` ("Set to `true` to indicate that the customer isn't in your
  checkout flow during this payment attempt and can't authenticate.").
- Without `confirm`: "its status is `requires_payment_method` until you attach a payment method", and then "enters the
  `requires_confirmation` status".
- With `confirm=true`: `requires_capture` for a manual hold and `succeeded` otherwise, or a decline.
- Returns `id`, `client_secret`, `amount`, `currency`, `customer`, `payment_method_types`, `status`, and
  `latest_charge`: "ID of the latest Charge object created by this PaymentIntent. This property is `null` until
  PaymentIntent confirmation is attempted."
- Stripe expands `customer` and `payment_method` when `expand[]` asks for them. `expand[]` is read on no PaymentIntent
  route of this simulator: both members are always ids (`StripeIntentView.of`). The one expansion served is `customer`
  on `GET /v1/payment_methods/{id}` (`RetrievePaymentMethod.EXPAND`).

The simulator's object: `id`, `object`, `amount`, `amount_capturable`, `amount_received`, `cancellation_reason`,
`capture_method`, `client_secret`, `created`, `currency`, `customer`, `last_payment_error`, `latest_charge`, `livemode`,
`payment_method`, `payment_method_types` (`["card"]`), `receipt_email`, `status`.

`GET /v1/payment_intents/{id}`: current state, `latest_charge` after a confirmation attempt, and `resource_missing` for
an unknown id. The customer and the payment method are ids here too (`StripeIntentView.of`).

`POST /v1/payment_intents/{id}/confirm`:

- `payment_method`: "If the payment method is attached to a Customer, it must match the `customer` that is set on this
  PaymentIntent." Also accepts optional `off_session`.
- Manual capture goes to `requires_capture` and automatic to `succeeded`. `processing` is published for asynchronous
  methods such as bank debits. A card reaching `processing` is a simulator scenario ("pending", which settles after a
  delay).
- "If the selected payment method requires additional authentication steps, the PaymentIntent will transition to the
  `requires_action` status and suggest additional actions via `next_action`." No confirmation here reaches that status:
  `StripeIntentStatus.REQUIRES_ACTION` is declared and assigned by nothing, a PaymentIntent of this simulator renders
  no `next_action` member, and authentication-required is a `402` decline, `authentication_required` with
  `decline_code` `authentication_not_handled` (`StripeDecline.AUTHENTICATION_REQUIRED`). An application that drives a
  3-D Secure step off `next_action` is handed a decline instead.

- "There is a variable upper limit on how many times a PaymentIntent can be confirmed. After this limit is reached, any
  further calls to this endpoint will transition the PaymentIntent to the `canceled` state." No number is published,
  and the simulator enforces no limit.
- A detached method is refused, both on confirm and on inline confirm.
- Failures: an unknown intent, `payment_intent_unexpected_state` ("The PaymentIntent's state was incompatible with the
  operation you were trying to perform.") for a terminal intent, a method of another customer,
  `authentication_required`, and an issuer decline.

`POST /v1/payment_intents/{id}/capture` (rule):

- "Capture the funds of an existing uncaptured PaymentIntent when its status is `requires_capture`."
- `amount_to_capture`: "which must be less than or equal to the original amount. Defaults to the full
  `amount_capturable` if it's not provided."
- "Returns a PaymentIntent object with `status=\"succeeded\"` if the PaymentIntent is capturable. Returns an error if
  the PaymentIntent isn't capturable or if an invalid amount to capture is provided."
- "Uncaptured PaymentIntents are cancelled a set number of days (7 by default) after their creation." Past that point:
  `charge_expired_for_capture`, "The charge can't be captured because the authorization has expired." The simulator
  requires `captureWindow` per account with no default, so a fixture chooses which side of day seven it sits on. Past
  the window the state read is `canceled`/`CANCELLED`, with `capturableRemainder` 0 and `capturesAccepted` false. The
  window is at least one microsecond because the deadline it fixes is stored as a timestamp beside the PaymentIntent,
  which keeps a microsecond at best: a finer window would fix the creation moment itself and the intent would be
  expired before anything could capture it.

Multicapture (rule, multicapture page):

- `final_capture` defaults to `true`. "setting `final_capture` to `false` notifies Stripe to not release the remaining
  uncaptured funds to make sure that they're captured in future requests".
- "if you confirm a 10 USD payment intent, capturing 7 USD with `final_capture=false` keeps the remaining 3 USD
  authorized". `amount_capturable` becomes `300` and `amount_received` becomes `700`.
- "The PaymentIntent remains in a `requires_capture` state until you do one of the following: Set `final_capture` to
  `true`. Make a capture without the `final_capture` parameter (because `final_capture` defaults to `true`). The
  authorization window expires."
- "Stripe allows up to 50 non-final captures for a single PaymentIntent. You can then perform one additional final
  capture to complete the payment."
- Eligibility: online card payments only, `capture_method=manual`, Amex/Visa/Discover/Mastercard/Cartes
  Bancaires/Diners Club/CUP/JCB, and IC+ pricing. The simulator does not model eligibility, so every manual intent is
  eligible.

| Capture condition (simulator)            | Answer                                                           |
|------------------------------------------|------------------------------------------------------------------|
| already captured or cancelled            | `400` `payment_intent_unexpected_state`, unless a key replays    |
| past `captureWindow`                     | `400` `charge_expired_for_capture`                               |
| above `amount_capturable`                | `400` `amount_too_large` (the code is a decision)                |
| 51st non-final capture                   | `400` `invalid_request_error` on `final_capture`, no `code`      |
| error after the state advanced           | scenario only. Reconcile by `GET`. Not a Stripe behaviour        |

`POST /v1/payment_intents/{id}/cancel` (rule):

- "You can cancel a PaymentIntent object when it's in one of these statuses: `requires_payment_method`,
  `requires_capture`, `requires_confirmation`, `requires_action` or, in rare cases, `processing`." `processing` applies
  only to ACH, ACSS, AU BECS, BACS, NZ BECS and SEPA, and "cancellation might fail due to a limited and varying
  cancellation time window". The simulator allows cancelling a card from `processing` (decision).
- "For PaymentIntents with a `status` of `requires_capture`, the remaining `amount_capturable` is automatically
  refunded."
- "Returns an error if the PaymentIntent is already canceled or isn't in a cancelable state."
- `cancellation_reason` on the request: `duplicate`, `fraudulent`, `requested_by_customer`, `abandoned`. The object adds
  `failed_invoice`, `void_invoice`, `automatic` and `expired`, "generated by Stripe internally". The simulator emits
  `expired` for an expired hold. No code is published for an unrecognised reason.
- "After it's canceled, no additional charges are made by the PaymentIntent and any operations on the PaymentIntent
  fail with an error."

### Refunds

`POST /v1/refunds` (rule, "Create a refund"):

- "you must specify a Charge or a PaymentIntent object on which to create it". Both are optional parameters only because
  `origin=customer_balance` (out of scope) replaces them.
- `amount` in minor units: "Can refund only up to the remaining, unrefunded amount of the charge". When omitted, the
  whole remainder is refunded (inference).
- `reason`: `duplicate`, `fraudulent`, `requested_by_customer`. The object adds `expired_uncaptured_charge`.
- "Creating a new refund will refund a charge that has previously been created but not yet refunded". "You can
  optionally refund only part of a charge. You can do so multiple times, until the entire charge has been refunded".
  "Once entirely refunded, a charge can't be refunded again. This method will raise an error when called on an
  already-refunded charge, or when trying to refund more money than is left on a charge". The codes are
  `charge_already_refunded`, and `refund_disputed_payment` for a disputed charge.
- Refundable while `requires_capture` (multicapture page): "you can [refund] any number of times up to the total
  captured amount minus the total refunded amount, which is the `amount_received`— `amount_refunded`." "The
  `charge.refunded` field transitions to `true` only when the final capture has been performed and the entire
  `amount_received` is refunded."
- Returns `id`, `object=refund`, `amount`, `currency`, `payment_intent`, `charge`, `status`, `created`. The last two
  references are nullable and expandable.

```json
{
  "id": "re_sim_123",
  "object": "refund",
  "amount": 500,
  "currency": "pln",
  "payment_intent": "pi_sim_123",
  "charge": "ch_sim_123",
  "reason": "requested_by_customer",
  "status": "succeeded",
  "created": 1788772800
}
```

Refund object (rule): `status` is one of `pending`, `requires_action`, `succeeded`, `failed`, `canceled`. `next_action`:
"If the refund has a status of `requires_action`, this property describes what the refund needs to continue
processing." `pending_reason` is one of `processing`, `insufficient_funds`, `charge_pending`. `failure_reason` is one of
`lost_or_stolen_card`, `expired_or_canceled_card`, `charge_for_pending_refund_disputed`, `insufficient_funds`,
`declined`, `merchant_request`, `unknown`.

| Refund condition (simulator)                                   | Answer                                                               |
|----------------------------------------------------------------|----------------------------------------------------------------------|
| both `payment_intent` and `charge`                             | `400` `invalid_request_error` on `charge`, no `code`                 |
| neither                                                        | `400` `parameter_missing` on `payment_intent`                        |
| unknown intent or charge                                       | `404` `resource_missing`                                             |
| a `charge` that did not succeed                                | `400` on `charge`, no `code`                                         |
| nothing captured yet                                           | `400` `payment_intent_unexpected_state`                              |
| everything captured already refunded                           | `400` `charge_already_refunded`                                      |
| above what is left                                             | `400` `charge_already_refunded` (no code is published for this case) |
| amount not a positive integer                                  | `400` `invalid_charge_amount`                                        |
| error after the refund is stored                               | scenario only. Not a Stripe behaviour                                |

- A `charge` refund behaves exactly like a refund of that charge's PaymentIntent.
- A refund comes to rest at the account's `refundStatus` (default `succeeded`) and never moves after that. `pending`
  and `requires_action` hold the money against further refunds, while `failed` and `canceled` do not. The payment's
  refund qualifier counts only `succeeded` refunds. Stripe publishes no rule for when `amount_refunded` moves.
- `next_action` is `null` at `requires_action`. `pending_reason` and `failure_reason` are not emitted.
- While `requires_capture`, refunds are limited to what was captured, and `amount_capturable` is unaffected.

### Charges

`GET /v1/charges/{id}` returns the charge that `latest_charge` names, or `resource_missing`. The published semantics:

- `paid`: "`true` if the charge succeeded, or was successfully authorized for later capture."
- `captured`: "If the charge was created without capturing, this Boolean represents whether it is still uncaptured or
  has since been captured." On the multicapture page: "The `captured` field for a charge only becomes `true` after a
  final capture or authorization reversal".
- `refunded`: "Whether the charge has been fully refunded. If the charge is only partially refunded, this attribute will
  still be false."
- `status` is `succeeded`, `pending` or `failed`. That is a different vocabulary from PaymentIntent status and must not
  be mapped through it.

| Simulator decision                                                                                                                                  |
|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| Fields: `id`, `object`, `amount`, `amount_captured`, `amount_refunded`, `captured`, `currency`, `paid`, `payment_intent`, `payment_method`, `refunded`, `status` |
| One charge per confirmation attempt, inline or `/confirm`, successful or declined. Never on create without confirm, capture or cancel. `latest_charge` is the newest |
| `status` is fixed when the charge is minted: `succeeded` or `failed`. `pending` is never reached                                                    |
| `paid` is true exactly when the status is `succeeded`, including an uncaptured or released hold                                                      |
| `captured` is true once the authorization is closed and something was captured. A cancelled hold that captured nothing stays `false`, and Stripe's two sentences do not settle that case |
| `refunded` is true once the authorization is closed, something was captured, and `amount_refunded` equals `amount_captured`                         |
| `amount_captured` sums the capture operations and `amount_refunded` sums the settled refunds. Both are 0 on a failed charge                         |
| An intent confirmed before charges existed has `latest_charge` `null`                                                                              |
| Not on the control read surface and not counted by a reset. No scenarios and no events                                                              |

### PaymentMethods

Published:

- `GET /v1/payment_methods`: "Returns a list of all PaymentMethods." `customer` and `type` are optional. Without
  `type`, "the list returns all payment method types except `custom`".
- `GET /v1/customers/{id}/payment_methods`: "Returns a list of PaymentMethods for a given Customer."
- A list is `object=list`, `url`, `has_more`, `data[]` (example).
- A card is `id`, `object=payment_method`, `type=card`, `customer`, `created`, `billing_details`, and `card` with
  `brand`, `funding`, `last4`, `exp_month`, `exp_year`, `country`, `networks`, `checks` (example).
- `customer`: "This will not be set when the PaymentMethod has not been saved to a Customer."
- `paypal` hash, expanded with `?query=paypal`: `payer_email` ("Values are provided by PayPal directly (if supported) at
  the time of authorization or settlement. They cannot be set or mutated."), `payer_id` ("PayPal account PayerID. This
  identifier uniquely identifies the PayPal customer.") and `country` (same wording as `payer_email`).
- Detach returns the method with `customer` null (example). "Detachment is permanent and irreversible — once detached,
  a PaymentMethod can no longer be used for payments or re-attached to a Customer."

| Simulator decision                                                                                                                                                  |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Methods exist only by fixture (`providerIds.paymentMethodId` is required). A fixture is refused if no stripe account of the run matches its `merchantId`, if the id is longer than 128 characters — the width a PaymentIntent of this simulator carries its `payment_method` in (`StripePaymentMethod.LONGEST_PAYMENT_METHOD_ID`) — or if it begins with `pi_sim_`, `re_sim_`, `cus_sim_`, `seti_sim_`, `ch_sim_` or `ephkey_sim_` (`A_PAYMENT_METHOD_ID_IS_NOT_A_MINTED_OBJECT_ID`). This list carries `cus_sim_` and the `customerId` list does not: a payment method is not a customer, and a seeded customer is |
| Fixture type `card` becomes `card`, `wallet` becomes `paypal`, and `psp-token` is refused (no Tokens API)                                                          |
| The `paypal` hash is three nulls. Nothing can set it                                                                                                                |
| Object: `id`, `object`, `type`, `customer`, `created`, `billing_details` and the type hash. No `livemode`                                                            |
| `billing_details`, `card.country`, `card.checks` and `card.networks.preferred` are null. `card.networks.available` is the fixture's brand                              |
| `expand[]=customer` on retrieve replaces the id with the customer object                                                                                            |
| `GET /v1/payment_methods` with an unknown customer or type gives an empty list. The customer route with an unknown customer gives `resource_missing`               |
| `has_more` is always false, there is no `limit`, and the list is newest first                                                                                       |
| Detachment is stored, and a constraint forbids a detached method having a customer. Confirm refuses a detached method, and a repeated detach or a detach of a never-attached method is refused |
| Removing the fixture removes the method. The customer stays                                                                                                         |
| No scenarios and no events                                                                                                                                          |

## Status Mapping

Normalized states are defined in `README.md`, "Common Normalized State Vocabulary".

| PaymentIntent                                           | Normalized          |
|---------------------------------------------------------|---------------------|
| `requires_payment_method`, `requires_confirmation`, `requires_action`, legacy `requires_source*` | `NOT_INITIALIZED` |
| `requires_capture`                                      | `READY_FOR_CAPTURE` |
| `requires_capture` after a `final_capture=false` capture, with `amount_capturable` > 0 | `PARTIALLY_CAPTURED` |
| `processing`                                            | `PROCESSING`        |
| `succeeded`                                             | `SUCCEEDED`         |
| `canceled`                                              | `CANCELLED`         |
| unrecognised                                            | `UNKNOWN`           |

- `requires_payment_method` before and after a failed attempt differ only by `last_payment_error`. The decline is
  carried on the operation.
- `requires_action` → `NOT_INITIALIZED` loses information, because the vocabulary has no "awaiting customer action"
  state.
- `canceled` also results from exceeding the confirmation limit, which is a failure.
- A partial capture under the default `final_capture=true` ends at `SUCCEEDED` and forfeits the rest.
- `PARTIALLY_CAPTURED` is derived from the capture operations and `amount_capturable`, because the native status stays
  `requires_capture`. While the authorization is open it wins over `PARTIALLY_REFUNDED` (decision).

Refund qualifiers: refund state is derived from refund objects, not from PaymentIntent status.

| Refund                                       | Payment                                              | Refund object |
|----------------------------------------------|------------------------------------------------------|---------------|
| `succeeded`, with a remainder                | `PARTIALLY_REFUNDED`                                 | `SUCCEEDED`   |
| `succeeded`, covering the whole capture      | `REFUNDED`                                           | `SUCCEEDED`   |
| `pending` or `requires_action`               | unchanged                                            | `PROCESSING`  |
| `failed` or `canceled`                       | according to the settled refunds                     | `FAILED` or `CANCELLED` |

| SetupIntent                                                     | Normalized        |
|-----------------------------------------------------------------|-------------------|
| `requires_payment_method` (before confirm), `requires_confirmation`, `requires_action` | `NOT_INITIALIZED` |
| `processing`                                                    | `PROCESSING`      |
| `succeeded`                                                     | `SUCCEEDED`       |
| `requires_payment_method` with `last_setup_error`               | `FAILED`          |
| `canceled`                                                      | `CANCELLED`       |
| unrecognised                                                    | `UNKNOWN`         |

Reconciliation (decision): `GET` is authoritative after a fault that follows an effect. A webhook and `GET` that
disagree while the flow converges are unreconciled, not `UNKNOWN`.

## Webhooks

Transport (rule, webhooks page):

- `POST` to the merchant URL with "a JSON payload that includes event information". No `Content-Type` value is
  published, and the simulator sends `application/json` (inference).
- The endpoint "must quickly return a successful status code (`2xx`)". "We consider redirect responses to webhook
  requests as failures".
- `Stripe-Signature` is sent when a `whsec_` secret is configured.

Signature (rule, "Verify webhook signatures manually"):

- The header "contains a timestamp and one or more signatures that you must verify. The timestamp has a `t=` prefix, and
  each signature has a scheme prefix. Schemes start with `v`, followed by an integer. Currently, the only valid live
  signature scheme is `v1`. To aid with testing, Stripe sends an additional signature with a fake `v0` scheme, for test
  events".
- Sample: `t=1492774577,v1=5257a869...,v0=6ffbb59b...`, all on one line.
- The signed string is "The timestamp (as a string)", "The character `.`", then "The actual JSON payload (that is, the
  request body)". The MAC is HMAC-SHA256: "To prevent downgrade attacks, ignore all schemes that aren't `v1`".
- During a secret roll there are several `v1` signatures, with the previous secret kept "for up to 24 hours".
- "Our libraries have a default tolerance of 5 minutes between the timestamp and the current time. You can change this
  tolerance by providing an additional parameter when verifying signatures." "Don't use a tolerance value of `0`." This
  is a receiver-library default, not a server rule. The simulator can send a stale `t` (`webhookTimestampAge`) and a
  corrupted signature (`webhookSignature=CORRUPTED`). It never enforces a tolerance itself.
- "Stripe generates the timestamp and signature each time we send an event to your endpoint. If Stripe retries an
  event ... then we generate a new signature and timestamp for the new delivery attempt."
- Retries: "Stripe attempts to deliver events to your destination for up to three days with an exponential back off in
  live mode... We retry event deliveries created in a sandbox three times over the course of a few hours." The
  simulator's schedule is configurable.
- A delivery whose account is no longer configured is not sent unsigned. An attempt at its own target makes it
  `UNDELIVERABLE`; a screen retry naming another target is refused with that reason and changes nothing (simulator
  decision).

Delivery (rule): "Stripe doesn't guarantee the delivery of events in the order that they're generated". "Webhook
endpoints might occasionally receive the same event more than once", so "Track event IDs to identify duplicate
deliveries". "Snapshot events record `created` in seconds, so distinct events can share a timestamp. Don't use `created`
to determine event order or whether you've already processed an event". "In some
cases, two separate Event objects are generated and sent. To identify these duplicates, use the ID of the object in
`data.object` along with the `event.type`."

Event object (rule): `id`, `object=event`, `account`, `api_version`, `context`, `created`, `data`, `livemode`,
`pending_webhooks`, `request`, `type`. `api_version`, `account`, `context` and `request` are nullable. The simulator
emits:

```json
{
  "id": "evt_...",
  "object": "event",
  "api_version": "2026-01-01",
  "type": "payment_intent.succeeded",
  "created": 1788772800,
  "livemode": false,
  "pending_webhooks": 0,
  "request": {"id": null, "idempotency_key": null},
  "data": {"object": { "...": "the same PaymentIntent or Refund object the API returns" }}
}
```

Events are scheduled through the control API, or automatically when a pending outcome lands, against a PaymentIntent
or a Refund. A PaymentIntent event must match the state the intent has reached.

| Event (Stripe's description)                                                                                                         | `data.object` | Simulator                               |
|--------------------------------------------------------------------------------------------------------------------------------------|---------------|-----------------------------------------|
| `payment_intent.amount_capturable_updated`: "Occurs when a PaymentIntent has funds to be captured. Check the `amount_capturable` property on the PaymentIntent to determine the amount that can be captured." Also "We send `payment_intent.amount_capturable_updated` on every capture, regardless of `amount_to_capture` and `final_capture` values" | PaymentIntent | only while `requires_capture` |
| `payment_intent.succeeded`: "Occurs when a PaymentIntent has successfully completed payment."                                          | PaymentIntent | yes                                     |
| `payment_intent.payment_failed`: "Occurs when a PaymentIntent has failed the attempt to create a payment method or a payment."        | PaymentIntent | yes                                     |
| `payment_intent.canceled`: "Occurs when a PaymentIntent is canceled."                                                                 | PaymentIntent | yes                                     |
| `payment_intent.requires_action`: "Occurs when a PaymentIntent transitions to requires_action state."                                | PaymentIntent | no                                      |
| `charge.refunded`: "Occurs whenever a charge is refunded, including partial refunds. Listen to `refund.created` for information about the refund." | Charge | no                                    |
| `refund.created`: "Occurs whenever a refund is created."                                                                              | Refund        | yes (the refund event the simulator emits) |
| `refund.updated`: "Occurs whenever a refund is updated." `charge.refund.updated`: "Occurs whenever a refund is updated on selected payment methods. For updates on all refunds, listen to `refund.updated` instead." | Refund        | no, because a refund never moves        |
| `refund.failed`: "Occurs whenever a refund has failed."                                                                               | Refund        | no                                      |
| `setup_intent.succeeded`: "Occurs when an SetupIntent has successfully setup a payment method." `setup_intent.setup_failed`: "Occurs when a SetupIntent has failed the attempt to setup a payment method." | SetupIntent   | no                                      |
| `payment_method.attached`, `payment_method.detached`                                                                                  | PaymentMethod | no                                      |

Stripe sends `payment_intent.amount_capturable_updated` "on every capture, regardless of `amount_to_capture` and
`final_capture` values". Here the event reports an intent only while it is `requires_capture`
(`StripeEventType.reports`), so a capture that closes the authorization reports `payment_intent.succeeded` alone.

`charge.refunded` and `refund.created` carry different objects, and a real account receives both.

Scenarios (decision): duplicate delivery with the same event id, delayed delivery, an invalid signature, a stale `t`,
and webhook state that eventually agrees with `GET` unless a scenario says otherwise.

## Required Error Shape

The body is `{"error": {...}}`. Attributes (rule): `advice_code`, `charge`, `code`, `decline_code`, `doc_url`,
`message`, `network_advice_code`, `network_decline_code`, `param`, `payment_intent`, `payment_method`,
`payment_method_type`, `request_log_url`, `setup_intent`, `source`, `type`.

- Only `type` is non-nullable. `message` is nullable ("For card errors, these messages can be shown to your users").
- `type` is one of `api_error`, `card_error`, `idempotency_error`, `invalid_request_error`.
- `error.payment_intent` and `error.setup_intent` are full objects: "The PaymentIntent object for errors returned on a
  request involving a PaymentIntent". The simulator embeds the full PaymentIntent it returns elsewhere, and a truncated
  SetupIntent (see "SetupIntents").
- `code` appears only "For some errors that could be handled programmatically". "When an API error has a `code` value,
  its `doc_url` attribute contains a link to that error code's entry in the table." The link is
  `https://docs.stripe.com/error-codes#` plus the code with `_` replaced by `-`, which holds for all 180 anchors on the
  page. With no code there is no `doc_url`.
- `card_error` covers every payment failure: "Payment errors—sometimes called 'card errors' for historical
  reasons—cover a wide range of common problems", and "For historical reasons, payment errors have the type
  `CardException`. But in fact, they can represent a problem with any payment, regardless of the payment method."
- Error codes, error types and decline codes are separate vocabularies. "If a card issuer declines a charge and you get
  an API error, the error includes a `decline_code` that describes the reason. Stripe decline codes are more specific
  than card issuer decline codes."

Published decline sample ("Declines"):

```json
{
  "error": {
    "code": "incorrect_cvc",
    "doc_url": "https://docs.stripe.com/error-codes#incorrect-cvc",
    "message": "...",
    "param": "cvc",
    "type": "card_error"
  }
}
```

A decline of this simulator carries no `param`, so `incorrect_cvc` is sent without the sample's `"param": "cvc"`
(`StripeErrors.declined`). `param` is sent on an `invalid_request_error`, which names the field it refused
(`StripeErrors.invalidRequest`).

Stripe's worked failure (example, "Make a payment [Server-side]"):

```json
{
  "error": {
    "code": "authentication_required",
    "decline_code": "authentication_not_handled",
    "doc_url": "https://docs.stripe.com/error-codes#authentication-required",
    "message": "This payment required an authentication action to complete, but `error_on_requires_action` was set.",
    "payment_intent": {
      "id": "pi_1G8JtxDpqHItWkFAnB32FhtI",
      "object": "payment_intent",
      "amount": 1099,
      "status": "requires_payment_method",
      "last_payment_error": {
        "code": "authentication_required",
        "decline_code": "authentication_not_handled",
        "type": "card_error"
      }
    },
    "type": "card_error"
  }
}
```

| Code                                                                                   | `type`                  | HTTP | Status evidence                                                  | Published text                                                                                                             |
|----------------------------------------------------------------------------------------|-------------------------|------|------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `card_declined`                                                                        | `card_error`            | 402  | rule ("Setup Intents API")                                       | "The card has been declined. When a card is declined, the error returned also includes the `decline_code` attribute with the reason why the card was declined." |
| `authentication_required`                                                              | `card_error`            | 402  | rule, and the type from the example                              | "The payment requires authentication to proceed. If your customer is off session, notify your customer to return to your application and complete the payment." |
| `expired_card`                                                                         | `card_error`            | 402  | rule                                                             | "The card has expired. Check the expiration date or use a different card."                                                 |
| `incorrect_cvc`, `processing_error`                                                    | `card_error`            | 402  | rule                                                             | the simulator sends no message                                                                                              |
| `payment_intent_unexpected_state`                                                      | `invalid_request_error` | 400  | inference from "400 Bad Request — The request was unacceptable" | "The PaymentIntent's state was incompatible with the operation you were trying to perform."                                |
| `setup_intent_unexpected_state`                                                        | `invalid_request_error` | 400  | inference                                                        | "The SetupIntent's state was incompatible with the operation you were trying to perform."                                  |
| `resource_missing`                                                                     | `invalid_request_error` | 404  | inference from "404 Not Found — The requested resource doesn't exist" | "The ID provided isn't valid. Either the resource doesn't exist, or an ID for a different resource has been provided."     |
| `charge_already_captured`, `charge_already_refunded`, `charge_expired_for_capture`     | `invalid_request_error` | 400  | inference                                                        | expired: "The charge can't be captured because the authorization has expired. Authorization and capture charges must be captured within a set number of days (7 by default)." |
| `idempotency_key_in_use`                                                               | `idempotency_error`     | 409  | rule                                                             | see "Idempotency"                                                                                                           |
| a reused key with different parameters                                                  | `idempotency_error`     | 400  | the status is a decision                                         |                                                                                                                            |
| `parameter_missing`, `parameter_unknown`, `parameter_invalid_integer`, `invalid_charge_amount`, `amount_too_small`, `amount_too_large` | `invalid_request_error` | 400 | inference | `invalid_charge_amount`: "The specified amount is invalid. The charge amount must be a positive integer in the smallest currency unit" |
| missing or invalid API key                                                             | `invalid_request_error` | 401  | rule and probe. No code is published                             | see "Authentication and Transport"                                                                                          |

- `insufficient_funds` is both an error code ("The customer's account has insufficient funds to cover this payment")
  and a decline code ("The card has insufficient funds to complete the purchase."). For a card, the pairing is
  `code=card_declined` with `decline_code=insufficient_funds`, which is what the simulator emits.
- `authentication_required` is both an error code and the first decline code.
- There is no unsupported-currency error code. `currency_not_supported` is a decline code ("The card doesn't support the
  specified currency"), and `payment_method_currency_mismatch` is the error code for a currency that disagrees with the
  method.

- The decline scenarios are the six of `StripeDecline`: `generic_decline`, `insufficient_funds`, `expired_card`,
  `incorrect_cvc`, `processing_error` and `authentication_required`. Neither `currency_not_supported` nor
  `payment_method_currency_mismatch` is among them, so no scenario refuses a currency.
- No maximum amount is published for `amount_too_large`.
- The decline table's "Next steps" column separates retryable reasons from final ones (`issuer_not_available` "Attempt
  the payment again", `lost_card` not). `retryWithAnotherSource` is not populated for Stripe.

## Test Data

Declines (rule, "Simulate a declined payment"). The simulator produces each by the scenario code shown:

| Number             | Error code         | Decline code         | Scenario code        |
|--------------------|--------------------|----------------------|----------------------|
| `4000000000000002` | `card_declined`    | `generic_decline`    | `generic_decline`    |
| `4000000000009995` | `card_declined`    | `insufficient_funds` | `insufficient_funds` |
| `4000000000000069` | `expired_card`     | n/a                  | `expired_card`       |
| `4000000000000127` | `incorrect_cvc`    | n/a                  | `incorrect_cvc`      |
| `4000000000000119` | `processing_error` | n/a                  | `processing_error`   |

- `4000002500003155`: "This card requires authentication for off-session payments unless you set it up for future
  payments. After you set it up, off-session payments no longer require authentication. However, on-session payments
  with this card always require authentication." The simulator's `authentication_required` scenario imitates this card.
- `4000000000003220`: "3D Secure authentication must be completed for the payment to be successful."
- `4242424242424242` succeeds. `4000002500001001` supports multicapture, and `4000008400000076` does not.
- `pm_card_*` ids (for example `pm_card_visa`) can stand in for a card number.

## Minimum Test Fixtures

- a manual hold, then capture: success, and a transient failure after which `GET` shows `succeeded`,
- a hold cancelled before capture,
- a capture after the deadline: `charge_expired_for_capture`,
- an off-session charge: success, `authentication_required`, and `card_declined` with a `decline_code`,
- a method of another customer, and an already-detached method (refused),
- an unknown method, PaymentIntent and charge: `resource_missing`,
- idempotency: an identical replay (stored answer plus `Idempotent-Replayed: true`), different parameters
  (`idempotency_error`), and concurrent use (`409` `idempotency_key_in_use`),
- a partial refund followed by a full refund, and a refund above the remainder (refused),
- a webhook that is valid, one with an invalid signature, one with a stale `t`, and a duplicate event id.

## Seeded Personas

One persona per intent, each seeding one customer with one saved card (`StripePersonas`). Core rules are in
`README.md`, "Seeded Personas".

| Persona                                        | Intent                                      | Writes                            |
|------------------------------------------------|---------------------------------------------|-----------------------------------|
| `stripe-customer-with-visa-success`            | `a-saved-card-that-succeeds`                | `preauth-success-capture-success` |
| `stripe-customer-with-visa-that-is-declined`   | `a-saved-card-that-is-declined`             | `preauth-success-capture-decline` |
| `stripe-customer-whose-capture-answer-is-lost` | `a-saved-card-whose-capture-answer-is-lost` | `capture-accepted-response-lost`  |

| What it seeds                   | Value                                                                                                             |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `alias`                         | the persona's own name                                                                                            |
| `ownerId`                       | `a-customer-stripe-holds-a-card-for` (`StripePersonas.THE_OWNER`)                                                 |
| `type`                          | `card`, which becomes a PaymentMethod of type `card`                                                              |
| `providerIds.paymentMethodId`   | the published `pm_card_visa`, **not** minted: this simulator mints no PaymentMethod id anywhere, so an invented one would be a shape Stripe never published (`StripePersonas.HOW_THE_IDS_ARE_MINTED`) |
| `providerIds.customerId`        | minted by `CreateCustomer.aCustomerId`, the same minting `POST /v1/customers` uses: `cus_sim_` and 16 hexadecimal characters, so this account's own key retrieves it |
| `last4`                         | `4242`, the published success card of "Test Data"                                                                 |
| `expMonth` / `expYear`          | `12` / `2030`, because Stripe publishes no expiry per test card                                                   |
| `brand`, `funding`              | `unpublished`: the pages this contract was read from publish the names of those fields and not their values for any one card |

- **One of these three per run.** All three seed the one published `pm_card_visa`, and a run claims a `providerIds`
  value for one instrument, so the second is `409`. Seed the others into runs of their own
  (`StripePersonas.ONE_PUBLISHED_IDENTIFIER_MEANS_ONE_AT_A_TIME`). The persona's own words in the catalogue and the
  preview say so; because `pm_card_visa` is published rather than minted, the preview states no unforeseeable refusal
  over it (`PersonaFixture.theIdsItPublishes`).
- Seed it into an account this run configures for Stripe: every route that answers for a customer or a payment method
  resolves it through the secret key that authenticated the call
  (`StripePersonas.AN_INSTRUMENT_BELONGS_TO_AN_ACCOUNT`).

## What Still Cannot Be Trusted Here

- Only the no-key request was observed against Stripe. Everything else is documentation.
- HTTP statuses are published for three cases only: `402` (a failed payment), `409` (a key conflict) and `401` (no
  authentication). The other statuses are inferences.
- Whether create still accepts `payment_method_types` is unknown. The two create routes differ here: SetupIntent refuses
  it and PaymentIntent ignores it.
- The ephemeral-key TTL comes from one example.
- PaymentSheet's first call, `v1/elements/sessions`, is in none of the 419 paths of Stripe's OpenAPI specification, so
  `stripe-mock` would answer `404` and this simulator stops at the ephemeral key. The SDK host can be moved on mobile
  only through non-public members, and on the web not at all.
- Transient failures after an effect (on capture and refund) are this project's fault model. Stripe publishes only
  "Treat requests that return `500` errors as indeterminate".
- No message is published for an unrecognized address or for a method an address does not answer. The simulator
  answers both `404` with `error.type` `invalid_request_error` and no `code`, because `405` is absent from the
  published status list and a code it did not publish would carry a `doc_url` to an anchor that does not exist; the
  `message` is the simulator's own sentence and names itself as such.
