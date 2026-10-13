# Grow / Meshulam Simulator Contract

## What This File Covers

The vendor contract for the Grow Light API as this simulator serves it: hosted card registration (`chargeType=3`),
pre-authorized hold (`chargeType=2`, J4/J5), stored-card charge, settlement, refund, the callback handshake and the
lookups. For each route it records what Grow publishes, what the simulator emits, the error codes with their Hebrew wire
texts, and where the simulator deviates.

Evidence classes, per `README.md`, "Evidence Classes":

- **rule**: prose or a table on a Grow page,
- **example**: a saved example on a Grow page or in the official Postman collection,
- **probe**: observed on 2026-09-15 against `https://sandbox.meshulam.co.il/api/light/server/1.0/` with credentials
  that match no business. It shows what Grow answers a caller it does not recognise, and nothing about a successful
  call. Re-confirm with real sandbox credentials before relying on it,
- **decision**: a choice made by this simulator,
- **inference**: read off the wording, stated nowhere.

Hebrew strings are wire values and are quoted verbatim. The English beside them is a gloss and is never emitted.

### Routes

All API routes are `POST /api/light/server/1.0/<name>` unless stated otherwise. Every answer is HTTP 200.

| Route                                                                   | Credential               | What the simulator does                                                                     | Key deviations and choices                                                                                                    |
|-------------------------------------------------------------------------|--------------------------|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `createPaymentProcess`                                                  | `userId` + `pageCode`    | creates a process (`chargeType` `2` or `3`) and returns `processId`, `processToken`, `url`  | amount judged first (`707`), then `54`, then `701` with the short wire text                                                   |
| `GET /far?l=<link>`, `POST /far`                                        | none                     | payer page: paid, declined or abandoned                                                     | the whole page is a decision, because Grow publishes only the URL                                                             |
| `createTransactionWithToken`                                            | `userId`                 | stored-card charge                                                                          | `paymentNum` required (probe), duplicates answered `712` (decision), declines from the published token table                  |
| `getTokenTransactionsByExternalIdentifiers` (trailing `/` accepted)     | `userId`                 | retraces a token charge by the caller's identifier, from the wire or the **Look a payment up** screen | the response shape is a decision, because Grow publishes none                                                                 |
| `approveTransaction`                                                    | `pageCode`               | acknowledges a callback                                                                     | every failure is `180`                                                                                                        |
| `GET updateMyUrl?url=` (trailing `/` accepted)                          | none                     | re-sends the newest callback already addressed to `url`                                     | refuses a url that no callback has been addressed to                                                                          |
| `settleSuspendedTransaction`                                            | `userId`                 | captures a hold (J4)                                                                        | ceiling `804` by default and `814` by setting, unknown transaction `813` (decision)                                            |
| `refundTransaction`                                                     | `userId` or `pageCode`   | refund, which creates a new transaction row                                                 | credential code set by what was sent (`801`, `716` or `724`), excess `105`/`190`/`218` by setting, allowance of 1 or 2 refunds |
| `getTransactionInfo`                                                    | `pageCode`               | transaction lookup                                                                          | `716` for every failure                                                                                                       |
| `getPaymentProcessInfo`                                                 | `pageCode`               | process lookup, with a zeroed row for an unpaid process                                     | unknown process `716` by default, `730` by setting                                                                            |
| `cancelBitTransaction`                                                  | `pageCode`               | always refuses with `730` (Bit is out of scope)                                             | only the Postman path is served, not the portal's `/api/light/andriod/1.0/...`                                                |

Account settings, set per account on the test-run configuration:

| Setting                   | Values (default first)                         | Governs                                                           |
|---------------------------|------------------------------------------------|-------------------------------------------------------------------|
| `wireForm`                | `LIVE_WIRE`, `PUBLISHED_EXAMPLE`               | the response content type and how Hebrew is escaped               |
| `callbackAcknowledgement` | `HTTP_2XX`, `APPROVE_TRANSACTION`              | what counts as acknowledging a callback                           |
| `settlementCeiling`       | `FRAME_CAPTURE` (`804`), `THIRTY_PERCENT_ABOVE` (`814`) | how far a settlement may exceed the hold                  |
| `refundAllowance`         | `ONE_REFUND`, `TWO_REFUNDS`                    | the number of refunds per transaction                             |
| `refundExcessRejection`   | `ORIGINAL_AMOUNT` (`105`), `CHARGE_AMOUNT` (`190`), `TRANSACTION_AMOUNT` (`218`) | the code for an excessive refund     |
| `merchantBalance`         | `AVAILABLE`, `EMPTY` (`115`)                   | whether a refund is funded                                        |
| `processLookupRejection`  | `PUBLISHED_EXAMPLE` (`716`), `PUBLISHED_TABLE` (`730`) | the code for an unknown process                           |
| `captureWindow`           | `P7D`                                          | when a settle is answered `803`                                   |
| `automaticVoidAfter`      | `P10D`                                         | when a hold becomes `CANCELLED`                                   |

The callback retry plan is the provider setting `webhook.retryPlan`, which defaults to `PT0S,PT1S,PT5S,PT30S,PT2M,PT5M`.

### Sources

| Topic                                                              | URL                                                                                                                                                 |
|--------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| Portal                                                             | https://developers.grow.business/                                                                                                                   |
| Payment process overview (redirect carries only `cField` values)   | https://developers.grow.business/reference/the-process                                                                                              |
| J4/J5 overview                                                     | https://developers.grow.business/reference/delayed-payment-overview                                                                                 |
| Create process, token only (`chargeType=3`)                        | https://developers.grow.business/reference/create-payment-process-6                                                                                 |
| Create process, J4/J5 (`chargeType=2`)                             | https://developers.grow.business/reference/create-payment-process-10                                                                                |
| Create process, response examples                                  | https://developers.grow.business/reference/delayed-payment-response                                                                                 |
| Payment link, and the second published `54` example               | https://developers.grow.business/reference/refund-overview-1                                                                                        |
| Token-only callback                                                | https://developers.grow.business/reference/token-server-response                                                                                    |
| J4/J5 callback                                                     | https://developers.grow.business/reference/delayed-payment-callback                                                                                 |
| Create transaction with token, and its responses                  | https://developers.grow.business/reference/create-transaction-with-token-4, https://developers.grow.business/reference/token-transactions-response   |
| Settle suspended transaction, and its responses (`819`)           | https://developers.grow.business/reference/settle-suspended-transaction, https://developers.grow.business/reference/delayed-payment-response-2       |
| Refund overview and refund transaction                             | https://developers.grow.business/reference/refund-overview, https://developers.grow.business/reference/refund-transaction                           |
| Refund responses (second pair, `210`)                              | https://developers.grow.business/reference/response                                                                                                 |
| Cancel Bit transaction                                             | https://developers.grow.business/reference/cancel-bit-transaction                                                                                   |
| Get transaction info, and its responses                            | https://developers.grow.business/reference/get-transaction-info, https://developers.grow.business/reference/response-get-transaction-info           |
| Get payment process info, and its responses                        | https://developers.grow.business/reference/get-payment-process-info, https://developers.grow.business/reference/response-get-payment-info           |
| Approve transaction (regular payment, and payment request)         | https://developers.grow.business/reference/approve-transaction, https://developers.grow.business/reference/approve-transaction-1                    |
| Server-to-server callback (Growin Wallet SDK section)              | https://developers.grow.business/reference/server-to-server-callback                                                                                |
| Generic server response (resend schedule, acknowledgement)         | https://developers.grow.business/reference/server-response-1                                                                                        |
| Per-flow callback pages                                            | https://developers.grow.business/reference/server-response, https://developers.grow.business/reference/payment-request-callback, https://developers.grow.business/reference/recurring-payment-callback |
| Parameter mapping (`StatusCode`, card codes, `TransactionTypeId`)  | https://developers.grow.business/reference/parameter-mapping                                                                                        |
| Error table                                                        | https://developers.grow.business/reference/errors                                                                                                   |
| Sandbox and test cards                                             | https://developers.grow.business/reference/testing-environment                                                                                      |
| Growin wallet SDK                                                  | https://developers.grow.business/reference/growin-wallet-sdk                                                                                        |
| Tokenization overview (token-charge declines)                      | https://developers.grow.business/reference/token                                                                                                    |
| Retrace a token payment                                            | https://developers.grow.business/reference/post_api-light-server-1-0-gettokentransactionsbyexternalidentifiers-1                                   |
| Authentication, and platform guidelines                            | https://developers.grow.business/reference/authentication, https://developers.grow.business/reference/api-guidelines-for-platforms-system-integrators |
| Postman collection                                                 | https://developers.grow.business/reference/postman-collection, https://documenter.gw.postman.com/api/collections/7312873/UVeGrRaX                  |
| WooCommerce plugin guide (the 72-hour J5 figure)                   | https://grow.business/plug-in-settings/                                                                                                             |
| Public sandbox base (probes)                                       | https://sandbox.meshulam.co.il/api/light/server/1.0/                                                                                                |

## Authentication and Transport

- Requests are server-to-server form data (rule). Every in-scope page says, verbatim: "All requests must be sent
  exclusively from your server's back end. Client-side (browser-based) requests are not supported and will be
  blocked." and "Do not include any special characters in any parameter of the API request." Grow defines no character
  set or error for the second rule, and the simulator does not enforce it.
