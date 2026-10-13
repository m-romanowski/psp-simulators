# Worldline Direct Simulator Contract

## What This File Covers

The vendor contract for the Worldline **Direct** Server API (`docs.direct.worldline-solutions.com`, major version `v2`,
Java SDK namespace `com.onlinepayments`), and what this simulator answers on each route. Worldline **Connect** / Global
Collect (`docs.connect.worldline-solutions.com`) is a different product; it shares the `X-GCS-` prefix and the
`errorId`/`errors[]` envelope but not its webhook transport or retry schedule. Anything sourced from Connect is marked.

Each statement names its evidence class (`README.md`, "Evidence Classes"). "Published schema" is the machine-readable
contract; "published rule" and "published example" are the prose pages. No live probe: the payment endpoints need
merchant credentials.

| Route                                                        | What the simulator does                                                                                     | Key deviations                                                                                                                  |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `POST /v2/{merchantId}/hostedtokenizations`                  | `200` with `hostedTokenizationId`, `hostedTokenizationUrl`, `partialRedirectUrl`; session lives 3 hours      | both URLs name this simulator's page, not the vendor's host; `partialRedirectUrl` keeps the published double slash; no payer is declined |
| `GET`/`POST /hostedtokenization/tokenization/form/{id}`      | the payer page at `hostedTokenizationUrl`: pick a test card and store it, or leave                          | the whole page is a simulator decision ("The Hosted Tokenization Page")                                                         |
| `GET /v2/{merchantId}/hostedtokenizations/{id}`              | `200` with `token` and `tokenStatus` once finished, `200 {}` before                                         | tokens are never temporary; unknown session is `404` with the unknown-payment `errorCode` and no `errors.id`                     |
| `POST /v2/{merchantId}/payments`                             | `201`, `createPaymentResponse`, `Location` header; `token` or `hostedTokenizationId`; `402` on decline      | omitted `authorizationMode` is treated as `FINAL_AUTHORIZATION` (inference); both instruments at once refused                   |
| `POST /v2/{merchantId}/payments/{paymentId}/capture`         | `201`, `captureResponse`, new `payment.id`                                                                  | landing status is `worldline.capture-landing`; response body is an inference                                                    |
| `POST /v2/{merchantId}/payments/{paymentId}/cancel`          | `200`, `cancelPaymentResponse` at `CANCELLED` / `UNSUCCESSFUL` / `6`                                        | full cancellation only; never `61`; amount read at the root or under `order`                                                    |
| hosted checkout, refund, `GET` payment / details / captures / refunds, webhooks, webhook test routes, approval | **not served**                                                         | a captured payment reports `refundsAccepted` `false`                                                                            |

Account settings that choose between vendor contradictions:

| Setting                          | Values (default first)                                                                                  | Chooses between                                                     |
|----------------------------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `worldline.signed-path-version`  | `V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT`, `V1_AS_THE_WORKED_EXAMPLES_SIGN_IT`                | `v2` or `v1` inside the signed string; routing is always `v2`       |
| `worldline.capture-landing`      | `CAPTURED_9`, `CAPTURE_REQUESTED_91`, `PENDING_CAPTURE_9`                                               | the four publications of a successful capture's status              |
| `worldline.authentication`       | `THE_SIGNATURE_IS_VERIFIED`, `THE_SIGNATURE_IS_NOT_VERIFIED`                                            | simulator decision: permissive auth for local tests; `apiKey` must still name the account |

Two contradictions are crossed by reading both forms: the idempotence-key header under either spelling, and the cancel
amount at the root or under `order`. A request carrying both forms with different values is refused, because no
precedence is published.

## Sources

All fetched on 2026-09-12. Section names are Worldline's.

| Page                              | URL (under `https://docs.direct.worldline-solutions.com/en/integration/`)                  | Sections used                                                                                                                                                                                 |
|-----------------------------------|---------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Developer guide                   | `api-developer-guide`                                                                        |                                                                                                                                                                                               |
| API environment and versioning    | `api-developer-guide/api`                                                                    | "Environments and endpoints", "API versioning"                                                                                                                                                |
| Authentication                    | `api-developer-guide/authentication`                                                         |                                                                                                                                                                                               |
| Manual authentication             | `api-developer-guide/manual-authentication`                                                  | "Implementation", "Collect key request information", "Prepare date headers", "Prepare canonical headers", "Build signature subject (create string-to-hash)", "Build authorisation header", "Troubleshooting mismatches" |
| Idempotent requests               | `api-developer-guide/idempotent-requests`                                                    | "Implement Idempotent Requests", "Possible outcomes of idempotent requests", "Response headers for subsequent requests", "Supported Methods"                                                  |
| Statuses                          | `api-developer-guide/statuses`                                                               | "Possible outcomes", "Numeric status codes"                                                                                                                                                   |
| Maintenance operations            | `api-developer-guide/maintenance-operations`                                                 | "Capture payments", "Cancel payments", "Refund payments", "Operation limits"                                                                                                                  |
| Authorisation and direct sale     | `api-developer-guide/authorisation-and-directsale`                                           | "Authorisation mode", "Process requests"                                                                                                                                                      |
| Transaction feedback              | `api-developer-guide/transaction-feedback`                                                   | "Decision matrix"                                                                                                                                                                             |
| Webhooks                          | `api-developer-guide/webhooks`                                                               | "Best practices", "Configuration", "Messages", "Status Changes", "Response", "Failure Recovery", "Testing"                                                                                    |
| API troubleshooting               | `api-developer-guide/api-troubleshooting`                                                    | "HTTP response codes", "Error Notifications", "Fix errors.id"                                                                                                                                 |
| Hosted Tokenization Page          | `basic-integration-methods/hosted-tokenization-page`                                         |                                                                                                                                                                                               |
| Hosted Checkout Page              | `basic-integration-methods/hosted-checkout-page`                                             |                                                                                                                                                                                               |
| Server-to-server                  | `basic-integration-methods/server-to-server`                                                 |                                                                                                                                                                                               |
| Java SDK                          | `how-to-integrate/server-sdks/java`                                                          |                                                                                                                                                                                               |

Also: Google Pay, https://docs.direct.worldline-solutions.com/en/payment-methods-and-features/payment-methods/google-pay;
the machine-readable contract, https://payment.preprod.direct.worldline-solutions.com/v1/public-contract-definition.yaml
(OpenAPI 3.0.0, `info.title` "API specifications", `info.version` `2.506.0`); and, for Connect only,
https://docs.connect.worldline-solutions.com/documentation/webhooks/.

`https://developer.payments.worldline.com/` does not resolve. `worldline-prod-{1,2}.dotcms.cloud` serve the same pages
as CMS origins; the documentation host's canonical tags point at `docs.direct.worldline-solutions.com`.

Out of scope although published: payouts, mandates, payment links, hosted fields, batches, DCC, surcharging,
detokenization, standalone 3-D Secure.

## Authentication and Transport

- Endpoints (published rule, "Environments and endpoints"): test `https://payment.preprod.direct.worldline-solutions.com/`,
  production `https://payment.direct.worldline-solutions.com/`, each "(Generic European endpoint)". Base URL and
  merchant id are configurable. **Simulator decision:** `merchantId` must be one path segment, carrying no `/`, `?` or
  `#`, because the simulator puts it in the path and a value carrying one would address a route other than the one it
  named. Worldline publishes no shape for a `merchantId` anywhere.
