# PSP Simulator API Contracts

The contract of this simulator: the surface each provider serves, the provider-neutral control API, and the rules every
provider shares. Each provider file holds the vendor's own contract and every place the simulator departs from it.

Every request and response schema of the control API is in the generated OpenAPI documents, browsable at
`/openapi/index.html` (sources under `src/main/resources/static/openapi/`). `verifyOpenApi` fails the build when they
disagree with the code, so this file carries no JSON schema.

## Scope

The PSP contracts are:

- [Stripe](stripe.md)
- [Grow / Meshulam](grow.md)
- [Autra](autra.md)
- [Azericard](azericard.md)
- [Worldline Direct](worldline.md)
- [Tranzila](tranzila.md)

The cross-provider documents are:

- [Technical and UI Requirements](technical-ui-requirements.md)
- [Testing Support Features](testing-support-features.md)
- [Design Constraints](design-constraints.md)
- [Contributing a Screen](contributing-a-screen.md)

## Provider Surfaces

What each provider serves and what it does not. Route by route behaviour, settings and vendor deviations are in the
provider file.

| Provider    | Routes served                                                                                                                                                                                                                                                                                                                                                   | Not built                                                                                                                                                                                                                                                                                                                  | Published statuses never reached                                                                                                        |
|-------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| `stripe`    | 18: PaymentIntents (create, retrieve, `confirm`, `capture` with multicapture, `cancel`), `GET /v1/charges/{id}`, `POST /v1/refunds`, Customers (create, retrieve, update, delete), PaymentMethods (two lists, retrieve, `detach`), SetupIntents (create, `confirm`), `POST /v1/ephemeral_keys`                                                                  | Checkout Session and any hosted payer page. PaymentMethod create and attach. SetupIntent retrieve and cancel. Refund read. Charge and refund lists. `setup_intent.*` events and `charge.refunded`. Ephemeral keys for anything but a `customer`                                                                             | PaymentIntent `requires_action`. SetupIntent `requires_action`, `processing` and `canceled`. Charge `pending`             |
| `grow`      | 13 paths, all `POST /api/light/server/1.0/<name>` unless stated: `createPaymentProcess` (`chargeType` `2` hold or `3` card registration), `createTransactionWithToken`, `settleSuspendedTransaction`, `refundTransaction`, `approveTransaction`, `getTransactionInfo`, `getPaymentProcessInfo`, `getTokenTransactionsByExternalIdentifiers` (and with a trailing `/`), `GET updateMyUrl` (and with a trailing `/`), `cancelBitTransaction`, and `GET`/`POST /far`, the payer page | a payment fixture (no route seeds a process or a transaction). Instalments: `paymentNum` is required on `createTransactionWithToken`, and `paymentNum` with `maxPaymentNum` on `createPaymentProcess` is refused `736`, but the count changes nothing. Bit: `cancelBitTransaction` always refuses `730`                        | `722` on `approveTransaction`: a failed process names no transaction, and no transaction is ever cancelled                                  |
| `azericard` | 3: `POST /cgi-bin/cgi_link` (`TRTYPE` `0`, `1`, `21`, `22`, `24`, `90`), `POST /token/cgi_link` (card storage, stored-card and MIT unscheduled payment) and `POST /azericard/payer`, the payer page a card-less `TRTYPE=0/1` or card save opens                                                                                                                  | CIT initiation (`MERCH_TRAN_STATE=C`), `TRTYPE=185`, partial reversal, 3-D Secure and OTP steps                                                                                                                                                                                                                            | none: the gateway publishes no pending state                                                                                            |
| `autra`     | 12: `POST /v1/oauth/token`, `/v1/acquiring/payments/initialize`, `/card-sessions`, `/payments` (direct sale), `/payments/pre-authorization`, `/payments/{paymentId}/capture`, `/payments/{paymentId}/void`, `/pix/initialize`, `/pix/checkout`, `GET /v1/acquiring/payments/{paymentId}` and `GET /v1/acquiring/payments`, the two read-only routes, and `GET`/`POST /tokenize/sessions/{hostedCardSessionId}`, the payer's card page                                                                                                                | `tokenize-card` (its `200` has no published schema). Payment links, merchants, terminals, fee simulation, 3-D Secure. On the two reads, `maskedCard` is answered by neither; `brandReferenceId` belongs to a recurring series, which no payment here is, so real autra would leave it out too. Recurring series: the sale's `recurringData` is refused `400` rather than accepted and answered with no `brandReferenceId`. Card webhooks: only Pix queues a callback                                                                                                                                  | `REFUND_PENDING` and `PARTIAL_REFUND_PENDING`                                                                                           |
| `worldline` | 6: `POST /v2/{merchantId}/hostedtokenizations`, `GET .../hostedtokenizations/{id}`, `POST /v2/{merchantId}/payments`, `.../payments/{paymentId}/capture`, `.../payments/{paymentId}/cancel`, and `GET`/`POST /hostedtokenization/tokenization/form/{id}`, the payer page at `hostedTokenizationUrl` | hosted checkout, refund, `GET` payment and details, captures and refunds lists, webhooks and the webhook test routes. A partial cancellation is refused. No read route returns the new `payment.id` a capture mints (`worldline.md`, "No Worldline Read Route")                                                               | `61` (partial cancel)                                                                                                                   |
| `tranzila`  | 8, plus the Hosted Fields field frames: `POST /v1/transaction/credit_card/create` (`debit`, `verify` `2`/`5`, `force`, `reversal`, `credit`, `cancel`, 3-D Secure), `/v1/transaction/credit_card/3ds/complete`, `/v2/handshake/create`, `/v1/transactions`, `/tranzila/report/v1/transaction`, `GET`/`POST /tranzila/directng/{terminal}/iframenew.php`, the payer page with its wallet sheet, and Hosted Fields: `GET /tranzila/hf/assets/js/thostedf.js` with `POST /tranzila/hf/process/ajax_process_transaction` | STO / My Billing, instalments, Bit, PayPal, MASAV, invoices, EMV PinPad, payment requests, legacy CGI and `direct.tranzila.com`, Handshake V1, Hosted Fields wallet and bank-transfer charges. Apple Pay and Google Pay are stand-in buttons on the payer page | `shva` (pending Shva response) and `010` (partial confirmation) |

Facts that belong to the control surface rather than to one vendor:

- `relatedObjects` is read back in the order the provider named its entries, because a read, a screen and a control
  answer all print them in that order and an Autra payment names the steps of its timeline there. An entry carries a
  fact a reader correlates the object by, which for some entries is the identifier of another object and for others is
  not: worldline names `authorizationMode` and `statusCategory.statusCode`, autra `installments`, `voidRetryable`,
  `createdAt`, `refundedAmount` and `events.1`, azericard `terminal`, `rrn` and `approval`, and nothing of a run can be
  looked up under any of those. Part of what the map carries is client free text answered back (autra `description`,
  `orderId` and `nickname`, worldline `merchantReference` and `merchantCustomerId`, azericard `order`), and every
  value is printed verbatim and redacted nowhere, so a read validates no value here: what may not appear in the map is
  held by `verifyNoPayerUrlAmongRelatedObjects` in the build instead, where refusing cannot fail a tester's page.
- A Grow payment process and the transaction its completion creates are two objects that name each other in
  `relatedObjects` (`processId`, `transactionId`, `refundedTransactionId`, `merchantId`). A process carries no ledger. A
  refund is a transaction row with no token and no `processId`. Processes and transactions share one id namespace per
  test run.
- A Worldline payment answers to the id it was created under plus one more per capture or cancel. Every read by id
  resolves any of them (see "Object identity and lists").
- Stripe charges and ephemeral keys are not on `/ui/objects` or the state read.

## Common Simulator Control API

The control API lives under `/__simulator/{provider}`, apart from every provider route. Every call takes an optional
`testRunId`. Without one it acts on `default`. There is no authentication: the simulator is reached only over a private
network (`technical-ui-requirements.md`, "Security Model"). No read ever returns a provider credential or a webhook
secret.

### Routes

All paths are under `/__simulator/{provider}`. The screen is the one `verifyClickableCapabilities` binds to the route.

| Route                                                 | What it does                                                                                               | Screen                                          |
|-------------------------------------------------------|------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| `PUT /config`                                         | merge accounts, callback targets, route aliases and settings, and replace the clock                        | `/ui/configuration/account`, `/ui/configuration/clock`, `/ui/configuration/route-alias`, `/ui/configuration/settings` |
| `GET /config/fields`                                  | every account field this provider declares, and for a field whose values are a closed set, each value with the sentence a form offers it under | `/ui/configuration/account` |
| `DELETE /config/accounts/{accountId}`                 | remove one account with its callback targets and provider row. `404` if the run has none                   | `/ui/configuration/account/remove`              |
| `DELETE /config/route-aliases?from=`                  | release one route alias. The only way to free its URL                                      | `/ui/configuration/route-alias/release`         |
| `POST /clock/advance`                                 | move a pinned clock forward by `advanceBy`                                                                 | `/ui/configuration/clock/advance`               |
| `POST /reset`                                         | clear state, audit and queued callbacks of one run, or of all runs with `"scope": "ALL"`                   | `/ui/dashboard/reset` (one run only)            |
| `POST /fixtures/payment-methods`                      | seed a payment-method fixture                                                                              | `/ui/payment-methods/seed`                      |
| `GET /fixtures/payment-methods`                       | list the run's fixtures                                                                                    | `/ui/payment-methods`                           |
| `DELETE /fixtures/payment-methods/{fixtureId}`        | remove a fixture and the provider's row for it                                                             | `/ui/payment-methods/remove`                    |
| `POST /scenarios`                                     | write one scenario rule                                                                                    | `/ui/scenarios/write`                           |
| `GET /scenarios`, `GET /scenarios/{scenarioId}`       | read the run's rules, all or one                                                                           | `/ui/scenarios`, `/ui/scenarios/diff`           |
| `DELETE /scenarios/{scenarioId}`                      | delete one rule                                                                                            | `/ui/scenarios/delete`                          |
| `POST /scenarios/presets/preview`                     | expand a preset into rules, writing nothing                                                                | `/ui/scenarios/preset`                          |
| `POST /scenarios/presets`                             | expand a preset and write its rules, or refuse it naming what the provider lacks                           | `/ui/scenarios/preset`                          |
| `POST /scenarios/import`                              | write a rules document, all rules or none, as new rules. `422` for another provider's document, an empty one, or a refused rule | `/ui/scenarios/import`   |
| `GET /scenarios/{scenarioId}/diff`                    | compare two rules ("Scenario Diff")                                                                        | `/ui/scenarios/diff`                            |
| `POST /scenarios/{scenarioId}/copy`                   | write a new rule from one, with an `edit` map and a `clear` list                                           | `/ui/scenarios/diff/copy` (a prefilled form)    |
| `GET /personas`, `POST /personas/preview`, `POST /personas` | persona catalogue, preview and seeding ("Seeded Personas")                                           | `/ui/personas`, `/ui/personas/seed`             |
| `POST /hosted/{providerObjectId}/complete`            | complete a payer's page as the payer would                                                                 | `/ui/objects/complete`                          |
| `POST /objects/{providerObjectId}/follow-ups/{name}`  | perform a provider-side step the object offers now ("Follow-ups")                                          | `/ui/objects/follow-up`                         |
| `POST /webhooks`                                      | `SEND` a callback now or after `delayMs`, or `RETRY` a delivery                                            | `/ui/webhooks/send`, `/ui/webhooks/retry`       |
| `POST /webhooks/deliver-due`                          | attempt every delivery of the run that is due on its clock                                                 | `/ui/webhooks/deliver-due`                      |
| `GET /webhooks`                                       | every delivery of the run, with its full attempt ledger                                                    | `/ui/webhooks`                                  |
| `GET /state/{providerObjectId}`                       | one object's state, addressed by any id it answers to                                                      | `/ui/objects`                                   |
| `GET /objects?after=&limit=`                          | every object of the run, newest first                                                                      | `/ui/objects`                                   |
| `GET /audit?auditId=&accountId=&origin=&direction=&method=&status=&providerObjectId=&scenarioId=&from=&until=&after=&limit=` | the sanitized trail, oldest first | `/ui/audit`           |
| `GET /idempotency?after=&limit=`                      | keyed exchanges: first request, replays, conflicts (`testing-support-features.md`, "Retry, Idempotency and Correlation Inspector") | `/ui/idempotency` |
| `GET /address`                                        | `publicBaseUrl`, `apiBasePath`, `providerBaseUrl`, `testRunHeader`, `testRunId`, and how a request names its account | `/ui/pairing`                     |
| `GET /operations`                                     | every operation, with the `match.*` it carries, `storiesKeyedOn`, `itsFlowKeyMayBeReplaced`, `reportsAProcessingState`, `failsWith` | `/ui`                          |
| `GET /originations?accountId=&startedAs=`                | every way this provider declares a payment may be started: each way's label, what it leaves behind, what the payer does next and the payer page this simulator serves for it, and each field's key, label, sentence, whether it is required and the query parameter a value is sent in. Beside the ways: the accounts this run configures and the fields the simulator fills in itself, each with the provider's own words for where its value comes from. With an account and a way named, a field also carries the values this test run has already produced for it | `/ui/objects/new` |
| `POST /originations?accountId=&startedAs=&field=`        | start one payment. Each value is one `field` parameter spelling `key=value`. `201` carries the `providerObjectId` and the provider's own words for what exists now, and the rule that decided it when one did. The provider's own refusal is `422` in its own words, and a rule that answered instead of the provider is `422` naming it. The core refuses only a missing required field, a key no way declares and a callback target this run does not configure | `/ui/objects/start` |
| `GET /reads?accountId=&readNamed=`                    | every read this provider publishes: the question each answers, what an empty answer would mean, and each field's key, label, sentence, whether it is required and the query parameter a value is sent in. With an account and a read named, each field also carries the values this test run has already produced for it. A provider that publishes none answers `200` saying what it has instead | `/ui/reads` |
| `GET /reads/answer?accountId=&readNamed=&field=`      | perform one read. Each value is one `field` parameter spelling `key=value`. Answers the rows the provider answered, each fact under the name the provider gave it as one of a value, no value at all, the members under it or the elements under it, plus what it answered about the query. Nothing found is `200` with no row and what the provider says that means; the provider's own refusal is `422` in its own words | `/ui/reads/ask` |
| `GET /run-bundle`, `POST /run-bundle/import`          | export and import a run's setup ("Run Bundle Schema")                                                      | `/ui/run-bundle`                                |
| `GET /repro-bundle?key=`, `POST /repro-bundle/import` | package one payment for a bug report, and import its setup half                                            | `/ui/repro-bundle`                              |