- Base URL is configurable.
- `multipart/form-data` and `application/x-www-form-urlencoded` are read alike (decision, because Grow publishes only
  multipart). A body of any other type yields no fields. It is answered `707` on the endpoints that judge the amount first
  and `54` elsewhere, never a framework `415`.
- Credentials are read from the parsed body only, never from the query string.
- An empty form value counts as absent: `pageCode=` is answered `54` (probe). Callbacks are the opposite: an empty
  `err=` is present.
- Success is body `status=1`. Every failure is HTTP 200 with `status` other than `1` and an `err` object.
- Grow callbacks are unsigned. Correlation relies on the private `notifyUrl`, the echoed `cField` values, the
  `processId`/`processToken` and `transactionId`/`transactionToken` pairs, and optionally `approveTransaction`.

**Credential fields are per endpoint, not uniform** (rule, from each endpoint's body-parameter table):

| Endpoint                                                                                  | `userId`        | `pageCode`      |
|-------------------------------------------------------------------------------------------|-----------------|-----------------|
| `createPaymentProcess`                                                                    | required        | required        |
| `createTransactionWithToken`, `settleSuspendedTransaction`                                | required        | not a field     |
| `refundTransaction`                                                                       | required        | optional        |
| `getTransactionInfo`, `getPaymentProcessInfo`, `approveTransaction`, `cancelBitTransaction` | not a field   | required        |

- Rule and example disagree on `refundTransaction`: the Postman `Refund` request sends `pageCode` and no `userId`. The
  simulator accepts either, and answers `724` when a request carries neither.
- Rule and example disagree on `createPaymentProcess` too: the Postman *Delayed Payment* request sends `pageCode` and no
  `userId`. The simulator follows the rule and requires both.

Content type (the response header is visible on the wire):

| Source                       | Says                                                                                                         |
|------------------------------|--------------------------------------------------------------------------------------------------------------|
| Postman overview (rule)      | "All responses have a text/html content type and require parsing as application/x-www-form-urlencoded"      |
| Portal and Postman examples  | JSON bodies. The saved `Cancel Bit Transaction` responses say `application/json`, and the saved callback says `application/x-www-form-urlencoded` |
| Probe, 10 endpoints          | `Content-Type: text/html, charset=UTF-8` over a JSON body                                                    |

Hebrew escaping (probe and example): every one of the ten live responses of 2026-09-15 escaped every Hebrew character
as `\uXXXX`, e.g. `{"status":0,"err":{"id":716,"message":"\u05e4\u05e8\u05de\u05d8\u05e8 ..."},"data":""}`, while
every published example, portal and Postman alike, shows raw UTF-8. Same JSON value, different bytes.

`wireForm=LIVE_WIRE` (default) reproduces what was observed: `text/html; charset=UTF-8` over JSON, every non-ASCII
character escaped as `\uXXXX`. `PUBLISHED_EXAMPLE` reproduces what is documented: `application/json` with raw UTF-8.

The probe recorded that header with a comma, `text/html, charset=UTF-8`, and `GrowWireForm.LIVE_WIRE` sends a
semicolon. Whether the comma is Grow's own byte or a transcription slip is unverified, so a client that splits the
header on its separator reads one thing from Grow and another here.

Platform credentials (rule): "For platform partner integrations, each API request must include your unique `apiKey`"
(platform guidelines). The authentication page describes the credential as `APIKey`, `UserId` and `PageCode`. The only
in-scope schema that carries `apiKey` is `approveTransaction` under Payment Request, "Required only for companies that
are working with multiple businesses". `x-api-key` is a header on `CreatePaymentLink`, `UpdatePaymentLink` and
`SetAccountDetails` only, and all three are out of scope.

A Grow account of this simulator carries `userId` and `pageCode` and nothing besides (`GrowAccount.USER_ID`,
`GrowAccount.PAGE_CODE`), so `apiKey` is neither configurable nor read: a request that sends it on
`approveTransaction` is answered without it.

Currency: Grow publishes no currency parameter and no currency error on any in-scope endpoint. ILS is the currency Grow
assumes, as the platform page says: "The companyCommission value must be sent in ILS". `271` and `734` are denominated
in shekels.

### Invalid Credentials

- The Light API never returns HTTP 401 or 403. Every probed response was HTTP 200.
- `err.id` is always numeric. No string identifiers exist. The published corpus has 2- and 3-digit codes, and the
  probe added the 4-digit `1013`, so nothing may assume a width.
- Credential validation precedes business validation on some endpoints only. The lookups (`getTransactionInfo`,
  `getPaymentProcessInfo`, `approveTransaction`) answer a bad credential exactly like an unknown object.

The table below is the probe (2026-09-15) and what the simulator emits. `54`, `180`, `701`, `716`, `724`, `730` and
`801` are published rows, and only their assignment to a credential failure is the probe's. `104`, `743` and `880` are
in no published table and rest on the probe alone. The endpoints marked decision were not probed.

| Endpoint                      | Credential shape sent               | Code   | Wire text                                     | Gloss                                                   | Evidence                          |
|-------------------------------|-------------------------------------|--------|-----------------------------------------------|---------------------------------------------------------|-----------------------------------|
| `createPaymentProcess`        | unknown `userId`/`pageCode`         | `701`  | `פרמטר קוד זיהוי אינו תקין userId`            | identification-code parameter invalid                   | probe (short text, as on token-server-response) |
| `settleSuspendedTransaction`  | unknown `userId`                    | `743`  | `אין עסק תואם ל userId שנשלח`                 | no business matching the `userId` sent                  | probe                             |
| `refundTransaction`           | `userId` sent, unknown              | `801`  | `קוד זיהוי עסק אינו תקין`                     | business identification code invalid                    | probe                             |
| `refundTransaction`           | `pageCode` only, unknown            | `716`  | `פרמטר קוד עסקה או תוקן אינו תקין`            | transaction code or token parameter invalid             | probe                             |
| `refundTransaction`           | neither                             | `724`  | `נדרש שדה userId או pageCode`                 | `userId` or `pageCode` required                         | probe                             |
| `getTransactionInfo`          | unknown `pageCode`                  | `716`  | `פרמטר קוד עסקה או תוקן אינו תקין`            | as above                                                | probe                             |
| `getPaymentProcessInfo`       | unknown `pageCode`                  | `716`  | `פרמטר קוד עסקה או תוקן אינו תקין`            | as above                                                | probe                             |
| `createTransactionWithToken`  | unknown `userId`                    | `880`  | `לא נמצא עסק מתאים לUserId`                   | no matching business for the `UserId`                   | probe                             |
| `createTransactionWithToken`  | no `transactionUniqueIdentifier`    | `104`  | `משתמש זה אינו קיים במערכת`                   | this user does not exist in the system                  | probe                             |
| `approveTransaction`          | unknown `pageCode`                  | `180`  | `פעולת עידכון לא בוצעה, רשומה לא נמצאה`       | update not performed, record not found                  | probe                             |
| `cancelBitTransaction`        | unknown `pageCode`                  | `730`  | `פרמטר קוד תהליך או תוקן אינו תקין`           | process code or token parameter invalid                 | probe                             |
| `getTokenTransactionsByExternalIdentifiers` | unknown `userId`      | `880`  | as `createTransactionWithToken`               |                                                         | decision (no probe)               |

- Missing credential field: `54`, text `חסרים נתונים:` followed by the field name, e.g. `חסרים נתונים:pageCode`
  (probe, and the wording on refund-overview-1). The error table's `חסרים שדות חובה : (parameter name)`, Postman's
  `חסרים שדות חובה : pageCode` (that template with the field filled in) and delayed-payment-response's
  `חסרים שדות חובה: pageCode` were never observed on the wire. A client must not match on
  the message.
- `54` is not the universal missing-field code. A settle without `sum` answered `707`, `הסכום אינו תקין`, the amount is
  not valid (probe).
- The amount is judged before the credential and before field presence. `createPaymentProcess` and
  `settleSuspendedTransaction` answer `707` for a missing, zero, negative or non-numeric amount even with an unknown
  `userId`. An empty form answers `707` (probe). The simulator applies the same order to `createTransactionWithToken`
  (decision).
- `1013`,
  `מזהה לא תקין. על המזהה להיות מספר שלם חיובי, ללא אפסים מובילים ושניתן לייצוג ב32 ביט (כלומר, קטן מ 4,294,967,295)`,
  the identifier is not valid: it must be a positive whole number with no leading zeros that fits in 32 bits.
  `createTransactionWithToken` returns it for a `transactionUniqueIdentifier` that is not a bare number. `4294967295`
  is accepted and `4294967296` refused, so the bound is inclusive, whatever the parenthesis says (probe).

### Response Envelope

Error body (example): `{"status": 0, "err": {"id": <n>, "message": "<Hebrew>"}, "data": ""}`. A failure never carries
identifiers in `data`. Success: `{"status": 1, "err": "", "data": {...}}`.

The JSON types vary between Grow's own examples, independently of the error class:

| Where                                                       | `status`        | `err.id`       | Evidence |
|-------------------------------------------------------------|-----------------|----------------|----------|
| API success                                                 | integer `1`     | none           | example  |
| `54`, Postman `createPaymentProcess`                        | string `"0"`    | string `"54"`  | example  |
| `54`, delayed-payment-response                              | integer `0`     | integer        | example  |
| `54`, refund-overview-1 and the live wire                   | string `"0"`    | integer `54`   | example, probe |
| `130`, `140`, `180`, `701`, `716`, `804`                     | integer `0`     | integer        | example  |

- `settleSuspendedTransaction` renders `statusCode`, `transactionId` and the card codes as strings, while
  `createTransactionWithToken` renders them as integers (example).
- Key order is observed and not part of any contract. On the wire, `54`, `180` and `724` put `err` first, and `104`,
  `701`, `716`, `730`, `743`, `801`, `880` and `1013` put `status` first (probe).
- The Postman `createPaymentProcess` success examples show a bare `data` object. The portal shows the full envelope,
  and the simulator emits the envelope.
- Callbacks are form data, so they have no JSON types.

The simulator emits integer `status` and `err.id` on every API response: `GrowModule` hands every endpoint
`GrowScalarType.INTEGER`, and no setting selects the string form Grow's `54` examples show. A client that reads either
field as a string will not match this wire.

## Minimum API Surface

### Create Hosted Payment Process

`POST /api/light/server/1.0/createPaymentProcess`, from create-payment-process-6 (token only) and
create-payment-process-10 (hold).

| Field                                   | Rule                                                                                                                           |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `pageCode`, `userId`                    | required                                                                                                                       |
| `chargeType`                            | required: `3` "Create Token", `2` "Suspended Charge". The simulator refuses any other value with `54` naming `chargeType`      |
| `sum`                                   | required. Must be "1" on the token-only flow                                                                                   |
| `saveCardToken=1`                       | required on token only, optional on the hold. A token-only request without it is answered `54`                                |
| `description`                           | required                                                                                                                       |
| `successUrl`, `cancelUrl`               | required. `successUrl` must be HTTPS and not localhost                                                                         |
| `notifyUrl`                             | optional to Grow, and every callback scenario here needs it. "Server updates cannot be sent to a localhost address." (server-response-1). Postman: "must be an external URL (NOT localhost!)" |
| `pageField[fullName]`, `pageField[phone]` | required. `fullName` must hold at least two names                                                                            |
| `pageField[email]`                      | optional                                                                                                                       |
| `cField1`..`cField9`                    | optional. This contract sends `cField1` as the merchant session token, and `cField2` as the payment intent id on the hold only |
| `companyCommission`                     | optional platform field: "must be sent in ILS", "excluding VAT", fixed amount, "applied per transaction". Postman sends it empty. The simulator accepts it and ignores it |
| `productData[0][...]`                   | optional. The J4/J5 page spells `catalogNumber`/`itemDescription`, and the token-only page spells `catalog_number`/`item_description`. Which one the endpoint accepts is undetermined |
| `paymentNum`                            | optional, `1-12`                                                                                                               |
| `maxPaymentNum`                         | optional. J4/J5 page: "The payments number must be minimum 2". Token page: the payer "can choose between 1- the value"         |

- Both `paymentNum` and `maxPaymentNum` in one call: `736`. The simulator enforces this. An account for regular
  payments that sends either one: `740`, which the simulator does not model.
- No hold-lifetime parameter exists.
- The token-only callback reports `statusCode` `11` with `sum` `1`, the same as a hold. Grow does not say whether that
  1-unit transaction can be settled or whether it expires. The simulator maps it to `SUCCEEDED` (card registered) and
  never expires it.
- Order of checks in the simulator: amount (`707`), required fields (`54`), `saveCardToken` on token-only (`54`),
  credentials (`701`), `chargeType` value (`54`), instalment pair (`736`).

Success (example, delayed-payment-response):

```json
{
  "status": 1,
  "err": "",
  "data": {
    "processId": "734754",
    "processToken": "2153287cd9af46457ae842337250b78d",
    "url": "https://sandbox.meshulam.co.il/far?l=89a71a421b8e0686024fbb762dab61e1"
  }
}
```

Failures: `54`, `707` and `701`, with the texts in "Invalid Credentials". The `701` callback failure example on
token-server-response carries the same short text the wire uses.

### The Hosted Page, and What Completing It Produces

`GET /far?l=<link>` and `POST /far`

**This whole section is a simulator decision.** Grow publishes the URL and nothing behind it. What Grow does publish is
what the completion must produce: the callbacks in "Merchant Callback Contract", the zeroed row in "Get Payment Process
Info", and the three test cards.

- The `l=` value is drawn once, stored beside the process and unique across test runs. It is not derived from
  `processId`.
- The page and its refusals are HTML5 documents with a `width=device-width` viewport, laid out for a phone's webview.
  Every answer carrying a document — the page, its refusals and the page for a link this simulator does not hold —
  carries `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and
  `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'`, in that order: the page is one
  self-contained document with inline style, no script and no external resource. The policy names no `form-action`,
  because the form is posted to this origin and answered with a redirect to `successUrl` or `cancelUrl` off it, and
  browsers disagree on whether `form-action` is checked again across that hop.
- The `303` a completion is answered with carries none of those four. A redirect has no document, so it would declare a
  policy governing nothing, and it is the answer of this route a shop's own client reads.
- The payer picks a card by alias, never by number: `sandbox-4580`, `sandbox-0000` and `sandbox-1121` stand for
  `4580458045804580`, `4580000000000000` and `4580111111111121`. A payment-method fixture seeded into the run is offered
  under its own alias. The three sandbox cards differ only in `cardSuffix`. The other descriptors come from Grow's own
  record example: `Foreign`/`2`, `Visa`/`3`, `0424`.

| Outcome   | Process `statusCode`   | Normalized                                                    | Callback                              | Redirect    |
|-----------|------------------------|---------------------------------------------------------------|---------------------------------------|-------------|
| paid      | `11` `עסקה מושהית`     | `chargeType=2`: `READY_FOR_CAPTURE`. `chargeType=3`: `SUCCEEDED` with a `cardToken` | success form           | `successUrl` |
| declined  | `0` `לא שולם`          | `FAILED`                                                      | envelope failure (`701`), no status pair | `cancelUrl` (decision) |
| abandoned | `0` `לא שולם`          | `NOT_INITIALIZED`                                             | none                                  | `cancelUrl` |

- What decides the shape is `chargeType` and not `cField2`. The `cField2` rule in "Merchant Callback Contract" is what a
  merchant reads off an inbound callback.
- A paid or declined completion resolves the callback target before it writes the outcome. If the run addresses no
  target for it, the page answers `422`, sends nothing and leaves the process open. That happens when `notifyUrl` is not
  among the run's callback targets, or when there is no `notifyUrl` and the merchant has no single target.
  `createPaymentProcess` itself accepts any `notifyUrl`.
- The redirect carries only the `cField` values (rule, the-process). Where a decline redirects is unpublished.
- The chosen card appears in the callback, the process, the transaction record and every later lookup, settle, refund
  and token charge. A completion made through the control callback route describes the first sandbox card. A seeded card
  whose brand or funding is in neither published code table carries `""` for that code.

### Create Stored-Card Transaction

`POST /api/light/server/1.0/createTransactionWithToken` (rule: create-transaction-with-token-4, and the Postman
`Payment With Token` request).

| Field                                   | Rule and wire                                                                                                               |
|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `cardToken`, `userId`                   | required. `pageCode` is not a field                                                                                         |
| `sum`, `description`                    | required                                                                                                                    |
| `paymentType`                           | required: `1` "Direct Debit (הוראת קבע)" (parameter mapping) or "Recurring Payments" (token page), `2` regular, `4` payments. Required present and never read: every response renders the constant `2`, and the unpaid process row `0` (decision) |
| `transactionUniqueIdentifier`           | required. A positive 32-bit whole number with no leading zeros, else `1013`. Absent (or `0` in the simulator): `104` (probe) |
| `pageField[fullName]`, `pageField[phone]` | required                                                                                                                  |
| `pageField[email]`, `pageField[invoiceName]`, `pageField[invoiceLicenseNumber]` | optional                                                            |
| `paymentNum`                            | published "optional -Determine payments number. 1-12". **Required on the wire:** omitting it answers `חסרים נתונים:paymentNum` (probe). The simulator requires it |
| `transactionGroupIdentifier`            | optional                                                                                                                    |
| `cField1`..`cField9`                    | optional, echoed by `getTransactionInfo`                                                                                    |

Rules:

- A repeat of `transactionUniqueIdentifier` "rejected outright regardless of the status of the first request with this
  ID" (rule). A `transactionGroupIdentifier` already carried by a successful payment is rejected (rule). Grow publishes
  no code for either. The simulator answers both with `712`, `העסקה כבר בוצעה`, the transaction was already carried out
  (decision).
- "Tokens can be saved for up to 180 payments" (rule, token page). No code is published for the 181st charge, and the
  simulator does not enforce the limit.
- Unknown token: `140`, `לא ניתן לבצע תשלום עם טוקן, קוד מזהה טוקן אינו תקין` (example, not in the error table).
- A decline or failure carries no `status`/`statusCode` pair, only the envelope. See "Declines".
- Probe: the wire checks for the presence of `userId`, `cardToken`, `paymentType` and `paymentNum`, and does **not**
  answer `54` for `description`, `pageField[fullName]` or `pageField[phone]`. The simulator enforces the published table
  and answers `54` for those three as well (decision).
- Order in the simulator: `sum` (`707`), required fields (`54`), identifier (`104`, `1013`), credential (`880`), token
  (`140`), then the outcome.
- This call is one of the three ways the wizard starts a payment, under the wire name
  `createTransactionWithToken` (`GrowOrigination.A_SAVED_CARD_CHARGE`). It is the only one of the three that leaves a
  payer no page: grow charges the card as it answers. The wizard asks for `cardToken`, `sum`, `description`,
  `paymentType`, `paymentNum`, `transactionUniqueIdentifier`, `transactionGroupIdentifier` and the three
  `pageField[...]`; it sends `userId` from the account and sends no `pageCode` and no `chargeType`, because this call
  reads neither. The `cardToken` is a box carrying the cards the finished processes of that test run saved on
  that account as suggestions, labelled by the process and the payer that saved each one, so nothing has to be typed to
  charge one; the box still takes any token, because grow is what reads that value and grow answers `err.id` 140 for a
  token that business never saved, which is a refusal a tester reaches by typing one
  (`WhereAFieldsValuesComeFrom.THE_VALUES_THIS_TEST_RUN_HAS_ALREADY_PRODUCED`,
  `SimulatorProvider.theValuesThisTestRunHasAlreadyProducedFor`). A charge started that way is recorded with the origin
  it was asked through, so one a tester clicked reports `UI` and one a client sent reports `PROVIDER_API`.
- The transaction a token charge creates carries no `processId`, so `relatedObjects` names the business and never the
  process whose payer saved the card. That is grow's own response shape and not an omission of this simulator.

Success (example, Postman and token-transactions-response). This is the transaction record every endpoint returns, with
per-endpoint differences noted at each endpoint:

```json
{
  "status": 1,
  "err": "",
  "data": {
    "asmachta": "89475113",
    "cardSuffix": "4580",
    "cardType": "Foreign",
    "cardTypeCode": 2,
    "cardBrand": "Visa",
    "cardBrandCode": 3,
    "cardExp": "0424",
    "firstPaymentSum": 0,
    "periodicalPaymentSum": 0,
    "status": "שולם",
    "statusCode": 2,
    "transactionTypeId": "1",
    "paymentType": "2",
    "sum": "1",
    "paymentsNum": 0,
    "allPaymentsNum": 1,
    "paymentDate": "28/11/22",
    "description": "test",
    "fullName": "test test",
    "payerPhone": "0505503144",
    "payerEmail": "no@mail.com",
    "transactionId": 236260,
    "transactionToken": "531bac1f99456a7140"
  }
}
```

Unpublished: whether `paymentType` in the response echoes the request or reflects the account.

### Retracing a Token Payment by the Caller's Own Identifier

`POST /api/light/server/1.0/getTokenTransactionsByExternalIdentifiers/`. Fields (rule): `userId`, `cardToken` and
`transactionUniqueIdentifier` are required, and `transactionGroupIdentifier` is optional. This is how a caller recovers
from a timed-out stored-card charge without the lost `transactionId`/`transactionToken`.

Grow publishes no response example. Everything below is a decision:

- success is `data.transactions[]`, and each row is the all-string record of "Get Payment Process Info",
- no matching charge, or a declined charge, answers `status=1` with an empty `transactions[]`,
- a `transactionGroupIdentifier` that is sent must match the charge's group,
- rejections reuse the codes of `createTransactionWithToken`: `54` for missing `userId`/`cardToken`, then `104`,
  `1013` and `880` from the probe, and the published example `140` for a token the business never saved.

A tester reaches this one by clicking, on the **Look a payment up** screen, which draws the reads a provider declares
and performs the one a tester picks (`GrowReads.DECLARED`, `kernel/spi/DeclaredRead`). Grow declares it as one
question, "Find out whether a charge on a saved card went through", asked with the two identifiers a merchant sent
itself: the `cardToken` is a box carrying the cards the finished processes of this test run saved on the account as
suggestions, so a saved card is charged without typing one and `err.id` 140 is reached by typing a token that business
never saved, and `transactionUniqueIdentifier` is typed because it is the merchant's own number and because an
identifier nobody sent is what reaches the empty answer. `userId` is the account's credential and the read declares it
under `DeclaredRead.fieldsItFillsInItself`, with the sentence saying where the value comes from
(`GrowReads.THE_MERCHANT_IS_THE_ACCOUNTS_OWN`), because a field the simulator fills in is one the port refuses to also
ask a tester for. The answer is drawn as the members Grow answers on the row, under the names Grow answers them under,
and the empty answer is drawn as Grow's own answer with the sentence that says what it means rather than as a blank
panel: this read declares an empty answer as one it gives, because `data.transactions[]` empty is what a charge nobody
retraced leaves.
Grow answers nothing of its own about the identifiers the question was asked with, so the screen prints no panel for
one; and every member of this row is one value, so none of them is drawn as a member of its own.

### Approve Transaction Callback

`POST /api/light/server/1.0/approveTransaction`. The only credential is `pageCode`. The other fields (rule and Postman)
are `transactionId` and `transactionToken` plus the echoed notification fields `transactionTypeId`, `paymentType`, `sum`,
`firstPaymentSum`, `periodicalPaymentSum`, `paymentsNum`, `allPaymentsNum`, `paymentDate`, `asmachta`, `description`,
`fullName`, `payerPhone`, `payerEmail`, `cardSuffix`, `cardType`, `cardTypeCode`, `cardBrand`, `cardBrandCode`,
`cardExp`, `processId` and `processToken`. The simulator reads nothing from the echoed fields.

- Success is `{"status": 1, "err": "", "data": ""}` (example). `Cancel Bit Transaction` has the same body, so an empty
  `data` does not identify this endpoint.
- Failure: `180`, `פעולת עידכון לא בוצעה, רשומה לא נמצאה` (example). The simulator uses it for an unknown credential, a
  missing id or token, and an unknown transaction. A missing `pageCode` is `54`. A repeated approval succeeds again.
- "This step does not alter the transaction status nor does it cancel or approve it" and "The transaction will be
  processed even if the ApproveTransaction request is not executed or fails" (rule, server-to-server-callback).

Whether the call is required is **undecidable from the published sources**:

| Source                                                   | Says                                                                                                                  |
|----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| approve-transaction, Important Notes, 2nd bullet         | "It is a critical component of the overall process and must be performed for every successful transaction."         |
| approve-transaction, Important Notes, 3rd bullet         | "Do not send this request in the case of token transactions (created with createTransactionWithToken) or delayed transactions (J4J5), or for save token only scenarios." |
| server-response, server-to-server-callback               | "Upon receiving the update, you are required to execute the ApproveTransaction API call"                              |
| the-process                                              | "Upon receiving this notification, you must call the “approveTransaction” method to approve the transaction"          |
| Postman                                                  | "It is not required to approve a token transaction." and "It is not required to approve a delayed transaction."       |
| Portal navigation                                        | regular-payment, payment-request and recurring-payment each have an Approve page, and J4/J5 and token do not          |

The simulator exposes the endpoint, and a payment completes without it.

Acknowledgement and resend. Grow defines acknowledgement in two ways:

- server-response-1: "it is imperative that you send a response to Grow's server, confirming the receipt of our call. In
  the event that no response is received, our server will make additional attempts". The same page says separately to
  call ApproveTransaction,
- approve-transaction: "When Grow consistently does not receive these acknowledgments, Grow will resend the server
  update up to 5 additional times", followed by "If acknowledgments are still not received, Grow may contact the
  business to confirm that everything is working properly".

callback delivery can be configured to wait for approval: `callbackAcknowledgement=HTTP_2XX` (default) counts a 2xx
reply, and `APPROVE_TRANSACTION` waits for the call. A missing approval stays visible in callback audit and can be
retried by control.

Resend schedule:

| Source                         | Attempts                                      | Delays                                                                                                   |
|--------------------------------|-----------------------------------------------|----------------------------------------------------------------------------------------------------------|
| approve-transaction (rule)     | 1 + "up to 5 additional times"                | none published                                                                                           |
| server-response-1 (rule)       | 6                                             | "Grow retries the update at 10, 20, and 30-minute intervals", "1-3 times after 10 minutes.", "4-5 times after 20 minutes.", "6 times after 30 minutes.", with "the initial call occurs immediately after the transaction, followed by subsequent calls at 10-minute intervals" |
| simulator (decision)           | 6                                             | `PT0S, PT1S, PT5S, PT30S, PT2M, PT5M`, replaceable per test run through `settings`, and a queued delivery keeps its plan |

`updateMyUrl` (rule, on the per-flow callback pages, server-response-1, token-server-response and Postman):
`GET https://sandbox.meshulam.co.il/api/light/server/1.0/updateMyUrl/?url=MySiteURL`, "This GET request simulates the
callback notification by sending an HTTP request to the URL you specify." Grow publishes no body and no response. The
simulator's behaviour is a decision:

- it re-sends the newest callback already addressed to `url`, through the normal delivery queue and audit, and answers
  `{"status": 1, "err": "", "data": ""}`,
- a url no callback has been addressed to is refused with a diagnostic rather than a Grow envelope,
- no credential is read, and `url` comes from the query string,
- a missing or empty `url` is answered `54`.

### Settle Suspended Transaction

`POST /api/light/server/1.0/settleSuspendedTransaction`, the J4 leg. Fields (rule): `userId`, `transactionId`,
`transactionToken` and `sum`, plus optional `productData[0][...]` and `pageField[invoiceName]`.

- A partial settle is allowed: "Release part of the money from J5 as needed (You can release any sum from the frame
  catch)" (rule, delayed-payment-overview).
