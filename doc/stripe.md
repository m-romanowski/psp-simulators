# Stripe Simulator Contract

## What This File Covers

The vendor contract for the Stripe `/v1` API as this simulator serves it: customers, ephemeral keys, SetupIntents,
PaymentIntents (manual-capture hold, off-session charge, multicapture), charges, refunds, PaymentMethods, signed
webhooks and errors. For each route it records what Stripe publishes, what the simulator emits, the error codes and
messages, and where the simulator deviates.

Evidence classes, per `README.md`, "Evidence Classes":

- **rule**: prose or a table on a Stripe page,
- **example**: a sample on a Stripe page,
- **probe**: observed against Stripe itself: a `POST /v1/payment_intents` carrying no API key, probed 2026-09-28, and
  the api.stripe.com traffic Stripe's own iOS SDK recorded with `pk_test_` keys at `Stripe-Version: 2020-08-27`
  (stripe-ios, `StripePayments/StripePaymentsTestUtils/Resources/recorded_network_traffic`, "the recordings"),
- **decision**: a choice made by this simulator,
- **inference**: read off the wording, stated nowhere.

All pages were fetched on 2026-09-11, except those "Sources" dates otherwise.

### Routes

| Route                                                  | What the simulator does                                                          | Key deviations and choices                                                                                     |
|--------------------------------------------------------|----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `POST /v1/customers`                                   | creates a customer. Reads `email` only                                           | the object has `id`, `object`, `created`, `email`, `livemode` and nothing else                                  |
| `GET /v1/customers/{id}`                               | retrieves, including a deleted customer                                          | a deleted customer is returned as the three-field deleted object                                               |
| `POST /v1/customers/{id}`                              | updates `email`                                                                  | a blank `email=` is treated as absent. A deleted customer is refused `400` with no `code`                      |
| `DELETE /v1/customers/{id}`                            | deletes, and detaches every PaymentMethod of the customer                        | no `Idempotency-Key`. A second delete is refused `400` with no `code`                                           |
| `POST /v1/ephemeral_keys`                              | mints a key for a `customer`, expiring after 1 hour                              | `customer` is the only resource served, else `parameter_missing`. The key reaches only the two customer read routes |
| `POST /v1/setup_intents`                               | creates at `requires_payment_method`, or `requires_confirmation` with a `payment_method` | the selection parameters of "Payment Method Selection"; `payment_method_types` is written before `2026-09-30.endive` and refused from it |
| `POST /v1/setup_intents/{id}/confirm`                  | attaches the method to the customer and ends at `succeeded`, or fails to `requires_payment_method` | the only SetupIntent operation that takes scenarios. No SetupIntent events                      |
| `POST /v1/payment_intents`                             | creates, and optionally confirms inline (`confirm=true`)                          | the selection parameters of "Payment Method Selection", as on a SetupIntent                                    |
| `GET /v1/payment_intents/{id}`                         | current state                                                                    | no scenarios                                                                                                   |
| `POST /v1/payment_intents/{id}/confirm`                | authorizes or charges, and mints a charge                                        | no `requires_action` path: authentication-required is a `402` decline. A method of a type the intent does not offer is refused |
| `POST /v1/payment_intents/{id}/capture`                | full, partial and multicapture (`final_capture`)                                 | 50 non-final captures, then an `invalid_request_error` on `final_capture` with no `code`                      |
| `POST /v1/payment_intents/{id}/cancel`                 | cancels                                                                          | `processing` is cancellable although no card reaches it in Stripe                                              |
| `GET /v1/charges/{id}`                                 | charge read                                                                      | 13 fields only. Not on the control read surface                                                                |
| `POST /v1/refunds`                                     | refund by `payment_intent` or `charge`                                           | both sent, or neither, is refused. The resting status is set by `refundStatus`                                 |
| `GET /v1/payment_methods`                              | list, filtered by `customer` and `type`                                          | an unknown customer or type gives an empty list. No paging                                                     |
| `GET /v1/customers/{id}/payment_methods`               | list for one customer                                                            | an unknown customer is `resource_missing`                                                                      |
| `GET /v1/payment_methods/{id}`                         | retrieve                                                                         | an unknown method is `resource_missing`                                                                        |
| `POST /v1/payment_methods/{id}/detach`                 | detaches permanently                                                             | a second detach, and a detach of a method never attached, are both refused                                     |

Every route above except `DELETE /v1/customers/{id}` reads `expand` (see "Expansion").

