# Stripe Simulator Contract

## What This File Covers

Stripe `/v1` as served by this simulator. Shared terms: [evidence classes](README.md#evidence-classes) and
[normalized states](README.md#common-normalized-state-vocabulary).

## Supported Variants

| Find | Contract |
|---|---|
| Routes and secret, publishable or ephemeral access | [Routes](#routes) |
| Credentials, HTTP, form grammar, stored values | [Authentication and Transport](#authentication-and-transport) |
| Server releases, mobile pins and breaking changes | [API Versions](#api-versions) |
| Dynamic or explicit payment methods; redirect requirements | [Payment Method Selection](#payment-method-selection) |
| Relations and includables | [Expansion](#expansion) |
| Client secrets and reduced responses | [Publishable Key](#publishable-key) |
| Card input, tokens, inline methods and test-id copies | [PaymentMethod Creation](#paymentmethod-creation) |
| Retry and replay | [Idempotency](#idempotency) |
| Objects and monetary operations | [Minimum API Surface](#minimum-api-surface) |
| Outcomes, statuses, events and errors | [Outcome control](#where-the-outcome-lives), [mapping](#status-mapping), [webhooks](#webhooks), [errors](#required-error-shape) |
| Test cards and personas | [Test Data](#test-data), [Seeded Personas](#seeded-personas) |
| Unsupported behaviour and evidence limits | [Omissions](#what-still-cannot-be-trusted-here) |
| HTTP save-then-charge and manual capture | [Generic Flow](#generic-flow) |

| Client | Evidence |
|---|---|
| Raw form/query HTTP | example: [Generic Flow](#generic-flow) |
| `stripe-java 33.4.2`, `2026-08-26.dahlia` | example: [Java form encoding][java-form] and [mobile-key version requirement][java-ephemeral] |
| Stripe's iOS client traffic at `2020-08-27` | probe: [recordings][recordings]; [sample coverage](#sources) |

## Generic Flow

Simulator call examples (**decision**).

### Configure the run

Set `BASE=http://localhost:8080`, `SK=sk_test_StripeFlow`, `PK=pk_test_StripeFlow`.
[Account settings and credential resolution](#account-settings):

```sh
curl -X PUT "$BASE/__simulator/stripe/config" -H 'Content-Type: application/json' -d '{
  "testRunId":"stripe-flow",
  "clock":{"fixedNow":"2026-11-01T09:00:00Z"},
  "accounts":[{"accountId":"merchant-1","configuration":{
    "secretKey":"sk_test_StripeFlow","publishableKey":"pk_test_StripeFlow",
    "captureWindow":"PT2H","apiVersion":"2026-10-28.endive"}}]
}'
```

### Save a card, then charge it

| Step | Variables from the response | Details |
|---|---|---|
| Create Customer | `CUSTOMER=id` | [Customers](#customers) |
| Create SetupIntent | `SETUP=id`, `CLIENT_SECRET=client_secret` | [SetupIntents](#setupintents), [selection](#payment-method-selection) |
| Confirm SetupIntent | `METHOD=payment_method` | [Client access](#publishable-key), [test-id copies](#inline-methods-and-test-id-copies) |
| Retrieve and list the saved method | — | [PaymentMethods](#paymentmethods) |
| Charge the saved method | — | [PaymentIntents](#paymentintents), [off-session validation](#confirmation-errors) |

```sh
curl "$BASE/v1/customers" -u "$SK:" -d email=payer@example.com
curl "$BASE/v1/setup_intents" -u "$SK:" -d customer="$CUSTOMER" \
  -d usage=off_session -d 'allowed_payment_method_types[]=card'
curl "$BASE/v1/setup_intents/$SETUP/confirm" -u "$PK:" \
  -d client_secret="$CLIENT_SECRET" -d payment_method=pm_card_visa
curl "$BASE/v1/payment_methods/$METHOD" -u "$SK:"
curl -G "$BASE/v1/customers/$CUSTOMER/payment_methods" -u "$SK:" -d type=card
curl "$BASE/v1/payment_intents" -u "$SK:" -d amount=1000 -d currency=usd \
  -d customer="$CUSTOMER" -d payment_method="$METHOD" -d confirm=true -d off_session=true
```

Alternatives to the published test id: [inline card data](#inline-methods-and-test-id-copies), or a
[fixture](#paymentmethods) followed by confirm with `payment_method=pm_flow_fixture`:

```sh
curl "$BASE/__simulator/stripe/fixtures/payment-methods" -H 'Content-Type: application/json' -d '{
  "testRunId":"stripe-flow","merchantId":"merchant-1","ownerId":"payer","type":"card",
  "alias":"saved-card","card":{"brand":"visa","last4":"4242","expMonth":12,"expYear":2030,"funding":"credit"},
  "providerIds":{"paymentMethodId":"pm_flow_fixture"}
}'
```

[Independent setup/charge controls](#where-the-outcome-lives); repeat the charge with `amount=1200`:

```sh
curl "$BASE/__simulator/stripe/scenarios" -H 'Content-Type: application/json' -d '{
  "testRunId":"stripe-flow","priority":100,
  "match":{"operation":"POST /v1/setup_intents/{id}/confirm"},
  "action":{"type":"FIXED_SUCCESS"}
}'
curl "$BASE/__simulator/stripe/scenarios" -H 'Content-Type: application/json' -d '{
  "testRunId":"stripe-flow","priority":100,
  "match":{"operation":"POST /v1/payment_intents","amount":"1200"},
  "action":{"type":"TERMINAL_FAILURE","providerCode":"insufficient_funds"}
}'
```

| Example read | Result |
|---|---|
| Charge `1000` | `succeeded`, `amount_received=1000`, `latest_charge` id |
| Charge `1200` under the rule | [Insufficient-funds error](#test-data); retrievable `requires_payment_method` intent; saved setup/method retained |

### Authorize, capture or release

For each alternative create a separate intent. Read `INTENT=id`, `PAYMENT_SECRET=client_secret` from create;
read `CHARGE=latest_charge` from the merchant's secret-key retrieve. [Client response fields](#publishable-key).

```sh
curl "$BASE/v1/payment_intents" -u "$SK:" -d amount=1000 -d currency=usd \
  -d customer="$CUSTOMER" -d capture_method=manual -d 'allowed_payment_method_types[]=card'
curl "$BASE/v1/payment_intents/$INTENT/confirm" -u "$PK:" \
  -d client_secret="$PAYMENT_SECRET" -d payment_method="$METHOD"
curl "$BASE/v1/payment_intents/$INTENT" -u "$SK:"
```

[Capture/cancel semantics](#paymentintents); examples:

| Alternative | Server call or control call | Server read |
|---|---|---|
| Authorization | Secret-key retrieve | `requires_capture`, `amount_capturable=1000`, `amount_received=0`, `latest_charge` id; use that id as `CHARGE` |
| Full capture | `POST /v1/payment_intents/$INTENT/capture`, empty body | `succeeded`, received `1000`, capturable `0`; Charge captured `1000`, `captured=true` |
| Partial, then final | Capture `amount_to_capture=300&final_capture=false`, then `amount_to_capture=250&final_capture=true` | After first: capturable `700`; after final: received `550`, capturable `0`; Charge amount `1000`, captured `550` |
| Release | `POST /v1/payment_intents/$INTENT/cancel` | `canceled`, received/capturable `0` |
| Expiry | `POST /__simulator/stripe/clock/advance`, `{"testRunId":"stripe-flow","advanceBy":"PT2H"}` | [Expired hold](#paymentintents) |

```sh
curl -X POST "$BASE/v1/payment_intents/$INTENT/capture" -u "$SK:"
curl "$BASE/v1/charges/$CHARGE" -u "$SK:"
```

### Reconcile a lost response and expand a read

Use a separate authorization. [Fault reconciliation](#status-mapping), [idempotency](#idempotency),
[expansion](#expansion):

```sh
curl "$BASE/__simulator/stripe/scenarios/presets" -H 'Content-Type: application/json' \
  -d '{"testRunId":"stripe-flow","preset":"capture-accepted-response-lost"}'
curl -X POST "$BASE/v1/payment_intents/$INTENT/capture" -u "$SK:" -H 'Idempotency-Key: flow-capture'
curl "$BASE/v1/payment_intents/$INTENT" -u "$SK:"
curl -X POST "$BASE/v1/payment_intents/$INTENT/capture" -u "$SK:" -H 'Idempotency-Key: flow-capture'
curl -G "$BASE/v1/payment_intents/$INTENT" -u "$SK:" \
  -d 'expand[]=customer' -d 'expand[]=payment_method' -d 'expand[]=latest_charge'
```

## Routes

Access is scoped to the key's account and the resolved test run (**decision**). Ephemeral access is also scoped to its
customer. Authentication/refusal details: [transport](#authentication-and-transport), [client secrets](#publishable-key),
[errors](#authentication-and-version-errors). Scenario column: **decision**; operation details are linked.

| Route | Secret | Publishable | Ephemeral | Scenario | Details |
|---|---|---|---|---|---|
| `POST /v1/customers` | yes | no | no | no | [Customers](#customers) |
| `GET /v1/customers/{id}` | yes | no | own customer | no | [Customers](#customers) |
| `POST /v1/customers/{id}` | yes | no | no | no | [Customers](#customers) |
| `DELETE /v1/customers/{id}` | yes | no | no | no | [Customers](#customers) |
| `POST /v1/ephemeral_keys` | yes | no | no | no | [Ephemeral Keys](#ephemeral-keys) |
| `POST /v1/setup_intents` | yes | no | no | no | [SetupIntents](#setupintents) |
| `GET /v1/setup_intents/{id}` | yes | with `client_secret` | no | no | [SetupIntents](#setupintents) |
| `POST /v1/setup_intents/{id}/confirm` | yes | with `client_secret` | no | yes | [SetupIntents](#setupintents) |
| `POST /v1/payment_intents` | yes | no | no | yes | [PaymentIntents](#paymentintents) |
| `GET /v1/payment_intents/{id}` | yes | with `client_secret` | no | no | [PaymentIntents](#paymentintents) |
| `POST /v1/payment_intents/{id}/confirm` | yes | with `client_secret` | no | yes | [PaymentIntents](#paymentintents) |
| `POST /v1/payment_intents/{id}/capture` | yes | no | no | yes | [PaymentIntents](#paymentintents) |
| `POST /v1/payment_intents/{id}/cancel` | yes | no | no | yes | [PaymentIntents](#paymentintents) |
| `GET /v1/charges/{id}` | yes | no | no | no | [Charges](#charges) |
| `POST /v1/refunds` | yes | no | no | yes | [Refunds](#refunds) |
| `POST /v1/payment_methods` | yes | yes, no secret | no | no | [Creation](#paymentmethod-creation) |
| `GET /v1/payment_methods` | yes | no | no | no | [PaymentMethods](#paymentmethods) |
| `GET /v1/customers/{id}/payment_methods` | yes | no | own customer | no | [PaymentMethods](#paymentmethods) |
| `GET /v1/payment_methods/{id}` | yes | no | no | no | [PaymentMethods](#paymentmethods) |
| `POST /v1/payment_methods/{id}/detach` | yes | no | no | no | [PaymentMethods](#paymentmethods) |

## Authentication and Transport

| Item | Contract | Evidence |
|---|---|---|
| Base | Stripe: `https://api.stripe.com/v1/...`; simulator base configurable | example [auth]; decision |
| Authentication | Basic username is the API key; password unnecessary. Bearer also accepted unconditionally | rule [auth]; decision |
| Key families | `sk_test_`, `pk_test_` as configured; restricted `rk_test_` and all live-mode prefixes unsupported | rule (prefixes) [keys]; decision (support) |
| TLS | Stripe rejects plain HTTP; simulator does not enforce TLS | rule [auth]; decision |
| Body/query | POST reads only `application/x-www-form-urlencoded`; other body types, including JSON, supply no fields. GET reads its query; responses are JSON | example [auth], [java-form]; decision |
| `Request-Id` | Every API answer; `req_sim_` + 24 hex characters. Stripe publishes the header name and an error request id beginning `req` | rule [errors]; decision (shape) |
| CORS on Stripe routes | `Access-Control-Allow-Origin: *`; `Access-Control-Allow-Credentials: true`; methods `GET, HEAD, PUT, PATCH, POST, DELETE`; expose `Request-Id, Stripe-Manage-Version, Stripe-Should-Retry, X-Stripe-External-Auth-Required, X-Stripe-Privileged-Session-Required`; max age `300`; `Vary: Origin`, with or without `Origin` | probe [recordings]: 3744/3745 responses, including errors |
| Preflight | OPTIONS with `Origin` and `Access-Control-Request-Method`: `200`, same CORS headers, requested `Access-Control-Allow-Headers`; no endpoint execution | decision: Authorization-bearing browser requests need it; no recorded preflight |
| CORS scope | Stripe addresses after alias resolution only; absent from other providers, UI, control API and trail | decision |

### Account settings

| Field | Constraint/default | Evidence |
|---|---|---|
| `secretKey` | Required `sk_test_`, at most 255 characters | rule (prefix) [keys]; decision (bound) |
| `publishableKey` | Optional `pk_test_`, at most 255; blank means none; disclosed without redaction | rule (public key/prefix) [keys]; decision (bound/blank) |
| Legacy publishable key | Accounts stored without it have none | decision |
| Key uniqueness | An account's credentials are unique within a run; different runs may reuse them. An unconfigured run cannot use the key | decision |
| `captureWindow` | Required ISO-8601 duration, at least one microsecond; no default, because stored deadlines have microsecond precision | decision; [capture expiry](#paymentintents) |
| `refundStatus` | Default `succeeded`; allowed [refund states](#refunds) | decision |
| `apiVersion` | [Version resolution](#api-versions) | rule/decision there |
| `webhookSecret` | Optional `whsec_` signing secret | rule [webhooks] |
| `webhookSignature` | `VALID` or `CORRUPTED` | decision |
| `webhookTimestampAge` | ISO-8601 duration | decision |

Requests resolve the run from configured credentials; an application needs no simulator header (**decision**).

### Form grammar

Examples: curl repeats `expand[]=customer`; Java numbers `expand[0]=customer`, sends an empty list as `expand=`, and
leaves query brackets unescaped (**example**, [expand], [java-form]). Stripe publishes no complete grammar or malformed-form rule.

| Input | Reading | Evidence/reason |
|---|---|---|
| `amount`, `metadata[order_id]`, `card[exp][month]`, `expand[]` | Word followed by bracketed keys | decision: sample shapes |
| Repeated `name[]=v` | Append in sent order | decision |
| `name[0]=v` | Hash key `0`; numeric keys become indexes only when a list is requested | decision: `metadata[0]` can be a hash key |
| List reader | Repeated `[]` or exactly indexes `0`…`n-1`, read in index order; absent or empty `name=` means no elements | decision: equivalent client encodings |
| List of hashes | `name[0][key]`; `a[][b]` is unreadable | decision: no element identified |
| Scalar reader | Plain name only; hash/list means absent | decision |
| Body and query | Both decoded after authentication/version resolution, before idempotency claim, on every route | decision; [validation/replay](#idempotency) |
| Trail names | Original spelling retained, subject to [withholding](#withholding-and-write-effects) | decision |
| Query brackets | Unescaped `[` and `]` accepted | example [java-form]; decision |
| Malformed names, duplicate scalars, conflicting shapes or non-list input | [Encoding errors](#parameter-and-expansion-errors) | decision |

### Resource identifiers

| Resource/value | Simulator shape or bound | Evidence |
|---|---|---|
| Customer id | `cus_sim_` + 16 hexadecimal characters | decision |
| PaymentMethod id | `pm_sim_` + 20 hexadecimal characters | decision; copying [probe R-attached][r-attached] |
| Ephemeral key id | `ephkey_sim_` + 16 hexadecimal characters; never the secret's `ek_sim_` stem | example [ephemeral-object]; decision (marking, width/alphabet) |
| Ephemeral secret | Opaque `ek_sim_` + 32 hexadecimal characters, drawn independently of its id; not `{id}_secret_{random}` | example [ephemeral-object], [android-sdk]; decision (shape) |
| Accepted `customer` / `payment_method` ids | At most 128 characters, including fixtures; narrower than the generic 5000-character string bound in [spec-2026-10-28.endive] | decision |
| Fixture Customer id | Refuse prefixes `pi_sim_`, `re_sim_`, `seti_sim_`, `ch_sim_`, `ephkey_sim_`; allow `cus_sim_` | decision: resource kinds must remain distinct |
| Fixture PaymentMethod id | Refuse prefixes `pi_sim_`, `re_sim_`, `cus_sim_`, `seti_sim_`, `ch_sim_`, `ephkey_sim_` | decision: resource kinds must remain distinct |
| Fixtures | Require an account matching `merchantId`; a fixture cannot reuse an id already held by a PaymentMethod (`422`, `providerIds.paymentMethodId`) | decision |

### Withholding and write effects

| Surface/effect | Contract | Evidence |
|---|---|---|
| Card input | Discard `number` and `cvc` after validation; retain card facts such as `last4` | rule [testing] (PCI); decision |
| Free text | Store and echo `metadata` / `billing_details` through API answers, reads and API replay, even if it passes Luhn | decision: an order number can pass Luhn; no published refusal |
| Observational records/display | In request/response trail, keyed-exchange answers, webhook history/attempts, control reads, UI and reproduction evidence, withhold keys containing `card` then `number`/`cvc`, and any 12–19-digit Luhn-valid run in a name/value, ignoring spaces/dashes | decision |
| Secrets | Withhold `client_secret` / `secret` at any depth in audit/keyed-exchange answers and UI; request trail names the account | decision: the secret opens the resource |
| Webhook bytes | Apply the card floor before storing, signing and delivery; retries reuse those bytes and sign afresh. Flattened history also withholds `data.object.client_secret`; it is not the signed body | decision; rule (signature input) [webhooks] |
| Run bundle | Configuration and fixtures only, not API-created methods; reproduction evidence uses withheld records | decision |
| Parameter comparison | Store a keyed fingerprint, not raw parameters, in keyed exchanges | decision |
| Persisted text | No NUL; [400 refusal](#parameter-and-expansion-errors) before effects. Ignored parameters are not stored | decision |
| Commit | Intent change, minted method, charge/ledger, scenario step and scheduled event form one unit; failed write discards it; delivery follows commit | decision |
| Preempt | Keep the scenario transition for then-success presets, with no method, monetary effect or idempotent answer; explicitly discarding the unit also rolls back its story | decision |

## API Versions

| Choice/rule | Contract | Evidence |
|---|---|---|
| Published format | Dates `2011-06-21`…`2024-06-20`; from `2024-09-30.acacia`, `date.codename`; dated `.preview` releases also exist | rule [changelog] |
| Release compatibility | Breaking changes in a major's first release; monthly releases are additive | rule [api-versions] |
| Supported server versions | Every regular basil, clover, dahlia and endive release, `2025-03-31.basil`…`2026-10-28.endive` | inference: basil capture/cancel semantics; [changes below](#version-changes) |
| SDK version headers | iOS pins `2020-08-27`, Android `2020-03-02`; both send `Stripe-Version`, including iOS Customer reads | example [ios-sdk], [android-sdk]; probe [recordings] |
| Mobile pins | `2020-03-02`, `2020-08-27` additionally accepted on publishable/ephemeral routes and secret-key `POST /v1/ephemeral_keys`; no other pre-basil version accepted | decision; example [ios-sdk], [android-sdk], [java-ephemeral]; probe [recordings] |
| Resolution | First non-blank `Stripe-Version` header, case-insensitive name, trimmed value; otherwise account `apiVersion` | rule [versioning]; decision (reading) |
| Account default | Empty setting and legacy accounts without a stored version get fixed `2026-10-28.endive`; stored defaults do not move with the release catalogue | decision |
| Stored default shape | `date.codename`, excluding `.preview` | decision |
| Validation order | Authentication → version → form → client-secret validation → idempotency claim; version refusal claims no key | decision |
| Response header | `Stripe-Version` names the answered version, including form refusals; absent on `401`, version refusal and forbidden publishable route | probe [recordings]: 3734/3745 responses; decision (refusal boundaries) |
| Beta suffix | Real traffic echoes `2020-08-27, vipps_preview=v1`; simulator serves no beta | probe [recordings]; decision |
| Unsupported/future/invalid values | [Version errors](#authentication-and-version-errors); values compared exactly | decision; reported invalid-version text [invalid-version-report] |
| Event version | Endpoint version if set, otherwise account default in Stripe; simulator has no endpoint objects and fixes `api_version` to the account default when scheduling, including a pending outcome scheduled before its event's `created` | rule [webhook-versioning]; decision (scheduling) |
| Event retries | Same event body/version on every attempt | rule [webhook-versioning]; decision (snapshot timing) |

### Version changes

| Change | Release | Operations | Behaviour | Evidence |
|---|---|---|---|---|
| Writable `payment_method_types` removed | `2026-09-30.endive` | PI create/confirm, SI create; Stripe also removes it from update | Older requests retain the written selection; endive uses the read-only computed object field and [refuses writes](#selection-errors) | rule [endive-selection], [spec-2026-08-26.dahlia]; also published at unsupported `2026-08-26.preview` [preview-selection] |
| Expiry reason | `2025-03-31.basil` | PI create/confirm/capture/cancel/retrieve, Charge retrieve, Refund create; embedded/expanded/error/event PI objects included | `expired` at basil and newer; `automatic` at mobile pins. Requests unchanged | rule [basil-capture] |
| Change declaration | Each answered breaking release | At least one declared operation | Release, changelog URL, operations and request/response transformations | rule [version-design]; decision (release/operation bounds) |
| Core transformation | All declared changes | Declared operations only | Current supported release at core; request changes oldest first, response changes newest first; request's own release excluded; mobile pins take every declared change; transform before replay storage | rule [version-design]; decision (order and declaration bounds) |
| Additions | All answered versions | Responses, requests, enums, nested references and event/error objects | [Additive schema rules](#response-conformance) apply across versions | rule [version-design]; probe [r-link], [r-apple] |

| Mobile-response evidence | Consequence | Evidence |
|---|---|---|
| `automatic_async` in 179 `2020-08-27` responses | Answer unchanged at the mobile pins | probe [recordings] |
| `2022-11-15` decline-code additions | None of the added values is emitted here | rule [decline-pi], [decline-si] |
| `charges` removed at `2022-11-15` | No recorded publishable response carries it | probe [recordings] |
| `2023-08-16` / `2024-04-10` defaults | Affect server creation, not a publishable operation | rule [selection-change], [async-default] |
| `2020-03-02`…`2020-08-27` | No intervening breaking change affects an intent, Customer or PaymentMethod | rule [changelog] |

### Response conformance

| Schema rule/boundary | Evidence |
|---|---|
| Emitted fields follow their answered-version JSON type, nullability, enum, expandable targets and list items | rule [spec-2026-10-28.endive] |
| An additive field uses its earliest published definition, including nested/transitive references; additive enum values are allowed, declared breaking values are not backfilled | rule [version-design]; probe [r-link], [r-apple] |
| `x-stripeBypassValidation` bypasses ordinary enum membership only, not declared breaking values; unknown fields/values are invalid | rule [spec-2026-10-28.endive]; decision |
| Field removal or narrowing of type/reference/union, nullability or enum requires a declared breaking change; deliberately omitted fields/unrelated schemas are outside the emitted surface | decision; [breaking-change manifest](../src/test/resources/stripe-openapi/breaking-changes.json) |
| Rendered response roots, lists and referenced objects | decision; [omission manifest](../src/test/resources/stripe-openapi/omissions.json), [omissions](#what-still-cannot-be-trusted-here) |
| A complete recorded `2020-08-27` PaymentMethod fits the schema; legacy PI `source` is absent from every pinned public schema; [rendered omissions](#what-still-cannot-be-trusted-here) | probe [r-method]; decision |
| Ephemeral-key [customer association](#ephemeral-keys) is a published example absent from OpenAPI, applied at every answered version | example [ephemeral-object]; decision; [example extension](../src/test/resources/stripe-openapi/evidence-overrides.json) |
| Immutable per-release response snapshots retain references, types, nullability, enums, properties, unions/intersections, items, additional properties and Stripe extensions; no requests/descriptions/unrelated schemas | decision; [snapshot sources](#sources) |

## Payment Method Selection

| Parameter | PI create | PI confirm | SI create | SI confirm | Evidence |
|---|---|---|---|---|---|
| `automatic_payment_methods` | yes | ignored | yes | ignored | rule [spec-2026-10-28.endive]; decision (unread parameters) |
| `allowed_payment_method_types` | yes | yes | yes | yes | rule: introduced `2026-07-29.dahlia` [allowed-selection]; additive across answered versions |
| `excluded_payment_method_types` | list | list or empty `""` | list | ignored | rule [spec-2026-07-29.dahlia], [spec-2026-10-28.endive] |
| `payment_method_configuration` | yes | ignored | yes | ignored | rule [pi-create], [si-create]; bound [stored values](#resource-identifiers) |
| `payment_method_types` | [version change](#version-changes) | [version change](#version-changes) | [version change](#version-changes) | ignored at every release: never published here | rule [spec-2026-07-29.dahlia], [spec-2026-08-26.dahlia] |

| Selection | Result | Evidence/reason |
|---|---|---|
| Modelled compatibility | `card`, `paypal`; Dashboard assumed to enable both. Cards support most currencies; PayPal: AUD, CAD, CHF, CZK, DKK, EUR, GBP, HKD, NOK, NZD, PLN, SEK, SGD, USD. Both support PI, SI and manual capture | rule [method-support], [paypal]; decision (model/Dashboard) |
| Compatibility filter | Currency and redirects only; SI has no currency. `allow_redirects=never` removes PayPal, not card | rule [selection-change], [method-support]; decision (no amount, country or customer filter) |
| Dynamic | No selector, `enabled=true`, exclusions or configuration: compatible modelled types, not excluded, ordered `card`, `paypal`; object has `automatic_payment_methods.enabled=true` | rule (default) [selection-change]; decision (order) |
| Dynamic redirect flag | Echo as sent; omit `allow_redirects` when absent, not `null` | rule [spec-2026-08-26.dahlia], [spec-2026-10-28.endive] |
| `enabled=false` alone | Offer `card` | rule [selection-change] (pre-dynamic default) |
| Allowed list | Silently filter incompatible/unmodelled types; preserve sent order, deduplicate; echo deduplicated allowed list | rule [allowed-selection]; decision (order/unmodelled types) |
| Explicit type list | String list with no schema enum; preserve order, deduplicate; incompatible, unmodelled or unpublished type [refuses](#selection-errors), rather than filtering | rule [allowed-selection], [spec-2026-08-26.dahlia]; probe (shape) [r-invalid-type] |
| Published enums | Allowed list: 99 values; exclusions: 55 values | rule [spec-2026-10-28.endive] |
| Configuration | Opaque id, at most 100 characters; no lookup; dynamic selection; details `{"id": sent, "parent": null}`, otherwise `null` | rule (bound) [spec-2026-10-28.endive]; decision: no configuration objects |
| Automatic hash | Required `enabled` boolean; `allow_redirects` enum `always`/`never` | rule [spec-2026-10-28.endive] |
| Object fields | `allowed_payment_method_types`, `automatic_payment_methods`, `excluded_payment_method_types`, `payment_method_configuration_details`, computed `payment_method_types` | rule [pi-object], [si-object] |
| Confirm replacement | Allowed/explicit list replaces selection; exclusions recompute dynamic selection; no selector preserves it; persist only with the confirm's state change | decision |
| Empty exclusions on PI confirm | Dynamic selection recomputed without exclusions, object shows `null`; otherwise keep selection, accept empty value | rule (empty form) [spec-2026-10-28.endive]; decision (effect) |
| Legacy stored selection | PI keeps `["card"]`, SI `["card", "paypal"]`; both read as dynamic | decision: preserve their answered lists |
| Dynamic confirmation | Requires `return_url` unless redirects are disabled; PI additionally accepts an off-session exemption; blank URL is absent | rule [selection-change]; [off-session values/errors](#confirmation-errors) |
| Type of a held method | Must be offered by the selected set; [incompatible-method errors](#selection-errors) | decision; published code descriptions [codes] |
| Unheld test method | No type check when no published card hash exists; [resolution boundary](#inline-methods-and-test-id-copies) | decision |

| Exclusive combination | Evidence/reason |
|---|---|
| Allowed list with explicit list, exclusions, automatic hash or configuration | rule [allowed-selection] |
| Explicit list with `enabled=true` | decision: fixed versus computed list; report [exclusive-report] |
| Exclusions/configuration with explicit list or `enabled=false` | decision: both need dynamic selection |
| Non-empty confirm exclusions on a non-dynamic selection | decision; error names the parameter that fixed selection |
| Explicit list with `enabled=false` | Accepted (decision) |

Errors: [Selection errors](#selection-errors). Confirmation validation order:
[Confirmation errors](#confirmation-errors).

## Expansion

| Rule | Contract | Evidence |
|---|---|---|
| Request scope | `expand` affects only that response; available on list/create/update/read endpoints with expandable fields | rule [expand-api], [expand] |
| Parameter location | GET query, POST body; other location ignored; no DELETE expansion; ephemeral key has no expandable field | rule [spec-2026-10-28.endive]; decision (unread location) |
| Paths | Dot-separated nesting, at most four properties, `data` included; `data` enters a list at any level | rule [expand], [expand-api] |
| No expansion | Absent/empty `expand=` returns ids and omits includables; an unset relation stays `null` | rule [expand], [spec-2026-10-28.endive] |
| Validation | Before endpoint execution/object lookup; merge shared prefixes; a refusal has no effects | decision |
| Property vocabulary | Judge against `2026-10-28.endive` at every answered version; `payment_record` and `paypay` accepted although absent at `2026-07-29.dahlia` / `2026-08-26.dahlia`; `sequra` accepted although absent at `2026-07-29.dahlia` | decision; [spec-2026-07-29.dahlia], [spec-2026-08-26.dahlia] |
| Published non-expandable property | Accept and stop judging trailing segments: e.g. `amount`, `metadata`, `review`, `card`, `customer.test_clock`; omitted properties stay absent, including Refund `customer`/`payment_method`, Charge `balance_transaction` | decision from [probe R-ignored][r-ignored]; no recorded secret-key equivalent |
| Lists | Accept `object`, `has_more`, `url` as no-ops; other paths pass through `data` | decision; [reported errors](#parameter-and-expansion-errors) |
| Scoping | Expand only same-account/run objects/refunds. Missing, foreign or removed-fixture targets stay ids; never invent an object or unset an existing reference | decision |
| Deleted customer | [Deleted Customer object](#customers); no includables | rule [spec-2026-10-28.endive] |
| Repeated relation | Read a shared target once per response | decision: one response describes one moment; lists have no paging bound |
| Errors | Expand successful answers only; embedded error intents are not expanded | decision: recordings do not settle it; [decline method rendering](#required-error-shape) is independent of `expand` |
| Client/control views | [Publishable reduction](#publishable-key); control origination/actions do not read `expand` | decision |
| Replay | [Idempotency](#idempotency) stores the expanded answer | rule |

Schema evidence: relations are `anyOf` id/object; Customer also admits deleted shape; expandable fields are in
`x-expandableFields`. Includables need `expand` even when absent from that list, as `invoice_credit_balance` is
(**rule**, [spec-2026-10-28.endive], [customer-object]).

| Object | Relation/includable | Answer | Evidence |
|---|---|---|---|
| PaymentIntent | `customer`, `payment_method`, `latest_charge` | [Customer](#customers), [PaymentMethod](#paymentmethods), [Charge](#charges) | rule [pi-object] |
| SetupIntent | `customer`, `payment_method` | [Customer](#customers), [PaymentMethod](#paymentmethods) | rule [si-object] |
| PaymentMethod | `customer` | [Customer](#customers); lists use `data.customer` | rule [method-object] |
| Charge | `customer`, `payment_intent` | Its PI's Customer, including deleted shape; [PI](#paymentintents) | rule [charge-object] |
| Charge | included `refunds` | Nullable RefundList shape; all refunds newest first, `has_more=false`, `url=/v1/charges/{id}/refunds`; `refunds.data.` enters items | rule [charge-object], [spec-2026-10-28.endive]; decision (no paging route) |
| Refund | `charge`, `payment_intent` | [Charge](#charges), [PI](#paymentintents) | rule [refund-object] |
| Customer | included `sources`, `subscriptions`, `tax_ids` | Empty `object=list`, `data=[]`, `has_more=false`, `url=/v1/customers/{id}/{property}` | rule [customer-object]; decision (no items modelled) |
| Customer | included `cash_balance`, `invoice_credit_balance` | `null`, `{}` respectively | rule (nullable cash; map of balances) [customer-object]; decision (empty model) |
| Customer | included `tax` | `automatic_tax=unrecognized_location`, `ip_address=null`, `location=null`, `provider=stripe` | rule (provider default) [customer-object]; decision (no address/IP) |
| List | `data` | Apply subsequent path to each listed PaymentMethod | rule [expand] |

Auto-expansion ended for Customer sources/subscriptions/tax ids at `2020-08-27` and Charge refunds at `2022-11-15`
(**rule**, [changelog], [charge-auto-expand]); none of the answered versions auto-expands them.

## Publishable Key

[Route access](#routes), [key configuration](#account-settings), [secret refusal messages](#client-secret-errors).

| Rule | Contract | Evidence |
|---|---|---|
| Recorded visibility | PI omits `customer`, `metadata`, `latest_charge`, `amount_received`, `amount_capturable`; SI omits `customer`, `metadata`, `latest_attempt`, `mandate`. Recorded allowlists below exceed page badges | probe [recordings]; rule (badges) [pi-object], [si-object] |
| Permission | Client key can identify the account and create tokens/PMs, not read account data or create charges | rule [keys] |
| Intent retrieval | `client_secret` in query; return a subset | rule [pi-retrieve], [si-retrieve] |
| Mobile request example | PaymentSheet retrieves/confirms with `expand[0]=payment_method`; confirm sends `payment_method` and `use_stripe_sdk=true` in addition to the secret. Unread fields follow [omissions](#what-still-cannot-be-trusted-here) | probe [r-valid-pi], [r-valid-si]; example [ios-sdk] |
| Intent confirm | `client_secret` in body; same operation, scenarios, selection and redirect rules as secret-key confirmation | rule [pi-confirm], [si-confirm]; decision (same core) |
| Secret validation | After key/version/form, before idempotency: require nonblank scalar → find own account/run intent → compare exact secret. Refusals have no state/key effect | rule (required) [pi-confirm], [si-confirm]; probe [r-wrong-secret], [r-mismatched]; decision (order/malformed/SI wording) |
| Secret-key request | Does not read `client_secret` | inference: published requirement is only for publishable keys |
| Manual confirmation | Stripe requires a secret key for `confirmation_method=manual`; this field is not modelled, so no simulator intent is manual | rule [pi-confirm]; decision |
| Reduction | Apply recursively on fresh/replayed answers, errors, lists, includables and event envelopes, at any depth; expanded Customer/Charge/Refund becomes its id; PM loses metadata and reduces nested relations | decision; rule [keys]; probe [r-attached], [r-decline-pi] |
| Expansion | Validate paths normally; apply the reduction above | probe (PM customer id) [r-attached]; decision (other relations) |
| Allowlist | A listed but unmodelled member stays absent; [omissions](#what-still-cannot-be-trusted-here) | probe [recordings]; rule (schema badges) [pi-object], [si-object] |

| Object | Publishable allowlist | Evidence |
|---|---|---|
| PaymentIntent | `allowed_payment_method_types`, `amount`, `amount_details`, `amount_subtotal`, `automatic_payment_methods`, `canceled_at`, `cancellation_reason`, `capture_method`, `client_secret`, `confirmation_method`, `created`, `currency`, `description`, `excluded_payment_method_types`, `id`, `last_payment_error`, `livemode`, `next_action`, `object`, `payment_method`, `payment_method_configuration_details`, `payment_method_options`, `payment_method_types`, `processing`, `receipt_email`, `setup_future_usage`, `shipping`, `source`, `status`, `total_details` | probe [recordings]: 1996 successful PI responses |
| SetupIntent | `allowed_payment_method_types`, `automatic_payment_methods`, `cancellation_reason`, `client_secret`, `created`, `description`, `excluded_payment_method_types`, `id`, `last_setup_error`, `livemode`, `next_action`, `object`, `payment_method`, `payment_method_configuration_details`, `payment_method_options`, `payment_method_types`, `status`, `usage` | probe [recordings]: 442 successful SI responses |
| PaymentMethod | [Full object](#paymentmethods) except `metadata` | probe [r-method] |

## PaymentMethod Creation

Route access: [Routes](#routes). Output: [PaymentMethods](#paymentmethods). Refusals: [card creation errors](#card-creation-errors).

| Parameter | Accepted input/default | Evidence |
|---|---|---|
| `type` | Required; simulator creates `card` only; PayPal is fixture-only | rule [method-create]; decision (modelled input) |
| `card` | Hash: required `number`, `exp_month`, `exp_year`, optional `cvc`; or `token` alone | rule [method-create], [spec-2026-10-28.endive] (`anyOf`) |
| Number | Remove spaces; 12–19 digits and valid Luhn | probe [r-method], [r-create-token], [r-token-invalid]; decision (length) |
| Expiry | Integer month 1–12; two-digit year nearest the run clock, at most 49 years ahead/50 behind; four-digit year literal; no past year/month | probe [r-year], [r-token-year]; rule [testing]; decision (window/month attribution) |
| `cvc` | Optional/blank means none; otherwise 3 or 4 digits regardless of brand | rule [testing]; decision (no brand-specific length) |
| `billing_details` | String values at most 5000; `address`, `email`, `name`, `phone` may be empty strings | rule [spec-2026-10-28.endive] |
| `metadata` | At most 50 keys, key length 40, value length 500 | rule [metadata] |
| `allow_redisplay` | `always`, `limited`, `unspecified`; default `unspecified` | rule [method-create] |
| `card[token]` | Published tokens from card-brand/country and [decline tables](#test-data); no Tokens API | decision; rule (test values) [testing] |
| Other parameters | `card[networks]`, `customer`, `payment_method` and unconsumed fields have no effect | decision; [unknown parameters](#what-still-cannot-be-trusted-here) |
| Validation order | Token → required card fields → number → month/year integers → month → year → month against clock → CVC → billing → metadata → redisplay | decision: Stripe publishes no order |

### Card catalogue

| Input | Card facts | Evidence/reason |
|---|---|---|
| Published success cards | Brand/country tables in [Testing][testing], except BCcard, DinaCard, Carnet; exclude co-branded Cartes Bancaires/eftpos | rule; decision: no published single API brand for these rows |
| Issuer-decline cards | [Test Data](#test-data) | rule [testing] |
| `pm_card_jcb`, `tok_jcb` | Japan's `3530111333300000`, the country-bearing JCB row | decision |
| `pm_card_amex`, `pm_card_discover`, `pm_card_diners` | First number listed for that brand | decision |
| Uncatalogued number | Only infer facts the number itself supplies; no invented brand/country/funding | decision; [card attributes](#card-attributes) |

### Inline methods and test-id copies

| Input/effect | Contract | Evidence |
|---|---|---|
| `payment_method_data` | PI create with/without confirm, PI confirm, SI create/confirm; same body parser with prefixed paths; card-error `param` remains bare | rule [pi-create], [pi-confirm], [si-create], [si-confirm]; probe [r-inline-pi], [r-inline-si] |
| Conflicting `payment_method` | Nonblank value with inline data [refuses](#card-creation-errors) | decision: both select one method |
| Confirm parsing order | [Confirmation errors](#confirmation-errors); probe used an already-succeeded SI | probe [r-inline-missing] |
| Minting | Mint/store with intent change on success, decline or pending outcome; no method on validation refusal, lost race or preempt; create stores with its intent | probe [r-decline-pi], [r-decline-si]; decision (atomicity) |
| Attachment | Successful SI with customer attaches; PI leaves a newly created method unattached | rule [si-object], [pi-create] |
| Initial intent state | With inline data, same `requires_confirmation` as a named method | rule [lifecycle] |
| Published test id | Catalogued `pm_card_*` on any PI/SI create/confirm uses a fresh copy per use, with [generated id](#resource-identifiers) | probe [r-attached] |
| Copy fields | Published card hash and [copy expiry](#card-attributes), no customer/billing/metadata | probe [r-attached], [r-decline-pi]; decision (mint timing) |
| Held fixture | Wins over copying, even with a published test id; its own fields are used | decision: fixture declares the object |
| Unheld/uncatalogued id | Refused on confirms and PI inline confirm, except PI accepts `pm_card_`, `pm_usBankAccount_`, `pm_sepaDebit_` stems as unknown methods; non-confirming create stores the id as sent | probe (unknown PM) [r-unknown-pm]; rule (stems) [testing]; decision (permissive fallback) |
| Rule instrument identity | [Test Data](#test-data) | decision |

## Idempotency

| Rule | Simulator contract | Evidence |
|---|---|---|
| Scope | Account-wide across secret and publishable keys, with the same compared parameters | rule [idempotency]: “A key is scoped to an account” |
| Header | POST-only `Idempotency-Key`, at most 255 characters; UUID v4 suggested; GET/DELETE ignore it | rule [idempotency] |
| Cached answer | Original HTTP status/body, success or failure including `500`; expansion and version transformation precede storage; [publishable reduction](#publishable-key) still applies on replay | rule [idempotency]; decision (render order) |
| Replay header | `Idempotent-Replayed: true` | rule [low-level] |
| iOS requests | SDK sends no `Idempotency-Key`; the recorded `idempotency-key` is a response header | probe [r-valid-pi], [r-valid-si]; example [ios-sdk] |
| Lifetime | 24-hour window; a pruned key starts a new request | rule [idempotency] |
| Comparison | Endpoint plus decoded path/value parameters; name order ignored; repeated `[]` equals indexed list; element order remains significant | decision: encoding is not a parameter |
| Pre-execution refusal | Form/version/key/client-secret errors, PI/SI/PM create refusals and a confirm's own parameter errors are not cached | rule [idempotency], [low-level]; decision (boundary) |
| Executed refusal | Confirm refusals about state, unknown/detached/other-customer method or incompatible type are stored; declined state changes also replay | decision; rule (executed failure caching) [idempotency] |
| Lookup before key | Publishable intent missing from this account/run gives its resource error before conflict with an existing key; existing answer remains replayable | decision |
| Wrong parameters / concurrency / long key | [Idempotency errors](#authentication-and-version-errors) | rule/decision there |
| `Stripe-Should-Retry` | Stripe: true/false or absent when unknown; not emitted by simulator | rule [low-level]; decision |

## Where the Outcome Lives

| Outcome/control | Contract | Evidence |
|---|---|---|
| HTTP vocabulary | `200`, `400`, `401`, `402`, `403`, `404`, `409`, `424`, `429`, `500`, `502`, `503`, `504` | rule [errors] |
| Failed-payment category | Failed charge/declined card belongs to `4xx`; 402 means valid parameters but failed request | rule [errors] |
| Payment decline | `402` error and retrievable `requires_payment_method` intent; error lives on the operation | rule [save-reuse] |
| Nonterminal state | Read the body; `requires_action`, `processing` and post-failure `requires_payment_method` can be returned by reads as `200` | rule [lifecycle], [errors] |
| Scenarios | Per operation in [Routes](#routes); saving and charging are independent decisions | decision |
| Instrument fallback | [Test Data](#test-data) | rule/decision there |
| Evidence boundary | Stripe publishes no complete status-per-code table; [error tables](#required-error-shape) classify each status | rule [errors]; inference |

## Minimum API Surface

Common access, identifiers, parameter parsing and retention: [Routes](#routes),
[transport](#authentication-and-transport), [errors](#required-error-shape).

### Customers

| Operation/property | Contract | Evidence |
|---|---|---|
| Object | `id`, `object=customer`, `created`, `email`, `livemode` | rule/example [customer-object]; decision (subset) |
| Create/update | Read only `email`; blank update leaves it unchanged | rule [customer-create], [customer-update]; decision (blank/unread fields) |
| Delete | Permanent; return `{"id": ..., "object": "customer", "deleted": true}`; Stripe also cancels subscriptions, which are unmodelled here | rule/example [customer-delete] |
| Deletion effect | Detach every saved method; deleted customer remains retrievable as the deleted object | rule [customer-delete]; decision (detach interpretation) |
| Deleted update / repeated delete | [Customer errors](#resource-and-method-errors) | rule (no further operations) [customer-delete]; decision (shape) |
| Fixture | `providerIds.customerId` seeds Customer under fixture `merchantId`; [identifier constraints](#resource-identifiers) | decision |
| Native/normalized status | `customer` → `SUCCEEDED`; `customer deleted=true` → `CANCELLED` | decision |

### Ephemeral Keys

| Property | Contract | Evidence |
|---|---|---|
| Published create input | Optional `customer`, `issuing_card`, `nonce`, `verification_session`; “short-lived API key for a given resource” | rule [ephemeral-create] |
| Simulator resource | `customer` only; [missing-resource error](#resource-and-method-errors) | decision |
| Object | `id`, `object=ephemeral_key`, `associated_objects`, `created`, `expires`, `livemode`, `secret`; customer association contains `type=customer`, string `id` | rule/example [ephemeral-object]; [schema extension](#response-conformance) |
| TTL | One hour; example `expires-created=3600`, no prose TTL guarantee | inference [ephemeral-object] |
| Wire credential | Bearer secret; [id/secret shapes](#resource-identifiers), [own-customer routes](#routes); expired/foreign resource or operation [refuses](#authentication-and-version-errors) | example [ephemeral-object], [android-sdk]; decision |
| Published example shapes | `ephkey_` + 24 alphanumerics; `ek_test_` + 82 base64url characters. Example secret encodes account `acct_1M2JTkLkdIwHu7ix` and `2SYuDQF6sHYNYQsGFohUjpWFKPNVh4w`, not its own key id; no normative shape/width stated | example [ephemeral-object] |
| SDK parsing | Android fixtures pair `ephkey_123` with `ek_test_123`; parser treats secret as opaque | example [android-sdk] |
| Version creation | Server SDK requires an explicit version for the mobile client; direct HTTP without it uses [account resolution](#api-versions), since API's missing-header refusal is unpublished | example [java-ephemeral], [node-ephemeral], [android-sdk]; decision |
| Legacy invalid stems | Invalid rows using secret stem for id, or ephemeral-id stem for Customer/PM/SI/Charge, are removed, not renamed; lookups use id/secret without parsing id | decision: a renamed short-lived id was never handed to its client |

### SetupIntents

| Operation/property | Contract | Evidence |
|---|---|---|
| Create input | `customer`, `usage=on_session/off_session` (default `off_session`), `payment_method`, metadata, [selection](#payment-method-selection), [inline data](#inline-methods-and-test-id-copies) | rule [si-create], [si-object] |
| Initial state | With method: `requires_confirmation`; without: `requires_payment_method`; create accepts any otherwise-valid customer id without lookup | rule [lifecycle]; decision (customer lookup) |
| Retrieve | Current object or [resource error](#resource-and-method-errors) | rule [si-retrieve] |
| Confirm | Attach to the named customer and mark `succeeded` atomically; this is the only non-fixture attachment path | rule [si-confirm], [si-object]; decision (attachment surface) |
| Scenario presets | `CARD_ADD_SUCCESS`, `CARD_ADD_FAILED` | decision; [operation scope](#routes) |
| Failed setup | `requires_payment_method`, previous failure in `last_setup_error`; no `failed` native status | rule [si-object] |
| Method/customer checks | [Resource and method errors](#resource-and-method-errors); method of another customer, detached method, deleted/missing customer and unsupported/unknown method are refused before writing | rule [si-object], [customer-delete]; probe [r-unknown-si]; inference (attached method with no customer) |
| Object | `id`, `object`, `allowed_payment_method_types`, `automatic_payment_methods`, `client_secret`, `created`, `customer`, `excluded_payment_method_types`, `last_setup_error`, `livemode`, `payment_method`, `payment_method_configuration_details`, `payment_method_types`, `status`, `usage` | rule [si-object]; decision (subset) |
| PayPal data | Payer fields remain null: setup neither authorizes nor settles | rule [method-object]; decision |
| UI creation | Create alone or create + server-side confirm; a wizard-confirmed PI/SI sends dynamic selection with `allow_redirects=never` | decision; rule (no-return-URL configuration) [selection-change] |
| States and events | [Status Mapping](#status-mapping), [Webhooks](#webhooks) | rule/decision there |

### PaymentIntents

| Operation/property | Contract | Evidence |
|---|---|---|
| Create input | `amount`, `currency`, `capture_method`, `customer`, `receipt_email`, `payment_method`, metadata, selection, `confirm`, `off_session`, inline data | rule [pi-create]; [selection](#payment-method-selection), [creation](#paymentmethod-creation), [validation](#confirmation-errors) |
| Capture methods | `manual`, `automatic`, `automatic_async` (default); automatic forms behave alike | rule [pi-create], [async-default]; decision (same handling) |
| Off-session meaning | Customer is outside checkout and cannot authenticate during the attempt | rule [pi-create] |
| Create/confirm state | No method → `requires_payment_method`; method without confirm → `requires_confirmation`; successful confirm → `requires_capture` for manual, `succeeded` otherwise | rule [lifecycle], [pi-confirm] |
| Customer | Create stores any otherwise-valid id; attached method must match it, including on inline confirm | rule [pi-create], [pi-confirm], [spec-2026-10-28.endive]; decision (no customer lookup at create) |
| Pending | Scenario can put card payment in `processing` and settle after delay; Stripe publishes this state for asynchronous methods, e.g. bank debits | rule [lifecycle]; decision (card scenario) |
| Confirmation limit | Stripe has an unpublished variable upper bound, then cancels; simulator enforces no bound | rule [pi-confirm]; decision |
| Retrieve | Current state; [resource error](#resource-and-method-errors) for unknown id | rule [pi-retrieve] |
| Object | `id`, `object`, `allowed_payment_method_types`, `amount`, `amount_capturable`, `amount_received`, `automatic_payment_methods`, `cancellation_reason`, `capture_method`, `client_secret`, `created`, `currency`, `customer`, `excluded_payment_method_types`, `last_payment_error`, `latest_charge`, `livemode`, `payment_method`, `payment_method_configuration_details`, `payment_method_types`, `receipt_email`, `status` | rule [pi-object]; decision (subset) |
| `latest_charge` | Null until a confirmation attempt; [charge lifecycle](#charges) | rule [pi-object] |
| Additional authentication | [Omissions](#what-still-cannot-be-trusted-here), [explicit authentication-required decline](#issuer-errors) | rule [pi-confirm]; decision |

| Capture/cancel | Contract | Evidence |
|---|---|---|
| Capture | Only `requires_capture`; `amount_to_capture` defaults to full capturable amount and cannot exceed it; returns PI or [capture error](#capture-and-refund-errors) | rule [pi-capture] |
| Final capture | `final_capture=true` default; release remaining authorization and end `succeeded` | rule [multicapture] |
| Non-final capture | `false` keeps remainder authorized; PI stays `requires_capture` until final/default-final capture or expiry | rule [multicapture] |
| Release without refund | Partial final capture and cancellation release uncaptured authorization without creating a Refund | rule [basil-capture] |
| Published partial example | 10 USD authorized, 7 USD non-final capture → `amount_received=700`, `amount_capturable=300` | example [multicapture] |
| Capture count | Up to 50 non-final captures, then one additional final capture | rule [multicapture] |
| Eligibility | Stripe: online card/manual capture, Amex, Visa, Discover, Mastercard, Cartes Bancaires, Diners Club, CUP, JCB and IC+ pricing; simulator permits every manual intent | rule [multicapture]; decision (no eligibility model) |
| Expiry | Stripe default seven days; simulator uses [configured window](#account-settings). At deadline: `canceled`/`CANCELLED`, capturable remainder `0`, `capturesAccepted=false` | rule [pi-capture]; decision |
| Cancel states | `requires_payment_method`, `requires_capture`, `requires_confirmation`, `requires_action`; rare `processing` limited in Stripe to ACH/ACSS/AU BECS/BACS/NZ BECS/SEPA and varying windows; simulator also permits processing cards | rule [pi-cancel]; decision |
| Cancel effect | Release remaining `amount_capturable`; already-cancelled/non-cancellable intents refuse; no further charges or operations on the PI after cancellation | rule [pi-cancel]; [errors](#capture-and-refund-errors) |
| Cancel reason input | `duplicate`, `fraudulent`, `requested_by_customer`, `abandoned` | rule [pi-cancel] |
| Cancel reason output | Also published: `failed_invoice`, `void_invoice`, `automatic`, `expired`; generated internally. Simulator expiry follows [version transformation](#version-changes) | rule [pi-object], [basil-capture] |

### Refunds

| Operation/property | Contract | Evidence |
|---|---|---|
| Target | `payment_intent` or `charge`; both/neither [refuse](#capture-and-refund-errors). Stripe also supports out-of-scope `origin=customer_balance` in place of both | rule [refund-create]; decision (exclusive target) |
| Charge target | Same semantics as refunding that charge's PI; failed charge [refuses](#capture-and-refund-errors) | decision |
| Amount | Positive minor units, at most captured minus refunded; omit for full remainder | rule [refund-create]; inference (omission) |
| Repeated refunds | Partial refunds allowed until full amount; fully refunded/disputed/over-remainder cases [refuse](#capture-and-refund-errors) | rule [refund-create], [codes] |
| During authorization | Refund captured money while PI remains `requires_capture`; capturable amount unaffected | rule [multicapture]; decision (unchanged authorization) |
| Reason | Input `duplicate`, `fraudulent`, `requested_by_customer`; published object additionally allows `expired_uncaptured_charge` | rule [refund-create], [refund-object] |
| Object | `id`, `object=refund`, `amount`, `currency`, nullable/expandable `payment_intent` and `charge`, `reason`, `status`, `created` | rule/example [refund-object]; decision (subset) |
| Status enum | `pending`, `requires_action`, `succeeded`, `failed`, `canceled` | rule [refund-object] |
| Simulator status | Account's [refundStatus](#account-settings), fixed after creation; pending/action holds money against further refunds, failed/canceled does not; refund qualifier counts settled success only | decision: no published rule for when `amount_refunded` moves |
| Published optional details | `next_action` describes required action; `pending_reason`: `processing`, `insufficient_funds`, `charge_pending`; `failure_reason`: `lost_or_stolen_card`, `expired_or_canceled_card`, `charge_for_pending_refund_disputed`, `insufficient_funds`, `declined`, `merchant_request`, `unknown` | rule [refund-object]; [omitted here](#what-still-cannot-be-trusted-here) |

### Charges

| Property/effect | Contract | Evidence |
|---|---|---|
| Retrieve | Charge named by PI's latest-charge reference; [resource error](#resource-and-method-errors) if unknown | rule [charge-object]; decision |
| Object | `id`, `object`, `amount`, `amount_captured`, `amount_refunded`, `captured`, `currency`, `customer` (PI's), `outcome`, `paid`, `payment_intent`, `payment_method`, `refunded`, `status` | rule [charge-object]; decision (subset) |
| Minting | One charge per inline or separate confirmation attempt, success or decline; none on unconfirmed create, capture or cancel; latest attempt wins | decision |
| Status | Stripe enum `succeeded`, `pending`, `failed`; simulator fixes success/failure at mint and never reaches pending | rule [charge-object]; decision |
| `paid` | Succeeded, including an authorized uncaptured or released hold | rule [charge-object] |
| `captured` | Authorization closed and positive captured amount; a never-captured cancelled hold stays false | rule [charge-object], [multicapture]; decision (zero captured cancellation is unspecified) |
| `refunded` | Authorization closed, positive capture, settled refund sum equals captured sum; partial refund stays false | rule [charge-object], [multicapture]; decision |
| Amounts | Capture sum and settled refund sum; both zero on failed charge | decision |
| Decline outcome | [Issuer errors](#issuer-errors) | decision/probe there |
| Legacy references | PI confirmed without a stored charge retains `latest_charge=null`; latest recoverable old failed charge gets its intent's reason; unrecoverable older failures keep null | decision |
| Control/reset | Charge is not a control-read object and not counted by reset | decision |

### PaymentMethods

Creation and copies: [PaymentMethod Creation](#paymentmethod-creation). Keys: [Routes](#routes).

| Property/operation | Contract | Evidence |
|---|---|---|
| Object | `id`, `object=payment_method`, `allow_redisplay`, `billing_details`, `created`, `customer`, `livemode=false`, `metadata`, `type`, type-specific hash | rule [method-object]; decision (subset); [publishable allowlist](#publishable-key) |
| Billing object | `address` (`city`, `country`, `line1`, `line2`, `postal_code`, `state`), `email`, `name`, `phone`, `tax_id` | rule [method-object] |
| Unattached customer | `customer=null` | rule [method-object] |
| Account list | Optional `customer`/`type`; unknown filter gives empty list. Stripe excludes `custom` when no type is supplied | rule [method-list]; decision (unknown filter) |
| Customer list | Given customer only; unknown customer [refuses](#resource-and-method-errors) | rule [method-customer-list]; decision (error) |
| Lists | `object=list`, `url`, `data[]`, newest first, `has_more=false`, no paging/`limit` | example [method-list]; decision (ordering/paging) |
| Fixture types | `card` → `card`, `wallet` → `paypal`; `psp-token` refused | decision: no Tokens API |
| Fixture requirements | Required `providerIds.paymentMethodId`; [account/id constraints](#resource-identifiers) | decision |
| Fixture card facts | Own brand/last4/expiry/funding; empty billing/metadata; country/display brand/regulated status/3DS usage null; only network is its brand | decision |
| PayPal hash | `payer_email`, `payer_id`, `country`, all null and unsettable here; Stripe supplies payer email/country at authorization/settlement and publishes PayerID as its customer identifier | rule [method-object]; decision (no payer flow) |
| Detach | Return method with null customer; permanent: cannot pay or reattach; [repeat/never-attached errors](#resource-and-method-errors) | rule/example [method-detach]; decision (error) |
| Fixture removal/reset | Removing fixture removes method, keeps customer; run reset removes API-created methods | decision |
| Legacy creation fields | Stored PM without creation attributes retains brand as its sole network and empty metadata | decision |

#### Card attributes

| Member | Created/copied card | Evidence |
|---|---|---|
| `brand` | Testing catalogue's Visa/Mastercard/Amex/Discover/Diners/JCB/UnionPay value; `unknown` if absent | rule [testing], [method-object] |
| `funding` | Listed debit/prepaid/credit; credit for the recorded Visa numbers below; unknown otherwise | rule [testing]; probe [r-method], [r-decline-pi], [r-checkout-decline] |
| `country` | Testing country row; US for the recorded Visa numbers; null otherwise | rule [testing]; probe [r-method], [r-decline-pi], [r-checkout-decline] |
| `display_brand` | `american_express`, `diners_club`, `union_pay`, otherwise brand; null for unknown | inference from published values [method-object] |
| `networks` | `{available: [brand], preferred: null}`; null for unknown | probe (Visa) [r-method]; inference (others) |
| `regulated_status`, `three_d_secure_usage` | `unregulated`, `{supported: true}` for recorded Visa; null otherwise | probe [r-method]; rule (nullable) [method-object] |
| `last4` | Last four digits | rule [method-object] |
| Expiry | Raw card: supplied month/year; copy/token: run month and following year | probe (copy) [r-attached]; decision (token) |
| `checks` | `address_line1_check`, `address_postal_code_check`, `cvc_check` all null | probe [r-method] |
| `generated_from`, `wallet` | Null | probe [r-method] |
| `fingerprint` | Absent | probe (publishable) [r-method]; decision (secret): no full number retained |

The recorded Visa facts apply to `4242424242424242`, `4000000000009995`, `4000000000000002` (**probe**,
[r-method], [r-new-decline], [r-checkout-decline]). Token/id `6/2026` for a June 2025 request and `2/2027` for February
2026 illustrate the copy expiry rule (**probe**, [r-attached], [r-copy-expiry]).

## Status Mapping

Native enums and normalized state; **rule** for native vocabulary [lifecycle], [si-object], **decision** for mapping.

| PaymentIntent native state | Normalized | Emission/boundary |
|---|---|---|
| `requires_payment_method`, `requires_confirmation` | `NOT_INITIALIZED` | Failed attempt differs by `last_payment_error`; decline carried on operation |
| `requires_action` | `NOT_INITIALIZED` | Published but never reached here; common vocabulary has no awaiting-authentication state |
| Legacy `requires_source`, `requires_source_action` | `NOT_INITIALIZED` | Used before `2019-02-11`, never emitted here |
| `requires_capture` | `READY_FOR_CAPTURE` | Before captures |
| `requires_capture` with non-final captures and capturable remainder | `PARTIALLY_CAPTURED` | Derived from capture operations; while authorization is open wins over refund qualifier |
| `processing` | `PROCESSING` | [Scenario pending](#paymentintents) |
| `succeeded` | `SUCCEEDED` | Default-final partial capture forfeits remaining authorization |
| `canceled` | `CANCELLED` | Stripe also uses it after confirmation limit failure |
| Unrecognised | `UNKNOWN` | — |

Refund state derives from refund objects, not PI native status (**decision**):

| Refund | Payment qualifier | Refund normalized |
|---|---|---|
| `succeeded`, part of capture | `PARTIALLY_REFUNDED` | `SUCCEEDED` |
| `succeeded`, whole capture | `REFUNDED` | `SUCCEEDED` |
| `pending` / `requires_action` | Unchanged | `PROCESSING` |
| `failed` / `canceled` | Settled-refund qualifier | `FAILED` / `CANCELLED` |

| SetupIntent native state | Normalized | Emission |
|---|---|---|
| `requires_payment_method` before confirm, `requires_confirmation` | `NOT_INITIALIZED` | yes |
| `requires_action` | `NOT_INITIALIZED` | published, not reached |
| `processing` | `PROCESSING` | published, not reached |
| `succeeded` | `SUCCEEDED` | yes |
| `requires_payment_method` with `last_setup_error` | `FAILED` | yes |
| `canceled` | `CANCELLED` | published, not reached |
| Unrecognised | `UNKNOWN` | — |

After an after-effect fault, GET is authoritative. Conflicting webhook/GET while converging is unreconciled, not
`UNKNOWN` (**decision**); Stripe calls a `500` indeterminate (**rule**, [low-level]).

## Webhooks

| Transport/signing | Contract | Evidence |
|---|---|---|
| Request | POST JSON to merchant URL; `Content-Type: application/json` | rule (JSON POST) [webhooks]; inference (content type) |
| Acknowledgement | Quick `2xx`; redirects fail | rule [webhooks] |
| Signature | `Stripe-Signature` when signing secret configured; timestamp `t=`, scheme `v1=` | rule [webhooks] |
| MAC | HMAC-SHA256 of `timestamp + "." + actual JSON body`; ignore non-v1 schemes | rule [webhooks] |
| Published schemes/sample | Live v1; test may add fake v0; `t=1492774577,v1=5257a869...,v0=6ffbb59b...` on one line | rule/example [webhooks] |
| Secret rotation | Multiple v1 signatures; previous secret retained up to 24 hours in Stripe; simulator signs with configured secret | rule [webhooks]; decision |
| Receiver tolerance | SDK default five minutes; configurable; Stripe says not to use zero. Simulator enforces none and can send stale timestamp/corrupted signature | rule [webhooks]; decision |
| Attempts | Fresh timestamp/signature each attempt; identical timestamps can produce identical signatures | rule [webhooks] |
| Body/withholding | [Stored signed bytes and history](#withholding-and-write-effects) | decision |
| Missing account | Do not send unsigned; own-target attempt becomes `UNDELIVERABLE`; screen retry to another target refuses without effects | decision |
| Retries | Stripe live: up to three days exponential backoff; sandbox: three retries across a few hours; simulator schedule configurable | rule [webhooks]; decision |
| Ordering | No guaranteed event order; `created` is seconds, distinct events can share it | rule [webhooks] |
| Deduplication | Same Event may repeat: use event id; distinct Event objects for one notification: use `data.object` id + event type, not `created` | rule [webhooks] |
| Controls | Duplicate event id, delayed delivery, corrupt signature, stale timestamp, convergence with GET unless scenario overrides | decision |

| Event envelope | Simulator value | Evidence |
|---|---|---|
| `id`, `object`, `type` | Event id, `event`, type below | rule [event-object] |
| `api_version` | [Scheduling-time account default](#api-versions) | rule/decision there |
| `created` | Event timestamp in seconds | rule [event-object] |
| `livemode`, `pending_webhooks` | `false`, `0` | decision |
| `request` | `{"id": null, "idempotency_key": null}` | rule/example [event-object]; decision |
| `data.object` | API's PI or Refund shape, subject to [withholding](#withholding-and-write-effects) and [version changes](#version-changes) | rule [event-object]; decision |
| `account`, `context` | Published nullable attributes, omitted here; published `api_version`/`request` also nullable | rule [event-object]; decision (omission) |
| Scheduling | Control API, or pending outcome; only PI and Refund subjects; PI event must match attained state | decision |

| Event | Stripe meaning | Simulator | Evidence |
|---|---|---|---|
| `payment_intent.amount_capturable_updated` | Funds capturable; Stripe sends on every capture regardless of amount/final flag | Only while `requires_capture`; closing capture reports succeeded alone | rule [event-types], [multicapture]; decision (state check) |
| `payment_intent.succeeded` | Payment completed | yes | rule [event-types] |
| `payment_intent.payment_failed` | Failed attempt to create payment method/payment | yes | rule [event-types] |
| `payment_intent.canceled` | Intent cancelled | yes | rule [event-types] |
| `refund.created` | Refund created | yes | rule [event-types] |
| Unsupported event names/subjects | [Omissions](#what-still-cannot-be-trusted-here) | Refuse by name | decision |

## Required Error Shape

| Element | Contract | Evidence |
|---|---|---|
| Envelope/type | `{"error": {...}}`; `type` is required/non-null: `api_error`, `card_error`, `idempotency_error`, `invalid_request_error` | rule [errors] |
| Published attributes | `advice_code`, `charge`, `code`, `decline_code`, `doc_url`, `message`, `network_advice_code`, `network_decline_code`, `param`, `payment_intent`, `payment_method`, `payment_method_type`, `request_log_url`, `setup_intent`, `source`, `type`; only `type` non-nullable | rule [errors] |
| Message | Nullable; card messages may be shown to users | rule [errors] |
| Documentation link | With code, `https://docs.stripe.com/error-codes#` + code with underscores replaced by dashes (all 180 published anchors); without code, no `doc_url` | rule [codes]; decision (same format at every version) |
| Recorded link format | `2020-08-27` traffic used `https://stripe.com/docs/error-codes/{code-with-dashes}`; version-dependence unpublished | probe [recordings]; decision (current anchored form) |
| Embedded PI | Full simulator PI, unchanged for a parameter/state refusal | rule [errors]; decision (subset) |
| Embedded SI | Full object on decline; parameter/state refusal: `id`, `object`, `customer`, `last_setup_error`, `payment_method`, `status` | probe [r-decline-si], [r-unknown-si]; decision |
| Declined method | Created/retrievable PM in `error.payment_method` and last error's `payment_method`; intent's own `payment_method=null`, status `requires_payment_method`; retry names a method again; an attached method stays attached | probe [r-decline-pi], [r-decline-si], [r-checkout-decline]; decision (retry) |
| Reduction/expansion | [Publishable view](#publishable-key), [expansion policy](#expansion), [withholding](#withholding-and-write-effects) | probe/decision there |
| Missing parameter | `param={name}`, “Missing required param: {name}.”; includes refund/ephemeral targets | probe [r-inline-missing]; inference (other missing fields) |
| Vocabulary | Error code, error type and decline code are separate; `card_error` covers payment failures of any method, despite historical CardException naming | rule [errors], [error-handling] |

### Authentication and version errors

`—` means omitted. Status/evidence applies to this simulator; source qualifications are explicit.

| Condition | HTTP | Type | Code | Param | Message/header | Evidence |
|---|---|---|---|---|---|---|
| No API key | 401 | `invalid_request_error` | — | — | `WWW-Authenticate: Basic realm="Stripe"`; “You did not provide an API key. You need to provide your API key in the Authorization header, using Bearer auth (e.g. 'Authorization: Bearer YOUR_SECRET_KEY'). See https://stripe.com/docs/api#authentication for details, or we can help at https://support.stripe.com/.” | rule [auth]; probe: unauthenticated POST [no-key], 2026-09-28 |
| Unknown key / unconfigured run | 401 | `invalid_request_error` | — | — | “No valid API key provided.” | rule (status) [errors]; decision (message) |
| Forbidden publishable route | 403 | `invalid_request_error` | `secret_key_required` | — | “The API key provided is a publishable key, but a secret key is required.” [Version header](#api-versions); [pre-execution refusal](#idempotency); judged before other parameters | rule (code/text) [codes]; probe (403) [pk-report]; decision (type/order) |
| Ephemeral key expired or outside own resource/operations | 401 | `invalid_request_error` | — | — | Unpublished shape | decision; [scope](#routes) |
| Published but unsupported version | 400 | `invalid_request_error` | — | — | “Stripe-Version {sent} is a published Stripe API version this simulator does not answer. It answers 2025-03-31.basil through 2026-10-28.endive, …” | decision: do not claim unsupported behaviour |
| Published version with beta suffix | 400 | `invalid_request_error` | — | — | “… asks for a beta, and this simulator answers no beta” | decision |
| Real calendar `{date}.{codename}` after `2026-10-28` | 400 | `invalid_request_error` | — | — | “Stripe-Version {sent} is dated after 2026-10-28.endive, the newest Stripe API version this simulator was built with, so it may be a release Stripe published since. …” | decision: distinguish future from invalid |
| Other version, including `2026-08-26.DAHLIA` | 400 | `invalid_request_error` | — | — | “Invalid Stripe API version: {sent}” | decision; reported text [invalid-version-report]; no published vendor refusal |
| Idempotency key over bound | 400 | `invalid_request_error` | — | — | Bound in [Idempotency](#idempotency) | rule (bound) [idempotency]; decision (shape) |
| Reused key, different endpoint/parameters | 400 | `idempotency_error` | — | — | Reuse conflict | rule [idempotency]; decision (status) |
| Concurrent key | 409 | `idempotency_error` | `idempotency_key_in_use` | — | “The idempotency key provided is currently being used in another request. This occurs if your integration is making duplicate requests simultaneously.” Retryable | rule [idempotency], [low-level] |
| Unknown address/method | 404 | `invalid_request_error` | — | — | Simulator's own sentence names the simulator; `405` absent from published statuses | decision; rule (status list) [errors] |

### Client-secret errors

| Condition, in validation order | HTTP | Code | Param | Message | Evidence |
|---|---|---|---|---|---|
| Absent, blank, hash or list | 400 | `parameter_missing` | `client_secret` | [Missing-parameter template](#required-error-shape) | rule (required) [pi-confirm], [si-confirm]; probe (template) [r-inline-missing]; decision (application) |
| Unknown/other-account/other-run intent | 404 | `resource_missing` | `intent` | [Object-specific template](#resource-and-method-errors) | probe [r-mismatched], [r-bad-si]; decision (unknown/run equivalence) |
| Any non-matching PI secret, malformed included | 400 | `payment_intent_invalid_parameter` | `client_secret` | “The client_secret provided does not match the client_secret associated with the PaymentIntent.” | probe [r-wrong-secret]; decision (malformed comparison) |
| Any non-matching SI secret | 400 | `setup_intent_invalid_parameter` | `client_secret` | Same sentence with `SetupIntent` | decision: no corresponding recorded refusal |

All rows use `invalid_request_error`; refusal order/effects are in [Publishable Key](#publishable-key).

### Parameter and expansion errors

| Condition | HTTP/type | Code | Param | Message/rule | Evidence |
|---|---|---|---|---|---|
| Malformed name: `a[b`, `a]`, `]a`, `[a]`, `a[[b]`, empty name | 400 / `invalid_request_error` | — | Unreadable path; none for empty name | Reject whole form | decision |
| Conflicting shapes `x=1&x[k]=2`, `x[]=1&x[0]=2`; repeated scalar `x=1&x=2`, `x[k]=1&x[k]=2` | 400 / `invalid_request_error` | — | Unreadable path | Reject whole form | decision |
| List is nonempty scalar, gapped indexes, `01` index, non-index key; `a[][b]` | 400 / `invalid_request_error` | — | List name/path | Accepted-shape explanation | decision |
| Shape/limit violation, including automatic hash/boolean, empty/long configuration, billing/metadata bounds | 400 / `invalid_request_error` | — | Field path | Accepted-shape explanation; [input](#paymentmethod-creation), [selection](#payment-method-selection) | rule (shapes/bounds) [spec-2026-10-28.endive]; decision (shape) |
| Resource identifier above model bound | 400 / `invalid_request_error` | — | Supplied parameter | Accepts “an identifier of at most 128 characters” | decision; [bound](#resource-identifiers) |
| NUL in persisted SQL text | 400 / `invalid_request_error` | — | Supplied parameter | Accepts “text without a NUL character” | decision |
| `expand` references nonexistent property | 400 / `invalid_request_error` | — | — | “This property cannot be expanded because it doesn't exist: {path}” | probe [expand-report]; decision (status/type/param) |
| List expansion missing `data` | 400 / `invalid_request_error` | — | — | “This property cannot be expanded ({path}). You may want to try expanding '{suggestion}' instead.” Insert `data` at the list: `refunds.charge` → `refunds.data.charge` | probe [python-expand-report], [node-expand-report]; decision (status/type/param) |
| Object expands nothing | 400 / `invalid_request_error` | — | — | “This property cannot be expanded ({path}).” | probe [event-expand-report] (Events); decision (ephemeral key/status/type/param) |
| More than four properties | 400 / `invalid_request_error` | — | — | “This property cannot be expanded ({path}). Expansions have a maximum depth of four levels.” | rule (depth) [expand]; decision (unpublished refusal) |

Paths in messages end at the refused property (**decision**). Reports [event-expand-report] include the list-style
`customer` suggestion and refusal of `data.object` for `data.object.customer`; Events themselves do not support expand.

### Selection errors

All rows: HTTP `400`, type `invalid_request_error`.

| Condition | Code | Param | Message | Evidence |
|---|---|---|---|---|
| [Exclusive selectors](#payment-method-selection) | `parameters_exclusive` | First named | “You may only specify one of these parameters: {param}, {other}.” | rule (exclusion/code) [allowed-selection], [codes]; decision (text/param) |
| Endive explicit-list write on affected operation, any shape | `payment_method_types_no_longer_supported` | `payment_method_types` | Read-only computed list; use allowed list or dynamic selection/exclusions | rule (400/code) [endive-selection]; decision (param/text); code has no error-page entry |
| Unsupported explicit-list type | — | `payment_method_types` | “The payment method type `{type}` is not supported when creating a PaymentIntent.” Also “confirming a PaymentIntent” / “creating a SetupIntent” | rule (refusal) [allowed-selection]; probe (shape) [r-invalid-type] |
| Unpublished allowed/excluded enum | — | `{name}[{index}]` | “Invalid {name}[{index}]: must be one of {published values}” | probe (enum shape) [r-invalid-network]; decision (indexed param) |
| Invalid `allow_redirects` enum | — | `automatic_payment_methods[allow_redirects]` | Same enum template | probe (shape) [r-invalid-network]; decision |
| Missing automatic `enabled` | `parameter_missing` | `automatic_payment_methods[enabled]` | [Missing-parameter template](#required-error-shape) | rule (required) [spec-2026-10-28.endive]; decision (answer) |
| Empty computed set | `payment_intent_invalid_parameter` / `setup_intent_invalid_parameter` | Parameter that emptied it | Selection leaves no usable type | decision: unconfirmable intent |
| Held type not offered, PI confirm/inline confirm | `payment_intent_incompatible_payment_method` | `payment_method` | “The PaymentMethod provided ({type}) is not allowed for this PaymentIntent. Attach a PaymentMethod of one of the following types: {offered}.” Confirm embeds stored PI; create mints none | decision; rule (code meaning) [codes] |
| Held type not offered, SI confirm | `setup_intent_invalid_parameter` | `payment_method` | Same sentence with `SetupIntent`; truncated SI; no attachment | decision; rule (code meaning) [codes] |
| Request changes selection to an incompatible set | Same PI/SI code | `payment_method` | “The PaymentMethod provided ({type}) is not allowed by the payment method types this request selects: {selected}. This PaymentIntent keeps the payment method types it had.” Or `This SetupIntent` | decision: message matches unchanged embedded object |

Code definitions: PI incompatible means “expected a payment method with different properties”; PI/SI invalid parameter
means “One or more provided parameters wasn't allowed for the given operation” (**rule**, [codes]).

### Card creation errors

| Condition | HTTP/type | Code | Param | Message | Evidence |
|---|---|---|---|---|---|
| Missing type/card/required card member | 400 / `invalid_request_error` | `parameter_missing` | Full path, including inline prefix | [Missing-parameter template](#required-error-shape) | rule (required) [method-create]; probe [r-inline-missing] |
| Unmodelled `type` | 400 / `invalid_request_error` | — | `type` | Simulator creates only card PaymentMethods | decision |
| Invalid redisplay | 400 / `invalid_request_error` | — | `allow_redisplay` | “Invalid allow_redisplay: must be one of always, limited, or unspecified” | probe (enum shape) [r-invalid-network]; decision (application) |
| Token beside number/month/year/CVC | 400 / `invalid_request_error` | `parameters_exclusive` | `card[token]` | [Exclusive-parameter template](#selection-errors) | rule (union) [spec-2026-10-28.endive]; decision (answer) |
| Inline data beside named method | 400 / `invalid_request_error` | `parameters_exclusive` | `payment_method` | [Exclusive-parameter template](#selection-errors) | decision |
| Unknown token | 404 / `invalid_request_error` | `resource_missing` | `card[token]` | “No such token: '{token}'”; invalid token-id shape is not echoed | decision |
| Invalid number shape | 402 / `card_error` | `invalid_number` | `number` | “Your card number is invalid.” | rule (code) [codes]; decision (status/text/routes) |
| Invalid Luhn | 402 / `card_error` | `incorrect_number` | `number` | “Your card number is incorrect.” | probe on Tokens [r-token-invalid]; decision (PM/inline routes) |
| Non-integer month/year | 400 / `invalid_request_error` | `parameter_invalid_integer` | Full path | “Invalid integer: {value}” | rule (code: requires integer) [codes]; decision (text) |
| Invalid/past month | 402 / `card_error` | `invalid_expiry_month` | `exp_month` | “Your card's expiration month is invalid.” | rule (code) [codes], [testing]; decision (text/status/month attribution) |
| Invalid/past year | 402 / `card_error` | `invalid_expiry_year` | `exp_year` | “Your card's expiration year is invalid.” | probe on Tokens [r-token-year]; decision (PM/inline routes) |
| Invalid CVC shape | 402 / `card_error` | `invalid_cvc` | `cvc` | “Your card's security code is invalid.” | probe on Tokens [r-token-cvc]; decision (PM/inline routes) |

Published descriptions differ from some emitted sentences: `invalid_number` “The card number is invalid”;
`invalid_expiry_month` “The card's expiration month is incorrect” (**rule**, [codes]). Two-digit years `32`, `42`, `50`
were returned as `2032`, `2042`, `2050`; `20` failed in 2025, and Tokens rejected 2013 (**probe**, [r-year], [r-token-year], [r-pk-error]).
Testing also specifies rejection of an expiry up to 50 years in the past, e.g. `95` (**rule**, [testing]).

### Confirmation errors

| Order/condition | HTTP/type | Code | Param | Message/effect | Evidence |
|---|---|---|---|---|---|
| 1. Unknown intent | 404 / `invalid_request_error` | `resource_missing` | `intent` | [Resource template](#resource-and-method-errors) | probe [r-mismatched], [r-bad-si] |
| 2. Inline-data parameters | [Card creation errors](#card-creation-errors) | — | — | Read before terminal-state check | probe [r-inline-missing] |
| 3. Non-confirmable status | 400 / `invalid_request_error` | `payment_intent_unexpected_state` / `setup_intent_unexpected_state` | — | “The PaymentIntent's state was incompatible with the operation you were trying to perform.” Or `SetupIntent` | rule (code text) [codes]; inference (status); decision (order) |
| 4. Selection parameters | [Selection errors](#selection-errors) | — | — | Unknown intent wins over endive type-list write; succeeded intent gets unexpected state | decision |
| 5. Dynamic selection needs return URL | 400 / `invalid_request_error` | — | `return_url` | “A `return_url` must be specified because this Payment Intent is configured to automatically accept the payment methods enabled in the Dashboard, some of which may require a full page redirect to succeed. If you do not want to accept redirect-based payment methods, set `automatic_payment_methods[enabled]` to `true` and `automatic_payment_methods[allow_redirects]` to `never` when creating Setup Intents and Payment Intents.” SI uses `Setup Intent` | probe (reported PI sentence only) [return-report], [return-report-2]; decision (status/type/code/param/SI wording/configuration test) |
| 6. Method resolution/ownership | [Method errors](#resource-and-method-errors) | — | — | Stored intent unchanged | decision |
| PI create has `off_session`/`return_url` without `confirm=true`, even `off_session=false` | 400 / `invalid_request_error` | — | Parameter name | “The parameter `{name}` can only be used with `confirm=true`.” | rule (restriction) [pi-create]; decision (answer) |
| Invalid PI `off_session` | 400 / `invalid_request_error` | — | `off_session` | “Invalid off_session: must be one of true, false, one_off, recurring” | rule (values) [pi-confirm]; decision (answer) |
| Valid PI `off_session` | — | — | — | `true`, `one_off`, `recurring` waive URL; false/absent retain requirement | rule (true) [selection-change]; inference (one_off/recurring) |
| PI confirm missing method | 400 / `invalid_request_error` | `payment_intent_unexpected_state` | — | “You cannot confirm this PaymentIntent because it's missing a payment method. You can either update the PaymentIntent with a payment method and then confirm it again, or confirm it again directly with a payment method or ConfirmationToken.” Embed unchanged PI | probe [r-missing-pm] |
| PI inline create-confirm / SI confirm missing method | 400 / `invalid_request_error` | `parameter_missing` | `payment_method` | [Missing-parameter template](#required-error-shape) | decision: no created PI to embed; no recorded SI missing-method answer |

### Resource and method errors

Unknown resources use `resource_missing`, `invalid_request_error`; same template for every permitted key kind. The published code covers missing/invalid ids and ids of the wrong resource kind (**rule**, [codes]).

| Resource | HTTP | Param | Message | Evidence |
|---|---|---|---|---|
| PI | 404 | `intent`; `payment_intent` for refund | “No such payment_intent: '{id}'” | probe [r-mismatched]; decision (refund param) |
| SI | 404 | `intent` | “No such setupintent: '{id}'” | probe [r-bad-si] |
| PM confirm / PI inline confirm | 400 | `payment_method` | “No such PaymentMethod: '{id}', It's possible this PaymentMethod exists on one of your connected accounts, in which case you should retry this request on that connected account. Learn more at https://stripe.com/docs/connect/authentication” | probe [r-unknown-pm], [r-unknown-si] |
| PM lookup/detach | 404 | `payment_method` | Same PM template | inference from lookup status; decision (unrecorded routes) |
| Customer | 404 | `customer` | “No such customer: '{id}'” | probe [customer-error-report]; inference (other customer routes) |
| Charge | 404 | `charge` | “No such charge: '{id}'” | decision: same per-object form, no charge-error recording |

Echoed ids follow [withholding](#withholding-and-write-effects).

| Existing-resource refusal | HTTP/type | Code | Param | Message/effect | Evidence |
|---|---|---|---|---|---|
| Deleted customer update/delete/attachment | 400 / `invalid_request_error` | — | — | No further operation; no new write | rule [customer-delete]; decision (shape) |
| Detached method on confirm/inline confirm | 400 / `invalid_request_error` | — | `payment_method` | Permanent detachment; no payment/reattachment | rule [method-detach]; decision (answer) |
| Repeated detach / never-attached method | 400 / `invalid_request_error` | — | `payment_method` | No remaining or prior attachment to detach | rule (permanence) [method-detach]; decision (refusal) |
| PI method belongs to another customer, or attached method but no PI customer | 400 / `invalid_request_error` | — | `payment_method` | “Payment methods attached to other Customers cannot be used with this PaymentIntent.” Include published same-customer requirement and reason; no charge | rule [pi-create], [pi-confirm], [spec-2026-10-28.endive]; decision (shape) |
| SI method belongs to another customer | 400 / `invalid_request_error` | — | `payment_method` | “Payment methods attached to other Customers cannot be used with this SetupIntent.” | rule [si-object]; decision (shape) |
| Attached method but no SI customer | 400 / `invalid_request_error` | — | `payment_method` | Same SI sentence and reason | inference from SI/PI customer requirements [si-object], [pi-create] |

### Capture and refund errors

All failures use `invalid_request_error`, HTTP `400` unless specified.

| Condition | Code | Param | Message/rule | Evidence |
|---|---|---|---|---|
| Capture already closed/cancelled | `payment_intent_unexpected_state` | — | Unless idempotent replay | decision; inference (status) [codes] |
| Expired capture window | `charge_expired_for_capture` | — | “The charge can't be captured because the authorization has expired.” | rule [pi-capture], [codes]; inference (status) |
| Capture above capturable | `amount_too_large` | `amount_to_capture` | Above authorized remainder | decision (code); inference (status) [codes] |
| 51st non-final capture | — | `final_capture` | Non-final capture limit exceeded | rule (limit) [multicapture]; decision (shape) |
| Refund both targets | — | `charge` | Specify one target | decision |
| Refund neither target | `parameter_missing` | `payment_intent` | [Missing-parameter template](#required-error-shape) | rule (target) [refund-create]; decision (answer) |
| Unknown refund target | [Resource errors](#resource-and-method-errors) | — | HTTP 404 | decision/inference there |
| Failed Charge refund | — | `charge` | Failed charge holds no funds | decision |
| Nothing captured | `payment_intent_unexpected_state` | — | No captured funds | decision |
| Fully refunded or above remaining refund | `charge_already_refunded` | — | No more refundable amount | rule (full refund) [refund-create]; decision (over-remainder code) |
| Non-positive/non-integer refund amount | `invalid_charge_amount` | `amount` | “The specified amount is invalid. The charge amount must be a positive integer in the smallest currency unit” | rule [codes]; inference (status) |
| Unknown cancellation/refund reason | — | Reason field | No published code; accepted enums in [PI](#paymentintents)/[Refund](#refunds) | decision |

Published `charge_already_captured`, `amount_too_small`, `amount_too_large` use `invalid_request_error`/400 by
**inference** from [errors]/[codes]; no maximum amount is published for the last. Disputed refunds publish
`refund_disputed_payment` (**rule**, [refund-create]), but disputes are [outside this model](#what-still-cannot-be-trusted-here).

### Issuer errors

All emitted rows are `402`, `card_error`; `doc_url` follows the [common rule](#required-error-shape).

| Outcome | Code | Decline code | Param | Message | Evidence |
|---|---|---|---|---|---|
| Generic decline, lost/stolen/velocity | `card_declined` | [Card-specific mapping](#test-data) | — | “Your card was declined.” | rule (status/type) [save-reuse]; probe (generic text) [r-decline-pi]; decision (same text for lost/stolen/velocity) |
| Insufficient funds | `card_declined` | `insufficient_funds` | — | “Your card has insufficient funds.” | probe [r-decline-pi], [r-decline-si] |
| Expired | `expired_card` | — | — | “Your card has expired.” | rule (code/status/type) [codes]; decision (text) |
| Incorrect CVC | `incorrect_cvc` | — | `cvc` | “Your card's security code is incorrect.” | example (param) [declines]; rule (code/status/type) [incorrect-cvc]; decision (text) |
| Processing failure | `processing_error` | — | — | “An error occurred while processing your card. Try again in a little bit.” | rule [codes]; decision (text) |
| Explicit authentication-required scenario | `authentication_required` | `authentication_not_handled` | — | “This payment required an authentication action to complete, but `error_on_requires_action` was set.” Full PI error has `requires_payment_method`, matching last error | example [without-auth]; rule (code) [authentication-required]; rule (status) [save-reuse] |

| Distinction | Contract | Evidence |
|---|---|---|
| Code descriptions | `card_declined`: declined card includes decline reason; `authentication_required`: customer must return to authenticate | rule [codes] |
| Insufficient funds | Both an error-code name and decline-code name; card uses the pairing above | rule [codes], [declines] |
| Authentication required | Both an error-code name and a decline-code name; emitted pairing above is separate | rule [codes], [declines]; example [without-auth] |
| Currency | `currency_not_supported` is a decline code, not an unsupported-currency error code; `payment_method_currency_mismatch` is the error for mismatched method currency; neither is a scenario outcome here | rule [codes], [declines]; decision |
| Retry advice | Published next steps distinguish retryable `issuer_not_available` from final `lost_card`; simulator leaves `retryWithAnotherSource` unpopulated | rule [declines]; decision |
| Failed charge | `status=failed`, `paid=false`, issuer-declined `outcome`; preserve each charge's failure reason across retries. Network status, risk level, seller message are representative, not established by a Charge-retrieve recording | decision |

## Test Data

Card mappings: **rule** [testing-declines], [declines]; alias/setup qualifications are in the rules table below.

| Card number | Suffix after `pm_card_` | `code` | `decline_code` | Failed operation |
|---|---|---|---|---|
| `4000000000000002` | `visa_chargeDeclined`, `chargeDeclined` | `card_declined` | `generic_decline` | PI confirmation and SI confirmation |
| `4000000000009995` | `visa_chargeDeclinedInsufficientFunds`, `chargeDeclinedInsufficientFunds` | `card_declined` | `insufficient_funds` | PI confirmation and SI confirmation |
| `4000000000009987` | `visa_chargeDeclinedLostCard`, `chargeDeclinedLostCard` | `card_declined` | `lost_card` | PI confirmation and SI confirmation |
| `4000000000009979` | `visa_chargeDeclinedStolenCard`, `chargeDeclinedStolenCard` | `card_declined` | `stolen_card` | PI confirmation and SI confirmation |
| `4000000000000069` | `chargeDeclinedExpiredCard`, `visa_chargeDeclinedExpiredCard` | `expired_card` | — | PI confirmation and SI confirmation |
| `4000000000000127` | `chargeDeclinedIncorrectCvc`, `visa_chargeDeclinedIncorrectCvc` | `incorrect_cvc` | — | PI confirmation and SI confirmation when CVC supplied |
| `4000000000000119` | `chargeDeclinedProcessingError`, `visa_chargeDeclinedProcessingError` | `processing_error` | — | PI confirmation and SI confirmation |
| `4000000000006975` | `visa_chargeDeclinedVelocityLimitExceeded`, `chargeDeclinedVelocityLimitExceeded` | `card_declined` | `card_velocity_exceeded` | PI confirmation and SI confirmation |
| `4000000000000341` | `chargeCustomerFail`, `visa_chargeCustomerFail` | `card_declined` | `generic_decline` | PI confirmation; SI confirmation succeeds and attaches |
| `4242424242424241` | — | `incorrect_number` | — | [Card creation](#card-creation-errors); no method created |
| `4242424242424242` | `visa` | — | — | None; success card |

| Rule | Contract | Evidence |
|---|---|---|
| Catalogue | Each number, named PaymentMethod and corresponding `tok_` spelling has the same behavior; method creation and intent creation without confirmation do not attempt payment | rule [testing-declines] |
| Names | Published issuer-decline names have `visa_`; expired, CVC, processing and `chargeCustomerFail` names do not. Other spellings above are compatibility aliases | rule [testing]; decision (aliases) |
| Precedence | Matching explicit scenario, else card behavior, else success; applies to PI create/confirm/capture and SI confirm. A rule for another operation does not override the card | decision |
| Rule identity | For copies, `paymentMethodId` matches the published source id on the copying request and later operations; otherwise match the fixture/created method id | decision |
| Confirmation | Includes `confirm=true` create and `/confirm`, automatic/manual capture, on-/off-session; supported non-3DS declines have no published off-session distinction | rule [testing] |
| Capture | No separate issuer failure for these cards after authorization. Authorization allowed by a scenario can be captured | rule [testing]; decision (no invented second decline) |
| Attachment | Issuer-decline cards fail setup and remain unattached. `chargeCustomerFail` succeeds at setup and remains attached after its charge declines; [attachment surface](#setupintents) | rule [testing]; probe [r-decline-si]; decision (SI as attachment, successful setup and generic reason for `chargeCustomerFail`) |
| CVC | Omission skips incorrect-CVC behavior. Named methods/tokens imply the check was supplied. Store behavior name, never PAN/CVC | rule [testing]; decision (named input, storage) |
| Legacy cards | Known copies recover behavior from `copied_from`; older PAN-free raw cards/fixtures under ordinary ids have none because last4 cannot identify a card. Held methods named by published decline ids retain their id's outcome | decision |
| Decline representation | [Issuer errors](#issuer-errors), [embedded objects](#required-error-shape), [failed charges](#charges), [idempotent replay](#idempotency) | probe [r-decline-pi], [r-decline-si], [r-checkout-decline]; decision |
| Authentication scenario | Explicit `authentication_required` decline in [issuer errors](#issuer-errors); card-driven authentication is [outside the contract](#what-still-cannot-be-trusted-here) | example [without-auth]; decision |

## Seeded Personas

One Customer and saved card per persona (**decision**); shared rules: [Seeded Personas](README.md#seeded-personas).

| Persona | Intent | Scenario preset |
|---|---|---|
| `stripe-customer-with-visa-success` | `a-saved-card-that-succeeds` | `preauth-success-capture-success` |
| `stripe-customer-with-visa-that-is-declined` | `a-saved-card-that-is-declined` | `preauth-success-capture-decline` |
| `stripe-customer-whose-capture-answer-is-lost` | `a-saved-card-whose-capture-answer-is-lost` | `capture-accepted-response-lost` |

| Seeded field | Value | Evidence |
|---|---|---|
| `alias` | Persona name | decision |
| `ownerId` | `a-customer-stripe-holds-a-card-for` | decision |
| `type` | `card` | decision |
| `providerIds.paymentMethodId`, `providerIds.customerId` | Fresh ids with the [creation formats](#resource-identifiers), never the published test id itself | decision |
| `last4` | `4242` | rule [testing] |
| `expMonth`, `expYear` | `12`, `2030`; test cards accept any future date | rule [testing]; decision (values) |
| `brand`, `funding` | `visa`, `credit` | rule [testing]; probe [r-method] |
| Scope | Each persona mints its own ids, so all can coexist in a run; seed into the run's configured Stripe account and use that account's key | decision |

## What Still Cannot Be Trusted Here

| Surface | Boundary | Evidence |
|---|---|---|
| Intent responses | Omit `confirmation_method`, `next_action`, `shipping`, `description`, `canceled_at`, `processing`, `setup_future_usage`, legacy `source` and other fields beyond the [rendered subsets](#minimum-api-surface); [response conformance](#response-conformance) defines schema bounds | decision; probe [recordings]; rule [openapi] |
| Refund responses | Omit `next_action`, `pending_reason`, `failure_reason`; no action payload modeled. Pinned schemas make `next_action` optional/non-nullable, current prose nullable; omission satisfies both | decision; rule [refund-object], [openapi] |
| Unread parameters | Accepted without effect on every route/version. Stripe returns 400 / `invalid_request_error` / `parameter_unknown`, `param={name}`, “Received unknown parameter: {name}”. Public schemas omit real inputs such as `payment_method_data[card]`, so schema-only rejection would reject real integrations | decision; probe [r-unknown-param], [r-inline-missing] |
| Collection clients | No `/v1/elements/sessions`, Checkout Sessions API, Tokens API, complete Stripe.js/PaymentSheet, live collection or non-card rails. PaymentSheet's first `/v1/elements/sessions` call is absent from the published OpenAPI paths; SDK host override uses non-public mobile members and is unavailable on web | decision; rule [openapi]; example [ios-sdk], [android-sdk] |
| Authentication | No 3DS, challenge or `next_action` completion | decision; rule [testing] |
| Events | No `payment_intent.requires_action`, `setup_intent.succeeded`, `setup_intent.setup_failed`, `payment_method.attached`, `payment_method.detached` | decision; rule [event-types] |
| Refund events | No `charge.refunded` (published payload is Charge, real accounts also get it beside `refund.created`, including for partial refunds), `refund.updated`, `charge.refund.updated` or `refund.failed`; latter updates concern selected methods or status changes, while [refund status](#refunds) is fixed here | decision; rule [event-types], [refund-object] |
| Selection dimensions | Beyond [modelled compatibility](#payment-method-selection), Stripe also weighs amount, country, customer and Dashboard settings | rule [selection-change]; decision |

| After-effect faults | Capture/refund transport failures are simulator fault controls; [authoritative reads](#status-mapping) define reconciliation | decision; rule [low-level] |

Published test behavior omitted here (rule [testing]; decision for simulator boundary):

| Published group | Cards and ids | Simulator behavior |
|---|---|---|
| Authentication/setup | `4000002500003155`, `4000002760003184`, `4000003800000446`, `4000008260003178`; `authenticationRequiredOnSetup`, `authenticationRequired`, `authenticationRequiredSetupForOffSession`, `authenticationRequiredChargeDeclinedInsufficientFunds` | Scenario controls outcome; authentication boundary above |
| 3DS/mobile challenges | Testing's 3DS support/mobile challenge tables, including `4000000000003220`, `pm_card_threeDSecure2Required` | Scenario controls outcome; authentication boundary above |
| Radar/fraud review/captcha/disputes | Testing's corresponding tables, including `4100000000000019`, `4000000000004954`, `4000000000009235`, `4000000000000259` | No risk engine, review, captcha or dispute lifecycle |
| Refund timing/balance availability | `4000000000007726`, `4000000000005126`, `pm_card_pendingRefund`, `pm_card_refundFail`, `pm_card_bypassPending`, `pm_card_bypassPendingInternational` and corresponding testing-table cards | No card-driven refund/balance behavior |
| Terminal/non-card | Testing's PIN/Terminal cards and non-card values | No Terminal/non-card test-value outcomes |

Out-of-scope ids use the [permissive test-id handling](#inline-methods-and-test-id-copies); numbers create ordinary card methods.

## Sources

| Evidence | Scope |
|---|---|
| Rule | Stripe API pages linked at each fact; [API versions][api-versions], [upgrades] and immutable OpenAPI sources below |
| Example | Published examples and SDK sources: [Java form encoder][java-form]; [Java][java-ephemeral] and [Node][node-ephemeral] ephemeral-key creation; [iOS][ios-sdk] `STPAPIClient.swift`, `STPAPIClient+Payments.swift`; [Android][android-sdk] `ApiVersion.kt`, `RequestHeadersFactory.kt`, `StripeApiRepository.kt`, `EphemeralKeyFixtures.kt`, `EphemeralKeyManager` |
| Probe | No-key POST to [Stripe][no-key], without account/version, 2026-09-28; [Stripe-owned iOS recordings][recordings] use publishable keys and `Stripe-Version: 2020-08-27`; linked developer reports retain their separate scope |
| Decision / inference | Simulator choices and inferred statuses are labeled at their facts; they are not observed Stripe responses |
| Versioned inputs | [Endive create parameters][versioned-create] and corresponding PI/SI create/confirm/object pages at `2026-08-26.dahlia`; immutable schemas determine historical inputs, not the page's version query |
| Reported publishable refusal | [2017 stripe-php `GET /v1/account` trace][pk-report]: 403, “This API call cannot be made with a publishable API key. Please use a secret API key.”; [simulator error](#authentication-and-version-errors) uses published code wording |
| Expansion reports | [2025 Subscription create][expand-report] refused `payment_intent`; [2017 Charges list][python-expand-report] suggested `data.application` for `application`; [2020 Plans list][node-expand-report] did the same for `tiers`; [2017 Events thread][event-expand-report] gives the event-path examples in [expansion errors](#parameter-and-expansion-errors). Reports provide no status/type/param |
| Non-expandable observation | [2025 PK intent retrieve][r-ignored] with `expand[0]=metadata` and `client_secret`: HTTP 200, no `metadata`; no secret-key counterpart recorded |
| Recording corpus | Two invalid-key 401s have no version header; PM creation: 132 responses, three requests sent metadata, none returned it; inline confirms: 34 PI and 13 SI HTTP 200 responses |

Recording references (probe):

| Reference | Recording | Location |
|---|---|---|
| `r-method` | [STPPaymentMethodFunctionalTest/testCreateCardPaymentMethod/0000_post_v1_payment_methods.tail][r-method] | committed recording |
| `r-attached` | [PaymentSheetAPITest/testPaymentSheetLoadAndConfirmWithPaymentIntentAttachedPaymentMethod/0002_post_v1_payment_intents_pi_3RW1PFFY0qyl6XeW1c5Mc41w_confirm.tail][r-attached] | committed recording |
| `r-decline-pi` | [PaymentSheetAPITest/testDeferredConfirmsavedinsufficientfundscard/0002_post_v1_payment_intents_pi_3RW1OTFY0qyl6XeW1v2ixP94_confirm.tail][r-decline-pi] | committed recording |
| `r-decline-si` | [PaymentSheetAPITest/testDeferredConfirmsavedinsufficientfundscard/0006_post_v1_setup_intents_seti_1RW1OVFY0qyl6XeWTBEXiXIe_confirm.tail][r-decline-si] | committed recording |
| `r-checkout-decline` | [CheckoutConfirmationFunctionalTests/testconfirmwithdeclinedcardallowsretrywithvalidcard/0004_post_v1_checkout_client_cs_test_a16zwjB2W92T7K1rsfTdZFaTuVGUvRcAogIlG1J7DdFWdtj51dqYfmml1X_confirm.tail][r-checkout-decline] | committed recording |
| `r-link` | [PaymentSheetLPMConfirmFlowTests/testLinkCardConfirmFlows/0002_get_v1_payment_intents_pi_3UVdlcGoesj9fw9Q1dp9Xowy.tail][r-link] | committed recording |
| `r-apple` | [CheckoutApplePayContextFunctionalTest/testCompletesPayment/0005_get_v1_payment_intents_pi_3UVe60Lu5o3P18Zp1ny1phsB.tail][r-apple] | committed recording |
| `r-wrong-secret` | [STPPaymentIntentFunctionalTest/testRetrieveWithWrongSecret/0000_get_v1_payment_intents_pi_1GGCGfFY0qyl6XeWbSAsh2hn.tail][r-wrong-secret] | committed recording |
| `r-mismatched` | [STPPaymentIntentFunctionalTest/testRetrieveMismatchedPublishableKey/0000_get_v1_payment_intents_pi_1GGCGfFY0qyl6XeWbSAsh2hn.tail][r-mismatched] | committed recording |
| `r-inline-missing` | [STPPaymentHandlerFunctionalTests/testconfirmsetupintentsendsanalytic/0000_post_v1_setup_intents_seti_1P1xLBFY0qyl6XeWc7c2LrMK_confirm.tail][r-inline-missing] | committed recording |
| `r-missing-pm` | [STPPaymentHandlerFunctionalTests/testconfirmpaymentintentsendsanalytic/0000_post_v1_payment_intents_pi_3P20wFFY0qyl6XeW0dSOQ6W7_confirm.tail][r-missing-pm] | committed recording |
| `r-inline-pi` | [STPPaymentIntentFunctionalTest/testConfirmCardWithoutNetworkParam/0001_post_v1_payment_intents_pi_3SHqvTFY0qyl6XeW1L2WGE3T_confirm.tail][r-inline-pi] | committed recording |
| `r-inline-si` | [StripeAPIBridgeNetworkTests/testConfirmSetupIntent/0001_post_v1_setup_intents_seti_1RxtukFY0qyl6XeWvxRtOLup_confirm.tail][r-inline-si] | committed recording |
| `r-year` | [StripeAPIBridgeNetworkTests/testCreatePaymentMethod/0000_post_v1_payment_methods.tail][r-year] | committed recording |
| `r-unknown-pm` | [STPPaymentHandlerFunctionalTests/testconfirmpaymentintentsavedpmsendsanalytic/0000_post_v1_payment_intents_pi_3P20wFFY0qyl6XeW0dSOQ6W7_confirm.tail][r-unknown-pm] | upstream recording |
| `r-unknown-si` | [STPPaymentHandlerFunctionalTests/testconfirmsetupintentsavedpmsendsanalytic/0000_post_v1_setup_intents_seti_1P1xLBFY0qyl6XeWc7c2LrMK_confirm.tail][r-unknown-si] | upstream recording |
| `r-bad-si` | [STPApplePayContextFunctionalTest/testBadSetupIntentClientSecretErrors/0002_get_v1_setup_intents_seti_bad.tail][r-bad-si] | upstream recording |
| `r-ignored` | [StripeAPIBridgeNetworkTests/testRetrievePaymentIntent/0001_get_v1_payment_intents_pi_3RVwmeFY0qyl6XeW1AaJQfeN.tail][r-ignored] | upstream recording |
| `r-invalid-network` | [STPPaymentIntentFunctionalTest/testConfirmCardWithInvalidNetworkParam/0001_post_v1_payment_intents_pi_3SHqvRFY0qyl6XeW0YHitP0Y_confirm.tail][r-invalid-network] | upstream recording |
| `r-invalid-type` | [PaymentSheetLoaderTest/testPaymentSheetLoadDeferredIntentFails/0000_get_v1_elements_sessions.tail][r-invalid-type] | upstream recording |
| `r-token-invalid` | [STPCardFunctionalTest/testCardTokenCreationWithInvalidParams/0000_post_v1_tokens.tail][r-token-invalid] | upstream recording |
| `r-token-year` | [STPCardFunctionalTest/testCardTokenCreationWithExpiredCard/0000_post_v1_tokens.tail][r-token-year] | upstream recording |
| `r-token-cvc` | [STPCardFunctionalTest/testInvalidCVC/0000_post_v1_tokens.tail][r-token-cvc] | upstream recording |
| `r-copy-expiry` | [STPPaymentHandlerFunctionalTests/testconfirmpaymentintentwithpaymentsorchestration/0001_post_v1_payment_intents_pi_3T4q8nFRhicYlEF70UH3R6pA_confirm.tail][r-copy-expiry] | upstream recording |
| `r-unknown-param` | [STPPaymentMethodModernTest/testCreateCardPaymentMethodWithAdditionalAPIStuff/0000_post_v1_payment_methods.tail][r-unknown-param] | upstream recording |
| `r-create-token` | [STPCardFunctionalTest/testCreateCardTokenAsync/0000_post_v1_tokens.tail][r-create-token] | upstream recording |
| `r-pk-error` | [StripeAPIBridgeNetworkTests/testPKPaymentError/0000_post_v1_tokens.tail][r-pk-error] | upstream recording |
| `r-new-decline` | [PaymentSheetAPITest/testDeferredConfirmnewinsufficientfundscard/0000_post_v1_payment_methods.tail][r-new-decline] | upstream recording |

Immutable OpenAPI provenance (**rule**, [openapi]): `info.version`, full source commit and URL accompany each response snapshot.

| Version | Source commit | Response snapshot |
|---|---|---|
| `2020-03-02` | [0e77e37c1ad9375293fb8cc397130b9e4ed31393][spec-2020-03-02] | [2020-03-02.json](../src/test/resources/stripe-openapi/2020-03-02.json) |
| `2020-08-27` | [adae467392d46f04ac1afca7b96e7d471c647e1c][spec-2020-08-27] | [2020-08-27.json](../src/test/resources/stripe-openapi/2020-08-27.json) |
| `2025-03-31.basil` | [072fe51d85959b0ca71c1296b32725c14a78f4a8][spec-2025-03-31.basil] | [2025-03-31.basil.json](../src/test/resources/stripe-openapi/2025-03-31.basil.json) |
| `2025-04-30.basil` | [46e685e34c4fc7f56e8696936db2554e0c5b0fc1][spec-2025-04-30.basil] | [2025-04-30.basil.json](../src/test/resources/stripe-openapi/2025-04-30.basil.json) |
| `2025-05-28.basil` | [6c7b45018fdeb279042e6f74c5141efe1e90b3d6][spec-2025-05-28.basil] | [2025-05-28.basil.json](../src/test/resources/stripe-openapi/2025-05-28.basil.json) |
| `2025-06-30.basil` | [1729cb1360263b054abb3c5146548630a86fb68c][spec-2025-06-30.basil] | [2025-06-30.basil.json](../src/test/resources/stripe-openapi/2025-06-30.basil.json) |
| `2025-07-30.basil` | [805a9b8cc62f35d2673b28c34ca5fa27d0650b8b][spec-2025-07-30.basil] | [2025-07-30.basil.json](../src/test/resources/stripe-openapi/2025-07-30.basil.json) |
| `2025-08-27.basil` | [62fbe0713198e910b8843f70fdcd351be3ec979c][spec-2025-08-27.basil] | [2025-08-27.basil.json](../src/test/resources/stripe-openapi/2025-08-27.basil.json) |
| `2025-09-30.clover` | [053c4b35b25fdb4b8f2afa4294a046026715f820][spec-2025-09-30.clover] | [2025-09-30.clover.json](../src/test/resources/stripe-openapi/2025-09-30.clover.json) |
| `2025-10-29.clover` | [260f91c74e44bcd2a819bac9428f6c9e57360105][spec-2025-10-29.clover] | [2025-10-29.clover.json](../src/test/resources/stripe-openapi/2025-10-29.clover.json) |
| `2025-11-17.clover` | [8820133ae30a4b968ea20d803c51f639ab5461c6][spec-2025-11-17.clover] | [2025-11-17.clover.json](../src/test/resources/stripe-openapi/2025-11-17.clover.json) |
| `2025-12-15.clover` | [ed93b94259c55782b8b04e01f4ffdaa2872e9bcd][spec-2025-12-15.clover] | [2025-12-15.clover.json](../src/test/resources/stripe-openapi/2025-12-15.clover.json) |
| `2026-01-28.clover` | [d0f9e4c144d0927877afa13586f6efc78da5b0fc][spec-2026-01-28.clover] | [2026-01-28.clover.json](../src/test/resources/stripe-openapi/2026-01-28.clover.json) |
| `2026-02-25.clover` | [d608561910d9b3a8c36da7bb503a51d8c201618f][spec-2026-02-25.clover] | [2026-02-25.clover.json](../src/test/resources/stripe-openapi/2026-02-25.clover.json) |
| `2026-03-25.dahlia` | [5146460c7a0c6dfcf9d4c92d1b0abab58719c6ff][spec-2026-03-25.dahlia] | [2026-03-25.dahlia.json](../src/test/resources/stripe-openapi/2026-03-25.dahlia.json) |
| `2026-04-22.dahlia` | [0ed1633f1840f4d46b6b7d938a5b8c8662924495][spec-2026-04-22.dahlia] | [2026-04-22.dahlia.json](../src/test/resources/stripe-openapi/2026-04-22.dahlia.json) |
| `2026-05-27.dahlia` | [eebb74e2089c37baf2d3ccf322e7a30927dd4abf][spec-2026-05-27.dahlia] | [2026-05-27.dahlia.json](../src/test/resources/stripe-openapi/2026-05-27.dahlia.json) |
| `2026-06-24.dahlia` | [a2de9917ac9c7be3ba11abd5151b5c2df3add59e][spec-2026-06-24.dahlia] | [2026-06-24.dahlia.json](../src/test/resources/stripe-openapi/2026-06-24.dahlia.json) |
| `2026-07-29.dahlia` | [19be337d65af244fed355eaeb144de6dbeea82f1][spec-2026-07-29.dahlia] | [2026-07-29.dahlia.json](../src/test/resources/stripe-openapi/2026-07-29.dahlia.json) |
| `2026-08-26.dahlia` | [9ac29c7795ab21c7711b4bc25bb2dd739552a5fa][spec-2026-08-26.dahlia] | [2026-08-26.dahlia.json](../src/test/resources/stripe-openapi/2026-08-26.dahlia.json) |
| `2026-09-30.endive` | [969d8c308ea65b6dbdef4b6b4a71904027a010b8][spec-2026-09-30.endive] | [2026-09-30.endive.json](../src/test/resources/stripe-openapi/2026-09-30.endive.json) |
| `2026-10-28.endive` | [51610f5f061c9723730582a7638b8cc0e646640d][spec-2026-10-28.endive] | [2026-10-28.endive.json](../src/test/resources/stripe-openapi/2026-10-28.endive.json) |

[allowed-selection]: https://docs.stripe.com/changelog/dahlia/2026-07-29/allowed-payment-method-types-parameter.md
[android-sdk]: https://github.com/stripe/stripe-android
[api-versions]: https://docs.stripe.com/api-versions.md
[async-default]: https://docs.stripe.com/changelog/2024-04-10/automatic-sync-default-paymentintents.md
[auth]: https://docs.stripe.com/api/authentication
[basil-capture]: https://docs.stripe.com/changelog/basil/2025-03-31/remove-refund-from-partial-capture-and-payment-cancellation-flow.md
[changelog]: https://docs.stripe.com/changelog.md
[charge-auto-expand]: https://docs.stripe.com/changelog/2022-11-15/deprecates-charges-auto-expand.md
[charge-object]: https://docs.stripe.com/api/charges/object.md
[codes]: https://docs.stripe.com/error-codes
[customer-create]: https://docs.stripe.com/api/customers/create
[customer-delete]: https://docs.stripe.com/api/customers/delete
[customer-error-report]: https://github.com/stripe/stripe-go/issues/1338
[customer-object]: https://docs.stripe.com/api/customers/object.md
[customer-update]: https://docs.stripe.com/api/customers/update
[decline-pi]: https://docs.stripe.com/changelog/2022-11-15/adds-decline-codes-paymentintent-paymentmethod.md
[decline-si]: https://docs.stripe.com/changelog/2022-11-15/adds-decline-codes-setupintent.md
[declines]: https://docs.stripe.com/declines/codes
[endive-selection]: https://docs.stripe.com/changelog/endive/2026-09-30/removes-the-payment-method-types-parameter-from-payment-intents-and-setup-intents.md
[ephemeral-create]: https://docs.stripe.com/api/ephemeral_keys/create
[ephemeral-object]: https://docs.stripe.com/api/ephemeral_keys/object
[error-handling]: https://docs.stripe.com/error-handling
[errors]: https://docs.stripe.com/api/errors
[event-expand-report]: https://groups.google.com/a/lists.stripe.com/g/api-discuss/c/GN--PqYSzQs
[event-object]: https://docs.stripe.com/api/events/object
[event-types]: https://docs.stripe.com/api/events/types
[exclusive-report]: https://github.com/laravel/cashier-stripe/pull/1345
[expand]: https://docs.stripe.com/expand.md
[expand-api]: https://docs.stripe.com/api/expanding_objects.md
[expand-report]: https://forum.adalo.com/t/stripe-subscription-component-error/56346
[idempotency]: https://docs.stripe.com/api/idempotent_requests
[invalid-version-report]: https://wordpress.org/support/topic/invalid-stripe-api-version-yith_wcstripe_api_version/
[ios-sdk]: https://github.com/stripe/stripe-ios
[java-ephemeral]: https://github.com/stripe/stripe-java/blob/v33.4.2/src/main/java/com/stripe/service/EphemeralKeyService.java
[java-form]: https://github.com/stripe/stripe-java/blob/v33.4.2/src/main/java/com/stripe/net/FormEncoder.java
[keys]: https://docs.stripe.com/keys.md
[lifecycle]: https://docs.stripe.com/payments/paymentintents/lifecycle
[low-level]: https://docs.stripe.com/error-low-level
[metadata]: https://docs.stripe.com/api/metadata.md
[method-create]: https://docs.stripe.com/api/payment_methods/create.md
[method-customer-list]: https://docs.stripe.com/api/payment_methods/customer_list
[method-detach]: https://docs.stripe.com/api/payment_methods/detach
[method-list]: https://docs.stripe.com/api/payment_methods/list
[method-object]: https://docs.stripe.com/api/payment_methods/object.md
[method-support]: https://docs.stripe.com/payments/payment-methods/payment-method-support.md
[multicapture]: https://docs.stripe.com/payments/multicapture
[no-key]: https://api.stripe.com/v1/payment_intents
[node-ephemeral]: https://github.com/stripe/stripe-node/blob/master/src/resources/EphemeralKeys.ts
[node-expand-report]: https://github.com/stripe/stripe-node/issues/1007
[openapi]: https://github.com/stripe/openapi
[paypal]: https://docs.stripe.com/payments/paypal.md
[pi-cancel]: https://docs.stripe.com/api/payment_intents/cancel
[pi-capture]: https://docs.stripe.com/api/payment_intents/capture
[pi-confirm]: https://docs.stripe.com/api/payment_intents/confirm.md
[pi-create]: https://docs.stripe.com/api/payment_intents/create
[pi-object]: https://docs.stripe.com/api/payment_intents/object
[pi-retrieve]: https://docs.stripe.com/api/payment_intents/retrieve.md
[pk-report]: https://mage2.pro/t/topic/4773
[preview-selection]: https://docs.stripe.com/changelog/dahlia/2026-08-26/removes-payment-method-types-parameter-from-payment-intents-setup-intents
[python-expand-report]: https://github.com/stripe/stripe-python/issues/316
[r-apple]: ../src/test/resources/stripe-ios-recorded-traffic/CheckoutApplePayContextFunctionalTest/testCompletesPayment/0005_get_v1_payment_intents_pi_3UVe60Lu5o3P18Zp1ny1phsB.tail
[r-attached]: ../src/test/resources/stripe-ios-recorded-traffic/PaymentSheetAPITest/testPaymentSheetLoadAndConfirmWithPaymentIntentAttachedPaymentMethod/0002_post_v1_payment_intents_pi_3RW1PFFY0qyl6XeW1c5Mc41w_confirm.tail
[r-bad-si]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPApplePayContextFunctionalTest/testBadSetupIntentClientSecretErrors/0002_get_v1_setup_intents_seti_bad.tail
[r-checkout-decline]: ../src/test/resources/stripe-ios-recorded-traffic/CheckoutConfirmationFunctionalTests/testconfirmwithdeclinedcardallowsretrywithvalidcard/0004_post_v1_checkout_client_cs_test_a16zwjB2W92T7K1rsfTdZFaTuVGUvRcAogIlG1J7DdFWdtj51dqYfmml1X_confirm.tail
[r-copy-expiry]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPPaymentHandlerFunctionalTests/testconfirmpaymentintentwithpaymentsorchestration/0001_post_v1_payment_intents_pi_3T4q8nFRhicYlEF70UH3R6pA_confirm.tail
[r-decline-pi]: ../src/test/resources/stripe-ios-recorded-traffic/PaymentSheetAPITest/testDeferredConfirmsavedinsufficientfundscard/0002_post_v1_payment_intents_pi_3RW1OTFY0qyl6XeW1v2ixP94_confirm.tail
[r-decline-si]: ../src/test/resources/stripe-ios-recorded-traffic/PaymentSheetAPITest/testDeferredConfirmsavedinsufficientfundscard/0006_post_v1_setup_intents_seti_1RW1OVFY0qyl6XeWTBEXiXIe_confirm.tail
[r-ignored]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/StripeAPIBridgeNetworkTests/testRetrievePaymentIntent/0001_get_v1_payment_intents_pi_3RVwmeFY0qyl6XeW1AaJQfeN.tail
[r-inline-missing]: ../src/test/resources/stripe-ios-recorded-traffic/STPPaymentHandlerFunctionalTests/testconfirmsetupintentsendsanalytic/0000_post_v1_setup_intents_seti_1P1xLBFY0qyl6XeWc7c2LrMK_confirm.tail
[r-inline-pi]: ../src/test/resources/stripe-ios-recorded-traffic/STPPaymentIntentFunctionalTest/testConfirmCardWithoutNetworkParam/0001_post_v1_payment_intents_pi_3SHqvTFY0qyl6XeW1L2WGE3T_confirm.tail
[r-inline-si]: ../src/test/resources/stripe-ios-recorded-traffic/StripeAPIBridgeNetworkTests/testConfirmSetupIntent/0001_post_v1_setup_intents_seti_1RxtukFY0qyl6XeWvxRtOLup_confirm.tail
[r-invalid-network]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPPaymentIntentFunctionalTest/testConfirmCardWithInvalidNetworkParam/0001_post_v1_payment_intents_pi_3SHqvRFY0qyl6XeW0YHitP0Y_confirm.tail
[r-invalid-type]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/PaymentSheetLoaderTest/testPaymentSheetLoadDeferredIntentFails/0000_get_v1_elements_sessions.tail
[r-link]: ../src/test/resources/stripe-ios-recorded-traffic/PaymentSheetLPMConfirmFlowTests/testLinkCardConfirmFlows/0002_get_v1_payment_intents_pi_3UVdlcGoesj9fw9Q1dp9Xowy.tail
[r-method]: ../src/test/resources/stripe-ios-recorded-traffic/STPPaymentMethodFunctionalTest/testCreateCardPaymentMethod/0000_post_v1_payment_methods.tail
[r-mismatched]: ../src/test/resources/stripe-ios-recorded-traffic/STPPaymentIntentFunctionalTest/testRetrieveMismatchedPublishableKey/0000_get_v1_payment_intents_pi_1GGCGfFY0qyl6XeWbSAsh2hn.tail
[r-missing-pm]: ../src/test/resources/stripe-ios-recorded-traffic/STPPaymentHandlerFunctionalTests/testconfirmpaymentintentsendsanalytic/0000_post_v1_payment_intents_pi_3P20wFFY0qyl6XeW0dSOQ6W7_confirm.tail
[r-token-cvc]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPCardFunctionalTest/testInvalidCVC/0000_post_v1_tokens.tail
[r-token-invalid]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPCardFunctionalTest/testCardTokenCreationWithInvalidParams/0000_post_v1_tokens.tail
[r-token-year]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPCardFunctionalTest/testCardTokenCreationWithExpiredCard/0000_post_v1_tokens.tail
[r-unknown-param]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPPaymentMethodModernTest/testCreateCardPaymentMethodWithAdditionalAPIStuff/0000_post_v1_payment_methods.tail
[r-unknown-pm]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPPaymentHandlerFunctionalTests/testconfirmpaymentintentsavedpmsendsanalytic/0000_post_v1_payment_intents_pi_3P20wFFY0qyl6XeW0dSOQ6W7_confirm.tail
[r-unknown-si]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPPaymentHandlerFunctionalTests/testconfirmsetupintentsavedpmsendsanalytic/0000_post_v1_setup_intents_seti_1P1xLBFY0qyl6XeWc7c2LrMK_confirm.tail
[r-wrong-secret]: ../src/test/resources/stripe-ios-recorded-traffic/STPPaymentIntentFunctionalTest/testRetrieveWithWrongSecret/0000_get_v1_payment_intents_pi_1GGCGfFY0qyl6XeWbSAsh2hn.tail
[r-year]: ../src/test/resources/stripe-ios-recorded-traffic/StripeAPIBridgeNetworkTests/testCreatePaymentMethod/0000_post_v1_payment_methods.tail
[recordings]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic
[refund-create]: https://docs.stripe.com/api/refunds/create
[refund-object]: https://docs.stripe.com/api/refunds/object
[return-report]: https://laracasts.com/discuss/channels/laravel/cashier-stripe-integration-fails-with-error-return-url-required
[return-report-2]: https://github.com/Attendize/Attendize/issues/1115
[save-reuse]: https://docs.stripe.com/payments/save-and-reuse
[selection-change]: https://docs.stripe.com/changelog/2023-08-16/automatic-payment-methods.md
[si-confirm]: https://docs.stripe.com/api/setup_intents/confirm.md
[si-create]: https://docs.stripe.com/api/setup_intents/create
[si-object]: https://docs.stripe.com/api/setup_intents/object
[si-retrieve]: https://docs.stripe.com/api/setup_intents/retrieve.md
[testing]: https://docs.stripe.com/testing.md
[upgrades]: https://docs.stripe.com/upgrades.md
[version-design]: https://stripe.com/blog/api-versioning
[versioned-create]: https://docs.stripe.com/api/payment_intents/create.md?api-version=2026-10-28.endive
[versioning]: https://docs.stripe.com/api/versioning.md
[webhook-versioning]: https://docs.stripe.com/webhooks/versioning.md
[webhooks]: https://docs.stripe.com/webhooks
[without-auth]: https://docs.stripe.com/payments/without-card-authentication
[spec-2020-03-02]: https://raw.githubusercontent.com/stripe/openapi/0e77e37c1ad9375293fb8cc397130b9e4ed31393/openapi/spec3.json
[spec-2020-08-27]: https://raw.githubusercontent.com/stripe/openapi/adae467392d46f04ac1afca7b96e7d471c647e1c/openapi/spec3.json
[spec-2025-03-31.basil]: https://raw.githubusercontent.com/stripe/openapi/072fe51d85959b0ca71c1296b32725c14a78f4a8/openapi/spec3.json
[spec-2025-04-30.basil]: https://raw.githubusercontent.com/stripe/openapi/46e685e34c4fc7f56e8696936db2554e0c5b0fc1/openapi/spec3.json
[spec-2025-05-28.basil]: https://raw.githubusercontent.com/stripe/openapi/6c7b45018fdeb279042e6f74c5141efe1e90b3d6/openapi/spec3.json
[spec-2025-06-30.basil]: https://raw.githubusercontent.com/stripe/openapi/1729cb1360263b054abb3c5146548630a86fb68c/openapi/spec3.json
[spec-2025-07-30.basil]: https://raw.githubusercontent.com/stripe/openapi/805a9b8cc62f35d2673b28c34ca5fa27d0650b8b/openapi/spec3.json
[spec-2025-08-27.basil]: https://raw.githubusercontent.com/stripe/openapi/62fbe0713198e910b8843f70fdcd351be3ec979c/openapi/spec3.json
[spec-2025-09-30.clover]: https://raw.githubusercontent.com/stripe/openapi/053c4b35b25fdb4b8f2afa4294a046026715f820/openapi/spec3.json
[spec-2025-10-29.clover]: https://raw.githubusercontent.com/stripe/openapi/260f91c74e44bcd2a819bac9428f6c9e57360105/openapi/spec3.json
[spec-2025-11-17.clover]: https://raw.githubusercontent.com/stripe/openapi/8820133ae30a4b968ea20d803c51f639ab5461c6/openapi/spec3.json
[spec-2025-12-15.clover]: https://raw.githubusercontent.com/stripe/openapi/ed93b94259c55782b8b04e01f4ffdaa2872e9bcd/openapi/spec3.json
[spec-2026-01-28.clover]: https://raw.githubusercontent.com/stripe/openapi/d0f9e4c144d0927877afa13586f6efc78da5b0fc/latest/openapi.spec3.json
[spec-2026-02-25.clover]: https://raw.githubusercontent.com/stripe/openapi/d608561910d9b3a8c36da7bb503a51d8c201618f/latest/openapi.spec3.json
[spec-2026-03-25.dahlia]: https://raw.githubusercontent.com/stripe/openapi/5146460c7a0c6dfcf9d4c92d1b0abab58719c6ff/latest/openapi.spec3.json
[spec-2026-04-22.dahlia]: https://raw.githubusercontent.com/stripe/openapi/0ed1633f1840f4d46b6b7d938a5b8c8662924495/latest/openapi.spec3.json
[spec-2026-05-27.dahlia]: https://raw.githubusercontent.com/stripe/openapi/eebb74e2089c37baf2d3ccf322e7a30927dd4abf/latest/openapi.spec3.json
[spec-2026-06-24.dahlia]: https://raw.githubusercontent.com/stripe/openapi/a2de9917ac9c7be3ba11abd5151b5c2df3add59e/latest/openapi.spec3.json
[spec-2026-07-29.dahlia]: https://raw.githubusercontent.com/stripe/openapi/19be337d65af244fed355eaeb144de6dbeea82f1/latest/openapi.spec3.json
[spec-2026-08-26.dahlia]: https://raw.githubusercontent.com/stripe/openapi/9ac29c7795ab21c7711b4bc25bb2dd739552a5fa/latest/openapi.spec3.json
[spec-2026-09-30.endive]: https://raw.githubusercontent.com/stripe/openapi/969d8c308ea65b6dbdef4b6b4a71904027a010b8/latest/openapi.spec3.json
[spec-2026-10-28.endive]: https://raw.githubusercontent.com/stripe/openapi/51610f5f061c9723730582a7638b8cc0e646640d/latest/openapi.spec3.json
[testing-declines]: https://docs.stripe.com/testing.md#declined-payments
[incorrect-cvc]: https://docs.stripe.com/error-codes#incorrect-cvc
[authentication-required]: https://docs.stripe.com/error-codes#authentication-required
[r-valid-pi]: ../src/test/resources/stripe-ios-recorded-traffic/PaymentSheetAPITest/testDeferredConfirmvalidsavedcard/0002_get_v1_payment_intents_pi_3RyFvBFY0qyl6XeW1nCMOTKN.tail
[r-valid-si]: ../src/test/resources/stripe-ios-recorded-traffic/PaymentSheetAPITest/testDeferredConfirmvalidsavedcard/0012_post_v1_setup_intents_seti_1RyFvFFY0qyl6XeWN8iE5v5p_confirm.tail
[r-create-token]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/STPCardFunctionalTest/testCreateCardTokenAsync/0000_post_v1_tokens.tail
[r-pk-error]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/StripeAPIBridgeNetworkTests/testPKPaymentError/0000_post_v1_tokens.tail
[r-new-decline]: https://github.com/stripe/stripe-ios/tree/master/StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic/PaymentSheetAPITest/testDeferredConfirmnewinsufficientfundscard/0000_post_v1_payment_methods.tail