- The ceiling is disputed: `804` (the settled amount may not exceed the hold) against `814` (up to 30% above). The
  default follows `804`, which is also Grow's own failure example for this endpoint. `settlementCeiling` selects the
  other.
- Success returns the transaction record with `statusCode` `"2"` and `status` `שולם`, and every scalar is a string
  (example, Postman and delayed-payment-response-2).
- Grow publishes two failure examples for this endpoint: `804` (Postman) and `819`,
  `אין באפשרותך לבצע תשלום כעת  \נסה בשלב יותר מאוחר` (delayed-payment-response-2). `819` is in no table.
- A scenario naming `819` is answered with `err.id` `819` and an empty `err.message`, not Grow's published Hebrew:
  `GrowErrors.chosenByScenario` carries wire text for the token-charge declines of `GrowDecline` alone and falls back
  to `NO_WIRE_TEXT_RECORDED`, `""`. Assert `err.id`.
- `803`, `808` and `810` are table rows about release, and `813` is a row about charging. Assigning any of them to
  this endpoint is an inference from the Hebrew.
- The simulator returns the same `transactionId` and `transactionToken` it was sent. That is an assumption, because the
  published pairs use unrelated placeholder ids (Postman: `1234123412341234` answered by `236251`).

| Check (simulator order)                     | Code   |
|---------------------------------------------|--------|
| `sum` missing, zero, negative, non-numeric   | `707` (published row, assignment probed) |
| missing `userId`, `transactionId`, `transactionToken` | `54` |
| unknown `userId`                            | `743` (probe) |
| unknown transaction or token                | `813` (decision) |
| not a hold                                  | `810`  |
| already settled                             | `808`  |
| past `captureWindow` or `automaticVoidAfter` | `803` |
| above the ceiling                           | `804` or `814` |

