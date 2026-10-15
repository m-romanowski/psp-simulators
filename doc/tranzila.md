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
| `POST /v1/transaction/credit_card/create`                      | `debit`, `verify` (`verify_mode` `2`, `5`), `force`, `reversal`, `credit`, `cancel`; 3-D Secure on `activate_3ds=Y` | amount from `items` only; discounts, exchange rates and `C` items refused; `sto`, `verify_mode` `6` and instalments refused |
| `POST /v1/transaction/credit_card/3ds/complete`                | finishes a challenged 3-D Secure transaction by `track_id`                                                     | challenge answer shape is a simulator decision                                                           |
| `POST /v2/handshake/create`                                    | mints a `thtk` bound to terminal and `sum`, 20 minutes                                                         |                                                                                                          |
| `POST /v1/transactions`                                        | lookup by `transaction_index` or date range                                                                   | amounts in minor units, as strings (inference)                                                           |
| `GET`/`POST /tranzila/directng/{terminal}/iframenew.php`       | the payer page: tranmodes `A`, `V`, `N`, `K`, `AK`, `VK`, `NK`; browser return and notify                      | the page is the simulator's own; notify unsigned; Apple Pay and Google Pay are stand-in buttons and a simulator sheet |
| `/tranzila/3ds/challenge/{track_id}`                            | the challenge page a 3-D Secure create answers in `challengeUrl`: a code, `555` passes                       | the page is the simulator's own; its address is unpublished                                              |
| `/tranzila/hf/...`                                             | the simulator's own implementation of the `TzlaHostedFields` API, its frames and its process route           | `card_mask` keeps only the last four; which refusal answers which code is a simulator decision; the notify uses the payment page's format |
| `POST /tranzila/report/v1/transaction`                         | lookup by `transaction_index` or date range, amounts in minor units                                           | `ufields`, `dfields` refused                                                                             |
| STO / My Billing, Bit, PayPal, MASAV, invoices, EMV PinPad, payment requests, legacy CGI, `direct.tranzila.com`, Handshake V1, instalments | **not served** |                                                                                     |

The screens start a payment as a `debit` or a J5 directly, or open a payment page that waits for its payer; the payer
step on the page's own screen pays it or leaves it.

Account settings that choose between vendor contradictions or supply what the vendor leaves open:

| Setting                                  | Values (default first)                                                         | Chooses between                                                                                   |
|------------------------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `tranzila.access-token`                  | `AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT`, `AS_THE_QUICKSTART_SIGNS_IT`         | the two published HMAC constructions ("Authentication and Transport")                             |
| `tranzila.authentication`                | `THE_ACCESS_TOKEN_IS_VERIFIED`, `THE_ACCESS_TOKEN_IS_NOT_VERIFIED`             | simulator decision: permissive auth for local tests; the app key must still name the account      |
| `tranzila.verification-approval-code`    | `777`, `000`                                                                   | the published code table (`777`) and vendor correspondence (`000`) for an approved J2, J5 or page `K` |
| `tranzila.j5-validity`                   | `P7D`; an ISO-8601 duration above zero, at most 365 days                       | simulator decision: J5 hold validity is unpublished ("acquirer-defined", vendor correspondence)   |

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
| Iframe Integration new DirectNG   | `PB/iframe-integration-directng`                                        | "Base URL", "Data Transfer Methods", "Receiving the Response", "Key Response Fields", "Data Retrieval", "iframe parameters table", "Add Digital wallets Payment Options", "Apple Pay", "Google Pay", "Notify Url", "Preventing Duplicate Transactions" (DCdisable, Handshake) |
| Iframe Integration (legacy)       | `PB/iframe-integration`                                                 | iframe parameters table (tranmode combinations), Dtran, "Apple Pay", "Google Pay"                          |
| Apple Pay Integration via Iframe  | `PB/apple-pay-iframe`                                                   | "Iframe Parameters", "How It Works", "Transaction Processing", "API Charging After J5 Verification"        |
| Hosted Fields                     | `PB/hosted-fields`                                                      | "Getting started", "Using TzlaHostedFields.create method", "Using Submit Handler", "3DS TRANSACTIONS", "Test Credit Card Numbers for 3DS", "Request Keys", "Response Keys", "Response Examples", "Error Messages", "Response Hash" |
| Hosted Fields scripts             | `https://hf.tranzila.com/assets/js/thostedf.js`, `.../hfields_depend/ops_controlfield.js` | read for the public API and wire shapes; not served                                        |
| Reports authentication            | `RP/authentication`                                                     | "Required Headers", "Generating a Valid Access Token"                                                     |
| Transaction Reports API           | `RP/tranzila-transaction-reports-api/gettransactions`                   | schema, examples                                                                                          |
| Track Transaction Data            | `RP/track-transaction-data/get-transaction-information`                 | schema, examples                                                                                          |
| Notify guides                     | `https://docs.tranzila.com/docs/vibe-coding/closing-an-order-in-base44-using-notify-page`, `.../tranzila-lovable-notify-page-guide` | notify encoding, acknowledgement, fields |

Where the two card API pages disagree, the visible Transactions API page governs.

## Hosts and Paths

| Vendor host                      | Served at                                  | Evidence                                                                                 |
|----------------------------------|--------------------------------------------|------------------------------------------------------------------------------------------|
| `https://api.tranzila.com`       | the root, at the vendor's own paths        | published schema (`servers` `https://api.tranzila.com/v1`, `/v2`)                         |
| `https://directng.tranzila.com`  | `/tranzila/directng`                       | published rule ("Base URL"); `direct.tranzila.com` is "Legacy Deprecated"                 |
| `https://hf.tranzila.com`        | `/tranzila/hf`                             | published rule ("Getting started")                                                       |
| `https://report.tranzila.com`    | `/tranzila/report`                         | published schema                                                                         |