Rules for every route:

- A list read (`objects`, `webhooks`, `audit`, `GET` fixtures and scenarios) answers a bare JSON array.
- `objects` and `audit` page by `after` (the last object id or trail row seen) and `limit` (default `100`, `1` to
  `500`). `idempotency` defaults to `50`. A cursor the run no longer holds, or an empty `?after=`, is `404`.
- An unknown provider is `404`. A malformed `testRunId` is `400`.
- Status codes: `200` read or update, `201` created, `400` malformed request, `404` unknown provider, run or object,
  `409` conflict, `422` valid JSON the provider cannot apply.
- A body a reader of this API could not parse is `400` carrying `message`, the `properties` the reading stopped at
  where it named one, and nothing of what the body said. Where it named none the answer is `message` alone. It leaves
  one row, under the run the query named where the route it was heading to reads its run there — of the routes carrying
  a body only `POST .../scenarios/{scenarioId}/copy` does — and under `UNKNOWN` otherwise, because nothing read a run
  out of the body.
- A `Content-Type` no route of this API reads is `415` in Spring Boot's own `{"timestamp","status","error","path"}` and
  leaves no row: no reader of this API was chosen, so nothing of the body was read and this simulator decided nothing.
  An address this API does not serve and a method one of its addresses does not answer are answered the same way
  ("Every shape this application publishes when nothing answered the request" in `design-constraints.md`).
- A refused configure, run-bundle or repro-bundle import, fixture seed or persona seed changes nothing: each writes in
  one transaction.
- A value another request claims between a call's check and its write is `409` and nothing of the call is kept. A
  refusal the dry run decides stays `422`.

### Test-run resolution

A provider request is assigned to a test run in this order:

1. the `X-Simulator-Test-Run-Id` header,
2. a route alias bound for one run,
3. the account credential the request carries,
4. correlation with an existing object, which never decides: every provider's `resolve` offers an
   `ACCOUNT_CREDENTIALS` candidate and none offers an `OBJECT_CORRELATION` one
   (`TestRunResolutionRule.OBJECT_CORRELATION`),
5. otherwise `default`.

- A run exists only once a call naming it has succeeded. `default` always exists.
- A header naming a run that does not exist is refused. It does not fall through to credentials.
- A header-routed request is still authenticated against that run's accounts. Its clock and its audit rows are that
  run's even when it is refused.
- Two runs holding the same credential make a request without the header ambiguous. It is refused and recorded under
  `UNKNOWN`, which no run may be named.
- Routing is not permissive authentication. The two are separate settings.

### Configuration

- `accounts[]`: `accountId` is a caller-chosen label, trimmed at both ends, case kept. `configuration` is a flat map of
  strings the provider owns. An unknown key is `422` naming `accounts[i].configuration.<key>`.
- Accounts, callback targets, route aliases and settings **merge**: named ones are created or replaced, others are left
  alone. A payload naming one account twice is refused.
- The `clock` block **replaces**: `"clock": {}` returns the run to the host clock, and no `clock` block leaves it alone.
  The clock belongs to the run, not to a provider.
- A webhook target names an `accountId` and a URL, and carries no signing material.
- Reset never touches configuration.
- A refused call configures nothing: the clock, the run, accounts, targets, aliases and settings are written in one
  transaction, after the refusal is decided.

The keys each provider reads (values listed default first):