- Authorization header (published rule, "Build authorisation header"):
  `Authorization: GCS v1HMAC:<your-api-key>:<base64-encoded-signature>`.
- JSON bodies. `Content-Type: application/json` with a body; **the empty string** without one: "For requests without a
  body, set Content-Type to an empty string . Do not omit or set to null ." The content type is line two of the signed
  string.
- `Date` is RFC 1123 UTC and "Must be identical for both request header and the string-to-hash". `x-gcs-date` when
  signed as a canonical header.
- "Our platform will reject requests with a timestamp older than 5 minutes." The simulator enforces five minutes.
- This simulator verifies the signature by default, so a client whose signing is wrong finds out here. Setting
  `worldline.authentication` to the permissive value leaves the signature and the `Date` unchecked while the `apiKey`
  must still name the account; the two values and the reason for the second are
  `WorldlineAuthentication.WHY_THERE_ARE_TWO`. Worldline publishes no such mode, so it is this simulator's own.
- An invalid signature, stale date or unknown API key is `403`, not `401`; `401` is not a published status. A credential
  or account mismatch is `403` with `errors.id` `ACCESS_TO_MERCHANT_NOT_ALLOWED` (published example, "Error
  Notifications").

Canonical string (published rule, "Build signature subject (create string-to-hash)"), each element terminated by `\n`:

```
{HTTP_METHOD}\n
{Content-Type}\n
{Date}\n
{canonical-x-gcs-header-1}\n
{canonical-x-gcs-header-N}\n
{request-path}\n
```

then "Hash the signature subject (string-to-hash) using HMAC-SHA256 (UTF-8 encoding) with your API Secret . Then
Base64-encode the resulting signature".

Canonical header normalisation (published rule, "Prepare canonical headers"): "Lowercase the header key.", "Trim
leading/trailing whitespace from the header value.", "Collapse any internal whitespace runs in the header value to a
single space.", "Format each header key-value pair as {lowercased-key}:{normalized-value} .", "Sort the canonical
key-value pairs alphabetically by key." Only `x-gcs-*` headers participate, and "If you include any x-gcs-* header in
the signature subject, make sure to add it in the actual request."

| Contradiction                         | Publication A                                                                                                                                              | Publication B                                                                                                                                                           | Simulator                                                     |
|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| path version in the signed string     | `v2`: "The major versions of our APIs are indicated in the URLs (e.g., v2)"; property table: "including the API version and your merchantId i.e. "/v2/merchantId/hostedcheckouts""; every schema path | `v1`: the manual-authentication worked examples end `/v1/{merchantId}/payments\n` and `/v1/{merchantId}/payments/{paymentId}\n`; the schema itself is served from `/v1/` | `worldline.signed-path-version`; routes `v2` under both      |
| idempotence-key header name           | `X-GCS-Idempotence-Key` ("Supported Methods": "Using universally unique identifiers (UUIDs) for generating the X-GCS-Idempotence-Key , minimising the risk of key collisions."; Java SDK: "The SDK will send an X-GCS-Idempotence-Key header") | `X-GCS-Idempotency-Key`, canonicalised `x-gcs-idempotency-key`, in the manual-authentication table and all six language samples                                        | both read; both with different values refused                 |

`X-GCS-Authentication-Token` is named on no Direct page; the simulator only redacts it from the audit trail.

### Idempotency

Published rule, "Implement Idempotent Requests" and "Possible outcomes of idempotent requests":

- a completed request repeated under the same key is **replayed, even with a different payload**: "Our server will
  respond with the same outcome as the original request, even with different payloads. Updates related to the
  operation, such as payment status, may still occur.",
- `409` means still running: "A response with an HTTP status code of 409 (Conflict) will indicate that the request is
  currently being processed.",
- the key "Has a maximum length of 40 characters." and "Should be ASCII compliant and not support UTF-8.",
- "Do not use the same key twice within the idempotence period (at least 24 hours).",
- a repeat carries `X-GCS-Idempotence-Request-Timestamp`, "the timestamp of the initial request in milliseconds since
  January 1st, 1970 00:00:00 UTC",
- supported on `CreatePayment`, `CancelPayment`, `RefundPayment`, `CapturePayment`, `CompletePayment`, `CreatePayout`,
  `SubsequentPayment`; on any other call the key is ignored (Java SDK),
- `aPIError.retriable` (published schema): "the same request can safely be sent again with a new idempotence key", so a
  retriable failure is retried under a **new** key.

The simulator refuses a key over 40 characters or outside ASCII.

## Minimum API Surface

### Payment Identifiers and the Operation List

- **Every maintenance operation mints a new `payment.id`** (published rule, "Status Changes"): "Once you perform such a
  maintenance operation, the payment.id of this transaction changes". Platform-driven status changes ("offline events")
  do not.
- Grammar `PAYID_PAYIDSUB` (published rule, "Decision matrix"). Examples `3066019730_0`, `3066019730_1` (schema),
  `4373719828_0` ("Decision matrix"). "use the full, intact payment.id". The increment is not to be relied on: "we
  strongly recommend not building your business operations around it."
- `GetPayment` "accepts only the original payment.id"; `GetPaymentDetails` "accepts any payment.id from either the
  initial CreatePayment/CreateHostedCheckout request or subsequent maintenance operations."
- The operation list is `Operations` on `paymentDetailsResponse` (schema) and `operations` in the webhooks page's
  sample. Each entry is an `operationOutput` with its own `id`, `amountOfMoney`, `status`, `statusOutput`.
- Capture and refund ids are payment ids (`captureResponse.id`, `refundResponse.id`, both `$ref` `paymentId`). There is
  no `capture_` or `refund_` namespace. `captureId` exists only as a `refundRequest` field, "only necessary for
  Paypal/PostfinancePay multi-capture payments."

Vendor walkthrough (published example, "Status Changes"):

| `payment.id`  | operation or status change                                                            | `statusCode` |
|---------------|---------------------------------------------------------------------------------------|--------------|
| `payment.id1` | authorisation via `CreatePayment` / `CreateHostedCheckout`, webhook `payment.created` | `5`          |
| `payment.id2` | capture via `CapturePayment`, webhook `payment.capture_requested`                     | `91`         |
| `payment.id2` | platform confirms the capture, webhook `payment.captured`                             | `9`          |
| `payment.id3` | refund via `RefundPayment`, webhook `refund.refund_requested`                         | `81`         |
| `payment.id3` | platform confirms the refund, webhook `payment.refunded`                              | `8`          |

Simulator: the control read `GET /__simulator/worldline/state/{anyIdentifier}` resolves any id a payment has answered
to and reports `addressedBy` and `answersTo` beside `providerObjectId`.

### Create Hosted Tokenization

`POST /v2/{merchantId}/hostedtokenizations`

Response (published schema, `createHostedTokenizationResponse`):

```json
{
  "hostedTokenizationId": "2f4d08d6ddfc411e944e81c54d824a99",
  "hostedTokenizationUrl": "https://simulator.example/hostedtokenization/tokenization/form/2f4d08d6ddfc411e944e81c54d824a99",
  "partialRedirectUrl": "simulator.example//hostedtokenization/tokenization/form/2f4d08d6ddfc411e944e81c54d824a99"
}
```

- `partialRedirectUrl` is deprecated (`x-deprecated-by: hostedTokenizationUrl`) **and populated** in the published
  example: `"preprod.direct.worldline-solutions.com//hostedtokenization/tokenization/form/2f4d08d6ddfc411e944e81c54d824a99"`,
  with "You will need to add the protocol and the relevant subdomain to this URL". The simulator keeps the double slash.
- Simulator: both URLs are built from `simulator.public-base-url`, as Grow's `url` is. `hostedTokenizationUrl` is
  `{public-base-url}/hostedtokenization/tokenization/form/{hostedTokenizationId}`, the vendor's own form path under this
  simulator's host. `partialRedirectUrl` is the host and path of `public-base-url`, a slash, then that form path, so
  it keeps the double slash. The page is served at `hostedTokenizationUrl`; nothing promises a client that the
  double-slash form routes.
- `invalidTokens`, `expiredCardTokens` and `sri` are published and optional.
- Lifetime: "Sessions have a maximum life span of 3 hours." Enforced.

### The Hosted Tokenization Page

`GET /hostedtokenization/tokenization/form/{hostedTokenizationId}` and `POST` to the same path.

What this contract holds from the vendor about the page is little:

- `hostedTokenizationUrl` is where the customer goes: `partialRedirectUrl` is to be completed "before redirecting your
  customer to this URL" ("Create Hosted Tokenization").
- The page stores a card and charges nothing. What it produces is read with `GET .../hostedtokenizations/{id}` as a
  `token` and a `tokenStatus` of `UNCHANGED`, `CREATED` or `UPDATED` ("Get Hosted Tokenization"), or charged by
  creating a payment with the `hostedTokenizationId` ("Create Payment With Stored Token").
- A session lives three hours.
- No page this contract was read from publishes a list of test cards. The published samples print one card,
  `************1111` expiring `1230`.

Nothing in this contract describes the page's fields, its look, or how it tells the embedding merchant page that the
payer finished. Every one of the following is a **simulator decision**:

- **Cards.** The payer picks a card by its alias from a list: `published-1111` (the card the samples print) and every
  card fixture seeded into the run. No field takes a card number, a CVV or a holder name. The list is the one the
  control completion offers.
- **Outcomes.** Two buttons: **Store this card** (`PAID`) and **Leave without storing a card** (`ABANDONED`). No payer
  is declined, because the vendor publishes no decline for this page. Leaving ends the session at once as `EXPIRED`,
  exactly as the control completion does. At the vendor a payer who leaves lets the session time out.
- **Same path as the control route.** A `POST` asks the provider for its completion offer, builds the request through
  `HostedCompletionControl` and calls `SimulatorProvider#complete`, as
  `POST /__simulator/worldline/hosted/{id}/complete` does. The session, the token and the card left behind are the
  same.
- **Finishing.** A finished `POST` answers `200` with a short confirmation page in place. It never prints the token.
  The page sends nothing to the page that embeds it: no redirect, no `postMessage`, no callback. The merchant learns the
  result the published way, with `GET .../hostedtokenizations/{id}` or by creating a payment with the
  `hostedTokenizationId`.
- **Refusals.** An id no run holds is `404`. A session already tokenized or past its three hours is `410`, in the
  words the control completion uses, and so is a `POST` that loses a race to another request completing the same
  session between this page's own offer check and its completion. An outcome or card the page does not offer, or a
  store naming no card, is `422` and changes nothing. Every refusal is a simulator-written HTML page.
- **Test run.** The page takes no `X-Simulator-Test-Run-Id` header and needs none. `hostedTokenizationId` is 32
  random lowercase hex digits and is unique across every run, so the id in the path names exactly one session and
  its run, as Grow's unguessable link does. A create that draws an id another run holds draws again, as it does
  within one run.
- **Framing.** The vendor's page is embedded in the merchant's page, so the answer carries no `X-Frame-Options` and no
  `frame-ancestors`. It carries `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: no-referrer` and `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline';
  form-action 'self'`, in that order and without `frame-ancestors`, so framing stays allowed. The page is one
  self-contained document
  with inline style, no script and no external resource, laid out for a phone. It carries no credential and no
  secret field.
- **Trail.** A `POST` is recorded as an inbound and an outbound `PROVIDER_API` row at the page's path, under the run
  that opened the session, naming the session. A `GET` is not recorded.

### Get Hosted Tokenization

`GET /v2/{merchantId}/hostedtokenizations/{hostedTokenizationId}`

```json
{
  "token": {
    "id": "token_123",
    "card": {
      "data": {
        "cardWithoutCvv": {
          "cardNumber": "************1111",
          "expiryDate": "1230"
        }
      }
    }
  },
  "tokenStatus": "CREATED"
}
```

- `tokenStatus`: "UNCHANGED - The token has not changed", "CREATED - The token has been created", "UPDATED - The token
  has been updated".
- `isTemporary` is required on `tokenResponse` and `tokenInfo`: "Temporary tokens have a lifespan of two hours and can
  only be used once." Which requests mint one is unpublished, so every token this simulator mints is permanent and
  answers `isTemporary` `false` (`GetHostedTokenization.theTokenOf`,
  `WorldlineTokenization.A_TOKEN_OF_THIS_SIMULATOR_IS_NEVER_TEMPORARY`). Nothing here expires a token or spends it on
  one use, so neither half of that two-hour rule can be exercised.

- Both members are optional; no example of an unfinished session is published. Simulator: `200 {}` before a payer
  finishes; session states `AWAITING_THE_PAYER` and `EXPIRED` are its own words.

### Create Hosted Checkout

**Not served.** `POST /v2/{merchantId}/hostedcheckouts`

```json
{
  "order": {
    "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" },
    "references": { "merchantReference": "order-123" }
  },
  "feedbacks": { "webhooksUrls": ["https://merchant.example/worldline/webhooks"] },
  "hostedCheckoutSpecificInput": {
    "returnUrl": "https://merchant.example/worldline/return",
    "isRecurring": true,
    "cardPaymentMethodSpecificInput": { "tokenizationMode": "createWithConsent" }
  }
}
```

- `cardPaymentMethodSpecificInputBase.tokenize` is deprecated here in favour of
  `hostedCheckoutSpecificInput.cardPaymentMethodSpecificInput.tokenizationMode` (`createWithConsent`, `createAlways`,
  `useExplicitly`, `noTokenization`), and "This property is not allowed when cardpaymentmethodspecificinput.tokenize is
  specified."
- `isRecurring` belongs to `hostedCheckoutSpecificInput`: "Only payment products that support recurring payments will be
  shown. Any transactions created will also be tagged as being a first of a recurring sequence."
  `recurring.recurringPaymentSequenceIndicator` belongs to the server-to-server card input, not to hosted checkout.
- Response (`createHostedCheckoutResponse`):

```json
{
  "RETURNMAC": "12345678-90ab-cdef-1234-567890abcdef",
  "hostedCheckoutId": "hc_123",
  "redirectUrl": "https://simulator.example/worldline/checkout/hc_123"
}
```

- `RETURNMAC`: "When the customer is returned to your site we will append this property and value to the query-string.
  You should store this data, so you can identify the returning customer."
- Lifetime three hours ("CreateHostedCheckout session is valid for three hours", "The redirectUrl is valid for three
  hours", "the hostedCheckoutId is valid for three hours"); `hostedCheckoutSpecificInput.sessionTimeout` default 180
  minutes, maximum 1440.
- On expiry at code `0` the payment moves to code `1` and a webhook is sent.

### Get Hosted Checkout Status

**Not served.** `GET /v2/{merchantId}/hostedcheckouts/{hostedCheckoutId}`

```json
{
  "status": "PAYMENT_CREATED",
  "createdPaymentOutput": {
    "paymentStatusCategory": "SUCCESSFUL",
    "payment": {
      "id": "3066019730_0",
      "status": "CAPTURED",
      "statusOutput": {
        "statusCategory": "COMPLETED",
        "statusCode": 9,
        "isCancellable": false,
        "isAuthorized": true,
        "isRefundable": true
      },
      "paymentOutput": {
        "cardPaymentMethodSpecificOutput": {
          "token": "token_123",
          "card": { "cardNumber": "************1111", "expiryDate": "1230" }
        }
      }
    }
  }
}
```

- `status` enum: `IN_PROGRESS`, `PAYMENT_CREATED`, `CANCELLED_BY_CONSUMER`.
- `paymentStatusCategory`: `SUCCESSFUL`, `REJECTED`, `STATUS_UNKNOWN`, distinct from `statusOutput.statusCategory` ("for
  API call GetHostedCheckoutStatus different values apply for this property").
- Takes a `hostedCheckoutId`, not a payment id; after three hours use `GetPayment` or `GetPaymentDetails`.

### Create Payment With Stored Token

`POST /v2/{merchantId}/payments` answers **`201`** ("a payment object was created") with a `Location` header ("The URI of
this created resource is returned in the Location header").

Authorization modes (published schema `authorizationMode`, published rule "Process requests"):

| Mode                  | Published                                                                                                                                         | Partial capture | Partial cancel |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|----------------|
| `PRE_AUTHORIZATION`   | "Only blocks the order.amountOfMoney.amount up to 30 days. You can cancel the authorisation any time, both fully and partially. Supports both full and partial captures." | yes | yes |
| `FINAL_AUTHORIZATION` | "Only blocks the order.amountOfMoney.amount for 7 days. You can cancel the authorisation any time, but only fully. Supports only full captures." | no              | no             |
| `SALE`                | "Blocks and captures the order.amountOfMoney.amount in one go."                                                                                   | —               | —              |
| unspecified           | "In case the acquirer does not allow this to be specified the authorizationMode is 'unspecified', which behaves similar to a final authorization." | no            | no             |

Simulator: an omitted `authorizationMode` is `FINAL_AUTHORIZATION` (inference from the last row).

```json
{
  "order": {
    "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" },
    "customer": { "merchantCustomerId": "customer-123" },
    "references": { "merchantReference": "order-123" }
  },
  "feedbacks": { "webhooksUrls": ["https://merchant.example/worldline/webhooks"] },
  "cardPaymentMethodSpecificInput": {
    "token": "token_123",
    "isRecurring": true,
    "authorizationMode": "SALE"
  }
}
```

Minimum pre-authorization body:

```json
{
  "order": {
    "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" },
    "references": { "merchantReference": "order-123" }
  },
  "feedbacks": { "webhooksUrls": ["https://merchant.example/worldline/webhooks"] },
  "cardPaymentMethodSpecificInput": {
    "token": "token_123",
    "authorizationMode": "PRE_AUTHORIZATION"
  }
}
```

Instead of the token, a root-level
`hostedTokenizationId` may be sent: "Use this field after a successful Hosted Tokenization session to create a payment
with the tokenized payment method details." Simulator: one of the two is required, both is refused.

Response (published schema, `createPaymentResponse`):

```json
{
  "creationOutput": {},
  "payment": {
    "id": "3066019730_0",
    "status": "PENDING_CAPTURE",
    "statusOutput": {
      "statusCategory": "PENDING_MERCHANT",
      "statusCode": 5,
      "isCancellable": true,
      "isAuthorized": true,
      "isRefundable": false
    },
    "paymentOutput": {
      "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" },
      "paymentMethod": "card",
      "cardPaymentMethodSpecificOutput": {
        "card": { "cardNumber": "************1111", "expiryDate": "1230" },
        "token": "token_123"
      }
    }
  }
}
```

- A pre-authorization succeeds at `PENDING_CAPTURE` / `PENDING_MERCHANT` / `5` (published rule, "Authorisation mode").
- `amount` is minor units, `0` to `999999999999`; `amount` and `currencyCode` required; `currencyCode` three characters.
  `merchantReference` `maxLength: 40`.
- Every declared error of this operation is `paymentErrorResponse` (`errorId`, `errors[]`, `paymentResult`). A decline is
  `402` "Payment Rejected".
- Wallets: `mobilePaymentMethodSpecificInput` with `paymentProductId`, `encryptedPaymentData` or `decryptedPaymentData`,
  `authorizationMode`, `requiresApproval`. Google Pay samples use `"paymentProductId":"320"`, `"requiresApproval":true`,
  and put `tokenize` and `recurring` under `paymentProduct320SpecificInput`. Not served.

### Three-D Secure and merchantAction

**Not served.** `createPaymentResponse.merchantAction.actionType` has six values; `REDIRECT` means "The customer needs to be redirected
using the details found in redirectData". Server-to-server branches: frictionless (`actionType=null`), challenge
(`REDIRECT`, redirect to `merchantAction.redirectData.redirectURL`, return via `cardPaymentMethodSpecificInput.returnUrl`),
no 3-D Secure (`null`). The challenge pairs with `REDIRECTED` / `46` "Waiting authentication", where an abandoned check
stays "indefinitely". The statuses page writes `MerchantAction.ActionType`; the schema spelling governs.

### Approval has no endpoint

`requiresApproval` (on `redirectPaymentMethodSpecificInput` and `mobilePaymentMethodSpecificInput`): "true = the payment
requires approval before the funds will be captured using the Approve payment or Capture payment API". No `approve` path
exists in the schema. The authorisation-mode page calls it "Applicable to non-card payments (i.e. Paypal)", while Google
Pay samples set it on the mobile input. Reading taken (inference): `CapturePayment` is the only way forward.

### Capture Payment

`POST /v2/{merchantId}/payments/{paymentId}/capture`

```json
{
  "amount": 5000,
  "isFinal": true
}
```

`amount` is a bare integer at the root; this is the one maintenance route without `amountOfMoney`.

Response `201` (published schema, `captureResponse`; no published example, reassembled from field examples):

```json
{
  "captureOutput": { "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" } },
  "status": "CAPTURE_REQUESTED",
  "statusOutput": { "statusCode": 91 },
  "id": "3066019730_1"
}
```

`captureStatusOutput` publishes only `statusCode`.

- Only from `PENDING_CAPTURE`: "This API allows multiple, partial captures of the authorized funds."
- "The amount can be lower than the amount that was authorized, but not higher."
- Omitted `amount`: "the full amount will be captured and the request will be final. If the full amount is captured, the
  request will also be final."
- `isFinal` defaults to `false` ("The transaction will remain open for subsequent (partial) capture requests"); `true`
  means "The transaction will be closed for any further capture requests."
- Partial capture needs `PRE_AUTHORIZATION`; otherwise the simulator answers `ACTION_NOT_ALLOWED_ON_TRANSACTION`.
- Asynchronous capture: `CAPTURE_REQUESTED` at `4`, `91`, `92` or `99`.
- A rejected capture (`93`, "Your acquirer declined your data capture request.") is **not terminal**: "The global status
  of the transaction will remain in statusOutput.statusCode=5 . Therefore, you can retry the operation at a later point."

### Cancel Payment

`POST /v2/{merchantId}/payments/{paymentId}/cancel`

```json
{
  "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" },
  "isFinal": true
}
```

The schema and SDK samples put `amountOfMoney` at the root; the maintenance page's property table labels it
`order.amountOfMoney`. The simulator reads either.

Response `200`, the one maintenance route that wraps a payment (`cancelPaymentResponse`):

```json
{
  "payment": {
    "id": "3066019730_1",
    "status": "CANCELLED",
    "statusOutput": { "statusCategory": "PENDING_MERCHANT", "statusCode": 61 }
  }
}
```

- Partial cancel only under `PRE_AUTHORIZATION`. Omitted `amountOfMoney`: "our platform will cancel the full original
  amount." `isFinal` defaults to `false`.
- Published outcomes: `CANCELLED` / `PENDING_MERCHANT` / `61` ("Author. deletion waiting") or `62` ("Author. deletion
  uncertain"), each resolving to `6` or `63`; `CANCELLED` / `UNSUCCESSFUL` / `6`; `CANCELLATION_REJECTED` /
  `UNSUCCESSFUL` / `63`.
- **Cancellation is not terminal**: code `63`, "The payment reverts to its previous state.", "The payment remains
  authorised.", "Your customer can try to cancel the payment again."
- A just-captured payment is undone through the refund route's deletion branch, not cancel.

A payment closed by a capture or cancellation answers `409` "Cancellation is not allowed because payment is closed".

A cancel of this simulator lands at `CANCELLED` / `UNSUCCESSFUL` / `6`, the settled code, and at `61` or `62` never:
the two published ways a caller learns how a `61` resolved, the webhook and `GET .../details`, are both unserved
(`CancelPayment.A_CANCELLATION_LANDS_AT_THE_SETTLED_CODE`).

The partial cancellation Worldline publishes under `PRE_AUTHORIZATION` is refused here: a cancel naming less than the
remaining authorized amount is answered `ACTION_NOT_ALLOWED_ON_TRANSACTION`, because this simulator's monetary ledger
has an `AUTHORIZATION`, a `CAPTURE` and a `REFUND` and no operation that lowers an authorization without capturing
(`CancelPayment.THIS_SLICE_CANCELS_IN_FULL_ONLY`).

### Refund Payment

**Not served.** `POST /v2/{merchantId}/payments/{paymentId}/refund`

```json
{
  "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" }
}
```

Response `201` (`refundResponse`, not wrapping a payment; `statusOutput` is an `orderStatusOutput` with `isCancellable`
only):

```json
{
  "refundOutput": { "amountOfMoney": { "amount": 5000, "currencyCode": "PLN" } },
  "status": "REFUND_REQUESTED",
  "statusOutput": {
    "statusCategory": "PENDING_CONNECT_OR_3RD_PARTY",
    "statusCode": 81,
    "isCancellable": false
  },
  "id": "3066019730_2"
}
```

- Only after code `9`: "You can reimburse successfully processed payments (which have status.Output.statusCode=9 )."
  Too early is `50001127`: "you have tried to refund a payment before the original payment has been fully captured."
- Omitted `amountOfMoney`: "our platform will refund the full original amount."
- Repeated partial refunds "as long as the total amount to be refunded does not exceed the total amount that was paid."
- Operation limits ("Operation limits"): soft `50000429`, "limited to one every 30 minutes"; hard `50000430`, "the
  transaction has reached the maximum number of allowed operations". Counts unpublished: "building your mechanism ...
  around the error codes 50000429 / 50000430 , and not around the number of operations performed."
- `400` and `404` answer `refundErrorResponse`, whose `refundResult` is deprecated ("not used by any payment product").
- **Refund or deletion** (code `9`): "the payment will be either refunded (statusOutput.statusCode=81 ) or deleted
  (statusOutput.statusCode=71 )" depending on whether the acquirer has transferred funds. No field exposes the trigger.

| Code | Name                        | Published                                                                     |
|------|-----------------------------|-------------------------------------------------------------------------------|
| `71` | Payment deletion pending    | a refund of a transaction that "has just recently received" `9`; resolves to `7` or `73` |
| `72` | Payment deletion uncertain  | technical-failure counterpart; resolves to `7` or `73`                        |
| `7`  | Payment deleted             | "This is a final status."                                                     |
| `73` | Payment deletion refused    | "This is a final status. No further actions are possible."                    |

### Maintenance State Rules

Under `PRE_AUTHORIZATION`:

| Step                                            | Result                                                                   |
|-------------------------------------------------|--------------------------------------------------------------------------|
| authorize 5000                                  | `PENDING_CAPTURE`, `5`                                                   |
| capture 2000, `isFinal=false`                   | captured 2000, 3000 capturable and cancellable                           |
| capture 3000, `isFinal=true`                    | captured 5000, `CAPTURED`                                                |
| cancel 3000, `isFinal=true`, before full capture | remaining hold released, no further capture                             |
| refund 1000 after capture                       | refunded 1000, 4000 refundable                                           |
| full refund                                     | `REFUNDED` at `8` or `85`, or the deletion branch to `7`                 |
| refused refund (`83`, "Refund refused")         | stays at `9`: "you can retry the operation at a later point"             |
| any maintenance after final close               | `ACTION_NOT_ALLOWED_ON_TRANSACTION`: "a maintenance operation that is not allowed for this specific transaction" |

Every step mints a new `payment.id`; amounts are recoverable from `GET .../details`.

Of that walk, this simulator refuses the partial cancel (see "Cancel Payment") and reaches the refund steps through no
route: `WorldlineApiController` maps five and none of them refunds. A captured payment reports
`derivedBalances.refundableRemainder` equal to the captured amount with `refundsAccepted` `false`
(`WorldlinePayment.refundsAccepted`).


### Get Payment

**Not served.** `GET /v2/{merchantId}/payments/{paymentId}`: `200` `paymentResponse`, or `404` `errorResponse` with `errors.id`
`UNKNOWN_PAYMENT_ID`. Original id only: "For capture operations, you must use Get Captures or Get Payment Details. For
refund operations, you must use Get Refunds or Get Payment Details." Carries `statusOutput.isCancellable`,
`isAuthorized`, `isRefundable` (`paymentStatusOutput`), and a single `operationOutput`, not a list.

### Get Payment Details

**Not served.** `GET /v2/{merchantId}/payments/{paymentId}/details`: `200` `paymentDetailsResponse`, or `404`. Accepts any id, returns
the full operation list, and "The displayed item is always the last final transaction's status." "Send
GetPaymentDetails requests to keep an eye on the number of performed maintenance operations per transactions."

### Get Captures and Get Refunds

**Not served.** `GET /v2/{merchantId}/payments/{paymentId}/captures` and `.../refunds`: `200` only (`capturesResponse`,
`refundsResponse`); no error response declared. "An HTTP 200 response is returned if the payment was found, even if no
captures were performed on it."

### Validate Webhook Credentials and Send Test Webhook

**Not served.**

| Route                                              | Body                                                                              | Answers                                                                                                            |
|----------------------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `POST /v2/{merchantId}/webhooks/validateCredentials` | `{"key": "bfa99cffb98dcad6c7f15824f5eb37", "secret": "0mw3LBod/3yPuI+UOmjpVmzL8U+YaTDC44haC6bzQpw="}` | `200` `validateCredentialsResponse`, `403` "API authentication failed."                                            |
| `POST /v2/{merchantId}/webhooks/sendtest`          | `{"url": "https://your-webhooks-url.com"}`                                        | `204`, or `400`, `403`, `404` "Configuration for sending webhook messages was not found."                          |

`secret` is a proof: "The base64-encoded value of an empty string hashed over by the webhooks secret ... Use exclusively
hashing algorithm HMAC 256". An omitted `url` uses the account's configured URL.

## Status Mapping

| Native                                           | Normalized                                                                                   |
|--------------------------------------------------|----------------------------------------------------------------------------------------------|
| `PENDING_CAPTURE`                                | `READY_FOR_CAPTURE`                                                                          |
| `CAPTURED`                                       | `SUCCEEDED`                                                                                  |
| `REFUNDED` (`7`, `8`, `85`)                      | `UNKNOWN`                                                                                    |
| `REFUND_REQUESTED` (`71`, `72`, `81`, `82`)      | `UNKNOWN`                                                                                    |
| `CANCELLED`                                      | `CANCELLED`                                                                                  |
| `CANCELLATION_REJECTED` (`63`)                   | `READY_FOR_CAPTURE` (inference: "The payment remains authorised.")                           |
| `REDIRECTED` (`46`)                              | `UNKNOWN`                                                                                    |
| `REJECTED`                                       | `FAILED`                                                                                     |
| `REJECTED_CAPTURE` (`93`)                        | `READY_FOR_CAPTURE`; the capture operation is `FAILED`                                       |
| unrecognized                                     | `UNKNOWN`                                                                                    |

`WorldlineStatus.normalizedPaymentStatus` carries a case for `CREATED`, `PENDING_CAPTURE`, `CAPTURE_REQUESTED`,
`CAPTURED`, `REJECTED`, `REJECTED_CAPTURE`, `CANCELLED` and `CANCELLATION_REJECTED`, and answers `UNKNOWN` for every
other native value. `REDIRECTED`, `REFUND_REQUESTED` and `REFUNDED` are three Worldline publishes and no route of this
simulator produces, which is why each falls to `UNKNOWN`.

Worldline qualifies a captured payment by what has been refunded against it: a full refund resolves to `REFUNDED` at
`8` or `85`, or through the deletion branch to `7`, and a refused refund (`83`) leaves the payment at `9`. This
simulator writes no Worldline refund, through any route (`WorldlineApiController`), and answers `refundsAccepted`
`false` on every payment (`WorldlinePayment.refundsAccepted`), so a `CAPTURED` payment here is `SUCCEEDED`, no refund
total qualifies it, and the shared vocabulary's `PARTIALLY_REFUNDED` and `REFUNDED` (`README.md`, "Common Normalized
State Vocabulary") are carried by no Worldline payment of this simulator.

| Native                                           | Normalized                                                                                   |
|--------------------------------------------------|----------------------------------------------------------------------------------------------|
| `CREATED`, `CAPTURE_REQUESTED`                   | `PROCESSING`                                                                                 |
| `AUTHORIZATION_REQUESTED`                        | `UNKNOWN`                                                                                    |

`WorldlineStatus.normalizedPaymentStatus` maps each native value to one normalized state, so no scenario chooses
between `PROCESSING` and `UNKNOWN` for an in-flight status.

Monetary qualifiers, from the operation list:

- captured below authorized, remainder open (after `isFinal=false`): `PARTIALLY_CAPTURED`. Worldline publishes no native
  status for this; the label needs `PRE_AUTHORIZATION`,
- captured equal to authorized, or `isFinal=true`: `SUCCEEDED` under the default landing `CAPTURED_9`.

A closing capture takes its landing's native status, so with `worldline.capture-landing` set to `CAPTURE_REQUESTED_91`
it normalizes to `PROCESSING` and with `PENDING_CAPTURE_9` to `READY_FOR_CAPTURE` rather than `SUCCEEDED`
(`CapturePayment.captured`, `WorldlineCaptureLanding`, `WorldlinePayment.normalizedStatus`).

Published `GetPayment` / `CreatePayment` outcomes ("Possible outcomes"):

| Status                    | Category                       | Codes                                |
|---------------------------|--------------------------------|--------------------------------------|
| `CREATED`                 | `CREATED`                      | `0`                                  |
| `CANCELLED`               | `UNSUCCESSFUL`                 | `1`, `6`, `61`, `62`, `64`, `75`, `96` |
| `REJECTED`                | `UNSUCCESSFUL`                 | `2`, `57`, `59`, `73`, `83`          |
| `REJECTED_CAPTURE`        | `UNSUCCESSFUL`                 | `93`                                 |
| `REDIRECTED`              | `PENDING_CONNECT_OR_3RD_PARTY` | `46`                                 |
| `PENDING_CAPTURE`         | `PENDING_MERCHANT`             | `5`, `56`                            |
| `AUTHORIZATION_REQUESTED` | `PENDING_CONNECT_OR_3RD_PARTY` | `50`, `51`, `55`                     |
| `CAPTURED`                | `COMPLETED`                    | `9`                                  |
| `REFUND_REQUESTED` (on the payment) | `REVERSED`           | `81`, `82`                           |
| `REFUNDED` (on the payment) | `REVERSED`                   | `7`, `8`, `85`                       |

Per-resource tables:

- `RefundPayment` / `GetRefunds`: `REJECTED` / `UNSUCCESSFUL` / `73`, `83`; `REFUND_REQUESTED` /
  `PENDING_CONNECT_OR_3RD_PARTY` / `71`, `72`, `81`, `82`; `REFUNDED` / `REFUNDED` / `7`, `8`, `85`,
- `CapturePayment` / `GetCapture`: `REJECTED_CAPTURE` / `93`; `CAPTURE_REQUESTED` / `4`, `91`, `92`, `99`; no category
  column and no successful status,
- `CancelPayment`: `CANCELLED` / `PENDING_MERCHANT` / `61`, `62`; `CANCELLATION_REJECTED` / `UNSUCCESSFUL` / `63`;
  `CANCELLED` / `UNSUCCESSFUL` / `6`.

Code `85`, "Refund processed by merchant": "All refunded transactions for all payment methods going through our
acquiring services will reach this status instead of status 8."

### What a successful capture lands in: four publications, three answers

| Publication                                                       | Status              | Category           | Code                  | Setting value            |
|-------------------------------------------------------------------|---------------------|--------------------|-----------------------|--------------------------|
| statuses page, `GetPayment` / `CreatePayment` table               | `CAPTURED`          | `COMPLETED`        | `9`                   | `CAPTURED_9` (default)   |
| `GetHostedCheckoutStatus` sample                                  | `CAPTURED`          | `COMPLETED`        | `9`                   | `CAPTURED_9`             |
| statuses page, `CapturePayment` / `GetCapture` table; prose for code `5`: "The subsequent status will be statusOutput.statusCode=91" | `CAPTURE_REQUESTED` | none published | `91` (of `4`, `91`, `92`, `99`) | `CAPTURE_REQUESTED_91` |
| "Authorisation mode": "status="PENDING_CAPTURE" / statusOutput.statusCategory="PENDING_MERCHANT" / statusOutput.statusCode=9" | `PENDING_CAPTURE` | `PENDING_MERCHANT` | `9` | `PENDING_CAPTURE_9` |

`CAPTURE_REQUESTED_91` answers category `PENDING_MERCHANT`, a simulator decision.

### `statusCategory` and `REVERSED`

The statuses page defines seven categories (`CREATED`, `UNSUCCESSFUL`, `PENDING_PAYMENT`, `PENDING_MERCHANT`,
`PENDING_CONNECT_OR_3RD_PARTY`, `REFUNDED`, `COMPLETED`) without `REVERSED`; its own tables and the schema's
`statusCategoryValue` enum use `REVERSED`. Unresolved.

## Error Shape

Published example, unknown payment id ("Error Notifications"):

```json
{
  "errorId": "4dac7acb-70c1-4917-80cd-068833fd8da5",
  "errors": [
    {
      "errorCode": "50001130",
      "category": "DIRECT_PLATFORM_ERROR",
      "code": "1002",
      "httpStatusCode": 404,
      "id": "UNKNOWN_PAYMENT_ID",
      "message": "UNKNOWN_PAYMENT_ID",
      "propertyName": "paymentId",
      "retriable": false
    }
  ]
}
```

- `errorResponse` requires `errorId` and `errors`; `aPIError` requires `errorCode` only.
- `code` is deprecated ("Deprecated: Use errorCode instead.", `x-deprecated-by: errorCode`). Both are digit strings: `code` "4- to 8-digit error code linked to
  errors.category", `errorCode` "8-digit error code linked to technical errors (for 5xxxxxxx errors) or transaction
  exceptions."
- `id` is the symbolic name ("a short human-readable message"). `INVALID_PAYMENT_ID` appears nowhere.
- `category`: `DIRECT_PLATFORM_ERROR` "indicating that a functional error has occurred in the platform",
  `PAYMENT_PLATFORM_ERROR` "indicating that a functional error has occurred in the payment platform", `IO_ERROR`
  "indicating that a technical error has occurred within the payment platform or between the payment platform and third
  party systems."
- `propertyName` is a JSonata query: "a.b selects the value of property b of root property a", "a[1] selects the first
  element of the array in root property a".
- Envelopes by route: `paymentErrorResponse` (adds `paymentResult`) on every `CreatePayment` error;
  `refundErrorResponse` (adds deprecated `refundResult`) on refund `400`/`404`; `errorResponse` elsewhere.

**Published HTTP statuses: 200, 201, 202, 400, 402, 403, 404, 409, 410, 500, 502, 503**, and "Make sure your application
can handle HTTP error codes not included in this list." `403` "Not authorized"; `402` "Payment Rejected"; `502` "We were
unable to process a message from a downstream partner/acquirer."

`409` has three published meanings:

| Meaning                                                                                              | Served by                                                                  |
|------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| "the request is currently being processed" (idempotency)                                             | the idempotence key                                                        |
| "Either you submitted a duplicate request or you are trying to create something with a duplicate key" | nowhere: a completed repeat is a replay, and `merchantReference` has no uniqueness rule |
| "Cancellation is not allowed because payment is closed"                                              | the cancel route                                                           |

Scenarios, with the published `errors.id` ("Fix errors.id") or code:

| Scenario                                  | Published                                                                                                   |
|-------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| invalid API key or secret                 | `403`, `ACCESS_TO_MERCHANT_NOT_ALLOWED`                                                                     |
| unknown token                             | `UNKNOWN_TOKEN_ID`                                                                                          |
| unknown payment id                        | `404`, `UNKNOWN_PAYMENT_ID`; siblings `UNKNOWN_REFUND_ID`, `UNKNOWN_PAYOUT_ID`, `UNKNOWN_CAPTURE_ID`        |
| invalid currency or amount                | `INVALID_VALUE`; bad amount example `errorCode` "50001111", `code` "1008", `propertyName` "order.amountOfMoney" |
| issuer decline                            | `402`, `paymentErrorResponse`; codes `30921001`, `40001134` (3-D Secure failed), `40001137` (3-D Secure missing), `50001054` (brand/PAN mismatch) |
| capture rejected                          | `REJECTED_CAPTURE`, `93`, still capturable                                                                  |
| cancellation rejected                     | status `CANCELLATION_REJECTED`, `63`, not an error body                                                     |
| refund rejected                           | `83`, stays at `9`                                                                                          |
| refund before full capture                | `50001127`                                                                                                  |
| maintenance not allowed                   | `ACTION_NOT_ALLOWED_ON_TRANSACTION`                                                                         |
| payment method not enabled                | `PAYMENT_PRODUCT_NOT_AVAILABLE`                                                                             |
| operation limits                          | `50000429`, `50000430`                                                                                      |
| server errors                             | `500`, `502`, `503`                                                                                         |

Simulator decision: an unknown hosted tokenization session answers `404` with the unknown-payment `errorCode` and **no
`errors.id`**; no published id fits the resource.

Simulator decision: the `403` for a refused credential carries `ACCESS_TO_MERCHANT_NOT_ALLOWED` and no `errorCode`,
although the schema requires one on every `aPIError`, because Worldline publishes no code for that id
(`WorldlineAnswers.refusedCredential`). A client that reads `errorCode` without allowing for its absence fails on that
one response.

## Webhooks

**Not served.**

- `POST` to the merchant URL: "A webhook is a POST request from our platform to an HTTPS endpoint at your server."
  `Content-Type: application/json`.
- Signature: "Validate the signature on the message." (published rule); the Java SDK says the request "should contain
  the two headers for the keyId and the signature". **Direct names neither header.** See "What Worldline's Own Source
  Names and No Direct Page Does".
- Retries ("Failure Recovery"): five, at 10 minutes, 1 hour, 2 hours, 8 hours, 24 hours, "relative to last delivery
  attempt"; header `retry-count`, `0` on the first attempt.
- Any non-2xx fails; "Decouple your business logic from the actual handling of the webhook message. We strongly
  recommend answering right away with a 2xx status code before you perform any follow-up actions later on."
- **No endpoint-verification handshake.** Direct uses `validateCredentials` and `sendtest` instead: "use our dedicated
  ValidateWebhookCredentials / SendTestWebhooks endpoints."

Connect, not Direct: `X-GCS-Signature` and `X-GCS-KeyId` on its webhooks page, a GET challenge with
`X-GCS-Webhooks-Endpoint-Verification`, ten retries over 48 hours, and a 5-minute pause after five consecutive failures.

Configuration:

- up to five account URLs ("You can add up to five URLs by repeating the operation."),
- request-level `feedbacks.webhooksUrls[]` (`maxItems: 5`, each `maxLength: 325`); `feedbacks.webhookUrl` is deprecated,
- request-level URLs **replace** account URLs: "sending any value in property feedbacks.webhooksUrls will overwrite the
  values you have configured in your account"; "URLs defined on transaction level ... will override URLs in both
  PSPID/group level", and "API credentials defined on PSPID level will override credentials on group level",
- a webhook needs "At least on endpoint URL" and "At least a valid API key/secret pair on either PSPID/group level".

Envelope: a **bare JSON array** (published example, "Messages"):

```json
[
  {
    "apiVersion": "v1",
    "id": "34b8a607-1fce-4003-b3ae-a4d29e92b232",
    "created": "2020-12-09T11:20:40.3744722+01:00",
    "merchantId": "merchant-1",
    "payment": {
      "paymentOutput": {
        "amountOfMoney": { "amount": 1000, "currencyCode": "EUR" },
        "paymentMethod": "card"
      },
      "status": "CREATED",
      "statusOutput": {
        "isCancellable": false,
        "statusCategory": "CREATED",
        "statusCode": 0,
        "isAuthorized": false,
        "isRefundable": false
      },
      "id": "3066019730_0"
    },
    "type": "payment.created"
  }
]
```

No `events` key is published. The `payment.test` sample under "Testing" is a bare object with `"apiFullVersion":
"v1.1"`.

| Event                              | Published                                                                                                  |
|------------------------------------|------------------------------------------------------------------------------------------------------------|
| `payment.created`                  | "The transaction or the operation (i.e. a refund ) has been created. This is the initial state once a new payment is created." |
| `payment.redirected`               | "The consumer has been redirected to a 3rd party to complete the authentication/payment." |
| `payment.authorization_requested`  | "We have requested an authorization against an asynchronous system and is awaiting its response." |
| `payment.pending_approval`         | "There are transactions waiting for your approval." |
| `payment.pending_completion`       | "There are transactions waiting for you to complete them." |
| `payment.pending_capture`          | "There are transactions waiting for you to capture them." |
| `payment.capture_requested`        | `91`; "The transaction is in the queue to be captured. (For cards, this means that the transaction has been authorised.)" |
| `payment.captured`                 | `9`                                                                                                        |
| `payment.rejected`                 |                                                                                                            |
| `payment.rejected_capture`         | `93`                                                                                                       |
| `payment.cancelled`                | published twice: "Applicable only for transactions reaching statusOutput.statusCode=6." and "Applicable only for transactions reaching statusOutput.statusCode=1 ." (consumer cancel or session expiry) |
| `payment.refunded`                 | `8`                                                                                                        |
| `refund.refund_requested`          | `81`                                                                                                       |
| `payment.test`                     | only in "Testing"                                                                                          |
| `paymentlink.*`                    | `created`, `clicked`, `paid`, `cancelled`, `expired`, `payment.rejected`, `payment.cancelled`; out of scope |

- Online events (merchant-caused) mint a new payment id; offline events do not.
- Duplicates share `payment.id` and `type`, not the event `id`: "Duplicate webhooks will have identical values for both
  properties payment.id and type ." Duplicates are normal.
- Webhooks are asynchronous and "not suitable for handling real-time events for the checkout process".

## Seeded Personas

One persona per intent, each seeding one tokenized card (`WorldlinePersonas`). Core rules are in `README.md`, "Seeded
Personas".

| Persona                                                | Intent                                      | Writes                            |
|--------------------------------------------------------|---------------------------------------------|-----------------------------------|
| `worldline-customer-with-tokenized-card`               | `a-saved-card-that-succeeds`                | `preauth-success-capture-success` |
| `worldline-tokenized-card-that-is-declined`            | `a-saved-card-that-is-declined`             | `preauth-success-capture-decline` |
| `worldline-tokenized-card-whose-capture-answer-is-lost`| `a-saved-card-whose-capture-answer-is-lost` | `capture-accepted-response-lost`  |

| What it seeds           | Value                                                                                                                     |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `alias`                 | the persona's own name                                                                                                    |
| `ownerId`               | `a-shopper-worldline-holds-a-token-for` (`WorldlinePersonas.THE_OWNER`)                                                   |
| `type`                  | `card`                                                                                                                    |
| `providerIds.token`     | minted by `WorldlineIdentifiers.aToken`, the same minting a completed hosted tokenization hands back: 32 lowercase hexadecimal characters, no prefix, so `cardPaymentMethodSpecificInput.token` carries it as it stands (`WorldlinePersonas.HOW_A_TOKEN_IS_MINTED`) |
| `last4`                 | `1111`, from the published `tokenCard` sample                                                                             |
| `expMonth` / `expYear`  | `12` / `2030`, from the same sample's `expiryDate` `1230`                                                                 |
| `brand`, `funding`      | `unpublished`, because none of the pages this contract was read from publishes a test-card list and those samples carry no brand (`WorldlinePayerCards.WORLDLINE_PUBLISHES_NO_TEST_CARD_LIST_ON_THESE_PAGES`) |

- Each persona mints a token of its own, so seeding one does not claim the id another needs.

## What This Contract Could Not Settle

| Contradiction or gap                                                     | Readings                                                                                       | Simulator                              |
|--------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|----------------------------------------|
| signed path version                                                      | `v1` / `v2`                                                                                    | `worldline.signed-path-version`        |
| idempotence-key header                                                   | `X-GCS-Idempotence-Key` / `X-GCS-Idempotency-Key`                                              | both read                              |
| cancel and refund amount location                                        | root `amountOfMoney` (schema, SDK) / `order.amountOfMoney` (maintenance page)                  | both read on cancel                    |
| successful capture status                                                | four publications, three answers                                                               | `worldline.capture-landing`            |
| webhook delivery shape                                                   | bare array / bare object                                                                       | not served                             |
| `payment.created` pairing                                                | `CREATED` / `0` ("Messages") or `5` ("Status Changes")                                         | not served                             |
| `REVERSED` as a category                                                 | absent from the definition list / present in tables and schema                                 | not emitted                            |
| operation list name                                                      | `Operations` / `operations`                                                                    | not served                             |
| `CANCELLATION_REJECTED`                                                  | on the statuses page (`63`), in **no schema enum**                                             | status known to the mapping            |
| webhook signature header names                                           | withheld by Direct docs                                                                        | not served                             |
| "Approve payment" endpoint                                               | named, not published                                                                           | not served                             |
| native status of a partly captured payment                               | none published                                                                                 | `PARTIALLY_CAPTURED` label             |
| no maintenance response example on any page or in the schema             | capture and cancel bodies reassembled from field `example:` values                             | inference                              |
| no `errors.id` or `errors.errorCode` for an address no route answers      | the four published unknown-object ids name a payment, refund, payout and capture               | `404` `errorResponse` with neither     |
| `405` absent from the published status list                              | the same page instructs a caller to handle statuses outside it                                  | `405` `errorResponse`                  |

## What Worldline's Own Source Names and No Direct Page Does

- **`X-GCS-Signature` and `X-GCS-KeyId` are Direct's webhook headers, undocumented.** Both are hard-coded in the Direct
  Java and PHP server SDKs; the signature is Base64 HMAC-SHA256 over the raw body, compared in constant time. Neither
  string occurs on any of the 198 pages the Direct sitemap lists, and `GCS` does not occur in the contract document.
  Other `X-GCS-` headers (idempotence key, its response timestamp, the date header) are documented on Direct.
- Sources that say the Direct guide documents these headers conflate Direct with Connect.
- Unpublished: which requests mint a temporary token rather than a permanent one.
- Unpublished: the hosted-tokenization read before a payer finishes (both members optional, no example).

## No Worldline Read Route

`GET /payments/{paymentId}` and `GET /payments/{paymentId}/details` are not served, so a client that captures and
receives a new `payment.id` has no Worldline route to read it back. Testers use `GET
/__simulator/worldline/state/{anyIdentifier}` and the Payments screen, which link every id of a payment.