Simulator decision: the three non-API hosts sit under a prefix because the payment page's first path segment is the
terminal name, which at the root would shadow every other route. The 3-D Secure challenge page is served under `/tranzila/3ds`, an address of this simulator,
because Tranzila publishes only that its address arrives in `challengeUrl`. The pairing screen publishes one base URL per host,
and route aliases (`README.md`, "Route aliases") map the vendor's absolute URLs. Pairing publishes
`TRANZILA_API_BASE_URL` (`/v1`), `TRANZILA_API_V2_BASE_URL` (`/v2`), `TRANZILA_DIRECTNG_BASE_URL`
(`/tranzila/directng`), `TRANZILA_HF_BASE_URL` (`/tranzila/hf`) and `TRANZILA_REPORT_BASE_URL` (`/tranzila/report`). The payer's own submission goes to `/tranzila/directng/{terminal}/payer/{pageId}`, a route of
the simulator's page and not a vendor address; a `GET` there shows the waiting page again, and
`/tranzila/directng/{terminal}/payer/{pageId}/wallet/{wallet}` is the stand-in wallet sheet ("Apple Pay and Google
Pay").

At the root, Tranzila claims the namespaces `/v1/transaction`, `/v1/transactions` (lookups) and the one route
`/v2/handshake/create`; stripe and autra also answer under `/v1`, and worldline keeps the rest of `/v2`, including a
merchant named `handshake`. The namespace with more path segments answers the faults under it ("Error Shape"): an
unknown address under `/v2/handshake/create` or a wrong method on it is answered by tranzila, and anywhere else under
`/v2` by worldline.

## Authentication and Transport

Four headers on every `api.tranzila.com` and `report.tranzila.com` call (published rule, `PB/authentication` header
table; published schema, `required: true`): `X-tranzila-api-app-key`, `X-tranzila-api-request-time`,
`X-tranzila-api-nonce`, `X-tranzila-api-access-token`. Bodies are `application/json`.

| Element        | Published                                                                                                                         | Simulator                                                                                  |
|----------------|-----------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| access token   | "hash_hmac using 'sha256' on application key with secret + request-time + nonce" (published rule); PHP `hash_hmac('sha256', $appKey, $secret . $time . $nonce)` (published example); `RP/authentication`: "message: app_key, key: secret + timestamp + nonce", "Output must be hex-encoded" | default construction                                                                       |
| quickstart     | `printf "%s%s%s" "$TRANZILA_APP_KEY" "$REQUEST_TIME" "$NONCE" \| openssl dgst -sha256 -hmac "$TRANZILA_APP_SECRET" -binary \| xxd -p -c 256`: message app key + time + nonce, key the secret; `REQUEST_TIME=$(date +%s%3N)` (published example) | `AS_THE_QUICKSTART_SIGNS_IT`: that construction, request time in milliseconds              |
| hex case       | Handshake V2: "Result must be a lowercase hex string" (published schema); the .NET sample emits `ToString("X2")`, uppercase (published example) | compared case-insensitively                                                                |
| request time   | "Unix format (integer counting seconds…)" (published rule); reports: "Must be within a few minutes of server time" (published schema) | simulator decision: an integer of at most 15 digits, ±5 minutes on the run clock, because "a few minutes" is the only bound published |
| nonce          | "A 40 bytes NONCE – unique random string" (published rule); PHP `bin2hex(random_bytes(40)) //actually 80 characters string`; quickstart `openssl rand -hex 20`; reports: "80-character random hexadecimal string generated fresh for each session" and "Generate once and reuse"; Handshake V2: "The nonce is unique per request" | any non-empty value; on `api.tranzila.com` a nonce is remembered per account for 10 minutes on the run clock, twice the request-time tolerance, so a signature cannot be replayed while its request time is still accepted; reuse is refused `20000`, because the auth page says the token "prevent[s] processing of the same request more than once"; the nonce is spent only once the token verifies, so a wrong token does not spend it; on `report.tranzila.com` reuse is accepted, because that host's own header says "Generate once and reuse" |
| secret         | "you cannot call Tranzila API from within web client application" (published rule)                                                | the account's secret; never answered back                                                  |

`THE_ACCESS_TOKEN_IS_NOT_VERIFIED` leaves the token, request time and nonce unchecked; the app key must still name the
account. Tranzila publishes no such mode.

### Duplicates and DCdisable

No idempotency key or header is published. Duplicate protection is `DCdisable` (published rule, "Prevent Duplicate
Transactions"; DirectNG "DCdisable"):

| Rule                                                                                       | Evidence           | Simulator                                                          |
|--------------------------------------------------------------------------------------------|--------------------|--------------------------------------------------------------------|
| API: `user_defined_fields: [{"name":"DCdisable","value":"…"}]`; page: parameter `DCdisable` | published rule     | both read                                                          |
| "The duplicate check is performed up to 24 hours back", "at the parent terminal level"     | published rule     | 24 hours on the run clock, per terminal, `txn_type` and `verify_mode` |
| "If the original transaction failed, `DCdisable` will not prevent a retry"                  | published rule     | only an approved original of the same `txn_type` (for `verify`, the same `verify_mode`) blocks; a request arriving while another under the same key runs waits for it, then is answered as its duplicate if it was approved or proceeds if it was declined |
| maximum 254 characters; `22103` "Invalid DCdisable"                                        | published rule, schema | over 254 → `22103`                                             |
| duplicate messages "This payment has already been made, please contact the business" / "This payment has already been paid. Please contact customer service." | published rule | API: `200`, `error_code` `0`, the first message, and the original's `transaction_result`; no second transaction |
| DCdisable is not a universal idempotency key for JSON calls                               | vendor correspondence | applied on create, to every `txn_type`, and on the payment page; no other route |

The idempotency inspector records the `DCdisable` value exactly as the client sent it, under a key name carrying its
scope (`DCdisable debit`, `DCdisable verify 5`, `DCdisable reversal`, …), so a 254-character value is kept whole.

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
| `card_number`               | string, 8–19; a token goes here: "A Tranzila token replaces **only the full card number**"    | digits only: a card number, and a new token is minted; with a letter: a token, which must be one this run minted (a payment page's `TranzilaTK` included) or a seeded fixture's, else `20111`; an Apple Pay token is refused `20111` on a `debit` or `verify` ("Apple Pay and Google Pay") |
| `expire_month`, `expire_year` | integer 1–12; integer, example `2025`                                                       | required, as the schema says, also with a token; not on `cancel`            |
| `cvv`                       | string 3–4, "can be null"                                                                     | optional; answered in `Responsecvv`                                         |
| `card_holder_id`            | string, length 9                                                                              | optional; answered in `Responseid`                                          |
| `txn_currency_code`         | ISO 4217 alpha-3, default `ILS`                                                               | the Transactions API enum; on a follow-on, absent or equal to the original's, else `400` `20004` |
| `items[]`                   | required per item `name`, `units_number` (integer), `unit_price` (number)                     | the amount ("Amount")                                                       |
| `reference_txn_id`          | integer                                                                                       | names the original for `force`, `reversal`, `credit`, `cancel`              |
| `authorization_number`      | string; "REQUIRED when txn_type is `credit`, `reversal` or `cancel` … Ignored for other transaction types" | see "Per Transaction Type"                                       |
| `payment_plan`              | `1`, `6`, `8`; default `1`                                                                    | anything but `1` refused `20004` (instalments out of scope)                 |
| `pan_entry_mode`            | `50`, `51`, `52`                                                                              | accepted, echoed                                                            |
| `user_defined_fields[]`     | `{name, value}`                                                                               | `DCdisable` read; all stored and answered by the lookups                    |
| `client`, `remarks`, `response_language`, `created_by_user`, `created_by_system` | published schema                        | accepted, echoed in `original_request`                                      |
| `activate_3ds`, `3ds_settings` | 3DS schema, same route                                                                     | "3-D Secure"                                                                |

Simulator decision: an out-of-scope value the schema admits (`sto`, `verify_mode` `6`, `payment_plan` `6`/`8`) is
refused `20004`, so a client learns it was not performed instead of reading a success.

Simulator decision: a body field outside both the Transactions API schema and the 3DS schema, other than the
top-level `auth_3ds_redirect` and `force_txn_on_3ds_fail` ("3-D Secure"), is a schema mismatch, refused `20004`, so `verfiy_mode` (the hidden page's spelling) is refused rather than silently read. Every schema
violation answers `20004`; `20003` is never answered.

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
    "club_number": "088",
    "card_type": 2,
    "card_type_name": "Visa",
    "currency_code": "ILS",
    "expiry_month": 7,
    "expiry_year": 25,
    "payment_plan": 1,
    "token": "t5d55d2922ca4024245",
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

- `transaction_id` integer, never `0`, `expiry_year` YY, `currency_code` ISO alpha-3, `amount` "transaction amount in currency"
  (published schema).
- `Responsecvv` / `Responseid`: `0` not entered, `1` entered and valid, `2` invalid, `3` not checked (published schema).
  Simulator decision: `1` when sent, `0` when not, unless a scenario says otherwise.
- `token`: "applies only to tokens service subscribers" (published schema). Simulator decision: always answered, as for
  a subscribed terminal, because a run's later `force`, `credit` and repeat charges need it. Format: `t`, 14 hex
  digits and the card's last four, 19 characters (simulator decision). The Hosted Fields examples' tokens end in the
  card's last four (`me4b50240d8c6222312` / `2312`, `s9aa520a6e14fac5178` / `5178`, `Ua19f41b365c87a9943` / `9943`,
  `Mc7fcd54jdk41a62716` / `2716`, published example); the API's and the 3DS examples' tokens (`O5d55d2922ca4021382`,
  `Q823424234234dfsgdf029`) do not.
- `card_type`: from the card prefix through the page's `cardtype` table; `card_locality`: always `domestic`
  (simulator decision).
- `tranmode`: none on a `reversal`, because the lookups' `tranmode` table publishes none for it.
- `original_request` is "The original JSON sent in the request"; the simulator masks `card_number` and answers `cvv` as
  `***`, as the 3DS complete example does.

### Amount

There is no top-level amount; the request schema has no amount field (published schema). The amount is Σ
`unit_price` × `units_number` over `items`, in decimal currency units (`unit_price` `10.53`, published example).

| Item field                 | Published                                                                                       | Simulator                                                     |
|----------------------------|-------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `price_type`               | `N` "Net (excluding VAT)", `G` "Gross (including VAT)" (Transactions API schema); "if Net, VAT is added to sent amount", default `G`, is published for the STO item's `priceType` (3DS and hidden page schema) | `G`: the item as sent, lexical form kept; `N`: VAT added, the item rounded half-up to 2 decimals (inference: the STO item's arithmetic applied to card items) |
| `vat_percent`              | number, no description (Transactions API schema); "if omitted, current Bank Of Israel VAT will be used" for the STO item (3DS schema) | the rate for an `N` item; omitted: 18, the value of the Transactions API's own example (`price_type: G`, `vat_percent: 18`) (inference) |
| `discount`, `discount_type`, `to_txn_currency_exchange_rate`, `type` `C` | published schema, arithmetic unpublished                          | refused `20004`                                               |
| `currency_code` other than the transaction's currency (the original's on a follow-on) | published schema, conversion unpublished | refused `20004`                                    |
| any other field            | outside the schema                                                                              | refused `20004`                                               |

### Per Transaction Type

| `txn_type`           | Published                                                                                                   | Simulator                                                                                                                       |
|----------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `debit`              | "Charges the card" (published rule)                                                                         | approved `000`, or the scenario's decline                                                                                       |
| `verify` + `2`       | "J2 verification. Checks the card without capturing funds."                                                 | approved at `tranzila.verification-approval-code`; nothing held                                                                 |
| `verify` + `5`       | "J5 authorization. Reserves credit for the specified amount. The actual charge is later performed using `txn_type=force`." | approved at `tranzila.verification-approval-code`; a hold lives `tranzila.j5-validity` on the run clock               |
| `force`              | J5 capture; example sends `reference_txn_id`, card, items, no `authorization_number`                        | `reference_txn_id` must name a live J5 of the same terminal; one `force` per J5; amount ≤ J5 amount else processor code `507`   |
| `reversal`           | "Releases a reserved credit amount from a J5 authorization transaction (`verify_mode=5`)"                   | releases a live, unforced J5 in full                                                                                            |
| `credit`             | "Credits funds back to the cardholder"; `23002` "Already Credited this transaction id", "id 4 - sum is larger than the original debit transaction" | one approved `credit` per `debit` or `force`, at most its amount; partial allowed; a declined `credit` does not use it; not after a `cancel` |
| `cancel`             | "Cancels a `debit` or `credit` transaction"; example carries no card, expiry or items                       | cancels a `debit`, `force` or `credit` in full; not after a `credit`; card fields not required                                  |

A follow-on (`force`, `reversal`, `credit`, `cancel`) takes the original's currency; a different `txn_currency_code` on
it is refused `400` `20004`, before the `DCdisable` length check (`22103`), the `DCdisable` duplicate answer and any rule
below that answers `200`, because a schema-class refusal precedes the application errors.

Reference rules (`20112`, `21100`, `21101`, `22100`, `22101`, `23001`, `23002` are published codes; which condition
answers which is a simulator decision unless the code's own text says it):

| Condition                                                                  | Answer                                                     |
|----------------------------------------------------------------------------|------------------------------------------------------------|
| `reference_txn_id` names no transaction of this terminal                   | `20112` "Original transaction not found"                   |
| `reference_txn_id` missing on `force`, `reversal`, `credit`, `cancel`       | `21101` "Provided index was empty"                         |
| `authorization_number` empty on `credit`, `reversal`, `cancel`              | `22101` "Empty authorization number"                       |
| `authorization_number` differs from the original's `auth_number`            | `22100` "Authorization number is not equal to the transaction value" |
| `authorization_number` on `force`                                           | optional; checked as above when present                    |
| `credit` of a cancelled transaction                                         | `23001` "id 1 - Attempting to credit a transaction that has already been canceled" |
| a second `credit` of the same original                                      | `23002` "Already Credited this transaction id"             |
| `credit` above the original                                                 | `23002` "id 4 - sum is larger than the original debit transaction" |
| `credit` with a different card than the original                            | `20112` ("uncreditable origin" text)                       |
| `force` or `reversal` of a J5 already forced, reversed or past validity     | `20112` ("uncreditable origin" text)                       |
| `cancel` of a transaction already credited                                  | `20112` ("uncreditable origin" text)                       |
| `cancel` of a transaction already cancelled                                 | `20112` ("uncreditable origin" text)                       |
| `debit` or `verify` with an Apple Pay token                                 | `20111` "Provided token check failure"                     |
| `force` or `credit` with an Apple Pay token of another payment              | `20112` ("uncreditable origin" text)                       |

- Partial `force` (lower than the J5 amount) is allowed and closes the J5: "partial force is supported" (vendor
  correspondence); nothing published contradicts it, and multiple captures per J5 are unpublished.
- `force`, `reversal` and a token `debit` need no CVV (vendor correspondence; the schema makes `cvv` optional anyway).
- A J5 past `tranzila.j5-validity` answers `20112` because the hold no longer exists to act on; validity is
  "acquirer-defined" (vendor correspondence) and unpublished.
- A `credit` and a `cancel` of one original exclude each other: whichever is approved first settles it.
- Simulator decision: the same card is the same last four, expiry month and year and card type, and the same first
  six digits when both masks show them (a seeded token's mask shows none); no value derived from the full card
  number is stored.

### Response Codes

`processor_response_code` carries a code of the published table (`PB/transaction-response-codes`); "Those are
application error codes, response code will be **success (200)**". The simulator answers this subset:

| Code  | Published text (code table)                                                                          | When                                          |
|-------|------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| `000` | "Transaction approved."                                                                              | approved `debit`, `force`, `credit`, `cancel`, `reversal` |
| `777` | "operation completed (success code for operations in which no transaction is recorded, including J2 and J5)" (Hebrew text, translated) | approved J2, J5 or page `K` at the default setting |
| `507` | "actual transaction amount higher than the approved amount" (Hebrew text, translated)               | `force` above the J5 amount                   |
| `001` | "Blocked confiscate card. Please check and enter the credit number again."                           | a scenario's decline                          |
| `002` | "Stolen confiscate card. Please check and enter the credit number again."                            | a scenario's decline                          |
| `003` | "Contact credit company to approve the transaction."                                                 | a scenario's decline                          |
| `004` | "Refusal. Please contact the card owner to check the reason with his credit company."                | a scenario's decline                          |
| `005` | "Forged confiscate card."                                                                            | a scenario's decline                          |
| `006` | "Incorrect identity number or CVV."                                                                  | a scenario's decline                          |
| `012` | "Unauthorized card for this terminal."                                                               | a scenario's decline                          |
| `015` | "Expired card. Please check the expiration date again."                                              | a scenario's decline                          |
| `016` | "Unauthorized currency."                                                                             | a scenario's decline                          |
| `017` | "Unauthorized credit type for this transaction."                                                     | a scenario's decline                          |
| `900`–`928`, `930` | "Transaction failed at 3D Secure authentication stage." … (3DS Errors tab); `900` answered with the API's own "Transaction failed during 3D Secure validation" | a failed 3-D Secure authentication ("3-D Secure"), or a scenario's failure of operation `3ds` only |

`shva` ("Pending SHVA response.") and `010` ("Partial confirmation") are never answered: no published read resolves a
pending answer, and no published field carries a partially confirmed amount.

An approved J2 or J5 (`777`, or `000` by setting) answers `error_code` `0`. Simulator decision, decline envelope:
`200`, `error_code` the processor code as an integer (`4` for `004`, `507`), `message` the code's text above, and
`transaction_result` with `processor_response_code` and `transaction_id`. No decline body is published; `error_code`
is "0 - success, others - failure" (published schema), and the code table is the only failure vocabulary.

### Application Error Codes

Answered at HTTP `200` with `{"error_code": <integer>, "message": "<text>"}` (published schema, "The response can be 200
with an Application error code"):

| Code    | Published text                                                                     |
|---------|------------------------------------------------------------------------------------|
| `20111` | Provided token check failure                                                       |
| `20112` | "Original transaction not found"; "An origin transaction index is linked to an uncreditable origin transaction (e.g., rejected or non-existent index) or an attempt to credit a different card than the one originally charged." |
| `21100` | Transaction index is not equal to original transaction index                       |
| `21101` | Provided index was empty                                                           |
| `22100` | Authorization number is not equal to the transaction value                         |
| `22101` | Empty authorization number                                                         |
| `22103` | Invalid DCdisable                                                                  |
| `23001` | "Original transaction authorization number is invalid."; "id 1 - Attempting to credit a transaction that has already been canceled" |
| `23002` | "Already Credited this transaction id"; "id 4 - sum is larger than the original debit transaction" |
| `900`   | Transaction failed during 3D Secure validation (3DS spec)                          |

`21100` is never answered: no request field carries a second index to compare.

### 3-D Secure

Create with `activate_3ds: "Y"` and `3ds_settings` (`browser`, `force_txn_on_3ds_fail` default `N`, `force_challenge`
`0`/`1`, `auth_3ds_redirect`) (published schema). Three published outcomes ("3DS Process"): authorised frictionlessly
with 3DS statuses; not authorised, which ends the flow unless `force_txn_on_3ds_fail` is `Y`; or a challenge at
`challengeUrl`, whose result "would be redirected to the address … `uth_3ds_redirect` object, `url` attribute".

| Outcome       | Simulator                                                                                                                       |
|---------------|---------------------------------------------------------------------------------------------------------------------------------|
| frictionless  | the normal create answer plus `t3ds_data`, placed before `original_request`                                                      |
| failed        | `200`, `error_code` `900` (or the 3DS code a scenario names), the decline envelope ("Response Codes") and a transaction recorded with that code; with `force_txn_on_3ds_fail` `Y`, the normal create answer without 3-D Secure |
| challenge     | `200`, `{"error_code": 0, "message": "success", "challengeUrl": "…", "track_id": "…"}`; `challengeUrl` names this simulator's challenge page; no transaction yet |

- 3-D Secure authenticates the card holder of a `debit` or a `verify` (`2` or `5`); `activate_3ds` `Y` on a `force`,
  `reversal`, `credit` or `cancel` is refused `20004` (simulator decision: those act on a transaction already made).
- `3ds_settings` is read as the schema types it: `browser` members of the published names and types (`java_enabled`
  `0`/`1`, `color_depth` of its enum, `screen_height`/`screen_width` not negative, `time_zone` an integer, the rest
  strings), `force_txn_on_3ds_fail` `Y`/`N`, `force_challenge` `0`/`1` as integers; anything else is refused `20004`,
  also when `activate_3ds` is not `Y`.
- `t3ds_data` (simulator decision, the published example's values): `version` `1.0`, `statusCode` `YA`,
  `statusMessage` `Authenticated successfully.`, `eci` `05`; `xid` and `cavv` are 20 bytes drawn for each answer,
  base64, never stored and withheld from the trail and the idempotency inspector, as `challengeUrl` is.

Simulator decision: the challenge answer's shape is unpublished (no `200` schema for create); the two names are the
prose's `challengeUrl` and the complete body's `track_id`. `track_id` is `3ds_` and 40 lowercase hex digits. Redirect
after the challenge ("Vendor Contradictions"): a top-level `auth_3ds_redirect` `{url, params}`, as the complete
example's `original_request` carries it, is admitted on create and supplies the URL (an absolute `http` or `https`
address) and params; `3ds_settings.auth_3ds_redirect`, an array of `{key, value}` with no URL, supplies params after
them, a key both name keeping the top-level value; with no URL, the browser is not redirected and the result reaches
the client only through complete.

The challenge page (simulator decision; Tranzila publishes only that the browser opens `challengeUrl`):

| Route                                     | What it does                                                                                   |
|-------------------------------------------|------------------------------------------------------------------------------------------------|
| `GET /tranzila/3ds/challenge/{track_id}`  | the amount, the card's type and last four, and a field for the code; framable, so no `frame-ancestors` |
| `POST /tranzila/3ds/challenge/{track_id}` | `code` `555` passes, any other code fails, once; then the browser is `POST`ed to `auth_3ds_redirect.url` with the params and `track_id` (a param named `track_id` is not echoed), by the payment page's auto-submitting form; with no URL the page says whether the card holder was authenticated |

A challenge answered once answers `410`; one this simulator does not hold `404`.

Complete: `POST /v1/transaction/credit_card/3ds/complete`, `{track_id, terminal_name}` (published schema), both
required strings, nothing else; authenticated as create. Answers the published example's shape: `transaction_result`
with string values (`transaction_id: "372803"`, `card_type: "2"`, `expiry_month: "09"`, `expiry_year: "25"`,
`payment_plan: "1"`), `amount` a number, `card_mask` with `*`, `message` `Success`, `t3ds_data`
`{version, statusCode, statusMessage, xid, cavv, eci}`, and `original_request` as create sent it, card masked and
`cvv` `***`.

| Complete of                                        | Answer                                                                   |
|----------------------------------------------------|--------------------------------------------------------------------------|
| a challenge the payer passed                       | the transaction made now, under the run's scenario rules for its `debit` or `verify`; the complete shape with `t3ds_data`, or the decline envelope |
| a challenge the payer failed                       | `900` and the decline envelope, the transaction recorded `900`; with `force_txn_on_3ds_fail` `Y`, the transaction made as passed, answered without `t3ds_data` |
| a challenge the payer has not answered             | `900` `{"error_code":900,"message":"Transaction failed during 3D Secure validation"}`; the challenge stays open |
| a challenge already completed, an unknown `track_id`, a challenge of another terminal or of the Hosted Fields | `900`, as above                                           |
| a challenge whose `DCdisable` an approved transaction took meanwhile | the duplicate answer ("Duplicates and DCdisable"), naming that transaction |

A J5 completed from a challenge is held `tranzila.j5-validity` from its completion. The idempotency inspector keeps
`track_id` as a duplicate-detection key: the completion that ran, and each later one refused `900`. A complete
retried after its first answer was lost (a fault after the transaction was made) is answered `900` as completed, so
that `900` does not mean nothing was charged: the lookups ("Transaction Lookups") read whether the transaction was
made. The transaction a completion makes carries the `items` and `user_defined_fields` the create sent, read back
from its `original_request`, so the lookups answer them as for any create; its `xid`, `cavv` and `eci` there are
empty, because `xid` and `cavv` are never stored.

A challenge keeps the create's `original_request`, `card_holder_id` included, and for the Hosted Fields the charge's
`user_form_data`, until it is completed or its test run is reset. Neither is shown on the challenge's own state; the
trail and the idempotency inspector withhold `card_holder_id`, the values of identity-named `user_defined_fields` and
the identity keys of `user_form_data` wherever an answer carries them.

Which card takes which outcome: the published test cards (`PB/hosted-fields`, "Test Credit Card Numbers for 3DS"),
by the number sent, or for a token by its card's first six and last four; otherwise the run's scenario on the
operation `3ds`: a failure code of the 3DS table (`900`–`928`, `930`) fails the authentication with that code, a
success or no rule leaves it to `force_challenge`, `1` challenging and `0` passing frictionlessly. The vocabulary
declares the 3DS codes for `3ds` alone and the processor declines for the transaction types alone: a rule naming an
operation and a code of the other table is refused `422`, and a rule naming no operation fails only the operations
whose table holds its code, so `004` passes the `3ds` step and declines the `debit` or `verify`, and `913` fails the
`3ds` step and not the charge `force_txn_on_3ds_fail` `Y` lets through. When `force_txn_on_3ds_fail` `Y` lets a failed
authentication through, the answer names the rule that decided the transaction's own operation if one did, and the
`3ds` rule otherwise; a trail row names one rule.

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

- `thtk`: `w` and 41 lowercase hex digits, the length of the published example (simulator decision).
- A body outside the schema (`terminal_name` a string, `sum` a JSON number above zero, `request_params` an object, no
  other field) is answered `400`, and so is a `sum` of more than 15 digits, more than 6 decimals or written with an
  exponent (simulator decision: no bound is published, and the bound keeps an absurd number from being expanded); a `terminal_name` other than the app key's terminal, a missing or wrong
  authentication header, or an app key no account holds is answered `401` (simulator decision: the published bodies
  carry no reason).
- "Handshake data is retained for **20 minutes** to allow for direct retries. A successful charge resets the timer."
  (published rule). Enforced on the run clock: an approved attempt on the page renews it for 20 minutes; a declined
  one does not.
- An amount that does not match: `"Illegal Operation 912791"`, "no transaction is executed" (published rule: checked
  "After form submission"). The page also checks the `thtk` when it is opened (simulator decision: the page's `sum`
  cannot change between opening and paying, so the mismatch is known at once): it must name a live handshake of the
  page's terminal locking the page's `sum`, compared as numbers. When the payer pays, the handshake must still be
  live. Either refusal executes nothing.
- A page call sent with a `thtk` is resolved to the test run that holds that handshake when its terminal name is
  configured in more than one run.
- `request_params` is "returned upon transaction completion" (published rule): each top-level member is echoed in the
  browser return and notify as a field of its own, a string as itself, a number as written and anything else as JSON
  text, after the page's own fields and never in place of one (simulator decision: the published text does not say how
  an object comes back in a form).
- Hosted Fields requires a `thtk` ("All use and interaction with Hosted Fields requires the use of a secure
  'handshake'"). The payment page requires one only once "Enable Hand Shake" is set in the dashboard; simulator
  decision: the page checks a `thtk` when sent and serves without one, as a terminal without that setting does.

### Transaction Lookups

| Route                                   | Request (published schema)                                                                                               | Simulator                                                                                         |
|-----------------------------------------|--------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `POST /v1/transactions`                 | `terminal_name`; `transaction_index`, or `transaction_start_date` + `transaction_end_date` (`YYYY-MM-DD`); `page`; ≤1000 rows | every field a string; `amount` in minor units (inference: the published example `amount: '500'` matches the reports' minor units); `user_defined_1`..`20`; `user_defined[]` metadata |
| `POST /tranzila/report/v1/transaction`  | as above, plus `detailed` `Y`/`N`, `page_results` 1–1000, `order_direction`, `ufields[]`, `dfields[]`                     | typed fields; `user_defined_1`..`25`; `items[]` when `detailed` is `Y`; `ufields`/`dfields` refused `400` |

Response `{transactions:[…], user_defined:[…], total:"<string>", rows:<int>}` on both routes. Both answer the
transactions of this run's terminal: every transaction of the card API, the payment page and Hosted Fields, approved
or declined, a transaction whose answer a scenario fault kept from the client included. A page `K` records none. No
match is `{"transactions":[],"user_defined":[…],"total":"0","rows":0}`, `user_defined` listing the terminal's slots
(simulator decision).

| Rule                                         | Evidence                                   | Simulator                                                                                       |
|----------------------------------------------|--------------------------------------------|-------------------------------------------------------------------------------------------------|
| `transaction_index` or the two dates         | "mutually exclusive" (reports)             | both refused on both routes, and so is neither or one date alone; an index is answered on page 1 |
| dates                                        | `YYYY-MM-DD`; end "inclusive" (reports)    | calendar dates from `0001-01-01`, both ends inclusive, read on the run clock in UTC; a start after the end finds nothing |
| `page`                                       | "the next 1000 rows" (`/v1/transactions`); `minimum: 1` (reports) | 1 to 2147483647, default 1                                                  |
| rows a page                                  | 1000 (`/v1/transactions`); `page_results` 1–1000, default 1000 (reports) | as published                                                   |
| order                                        | "by transaction date", default `desc` (reports); unpublished for `/v1/transactions` | reports as published; `/v1/transactions` oldest first, so a page read keeps its rows as transactions arrive (simulator decision); equal times oldest first in arrival order, and newest first in reverse arrival order under `desc` |
| `total`, `rows`                              | "across all pages", "in this response page" (reports) | as published                                                                    |
| any other body field                         | schema                                     | refused: `/v1/transactions` `400` `20004`, reports `400`                                       |
| a lookup                                     | a read                                     | recorded in the trail both ways; no keyed exchange; no scenario rule applies to it (simulator decision) |

Fields (`/v1/transactions` as strings; the report host typed as its schema, `null` where it publishes `nullable`):

| Field                                                    | Simulator                                                                                         |
|----------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `index`                                                  | `transaction_id`                                                                                  |
| `transaction_date`, `transaction_time`                   | when the transaction was made, on the run clock in UTC (simulator decision: no zone is published) |
| `amount`                                                 | minor units, rounded half up                                                                      |
| `credit_card_token`                                      | the transaction's token; empty for a decline                                                       |
| `expiration_month`, `expiration_year`                    | two digits each                                                                                   |
| `card_type` (`/v1/transactions`), `card_issuer` (reports) | `1`, as the page's `cardissuer`                                                                  |
| `card_description`, `card_brand`                         | the API's `card_type_name` and `card_type` (the published descriptions are Hebrew and no table is published) |
| `clearing_processor`                                     | the page's `cardacquirer`                                                                         |
| `stars`, `is_foreign`                                    | `0`                                                                                               |
| `authorization_number`                                   | the auth number; empty for a decline                                                               |
| `payment_plan`, `currency`                               | `1`; the numeric currency (`1`, `2`, `978`, `826`)                                                |
| `number_of_payments`, `first_payment_amount`, `other_payment_amount` | `0`                                                                                   |
| `processor_response_code`                                | as on create                                                                                      |
| `tranmode`                                               | `A` debit, `V` J5, `N` J2, `F` force, `C` credit, `D` cancel; empty for a reversal, which the table does not name |
| `txn_type`                                               | `debit`, `verify`, `j2`, `force`, `credit`, `cancel`, `reversal`; upper case on the report host   |
| `transtatus`                                             | `1` approved (`000`, `777`), `0` declined                                                          |
| `telauthability`, `txn_payment_method`, `processor`      | `1`, `CC`, `SHVA`                                                                                 |
| `child_terminal`                                         | the terminal name                                                                                 |
| `broker`                                                 | `apple_pay` or `GOOGLE_PAY`, the reports' own values and case, for a transaction a wallet paid on the page and the `force`, `credit`, `reversal` and `cancel` acting on it; empty otherwise |
| `credit_card_owner_id`, `club_number`, `host_ip`, `refnr`, `tempref`, `xid`, `cavv`, `eci`, `uid`, `broker_product`, `pan_entry_mode` | empty: the simulator keeps none of them |
| `bank`, `bank_branch`, `bank_account`                    | empty on `/v1/transactions`, `null` on the report host ("`null` for card transactions")           |
| `txnfdid`, `cancelfdid`, `txnfdnumber`, `cancelfdnumber` | `null` (no invoice is served)                                                                      |

User-defined fields (simulator decisions): Tranzila configures a terminal's slots in its settings and publishes
`POST /v1/terminals/settings/fields/list` to read them, which is not served here; the simulator has no terminal field
configuration, so a name takes a slot the first time a transaction carries it.

- Kept per transaction, in the order sent: on create, every `user_defined_fields` entry, `DCdisable` included; on the
  payment page, its `DCdisable` and then every parameter it echoes except those it reads, displays or keeps as a
  setting: `sum`, `currency`, `tranmode`, `cred_type`, the three addresses, `DCdisable`, `thtk`, `myid`, `pdesc`,
  `lang`, `accessibility`, `opensum`, `trBgColor`, `trTextColor`, `trButtonColor`, `buttonLabel`, `hidesum`, `nologo`,
  `hide_cc`, `apple_pay`, `google_pay`, `bit_pay`, `ppnewwin`, `recur_payments`, `recur_start_date`, `recur_sum`,
  `recur_transaction`, `npay`, `fpay`, `spay`, `maxpay`, `Ilang`, `IMaam`, `Z_field`, `business_id`,
  `json_purchase_data`, and the names the return sets itself (`supplier`, `Response`, `transaction_id`, `index`,
  `ccno`, `Responsecv`, `Responseid`, `cardtype`, `cardissuer`, `cardacquirer`, `TranzilaTK`, `txn_type`,
  `ConfirmationCode`); on Hosted Fields, its `DCdisable` and then every `user_form_data` key but `notify_url`, `pdesc`,
  `json_purchase_data`, `force_txn_on_3ds_fail` and `force_challenge`. The published example lists the page's own
  `email` and `contact` as user-defined fields.
- No slot keeps a name the trail withholds as an identity (`card_number`, `cvv`, `card_holder_id`, `myid`,
  `credit_card_number`, `card_holder_id_number`, `identity_number`), compared without regard to case, from any of the
  three.
- A terminal of a run numbers each name once, when the first transaction carrying it is recorded, in the order that
  transaction sent its new names; two transactions recorded at once number their names one after the other, never
  interleaved. A terminal holds 25 slots; a name arriving after the 25th is not kept. A name a transaction carries
  twice answers its last value.
- `/v1/transactions` answers slots 1–20, the report host 1–25. `user_defined` describes each slot answered, highest
  `FIELD_ID` first as both published lists order them: `TERMINAL_ID` the terminal, `FIELD_ID` (a string on
  `/v1/transactions` as its example, an integer on the report host as `UserDefinedFieldMeta`), `API_PARAMETER_NAME`
  and `CAPTION` the name, `DEFAULT_VALUE` and `DISPLAY_ORDER` `null`, `MANDATORY` `N`, `DATA_TYPE` `string`,
  `UPDATE_DATE` when the name was first recorded (`YYYY-MM-DD HH:MM:SS`, UTC).

`items` (report host, `detailed` `Y`): each item a create charged, in order: `item_id` the simulator's own number,
`item_name`, `quantity` the units with two decimals, `price` the unit price in minor units (an `N` item's with its VAT
added), `item_type` the item's `type` or `I`. A page payment and a Hosted Fields charge carry one item named
`simulator` at the amount; a `cancel` or `reversal` carries none, even when sent with items, since its amount is the
transaction it acts on.

`transactions.credit_card_token` and `transactions.credit_card_owner_id` are withheld from the trail, and so is each of
the seven identity names above wherever it appears as a field of any trail row: a request, a page's return, a Hosted
Fields answer's `user_form_data`, or a notify. A `user_defined_fields` entry whose `name` is one of them, in any case,
has its `value` withheld from the request row, from `original_request` in the response row and from the keyed exchange
answer, and a duplicate check's fingerprint does not read it; every other entry's value stays readable. Simulator
decision: `ufields` and `dfields` are refused rather than ignored, because an ignored filter answers rows the caller
excluded; an empty array filters nothing and is accepted.

## The Payment Page (DirectNG)

`GET` or `POST /tranzila/directng/{terminal}/iframenew.php`; POST "strongly recommend[ed]", key-value pairs (published
rule, "Data Transfer Methods").

| Parameter                                         | Published                                                                             | Simulator                                                   |
|---------------------------------------------------|---------------------------------------------------------------------------------------|-------------------------------------------------------------|
| `sum`                                             | "Positive Decimal Number"                                                             | required; digits with an optional fraction, above zero      |
| `currency`                                        | `1` NIS, `2` USD, `978` Euro, `826` GBP                                               | required                                                    |
| `tranmode`                                        | `A` standard, `V` J5, `K` token without checking card, `N` J2, `J` standing order; legacy adds "With token add K (AK, NK, VK)" | required (the table marks it so); `A`, `V`, `N`, `K`, `AK`, `VK`, `NK`; `J` refused |
| `cred_type`                                       | `1`, `6`, `8`                                                                         | `1` only                                                    |
| `success_url_address`, `fail_url_address`, `notify_url_address` | URLs                                                                   | absolute `http` or `https` addresses; `notify_url_address` must be a callback target configured for the terminal's account, or no page is opened (simulator decision: the simulator only calls addresses an account configured) |
| `DCdisable`                                       | ≤254 characters                                                                       | "Duplicates and DCdisable"                                  |
| `thtk`                                            | the Handshake token                                                                   | "Handshake V2"                                              |
| `myid`, `company`, `contact`, `email`, `phone`, `address`, `city`, `pdesc`, `remarks`, `json_purchase_data` | predetermined fields          | echoed in the return like every other parameter             |
| any other parameter, a shop's own `orderid` among them | "the parameters entered on the payment page" come back ("Data Retrieval") | echoed in the return and the notify, in the order received |
| `apple_pay`, `google_pay`                         | `1` "Add the option to pay with Apple Pay" / "with Google" ("Add Digital wallets Payment Options") | `1` offers the wallet's stand-in button ("Apple Pay and Google Pay"); echoed like every other parameter |
| `bit_pay`, `ppnewwin`, `recur_*`, display parameters | published                                                                          | ignored                                                     |

The page itself is a simulator decision: Tranzila publishes its parameters and its return, not its form. Every refusal
of a parameter is a page of the simulator's own words ("Error Shape") and opens nothing.

| What the payer does                     | Simulator                                                                                                  |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------|
| picks a card                            | the cards offered are `published-9022`, a Visa ending `9022` standing for the published test card `4907639999909022`, and every payment-method fixture of the run that carries a token; the page asks for no card number, CVV or ID |
| pays under `A`, `V`, `N`, `AK`, `VK`, `NK` | the same transaction an API `debit`, `verify` `5` or `verify` `2` makes, with the card's token as `card_number`, the page's `DCdisable` and the run's scenario rules; a card charged on the page is kept as its last four digits only |
| pays under `K`                          | no transaction; `Response` per `tranzila.verification-approval-code` and the card's token as `TranzilaTK`; no scenario rule applies |
| pays under a `DCdisable` already approved | no transaction, no return and no notify; the page shows "This payment has already been paid. Please contact customer service." and stays open |
| leaves                                  | the page ends with no transaction, no return and no notify (no cancel return is published)                 |

`published-9022` is the page's own, and its token is not one the card API knows by itself: it charges on the card API
of a run once a page of that run returned it as `TranzilaTK`, whether a transaction carries it or a `K` page saved it,
and answers `20111` in any other run.

`myid` is the payer's national ID, the page's counterpart of the API's `card_holder_id`: it travels to the shop in the
return and the notify and is withheld from every row of the trail, inbound, outbound and the notify's. It is kept
with the page, with every other parameter to echo, so that a notify can be sent again, and is forgotten when the test
run is reset.

The published test card answers by the scenario like any other card here: the page performs no 3-D Secure.

### Apple Pay and Google Pay

"the response to an APPLE transaction is the regular response to a credit card transaction" (published rule,
DirectNG): a wallet payment is a card payment of the page's tranmode. A real wallet sheet needs Apple's or Google's
own script and device, so the page shows a stand-in button per wallet and a sheet of this simulator's own (simulator
decision).

| What the payer does                         | Simulator                                                                                                  |
|---------------------------------------------|------------------------------------------------------------------------------------------------------------|
| opens a page with `apple_pay=1` / `google_pay=1` under `A`, `V`, `AK` or `VK` | a button per wallet beside the card form, `GET` to `/tranzila/directng/{terminal}/payer/{pageId}/wallet/apple_pay` or `.../google_pay` |
| opens a page with a wallet under `N`, `NK` or `K` | no button: the page offers the card form only (simulator decision: no refusal is published, and a hidden option still lets the payer pay by card, as a page without the wallet does) |
| taps a wallet button                        | the sheet: the amount, the run's cards (`published-9022` and the fixtures) as the card in the wallet, a confirm button that posts `wallet`, `card` and `outcome=PAID` to the payer's submission, and a cancel back to the waiting page; no script, the page's own policy |
| confirms                                    | the transaction an API `debit` or `verify` `5` makes for that card, under the run's scenario rules, the page's `DCdisable` and its `thtk`, with the transaction's `broker` the wallet's; browser return and notify exactly as for a card |
| names a wallet the page does not offer, or one not served | `422`, the page stays open                                                                |

- The return and the notify carry the published fields only; no wallet field is published for them, so a wallet
  payment is told apart by the lookups' `broker`.
- `ccno` and the lookups show the picked card's last four. Published: "The last 4 digits shown are the Device
  Account Number, not the actual card number" (Apple Pay page); simulator decision: the stand-in has no device
  account, so the picked card stands for it.
- `TranzilaTK` is a token minted for the payment, never the picked fixture's own (simulator decision: the wallet
  hands Tranzila a card the shop never held, so a token the shop already holds would be a different card).
- A scenario rule matches a wallet payment as the card payment it is, by operation (`debit`, `verify`), amount and
  the picked card's token; no `wallet` fact is added, since the processor sees a card payment (published rule above).

Tokens (decisions on contradictory text, "Vendor Contradictions"):

| Token          | Simulator on the card API                                                                                   |
|----------------|-------------------------------------------------------------------------------------------------------------|
| Google Pay     | a token like any other: `force`, `credit`, `cancel`, `reversal`, and a new `debit` or `verify`, which records no `broker` ("Google Pay transactions create a Tranzila token for future use", DirectNG) |
| Apple Pay      | `force` (J5 then J4 by API, Apple Pay page), `credit` and `cancel` of its own payment; a new `debit` or `verify` is refused `20111`, and a `force` or `credit` of another payment `20112` ("cannot be used for new recurring payments", Apple Pay page; simulator decision: the API has no field telling a recurring charge from another charge on a token, so every new charge is refused, with the published token-check code) |

The Hosted Fields wallet charges remain out of scope ("Out of Scope").

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
| return method is a terminal setting ("Return Method"); guides set POST                                 | published rule                    | a `POST` form to `success_url_address` (approved) or `fail_url_address` (declined), submitted by one inline script the page's policy admits by its hash; with no address for the outcome, the page shows the fields itself, `TranzilaTK` withheld |
| `Response` `000` = success; `ccno` last 4; `TranzilaTK` "if tokenization is enabled"                   | published rule                    | `TranzilaTK` on every approved attempt; J2, J5 and `K` `Response` per `tranzila.verification-approval-code` (for `K`, inference from the `777` text) |
| `cardissuer`, `cardacquirer`                                                                           | published tables ("Key Response Fields") | simulator decision: `cardissuer` `1` for every card, as the one published sample has it (`cardtype=1`, `cardissuer=1`, `cardacquirer=2`); `cardacquirer` by card type from the published table, `1` Isracard, `3` Diners, `4` Amex and `2` (Visa Cal) for every other |
| `Responsecv`, `Responseid`                                                                             | published example                 | `0`: the page collects neither                                             |
| fields returned                                                                                        | published example; "Data Retrieval": "the response from the credit card companies, along with the parameters entered on the payment page" (published rule) | `supplier`, `sum`, `currency` as sent, `Response`, every other parameter the page received but `thtk` and the names the return sets itself, `transaction_id` and `index` (none for `K`), `ccno`, `Responsecv`, `Responseid`, `cardtype`, `cardissuer`, `cardacquirer`, `TranzilaTK`, `txn_type` (none for `K`), `tranmode` as sent, then the handshake's `request_params`; the return's own value wins a name both carry |
| notify: `application/x-www-form-urlencoded` `POST`                                                     | published rule (notify guides)    | as published                                                               |
| notify fields: `ConfirmationCode?: string; // Success confirmation` among them                          | published example (Lovable notify guide) | simulator decision: the return's fields plus `ConfirmationCode`, the transaction's auth number, when it has one |
| notify address: `notify_url_address`, "Or add through tranzila settings"                              | published rule                    | `notify_url_address`; without one, the account's one configured callback target stands for the terminal's setting, and with none or several no notify is sent |
| notify acknowledged by `Return "OK" with status 200 (otherwise Tranzila retries).`                      | published rule (Base44 guide)     | `200` with body `OK` acknowledges; anything else is retried                |
| notify signature                                                                                       | none published                    | unsigned                                                                   |
| notify retry schedule; notify on a declined attempt                                                    | unpublished                       | `simulator.webhook.retry-delays`; sent for every finished attempt, approved or declined |

`Responsecv` (one `v`) is the page's spelling; the API answers `Responsecvv`.

## Hosted Fields

The simulator serves its own implementation of the documented `TzlaHostedFields` API, never Tranzila's script:

| Served                                              | What                                                                                         |
|-----------------------------------------------------|----------------------------------------------------------------------------------------------|
| `GET /tranzila/hf/assets/js/thostedf.js`            | `TzlaHostedFields.create({sandbox, fields, styles})`, `fields.onEvent`, `fields.charge(params, callback)`; `text/javascript`, `Cache-Control: no-cache`; the frames' addresses are taken from the address the script was loaded from |
| `GET /tranzila/hf/assets/js/hfields_depend/genfield.php` | a field frame: `field_name` `credit_card_number`, `cvv`, `expiry`, `card_holder_id_number` (vendor script field ids), `instance_identifier`, `placeholder`; `expiry` with `version` `2` is a month and a year list |
| `GET /tranzila/hf/assets/js/hfields_depend/controlfields.html` | the hidden frame the charge is posted from                                         |
| `POST /tranzila/hf/process/ajax_process_transaction` | the charge; form-encoded, as the vendor script posts it                                     |

Simulator decision, the frames: a field frame shares what the payer types only with frames of its own origin, and
the merchant's page receives events and the charge answer, never a card field. A frame names no `frame-ancestors`,
because any merchant page frames it; its policy otherwise admits its one inline script by hash, and the hidden frame
also `connect-src 'self'`. The hidden frame answers a charge only to the page that framed it, at that page's origin.
A charge the hidden frame refuses before posting calls back `err` with the vendor script's codes
(`terminal_name_invalid`, `credit_card_number_invalid`, `expiry_invalid`, `cvv_invalid`,
`card_holder_id_number_invalid`) and the simulator's own messages. As the vendor script does, a server answer reaches
the callback with `err` `false`, and an answer that is not `2xx` or not JSON as `err`
`{error:"network_error", error_description}` with no response. A page whose origin is opaque (`"null"`) is answered
nothing, since a frame cannot address an answer to it.

The fields as the vendor script types them: the card number keeps digits only, grouped as its card type is (4-6-5
for `ae`, 4-6-4 for `diners`, fours otherwise) and cut at that type's longest length; the expiry keeps digits only and
is shown `MM/YY`; the CVV keeps up to four digits; the ID up to nine, shown padded with leading zeros to nine when the
payer leaves the field and posted as typed. A card number's type is the vendor script's: it is tried against `ae`,
`diners`, `mc`, `visa` and `isracard` in that order, each with the number padded with `1`s to that type's length;
the first that fits is its type, unless the number is 8–10 digits that fail that type's own check and pass the
Isracard check (weighted sum a multiple of 11), which makes it `isracard`; an empty number or one fitting none is
`unknown`, and `cardTypeChange` is sent whenever that type changes. The field calls a number valid when its length is
one of its type's and it passes its type's check (Luhn, which the vendor script also passes for any 8 or 9 digits, or
the Isracard check); the hidden frame posts what the server accepts (below). An expiry is valid until its month ends. `styles` is applied as a stylesheet in each field frame, every key a selector (`input`,
`input:focus`, `::placeholder`, `.hosted-fields-invalid`), and the `hosted-fields-valid` / `hosted-fields-invalid`
classes are set on the input in the frame as well as on the merchant's container.

- Events (published rule): `ready`, `validityChange`, `cardTypeChange`, `focus`, `blur`, `submit`; CSS classes
  `hosted-fields-invalid`, `hosted-fields-valid`. Messages between page and frames are JSON strings (vendor script).
  The vendor script adds `empty` and `notEmpty` when a field empties or fills, sends `inputSubmitRequest` on Enter,
  names `cardType` `ae`, `diners`, `mc`, `visa`, `isracard` or `unknown`, and passes any other type a frame sends to
  `onEvent` as `{type, field, timestamp}`; the simulator does the same, and on Enter sends both
  `inputSubmitRequest` and `submit` ("Vendor Contradictions"). `validityChange` carries `field` and `isValid`.
- `charge` keys (published rule, "Request Keys"): mandatory `terminal_name`, `thtk`, `amount` ("In currency");
  `tran_mode` `A` (default), `V`, `N`; `currency_code` `ILS` (default), `USD`, `EUR`; `tokenize`; `expiry_month`,
  `expiry_year` (`YYYY`) when the expiry field is not used; `force_txn_on_3ds_fail` (default `Y` here, `N` in the 3DS
  schema), `force_challenge`; `notify_url`, `pdesc`, `json_purchase_data`. Simulator decision: `payment_plan` other
  than `1` asks for instalments, which are not served, and is refused with the table's `10007` INVALID PAYMENT PLAN.
- Response (published example, "Response Examples", "Regular Transaction"; in full, key order as published, which
  `response_hash` depends on):

```json
{
  "errors": null,
  "transaction_response": {
    "success": true,
    "error": null,
    "processor_response_code": "000",
    "transaction_id": "97588",
    "auth_number": "0000000",
    "amount": "10.00",
    "currency_code": 1,
    "credit_card_last_4_digits": "2312",
    "expiry_month": "12",
    "expiry_year": "26",
    "user_form_data": {
      "response_language": "",
      "contact": "john.doe@example.com",
      "company": "Example Corp",
      "business_id": "515123456",
      "json_purchase_data": "",
      "force_challenge": "0",
      "force_txn_on_3ds_fail": "N",
      "expiry": "",
      "notify_url": "",
      "shopify_id": "",
      "DCdisable": "",
      "requested_by_user": "merchant_user",
      "card_holder_id_number": ""
    },
    "card_type": "5",
    "card_mask": "458059****2312",
    "card_locality": "domestic",
    "txn_type": "debit",
    "tranmode": "A",
    "card_type_name": "Isracard",
    "cvv_status": "3",
    "id_status": "0",
    "card_issuer": "1",
    "token": "me4b50240d8c6222312",
    "payment_plan": 1,
    "total_installments_number": null
  },
  "response_hash": "7d7dfdbbd6e863ebb8b68c74a6080cdd26bbac4b39fe01a0aa3839ee6c63d217"
}
```

- `transaction_id`, `amount` and `card_type` are strings and `currency_code` is numeric, unlike the API. `amount` is
  written with two decimals, as every published example writes it.
- The charge is the API's: `A` a `debit`, `V` a `verify` `5`, `N` a `verify` `2`, with the API's transaction rows,
  scenario rules, `DCdisable` (a charge key, echoed in `user_form_data`) and duplicate answer (the original
  transaction). An approved charge renews the `thtk` for 20 minutes.
- The typed card (simulator decision): a number naming a payment-method fixture of the run, by last four and card
  type, is charged as that fixture's token; any other number passing Luhn is charged as a new card under a new token.
  Only the last four and the card type are kept, so `card_mask` is `*` for every digit but the last four, unlike the
  published example. A number only the Isracard check accepts is typed Isracard (`5`) whatever its prefix; the number, the CVV and the ID are never stored, answered or written to the trail.
- `token` is answered only when `tokenize` is `true`; `cvv_status` and `id_status` are `1` when the field was filled
  and `0` when not; `card_issuer` is `1`; `error` is `null` on approval and the processor code's text on a decline,
  with `success` `false` (simulator decisions).
- `user_form_data`: every key the charge sent other than the ones it reads, in the order received, as strings
  (simulator decision); `notify_url` is echoed there too.
- `notify_url` ("URL endpoint for receiving transaction data (IPN)", published rule) is notified as the payment
  page's notify is, because no Hosted Fields notify format is published: a form `POST` acknowledged by `200` `OK`
  and retried otherwise, sent for an approved or a declined charge and not for a `DCdisable` duplicate, carrying
  `supplier`, `sum` as sent, `currency` numeric, `Response`, the `user_form_data` fields, `transaction_id`, `index`,
  `ccno`, `Responsecv`, `Responseid`, `cardtype`, `cardissuer`, `cardacquirer`, `TranzilaTK` when the charge was
  approved and `tokenize` is `true`, `txn_type`, `tranmode`, the handshake's `request_params` and `ConfirmationCode`;
  a key the charge sent under one of the notify's own names is not echoed, as on the page. The address follows the
  page's rule: a `notify_url` must be a callback target configured for the terminal's account, else the charge is
  refused; without one, the account's one configured target stands in, and with none or several nothing is sent.
  `TranzilaTK` and `identity_number` are withheld from the trail.
- `response_hash` = HMAC-SHA256, lowercase hex, over the response JSON without `response_hash`, serialised with no space
  after `,` or `:`, keyed by the account secret (published rule and example, "Response Hash"; the per-user secret for
  `requested_by_user` is not modelled). The simulator writes every character below a space or above `~` as `\uXXXX`
  (DEL included) and `/` unescaped, as `json.dumps` in the published Python sample re-serialises, and `response_hash` is the last key, so the answer less
  `,"response_hash":"…"` is the hashed text.
- Errors: client-side validation calls back `err` as `{error:"invalid_resource", error_description, messages:[{code,
  message, param}]}` (vendor script; the prose's `error.messages.forEach` agrees); a server refusal answers
  `errors:[{code, param, message}]` with codes `10000`–`10016` (published rule, "Error Messages").
- 3-D Secure: a `challengeUrl` in the answer opens the simulator's challenge page in a frame; on success the script
  posts `{track_id, supplier}` to the process route and the result reaches the `charge` callback (vendor script).
  Every charge is authenticated, as a terminal enrolled in the service is ("only if your terminal has enrolled in the
  service", published rule): the typed number decides by the published test cards, otherwise the run's scenario on
  `3ds` and `force_challenge` (`1` challenges, anything else does not), as on the API. `force_txn_on_3ds_fail` is `Y`
  unless sent `N` (published default), so the frictionless fail card is charged by default. A challenge answers
  `{"challengeUrl":"…","track_id":"…"}` with no `response_hash` (simulator decision: it is no final answer), records
  nothing and notifies nothing; the hidden frame tells the merchant's page, at that page's origin, to open
  `challengeUrl` on this script's own origin in a frame of the vendor script's size (400×650, in a 410×650 holder,
  bounded by the viewport) with a close control that calls nothing back, as the vendor script's does. The answered
  challenge page tells the frames of its own origin beside it the `track_id` and nothing else; the hidden frame takes
  it only from a frame of its own origin under the same page, for a challenge it is waiting on, and posts `track_id` and
  `supplier`; its answer closes the challenge frame and reaches the `charge` callback, hashed. That continuation
  completes the challenge as complete does (one transaction, once), renews the `thtk` when approved and sends the
  notify. A continuation of no challenge waiting to be completed, or of one the payer has not answered, answers
  `errors` `[{code: 900, param: "track_id", message: "Transaction failed during 3D Secure validation"}]`; a
  `supplier` naming no terminal of the run answers `10013` with `param` `supplier`.
- No server-side verification of a Hosted Fields result is published; `response_hash` is the only proof.

Server refusals answer `200` `{"errors":[{code, param, message}],"transaction_response":null,"response_hash":…}`,
`code` an integer and `message` the table's text; nothing is charged. Every key refused is answered together; the
terminal and the handshake are checked once every key is admitted. `response_hash` is `null` when the charge names
no terminal of the run, since no secret is named then. Which condition answers which code is a simulator decision:

| Condition                                                                         | `code`, `param`                     |
|-----------------------------------------------------------------------------------|-------------------------------------|
| `terminal_name`, `thtk`, `amount`, `credit_card_number`, `expiry_month` or `expiry_year` missing or empty | `10000`, the key |
| `amount` not a positive decimal of at most 13 whole digits and 2 places            | `10006`, `amount`                   |
| `currency_code` other than `ILS`, `USD`, `EUR`                                    | `10012`, `currency_code`            |
| `tran_mode` other than `A`, `V`, `N`                                              | `10014`, `tran_mode`                |
| `payment_plan` other than `1`                                                     | `10007`, `payment_plan`             |
| `credit_card_number` not 8–19 digits passing Luhn, nor 8–10 digits passing the Isracard check (spaces dropped) | `10002`, `credit_card_number` |
| `cvv` not 3–4 digits                                                              | `10003`, `cvv`                      |
| `expiry_month` not 1–12; `expiry_year` not four digits                            | `10004`, `expiry_month`; `10005`, `expiry_year` |
| `card_holder_id_number` not 1–9 digits                                            | `10011`, `card_holder_id_number`    |
| `DCdisable` over 254 characters                                                   | `22103`, `DCdisable`, "Invalid DCdisable" |
| `terminal_name` names no terminal of the run                                       | `10013`, `terminal_name`            |
| `thtk` names no live handshake of this terminal locking `amount`                  | `912791`, `thtk`, "Illegal Operation 912791" (the published handshake refusal) |
| `notify_url` names no callback target of the terminal's account                   | `400`, `notify_url`, the reason (the payment page refuses the same address with `400`) |

`10001`, `10008`–`10010`, `10015` and `10016` are never answered: no rule above leaves them a condition.

## Status Mapping

Tranzila has no status field. Every transaction is an object of its own; its native status is
`<txn_type>[ <verify_mode>] <processor code>` (`debit 000`, `verify 5 777`). Every row is a simulator decision.

| Native                                                   | Normalized                                     |
|----------------------------------------------------------|------------------------------------------------|
| `debit` approved                                         | `SUCCEEDED`                                    |
| `verify` `2` approved                                    | `SUCCEEDED` (a card checked, nothing held)     |
| `verify` `5` approved, live                              | `READY_FOR_CAPTURE`                            |
| `verify` `5` past `tranzila.j5-validity`, or reversed     | `CANCELLED`                                    |
| `verify` `5` forced                                      | `SUCCEEDED`                                    |
| original with a credit below its amount / equal to it    | `PARTIALLY_REFUNDED` / `REFUNDED`              |
| cancelled `debit`                                        | `CANCELLED`                                    |
| 3-D Secure challenge `challenge awaiting the payer`, `challenge passed, not completed`, `challenge failed, not completed` | `NOT_INITIALIZED` |
| 3-D Secure challenge completed                           | the native and normalized status of the transaction it made, or of the duplicate it answered |
| any decline, `900`                                       | `FAILED`                                       |
| `force`, `credit`, `reversal`, `cancel` approved / declined | `SUCCEEDED` / `FAILED`                      |
| cancelled `force` or `credit`                            | `CANCELLED`                                    |
| payment page waiting for its payer (`awaiting the payer`) | `NOT_INITIALIZED`                             |
| payment page left (`left without paying`)                | `CANCELLED`                                    |
| payment page paid or declined                            | the native and normalized status of the transaction it made; `K` pages `K <Response>`, `SUCCEEDED`; a page whose transaction is no longer held `paid` `SUCCEEDED` or `declined` `FAILED` |

A `force`, `credit`, `reversal` or `cancel` has its own `transaction_id` and an empty ledger, and relates to its
original by `reference_txn_id`; the original's ledger operations name it in `createdObjectId`. A payment is no longer
refundable once it has a `credit`, approved or cancelled, or once it is cancelled or declined; a declined `credit` does
not count. An original whose `credit` was cancelled returns to its normalized status before the credit, with
`refundsAccepted` `false`.

## Error Shape

| Host / route                          | Failure                                     | Body                                                                                       | Evidence                       |
|---------------------------------------|---------------------------------------------|--------------------------------------------------------------------------------------------|--------------------------------|
| `api.tranzila.com` create, complete   | application error                           | `200`, `{"error_code": 20112, "message": "…"}` (integer)                                   | published schema               |
| `api.tranzila.com` create, complete   | `400` `20001` Invalid JSON Format, `20004` JSON Schema mismatch; `20003` Invalid JSON Schema is published and never answered | `Error` schema: `{"error_code": "20004", "message": "…"}` (string) | published schema               |
| `api.tranzila.com` create, complete   | `401` `20000` Authentication failed, `20002` Authorization failed | `Error` schema                                                       | published schema               |
| `api.tranzila.com` under Tranzila's namespaces | unknown address `404`; wrong method `405` | `{"error_code":"404","message":"The specified resource was not found"}`; `{"error_code":"405","message":"This address does not answer this HTTP method"}` | simulator decision: the `404` text is the one declared on the create operation; no `405` is published |
| `/v1/transactions`                    | auth, schema                                | as create (only `200` is declared)                                                         | simulator decision             |
| `report.tranzila.com`                 | unknown address `404`; wrong method `405`   | `{"error":"Not Found","message":"The specified resource was not found"}`; `{"error":"Method Not Allowed","message":"This address does not answer this HTTP method"}` | simulator decision |
| `/v2/handshake/create`                | `400`, `401`                                | `error_code` equal to the HTTP status                                                      | published example              |
| under `/v2/handshake/create`          | unknown address `404`; wrong method `405`  | the handshake's shape, `{"error_code":404,"message":"The specified resource was not found"}`, `405` with "This address does not answer this HTTP method" | simulator decision |
| `report.tranzila.com`                 | `400`, `401`                                | `{"error":"Bad Request","message":"terminal_name is required."}`, `{"error":"Unauthorized","message":"Invalid access token. …"}`; another `400` names its reason in `message`, and a `401` adds its reason after the published sentence | published example; the other messages simulator decision |
| payment page, Hosted Fields frames, 3-D Secure challenge | refusal                  | a simulator-written HTML page under the page's own policy and `Cache-Control: no-store`, also for an unknown address or a wrong method under `/tranzila/directng`, `/tranzila/hf` and `/tranzila/3ds`: `404` no such terminal, page or challenge, `400` a parameter or a `thtk` refused, `422` a payment refused (the page stays open), `409` being completed by another submission, `410` already completed or a challenge already answered | simulator decision |
| Hosted Fields charge                  | refusal                                     | `200`, `errors` ("Hosted Fields")                                                          | published rule, simulator decision |

The `Error` schema's `'response '` key (trailing space) is not answered: it is not required.

## Out of Scope

Published and not served: STO / My Billing (`txn_type` `sto`, `verify_mode` `6`, `/v1/sto/*`, `/v2/sto/*`, page
`tranmode` `J`, `recur_*`), instalments (`payment_plan` / `cred_type` other than `1`), Bit, PayPal, MASAV, invoices,
EMV PinPad, payment requests (`/v1/pr/create`), the legacy CGI host `secure5.tranzila.com` and Dtran,
`direct.tranzila.com`, Handshake V1, `/v1/terminals/settings/fields/list`, Hosted Fields wallet and bank-transfer
charges (`chargeBit`, `chargeGpay`, `chargeBt`, `hfChargeApple`), Apple Pay and Google Pay outside the payment page
(native apps, the Apple Pay merchant validation and `apple_pay_token` schemas).

## Vendor Contradictions

| Topic                          | Publication A                                                                          | Publication B                                                                                      | Simulator                                  |
|--------------------------------|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|--------------------------------------------|
| which card API page            | Transactions API (visible, not in sitemap)                                             | Tranzila API (hidden, in sitemap)                                                                  | the Transactions API                       |
| verify mode field              | `verify_mode`, `2`/`5`/`6` (Transactions API, 3DS schema)                              | `"verfiy_mode"` in the hidden page's examples; `verfiy_mode` enum `[5]` in its Apple Pay schema     | `verify_mode`                              |
| `authorization_number` on `force` | "Ignored for other transaction types"; force example omits it (Transactions API)    | "Relevant for force transactions" (3DS schema); "The force charge (J4) must include … authorization_number" (Apple Pay page) | optional, checked when present |
| card fields on `cancel`        | schema requires `expire_month`, `expire_year`, `card_number` (Transactions API)        | cancel example sends only `terminal_name`, `txn_type`, `reference_txn_id`, `authorization_number`  | card fields not required on `cancel`       |
| HMAC construction              | message app key, key secret + time + nonce, seconds (auth pages)                       | message app key + time + nonce, key secret, milliseconds (quickstart)                              | `tranzila.access-token`                    |
| nonce                          | "40 bytes" (auth page); 80 hex characters (PHP sample, reports)                        | 40 hex characters (quickstart); "Generate once and reuse" (reports header) vs "unique per request" (Handshake V2) | any length; reuse per host          |
| hex case                       | lowercase (Handshake V2)                                                               | uppercase (.NET sample)                                                                            | case-insensitive                           |
| J2/J5 approval code            | `777` (code table)                                                                     | `000` (vendor correspondence)                                                                      | `tranzila.verification-approval-code`      |
| currency format                | API ISO alpha-3 (`ILS`); Hosted Fields request ISO                                     | page `currency` numeric (`1`, `2`, `978`, `826`); Hosted Fields response `currency_code: 1`; reports `'1'` | each surface as published          |
| currency enum                  | `GBP` (Transactions API)                                                               | `GPB` (hidden page schema); `EUR`/`USD`/`ILS` only (3DS component)                                 | `GBP`                                      |
| amount units                   | decimal currency units (API, page `sum`, Hosted Fields "In currency")                  | "smallest currency unit (agorot for ILS)" (`report.tranzila.com`); `amount: '500'` (`/v1/transactions` example) | each surface as published; `/v1/transactions` in minor units (inference) |
| CVV / ID result names          | `Responsecvv` / `Responseid` (API)                                                     | `Responsecv` / `Responseid` (page); `cvv_status` / `id_status` (Hosted Fields)                      | each surface as published                  |
| error envelopes                | `error_code` integer in `200` bodies; string in the `Error` schema                    | `error_code` = HTTP status (Handshake V2); `{error, message}` (reports)                            | each host as published                     |
| field types                    | `transaction_id` integer, `auth_number` string (API schema)                            | strings `"372803"` (3DS complete), `"97588"` (Hosted Fields)                                       | each route as its example                  |
| `force_txn_on_3ds_fail` default | `Y` (Hosted Fields)                                                                   | `N` (3DS schema)                                                                                   | each surface as published                  |
| Hosted Fields challenge frame  | "an iframe (400x600)" (prose)                                                          | 400×650 in a 410×650 holder (vendor script)                                                        | the vendor script's                        |
| `auth_3ds_redirect`            | array of `{key, value}` in `3ds_settings`, no URL (schema)                             | top-level `{url, params}`, with `force_txn_on_3ds_fail` also top-level (complete example); `uth_3ds_redirect` (prose) | top-level `{url, params}` admitted, supplies URL and params; the `3ds_settings` array supplies params only; a top-level `force_txn_on_3ds_fail` is admitted, and `3ds_settings.force_txn_on_3ds_fail` wins when both are sent |
| page tranmodes                 | `A`, `V`, `K`, `N`, `J` (DirectNG)                                                     | adds `AK`, `NK`, `VK` (legacy page)                                                                | combinations served                        |
| wallet tranmodes               | Apple Pay "only supports transaction modes A … and V" (Apple Pay page); Google Pay "tranmode A and V only" (DirectNG) | "A/AK and V/VK" for both (legacy page)                                                  | `A`, `V`, `AK`, `VK`: the `K` suffix asks only for the token every approved page payment answers |
| Apple Pay token                | "returns it in the charging service as a Tranzila token for reuse" (DirectNG, legacy page); J5 "returns … Token" for a later force (Apple Pay page) | "creates a Tranzila token for future use (unlike Apple Pay)" (DirectNG); "cannot be used for new recurring payments" (Apple Pay page) | a token answered; good for its own payment's `force`, `credit`, `cancel`; refused for a new charge |
| Apple Pay browsers             | "users on the CHROME browser cannot pay with APPLE PAY" (DirectNG, legacy page)        | Chrome 78+ supported (Apple Pay page)                                                              | the stand-in is shown in every browser     |
| notify success value           | `Response` `000` (page)                                                                | "'000' or '0' = success" (notify guide)                                                            | `000` sent                                 |
| Hosted Fields errors           | `errors[{code, param, message}]`, codes `10000`–`10016` (table)                        | `err.messages[]` (prose, vendor script)                                                            | client-side `err`, server `errors`         |
| Hosted Fields Enter key        | `submit` "request upon Enter key in hosted fields" (prose)                             | `inputSubmitRequest` (vendor script)                                                               | both sent                                  |
| user-defined slots             | 25 (Transactions API, reports)                                                         | 20 (`/v1/transactions`)                                                                            | each lookup as published                   |
| `transtatus`                   | `'0'` beside `processor_response_code` `'000'` (`/v1/transactions` example)              | "`1` = transaction successful / settled, `0` = failed or declined" (reports)                        | `1` approved, `0` declined, on both        |
| `credit_card_token`            | `'2312'`, four digits (`/v1/transactions` example)                                     | "Tranzila token representing the card" (reports)                                                   | the token on both                          |
| `/v1/transactions` `txn_type`  | `''` (example)                                                                         | the `tranmode` table's names (`debit`, `j2`, …)                                                    | the table's name                           |
| `user_defined` metadata        | in the `/v1/transactions` example; `UserDefinedFieldMeta` "Returned in the `user_defined` array of the `/v1/transaction` response" (reports) | absent from the reports' response schema and example                     | answered on both                           |
| card issuer of a Visa          | `card_issuer` `2` (C.A.L) for a Visa (reports example)                                  | `cardissuer=1` beside `cardtype=1` (DirectNG return sample)                                        | `1` for every card on the page and both lookups |
| date range                     | end "inclusive" (reports)                                                              | an example whose start (`2023-11-12`) follows its end (`2023-08-31`) (`/v1/transactions`)          | accepted; such a range finds nothing       |

## What This Contract Could Not Settle

| Gap                                                          | Simulator                                                      |
|--------------------------------------------------------------|----------------------------------------------------------------|
| sandbox host, test terminal, general test-card list          | none; only the 3-D Secure test cards are published             |
| request-time tolerance                                       | ±5 minutes                                                     |
| decline body of create                                       | `error_code` = processor code ("Response Codes")               |
| DCdisable duplicate answer                                   | the original's result, `error_code` `0`                         |
| J5 validity; partial, multiple and over-capture              | `tranzila.j5-validity`; one `force`, ≤ J5 amount, `507` above  |
| partial `reversal`; `cancel` after settlement                | full reversal only; `cancel` any time                          |
| arithmetic of discounts and exchange rates                   | refused `20004`                                                |
| 3-D Secure create challenge answer; redirect method          | `challengeUrl` + `track_id`; browser `POST` to `auth_3ds_redirect.url` |
| 3-D Secure challenge page, its failing codes, the fields posted back | the simulator's page; any code but `555` fails; the params and `track_id` |
| `t3ds_data` values of a simulated authentication              | the published example's, `xid` and `cavv` drawn per answer      |
| full notify field list, signature, retry schedule, on decline | the return's fields plus `ConfirmationCode`, unsigned, simulator retry plan, sent |
| page `K` answer (no transaction recorded)                    | `Response` per `tranzila.verification-approval-code` (inference from the `777` text), `TranzilaTK`, no `index` |
| the page's form and its cards                                | the published test card and the run's fixtures, picked by alias |
| a wallet's sheet; a wallet with a tranmode it does not pay; a wallet field in the return | the simulator's sheet; no button; none     |
| API `card_type` ("shva card type") values                    | the page's `cardtype` table (`1` Mastercard … `6` Maestro)     |
| token format                                                 | `t`, 14 hex digits, the card's last four                       |
| `/v1/transactions` amount units                              | minor units, as a string (inference from `amount: '500'`)      |
| lookup dates and times: zone                                 | UTC on the run clock                                           |
| which slot a user-defined field takes; `/v1/transactions` order | the order a terminal first recorded the name; oldest first  |
| Hosted Fields: which code a condition answers, the refusal envelope, a `thtk` not locking the amount | "Hosted Fields"; `912791` |