### Capture Window and Automatic Void

The request has no hold-lifetime field. Grow's figures disagree:

| Source                     | Figure                                                                                                           |
|----------------------------|------------------------------------------------------------------------------------------------------------------|
| delayed-payment-overview   | "J5 transactions have a timeframe of up to 7 days", "If no J4 transaction is made, J5 will automatically release after 10 days" |
| WooCommerce plugin guide   | `במצב J5 ניתן לערוך את הסכום לחיוב עד 72 שעות מרגע הרכישה`, the amount can be edited for up to 72 hours          |
| Postman overview           | "J5 up to 3 days."                                                                                               |

The Postman figure is the weakest of the three: the collection the portal links is titled "Meshulam API" and its server
is `sandbox.meshulam.co.il`, so it carries the legacy brand and is not a statement grow makes under its own name today.

The defaults follow the portal: `captureWindow=P7D` and `automaticVoidAfter=P10D`.

Neither span is a field of grow's API. Two independent enumerations of every field on grow's reference pages, one of
101 distinct names and one of 116, found no request or response field that sets or reports a hold lifetime. So both
spans are configuration this simulator invented, and every bound on them is a simulator decision, because grow
publishes no bound on a setting it does not have:

| Bound                             | Evidence | Reason                                                                               |
|-----------------------------------|----------|--------------------------------------------------------------------------------------|
| longer than nothing               | decision | a hold whose span closes at or before the moment it is created can never be captured. An expired hold is reached instead by stating a span a test can outlive and moving the clock past it |
| at most 365 days                  | decision | the simulator holds a span, not a deadline, and reads a deadline by adding the span to the hold's creation instant; far enough out `Instant.plus` throws instead of answering. None of grow's three figures is adopted as this bound: each is phrased as a ceiling on the product and they disagree |
| a whole number of microseconds    | decision | the column keeps a span as a Postgres `interval`, whose resolution is a microsecond, so a span stated more finely is not the span that would be stored |