| Provider    | Key                                     | Meaning                                                                                                 |
|-------------|-----------------------------------------|---------------------------------------------------------------------------------------------------------|
| `grow`      | `userId`                                | required, unique in the run. Authenticates `createPaymentProcess` (with `pageCode`), `createTransactionWithToken`, `settleSuspendedTransaction`, `getTokenTransactionsByExternalIdentifiers`, and `refundTransaction` (either credential) |
| `grow`      | `pageCode`                              | required, unique in the run. Authenticates `createPaymentProcess` (with `userId`), `approveTransaction`, `getTransactionInfo`, `getPaymentProcessInfo`, `cancelBitTransaction`, and `refundTransaction` (either credential). `updateMyUrl` reads no credential |
| `grow`      | `wireForm`                              | `LIVE_WIRE` or `PUBLISHED_EXAMPLE`: the response content type and how Hebrew is escaped                   |
| `grow`      | `callbackAcknowledgement`               | `HTTP_2XX` or `APPROVE_TRANSACTION`                                                                     |
| `grow`      | `settlementCeiling`                     | `FRAME_CAPTURE` or `THIRTY_PERCENT_ABOVE`                                                               |
| `grow`      | `refundAllowance`                       | `ONE_REFUND` or `TWO_REFUNDS`                                                                           |
| `grow`      | `refundExcessRejection`                 | `ORIGINAL_AMOUNT`, `CHARGE_AMOUNT` or `TRANSACTION_AMOUNT`                                              |
| `grow`      | `merchantBalance`                       | `AVAILABLE` or `EMPTY`                                                                                  |
| `grow`      | `processLookupRejection`                | `PUBLISHED_EXAMPLE` or `PUBLISHED_TABLE`                                                                |
| `grow`      | `captureWindow`                         | ISO-8601 duration, positive, at most 365 days, whole microseconds, `P7D`                                |
| `grow`      | `automaticVoidAfter`                    | ISO-8601 duration, positive, at most 365 days, whole microseconds, `P10D`                               |
| `azericard` | `terminalId`                            | required, the `TERMINAL`, at most 32 characters, unique in the run                                      |
| `azericard` | `apiKey`                                | required, the merchant's RSA public key (PEM) that request `P_SIGN` is verified against                 |
| `azericard` | `apiSecret`                             | required, the gateway's RSA private key (PEM) that answers are signed with                             |
| `azericard` | `azericard.duplicate-response-code`     | the `RC` a duplicate `ORDER` gets, `-1`                                                                 |
| `azericard` | `azericard.partial-completion`          | `CLOSES_THE_AUTHORIZATION` or `LEAVES_THE_REMAINDER_CAPTURABLE`                                         |
| `azericard` | `azericard.callback-signature`          | `VALID`, `CORRUPTED` or `ABSENT`                                                                        |
| `autra`     | `clientId`, `clientSecret`              | required, the HTTP Basic credential of `POST /v1/oauth/token`. `clientId` unique in the run             |
| `autra`     | `documentId`                            | required, the merchant's CPF or CNPJ, digits only, at most 32, unique in the run                        |
| `autra`     | `autra.oauth-failure`                   | `BAD_REQUEST_WITH_ERROR_STRING` or `UNAUTHORIZED_INVALID_CLIENT`                                        |
| `autra`     | `autra.oauth-field-casing`              | `CAMEL_CASE` or `SNAKE_CASE`                                                                            |
| `autra`     | `autra.pre-authorization-request-shape` | `PUBLISHED_SCHEMA` or `PUBLISHED_GUIDE_EXAMPLE`                                                         |
| `autra`     | `autra.void-request-shape`              | `PUBLISHED_SCHEMA` or `PUBLISHED_GUIDE_EXAMPLE`                                                         |
| `autra`     | `autra.authorizer-rejected-message`     | the `msg` of an `AUTHORIZER_REJECTED` decline, `NN - text`                                              |
| `autra`     | `autra.merchant-not-found-status`       | `NOT_FOUND`, `FORBIDDEN` or `UNPROCESSABLE`                                                             |
| `autra`     | `autra.sale-decline-status`             | `TWO_HUNDRED_WITH_ERRORS` or `UNPROCESSABLE_WITH_ERRORS`                                                |
| `autra`     | `autra.acquirer-transaction-identifier` | `REPORTED` or `NOT_REPORTED`: whether the acquirer names the transaction `trackingNumber` and `nsu` carry |
| `autra`     | `webhookSecret`                         | optional, at least 32 characters. Absent means unsigned callbacks                                       |
| `stripe`    | `secretKey`                             | required, `sk_test_...`, at most 255, unique in the run. The only thing a request carries its account on |
| `stripe`    | `captureWindow`                         | required, ISO-8601 duration, at least one microsecond, at most 365 days. No default                     |
| `stripe`    | `refundStatus`                          | `succeeded`, `pending`, `requires_action`, `failed` or `canceled`: where a refund comes to rest         |
| `stripe`    | `webhookSecret`                         | optional, `whsec_...`, at most 255. Absent means no `Stripe-Signature`                                  |
| `stripe`    | `webhookSignature`                      | `VALID` or `CORRUPTED`. Needs `webhookSecret`                                                           |
| `stripe`    | `webhookTimestampAge`                   | ISO-8601 duration, `PT0S`, at most 365 days. Needs `webhookSecret`                                     |
| `worldline` | `merchantId`                            | required, the PSPID in the path, one path segment (no `/`, `?` or `#`), at most 128, unique in the run   |
| `worldline` | `apiKey`                                | required, unique in the run                                                                             |
| `worldline` | `apiSecret`                             | required, the HMAC-SHA256 key                                                                           |
| `worldline` | `worldline.signed-path-version`         | `V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT` or `V1_AS_THE_WORKED_EXAMPLES_SIGN_IT`             |
| `worldline` | `worldline.capture-landing`             | `CAPTURED_9`, `CAPTURE_REQUESTED_91` or `PENDING_CAPTURE_9`                                             |
| `worldline` | `worldline.authentication`              | `THE_SIGNATURE_IS_VERIFIED` or `THE_SIGNATURE_IS_NOT_VERIFIED` (permissive auth)                        |

Secret fields, which no read, screen or bundle carries: grow `userId` and `pageCode`, azericard `apiSecret`, autra
`clientSecret` and `webhookSecret`, stripe `secretKey` and `webhookSecret`, worldline `apiSecret`.

### Provider settings

`settings` holds run-wide knobs. The only key is `webhook.retryPlan`: comma-separated ISO-8601 durations, one per
attempt, none negative, at most 20.

| Provider                            | Declares `webhook.retryPlan` | Default plan                                                           |
|-------------------------------------|------------------------------|------------------------------------------------------------------------|
| `grow`                              | yes                          | 6 attempts: `PT0S,PT1S,PT5S,PT30S,PT2M,PT5M`                           |
| `autra`                             | yes                          | 16 attempts: `PT0S`, ten of `PT1M`, five of `PT10M`                    |
| `stripe`, `azericard`, `worldline`  | no, so setting it is `422`   | `simulator.webhook.retry-delays`: 5 attempts, `PT0S,PT1S,PT5S,PT30S,PT2M` |

- A key the provider does not declare is `422` naming the key. A bad value is refused by position, as in
  `settings.webhook.retryPlan[1]`.
- `/ui/configuration` offers one field per setting the chosen provider declares, and submits it to
  `POST /ui/configuration/settings`, which writes what `PUT /config` writes and refuses what it refuses. A field handed
  back unchanged, carrying the default of a setting the run has not set, writes nothing. Emptying a field is refused,
  like `PUT /config` with an empty value: nothing returns a setting to the provider's default but typing it.
- Settings merge. No route removes one. Reset leaves them.
- A delivery keeps the plan it was scheduled with.

### Route aliases

- `from` is the absolute `http` or `https` URL the application calls. `to` is a path of this simulator.
- Matched on host (from `X-Forwarded-Host`, else `Host`, without port, case-insensitive) and the whole path, exactly.
  The scheme is not matched.
- `from` is unique across the simulator. A second claim is `422` naming the run that holds it, or `409` naming
  `routeAliases[i].from` when it is claimed between the check and the write.
- Refused: a `from` with no path, user info, query or fragment, or longer than 512 characters of host and path. A `to`
  that is not a path, carries a query or fragment, or points into `/__simulator`, `/ui` or `/actuator`.