Account settings: the `sk_test_` secret key (required), `captureWindow` (required ISO-8601 duration of at least one
microsecond, with no default),
`refundStatus` (default `succeeded`), `apiVersion` (the account's default API version, see "API Versions"),
`webhookSecret` (`whsec_`), `webhookSignature` (`VALID` or `CORRUPTED`) and `webhookTimestampAge` (ISO-8601).

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
| Versioning, API versions, changelog, upgrades         | https://docs.stripe.com/api/versioning.md, https://docs.stripe.com/api-versions.md, https://docs.stripe.com/changelog.md, https://docs.stripe.com/upgrades.md | fetched 2026-10-31                                  |
| Webhook versioning                                    | https://docs.stripe.com/webhooks/versioning.md                        | fetched 2026-10-31                                                                              |
| Error handling                                        | https://docs.stripe.com/error-handling                                | "Catch exceptions", "Types of error and responses", "Payment errors"                            |
| Payments without card authentication                  | https://docs.stripe.com/payments/without-card-authentication          | "Make a payment [Server-side]"                                                                  |
| Save and reuse                                        | https://docs.stripe.com/payments/save-and-reuse                       | "Setup Intents API"                                                                             |
| Changelog entries on payment method selection         | https://docs.stripe.com/changelog/endive/2026-09-30/removes-the-payment-method-types-parameter-from-payment-intents-and-setup-intents.md, https://docs.stripe.com/changelog/dahlia/2026-07-29/allowed-payment-method-types-parameter.md, https://docs.stripe.com/changelog/2023-08-16/automatic-payment-methods.md | fetched 2026-10-31 |
| Payment method support, PayPal                        | https://docs.stripe.com/payments/payment-methods/payment-method-support.md, https://docs.stripe.com/payments/paypal.md | "Country and currency support", "Additional API support"; "Payment method properties", fetched 2026-10-31 |
| PaymentIntent and SetupIntent create, confirm, object, per version | https://docs.stripe.com/api/payment_intents/create.md?api-version=2026-10-28.endive and the same pages at `2026-08-26.dahlia` | fetched 2026-10-31 |
| Stripe's OpenAPI specification                       | https://github.com/stripe/openapi, at `2026-07-29.dahlia`, `2026-08-26.dahlia`, `2026-09-30.endive` and `2026-10-28.endive` | the request parameters and enums of the four intent operations |
| Stripe API versioning                                 | https://stripe.com/blog/api-versioning                                | "version change modules"                                                                        |
| Expanding responses                                   | https://docs.stripe.com/api/expanding_objects.md, https://docs.stripe.com/expand.md | "Expanding multiple levels", "Expanding properties in lists"; fetched 2026-10-31            |
| Stripe api-discuss, "Expanding objects when listing Events" | https://groups.google.com/a/lists.stripe.com/g/api-discuss/c/GN--PqYSzQs | four error messages a developer quoted, and Stripe's answer; fetched 2026-10-31             |
| Reports of expansion errors                           | https://forum.adalo.com/t/stripe-subscription-component-error/56346, https://github.com/stripe/stripe-python/issues/316, https://github.com/stripe/stripe-node/issues/1007 | the error messages quoted; fetched 2026-10-31 |
| Charge and Customer objects                           | https://docs.stripe.com/api/charges/object.md, https://docs.stripe.com/api/customers/object.md | the includable properties; fetched 2026-10-31                                      |
| Charges no longer auto-expand refunds                 | https://docs.stripe.com/changelog/2022-11-15/deprecates-charges-auto-expand.md | fetched 2026-10-31                                                                    |

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
- The curl examples send a list as `expand[]=customer`, once per element (example). The official Java client numbers
  each element instead, `expand[0]=customer&expand[1]=...`, sends an empty list as `expand=`, and writes the brackets
  unescaped in a query string (example: stripe-java 33.4.2, `com.stripe.net.FormEncoder`,
  https://github.com/stripe/stripe-java/blob/v33.4.2/src/main/java/com/stripe/net/FormEncoder.java). Stripe publishes
  no grammar beyond these samples, and no rule for a form that breaks it.

| Form grammar (decision, `StripeFormGrammar`, `StripeForm`)                                                                                    | Reason                                                                                   |
|-----------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| A name is a word followed by bracketed keys: `amount`, `metadata[order_id]`, `card[exp][month]`, `expand[]`                                    | the only shape any sample or official client sends                                       |
| `name[]=v` appends `v` to a list, in the order sent. `name[0]=v` addresses key `0` of a hash                                                   | `metadata[0]` is a hash key, so an index only becomes one when the reader asks for a list |
| A list is read from `name[]` repeated, or from a hash whose keys are exactly `0` to `n-1`, in index order whatever the order sent. An empty `name=` and an absent name read as no elements | the docs and the client must reach the same answer                                       |
| A list of hashes is sent as `name[0][key]`. `[]` followed by another key is refused                                                            | `a[][b]` does not say which element `b` belongs to                                       |
| A list read from anything else (a non-empty value, a gap, an index spelled `01`, a key that is not an index) is refused: `400`, `invalid_request_error`, no `code`, `param` the list's name, by `StripeErrors.unreadableParameter` | guessing an order or an element would answer a request nobody sent                       |
| A name that is not a word and bracketed keys (`a[b`, `a]`, `]a`, `[a]`, `a[[b]`, an empty name), one name in two shapes (`x=1&x[k]=2`, `x[]=1&x[0]=2`), and one value sent twice (`x=1&x=2`, `x[k]=1&x[k]=2`) refuse the whole request: `400`, `invalid_request_error`, no `code`, `param` the path where the form stopped being readable (none for an empty name), by `StripeErrors.unreadableEncoding` | no reading of such a form is the one its client meant                                    |
| The body and the query are both decoded, on every route, after the key is authenticated and before the `Idempotency-Key` is claimed. Neither the refusal nor its key is stored | a validation failure is not cached ("Idempotency")                                        |
| A value read as one (`amount`) is read only from a plain name. A hash or list under that name reads as absent                                   | a hash or a list carries no single value to read                                          |
| The trail records every name exactly as the client spelled it                                                                                 | the trail shows what was sent, not what was understood                                    |
| `[` and `]` are accepted unescaped in a query string (`server.tomcat.relaxed-query-chars`)                                                     | the servlet container refuses them by default, and the Java client sends them             |

## API Versions

| Fact                                                                                                                                                       | Evidence                                                                                                         |
|------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| A version is a date and, from `2024-09-30.acacia` on, a major's codename: `2026-10-28.endive`. Older ones are dates only, `2011-06-21` to `2024-06-20`         | rule, https://docs.stripe.com/changelog.md                                                                       |
| Breaking changes ship only in a major's first release. Monthly releases add and break nothing                                                              | rule, https://docs.stripe.com/api-versions.md                                                                    |
| "Requests made with curl use your Stripe account's default API version ... unless you override it by setting the `Stripe-Version` header". An SDK sends the version it pins as that header | rule, https://docs.stripe.com/api/versioning.md                                                  |
| Events use the endpoint's version if one was set at creation, else the account's default                                                                   | rule, https://docs.stripe.com/webhooks/versioning.md                                                              |
| A response carries `stripe-version` naming the version it was answered at, on `200`, `400`, `402` and `404`, and not on a `401` for an invalid key            | probe: 3734 of the 3745 responses in the recordings; the two invalid-key `401`s carry none                          |
| A version can carry a beta: `2020-08-27; vipps_preview=v1`, echoed back as sent                                                                             | probe: the recordings                                                                                            |
| stripe-ios pins `2020-08-27` (`STPAPIClient.swift`) and stripe-android pins `2020-03-02` (`ApiVersion.kt`), and both send it as `Stripe-Version` on their API calls (`STPAPIClient.swift`, `RequestHeadersFactory.kt`). stripe-ios sends it on `GET /v1/customers/{id}` | example: SDK sources, and the recordings (`get_v1_customers_*`) |
| The answer to an unknown or unsupported version is unpublished                                                                                             | none found on docs.stripe.com. stripe-mock's opt-in version check answers `400` `invalid_request_error`, no code (a mock) |

The catalogue is `StripeApiVersions` and `StripeApiMajor`: every release the changelog heads, including the dated
`.preview` ones.

| Simulator behaviour                                                                                                                                       | Evidence and reason                                                                                              |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Answered: every release of basil, clover, dahlia and endive, `2025-03-31.basil` to `2026-10-28.endive`                                                    | inference: basil's partial capture and cancel create no Refund and `cancellation_reason=expired`, which is how the simulator behaves; no clover or dahlia change touches the modelled surface. `2026-09-30.endive` removes writable `payment_method_types` from PaymentIntent create, update and confirm and SetupIntent create and update, answering `400` `payment_method_types_no_longer_supported` (rule, https://docs.stripe.com/changelog/endive/2026-09-30/removes-the-payment-method-types-parameter-from-payment-intents-and-setup-intents.md); `2026-08-26.dahlia` still accepts it (stripe/openapi spec at that version). This simulator does not yet answer that refusal at endive, and is modelling it separately |
| The version is read from `Stripe-Version`, the name matched in any case, the first non-blank value, trimmed. Without one, the account's default `apiVersion` | rule for the order, decision for the reading                                                                     |
| `apiVersion` left empty gives the account `2026-10-28.endive`, the newest answered, which is stored and kept                                               | decision: Stripe publishes no default for a new account; the newest is what current guides and SDKs pin, and keeping it means the account does not move when the catalogue grows |
| Accounts stored before `apiVersion` existed hold `2026-10-28.endive` (`V92__stripe_account_names_its_default_api_version.sql`)                              | decision: the same as an empty field                                                                             |
| `stripe_account_api_version_is_a_release_of_a_major` refuses a stored value that is not `date.codename`, or that is a `.preview`                             | decision                                                                                                         |
| A published version outside the range (acacia, a date-only one, a preview): `400`, `invalid_request_error`, no `code`, no `param`, "Stripe-Version {sent} is a published Stripe API version this simulator does not answer. It answers 2025-03-31.basil through 2026-10-28.endive, ..." (`StripeErrors.versionNotAnswered`) | decision: Stripe would answer it; the simulator cannot claim its behaviour at those versions, so it refuses rather than answering wrongly |
| A published version followed by `;` and a beta: the same status and type, "... asks for a beta, and this simulator answers no beta" (`StripeErrors.versionAsksForABeta`) | decision: no beta is modelled                                                                                     |
| A value shaped `{date}.{codename}`, a real calendar date after `2026-10-28`: the same status and type, "Stripe-Version {sent} is dated after 2026-10-28.endive, the newest Stripe API version this simulator was built with, so it may be a release Stripe published since. ..." (`StripeErrors.versionLaterThanTheCatalogue`) | decision: the catalogue is closed at build time, and calling a release Stripe published since then invalid would tell the client something false |
| Any other value: the same status and type, "Invalid Stripe API version: {sent}" (`StripeErrors.versionUnrecognised`). Values are compared exactly, so `2026-08-26.DAHLIA` is one | decision: the text is the one a third-party report quotes from the API (https://wordpress.org/support/topic/invalid-stripe-api-version-yith_wcstripe_api_version/), not a vendor source |
| A request authenticated by an ephemeral key, and `POST /v1/ephemeral_keys` under a secret key, may also send `2020-03-02` or `2020-08-27`, and is answered at it. Every other secret-key route refuses them | decision: the mobile SDKs pin those and send them on the two customer reads an ephemeral key reaches, and a backend mints the key at its mobile client's version (stripe-java `EphemeralKeyCreateParams`: "Set this to the API Version of your mobile client"; stripe-android `EphemeralKeyManager` hands its backend `ApiVersion.get().code`); the simulator's customer, PaymentMethod and ephemeral key fields are the same at both |
| The version is checked after authentication and before the form and the `Idempotency-Key` claim. A version refusal claims no key, and a form refusal carries the version it was answered at | decision, as for the form grammar |
| Every answer of an account carries `Stripe-Version`: the version it was answered at. A `401` and a version refusal carry none                            | probe (the recordings)                                                                                           |
| An event's `api_version` is the account's default when the simulator schedules the event, and every attempt sends the same body. An event owed by a pending outcome is scheduled when the outcome is, so it carries the default of that moment, not of its later `created` | rule for the default; decision for the moment: the body is built once, when scheduled, and this simulator has no webhook endpoint objects, so no endpoint names a version |

Version changes (`StripeVersionChanges`, `StripeVersionChange`) follow Stripe's published design, "version change
modules": each breaking change declares the release that shipped it, its changelog entry, the operations it touches and
the transformation that turns a request of the release before into one of the release that shipped it (rule,
https://stripe.com/blog/api-versioning).

| Simulator behaviour                                                                                                                                      | Evidence and reason                                                                                     |
|----------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| The operations implement the newest release answered. No operation receives the version: `StripeApi` hands it the request at that release (`StripeCoreRequest`) | rule (the blog: the core is the current version); decision for enforcing it in the signature            |
| The changes a request takes are those found walking back from the newest release to the request's release, the request's own release excluded. A version older than every release answered, such as a mobile SDK's pinned `2020-08-27`, takes every change | rule (the blog: "walk back") |
| A request passes its changes oldest first, each touching only the operations it declares                                                                 | decision: each transformation reads the shape of the release before it                                   |
| A change is declared only at a release this simulator answers, and touches at least one operation                                                         | decision                                                                                                 |
| A field or parameter a monthly release added is read and answered at every release, older ones included: `allowed_payment_method_types` and `excluded_payment_method_types` with their newest enums | decision: Stripe publishes no per-version gating of additive changes and calls adding a field safe       |
| Responses are the same at every release: no modelled change alters a response                                                                            | rule: the one change keeps `payment_method_types` on the object                                          |

| Change                                         | Release              | Operations                                                                         | Transformation                                                                                           | Changelog                                                                                                                                         |
|------------------------------------------------|----------------------|------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `payment_method_types` is no longer writable   | `2026-09-30.endive`  | `POST /v1/payment_intents`, `POST /v1/payment_intents/{id}/confirm`, `POST /v1/setup_intents` | an older request's `payment_method_types` leaves the form and reaches the core as the list it wrote; the core refuses the parameter on the form | https://docs.stripe.com/changelog/endive/2026-09-30/removes-the-payment-method-types-parameter-from-payment-intents-and-setup-intents |

The same removal is filed under `2026-08-26.preview` (https://docs.stripe.com/changelog/dahlia/2026-08-26/removes-payment-method-types-parameter-from-payment-intents-setup-intents),
a preview release this simulator does not answer; `2026-08-26.dahlia` still lists the parameter (rule: Stripe's
OpenAPI specification, https://github.com/stripe/openapi, at that release).

## Payment Method Selection

| Fact                                                                                                                                                                         | Evidence                                                                                                                         |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| Up to `2026-08-26.dahlia`, `payment_method_types` is a parameter of PaymentIntent create and confirm and SetupIntent create, a list of strings with no enum, and not one of SetupIntent confirm | rule: the OpenAPI specification at `2026-07-29.dahlia` and `2026-08-26.dahlia`                                                   |
| `payment_method_types` "returns an error when you specify an incompatible payment method"                                                                                  | rule, https://docs.stripe.com/changelog/dahlia/2026-07-29/allowed-payment-method-types-parameter                                   |
| `2026-09-30.endive` removes it from those operations (and update): "Passing `payment_method_types` on this API version returns a 400 error with the code `payment_method_types_no_longer_supported`". It "remains as a read-only property ... reflecting the computed list" | rule, the endive changelog entry above                                                                                           |
| `allowed_payment_method_types` (from `2026-07-29.dahlia`, on PaymentIntent create and confirm and SetupIntent create and confirm) "silently filters out incompatible methods", is reflected in `payment_method_types`, and "is mutually exclusive with `payment_method_types`, `excluded_payment_method_types`, `automatic_payment_methods`, and `payment_method_configuration`" | rule, the `2026-07-29` entry. The error for a combination is not stated there                                                     |
| `parameters_exclusive`: "Two or more mutually exclusive parameters were provided."                                                                                           | rule, https://docs.stripe.com/error-codes                                                                                         |
| `excluded_payment_method_types` is a parameter of PaymentIntent create and confirm and SetupIntent create; `automatic_payment_methods` (`enabled` required, `allow_redirects` `always` or `never`) and `payment_method_configuration` (at most 100 characters) of the two creates | rule: the OpenAPI specification at `2026-10-28.endive`, and https://docs.stripe.com/api/payment_intents/create, https://docs.stripe.com/api/setup_intents/create |
| `allowed_payment_method_types` and `excluded_payment_method_types` are enums of 99 and 55 published values                                                                   | rule: the OpenAPI specification at `2026-10-28.endive`                                                                            |
| Without a selection, automatic payment methods are enabled: "The previous default was to accept only card payments when both `payment_method_types` and `automatic_payment_methods` weren't specified" | rule, https://docs.stripe.com/changelog/2023-08-16/automatic-payment-methods                                                       |
| `allow_redirects=never` filters payment methods that require a redirect. PayPal requires one; a card does not                                                                | rule, the 2023-08-16 entry and https://docs.stripe.com/payments/payment-methods/payment-method-support ("Requires redirect")       |
| Cards present in "Most currencies". PayPal presents in AUD, CAD, CHF, CZK, DKK, EUR, GBP, HKD, NOK, NZD, PLN, SEK, SGD and USD. Both support PaymentIntents, SetupIntents and manual capture | rule, https://docs.stripe.com/payments/payment-methods/payment-method-support and https://docs.stripe.com/payments/paypal          |
| The object carries `allowed_payment_method_types`, `automatic_payment_methods` (`allow_redirects`, `enabled`), `excluded_payment_method_types`, `payment_method_configuration_details` (`id`, `parent`) and `payment_method_types` | rule, https://docs.stripe.com/api/payment_intents/object, https://docs.stripe.com/api/setup_intents/object                         |
| An enum value outside the published list: `400`, `invalid_request_error`, no `code`, `param` the path, "Invalid payment_method_options[card][network]: must be one of amex, ..., or unknown" | probe: the recordings (`testConfirmCardWithInvalidNetworkParam`)                                                                    |
| A type the deferred SetupIntent cannot use: `400`, `invalid_request_error`, no `code`, "The payment method type `foo` is not supported when creating a SetupIntent."           | probe: the recordings (`testPaymentSheetLoadDeferredIntentFails`, `GET /v1/elements/sessions`)                                     |
| `payment_intent_incompatible_payment_method`: "The PaymentIntent expected a payment method with different properties than what was provided." `setup_intent_invalid_parameter` and `payment_intent_invalid_parameter`: "One or more provided parameters wasn't allowed for the given operation" on the intent | rule, https://docs.stripe.com/error-codes                                                                                         |

What a request selects (`StripeSelections`), per operation:

| Operation                         | Reads                                                                                                   |
|-----------------------------------|---------------------------------------------------------------------------------------------------------|
| PaymentIntent and SetupIntent create | `automatic_payment_methods`, `allowed_payment_method_types`, `excluded_payment_method_types`, `payment_method_configuration`, and `payment_method_types` before `2026-09-30.endive` |
| PaymentIntent confirm             | `allowed_payment_method_types`, `excluded_payment_method_types`, and `payment_method_types` before `2026-09-30.endive` |
| SetupIntent confirm               | `allowed_payment_method_types`. `payment_method_types` is not read at any release, as it never was a parameter there |

| Simulator behaviour                                                                                                                                                         | Evidence and reason                                                                                                   |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| The types a selection can offer are the ones this simulator models, `card` and `paypal` (`StripePaymentMethodType`). The account's Dashboard settings are taken as enabling both | decision: no Dashboard is modelled, and a type no fixture can stand for could never be confirmed                       |
| A type is compatible unless a PaymentIntent's currency is one PayPal does not present in, or `allow_redirects=never` meets PayPal. A SetupIntent has no currency               | rule for both facts; decision that nothing else (country, amount, customer) is modelled                               |
| No selection, `automatic_payment_methods[enabled]=true`, `excluded_payment_method_types` or `payment_method_configuration`: dynamic. It offers every compatible modelled type not excluded, in the order `card`, `paypal`, and the object shows `automatic_payment_methods` `enabled: true` | rule for the default; decision for the order                                                                          |
| `allow_redirects` is answered as sent, `null` when the request sent none                                                                                                    | decision: the object's field is nullable and Stripe's sample omits it                                                  |
| `automatic_payment_methods[enabled]=false` alone offers `card`                                                                                                              | rule: Stripe's default before dynamic payment methods                                                                 |
| `allowed_payment_method_types` offers the modelled, compatible types of the list, in the order sent, and drops the rest silently, a published type this simulator does not model included. The object shows the list as sent, repeats removed | rule for the filter; decision for the order and for an unmodelled type                                                |
| `payment_method_types` offers the list in the order sent, repeats removed. A type not modelled, not published or not compatible refuses the request: `400`, `invalid_request_error`, no `code`, `param` `payment_method_types`, "The payment method type `{type}` is not supported when creating a PaymentIntent." (or "confirming a PaymentIntent", "creating a SetupIntent") | rule that it errors; the shape is the recorded one                                                                    |
| From `2026-09-30.endive`, `payment_method_types` on those three operations: `400`, `invalid_request_error`, `payment_method_types_no_longer_supported`, `param` `payment_method_types`, in any shape it is sent | rule for status and code; decision for `param` and the message. `doc_url` follows the code like every other, although the error-codes page has no entry for it |
| A selection that leaves no type: `400`, `invalid_request_error`, `payment_intent_invalid_parameter` or `setup_intent_invalid_parameter`, `param` the parameter that emptied it | decision: an intent offering nothing could never be confirmed                                                          |
| An unpublished value in `allowed_payment_method_types` or `excluded_payment_method_types`: `400`, `invalid_request_error`, no `code`, "Invalid {name}[{index}]: must be one of {the published values}", `param` `{name}[{index}]`. The same shape refuses `automatic_payment_methods[allow_redirects]` | probe for the shape; decision for the indexed `param`                                                                  |
| `automatic_payment_methods` without `enabled`: `parameter_missing`, `param` `automatic_payment_methods[enabled]`. An `enabled` that is not `true` or `false`, a parameter that is not a hash, and a `payment_method_configuration` that is empty or longer than 100 characters are refused with no `code`, by `StripeErrors.unreadableParameter` | rule for the shapes; decision for the answers                                                                         |
| `payment_method_configuration` is accepted without being looked up: it selects dynamically and is answered as `payment_method_configuration_details` `{"id": sent, "parent": null}`. Without one, the details are `null` | decision: no configuration objects are modelled, and refusing every id would refuse every integration that uses one |
| A selection is stored on the intent (`V93__stripe_intents_carry_their_payment_method_selection.sql`). Intents stored before keep the list they were answered with, `["card"]` for a PaymentIntent and `["card", "paypal"]` for a SetupIntent, and read as dynamic | decision: a list already answered does not change; neither intent was created with a selection                       |

Mutual exclusion (`400`, `invalid_request_error`, `parameters_exclusive`, "You may only specify one of these parameters:
{param}, {other}.", `param` the first named):

| Refused together                                                                                       | Evidence and reason                                                                                                         |
|--------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `allowed_payment_method_types` and any of `payment_method_types`, `excluded_payment_method_types`, `automatic_payment_methods`, `payment_method_configuration` | rule for the exclusion and the code; decision for the message and `param`                                                   |
| `payment_method_types` and `automatic_payment_methods[enabled]=true`                                   | decision: one fixes the list, the other computes it. A third-party report says Stripe refuses the pair (https://github.com/laravel/cashier-stripe/pull/1345) |
| `excluded_payment_method_types` or `payment_method_configuration` beside `payment_method_types` or `automatic_payment_methods[enabled]=false` | decision: both narrow or choose the dynamic set, which these switch off                                                      |
| On confirm, `excluded_payment_method_types` sent to an intent whose selection is not dynamic, named against the parameter that chose it | decision                                                                                                                    |

`payment_method_types` and `automatic_payment_methods[enabled]=false` together are accepted.

On confirm, `allowed_payment_method_types` or `payment_method_types` replaces the intent's selection,
`excluded_payment_method_types` recomputes a dynamic one, and a confirm sending none keeps it. The selection is stored
only with the confirmation's own state change: a refused confirm changes nothing.

Confirming a payment method this account holds whose type the selection does not offer (decision: Stripe publishes no
answer):

| Operation                                       | Answer                                                                                                                                                                         |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| PaymentIntent confirm, and create with `confirm=true` | `400`, `invalid_request_error`, `payment_intent_incompatible_payment_method`, `param` `payment_method`, "The PaymentMethod provided ({type}) is not allowed for this PaymentIntent. Attach a PaymentMethod of one of the following types: {offered}." Confirm embeds the intent as stored; create mints nothing |
| SetupIntent confirm                             | `400`, `invalid_request_error`, `setup_intent_invalid_parameter`, the same sentence with SetupIntent, and the truncated SetupIntent. Nothing is attached                        |

The code is the published one whose description fits ("expected a payment method with different properties"); a
SetupIntent has none of its own. A payment method this account does not hold is not type-checked, as before: its type
is unknown.

## Expansion

| Fact                                                                                                                                                                         | Evidence                                                                                                       |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `expand` "is available on all API requests, and applies to the response of that request only", "on any endpoint that returns expandable fields, including list, create, and update endpoints" | rule, https://docs.stripe.com/api/expanding_objects.md                                                         |
| "You can expand recursively by specifying nested fields after a dot (`.`)"                                                                                                   | rule, the same page                                                                                            |
| "Expansions have a maximum depth of four levels. Meaning that an `expand` string can contain no more than four properties". The deepest listed example, `data.payment_intent.customer.default_source`, counts `data` as one of the four | rule, https://docs.stripe.com/expand.md and https://docs.stripe.com/api/expanding_objects.md                   |
| "You can use the `data` keyword in any position in an expand string to move the expand cursor into the list"                                                                 | rule, https://docs.stripe.com/expand.md                                                                        |
| "In some cases, resources have properties that aren't included by default", requested with `expand` ("Using expansion to request includable properties")                    | rule, https://docs.stripe.com/expand.md                                                                        |
| Includable, "not returned by default; request it with the `expand` request parameter": the Charge's `refunds`, and the Customer's `cash_balance`, `invoice_credit_balance`, `sources`, `subscriptions`, `tax` and `tax_ids`. `invoice_credit_balance` is in no `x-expandableFields` list | rule, https://docs.stripe.com/api/charges/object.md and https://docs.stripe.com/api/customers/object.md        |
| "The `Charge` object no longer auto-expands refunds" from `2022-11-15`; customer `sources`, `subscriptions` and `tax_ids` stopped being auto-expanded at `2020-08-27`, before every version this simulator answers | rule, https://docs.stripe.com/changelog.md                                                                     |
| Each object lists its expandable fields in `x-expandableFields`; a relation is `anyOf` an id string and the object, and `customer` also the `deleted_customer` shape. `refunds` is a nullable `RefundList`: `object` `list`, `data`, `has_more`, `url` | rule: the OpenAPI specification at `2026-10-28.endive`                                                         |
| `expand` is a query parameter of every GET and a body parameter of every POST served here. `DELETE /v1/customers/{id}` has none, and the ephemeral key publishes no expandable field | rule: the same specification                                                                                   |
| "This property cannot be expanded because it doesn't exist: payment_intent", on a Subscription create, 2025                                                                 | probe: a developer's report on the Adalo forum (Sources). No status, `type` or `param` is quoted                |
| On a list: "This property cannot be expanded (application). You may want to try expanding 'data.application' instead." (Charges, 2017), the same form for `tiers` on Plans (2020) | probe: developers' reports in stripe-python #316 and stripe-node #1007 (Sources)                              |
| On Events, which "don't support the Expand feature": "This property cannot be expanded (customer). You may want to try expanding 'data.customer' instead.", and "This property cannot be expanded (data.object)" for `data.object.customer` | probe: a developer's report on Stripe's api-discuss list, 2017, and a Stripe answer in the same thread (Sources) |
| Stripe's iOS SDK retrieves and confirms an intent with `expand[0]=payment_method`, and a declined confirm answers the error's `payment_intent` with `payment_method` `null` | probe: the recordings (`testDeferredConfirmnewinsufficientfundscard`, `testDeferredConfirmvalidsavedcard`)     |
| With a publishable key and a `client_secret`, `GET /v1/payment_intents/{id}?expand[0]=metadata` answers `200`, and the answer carries no `metadata` at all              | probe: the recordings (`StripeAPIBridgeNetworkTests/testRetrievePaymentIntent`, 2025)                          |

What the evidence settles, by the kind of path:

| Path                                                                 | Observed                                                                                     | Answered here                                                    |
|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| a property the object does not have                                  | refused, "... because it doesn't exist: {path}" (secret key, 2025)                           | refused with that message                                        |
| on a list, a property other than `data`                              | refused with the `data.` suggestion (secret key, 2017 and 2020)                              | refused with that message                                        |
| any path on an object that expands nothing                           | refused, "This property cannot be expanded ({path})." (Events, 2017)                        | refused with that message (the ephemeral key)                    |
| a property the object has and does not publish as expandable         | ignored with `200` (publishable key, 2025). No secret-key answer is recorded                | ignored with `200`, at either key                                |

The last row is a decision: the only observation on a PaymentIntent ignores the path, and an integration Stripe answers
must not be refused here. The error messages are the newest observed for each kind of path.

The fields served (`StripeExpandable`):

| Object         | Field                    | Answered                                                                                     | Answered by          |
|----------------|--------------------------|----------------------------------------------------------------------------------------------|----------------------|
| PaymentIntent  | `customer`               | the customer, or the deleted customer                                                        | `StripeCustomerView` |
| PaymentIntent  | `payment_method`         | the payment method                                                                           | `StripePaymentMethodView` |
| PaymentIntent  | `latest_charge`          | the charge                                                                                   | `StripeChargeView`   |
| SetupIntent    | `customer`               | the customer, or the deleted customer                                                        | `StripeCustomerView` |
| SetupIntent    | `payment_method`         | the payment method                                                                           | `StripePaymentMethodView` |
| PaymentMethod  | `customer`               | the customer                                                                                 | `StripeCustomerView` |
| Charge         | `customer`               | the customer of its PaymentIntent, or the deleted customer                                   | `StripeCustomerView` |
| Charge         | `payment_intent`         | the PaymentIntent                                                                            | `StripeIntentView`   |
| Charge         | `refunds` (included)     | a list of the charge's refunds, newest first, `has_more` `false`, `url` `/v1/charges/{id}/refunds`; `refunds.data.` reaches each refund | `StripeRefundView` |
| Refund         | `charge`                 | the charge                                                                                   | `StripeChargeView`   |
| Refund         | `payment_intent`         | the PaymentIntent                                                                            | `StripeIntentView`   |
| Customer       | `sources`, `subscriptions`, `tax_ids` (included) | an empty list, `has_more` `false`, `url` `/v1/customers/{id}/{property}`             | `StripeRelatedObjects` |
| Customer       | `cash_balance` (included) | `null`                                                                                      | `StripeRelatedObjects` |
| Customer       | `invoice_credit_balance` (included) | `{}`                                                                              | `StripeRelatedObjects` |
| Customer       | `tax` (included)         | `automatic_tax` `unrecognized_location`, `ip_address` `null`, `location` `null`, `provider` `stripe` | `StripeRelatedObjects` |
| list           | `data`                   | each listed payment method, with the paths after `data.`                                     | `StripePaymentMethodView` |

| Simulator behaviour                                                                                                                                                         | Evidence and reason                                                                                                   |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Expansion is applied to the answer of PaymentIntent create, retrieve, confirm, capture and cancel, SetupIntent create and confirm, PaymentMethod retrieve, list, customer list and detach, Charge retrieve, Refund create, Customer create, retrieve and update, and ephemeral key create | rule                                                                                                                  |
| A GET reads `expand` from its query and a POST from its body; neither reads the other place                                                                                | rule for where it is published; decision that the other place is not read                                             |
| Without `expand`, or with an empty `expand=`, every relation is answered as its id and no includable property is answered. An unset relation stays `null` when it is asked to be expanded | rule: the relations are nullable `anyOf` an id and the object, and the includables are "not returned by default"     |
| A nested path expands the object inside the object it expanded, to four levels, `data` included                                                                             | rule                                                                                                                  |
| Every path is judged before the endpoint runs: a refused one refuses the request and nothing is created, changed or looked up. Several paths sharing a prefix are merged    | decision: a refusal on the parameters names no object                                                                 |
| A property is judged against the object's properties in the specification at `2026-10-28.endive`. A refusal is `400`, `invalid_request_error`, no `code`, no `param`, with the messages above; the path in it ends at the property refused. The suggestion inserts `data` where the list was met: `refunds.charge` suggests `refunds.data.charge` | probe for the messages; decision for the status, the type and the absent `param`, which no report shows            |
| On a list, `object`, `has_more` and `url` are accepted and expand nothing                                                                                                    | decision: they are properties the list has, judged like any other                                                     |
| More than four levels: `400`, `invalid_request_error`, no `code`, no `param`, "This property cannot be expanded ({path}). Expansions have a maximum depth of four levels." | rule for the limit; decision for the answer: no Stripe answer is published or recorded                                 |
| A property the object has that this simulator holds no relation or includable under (`amount`, `metadata`, `review`, `card`, `customer.test_clock`) is accepted and changes nothing; the segments after it are not judged. Where this simulator's object omits such a property (a Refund's `customer` and `payment_method`, a Charge's `balance_transaction`) it stays absent | decision: the table above; the object's own field list is narrower than Stripe's |
| Properties are judged against `2026-10-28.endive` at every version answered. `payment_record` on a PaymentIntent is absent from both `dahlia` specifications, `paypay` on a PaymentMethod from both, and `sequra` from `2026-07-29.dahlia` only; each is accepted at those versions too. No specification older than `2026-07-29.dahlia` was compared | decision: operations do not branch on the version, and none of them expands anything here |
| A relation is expanded only to an object of the account that answers and of the run the request resolved to; an id of another account or run is answered as the id. A Charge's refunds are those of the same account and run | decision: the same scoping as every lookup (`StripeLookup`, `StripeCustomerLookup`, `StripePaymentMethodLookup`, `StripeChargeLookup`) |
| A relation naming an object this run does not hold, such as a published `pm_card_*` this simulator never mints into a PaymentMethod, or a method a fixture removal forgot, is answered as the id | decision: inventing an object or answering `null` would both misstate a relation that is set                          |
| A deleted customer expands into the deleted customer object: `id`, `object` `customer`, `deleted` `true`, and includes nothing                                            | rule: `customer` is `anyOf` the `deleted_customer` shape, which has no includable property                            |
| The Customer's includables are answered empty: no list holds an item, `cash_balance` is `null` and `tax` names no location                                                 | decision: this simulator holds no source, subscription, tax id, cash balance, credit balance, address or IP address; `cash_balance` is nullable, `invoice_credit_balance` is a map of "balances, if any", and `provider` "defaults to `stripe`" |
| `refunds` answers every refund of the charge with `has_more` `false`                                                                                                        | decision: this build serves no `/v1/charges/{id}/refunds` route a later page could be read from                       |
| An object named twice in one answer, such as one customer behind several listed methods, is read once                                                                       | decision: the answer describes one moment, and a list has no paging bound                                             |
| Only a successful answer is expanded. The objects inside an error body (a decline's or a refusal's `payment_intent` or `setup_intent`) are not expanded                    | decision: the recordings cannot settle it, as their declined intent carries `payment_method` `null`, and `expand` applies to "the response" the request asked for |
| An expanded object carries what the object itself carries, `client_secret` included. The trail and the keyed-exchange log withhold `client_secret` and `secret` at any depth | decision: `StripeFields.withholding` matches the last segment of a path                                               |
| An idempotent replay answers the expanded body the first request stored. A repeat with another `expand` is refused as a reuse with other parameters                         | rule: `expand` is a parameter like any other under the key                                                            |
| `expand` on an object answered by the control surface (origination, object actions) is not read                                                                            | decision: those requests are built by this simulator and carry no `expand`                                            |

## Idempotency

| Rule (published)                                                                                                                                                  | Simulator                                                   |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| Header `Idempotency-Key`, up to 255 characters, V4 UUIDs suggested                                                                                                | a longer key is `400` `invalid_request_error`, no `code`    |
| "saving the resulting status code and body of the first request ... regardless of whether it succeeds or fails ... including `500` errors"                        | replays the stored status and body                          |
| "keys expire out of the system after 24 hours". Keys may be pruned once "at least 24 hours old", and "We generate a new request if a key is reused after the original is pruned." | 24-hour window                                              |
| a replay carries `Idempotent-Replayed: true`                                                                                                                      | same                                                        |
| a reused key with a different endpoint or parameters: `idempotency_error`                                                                                         | `400`, `idempotency_error`, no `code` (the status is a decision) |
| "same parameters" is not defined further                                                                                                                          | parameters are compared after decoding, as a path and a value each, ignoring the order of names (decision): `x[]=a&x[]=b` and `x[0]=a&x[1]=b` are the same parameters, `x[]=b&x[]=a` is not, because the client's encoding is not a parameter |
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
- `Stripe-Version` is a header, not a parameter. Every official server SDK refuses locally to create an ephemeral key
  without one (example: stripe-java 33.4.2 `EphemeralKeyService.create`, "`stripe-version` must be explicitly specified
  in `params` as the stripe version of your mobile client.", and stripe-node `EphemeralKeys.create`). What the API
  answers a request carrying none is unpublished, so the simulator answers it at the account's default like any other
  request (decision, see "API Versions"). A mint sending a version a mobile SDK pins, `2020-03-02` or `2020-08-27`,
  is answered at that version.
- The simulator serves only `customer`. With none, the answer is `400` `parameter_missing` on `customer`.
- The key authenticates `GET /v1/customers/{id}` and `GET /v1/customers/{id}/payment_methods` for its own customer
  only. Anything else is `401`, and so is an expired key.

### SetupIntents

Published:

- Create accepts `customer`, `usage` (`on_session`/`off_session`, "If not provided, this value defaults to
  `off_session`"), `payment_method`, metadata, and the selection parameters of "Payment Method Selection".
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
| The selection parameters are read as "Payment Method Selection" states. With none, `payment_method_types` is `["card", "paypal"]` |
| `client_secret` appears on the wire only. It is withheld from audit and screens, and no publishable-key surface exists                                              |
| Object: `id`, `object`, `allowed_payment_method_types`, `automatic_payment_methods`, `client_secret`, `created`, `customer`, `excluded_payment_method_types`, `last_setup_error`, `livemode`, `payment_method`, `payment_method_configuration_details`, `payment_method_types`, `status`, `usage` |
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
- `payment_method_types` on the object is "The list of payment method types (e.g. card) that this PaymentIntent is
  allowed to use". Which parameters set it, per release, is in "Payment Method Selection".

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
- `customer`, `payment_method` and `latest_charge` are expandable (see "Expansion").

The simulator's object: `id`, `object`, `allowed_payment_method_types`, `amount`, `amount_capturable`,
`amount_received`, `automatic_payment_methods`, `cancellation_reason`, `capture_method`, `client_secret`, `created`,
`currency`, `customer`, `excluded_payment_method_types`, `last_payment_error`, `latest_charge`, `livemode`,
`payment_method`, `payment_method_configuration_details`, `payment_method_types`, `receipt_email`, `status`.

`GET /v1/payment_intents/{id}`: current state, `latest_charge` after a confirmation attempt, and `resource_missing` for
an unknown id.

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
| Fields: `id`, `object`, `amount`, `amount_captured`, `amount_refunded`, `captured`, `currency`, `customer` (its PaymentIntent's), `paid`, `payment_intent`, `payment_method`, `refunded`, `status` |
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
| `customer` is expandable on retrieve, detach and both lists (`data.customer`), sent as `expand[]=...` or `expand[0]=...`. An `expand` that is not a list, or a path refused, is refused before the method is looked up, so an unknown method is refused on `expand` and not as `resource_missing` |
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
  "api_version": "2026-10-28.endive",
  "type": "payment_intent.succeeded",
  "created": 1788772800,
  "livemode": false,
  "pending_webhooks": 0,
  "request": {"id": null, "idempotency_key": null},
  "data": {"object": { "...": "the same PaymentIntent or Refund object the API returns" }}
}
```

`api_version` is the account's default version when the simulator schedules the event (see "API Versions").

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
| a form the grammar cannot read, or a list parameter that is not a list                  | `invalid_request_error` | 400  | decision. No code is published                                   | see "Authentication and Transport", form grammar                                                                            |
| a `Stripe-Version` this simulator does not answer, or one naming no published version   | `invalid_request_error` | 400  | decision. Stripe publishes no answer                             | see "API Versions"                                                                                                          |
| `payment_method_types_no_longer_supported`                                             | `invalid_request_error` | 400  | rule (the endive changelog entry)                                | see "Payment Method Selection"                                                                                              |
| `parameters_exclusive`, `payment_intent_invalid_parameter`, `setup_intent_invalid_parameter`, `payment_intent_incompatible_payment_method` | `invalid_request_error` | 400 | decision. The codes are published, their uses here are not | see "Payment Method Selection"                                                                                              |
| an enum value outside the published list                                                | `invalid_request_error` | 400  | probe. No code                                                   | "Invalid {param}: must be one of {values}"                                                                                  |

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

- Only the no-key request and the recordings, all made at `2020-08-27`, were observed against Stripe. Everything else
  is documentation.
- HTTP statuses are published for three cases only: `402` (a failed payment), `409` (a key conflict) and `401` (no
  authentication). The other statuses are inferences.
- Confirming with dynamic payment methods requires a `return_url` unless `off_session=true` or `allow_redirects=never`
  (rule, the 2023-08-16 entry). The simulator does not require one, because no type it models needs a redirect it
  serves.
- Compatibility is modelled by currency and redirects alone, over two types; Stripe also weighs amount, country,
  customer and the account's Dashboard settings.
- The ephemeral-key TTL comes from one example.
- The range of versions answered rests on an inventory of the breaking changes from acacia to endive, read once,
  and not on a per-version comparison of Stripe's specification. A request answered at a version outside the range
  is refused, while Stripe would answer it.
- PaymentSheet's first call, `v1/elements/sessions`, is in none of the 419 paths of Stripe's OpenAPI specification, so
  `stripe-mock` would answer `404` and this simulator stops at the ephemeral key. The SDK host can be moved on mobile
  only through non-public members, and on the web not at all.
- The answers to a refused `expand` path are quoted from developers' reports without a status or `param`, the answer to
  a path deeper than four levels is not published at all, and no secret-key answer to a property that exists and is not
  expandable is recorded.
- Transient failures after an effect (on capture and refund) are this project's fault model. Stripe publishes only
  "Treat requests that return `500` errors as indeterminate".
- No message is published for an unrecognized address or for a method an address does not answer. The simulator
  answers both `404` with `error.type` `invalid_request_error` and no `code`, because `405` is absent from the
  published status list and a code it did not publish would carry a `doc_url` to an anchor that does not exist; the
  `message` is the simulator's own sentence and names itself as such.