Grow publishes no minimum for either span, though it does publish one for instalments, `chargeDay` and refund counts.
It publishes no rule binding the two spans: it states 7 days and 10 days under two separate headings and never relates
them, and on its WooCommerce property the two figures are equal. The simulator refuses no combination of the two and
takes whichever span closes first, which the `automaticVoidAfter` field says on the form.

Every bound is enforced in `GrowHoldWindow` -- in `of` for a value a caller states, and in the canonical constructor so
that a row read back from the store cannot carry one either -- and again by a check constraint on each column. The two
agree in both directions for every text the provider can write: all 75 canonical forms a provider-accepted span prints
as are admitted by the column, and the column admits nothing `Duration.parse` cannot read.

The column's predicate is narrower than `Duration.parse`, and the migration carrying it resets what the predicate does
not admit (decision). `Duration.parse` also reads `+PT1H`, `pt1h`, `PT+1H` and `PT0,5S`, which are spans inside the
floor; the predicate is case-sensitive, sign-free and knows no comma decimal, so a row holding one of those four is
reset to the published default rather than kept. Only a row backfilled by `V27` can hold such a text, because
`JdbcGrowAccounts` writes `Duration.toString()`, which is always the canonical `PT...` form, so no route produces one.

| Stage                          | Native            | Normalized          | `capturesAccepted`   |
|--------------------------------|-------------------|---------------------|----------------------|
| within the capture window      | `11`              | `READY_FOR_CAPTURE` | true                 |
| past the capture window        | `11`              | `READY_FOR_CAPTURE` | false (settle `803`) |
| past the automatic void        | `11`              | `CANCELLED`         | false                |

Grow publishes no native status for an expired hold. `4`, "Canceled before transmission", is the only candidate, and
the simulator keeps `11`.

### Refund Transaction

`POST /api/light/server/1.0/refundTransaction`. Fields (rule): `transactionId`, `transactionToken`, `refundSum`,
`userId` (required on the page, absent from Postman), optional `pageCode`, and optional `stopDirectDebit=1` (ignored).

Rules (refund-overview):

- "Refund transactions can only be processed for transactions that have been previously charged". The simulator
  answers a refund of an unsettled hold with `813` (decision). The published wording of `813`,
  `לא ניתן לבצע חיוב לעסקה שלא בוצעה`, speaks of charging and not of refunding, and no Grow source assigns it to
  `refundTransaction`: the error table has no endpoint column, refund-overview and refund-transaction publish no code
  at all, and the only two published refund failure examples are `130` (Postman) and `210` (the response page).
- The page says both "It enables issuing full or partial refunds after a transaction" and "Refunds are applicable when
  you need to return the entire amount charged." The simulator allows partial refunds.
- The maximum is the refunded amount. An excess is `105` by default, with `190` or `218` by setting. `215` is an amount
  that is not usable at all.
- "Refunds are not possible if there is no balance in the account": `115`, with `merchantBalance=EMPTY`.
- Count: "A maximum of 2 refunds can be made through the API." / "For the third refund, please contact customer
  service." against `210`, which rejects the second refund. The default follows `210`, and `TWO_REFUNDS` follows the
  overview. "Through the API" may not mean per transaction.
- Same day: "If you want to refund money on the same day transaction has been made, that action won't be a refund but
  it will be a cancellation (i.e. in this situation you will not be able to refund a partial amount of the transaction,
  you can only cancel the whole transaction)". A same-day partial refund is `130`. A same-day full refund is allowed.
  `207` phrases the boundary as transmission rather than the calendar day. No example of a same-day full refund exists,
  and every void takes that path.
- No invoice is generated.

| Check (simulator order)                          | Code                     |
|--------------------------------------------------|--------------------------|
| missing `transactionId`, `transactionToken`, `refundSum` | `54`             |
| credential: `userId` sent / `pageCode` only / neither | `801` / `716` / `724` (published rows, assignment probed) |
| unknown transaction or token                     | `716`                    |
| not settled                                      | `813` (decision)         |
| refund allowance used                            | `210`                    |
| amount not positive or not numeric               | `215`                    |
| above the refundable remainder                   | `105`, `190` or `218`    |
| same-day partial                                 | `130`                    |
| no merchant balance                              | `115`                    |

A refund creates a new row (example):

- it has its own `transactionId` and a `refundedTransactionId` pointing at the original, and no `transactionToken` or
  `processId`, so `getTransactionInfo` cannot fetch it. Both published examples agree,
- the gap between ids differs: `235892` → `236256` in Postman and `425855` → `425856` on the response page, so no
  increment may be assumed. Grow does not say whether the two ids may be equal. The simulator never makes them equal,
- the response page's failure example is `210`, `בוצע זיכוי לעסקה זו, לא ניתן לזכות בשנית.`, and Postman's is `130`,
- `refundSum` is request-only. Whether the response `sum` is the refunded or the original amount is unpublished, and the
  simulator returns the refunded amount,
- the published body lacks `firstPaymentSum`, `periodicalPaymentSum`, `paymentsNum` and `allPaymentsNum` as well as the
  token and `processId`.

Success (example, Postman):

```json
{
  "status": 1,
  "err": "",
  "data": {
    "refundedTransactionId": 235892,
    "asmachta": "89474280",
    "cardSuffix": "4580",
    "cardType": "Foreign",
    "cardTypeCode": "2",
    "cardBrand": "Visa",
    "cardBrandCode": "3",
    "cardExp": "0523",
    "status": "תשלום שזוכה",
    "statusCode": 3,
    "transactionTypeId": "1",
    "paymentType": "2",
    "sum": "1",
    "paymentDate": "28/11/22",
    "description": "תיאור",
    "fullName": "לקוח לקוח",
    "payerPhone": "0505503144",
    "payerEmail": "nomail@nomail.com",
    "transactionId": "236256"
  }
}
```

### Terminology: settle versus void