- A `to` no route answers is accepted and answers `404` when called.
- An alias resolves requests only for the provider it was configured under.
- Removing an account leaves the run's aliases bound. Release is `200` with the remaining aliases, `404` if the run
  holds no such URL, `400` when `from` names nothing or is not a URL.

### Clock

- `POST /clock/advance` takes `advanceBy` and answers `advancedBy`, `movedFrom` and `now`.
- An unpinned run cannot be advanced: `409`, naming `clock.fixedNow`.
- `advanceBy` must be positive: `400`.
- Advances accumulate.
- Every deadline is compared with the run's clock when the object is next read or used. Nothing expires an object
  directly: pin, create, advance.
- `clock.timezone` and `clock.maxSkewSeconds` are accepted by the schema and read by nothing. Only `fixedNow` has an
  effect (`kernel/clock/DeclaredClock.java`).

### Fixtures

- The core owns the `fx_...` id, `merchantId`, `ownerId`, `type`, `alias` and the card facts (`brand`, `last4`,
  `expMonth`, `expYear`, `funding`). `last4` must be exactly four digits, so a PAN there is `400`. No fixture holds a
  PAN or a CVC.
- `providerIds` keys each provider reads. A key it does not read is `422`:

| Provider    | Reads                                                 |
|-------------|-------------------------------------------------------|
| `grow`      | nothing. A seeded `cardToken` would be refused `140`  |
| `azericard` | `token` (required), exactly 28 characters, which becomes a stored card. A run holds one stored card per `token`, and a second is `422` |
| `stripe`    | `paymentMethodId` (required) and `customerId`         |
| `autra`     | `token` and `paymentMethodId`, either or both. Stores nothing provider-side. The payer step hands the pair back |
| `worldline` | `token` (required)                                    |

- `type` is `card`, `wallet` or `psp-token`, and a provider refuses the kinds it carries nothing for: `autra`,
  `worldline` and `azericard` take `card` only, `stripe` takes `card` and `wallet` (as `paypal`) and refuses
  `psp-token` (`stripe.md`, "PaymentMethods"), and `grow` reads no `providerIds` and refuses no kind.
- `merchantId` must name an account the run configures for that provider on `stripe` and `azericard`, because each
  resolves a stored card through the account that holds it. The core does not check it: which values a `providerIds`
  key can be served with is the provider's own to decide.
- One fixture of a provider and run claims a `providerIds` value, and the `alias` the same way: a second naming it is
  `409` (`kernel/fixture/Seeding.java`, `Claimed`). The one exception is a key the provider declares as naming the owner
  an instrument belongs to rather than the instrument itself — `stripe` `customerId`
  (`ProviderPaymentMethodFields.keysThatNameAnOwner`); many fixtures of a run may name one value under it. A provider
  claiming its own object besides refuses in its own words, which is why the `azericard` row above reads `422` and not
  `409`.
- `defaultScenario` names a rule the run already holds. An unknown name is `422`. The rule fires through
  `match.paymentMethodId`.
- Payments, processes and authorizations are not seeded. Drive them through the provider routes.

### Scenario rules

A rule has `priority`, `match` and `action`, and optionally `story`, `keyedOn`, `whenState` and `thenState`.

`match` fields: `merchantId`, `orderId`, `providerObjectId`, `customerId`, `paymentMethodId`, `amount` (exact string),
`currency`, `operation` (spelled as the provider spells it on the wire), `eventType` and `header`.

| `action.type`                          | Effect                                                                                                    |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `FIXED_SUCCESS`                        | the operation succeeds                                                                                    |
| `TERMINAL_FAILURE`                     | a decline carrying `providerCode` in the provider's vocabulary                                            |
| `TRANSPORT_FAULT`                      | the operation runs, then `fault` (`SERVER_ERROR`, `TRUNCATED_BODY`, `CORRUPTED_BODY`, `DROPPED_CONNECTION`, `DELAYED_RESPONSE`) replaces the answer. `SERVER_ERROR` needs `httpStatus`. `DELAYED_RESPONSE` needs `delayMs`, capped by `simulator.fault.longest-delay` |
| `TRANSPORT_FAULT_INSTEAD_OF_OPERATION` | the fault answers and the operation does not run                                                          |
| `PROCESSING_THEN_TERMINAL`             | the provider's processing state now, the terminal one after `settlesAfterMs` on the run's clock. `providerCode` makes it a decline. Needs `match.operation` of an operation that reports a processing state |
| `DELIVERY_DELAYED`                     | hold a delivery `delayMs` before its first attempt                                                        |
| `DELIVERY_DUPLICATED`                  | post a delivery twice under one event id                                                                  |
| `DELIVERY_DROPPED`                     | record a delivery as dropped and never post it                                                            |

- A provider's vocabulary may declare the failure codes each operation answers; otherwise every operation answers every
  code the provider declares; `GET /operations` answers each operation's in `failsWith`. A vocabulary declaring
  them for some operations declares them for every one. A rule naming `match.operation` and a
  `providerCode` that operation does not answer is refused `422`, naming both. A rule naming no operation and a
  `providerCode` matches only the operations that answer that code: elsewhere it is no match, the next rule or the
  provider's default decides, and its story does not move.
- A delivery rule narrows only on `merchantId`, `providerObjectId` and `eventType`, and takes no `story`.
  `match.eventType` is refused on a request rule.
- `action.failuresBeforeSuccess` and `action.terminalStatus` are refused. A retry-then-success is a two-step story.
- Out-of-order delivery is a delay on the earlier of two deliveries.
- Precedence: the highest `priority`, then the most match fields named (a `whenState` counts as one), then the newest
  rule (`ScenarioRule.THE_ORDER_THE_ENGINE_CONSULTS_THEM`). A `match.header` rule, e.g. on `X-Simulator-Scenario`, is
  ordered like any other and wins nothing outright, and neither does a named `scenarioId`.
- The audit names the one rule that decided an exchange, in `scenarioId`, and not the rules it was ranked against
  (`AuditRecord.scenarioId`, `ScenarioEngine.theStepThisRequestIsOwed`).

Stories:

- `story` is required by `keyedOn`, `whenState` and `thenState`. Every story starts in `Started`. `thenState` must
  differ from `whenState`.
- `keyedOn` is `FLOW` (default, the key the provider resolves) or `TEST_RUN`.
- A flow key is `<space>:<id>`. The space is lower-case letters, digits and hyphens, starting with a letter. The key is
  not blank, has no NUL, no unpaired surrogate, and at most 512 code points.
- A flow-keyed story may not span an operation whose object may replace its id (Worldline capture and cancel). Keyed on
  `TEST_RUN`, it may.
