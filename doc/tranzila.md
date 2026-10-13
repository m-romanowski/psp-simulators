# Tranzila Simulator Contract

## What This File Covers

The vendor contract for Tranzila's card API, its DirectNG payment page, Hosted Fields, Handshake V2, 3-D Secure over the
API and the two transaction lookups, and what this simulator answers on each route.

Each statement names its evidence class (`README.md`, "Evidence Classes"): published rule, published example, published
schema (the OpenAPI YAML behind a reference page), vendor script (Tranzila's own JavaScript at `hf.tranzila.com`),
vendor correspondence (Tranzila's written answer to a merchant questionnaire, not documentation, the weakest class),
inference, simulator decision. No live probe: no sandbox host, test terminal or self-serve credential is published.

| Route                                                         | What the simulator does                                                                                       | Key deviations                                                                                           |
|---------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `POST /v1/transaction/credit_card/create`                      | `debit`, `verify` (`verify_mode` `2`, `5`), `force`, `reversal`, `credit`, `cancel`; 3-D Secure on `activate_3ds=Y` | amount from `items` only; item discount and VAT not applied; `sto`, `verify_mode` `6` and instalments refused |
| `POST /v1/transaction/credit_card/3ds/complete`                | finishes a challenged 3-D Secure transaction by `track_id`                                                     | challenge answer shape is a simulator decision                                                           |
| `POST /v2/handshake/create`                                    | mints a `thtk` bound to terminal and `sum`, 20 minutes                                                         |                                                                                                          |
| `POST /v1/transactions`                                        | lookup by `transaction_index` or date range                                                                   | amount units unpublished                                                                                 |
| `GET`/`POST /tranzila/directng/{terminal}/iframenew.php`       | the payer page: tranmodes `A`, `V`, `N`, `K`, `AK`, `VK`, `NK`; browser return and notify                      | the page is the simulator's own; notify unsigned                                                         |
| `/tranzila/hf/...`                                             | the simulator's own implementation of the `TzlaHostedFields` API, its frames and its process route           | never a copy of the vendor's script                                                                      |
| `POST /tranzila/report/v1/transaction`                         | lookup by `transaction_index` or date range, amounts in minor units                                           | `ufields`, `dfields` refused                                                                             |
| STO / My Billing, Bit, PayPal, MASAV, invoices, EMV PinPad, payment requests, legacy CGI, `direct.tranzila.com`, Handshake V1, instalments | **not served** |                                                                                     |

Account settings that choose between vendor contradictions or supply what the vendor leaves open:

| Setting                                  | Values (default first)                                                         | Chooses between                                                                                   |
|------------------------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `tranzila.access-token`                  | `AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT`, `AS_THE_QUICKSTART_SIGNS_IT`         | the two published HMAC constructions ("Authentication and Transport")                             |
| `tranzila.authentication`                | `THE_ACCESS_TOKEN_IS_VERIFIED`, `THE_ACCESS_TOKEN_IS_NOT_VERIFIED`             | simulator decision: permissive auth for local tests; the app key must still name the account      |
| `tranzila.verification-approval-code`    | `777`, `000`                                                                   | the published code table (`777`) and vendor correspondence (`000`) for an approved J2 or J5       |
| `tranzila.j5-validity`                   | `P7D`; any ISO-8601 duration                                                   | simulator decision: J5 hold validity is unpublished ("acquirer-defined", vendor correspondence)   |

## Sources

`PB/` is `https://docs.tranzila.com/docs/payments-and-billing/`, `RP/` is `https://docs.tranzila.com/docs/reports/`.
Section names are Tranzila's.

| Page                              | URL                                                                     | Sections used                                                                                             |
|-----------------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Tranzila Transactions API         | `PB/tranzila-transactions-api-1/create-a-credit-card-transaction`       | "Recommended Integration Workflow", "Transaction Types (`txn_type`)", "Prevent Duplicate Transactions (`DCdisable`)", "Card Entry Mode", "Installments", "Code Examples", schema |
| Tranzila API (hidden, in sitemap) | `PB/tranzila-api/create-a-credit-card-transaction`                      | examples and schema, contradictions only                                                                   |
| Authentication                    | `PB/authentication`                                                     | header table, "Create a Valid Access Token"                                                               |
| Quickstart                        | `https://docs.tranzila.com/docs/quickstart`                             | "Create a payment request" (signing only)                                                                 |
| Transaction Response Codes        | `PB/transaction-response-codes`                                         | tabs "SHVA Errors (English)", "שגיאות שב"א (Hebrew)", "3DS Errors (English)"                               |
| Tranzila Api 3DS                  | `PB/tranzila-api-3ds/create`, `PB/tranzila-api-3ds/complete`            | "3DS Process", schema, complete example                                                                   |
| Handshake V2                      | `PB/handshake-v2/createhandshakev2`                                     | schema, examples                                                                                          |
| Iframe Integration new DirectNG   | `PB/iframe-integration-directng`                                        | "Base URL", "Data Transfer Methods", "Receiving the Response", "Key Response Fields", "Data Retrieval", "iframe parameters table", "Notify Url", "Preventing Duplicate Transactions" (DCdisable, Handshake) |
| Iframe Integration (legacy)       | `PB/iframe-integration`                                                 | iframe parameters table (tranmode combinations), Dtran                                                    |
| Hosted Fields                     | `PB/hosted-fields`                                                      | "Getting started", "Using TzlaHostedFields.create method", "Using Submit Handler", "3DS TRANSACTIONS", "Test Credit Card Numbers for 3DS", "Request Keys", "Response Keys", "Response Examples", "Error Messages", "Response Hash" |
| Hosted Fields scripts             | `https://hf.tranzila.com/assets/js/thostedf.js`, `.../hfields_depend/ops_controlfield.js` | read for the public API and wire shapes; not served                                        |
| Reports authentication            | `RP/authentication`                                                     | "Required Headers", "Generating a Valid Access Token"                                                     |
| Transaction Reports API           | `RP/tranzila-transaction-reports-api/gettransactions`                   | schema, examples                                                                                          |
| Track Transaction Data            | `RP/track-transaction-data/get-transaction-information`                 | schema, examples                                                                                          |
| Notify guides                     | `https://docs.tranzila.com/docs/vibe-coding/closing-an-order-in-base44-using-notify-page`, `.../tranzila-lovable-notify-page-guide` | notify encoding, acknowledgement, fields |

The Transactions API page is linked from the navigation but absent from `sitemap.xml`; the hidden Tranzila API page is in
the sitemap and carries a deprecation notice with sunset date `2027-01-01`. The visible page governs here.

## Hosts and Paths

| Vendor host                      | Served at                                  | Evidence                                                                                 |
|----------------------------------|--------------------------------------------|------------------------------------------------------------------------------------------|
| `https://api.tranzila.com`       | the root, at the vendor's own paths        | published schema (`servers` `https://api.tranzila.com/v1`, `/v2`)                         |
| `https://directng.tranzila.com`  | `/tranzila/directng`                       | published rule ("Base URL"); `direct.tranzila.com` is "Legacy Deprecated"                 |
| `https://hf.tranzila.com`        | `/tranzila/hf`                             | published rule ("Getting started")                                                       |
| `https://report.tranzila.com`    | `/tranzila/report`                         | published schema                                                                         |

Simulator decision: the three non-API hosts sit under a prefix because the payment page's first path segment is the
terminal name, which at the root would shadow every other route. The pairing screen publishes one base URL per host,
and route aliases (`README.md`, "Route aliases") map the vendor's absolute URLs.

## Authentication and Transport

Four headers on every `api.tranzila.com` and `report.tranzila.com` call (published rule, `PB/authentication` header
table; published schema, `required: true`): `X-tranzila-api-app-key`, `X-tranzila-api-request-time`,
`X-tranzila-api-nonce`, `X-tranzila-api-access-token`. Bodies are `application/json`.

| Element        | Published                                                                                                                         | Simulator                                                                                  |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| access token   | "hash_hmac using 'sha256' on application key with secret + request-time + nonce" (published rule); PHP `hash_hmac('sha256', $appKey, $secret . $time . $nonce)` (published example); `RP/authentication`: "message: app_key, key: secret + timestamp + nonce", "Output must be hex-encoded" | default construction                                                                       |
| quickstart     | `printf "%s%s%s" "$TRANZILA_APP_KEY" "$REQUEST_TIME" "$NONCE" \| openssl dgst -sha256 -hmac "$TRANZILA_APP_SECRET"`: message app key + time + nonce, key the secret; `REQUEST_TIME=$(date +%s%3N)` (published example) | `AS_THE_QUICKSTART_SIGNS_IT`: that construction, request time in milliseconds              |
| hex case       | Handshake V2: "Result must be a lowercase hex string" (published schema); the .NET sample emits `ToString("X2")`, uppercase (published example) | compared case-insensitively                                                                |
| request time   | "Unix format (integer counting seconds…)" (published rule); reports: "Must be within a few minutes of server time" (published schema) | simulator decision: ±5 minutes on the run clock, because "a few minutes" is the only bound published |
| nonce          | "A 40 bytes NONCE – unique random string" (published rule); PHP `bin2hex(random_bytes(40)) //actually 80 characters string`; quickstart `openssl rand -hex 20`; reports: "80-character random hexadecimal string generated fresh for each session" and "Generate once and reuse"; Handshake V2: "The nonce is unique per request" | any non-empty value; on `api.tranzila.com` a nonce reused within the tolerance is refused `20000`, because the auth page says the token "prevent[s] processing of the same request more than once"; on `report.tranzila.com` reuse is accepted, because that host's own header says "Generate once and reuse" |
| secret         | "you cannot call Tranzila API from within web client application" (published rule)                                                | the account's secret; never answered back                                                  |

`THE_ACCESS_TOKEN_IS_NOT_VERIFIED` leaves the token, request time and nonce unchecked; the app key must still name the
account. Tranzila publishes no such mode.

### Duplicates and DCdisable

No idempotency key or header is published. Duplicate protection is `DCdisable` (published rule, "Prevent Duplicate
Transactions"; DirectNG "DCdisable"):

| Rule                                                                                       | Evidence           | Simulator                                                          |
|--------------------------------------------------------------------------------------------|--------------------|--------------------------------------------------------------------|
| API: `user_defined_fields: [{"name":"DCdisable","value":"…"}]`; page: parameter `DCdisable` | published rule     | both read                                                          |
| "The duplicate check is performed up to 24 hours back", "at the parent terminal level"     | published rule     | 24 hours on the run clock, per account                             |
| "If the original transaction failed, `DCdisable` will not prevent a retry"                  | published rule     | only an approved original blocks                                   |
| maximum 254 characters; `22103` "Invalid DCdisable"                                        | published rule, schema | over 254 → `22103`                                             |
| duplicate messages "This payment has already been made, please contact the business" / "This payment has already been paid. Please contact customer service." | published rule | API: `200`, `error_code` `0`, the first message, and the original's `transaction_result`; no second transaction |
| DCdisable is not a universal idempotency key for JSON calls                               | vendor correspondence | not applied to any route but create and the payment page        |

The duplicate answer is a simulator decision: no shape is published, and Tranzila's only published duplicate answer
(Dtran, legacy CGI) is "the original response" plus a flag, so a client that retried after a timeout gets the
transaction it already made.

## Minimum API Surface

### Create a Credit Card Transaction

`POST /v1/transaction/credit_card/create`. Required: `terminal_name`, `expire_month`, `expire_year`, `card_number`
(published schema).

Fields read:

| Field                       | Published                                                                                     | Simulator                                                                   |
|-----------------------------|-----------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `terminal_name`             | string                                                                                        | must name the account the app key names, else `401` `20002`                 |
| `txn_type`                  | `debit`, `credit`, `verify`, `force`, `cancel`, `reversal`, `sto`; default `debit`            | `sto` refused `20004`                                                       |
| `verify_mode`               | integer `2`, `5`, `6`                                                                         | `6` refused `20004`; required with `verify`                                 |
| `card_number`               | string, 8–19; a token goes here: "A Tranzila token replaces **only the full card number**"    | a token minted by this run, or a PAN; an unknown token → `20111`            |
| `expire_month`, `expire_year` | integer 1–12; integer, example `2025`                                                       | required, as the schema says, also with a token                             |
| `cvv`                       | string 3–4, "can be null"                                                                     | optional; answered in `Responsecvv`                                         |
| `card_holder_id`            | string, length 9                                                                              | optional; answered in `Responseid`                                          |
| `txn_currency_code`         | ISO 4217 alpha-3, default `ILS`                                                               | the Transactions API enum                                                                |
| `items[]`                   | required per item `name`, `units_number` (integer), `unit_price` (number)                     | the amount ("Amount")                                                       |
| `reference_txn_id`          | integer                                                                                       | names the original for `force`, `reversal`, `credit`, `cancel`              |
| `authorization_number`      | string; "REQUIRED when txn_type is `credit`, `reversal` or `cancel` … Ignored for other transaction types" | see "Per Transaction Type"                                       |
| `payment_plan`              | `1`, `6`, `8`; default `1`                                                                    | anything but `1` refused `20004` (instalments out of scope)                 |
| `pan_entry_mode`            | `50`, `51`, `52`                                                                              | accepted, echoed                                                            |
| `user_defined_fields[]`     | `{name, value}`                                                                               | `DCdisable` read; all stored and answered by the lookups                    |
| `client`, `remarks`, `response_language`, `created_by_user`, `created_by_system` | published schema                        | accepted, echoed in `original_request`                                      |
| `activate_3ds`, `3ds_settings` | 3DS schema                                                                                 | "3-D Secure"                                                                |

Simulator decision: an out-of-scope value the schema admits (`sto`, `verify_mode` `6`, `payment_plan` `6`/`8`) is
refused `20004`, so a client learns it was not performed instead of reading a success.

Simulator decision: a body field outside the Transactions API schema is a schema mismatch, so `verfiy_mode` (the hidden
page's spelling) is refused `20004` rather than silently read; the visible page is the current specification.

Success (published schema, `200`; field names published, values illustrative):

```json
{
  "error_code": 0,
  "message": "success",
  "transaction_result": {
    "processor_response_code": "000",
    "transaction_id": 12345,
    "auth_number": "0587923",
    "Responsecvv": "1",
    "Responseid": "0",
    "card_type": 2,
    "card_type_name": "Visa",
    "currency_code": "ILS",
    "expiry_month": 7,
    "expiry_year": 25,
    "payment_plan": 1,
    "token": "O5d55d2922ca4021382",
    "last_4": "4245",
    "card_mask": "458021xxxxxx4245",
    "card_locality": "domestic",
    "amount": 10.53,
    "txn_type": "debit",
    "tranmode": "A"
  },
  "original_request": {}
}
```

- `transaction_id` integer, `expiry_year` YY, `currency_code` ISO alpha-3, `amount` "transaction amount in currency"
  (published schema).
- `Responsecvv` / `Responseid`: `0` not entered, `1` entered and valid, `2` invalid, `3` not checked (published schema).
  Simulator decision: `1` when sent, `0` when not, unless a scenario says otherwise.
- `token`: "applies only to tokens service subscribers" (published schema). Simulator decision: always answered, as for
  a subscribed terminal, because a run's later `force`, `credit` and repeat charges need it.
- `original_request` is "The original JSON sent in the request"; the simulator masks `card_number` and answers `cvv` as
  `***`, as the 3DS complete example does.

### Amount

There is no top-level amount; the request schema has no amount field (published schema). The amount is Σ
`unit_price` × `units_number` over `items`, in decimal currency units (`unit_price` `10.53`, published example). The
simulator keeps the lexical form of the sum.

Deviation: `discount`, `discount_type`, `vat_percent`, `price_type` and `to_txn_currency_exchange_rate` are accepted and
not applied, because their arithmetic is unpublished.

### Per Transaction Type

| `txn_type`           | Published                                                                                                   | Simulator                                                                                                                       |
|----------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `debit`              | "Charges the card" (published rule)                                                                         | approved `000`, or the scenario's decline                                                                                       |
| `verify` + `2`       | "J2 verification. Checks the card without capturing funds."                                                 | approved at `tranzila.verification-approval-code`; nothing held                                                                 |
| `verify` + `5`       | "J5 authorization. Reserves credit for the specified amount. The actual charge is later performed using `txn_type=force`." | approved at `tranzila.verification-approval-code`; a hold lives `tranzila.j5-validity` on the run clock               |
| `force`              | J5 capture; example sends `reference_txn_id`, card, items, no `authorization_number`                        | `reference_txn_id` must name a live J5 of the same terminal; one `force` per J5; amount ≤ J5 amount else processor code `507`   |
| `reversal`           | "Releases a reserved credit amount from a J5 authorization transaction (`verify_mode=5`)"                   | releases a live, unforced J5 in full                                                                                            |
| `credit`             | "Credits funds back to the cardholder"; `23002` "sum is larger than the original debit transaction"         | against a `debit` or `force`; partial and repeated credits while their total ≤ the original, else `23002`                       |
| `cancel`             | "Cancels a `debit` or `credit` transaction"; example carries no card, expiry or items                       | cancels a `debit`, `force` or `credit` in full; card fields not required                                                        |

Reference rules (`20112`, `21100`, `21101`, `22100`, `22101`, `23001`, `23002` are published codes; which condition
answers which is a simulator decision unless the code's own text says it):

| Condition                                                                  | Answer                                                     |
|----------------------------------------------------------------------------|------------------------------------------------------------|
| `reference_txn_id` names no transaction of this terminal                   | `20112` "Original transaction not found"                   |
| `reference_txn_id` missing on `force`, `reversal`, `credit`, `cancel`       | `21101` "Provided index was empty"                         |
| `authorization_number` empty on `credit`, `reversal`, `cancel`              | `22101` "Empty authorization number"                       |
| `authorization_number` differs from the original's `auth_number`            | `22100` "Authorization number is not equal to the transaction value" |
| `authorization_number` on `force`                                           | optional; checked as above when present                    |
| `credit` of a cancelled transaction                                         | `23001` "Attempting to credit a transaction that has already been canceled" |
| `credit` total above the original                                           | `23002`                                                    |
| `credit` with a different card than the original                            | `20112` ("attempt to credit a different card")             |
| `force` or `reversal` of a J5 already forced, reversed or past validity     | `20112` (an "uncreditable origin")                         |
| `cancel` of a transaction already cancelled                                 | `20112`                                                    |

- Partial `force` (lower than the J5 amount) is allowed and closes the J5: "partial force is supported" (vendor
  correspondence); nothing published contradicts it, and multiple captures per J5 are unpublished.
- `force`, `reversal` and a token `debit` need no CVV (vendor correspondence; the schema makes `cvv` optional anyway).
- A J5 past `tranzila.j5-validity` answers `20112` because the hold no longer exists to act on; validity is
  "acquirer-defined" (vendor correspondence) and unpublished.

### Response Codes

`processor_response_code` carries a code of the published table (`PB/transaction-response-codes`, "SHVA Errors
(English)"); "Those are application error codes, response code will be **success (200)**". The simulator answers this
subset:

| Code  | Published text                                                                                       | When                                          |
|-------|------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| `000` | "Transaction approved."                                                                              | approved `debit`, `force`, `credit`, `cancel`, `reversal` |
| `777` | "operation completed (success code for operations in which no transaction is recorded, including J2 and J5)" (Hebrew tab, translated) | approved J2 or J5 at the default setting |
| `507` | "actual transaction amount higher than the approved amount" (Hebrew tab, translated)                | `force` above the J5 amount                   |
| `001`, `002`, `003`, `004`, `005`, `006`, `012`, `015`, `016`, `017` | blocked, stolen, call the credit company, refusal, forged, incorrect ID/CVV, card not authorised for the terminal, expired, unauthorised currency, unauthorised credit type | a scenario's decline |

`shva` ("Pending SHVA response.") and `010` ("Partial confirmation") are never answered: no published read resolves a
pending answer, and no published field carries a partially confirmed amount.

Simulator decision, decline envelope: `200`, `error_code` the processor code as an integer (`4` for `004`, `507`),
`message` the published English text, and `transaction_result` with `processor_response_code` and `transaction_id`.
No decline body is published; `error_code` is "0 - success, others - failure" (published schema), and the code table is
the only failure vocabulary.

### Application Error Codes

Answered at HTTP `200` with `{"error_code": <integer>, "message": "<text>"}` (published schema, "The response can be 200
with an Application error code"):

| Code    | Published text                                                                     |
|---------|------------------------------------------------------------------------------------|
| `20111` | Provided token check failure                                                       |
| `20112` | Original transaction not found; also: linked to an uncreditable origin, or crediting a different card |
| `21100` | Transaction index is not equal to original transaction index                       |
| `21101` | Provided index was empty                                                           |
| `22100` | Authorization number is not equal to the transaction value                         |
| `22101` | Empty authorization number                                                         |
| `22103` | Invalid DCdisable                                                                  |
| `23001` | Original transaction authorization number is invalid; also: crediting a transaction already canceled |
| `23002` | Already Credited this transaction id; also: sum is larger than the original debit transaction |
| `900`   | Transaction failed during 3D Secure validation (3DS spec)                          |

`21100` is never answered: no request field carries a second index to compare.

### 3-D Secure

Create with `activate_3ds: "Y"` and `3ds_settings` (`browser`, `force_txn_on_3ds_fail` default `N`, `force_challenge`
`0`/`1`, `auth_3ds_redirect`) (published schema). Three published outcomes ("3DS Process"): authorised frictionlessly
with 3DS statuses; not authorised, which ends the flow unless `force_txn_on_3ds_fail` is `Y`; or a challenge at
`challengeUrl`, whose result "would be redirected to the address … `uth_3ds_redirect` object, `url` attribute".

| Outcome       | Simulator                                                                                                                       |
|---------------|---------------------------------------------------------------------------------------------------------------------------------|
| frictionless  | the normal create answer plus `t3ds_data`                                                                                       |
| failed        | `200`, `error_code` `900`; with `force_txn_on_3ds_fail` `Y`, the normal create answer without 3-D Secure                        |
| challenge     | `200`, `{"error_code": 0, "message": "success", "challengeUrl": "…", "track_id": "…"}`; `challengeUrl` names this simulator's challenge page |

Simulator decision: the challenge answer's shape is unpublished (no `200` schema for create); the two names are the
prose's `challengeUrl` and the complete body's `track_id`. The challenge page returns the browser to
`auth_3ds_redirect.url` with its `params`, read in either published shape (array of `{key, value}` in `3ds_settings`, or
top-level `{url, params}`).

Complete: `POST /v1/transaction/credit_card/3ds/complete`, `{track_id, terminal_name}` (published schema). Answers the
published example's shape: `transaction_result` with string values (`transaction_id: "372803"`, `card_type: "2"`,
`expiry_year: "25"`), `t3ds_data` `{version, statusCode, statusMessage, xid, cavv, eci}`, and `original_request` with a
masked card. A `track_id` already completed or unknown → `900`.

Which card takes which outcome: the published test cards (`PB/hosted-fields`, "Test Credit Card Numbers for 3DS"),
otherwise the run's scenario:

| Card               | Published outcome                  |
|--------------------|------------------------------------|
| `4907639999909022` | frictionless success               |
| `4907639999990022` | frictionless fail                  |
| `4918914107195005` | challenge; enter `555` to pass     |

### Handshake V2

`POST /v2/handshake/create`, four auth headers, `{terminal_name, sum, request_params}`; `terminal_name` and `sum`
required (published schema).

| Answer | Body (published example)                                                             |
|--------|--------------------------------------------------------------------------------------|
| `200`  | `{"error_code":0,"message":"Success","thtk":"w5129f9cdd2ab53675bbb675c6b59dc51df3b8ce5f"}` |
| `400`  | `{"error_code":400,"message":""}`                                                    |
| `401`  | `{"error_code":401,"message":"Unauthorized"}`                                        |

- "Handshake data is retained for **20 minutes** to allow for direct retries. A successful charge resets the timer."
  (published rule). Enforced on the run clock.
- An amount that does not match: `"Illegal Operation 912791"`, "no transaction is executed" (published rule).
- `request_params` is "returned upon transaction completion" (published rule): echoed in the browser return and notify.
- Hosted Fields requires a `thtk` ("All use and interaction with Hosted Fields requires the use of a secure
  'handshake'"). The payment page requires one only once "Enable Hand Shake" is set in the dashboard; simulator
  decision: the page checks a `thtk` when sent and serves without one, as a terminal without that setting does.

### Transaction Lookups

| Route                                   | Request (published schema)                                                                                               | Simulator                                                                                         |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `POST /v1/transactions`                 | `terminal_name`; `transaction_index`, or `transaction_start_date` + `transaction_end_date` (`YYYY-MM-DD`); `page`; ≤1000 rows | every field a string; `user_defined_1`..`20`; `user_defined[]` metadata                          |
| `POST /tranzila/report/v1/transaction`  | as above, plus `detailed` `Y`/`N`, `page_results` 1–1000, `order_direction`, `ufields[]`, `dfields[]`                     | typed fields; `user_defined_1`..`25`; `items[]`; `ufields`/`dfields` refused `400`                |

Response `{transactions:[…], total:"<string>", rows:<int>}`. Both answer the transactions of this run's terminal; no
match is `{"transactions":[],"total":"0","rows":0}` (simulator decision).

- `tranmode` → `txn_type` (published schema): `A` debit, `C` credit, `F` force, `V` verify, `N` j2, `D` cancel; reports
  `txn_type` is upper case (`DEBIT`, `FORCE`, `J2`, …). `transtatus` `1` ok, `0` failed. `currency` `1`, `2`, `978`.
- Simulator decision: `ufields` and `dfields` are refused rather than ignored, because an ignored filter answers rows
  the caller excluded.

## The Payment Page (DirectNG)

`GET` or `POST /tranzila/directng/{terminal}/iframenew.php`; POST "strongly recommend[ed]", key-value pairs (published
rule, "Data Transfer Methods").

| Parameter                                         | Published                                                                             | Simulator                                                   |
|---------------------------------------------------|---------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `sum`                                             | "Positive Decimal Number"                                                             | required                                                    |
| `currency`                                        | `1` NIS, `2` USD, `978` Euro, `826` GBP                                               | required                                                    |
| `tranmode`                                        | `A` standard, `V` J5, `K` token without checking card, `N` J2, `J` standing order; legacy adds "With token add K (AK, NK, VK)" | `A`, `V`, `N`, `K`, `AK`, `VK`, `NK`; `J` refused |
| `cred_type`                                       | `1`, `6`, `8`                                                                         | `1` only                                                    |
| `success_url_address`, `fail_url_address`, `notify_url_address` | URLs                                                                   | read                                                        |
| `DCdisable`                                       | ≤254 characters                                                                       | "Duplicates and DCdisable"                                  |
| `thtk`                                            | the Handshake token                                                                   | "Handshake V2"                                              |
| `myid`, `company`, `contact`, `email`, `phone`, `address`, `city`, `pdesc`, `remarks`, `json_purchase_data` | predetermined fields          | echoed in the return                                        |
| `apple_pay=1`, `google_pay=1`                     | wallet buttons                                                                        | stand-in buttons that complete as a card payment; no wallet is contacted |
| `bit_pay`, `ppnewwin`, `recur_*`, display parameters | published                                                                          | ignored                                                     |

The page itself is a simulator decision: Tranzila publishes its parameters and its return, not its form. It offers the
run's test cards and a way to leave; the outcome follows the run's scenario rules.

### Browser Return and Notify

"the payment page will redirect to either the success or failure URL … The transaction data is returned as an array
through POST or GET" (published rule). "If a Notify page is configured, the transaction data will also be sent to it
simultaneously." Published return (published example, "Response Structure"):

```
supplier, sum, currency, Response=000, company, contact, email, phone, fax, address, city, zip, pdesc, lang,
transaction_id=41044, index=41044, ccno=4207, transaction_source=1, Responsecv=0, Responseid=1,
cardtype=1, cardissuer=1, cardacquirer=2, TranzilaTK, txn_type=debit, tranmode=A, club_number=088
```

| Rule                                                                                                   | Evidence                          | Simulator                                                                  |
|--------------------------------------------------------------------------------------------------------|-----------------------------------|----------------------------------------------------------------------------|
| return method is a terminal setting ("Return Method"); guides set POST                                 | published rule                    | an auto-submitting `POST` form                                             |
| `Response` `000` = success; `ccno` last 4; `TranzilaTK` "if tokenization is enabled"                   | published rule                    | `TranzilaTK` on every approved attempt; J2/J5 `Response` per `tranzila.verification-approval-code` |
| notify: `application/x-www-form-urlencoded` `POST`, same fields as the return                           | published rule (notify guides)    | as published                                                               |
| notify acknowledged by "Return 'OK' with status 200 (otherwise Tranzila retries)"                       | published rule (Base44 guide)     | `200` with body `OK` acknowledges; anything else is retried                |
| notify signature                                                                                       | none published                    | unsigned                                                                   |
| notify retry schedule; notify on a declined attempt                                                    | unpublished                       | `simulator.webhook.retry-delays`; sent for every finished attempt, approved or declined |

`Responsecv` (one `v`) is the page's spelling; the API answers `Responsecvv`.

## Hosted Fields

The simulator serves its own implementation of the documented `TzlaHostedFields` API, never Tranzila's script:

| Served                                              | What                                                                                         |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------|
| `GET /tranzila/hf/assets/js/thostedf.js`            | `TzlaHostedFields.create({sandbox, fields, styles})`, `fields.onEvent`, `fields.charge(params, callback)` |
| field frames under `/tranzila/hf/`                  | `credit_card_number`, `cvv`, `expiry`, `card_holder_id_number` (vendor script field ids)      |
| `POST /tranzila/hf/process/ajax_process_transaction` | the charge; form-encoded, as the vendor script posts it                                     |

- Events (published rule): `ready`, `validityChange`, `cardTypeChange`, `focus`, `blur`, `submit`; CSS classes
  `hosted-fields-invalid`, `hosted-fields-valid`. Messages between page and frames are JSON strings (vendor script).
- `charge` keys (published rule, "Request Keys"): mandatory `terminal_name`, `thtk`, `amount` ("In currency");
  `tran_mode` `A` (default), `V`, `N`; `currency_code` `ILS` (default), `USD`, `EUR`; `tokenize`; `expiry_month`,
  `expiry_year` (`YYYY`) when the expiry field is not used; `force_txn_on_3ds_fail` (default `Y` here, `N` in the 3DS
  schema), `force_challenge`; `notify_url`, `pdesc`, `json_purchase_data`. `payment_plan` other than `1` is refused
  `10007` INVALID PAYMENT PLAN.
- Response (published example, "Regular Transaction"): `{"errors":null,"transaction_response":{"success":true,
  "processor_response_code":"000","transaction_id":"97588","auth_number":"0000000","amount":"10.00","currency_code":1,
  "credit_card_last_4_digits":"2312","expiry_month":"12","expiry_year":"26","card_mask":"458059****2312",
  "cvv_status":"3","id_status":"0","token":"me4b50240d8c6222312","payment_plan":1,…},"response_hash":"…"}`.
  `transaction_id` and `amount` are strings and `currency_code` is numeric, unlike the API.
- `response_hash` = HMAC-SHA256, lowercase hex, over the response JSON without `response_hash`, serialised with no space
  after `,` or `:`, keyed by the account secret (published rule and example, "Response Hash"; the per-user secret for
  `requested_by_user` is not modelled).
- Errors: client-side validation calls back `err` as `{error:"invalid_resource", error_description, messages:[{code,
  message, param}]}` (vendor script; the prose's `error.messages.forEach` agrees); a server refusal answers
  `errors:[{code, param, message}]` with codes `10000`–`10016` (published rule, "Error Messages").
- 3-D Secure: a `challengeUrl` in the answer opens the simulator's challenge page in a frame; on success the script
  posts `{track_id, supplier}` to the process route and the result reaches the `charge` callback (vendor script).
- No server-side verification of a Hosted Fields result is published; `response_hash` is the only proof.

## Status Mapping

Tranzila has no status field; the native state is the transaction type with its processor code. Every row is a
simulator decision except the reports `transtatus`.

| Native                                                   | Normalized                                     |
|----------------------------------------------------------|------------------------------------------------|
| `debit` `000`                                            | `SUCCEEDED`                                    |
| `verify` `2` approved                                    | `SUCCEEDED` (a card checked, nothing held)     |
| `verify` `5` approved, live                              | `READY_FOR_CAPTURE`                            |
| `verify` `5` past `tranzila.j5-validity`, or reversed     | `CANCELLED`                                    |
| `verify` `5` forced                                      | `SUCCEEDED`                                    |
| original with credits below its amount / equal to it     | `PARTIALLY_REFUNDED` / `REFUNDED`              |
| cancelled `debit`, `force` or `credit`                   | `CANCELLED`                                    |
| challenged 3-D Secure, not completed                     | `NOT_INITIALIZED`                              |
| any decline, `900`                                       | `FAILED`                                       |

A `force` and a `credit` are transactions of their own with their own `transaction_id`, related to the original by
`reference_txn_id`.

## Error Shape

| Host / route                          | Failure                                     | Body                                                                                       | Evidence                       |
|---------------------------------------|---------------------------------------------|--------------------------------------------------------------------------------------------|--------------------------------|
| `api.tranzila.com` create, complete   | application error                           | `200`, `{"error_code": 20112, "message": "…"}` (integer)                                   | published schema               |
| `api.tranzila.com` create, complete   | `400` `20001` Invalid JSON Format, `20003` Invalid JSON Schema, `20004` JSON Schema mismatch | `Error` schema: `{"error_code": "20004", "message": "…"}` (string) | published schema               |
| `api.tranzila.com` create, complete   | `401` `20000` Authentication failed, `20002` Authorization failed | `Error` schema                                                       | published schema               |
| `api.tranzila.com` unknown path       | `404` "The specified resource was not found" | `Error` schema                                                                           | published schema               |
| `/v1/transactions`                    | auth, schema                                | as create (only `200` is declared)                                                         | simulator decision             |
| `/v2/handshake/create`                | `400`, `401`                                | `error_code` equal to the HTTP status                                                      | published example              |
| `report.tranzila.com`                 | `400`, `401`                                | `{"error":"Bad Request","message":"terminal_name is required."}`, `{"error":"Unauthorized","message":"Invalid access token. …"}` | published example |
| payment page, Hosted Fields frames    | refusal                                     | a simulator-written HTML page                                                              | simulator decision             |

The `Error` schema's `'response '` key (trailing space) is not answered: it is not required.

## Out of Scope

Published and not served: STO / My Billing (`txn_type` `sto`, `verify_mode` `6`, `/v1/sto/*`, `/v2/sto/*`, page
`tranmode` `J`, `recur_*`), instalments (`payment_plan` / `cred_type` other than `1`), Bit, PayPal, MASAV, invoices,
EMV PinPad, payment requests (`/v1/pr/create`), the legacy CGI host `secure5.tranzila.com` and Dtran,
`direct.tranzila.com`, Handshake V1, `/v1/terminals/settings/fields/list`, Hosted Fields wallet and bank-transfer
charges (`chargeBit`, `chargeGpay`, `chargeBt`, `hfChargeApple`), Apple Pay and Google Pay beyond the page's stand-in
buttons.

## Vendor Contradictions

| Topic                          | Publication A                                                                          | Publication B                                                                                      | Simulator                                  |
|--------------------------------|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|--------------------------------------------|
| which card API page            | Transactions API (visible, not in sitemap)                                             | Tranzila API (hidden, in sitemap, sunset `2027-01-01`)                                             | the Transactions API                       |
| verify mode field              | `verify_mode`, `2`/`5`/`6` (Transactions API, 3DS schema)                              | `"verfiy_mode"` in the hidden page's examples; `verfiy_mode` enum `[5]` in its Apple Pay schema     | `verify_mode`                              |
| `authorization_number` on `force` | "Ignored for other transaction types"; force example omits it (Transactions API)    | "Relevant for force transactions" (3DS schema); "The force charge (J4) must include … authorization_number" (Apple Pay page) | optional, checked when present |
| HMAC construction              | message app key, key secret + time + nonce, seconds (auth pages)                       | message app key + time + nonce, key secret, milliseconds (quickstart)                              | `tranzila.access-token`                    |
| nonce                          | "40 bytes" (auth page); 80 hex characters (PHP sample, reports)                        | 40 hex characters (quickstart); "Generate once and reuse" (reports header) vs "unique per request" (Handshake V2) | any length; reuse per host          |
| hex case                       | lowercase (Handshake V2)                                                               | uppercase (.NET sample)                                                                            | case-insensitive                           |
| J2/J5 approval code            | `777` (code table)                                                                     | `000` (vendor correspondence)                                                                      | `tranzila.verification-approval-code`      |
| currency format                | API ISO alpha-3 (`ILS`); Hosted Fields request ISO                                     | page `currency` numeric (`1`, `2`, `978`, `826`); Hosted Fields response `currency_code: 1`; reports `'1'` | each surface as published          |
| currency enum                  | `GBP` (Transactions API)                                                               | `GPB` (hidden page schema); `EUR`/`USD`/`ILS` only (3DS component)                                 | `GBP`                                      |
| amount units                   | decimal currency units (API, page `sum`, Hosted Fields "In currency")                  | "smallest currency unit (agorot for ILS)" (`report.tranzila.com`)                                  | each surface as published; `/v1/transactions` in currency units (unpublished there) |
| CVV / ID result names          | `Responsecvv` / `Responseid` (API)                                                     | `Responsecv` / `Responseid` (page); `cvv_status` / `id_status` (Hosted Fields)                      | each surface as published                  |
| error envelopes                | `error_code` integer in `200` bodies; string in the `Error` schema                    | `error_code` = HTTP status (Handshake V2); `{error, message}` (reports)                            | each host as published                     |
| field types                    | `transaction_id` integer, `auth_number` string (API schema)                            | strings `"372803"` (3DS complete), `"97588"` (Hosted Fields)                                       | each route as its example                  |
| `force_txn_on_3ds_fail` default | `Y` (Hosted Fields)                                                                   | `N` (3DS schema)                                                                                   | each surface as published                  |
| `auth_3ds_redirect`            | array of `{key, value}` in `3ds_settings` (schema)                                     | top-level `{url, params}` (complete example); `uth_3ds_redirect` (prose)                           | both shapes read                           |
| page tranmodes                 | `A`, `V`, `K`, `N`, `J` (DirectNG)                                                     | adds `AK`, `NK`, `VK` (legacy page)                                                                | combinations served                        |
| notify success value           | `Response` `000` (page)                                                                | "'000' or '0' = success" (notify guide)                                                            | `000` sent                                 |
| Hosted Fields errors           | `errors[{code, param, message}]`, codes `10000`–`10016` (table)                        | `err.messages[]` (prose, vendor script)                                                            | client-side `err`, server `errors`         |
| user-defined slots             | 25 (Transactions API, reports)                                                         | 20 (`/v1/transactions`)                                                                            | each lookup as published                   |

## What This Contract Could Not Settle

| Gap                                                          | Simulator                                                      |
|--------------------------------------------------------------|----------------------------------------------------------------|
| sandbox host, test terminal, general test-card list          | none; only the 3-D Secure test cards are published             |
| request-time tolerance                                       | ±5 minutes                                                     |
| decline body of create                                       | `error_code` = processor code ("Response Codes")               |
| DCdisable duplicate answer                                   | the original's result, `error_code` `0`                         |
| J5 validity; partial, multiple and over-capture              | `tranzila.j5-validity`; one `force`, ≤ J5 amount, `507` above  |
| partial `reversal`; `cancel` after settlement                | full reversal only; `cancel` any time                          |
| 3-D Secure create challenge answer; redirect method          | `challengeUrl` + `track_id`; browser `POST` to `auth_3ds_redirect.url` |
| notify field list, signature, retry schedule, on decline     | the return's fields, unsigned, simulator retry plan, sent      |
| page `K` answer (no transaction recorded)                    | `Response` `000`, `TranzilaTK`, no `index`                      |
| API `card_type` ("shva card type") values                    | the page's `cardtype` table (`1` Mastercard … `6` Maestro)     |
| token format                                                 | alphanumeric ending in the card's last four, as several published tokens do |
| `/v1/transactions` amount units                              | decimal currency units, as a string                            |