Grow uses "release" for two opposite things: capture (`804`, `808`, `809`, `810`) and ending a hold without payment
("you can release J5 by performing a J4 transaction and an immediate refund"). This file says **settle** for capture
(J4) and **void** for ending a hold. Hebrew texts keep Grow's word.

### Voiding a Suspended Transaction

No void endpoint exists for a card hold. Cancel exists for Bit only (`pageCode`, `processId`, `processToken`), under
two published paths: `POST /api/light/andriod/1.0/cancelBitTransaction/` (portal) and
`POST /api/light/server/1.0/cancelBitTransaction` (Postman). Grow's instruction is "you can release J5 by performing a
J4 transaction and an immediate refund".

- The void path is to settle the full held amount and then refund the same amount on the same day. That refund is a
  cancellation, so a partial void cannot be expressed (`130`).
- A second void attempt fails on the settle leg with `808`.
- The native end state is undocumented. The simulator always emits `3` (the only value Grow shows for a refund) and
  normalizes it to `REFUNDED`. Tests should assert the normalized state. Grow enumerates `4`, "Canceled before
  transmission", and nothing here writes it ("Native Status Codes", `GrowNativeStatus`).

- A partial settle followed by a refund leaves the remainder in a state Grow does not describe.
- Reconciliation: after the settle, `getTransactionInfo` on the original is expected to report `2` (no published example
  of a settled row). After the refund, nothing can be read back through the provider API. The simulator exposes both rows
  and the `refundedTransactionId` link through the control API. A lost refund response must never be re-issued, because
  `210` rejects it.

### Get Transaction Info

`POST /api/light/server/1.0/getTransactionInfo`. Fields: `pageCode`, `transactionId`, `transactionToken`. There is no
`userId`.

- The body is the transaction record plus `processId`, `processToken` and `customField`. In the examples, `statusCode`,
  `firstPaymentSum` and `paymentsNum` are integers, and `allPaymentsNum` is `"1"` in one example and `1` in the other.
- The portal example adds `directDebitId`, `paymentLinkProcessId`, `paymentLinkProcessToken` and `recurringDebitId`,
  which Postman omits. The simulator omits them.
- Every failure is `716`, which matches Grow's example for this endpoint.
- The correlation key is named `customField` here and in `getPaymentProcessInfo`, and `customFields` in callbacks. It
  renders as `{"cField1": "123456"}` when fields were sent and `[]` when none were (example). The simulator reproduces
  both.
- For a stored-card charge the simulator returns `processId` and `processToken` as `""`. No example exists for that case.
- What a refunded original reports is undocumented.

### Get Payment Process Info

`POST /api/light/server/1.0/getPaymentProcessInfo`. Fields: `pageCode`, `processId`, `processToken`. There is no
`userId`.

- Every published failure example is `716`, while the error table assigns `730` to a process lookup. The default is
  `716`, and `processLookupRejection=PUBLISHED_TABLE` gives `730`. A process of another merchant is always `716`.
- An unpaid or abandoned process returns a zeroed row, not an empty array (example, Postman):

```json
{
  "status": 1,
  "err": "",
  "data": {
    "processId": "399507",
    "processToken": "7b5789f24568dff8e03c643",
    "customField": [],
    "transactions": [
      {
        "asmachta": "",
        "cardSuffix": "",
        "cardType": "",
        "cardTypeCode": "",
        "cardBrand": "",
        "cardBrandCode": "",
        "cardExp": "",
        "firstPaymentSum": "0",
        "periodicalPaymentSum": "0",
        "status": "לא שולם",
        "statusCode": "0",
        "transactionTypeId": "6",
        "paymentType": "0",
        "sum": "1",
        "paymentsNum": "0",
        "allPaymentsNum": "0",
        "paymentDate": "",
        "description": "תיאור",
        "fullName": "לקוח לקוח",
        "payerPhone": "0505503144",
        "payerEmail": "no@mail.co.il",
        "transactionId": "0",
        "transactionToken": ""
      }
    ]
  }
}
```

- `0` `לא שולם` is the one status where the enumeration and the examples agree. Empty strings are used, not absent
  keys, and the payer fields keep their values. The row maps to `NOT_INITIALIZED`.
- The example is a Bit process (`transactionTypeId` `6`). A declined card may produce the same row, and the simulator
  does not claim the row tells abandonment from decline.

## Declines

An API decline is the envelope failure: `status: 0`, a numeric `err.id`, `data: ""`, and no status pair.

Token-charge decline reasons (rule and example, token page, "Optional Error Details:",
`פירוט שגיאות אופציונליות לחיוב טוקן לא תקין`). The simulator emits each by the scenario code shown:

| `err.message`                                                           | `err.id` | Class (`סופי` final)  | Gloss                                           | Scenario code                   |
|-------------------------------------------------------------------------|----------|-----------------------|-------------------------------------------------|---------------------------------|
| `כרטיס פג תוקף.`                                                        | `103`    | `סופי`                | expired card                                    | `expiredCard`                   |
| `כרטיס גנוב - עסקה לא מאושרת`                                           | `103`    | `סופי`                | stolen card, not approved                       | `stolenCard`                    |
| `כרטיס חסום - עסקה לא מאושרת`                                           | `103`    | `סופי`                | blocked card, not approved                      | `blockedCard`                   |
| `חברת האשראי לא אשרה את העסקה`                                          | `103`    | `לא סופי`             | the credit company did not approve              | `creditCompanyDidNotApprove`    |
| `כרטיס האשראי אינו פעיל/פג תוקף, נא לפנות ללקוח לקבלת כרטיס חדש`        | `451`    | `סופי`                | card inactive or expired, ask for a new card    | `cardInactiveOrExpired`         |
| `חברת האשראי חסמה את הכרטיס, נא לפנות ללקוח לבדיקה`                     | `452`    | `סופי`                | the credit company blocked the card             | `cardBlockedByTheCreditCompany` |
| `חסימת מערכת`                                                           | `453`    | `סופי`                | system block                                    | `systemBlock`                   |

Published body: `{"err": {"id": 103, "message": "כרטיס גנוב - עסקה לא מאושרת"}, "status": 0, "data": ""}`. The rule: "If a
tokenized charge encounters a final error (e.g., card expired, stolen card), cease charging the token, delete it, and
update the customer's new card number." `103`, `451`, `452` and `453` appear in no error table. Under `103` the reason is
in the message, not the code.

- Nothing equivalent is published for a hosted-page decline, and no endpoint has an issuer-code field. Postman's
  "Failure of payments notification will be recieved without the reason of failure" is published under Bit, Google Pay
  and Apple Pay only.