- A configured value a provider spells into a key (an Azericard terminal) is checked at `PUT /config`, `422` naming the
  property (`azericard.md`, "Identifier Spaces").

### Hosted completion

- `outcome` is `PAID`, `DECLINED` or `ABANDONED`. `card` goes with `PAID` only, and only where the page collects one
  (an Autra Pix does not). It names a published test card or a fixture alias.
- The route and the payer's page run the same code and produce the same object, state and callback.
- Completing a page that already has an outcome is `422`. So is an unknown card. An unknown object is `404`.
- A provider with no payer step refuses in its own words.

### Follow-ups

- A follow-up is a provider-side step on an existing object (Autra: `settle` on a Pix). An object offers it by name,
  in the provider's words, only while its state allows it.
- `testRunId` is in the body. An unreadable `testRunId` or a blank name is `400`.
- The provider performs it; what it moves or sends is the provider's own description of it. The answer carries
  `state` and `webhookJobIds`, empty when it queued nothing.
- A name the object does not offer now is `422` in the core's words; the provider is not asked. One the provider
  refuses at the move is `422` in its words. An unknown object is `404`, also when it is gone once moved.
- A provider with no follow-ups offers none, so the core answers every name `422`.

### Object identity and lists

- An object answers to a set of ids: the one it was created under plus any it has since answered to. The state read
  accepts any of them and returns `providerObjectId`, `addressedBy` and `answersTo`.
- Only Worldline objects have more than one id.
- Two objects answering to one id make the read or listing refuse.
- The trail filtered by `providerObjectId` returns rows carrying any id in the set.
- `GET /objects` returns, newest first, exactly what the state read returns for each object, addressed by its creation
  id. Order comes from an arrival column, not a timestamp.

### State response rules

- Amounts are strings in the exact lexical form sent or returned (`"1000"`, `"10.00"` and `"10.0"` differ), with
  `currency` beside them.
- `operations[]` is the monetary record, in `sequence` order. Each entry has `kind` (`AUTHORIZATION`, `CAPTURE`,
  `CANCELLATION`, `REFUND`), `amount`, `closesAuthorization` (`null` where the kind cannot close one),
  `createdObjectId`, native and normalized status, and a timestamp.
- `createdObjectId` names a second object the operation created (a Grow or Stripe refund, an Azericard `TOKEN`). It
  never names the object itself.
- `derivedBalances` (`capturedAmount`, `refundedAmount`, `capturableRemainder`, `refundableRemainder`) are recomputed
  from `operations[]`.
- `capturableRemainder` is money held and not captured: zero once the authorization is closed. `capturesAccepted` says
  whether a capture would be admitted now. They differ for a Grow hold past `captureWindow` but before
  `automaticVoidAfter`.
- `refundableRemainder` is captures less refunds. `refundsAccepted` says whether a refund would be admitted now. They
  differ for a Grow transaction that has used its refund allowance, and for Worldline, which has no refund.
- An open uncaptured Autra or Azericard hold reports both acceptances `true` and `refundableRemainder` zero: the
  reversal releases the hold.
- Both acceptances are floors: a rule, a ceiling or a decline can still refuse. An object nothing can capture or refund
  reports `false`. A state that contradicts its own balances is refused, not returned.
- Past a Stripe capture window the intent reports `canceled`, `CANCELLED`, remainder zero and `capturesAccepted`
  `false` together.
- `origin` is the surface that created the object: `PROVIDER_API`, `CONTROL_API`, `UI` or `SCHEDULED_JOB`.
- `updatedAt` is the time of the object's last change. A refused operation does not move it and shows only in the audit.
- `hostedSession` is `null` unless a payer finishes the object. Then it carries the account, `party`, `operation`,
  `createdAt`, `expires`, `aPartyIsNamed`, `itExpiresAtAKnownTime` and `itsDeadlineHasPassed`. Where the provider
  publishes no party or no deadline, the field holds the provider's own sentence.
- Status is computed at read time against the run's clock, so an expired object reads as expired without being
  rewritten.

### Audit trail

Each row: `auditId`, `resolutionRule`, `direction`, `origin`, `outcome`, `method`, `path`, `status`, `accountId`,
`providerObjectId`, `scenarioId`, `refusal`, `delivered`, sanitized headers and body.

- `auditId` is the row's own identity, and `?auditId=` narrows the trail to that one exchange. It is what a cursor
  names, and what the dashboard's traffic rows and the trail's own links address, so an exchange that carries no
  `providerObjectId` is still reachable by one link. A blank one is read as absent and narrows nothing, the way a blank
  `accountId` does. An `?auditId=` longer than 64 characters, the width of the stored column, is `400`, and the refusal
  does not repeat what was sent.

- `outcome` is the provider's verdict and `status` the HTTP status. Both are kept, because Grow answers `200` to
  everything.
- `path` is what the application called: the absolute URL when a route alias matched.
- `origin` is the surface that caused the row. `SCHEDULED_JOB` is a callback attempt. An `?origin=` outside the four
  values is `400`.
- `accountId` is set where exactly one account acted. Rows written before the column existed carry none. An
  `?accountId=` longer than 128 characters is `400`. A blank one is read as absent and filters nothing
  (`AuditQuery.givenOrAbsent`).
- Every state-changing control or screen action writes one `INBOUND_REQUEST` row. Its `status` is the verdict (`302`
  for a screen's success). Its body says what the action did. `sanitizedHeaders` is `{}`, `outcome` and
  `resolutionRule` are `null`.
- A refused action writes a row too. A `GET`, an unknown provider, and a screen action naming no provider or run write
  none.
- An import's row is filed under the run it writes into, whatever the outcome: `testRunId` in a body, the `into` box
  on a screen, and the run the screen is on when that box is empty. A target that is a name no run may carry files the
  row under `UNKNOWN`, the id this build already uses for a resolution that failed, so one read answers what became of
  an import rather than two. Every row of one carries `into` and `dryRun`, and the secret fields it was given by name,
  whether a box or the provider turned it away.
- A reset's row is written after the clearing, so a reset run holds one row.
- `scenarioId` is set only where a rule decided the exchange. Rule writes and deletes carry `scenarioIds` in the body.
- A secret field, and any field no provider declares, is written `[redacted]`.
- `refusal.subject` is one of `THE_OBJECT` (`providerObjectId`), `NO_OBJECT_ANSWERED_TO` (`field`),
  `SEVERAL_OBJECTS_ANSWERED_TO` (`field`, `howManyAnswered`), `AN_OBJECT_IT_WILL_NOT_NAME` (`inItsOwnWords`) or
  `NO_OBJECT_WAS_NAMED` (`whatItRefusedInstead`). It is `null` on a row that took effect.
- `delivered` is set only on the outbound half of a provider exchange: `transportFault`, `faultPlacement`,
  `declaredContentLength`, `writtenBodyBytes`, `heldForMillis`.

### Webhook control

- `SEND` needs `providerObjectId` and, where the provider names events, `eventType`. `RETRY` needs `webhookJobId`.
- A `SEND` without `delayMs` is attempted before the call answers. With `delayMs` it is scheduled on the run's clock.
- `RETRY` makes one more attempt, also on an exhausted delivery.
- `reuseEventId` replays under the same event id. Byte-identical unless the provider renders each attempt afresh.
- A second `SEND` naming a different outcome for an object that already has one is `422`.
- The caller never names the target: the run's configured targets are the allowlist.
- The response lists every delivery the call scheduled in `deliveries`.
- `attemptOverride` is accepted by the request schema and read by nothing
  (`DeclaredWebhookCall.attemptOverride`).

## Run Bundle Schema

A run bundle is one provider's setup for one test run: accounts, callback targets, route aliases, settings, clock,
fixtures and scenario rules. It carries no object, no callback job and no audit. The schema is in the OpenAPI document.

- `compatibility` lists the account fields, payment-method fields, settings and action types the bundle was written
  against. Import accepts it when every term is still declared, and refuses naming the term that moved. There is no
  version number.
- `accounts[].configuration` carries only plain fields. `secretsItDoesNotCarry` names the withheld ones, never their
  values.
- `clock` is `{"fixedNow": ...}`, `{"fixedNow": null}` for a host clock, or `null`.
- `fixtures[].triggersRuleAt` names a rule by its position in the bundle. Import rewrites it to the new rule id.
- `scenarios` has the shape of `POST .../scenarios/presets/preview`, with `preset` `null`.
- A run with a `match.header` rule cannot be exported: `422` naming `scenarios.match.header.<name>`.

Import (`POST .../run-bundle/import`, body `testRunId`, `dryRun`, `bundle`, `secrets`):

- `testRunId` defaults to the bundle's run.
- `secrets` is keyed by account and field, may name only declared secret fields, and is never echoed, audited or
  exported.
- A bundle carrying a secret or an undeclared key is refused before anything is written.
- A dry run and a real import return the same record, differing only in `applied`.
- Each entry has a `kind` (`clock`, `account`, `webhookTarget`, `routeAlias`, `setting`, `scenarioRule`, `fixture`)
  and a `verdict`: `CREATED`, `UPDATED` or `SKIPPED`. A rule is always `CREATED`.
- `201` applied, `200` dry run, `400` no bundle, `409` a value claimed between the check and the write, `422` a bundle
  this provider will not take.

A repro bundle (`GET .../repro-bundle?key=`) carries the setup above under `setup` and the payment's evidence under
`evidence`. Import writes only the setup and keeps provider ids unless `remapProviderIds` is sent. A bundle with no
setup half is `400`.

## Contract Compatibility Checks

`GET /ui/smoke` shows the last run and `POST /ui/smoke/run` runs the checks. No control route starts them.

- Each check drives this simulator's own provider routes over HTTP in test run `smoke`, which it resets first.
- After each check, the run removes every account it configured in `smoke`, so no credential outlives the check.
- Every expected value comes from a vendor sample under `src/main/resources/vendor-samples/<provider>/`, declared in
  `samples.json` with its evidence class and source. The control API's own samples are under `simulator/`.
- A malformed manifest or a value a check reads that its sample lacks stops the application at startup.
- A failure names the provider, operation, field and sample, never the observed value.
- A check with no vendor sample is reported as not applying. A provider with no checks is shown as uncovered.

## Scenario Diff

`GET /__simulator/{provider}/scenarios/{scenarioId}/diff?against=` (with optional `againstProvider` and
`againstTestRunId`) compares two rules. `POST .../scenarios/{scenarioId}/copy` writes a new rule from one. Screens:
`GET /ui/scenarios/diff` and `POST /ui/scenarios/diff/copy`.

- The answer lists the properties that differ, those alike, and the dimensions a comparison cannot reach.
- A header is compared by name only. Its value is withheld.
- `404` names the missing side. `400` if no second rule is named. `422` for rules of two providers.

## Seeded Personas

`GET /__simulator/{provider}/personas` is the catalogue, `POST .../personas/preview` shows what a persona would write,
and `POST .../personas` writes it. Screens: `GET /ui/personas` and `POST /ui/personas/seed`, which previews until
`confirm` is sent.

- The core defines persona intents. A provider publishes at most one persona per intent, and says in its own words why
  it has none for the others.
- A persona writes its presets' rules and one payment-method fixture, into one run. Its identity is its fixture's alias.
- `200` catalogue and preview, `201` seeded, `400` no persona or account, `404` unknown name or provider, `409` the run
  already holds the instrument, `422` a preset the provider cannot express.
- A seed writes its rules and every instrument in one transaction, so a refused one leaves none behind.

The three intents (`PersonaIntent`), and the preset each persona for one writes:

| Intent                                      | What a tester gets                                                                | Preset it writes                  |
|---------------------------------------------|-----------------------------------------------------------------------------------|-----------------------------------|
| `a-saved-card-that-succeeds`                | an instrument the provider holds and then captures without a hitch                | `preauth-success-capture-success` |
| `a-saved-card-that-is-declined`             | an instrument whose capture the provider declines                                 | `preauth-success-capture-decline` |
| `a-saved-card-whose-capture-answer-is-lost` | an instrument whose capture the provider takes and never answers                  | `capture-accepted-response-lost`  |

- Which personas a provider publishes, the ids each mints and the card facts each seeds are in that provider's own
  file, under "Seeded Personas". `grow` publishes none for any intent, and that file says why.
- `merchantId` names the account the persona's instruments belong to, because a provider answers for a stored
  instrument only through the key that account is addressed with
  (`PersonaAdmission.A_PERSONA_IS_SEEDED_INTO_AN_ACCOUNT`).
- A persona name has the shape of a preset name, `[a-z][a-z0-9-]{0,63}`
  (`DeclaredPersona.A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS`).
- A persona seeds at least one instrument and never two under one alias, so pressing it twice can be told from
  pressing it once (`DeclaredPersona.A_PERSONA_SEEDS_AN_INSTRUMENT`, `TWO_INSTRUMENTS_UNDER_ONE_ALIAS`).
- The preview mints nothing. For an id the provider mints when the persona is created, it cannot foresee the `409` for
  a value the run already claims, and names those keys in `theRefusalThisPlanCannotForesee`
  (`PersonaAdmission.A_PROVIDER_MINTS_ITS_IDS_WHEN_THE_PERSONA_IS_CREATED`,
  `theProviderNativeKeysAPlanCannotCompare`). A key whose value the persona publishes rather than mints is left out of
  that sentence, because the value is known in advance (`PersonaFixture.theIdsItPublishes`), so a persona
  carrying no minted key of its own states no such refusal at all. An alias the run already holds the preview does
  foresee, in `theRunAlreadyHoldsIt`.
- The card facts a persona seeds are the provider's own published test card or sample. A card field the provider
  publishes no value for is seeded as `unpublished`.
- The catalogue carries, per persona, the intent, what a tester gets, the provider's own words, the preset names, and
  per instrument the alias, owner, type, card facts, the `providerIds` keys it carries and the minting that produces
  them (`ControlPayloads.PersonaView`, `PersonaInstrumentView`).

## Common Webhook and Callback Delivery

Payload shape and signing are in each provider file.

- A `2xx` acknowledges, unless the provider needs a separate call (Grow `APPROVE_TRANSACTION`).
- A non-`2xx`, timeout or connection error schedules the next attempt of the retry plan ("Provider settings").
- Redirects are not followed. Connect and read timeouts and the response preview size are
  `simulator.webhook.*` properties.
- Each event keeps a stable event id. A replay reuses it.
- Delays are on the run's clock. `POST .../webhooks/deliver-due`, or the background poll, attempts what is due.

| State           | Meaning                                                                                                                                                                                                                                                                                   |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PENDING`       | not finished. Includes a `2xx` still waiting for a separate acknowledgement: attempts marked `AWAITING_SEPARATE_CALL`, outcome processing                                                                                                                                                 |
| `DELIVERED`     | acknowledged                                                                                                                                                                                                                                                                              |
| `EXHAUSTED`     | out of attempts. Still listed. `RETRY` makes one more                                                                                                                                                                                                                                     |
| `DROPPED`       | withheld by a rule. One trail row, `OUTBOUND_WEBHOOK_DROPPED`, attempt `0`                                                                                                                                                                                                                |
| `UNDELIVERABLE` | cannot be rendered, e.g. the signing account was removed. Trail row `OUTBOUND_WEBHOOK_NOT_SENT`. Reconfigure and retry to send it. With `targetUrl` `null` the provider found no target: the trail row has no path, a `RETRY` is refused `409`, and the provider has to schedule it again |

- A screen retry that names another target, and whose attempt the provider withholds for any reason (Azericard: the
  target is not one of the account's; Stripe: the account is gone), is refused with a refusal row naming the delivery's
  object and a message carrying the provider's reason. The delivery keeps its state, schedule and attempts; a not-sent
  trail row records the withheld attempt. A withheld attempt at the delivery's own target, named or not, makes it
  `UNDELIVERABLE`. The control API retries only the delivery's own target.
- Every attempt is an `OUTBOUND_WEBHOOK_ATTEMPT` trail row with the provider's verdict, the HTTP status, the target and
  the headers sent.
- An attempt's `sentHeaders` is everything it put on the wire, at least the content type. It is `null` on attempts made
  before it was recorded.
- A delivery a rule shaped carries that rule's id as `shapedBy`, and on every trail row it produces.
- Acknowledgement is read at each attempt. A signature is rendered from the account as it stands at each attempt.

## Common Normalized State Vocabulary

| State                | Meaning                                                              |
|----------------------|----------------------------------------------------------------------|
| `NOT_INITIALIZED`    | exists, authorization not completed                                  |
| `READY_FOR_CAPTURE`  | funds held and capturable                                            |
| `PARTIALLY_CAPTURED` | part captured, the rest still capturable                             |
| `PROCESSING`         | accepted, outcome pending                                            |
| `SUCCEEDED`          | captured, or a payment method registered                             |
| `PARTIALLY_REFUNDED` | captured funds partly returned                                       |
| `REFUNDED`           | captured funds fully returned                                        |
| `FAILED`             | final failure                                                        |
| `CANCELLED`          | voided or cancelled                                                  |
| `UNKNOWN`            | cannot be reconciled automatically                                   |

- Every object with a lifecycle carries one, beside its native status. Refunds and other operations carry their own.
- A partly refunded payment is a `PARTIALLY_REFUNDED` payment plus a `SUCCEEDED` refund.
- A payment's status is derived from its operation list.

## The Screens a Tester Drives

The screens under `/ui` carry the control API's coverage and no more. Each control route's screen is in the "Routes"
table above, and `verifyClickableCapabilities` fails the build when a route has none. The rules a screen follows are in
`technical-ui-requirements.md`, "Screen Rules".

- **Starting a payment**: `GET /ui/objects/new` asks which kind of payment, then only for the fields that provider
  reads. `POST /ui/objects/start` runs it. A run with no account is told so first. The same two steps are the
  `originations` routes above, which read the same declaration and run the same core port.
- **Acting on a payment**: capture, cancel and refund on the payment, and a callback from its callback panel. Force a
  status, trigger a reconciliation response and mark as unknown are refused.
- **Hosted sessions**: a panel of `/ui/objects` listing the objects a payer finishes, with the `hostedSession` facts.
  Expiry is measured on the run's clock. A creation time is the simulator's own record.

## Connect my app

`/ui/pairing` shows, for one provider and one run: the public base URL, the provider's base URL, the `testRunId` and
its header, the account ids, their callback URLs, the run's route aliases, and one config snippet per account.
`GET /__simulator/{provider}/address` returns the same address.

- A provider's base URL is this simulator's host plus the provider's own path.
- Each provider declares its own snippet settings and one sentence on how a request names its account.
- A snippet prints only fields the provider declares plain. Secrets are named, never printed.
- The pairing payload, printed as text, is the screen's own URL, carrying provider and run.
- It links to `/ui/audit` filtered to provider traffic.

## Evidence Classes

Every vendor sample, and every statement in a provider file, names its class:

- **published rule**: prose or a table on the provider's documentation,
- **published example**: a saved example on a provider page or in the provider's official collection,
- **live probe**: observed against the provider. Re-confirm with sandbox credentials before relying on it,
- **simulator contract**: the control API's own samples, under `vendor-samples/simulator/`. Refused in any provider
  manifest.

Provider files also mark an **inference** (read off the wording) and a **simulator decision** (a choice with no vendor
source), and may name two classes no vendor sample can carry (`kernel/smoke/EvidenceClass` admits only the four above):

- **vendor script**: the provider's own published JavaScript, read for its public API and wire shapes,
- **vendor correspondence**: the provider's written answer to a merchant, not published. The weakest class.

Where a provider's rule and example disagree, the file records both.