- Enumerated status `9`, "Transaction denied", appears in no example. Grow's enumerated `4`, `6` and `9` are reached by
  no fixture and no scenario here: the four codes this simulator writes are `0`, `2`, `3` and `11` ("Native Status
  Codes", `GrowNativeStatus`). A client that switches on `9` for a denial reads the failure envelope and a numeric
  `err.id` instead.

- No card number produces a decline. The testing-environment page lists `4580458045804580`, `4580000000000000` and
  `4580111111111121`. The first is "suitable for testing failed transactions in multiple payments". Declines come from
  scenario configuration.
- A test may assert the envelope and `err.id`. On `createTransactionWithToken` it may also assert the published message
  and its class.
- `retryWithAnotherSource` is not set for Grow. `סופי`/`לא סופי` is the retryability class the indicator would carry
  (see `technical-ui-requirements.md`, "State Model").
- Growin wallet SDK (browser, out of scope) client messages: `onFailure` `{"status": 0, "message": "תשלום נכשל"}`
  (payment failed), and `onError` `{"status": 0, "message": "לא ניתן לבצע עסקת תשלומים על כרטיס מסוג זה"}` (instalments
  not possible on this card type) and `{"status": 0, "message": "התשלום סורב"}` (payment refused).

## Native Status Codes

Published enumeration (rule, parameter-mapping): `4` Canceled before transmission, `2` paid, `9` Transaction denied, `6`
Refund transaction, `0` Not paid. It has no `3` and no `11`.

| Code | Hebrew `status`  | In enumeration | In examples                                                                      | Simulator emits           |
|------|------------------|----------------|----------------------------------------------------------------------------------|---------------------------|
| `0`  | `לא שולם`        | yes            | the zeroed process row                                                           | unpaid process            |
| `2`  | `שולם`           | yes            | lookups, token charge, settle, the callback of an immediately charged process    | settled or charged        |
| `3`  | `תשלום שזוכה`    | no             | the refund response                                                              | refund row                |
| `11` | `עסקה מושהית`    | no             | the J4/J5 and token-only callbacks                                               | hold, saved token, expired hold |
| `4`  | none             | yes            | never                                                                            | never                     |
| `6`  | none             | yes            | never (conflicts with `3` for a refund)                                          | never                     |
| `9`  | none             | yes            | never                                                                            | never                     |

The simulator emits example values over enumeration values, and never a code that appears in neither. There is no
published `statusCode` for a decline, an expired hold (only `803`), or a partly refunded transaction.

## Expected Normalized Mapping

| Condition                                                                  | Normalized                                   |
|----------------------------------------------------------------------------|----------------------------------------------|
| created process, abandoned process (row `0`)                               | `NOT_INITIALIZED`                            |
| paid hold (`chargeType=2`), `11`                                           | `READY_FOR_CAPTURE`                          |
| token saved (`chargeType=3`), `11`                                         | `SUCCEEDED`                                  |
| stored-card charge or settle, `2`                                          | `SUCCEEDED`                                  |
| hold past `automaticVoidAfter`                                             | `CANCELLED`                                  |
| settle then full refund, or settle then void (`3`)                         | `REFUNDED`                                   |
| partial refund                                                             | `PARTIALLY_REFUNDED`, derived from the operations |
| decline or failed process                                                  | `FAILED`, with no `retryWithAnotherSource`   |
| unknown or malformed callback                                              | `UNKNOWN` or rejected                        |

- A partial refund needs clock control, because a same-day partial refund is `130` (see `testing-support-features.md`,
  "Time Travel / Clock Control").
- There is no `PARTIALLY_CAPTURED` for Grow, because the fate of an unsettled remainder is unpublished.
- A registered card never expires, while its 1-unit `11` transaction looks like a hold. The two are not reconciled.
- After one refund on the default allowance, the transaction stays `PARTIALLY_REFUNDED` with `refundsAccepted` false.
  An unsettled hold reports `refundableRemainder` 0.

## Merchant Callback Contract

Sent to `notifyUrl` as `application/x-www-form-urlencoded` (rule). Field sources: token-server-response,
delayed-payment-callback, the server-response-1 parameter table, and the one published wire body, which is Postman's
`NotifyURL Payload - Body Example`:

```text
err=&status=1&data%5Bstatus%5D=%D7%A2%D7%A1%D7%A7%D7%94%20%D7%9E%D7%95%D7%A9%D7%94%D7%99%D7%AA&data%5BstatusCode%5D=11&data%5BtransactionTypeId%5D=1&data%5BpaymentType%5D=2&data%5Bsum%5D=1.00&data%5BpaymentsNum%5D=1&data%5BallPaymentsNum%5D=1&data%5BpaymentDate%5D=26%2F8%2F21&data%5Bdescription%5D=&data%5BfullName%5D=%D7%90%D7%99%D7%99%D7%9C%20%D7%92%D7%A8%D7%A9%D7%95%D7%9F&data%5BpayerPhone%5D=0545283882&data%5BpayerEmail%5D=&data%5BcustomFields%5D%5BcField9%5D=a0d193523d15&data%5BtransactionId%5D=159955
```

Encoding, as read off that body: brackets are `%5B`/`%5D` in keys, a space is `%20` and never `+`, `/` is `%2F`, and an
empty value is `key=` rather than an omitted pair. The simulator uses RFC 3986 percent-encoding for keys and values: every
octet outside `A-Za-z0-9-_.~` becomes an upper-case triplet. The body does not show `*`, which the simulator encodes as
`%2A`, or `~`, which it sends literally (decision). Pairs are joined by `&` with no leading `&`.

Published wire order: `err`, `status`, `data[status]`, `data[statusCode]`, `data[transactionTypeId]`,
`data[paymentType]`, `data[sum]`, `data[paymentsNum]`, `data[allPaymentsNum]`, `data[paymentDate]`,
`data[description]`, `data[fullName]`, `data[payerPhone]`, `data[payerEmail]`, `data[customFields][cField9]`,
`data[transactionId]`, `data[transactionToken]`, `data[processId]`, `data[processToken]`.

Success order the simulator emits. Fields missing from the wire body are placed by the `approveTransaction` echo order.
The positions of `cardToken` and `payerBankAccountDetails` are invented:

`err`, `status`, `data[status]`, `data[statusCode]`, `data[sum]`, `data[firstPaymentSum]`, `data[periodicalPaymentSum]`,
`data[paymentsNum]`, `data[allPaymentsNum]`, `data[paymentDate]`, `data[asmachta]`, `data[description]`,
`data[fullName]`, `data[payerPhone]`, `data[payerEmail]`, `data[cardToken]` (only with `saveCardToken=1`),
`data[cardSuffix]`, `data[cardType]`, `data[cardTypeCode]`, `data[cardBrand]`, `data[cardBrandCode]`, `data[cardExp]`,
`data[payerBankAccountDetails][bankNum]`, `[branchNum]`, `[accountNum]`, `[accountName]` (all empty),
`data[customFields][cField1]` through `[cField9]` (those sent), `data[transactionId]`, `data[transactionToken]`,
`data[processId]`, `data[processToken]`.

| Field group                                                                                                       | Evidence                                                         |
|-------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| `err`, `status`, `data[status]`, `[statusCode]`, `[sum]`, `[paymentsNum]`, `[allPaymentsNum]`, `[paymentDate]`, `[description]`, `[fullName]`, `[payerPhone]`, `[payerEmail]`, `[customFields][...]`, `[transactionId]`, `[transactionToken]`, `[processId]`, `[processToken]` | wire body                                          |
| `[asmachta]`, `[cardToken]`, the six card descriptors, `[firstPaymentSum]`, `[periodicalPaymentSum]`, `[payerBankAccountDetails][*]` | JSON renderings on the per-flow pages only             |
| `[transactionTypeId]`, `[paymentType]`                                                                            | in the wire body, and **not sent** by the simulator              |

- The SDK callback page renders `payerBankAccountDetails` as an object with four empty members, and the regular-payment
  page renders it as `""`.
- `err` is present and empty on success.
- Failure: `status=0`, `err[id]`, `err[message]`, `data=`. Grow's example is
  `{"status": 0, "err": {"id": 701, "message": "..."}, "data": ""}` (token-server-response). The simulator sends `701`
  with `פרמטר קוד זיהוי אינו תקין userId`.
- `customFields` is an empty array in Grow's J4/J5 example when no fields were sent.

Code tables (rule, parameter-mapping). Unknown values pass through:

| Table              | Values                                                                                                |
|--------------------|-------------------------------------------------------------------------------------------------------|
| `cardTypeCode`     | `1` local, `2` foreign, `3` fuel, `5` gift/rechargeable. Every `approveTransaction` schema says `1-Local \| 2-Foreign \| 3-Fuel \| 4-Debit \| 5-Gift/Rechargeable` |
| `cardBrandCode`    | `2` Mastercard, `3` Visa, `5` Isracard, `7` Discover, `8` Diners                                       |
| `transactionTypeId`| `1` credit card, `5` Paybox, `6` Bit, `13` Apple Pay, `14` Google Pay, `15` bank transfer              |

Flow discrimination:

- `statusCode` `11` is returned for both the hold and the token-only callback, so it cannot tell them apart.
- `data[cardToken]` cannot tell them apart either, because this contract sends `saveCardToken=1` on both.
- The discriminator is `data[customFields][cField2]` (decision): absent means a token was saved and no funds are held,
  and present means a hold is ready for capture. A merchant that sends no `cField2` has no discriminator.
- `statusCode` `2` in a callback belongs to `chargeType=1`, which is out of scope. It is published for Bit on the SDK
  and regular-payment pages (`transactionTypeId` `6`), and for card (`1`) on payment-request-callback and
  recurring-payment-callback.

Callback outcomes: success without `cField2` saves a token, success with `cField2` marks the hold ready for capture, and
failure marks the process failed. Authenticity: no `P_SIGN`, HMAC or signature header in provider-compatible mode.
`cField1` is an opaque merchant token and is echoed exactly. Negative scenarios: wrong `cField1`, token mismatch,
unknown process or transaction, duplicate callback, delayed callback, and a missing `approveTransaction`.

A test must not treat the redirect as the source of truth. A payer who never reaches the thank-you page does not affect
the server-side outcome (rule, the-process).

## Error Scenarios

Source: https://developers.grow.business/reference/errors unless marked. "There might be more", so the table is not
exhaustive, and a scenario may emit any numeric code (with an empty message when none is recorded).

| Code   | Wire text                                                                                           | Gloss                                                           | Emitted by the simulator                                        |
|--------|-----------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|-----------------------------------------------------------------|
| `54`   | table: `חסרים שדות חובה : (parameter name)`. Wire: `חסרים נתונים:<field>`                           | missing required fields                                         | missing field, every endpoint, with the wire text               |
| `104`  | `משתמש זה אינו קיים במערכת` (probe, not in table)                                                  | user does not exist                                             | token charge and retrace, no `transactionUniqueIdentifier`      |
| `105`  | `לא ניתן לבצע זיכוי על סכום הגדול מהסכום המקורי`                                                   | refund above the original                                       | refund excess (default)                                         |
| `115`  | `אין אשראי מספק לביצוע הזיכוי. אנא פנה לשירות הלקוחות בטלפון 3144*.`                               | no balance for the refund, call customer service                | `merchantBalance=EMPTY`                                         |
| `130`  | `לא ניתן לבצע זיכוי חלקי על עסקה שבוצעה היום`                                                      | partial refund of a same-day transaction                        | refund                                                          |
| `140`  | `לא ניתן לבצע תשלום עם טוקן, קוד מזהה טוקן אינו תקין` (example, not in table)                     | invalid token                                                   | token charge, retrace                                           |
| `170`  | `עסקה לא קיימת`                                                                                     | transaction does not exist                                      | no                                                              |
| `180`  | `פעולת עידכון לא בוצעה, רשומה לא נמצאה`                                                             | record not found                                                | `approveTransaction`                                            |
| `190`  | `לא ניתן לבצע זיכוי, סכום הזיכוי גדול מסכום החיוב`                                                 | refund above the charge                                         | `refundExcessRejection=CHARGE_AMOUNT`                           |
| `207`  | `לא ניתן לבצע זיכוי חלקי כי העסקה עדיין לא שודרה`                                                  | partial refund before transmission                              | no                                                              |
| `210`  | `בוצע זיכוי לעסקה זו, לא ניתן לזכות בשנית.`                                                        | already refunded                                                | refund allowance used                                           |
| `215`  | `הסכום לזיכוי אינו תקין`                                                                           | refund amount not valid                                         | refund                                                          |
| `218`  | `סכום הזיכוי גדול מסכום העסקה`                                                                     | refund above the transaction                                    | `refundExcessRejection=TRANSACTION_AMOUNT`                      |
| `300`  | `בעל עסק זה אינו מורשה לשירות API`                                                                 | business not authorised for the API                             | no                                                              |
| `701`  | table: `פרמטר קוד זיהוי אינו תקין: userId / pageCode`. Wire: `פרמטר קוד זיהוי אינו תקין userId`     | identification code invalid                                     | `createPaymentProcess` and the failure callback, with the wire text |
| `707`  | `הסכום אינו תקין`                                                                                  | amount not valid                                                | amount on create, settle, token charge                          |
| `712`  | `העסקה כבר בוצעה`                                                                                   | already carried out                                             | duplicate unique or group identifier (decision)                 |
| `714`  | `הגישה נחסמה`                                                                                       | access blocked                                                  | no                                                              |
| `715`  | `אין הרשאה לעסק לצפות בעסקה זו`                                                                    | not authorised to view this transaction                         | no                                                              |
| `716`  | `פרמטר קוד עסקה או תוקן אינו תקין`                                                                 | transaction code or token invalid                               | lookups, refund                                                 |
| `722`  | `לא ניתן לבצע אישור לעסקה שלא בוצעה או בוטלה`                                                      | cannot approve a transaction not done or cancelled              | no                                                              |
| `723`  | `apiKey שדה חובה`                                                                                   | `apiKey` required                                               | no                                                              |
| `724`  | table: `אירעה שגיאה בטעינת נתונים אנא חזור לבחירת אמצעי תשלום מחדש נדרש שדה userId או pageCode`. Wire: `נדרש שדה userId או pageCode` | data-loading error, `userId` or `pageCode` required | refund with neither credential, wire text      |
| `730`  | `פרמטר קוד תהליך או תוקן אינו תקין`                                                                 | process code or token invalid. In no example, returned live     | `cancelBitTransaction`, and process lookup by setting           |
| `731`  | `קוד pageCode אינו תואם את קוד ה-pageCode שאיתו בוצעה הזיהוי`                                       | `pageCode` does not match the identifying one                   | no                                                              |
| `736`  | `לא ניתן לשלוח את הפרמטרים paymentNum ו-maxPaymentNum באותה קריאה`                                  | both instalment fields                                          | `createPaymentProcess`                                          |
| `740`  | `החשבון מוגדר עם סוג תשלום רגיל בלבד, לא ניתן לשלוח פרמטר maxPaymentNum או paymentNum.`             | regular-payments-only account                                   | no                                                              |
| `743`  | `אין עסק תואם ל userId שנשלח` (probe, not in table)                                                | no business for the `userId`                                    | settle                                                          |
| `801`  | `קוד זיהוי עסק אינו תקין`                                                                           | business identification code invalid                            | refund with an unknown `userId`                                 |
| `802`  | `קוד עסקה או טוקן אינו תקין`                                                                        | near-twin of `716`. Assigned to no endpoint, and every probe of an unknown transaction answered `716` | no            |
| `803`  | `J5 פג תוקף`                                                                                        | J5 expired                                                      | settle past a deadline                                          |
| `804`  | `לא ניתן לשחרר עסקה בסכום הגבוה מתפיסת המסגרת`                                                     | above the frame capture                                         | settle ceiling (default)                                        |
| `808`  | `לא ניתן לבצע שחרור לעסקה ששוחררה`                                                                 | already released                                                | settle                                                          |
| `809`  | `שחרור התשלום נכשל`                                                                                 | release failed, no published trigger                            | no                                                              |
| `810`  | `לא ניתן לבצע שחרור לסוג עסקה זה`                                                                  | type cannot be released                                         | settle of a non-hold                                            |
| `812`  | `קוד זיהוי חברה אינו תקין (apiKey)`                                                                 | company code (`apiKey`) invalid                                 | no                                                              |
| `813`  | `לא ניתן לבצע חיוב לעסקה שלא בוצעה`                                                                | charge of a transaction not carried out                         | settle of an unknown transaction, refund of an unsettled one (decision) |
| `814`  | `הסכום המירבי לביצוע החיוב הוא עד 30% יותר מסכום העסקה.`                                            | at most 30% above                                               | `settlementCeiling=THIRTY_PERCENT_ABOVE`                        |
| `819`  | `אין באפשרותך לבצע תשלום כעת  \נסה בשלב יותר מאוחר` (example, not in table)                        | cannot pay now, try later                                       | by scenario, with an empty message (see "Settle Suspended Transaction") |
| `880`  | `לא נמצא עסק מתאים לUserId` (probe, not in table)                                                  | no business for the `UserId`                                    | token charge, retrace                                           |
| `890`  | `לא ניתן לשלם בסכום הנמוך מ- 0`                                                                     | amount below 0                                                  | no. The probe answered `707` for a negative or zero amount      |
| `1013` | see "Invalid Credentials" (probe, not in table)                                                    | identifier not a 32-bit whole number                            | token charge, retrace                                           |
| `103`, `451`, `452`, `453` | see "Declines" (token page, not in this table)                                          | token-charge declines                                           | by scenario                                                     |
| `440`  | no text recorded (probe)                                                                            | transaction not found                                           | no                                                              |
| `403`  | `Forbidden - לא נשלח X-API-KEY`                                                                     | a table row that is also an HTTP status. It belongs to the `x-api-key` endpoints | no                               |

Required scenarios with no published code, and how the simulator answers them:

| Scenario                                          | Simulator                                      |
|---------------------------------------------------|------------------------------------------------|
| duplicate `transactionUniqueIdentifier`           | `712` (decision)                               |
| duplicate `transactionGroupIdentifier`            | `712` (decision)                               |
| full refund of an unsettled hold                  | `813` (decision)                               |
| zero or negative amount                           | `707` (published row; the probe answered it, not `890`) |
| unsupported currency                              | none: Grow has no currency parameter           |
| transient HTTP or network error, malformed `data` or `err` | scenario faults                       |

## Seeded Personas

Grow publishes **no persona for any intent**, and the catalogue says so per intent rather than offering one
(`GrowPersonas.NO_INSTRUMENT_A_PERSONA_COULD_SEED`). A test run of this provider therefore starts with nothing to pay
with, which is Grow's contract and not an omission.

| Fact                                                                                                                | Why                                                                              |
|---------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| A persona seeds an instrument and hands over the provider-native ids that instrument answers to. Grow reads none     | a `cardToken` is minted only where a hosted process saved a card, and `createTransactionWithToken` resolves one against the process that saved it (`GrowFixtureCards.A_CARD_TOKEN_IS_MINTED_BY_A_SAVED_PROCESS`) |
| A Grow persona could still write this run's scenario rules and seed a card with no token                             | and `createTransactionWithToken` would answer `err.id 140` for it, so the persona would hand a tester an instrument no Grow endpoint could charge |
| Instead: press the preset on the Scenarios screen, then take a card through the hosted process                       | the only path Grow publishes to a `cardToken`                                    |
| The `cardToken` that process mints is printed on the process object as `relatedObjects.cardToken`                    | the callback to the merchant's own endpoint is the only place it reaches the wire and the trail withholds it, so the Payments screen is where a tester reads the value `createTransactionWithToken` and `getTokenTransactionsByExternalIdentifiers` require (`GrowProcessObject.THE_TOKEN_A_SAVED_PROCESS_MINTED_IS_READ_FROM_ITS_OBJECT`) |
| A tester who only clicks reaches `createTransactionWithToken` without reading that value at all                      | the third way of starting a payment suggests the cards this run saved on the box the token is typed into, so a saved card is charged without typing one and a token grow never saved reaches `err.id` 140 (see "Create Stored-Card Transaction") |
| A tester who only clicks reaches `getTokenTransactionsByExternalIdentifiers` too, on the **Look a payment up** screen | Grow declares the retrace as a read of its own, and that screen picks the saved card out of what this run minted and asks for the merchant's own identifier (see "Retracing a Token Payment by the Caller's Own Identifier") |

## What Still Cannot Be Trusted Here

- **No callback event vocabulary exists.** Grow's callback body has no event name, event id or attempt number. Every
  Grow event name in this simulator is its own, and an attempt override has nothing to act on.
- **The setting name `callbackAcknowledgement` is invented.** Waiting for approval is a property of the merchant's Grow
  account, and Grow publishes no name for it.
- **No `getTransactionInfo` example exists for a token charge.** Its shape is unwitnessed.
- **The same-day full refund's status is unpublished.** Every void lands on it.
- **Every probe was made without matching credentials.** Nothing about a successful call was observed.
- **Nothing is published for a request grow never read.** An address under `/api/light` that grow does not serve, a
  method one of its addresses does not answer, and a failure inside the gateway have no code and no status in the
  error table; the one published status rule is that every answer on the API routes is HTTP 200, which means the
  operation ran. The simulator answers these in `text/plain` of its own and never in the grow envelope
  (`design-constraints.md`, "No shared error rendering and no shared exception handling over provider routes").
