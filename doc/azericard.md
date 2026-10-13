# Azericard Simulator Contract

## What This File Covers

The vendor contract for the Azericard e-Commerce gateway, and what this simulator answers on each route. It covers
hosted authorization and pre-authorization, sales completion, online and offline reversal, card storage and stored-card
payment, the transaction-status inquiry, the `BACKREF` callback, and `P_SIGN` RSA signing.

Each statement names its evidence class (`README.md`, "Evidence Classes"): published rule, published example, live
probe, inference or simulator decision.

| Route                                  | What the simulator does                                                                                                 | Key deviations                                                                                                                                                 |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /cgi-bin/cgi_link` `TRTYPE=0/1`  | with the §2.3 card fields: verifies `P_SIGN`, claims the order trace, authorizes; XML under `text/html`. Without them: the same checks, then the payer page | the response `P_SIGN` order is a simulator decision; the amount signed is never fee-adjusted; see "Payer Page"                                                  |
| `POST /cgi-bin/cgi_link` `TRTYPE=21`   | completes a hold, in full or in part                                                                                    | partial completion closes the authorization unless `azericard.partial-completion` says otherwise; MAC always required                                          |
| `POST /cgi-bin/cgi_link` `TRTYPE=22/24`| reverses the whole reversible remainder; both types behave the same                                                     | no partial reversal; releasing an uncompleted hold is `CANCELLED`, returning completed money is `REFUNDED`                                                     |
| `POST /cgi-bin/cgi_link` `TRTYPE=90`   | status inquiry; JSON under `application/json; charset=UTF-8`                                                            | the success keys beyond the observed refusal are the simulator's own; `-5` and `-6` are invented codes                                                         |
| `POST /token/cgi_link`                 | card save (`TOKEN_ACTION=REGISTER`, `MERCH_TRAN_STATE=S`; payer page unless the §2.3 card fields are sent), stored-card payment (`TOKEN`) and MIT unscheduled payment (`MERCH_TRAN_STATE=M`); queues the `BACKREF` callback; with the §2.3 card fields or a `TOKEN` and no target of the terminal's account, it still authorizes and the callback is undeliverable | signed with the `TRTYPE=0/1` order; CIT (`C`) payment refused; MIT scheduled payment (`TRTYPE=185`) not served                                                 |
| `POST /azericard/payer`                | the payer page's own submission: authorizes, queues the `BACKREF` callback, answers the result page                     | the route, the page and the result page are the simulator's own (see "Payer Page")                                                                            |
| `POST <BACKREF>`                       | form-encoded server-to-server callback signed over the published five fields                                            | `azericard.callback-signature` can send it `CORRUPTED` or `ABSENT`; acknowledged by any `2xx`                                                                  |

Account settings of this provider: `terminalId`, `apiKey` (merchant public key, PEM), `apiSecret` (gateway private key,
PEM), `azericard.duplicate-response-code` (default `-1`), `azericard.partial-completion` (`CLOSES_THE_AUTHORIZATION`
default, `LEAVES_THE_REMAINDER_CAPTURABLE`), `azericard.callback-signature` (`VALID` default, `CORRUPTED`, `ABSENT`), `storedCardSourceTerminalIds`
(comma-separated, empty by default), `azericard.settlement-mac` (`LIVE_WIRE` default, `PUBLISHED_EXAMPLE`).

## Sources

Fetched on 2026-09-10, the sandbox manual again on 2026-09-25. Probes under "Test Data" are from 2026-09-25; every
other probe is from 2026-09-10.

| Source                                     | URL                                                                                                                                                                        |
|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sandbox integration manual, Azerbaijani    | https://developer-test.azericard.com/                                                                                                                                      |
| Same manual in English (quoted below)      | https://developer-test.azericard.com/en                                                                                                                                    |
| `P_SIGN` calculator                        | https://developer.azericard.com/i/p-sign                                                                                                                                   |
| Sandbox MAC calculators                    | https://testsite.3dsecure.az/sandbox/p-sign.php, https://testsite.3dsecure.az/sandbox/checkout-reversal-psign.php, https://testsite.3dsecure.az/sandbox/transaction_status_psign.php |
| Sandbox gateway probed                     | https://testmpi.3dsecure.az/cgi-bin/cgi_link                                                                                                                               |

`https://developer.azericard.com/`, `https://azericard.com/` and the production gateway `https://mpi.3dsecure.az/`
answer `403` behind a WAF; only the `/i/p-sign` leaf is reachable. The sandbox manual carries the whole contract.

Probe classes: the 2026-09-10 probes came from an unenrolled terminal and show how the gateway refuses a message it
cannot authenticate. The 2026-09-25 probes came from the published test terminals, signed with the published key, and
show how it refuses a message it *has* authenticated. **No probe completed a payment**, so no successful
authorization, settlement or `BACKREF` callback has been observed.

The manual publishes **no `RC` table** and **no MAC order for `/token/cgi_link`**. Every decline code and every token
MAC order the simulator uses is a simulator decision.

## Authentication, Signing and Transport

- Requests are HTTP `POST`, `application/x-www-form-urlencoded` (published rule and every curl sample).
- `P_SIGN`: "`P_SIGN | 1-256 | Merchant MAC in hexadecimal form.`" (published rule).
- MAC source: "all field values from the format tables are prefixed with the decimal field length in ASCII and
  concatenated in a specified order" (published rule, "P-Sign generation").
- An absent or empty value contributes a bare `-` and **no length prefix**: "If the field is not present, the '-'
  character is added to the message in its place", and "if the value of any of the specified variables is sent empty,
  then a - (hyphen) sign is added instead of that value, **and its length is ignored in the P_SIGN part**" (published
  rule, "Calculation of callback P_SIGN"). The `ACTION=3` example returns `APPROVAL`, `RRN` and `INT_REF` all empty, so
  this is on the ordinary callback path.
- `P_SIGN` is never in its own source: "MAC is calculated over all fields ... except the MAC field ("P_SIGN") itself".
- Algorithm: "The default MAC algorithm is RSAwithSHA256. Additional options may be available on demand", and the
  administrator "may specify which algorithm will be used for a particular merchant terminal" (published rule).
- Key length floor: "at least 112 bits for asymmetric cryptographic algorithms and 2048 bits for RSA with SHA256".
- Hex case: "MAC field value can be either an upper case or lower case hexadecimal string". The simulator emits upper
  case, as the sandbox does (live probe, `TRTYPE=90`: `"signature":"5EC7B40839EB09DE..."`), and accepts either.
- Key generation is published only as `openssl genrsa -out merchant_name_private_key.pem 2048` and
  `openssl rsa -in merchant_name_private_key.pem -pubout -out merchant_name_public_key.pem`. The simulator accepts PKCS#1
  and PKCS#8 private keys and X.509 SubjectPublicKeyInfo public keys.
- Key directions: "merchant should generate and give his public key to Azericard and Azericard will give his public key
  to merchant". The merchant verifies gateway messages: "use the `hex2bin(P_SIGN)` function, then verify the data via
  `AZERICARDpublic.pem`".
- Completion and reversal "may be mutually authenticated with SSL client/server certificates and does not require MAC,
  if they are not mutually authenticated, MAC for these messages is mandatory" (published rule). **Simulator decision:**
  the MAC is always required on `TRTYPE=21/22/24`; mutual TLS is not implemented.

## Signature Field Order

Four orders are published. Each worked example below was reproduced byte for byte with Azericard's own calculator
(published example plus live probe of the vendor tool).

| Operation                          | Fields, in order                                                  | Not in the MAC though required               |
|------------------------------------|-------------------------------------------------------------------|----------------------------------------------|
| `TRTYPE=0`, `TRTYPE=1` ("2.2.1")   | `AMOUNT`, `CURRENCY`, `TERMINAL`, `TRTYPE`, `TIMESTAMP`, `NONCE`, `MERCH_URL` | `ORDER`, `DESC`, `MERCH_NAME`, `EMAIL`, `COUNTRY`, `MERCH_GMT`, `BACKREF` |
| `TRTYPE=21/22/24` ("2.2.2"–"2.2.4")| published: `AMOUNT`, `CURRENCY`, `TERMINAL`, `TRTYPE`, `ORDER`, `RRN`, `INT_REF`; live probe, 2026-10-12, against the gateway a real merchant reaches: `AMOUNT`, `CURRENCY`, `ORDER`, `RRN`, `INT_REF`, `TERMINAL`, `TRTYPE`, `TIMESTAMP`, `NONCE`. `azericard.settlement-mac` names which of the two a terminal is verified against, `LIVE_WIRE` by default | published: `TIMESTAMP`, `NONCE`; live probe: none |
| `TRTYPE=90` ("8.3")                | `ORDER`, `TERMINAL`, `TRTYPE`, `TIMESTAMP`, `NONCE`               | `TRAN_TRTYPE`                                |
| Callback ("Calculation of callback P_SIGN") | `AMOUNT`, `TERMINAL`, `APPROVAL`, `RRN`, `INT_REF`       | —                                            |

Worked examples:

- `TRTYPE=1`: `11.48`, `USD`, `99999999`, `1`, `20030105153021`, `F2B2DD7E603A7ADA`, `www.sample.com` →
  `511.483USD89999999911142003010515302116F2B2DD7E603A7ADA14www.sample.com`.
- `TRTYPE=21`: `11.48`, `USD`, `99999999`, `21`, `20210506070034`, `112676199769`, `5E3601D7C71745A9` →
  `511.483USD899999999221142021050607003412112676199769165E3601D7C71745A9`. This is the published order and the
  simulator computes it for a terminal whose `azericard.settlement-mac` is `PUBLISHED_EXAMPLE`; the default signs the
  live-probe order instead, which the manual's own example cannot be checked against because it names no `TIMESTAMP`
  and no `NONCE`.
- `TRTYPE=90`: `20211112075614`, `17202191`, `90`, `20211112075714`, `7cfb4c2512eeec72` →
  `14202111120756148172021912901420211112075714167cfb4c2512eeec72`. The manual captions this 62-character string "This
  string is 190 bytes long"; the string is the contract.
- Callback: `11.48`, `99999999`, `168975`, `306276834930`, `4A29E93C607E33DC` →
  `511.48899999999616897512306276834930164A29E93C607E33DC`.

The completion/reversal order is **not** the "Order description" request-field table of "2.1.1"; signing that table's
order produces a MAC the gateway rejects.

"2.2" also shows a source string `81720078010511.48142003010515302116IT Books. Qty: 2` (`TERMINAL`, `TRTYPE`, `AMOUNT`,
`TIMESTAMP`, `DESC`) that matches no published order. It is not implemented.

"2.2" prefaces the orders with "The order of fields **in test terminal** (terminal id: The terminal will be presented
during the test) is as follows", so strictly they describe the test terminal. The simulator treats them as the contract;
the order is not configurable.

Orders the vendor does not publish, and what the simulator does:

| Operation                                    | Simulator decision                                                                                   | Evidence                                                                                                                                                                                   |
|----------------------------------------------|------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| MIT stored-card payment, `/token/cgi_link`   | the `TRTYPE=0/1` order; no token field signed                                                        | live probe, 2026-09-25 and 2026-09-29: `AMOUNT`, `CURRENCY`, `TERMINAL`, `TRTYPE`, `TIMESTAMP`, `NONCE`, `MERCH_URL`                                                                         |
| card registration (`MERCH_TRAN_STATE=S`)     | the same order; `TOKEN_ACTION`, `MERCH_TRAN_STATE`, `MERCH_RN_ID`, `MIT_AGREEMENT` unsigned           | live probe, 2026-09-29, the same seven fields                                                                                                                                              |
| CIT payment, `TRTYPE=0` on the token route   | same order, `TOKEN_ACTION`, `MERCH_TRAN_STATE`, `MERCH_RN_ID`, `MIT_AGREEMENT`, `TOKEN`, `EXT_NET_REF` unsigned | nothing published; "6" and "7" are additive to the e-commerce request, but `MERCH_RN_ID` is glossed "Must be considered when MAC is used" |
| synchronous response `P_SIGN` (`cgi_link` and `TRTYPE=90`) | the five-field callback order                                                           | published for the callback only                                                                                                                                                            |

## Routes and Response Formats

Published routes: `POST https://testmpi.3dsecure.az/cgi-bin/cgi_link` (authorization, pre-authorization, completion,
online and offline reversal, status, and the S2S, GooglePay and ApplePay variants), `POST
https://testmpi.3dsecure.az/token/cgi_link` (card storage, stored-card, CIT and MIT payments), and `POST <BACKREF>` (the
callback). No production host is published. The simulator serves `POST /cgi-bin/cgi_link` and `POST /token/cgi_link`;
the base URL is configurable.

| Response                          | Vendor                                                                                                                                                                                                          | Simulator                                               |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `cgi_link` other than `TRTYPE=90` | XML, lowercase elements, `INT_REF` spelled `intref`: `<response><desc><terminal><trtype><order><amount><currency><action><rc><approval><rrn><intref><timestamp><nonce><p_sign></response>` (published example), served as `Content-Type: text/html` (live probe, `TRTYPE=21`) | XML under `text/html` (`AzericardResponse.CONTENT_TYPE`)     |
| `TRTYPE=90`                       | JSON under `application/json, charset=UTF-8`, keys `terminal`, `actionCode`, `responseCode`, `statusMsg`, `tr_type`, `nonce`, `signature`, `timestamp` (live probe)                                               | JSON under `application/json; charset=UTF-8` (`AzericardStatusResponse.CONTENT_TYPE`) |
| missing required field            | `HTTP 200`, `text/html`, a human page with no `ACTION`, `RC` or signature (live probe)                                                                                                                          | the same three sentences in HTML of the simulator's own (`AzericardErrorPage.java`) |

Both content types are fixed; no setting serves another format. Format facts:

- "8.2 Response can be in HTML, XML and JSON format according to Merchant request" concerns status only, names HTML, and
  publishes no mechanism. `Accept: application/xml` on a `TRTYPE=90` probe changed nothing.
- No form-url-encoded gateway response is published or observed. Form encoding is the request and callback encoding.
- There is no `PSIGN` field; the string occurs only in the calculator page's title. The spelling hazards are `<intref>`
  in XML and `signature` in status JSON.
- The missing-field page reads: "Sorry, due to technical fault your request cannot be processed / Error message: Error in
  merchant terminal field / Please try once again or visit us later" (live probe, `TRTYPE=90` without `TERMINAL`).
- The missing-field page carries the same four defence headers as the payer page, in the same order (simulator
  decision: the payer's webview posts the signed form to these two routes, so this page is read by a browser like any
  other page these routes answer with).

## Shared Fields

Sizes are the vendor's field tables (published rule). Where two tables disagree, both are given.

| Field        | Size          | Published text and notes                                                                                                                                                                   |
|--------------|---------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AMOUNT`     | 1-12          | "Order total amount in float format with decimal point separator". Examples `10`, `5`, `1.00`, `11.48`. `10.00` and `10.0` sign differently; the simulator echoes the received string. |
| `CURRENCY`   | 03            | "Order currency: Consist of 3 symbols". Requests use `AZN`; every XML response example returns `<currency>944</currency>` (ISO numeric). Unreconciled.                               |
| `ORDER`      | 6-32; 6-20 in the status table | "Merchant order ID, numeric. Last 6 digits used as a system trace audit number, which must be unique within a day for the terminal id"                                     |
| `DESC`       | 1-50          |                                                                                                                                                                                            |
| `MERCH_NAME` | 1-50          |                                                                                                                                                                                            |
| `MERCH_URL`  | 1-250         | "Merchant primary web site URL"                                                                                                                                                            |
| `BACKREF`    | 1-250         | "Merchant URL for posting authorization result"                                                                                                                                            |
| `TERMINAL`   | 8             | "Merchant Terminal ID assigned by bank"                                                                                                                                                    |
| `EMAIL`      | 80            | "If this field is present Gateway may send transaction results notification to specified e-mail address"                                                                                   |
| `TRTYPE`     |               | the operation                                                                                                                                                                              |
| `COUNTRY`    | 02            | only "if merchant system is located in a country other than the gateway server`s country"                                                                                                 |
| `MERCH_GMT`  | 1-5           | "Merchant UTC/GMT time zone offset (e.g. –3)"; required only if the merchant is in another time zone than the gateway; also spelled `MERCHANT_GMT` |
| `TIMESTAMP`  | 14            | "Merchant transaction timestamp **in GMT**: YYYYMMDDHHMMSS. Timestamp difference between merchant server and e-Gateway server **must not exceed 1 hour**, otherwise e-Gateway will reject this transaction." Enforced. |
| `NONCE`      | 1-64          | "Must be filled with 8-32 unpredictable random bytes in hexadecimal format. Must be present if MAC is used." Examples are 16 hex characters.                                             |
| `LANG`       | 2             | "Language type"; no value list                                                                                                                                                             |
| `NAME`       | 2-45          | "Customer's name (as indicated on the card)"                                                                                                                                               |
| `M_INFO`     | 35000         | Base64 JSON with `browserScreenHeight`, `browserScreenWidth`, `browserTZ`, `mobilePhone` for 3-D Secure 2. Not in any MAC and not in the callback; the simulator redacts it from the audit, keeps it in no payer session and leaves it out of request fingerprints |
| `P_SIGN`     | 1-256         | hex, either case                                                                                                                                                                           |
| `NOTIFY_URL` |               | in no field table; posted by the sandbox form `https://testsite.3dsecure.az/sandbox/auth.php` (published example). Purpose unpublished. The simulator accepts it and reads it for nothing |

S2S-only fields ("2.3 S2S integration", "valid only for merchants with a valid PCI DSS certificate"):

| Field               | Size | Published text                                                                  |
|---------------------|------|---------------------------------------------------------------------------------|
| `MERCH_3D_TERM_URL` |      | "MerchantURL where will return Cres response from client ACS."                  |
| `CARD`              | 9-19 | "Card number (Primary account number)."                                         |
| `EXP`               | 02   | "Card expiration month (Numeric 2 digit value)."                                |
| `EXP_YEAR`          | 02   | "Card expiration year (Numeric 2 digit value: 20XX)"                            |
| `CVC2`              | 03   | "Card verification code(last three digits on the signature panel)."            |
| `CVC2_RC`           | 01   | `1` present, `0` not provided, `2` illegible                                    |

The simulator never stores, audits, prints or returns `CARD`, `EXP`, `EXP_YEAR` or `CVC2`; it keeps the last four digits
of the card number. `MERCH_3D_TERM_URL` is accepted and not read: no 3-D Secure step is simulated. A `CARD` request
missing `EXP` or `EXP_YEAR`, carrying a `CVC2_RC` other than `0`, `1` or `2`, or missing `CVC2` when `CVC2_RC` is `1` or
absent, is answered with the missing-field page naming that field; with `CVC2_RC` `0` or `2` no `CVC2` is expected or
checked (simulator decision).

Token and recurring fields ("6 Card storage", "7 Recurring"):

| Field              | Size  | Published text                                                                                                                                              |
|--------------------|-------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TOKEN_ACTION`     | 8     | `REGISTER`: "must be posted when storing the token"; "should not be posted for saved card payments"                                                         |
| `MERCH_TRAN_STATE` | 1     | "Authorization initiation indicator": `S` card storage, `C` CIT payment, `M` MIT unscheduled payment                                                        |
| `MIT_AGREEMENT`    | 1     | "Indicates whether a payment agreement exists with the cardholder for Merchant-Initiated Transactions": `N` MIT Unscheduled, `Y` MIT Scheduled |
| `MERCH_RN_ID`      | 16    | "Must be generated using 8–32 random bytes and represented in hexadecimal format. Must be considered when MAC is used." Size 16 and 8–32 bytes cannot both hold. |
| `TOKEN`            | 28    | "TOKEN parameter of saved card"                                                                                                                             |
| `EXT_NET_REF`      | 6-32  | "Value returned to the merchant during card storage"; "If the EXT_NET_REF value is not returned in the response to the card storage request, then there is no need to send the parameter during payment"         |
| `RECUR_FREQ`       | 2     | "Minimum time interval between authorizations (charges). Numeric format"                                                                                    |
| `RECUR_EXP`        | 8     | `YYYYMMDD`; "The subscription may last up to 1 year from the card registration date"                                                                        |
| `TRAN_TRTYPE`      | 1-2   | "Original transaction type for state request (For example: TRTYPE 0, 1, 22, 24 and etc.)"                                                                   |

Response-only: `ACTION` 1, `RC` 02, `APPROVAL` 06, `RRN` 12, `INT_REF` 1-32 (1-128 in the e-commerce response table),
and `CARD` (masked PAN, in callback examples, in no field table).

## Identifier Spaces

| Identifier | Published                                                                                                   | Simulator                                                                                                                                                           |
|------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ORDER`    | numeric, 6-32; the **last 6 digits** must be unique "within a day for the terminal id". Examples `20220628084800`, `20210506070034`, `20230621052514` | uniqueness enforced on `(TERMINAL, day, last 6 digits)`; a collision is answered `ACTION=1`                                                                         |
| `TERMINAL` | 8, "assigned by bank". Examples `77777777`, `99999999`, `17202191`; one XML example has 7-character `1111111` | neither width enforced; its own bound of 32 characters, refused by `PUT /config` naming `terminalId` (the stored column width; keeps the story key within `StoryKey.LONGEST_KEY`) |
| `RRN`      | 12, ISO-8583 field 37. Examples `112676199769`, `317276406077`, `306276834930`. The `TRTYPE=21/22/24` tables say `1`, a typo | 12 digits                                                                                                                                                           |
| `INT_REF`  | 1-32, "E-Commerce gateway internal reference number". Examples `0A7E21C778330DF2`, `5E3601D7C71745A9`, `4A29E93C607E33DC` |                                                                                                                                                                     |
| `APPROVAL` | 6, ISO-8583 field 38, "**Can be empty if not provided by card management system**". Examples `111111`, `168975` | 6 digits on an approval, empty otherwise                                                                                                                            |
| `NONCE`    | 1-64 characters carrying 8-32 random bytes                                                                 |                                                                                                                                                                     |
| `TOKEN`    | 28; no charset, no example                                                                                  |                                                                                                                                                                     |
| `P_SIGN`   | 1-256 hex characters; observed 512 (2048-bit RSA, live probe)                                               | the real signature length, never truncated                                                                                                                          |

## ACTION and RC

A result is the **pair** `ACTION` plus `RC`, both always present (published rule and example,
`<action>0</action><rc>00</rc>`).

| `ACTION` | Published meaning (e-commerce table)                           | Simulator emits it for                                       |
|----------|----------------------------------------------------------------|--------------------------------------------------------------|
| `0`      | Transaction successfully completed                             | success, with `RC=00`, `desc=Approved`                       |
| `1`      | Duplicate transaction detected                                 | an order-trace collision, `RC` = `azericard.duplicate-response-code` |
| `2`      | Transaction declined                                           | a scenario decline, `RC` from the scenario; a card not accepted or a cancelled payer page, `RC=ZZ` |
| `3`      | Transaction processing error ("Tranzaksiya emal xətası")       | message refusals (`-17`, `-24`) and the invented codes below |
| `6`      | Repeat of a declined transaction                               | never                                                        |
| `7`      | Repeat of a transaction with an authentication error           | never                                                        |
| `8`      | Repeat of a transaction that terminated without a response     | never                                                        |

The S2S, GooglePay and ApplePay tables publish only `0`–`3` and gloss `3` as "Transaction processing fault".

`ACTION=3` is an **error**, not a pending outcome. The only published example is terminal: `<desc>Authentication
failed</desc> ... <action>3</action> <rc>-19</rc> <approval></approval> <rrn></rrn> <intref></intref>`.

`RC` is "Transaction response code (ISO-8583 Field 39)", size `02`. With `ACTION=2` it is the issuer's code; with
`ACTION=3` the gateway's own, and every known one is negative. The simulator emits `RC` as an opaque string.

A configured `azericard.duplicate-response-code` is admitted against `-?[0-9A-Za-z]{1,2}`
(`AzericardDuplicateCode.SHAPE`), and the default is `-1` (`AzericardDuplicateCode.DEFAULT`). Only the two-character
body comes from the published size `02`. The optional sign does not: it is admitted because every gateway code in the
table that follows is negative, and the probed `-24` is three characters on the wire. Letters are admitted because
`ZZ` is a code the simulator itself emits.

| `RC`   | Evidence                                                  | Meaning                                                    | Simulator                                                                 |
|--------|-----------------------------------------------------------|------------------------------------------------------------|---------------------------------------------------------------------------|
| `00`   | published example                                         | approved                                                   | success                                                                   |
| `-19`  | published example                                         | "Authentication failed" (3-D Secure), correlation fields empty | **never emitted**: no cardholder authentication is simulated            |
| `-17`  | live probe                                                | "Access denied": bad `P_SIGN`, still signed                | bad signature, or `TIMESTAMP` outside the one-hour window                  |
| `-24`  | live probe                                                | "Transaction context mismatch": signed `TRTYPE=90` for an unknown `ORDER` | the same case (`AzericardOutcomes.RC_TRANSACTION_CONTEXT_MISMATCH`)  |
| `-2`   | simulator decision                                        | completion or reversal addressing no transaction           | invented                                                                  |
| `-3`   | simulator decision                                        | completion or reversal not admitted (amount, currency, state) | invented                                                               |
| `-4`   | simulator decision                                        | unknown or unusable `TOKEN`: one no account of the run stored, or one stored by a terminal this terminal does not name in `storedCardSourceTerminalIds` | invented; the manual publishes nothing about which terminal may charge a `TOKEN`, so both the restriction and the way out of it are the simulator's own |
| `-5`   | simulator decision                                        | status inquiry after the 24-hour window                    | invented                                                                  |
| `-6`   | simulator decision                                        | status inquiry whose `ORDER` names more than one transaction | invented                                                                |
| `-1`   | simulator decision                                        | default `RC` beside `ACTION=1`                             | `azericard.duplicate-response-code`                                       |
| `ZZ`   | simulator decision                                        | `ACTION=2` for a card the gateway does not accept (see "Test Data") and for a cancelled payer page | invented; two characters and no ISO-8583 meaning, so it reads as nobody's real issuer code |

`05`, `51`, `54`, `57`, `61` are ISO conventions, not Azericard material. `RC=113` and `RC=202` belong to "11 Money
transfer integration", a different product (JSON API on `https://testmt.azericard.com/`, fields `Merchant`, `SRN`,
`Amount`, `Cur`, `ResponseCode`, `Signature`, MD5-signed), whose table reads "113 | The status is pending, the payment
needs to be confirmed to complete transaction" and "202 | The transaction is in progress state"; they are three digits
and cannot fit `02`. **The e-Commerce
gateway publishes no pending or processing outcome.**

## Minimum API Surface

### Authorization and Pre-Authorization

`POST /cgi-bin/cgi_link` ("2 E-Commerce integration").

- Request fields: `AMOUNT`, `CURRENCY`, `ORDER`, `DESC`, `MERCH_NAME`, `MERCH_URL`, `TERMINAL`, `EMAIL`, `TRTYPE`,
  `COUNTRY`, `MERCH_GMT`, `BACKREF`, `TIMESTAMP`, `NONCE`, `LANG`, `P_SIGN`, `NAME`, `M_INFO`. None is marked required.
- `TRTYPE=0`: "Pre-Authorization"; "TRTYPE=0 should be sent in the first POST request for preauthorization, and
  TRTYPE=21 should be sent to confirm the order".
- `TRTYPE=1`: "Authorization" in the e-commerce and S2S sections, "Finance document" in the MAC and token sections.
- Response fields: `TERMINAL`, `TRTYPE`, `ORDER`, `AMOUNT`, `CURRENCY`, `ACTION`, `RC`, `APPROVAL`, `RRN`, `INT_REF`,
  `TIMESTAMP`, `NONCE`, `P_SIGN`, plus `<desc>` in every XML example.
- Response `AMOUNT` is "Amount authorized. **Usually, will be equal to original amount plus acquirer's fee**".
  **Simulator decision:** it signs and returns the requested amount; no fee.
- Rejection (live probe): `HTTP 200`, XML, `action=3`, `rc=-17`, `desc=Access denied`, echoing `terminal`, `trtype`,
  `order`, `amount`, `rrn`, `intref`, with `<currency></currency>` empty even when `AZN` was sent, the gateway's own
  `timestamp`, and a signature.

### Sales Completion

`POST /cgi-bin/cgi_link`, `TRTYPE=21` ("2.1.1": "The following data must be posted to confirm the payment").

- Request fields: `AMOUNT`, `CURRENCY`, `ORDER`, `RRN`, `INT_REF`, `TERMINAL`, `TRTYPE=21`, `TIMESTAMP`, `NONCE`,
  `P_SIGN`; the sample also carries `LANG`. `RRN` and `INT_REF` are "from authorization response".
- Unpublished: what completion is allowed against, partial remainder, repeat handling, hold expiry. Simulator decisions:
  completion only against an approved hold, amount positive, not above the capturable remainder, `CURRENCY` equal to the
  payment's (otherwise `ACTION=3`, `RC=-3`); a partial completion closes the authorization unless
  `azericard.partial-completion` is `LEAVES_THE_REMAINDER_CAPTURABLE`; no automatic expiry.

### Reversal

`POST /cgi-bin/cgi_link`, `TRTYPE=22` ("Online reversal") or `TRTYPE=24` ("Offline reversal"). Request fields and MAC
order are completion's.

- Both sections say "The following data must be posted **for a refund**"; the overview says "to complete and refund the
  payment should send below fields depending on trtype (trtype = 21 – checkout, trtype = 22 – online reversal, trtype =
  24 – offline reversal)", and the gateway "requests a reversal of **the pending or completed** transaction".
- Unpublished: which of the two applies before or after settlement, partial reversal, the unreversed remainder,
  idempotency.
- Simulator: `22` and `24` behave identically. A reversal must name the whole reversible amount (the completed amount,
  or the whole hold when nothing was completed). Against an uncompleted hold it releases the hold; against completed
  money it returns it. After any reversal a completion is refused (`RC=-3`).
- A hold that completed nothing reports `refundsAccepted` `true` with `refundableRemainder` zero; the release is
  reported by `capturableRemainder` (`README.md`, "State response rules").

### Card Storage and Stored-Card Payment

`POST /token/cgi_link` ("6 Card storage", "7 Recurring"), additive to the e-commerce request: "in addition to the
specified parameters, the parameter mentioned below is posted to the corresponding url address".

| Flow                          | Section | Additions                                                                                     | Simulator                                             |
|-------------------------------|---------|-----------------------------------------------------------------------------------------------|-------------------------------------------------------|
| card save                     | 6.1     | `TOKEN_ACTION=REGISTER`, `MERCH_TRAN_STATE=S`                                                 | served                                                |
| payment by stored card        | 6.2     | `TOKEN`, no `TOKEN_ACTION`                                                                    | served                                                |
| CIT payment                   | 6.3     | `MERCH_TRAN_STATE=C`                                                                          | refused with the HTML error page                      |
| MIT unscheduled card save     | 7.1     | `MERCH_TRAN_STATE=S`, `TOKEN_ACTION=REGISTER`, `MERCH_RN_ID`, `MIT_AGREEMENT=N`, `TRTYPE=1`   | served as a card save that returns `EXT_NET_REF`      |
| MIT unscheduled payment       | 7.2     | `MERCH_TRAN_STATE=M`, `EXT_NET_REF`, `MERCH_RN_ID`, `TOKEN`, `TRTYPE=1`                       | served as a stored-card payment; see below            |
| MIT scheduled card save       | 7.3     | as 7.1 with `MIT_AGREEMENT=Y`, `RECUR_FREQ`, `RECUR_EXP`                                      | as 7.1; `RECUR_FREQ` and `RECUR_EXP` are not read     |
| MIT scheduled payment         | 7.4     | `EXT_NET_REF`, `TOKEN`, and a row `TRTYPE \| 3 \| 185` with no description                      | not served                                            |

| Rule                                                                                                        | Evidence            |
|-------------------------------------------------------------------------------------------------------------|---------------------|
| a card save needs `TOKEN_ACTION=REGISTER` with `MERCH_TRAN_STATE=S`                                          | published rule      |
| a stored-card payment needs `TOKEN` and no `MERCH_TRAN_STATE`; an MIT payment needs `TOKEN`, `MERCH_TRAN_STATE=M`, `TRTYPE=1` and `MERCH_RN_ID` | published rule; a missing one is answered with the HTML error page naming it (simulator decision) |
| a card save carrying `MIT_AGREEMENT` (`N` or `Y`) needs `MERCH_RN_ID`, and its callback carries `EXT_NET_REF`, 20 uppercase hex characters stored with the card | published rule; the value's shape is a simulator decision |
| an MIT payment on a card stored with `EXT_NET_REF` must send the same `EXT_NET_REF`, or it is refused `ACTION=3`, `RC=-4` | simulator decision: "If the EXT_NET_REF value is not returned ... there is no need to send the parameter during payment" |
| `MERCH_RN_ID` is required where the tables list it and its value is not checked                              | simulator decision: size 16 and "8–32 random bytes" cannot both hold |
| every approved or declined authorization answered at once on `/token/cgi_link` (§2.3 card fields, `TOKEN`, MIT) queues the `BACKREF` callback in the transaction that stores the payment. When the callback has no target, the authorization is answered as usual and the callback delivery is `UNDELIVERABLE` with its reason. A `BACKREF` that is not a target of the terminal's account is kept as the delivery's target, withheld at each attempt with a not-sent row on the audit trail, and reached by retrying once it is configured, or by a retry naming a target configured for that account. With no `BACKREF` and not exactly one target for the account, the delivery has no target at all and a not-sent row with no path is on the audit trail; attempting it is refused (`409` on the control API, a refusal on the screen), and `POST /__simulator/azericard/webhooks` re-sends the payment's callback once a single target is configured. A retry naming a target that is not configured for the terminal's account is refused: nothing is posted, the delivery keeps its state, schedule and attempts, and a not-sent row records the withheld attempt. A card save without card fields is refused at hand-off instead (see "Payer Page") | inference from "6.1" and "6.2" ("After the payment is completed, the following parameters will be sent to the callback address"); authorization is not tied to the callback there, and `BACKREF` is no field of "6.2" or "7.2"; a §2.3 request on `/cgi-bin/cgi_link` queues none, as nothing there says so |

"To save the card, the merchant must make **a successful payment, even if it is a minimal amount**." No amount is
published.

Card-storage callback (published example, "6.1", "6.2", "7.1", "7.3"):

```
"AMOUNT"="xx", "CURRENCY"="AZN", "ORDER"="xxxxxxxxxxxx", "ACTION"="xxxxx", "RC"="xxx",
"APPROVAL"="xxx", "RRN"="xxx", "INT_REF"="xxxxxxxx", "EMAIL"="xxxxxxxx", "CARD"="xxxxxxxx",
"TOKEN"="xxxxxxxx", "TERMINAL"="xxxxxxxxx", "TRTYPE"="x", "TIMESTAMP"="xxxxxxxxxxxxxx",
"NONCE"="xxxxxxxxxxxxxxx", "P_SIGN"="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

with `"EXT_NET_REF"="xxxxxxxxxxxxxxxxxxxxx"` appended on the recurring card-save flows. The placeholder widths of
`ACTION` (5) and `RC` (3) contradict the tables (`1`, `02`); the tables govern.

### Payer Page

What is published and observed about the browser flow:

| Statement                                                                                                                                                                         | Evidence                         |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| the interface uses "the standard CGI/WWW forms posting method"                                                                                                                     | published rule ("1 Overview")    |
| MAC is needed "for at least the transactions passed through cardholder browser redirects"; completion and reversal are "sent directly to e-Commerce Gateway"                      | published rule ("P-Sign generation") |
| diagram "1.1": order generation, request for payment page, payment page generation, payment page redirection, "Customer fills payment detail", payment authorization, and a separate "Call back service" to the merchant | published rule |
| "TRTYPE=0 should be sent in the first POST request for preauthorization, and TRTYPE=21 should be sent to confirm the order after successful return information"                    | published rule ("2")             |
| the sandbox form `https://testsite.3dsecure.az/sandbox/auth.php` posts `TERMINAL`, `AMOUNT`, `CURRENCY`, `ORDER`, `DESC`, `MERCH_NAME`, `MERCH_URL`, `EMAIL`, `TRTYPE`, `COUNTRY`, `MERCH_GMT`, `TIMESTAMP`, `NONCE`, `BACKREF`, `NAME`, `M_INFO`, `NOTIFY_URL` and no card field | published example |
| the §2.3 S2S request is the only one carrying card fields, and every published synchronous authorization answer is §2.3's                                                           | published rule and example       |
| a `TRTYPE=0/1` or card-save request without card fields travels through the payer's browser to a hosted card page                                                                   | inference from the five rows above |
| card save: the merchant's server hands the signed fields and the static `/token/cgi_link` URL to its app; the app's webview posts them to the gateway; a card form (number, expiry, CVC, cancel) opens; after it a page says the payment succeeded or failed and offers a button back to the merchant; the result reaches the merchant's server as a callback carrying `TOKEN`; the saved card is later charged server-to-server with `MERCH_TRAN_STATE=M` | live probe, 2026-09-29 |
| the hosted page's fields and markup, where the browser goes afterwards, and whether `BACKREF` is posted by the gateway or by the browser                                            | unpublished                      |
| section 11's page ("redirected by clicking "back" button or after 60 seconds") belongs to the money-transfer product on `testmt.azericard.com`                                      | published rule; not applied here |

What the simulator does:

| Behaviour                                                                                                                                                                         | Reason                                                     |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| a `TRTYPE=0/1` request without `CARD` on `/cgi-bin/cgi_link`, and a card save without `CARD` on `/token/cgi_link`, are answered `200 text/html; charset=utf-8` with the card page; a request carrying `CARD` is answered at once with XML | the §2.3 card fields separate the S2S variant from the browser variant |
| a stored-card or MIT payment (`TOKEN`) is always answered at once                                                                                                                  | it names its card                                          |
| every refusal comes first: missing field page, `P_SIGN` and `TIMESTAMP` (`-17`), a terminal no account holds, a callback with no configured target (the missing-field page naming `BACKREF`), an `ORDER` trace already claimed that day or held by an open page of the same terminal (`ACTION=1`) | simulator decision: a misconfiguration is reported before the payer types a card |
| an open page holds its `ORDER` trace until it reaches an outcome, in the same database row a payment claims it with; a §2.3 authorization for that trace is answered `ACTION=1`; the session and its trace hold are written in one transaction | the trace is unique "within a day for the terminal id" (published rule) |
| the same signed form posted again while its page is open is served that page again; any other request for the same trace is answered `ACTION=1` | simulator decision: the page is `no-store`, so a webview retry, a double tap or a reload re-posts the form |
| an open page has no expiry | none is published; the only published bounds are the 1-hour `TIMESTAMP` skew, the 24-hour status window and the 1-year subscription |
| the page shows `MERCH_NAME`, `DESC`, `AMOUNT` with `CURRENCY` and `ORDER`, and asks for the card number, the expiry as `MM/YY` and the CVC, with **Pay** and **Cancel payment**; it posts to `POST /azericard/payer` with a hidden 32-character hex session id | simulator decision; the webview is assumed to post the form, as "forms posting" describes |
| the session is held server-side with the signed request minus any card field and `M_INFO`, and names its test run; the submission needs no test-run header                           | simulator decision: nothing the page authorizes, signs or calls back with reads `M_INFO` |
| the submission authorizes through the same code, state, ledger and audit as the S2S request, under the card rule of "Test Data"; **Cancel payment** is declined `ACTION=2`, `RC=ZZ` and records the payer outcome `ABANDONED` | simulator decision                                         |
| the `BACKREF` callback of "Merchant Callback" is queued, with `TOKEN` for a card save, `EXT_NET_REF` for an MIT card save and the masked `CARD`; if its target was removed after the page opened, the page answers `422`, changes nothing and stays open | live probe for the server-side callback; the refusal is a simulator decision |
| the authorization, the page's outcome and the queued callback are written in one transaction; a failure in any of them leaves no payment and the page open and payable | simulator decision                                         |
| the result page reads **Payment successful** or **Payment failed** with the amount, currency, order, `RRN` and merchant name, and a **Return to the merchant** link to `MERCH_URL` (`http` or `https` only) | live probe for the page; `MERCH_URL` behind the link is a simulator decision |
| submitting a page again shows the outcome it reached and authorizes nothing; a submission arriving while another is being authorized is answered `409` and authorizes nothing; a submission for a page reset away meanwhile is answered `404` | simulator decision                                         |
| a reset deletes a page and, by the foreign key's `on delete cascade`, its trace hold in one statement, so a page handed off while a reset runs is either cleared with its hold or survives it with its hold; a submission being settled when a reset starts finishes first, and the reset then clears what it wrote | simulator decision: the page row is locked before its trace row, the order a submission takes them in |
| a submission holds its page with a database row lock, which the database releases when that transaction commits, rolls back or loses its connection | simulator decision: no timeout decides that a holder is dead |
| every page answer carries `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'` | simulator decision: the page takes a card, and nothing publishes it as embeddable |
| a scenario rule answering in place of the authorization is delivered to the browser, or refuses a control-API completion with `422`; the page stays open, and the rule's story advances as it would for any request | simulator decision: a fault-then-success story reaches its success on the next submission |
| the open page is an object in `NOT_INITIALIZED`; the control API and the screens finish it by naming a published card as `azericard-2601`, `azericard-0780` or `azericard-0724`  | simulator decision                                         |

### Transaction Status

`POST /cgi-bin/cgi_link`, `TRTYPE=90` ("8.1").

- Request fields: `TRAN_TRTYPE` (1-2), `ORDER` (6-20), `TERMINAL` (8), `TRTYPE=90`, `TIMESTAMP` (14), `NONCE` (1-64),
  `P_SIGN` (1-256).
- Window: "Merchant can send transaction status request to E-commerce gateway **during 24h from transaction request
  time**."
- Response, published only as labels ("8.2"): `ACTION`, "Response code", "Transaction Status message", `TERMINAL`,
  "Card number", "Transaction amount", "Transaction currency", "Transaction date", "Transaction state", "Merchant order
  id", "Banks approval code", "Transaction RRN", `INT_REF`, "Original transaction TRTYPE", "Timestamp", "Nonce",
  `P_SIGN`. **No concrete status response is published.**

Observed refusal (live probe):

```json
{
  "terminal": "77777777",
  "actionCode": "3",
  "responseCode": "-17",
  "statusMsg": "Access denied",
  "tr_type": "1",
  "nonce": "2c9434b2aa5bb4af",
  "signature": "5EC7B408...",
  "timestamp": "20260910115344"
}
```

This settles key spelling, content type and signing; `tr_type` carries the request's `TRAN_TRTYPE`. It settles nothing
about a success. **Simulator decision:** a success adds `amount`, `currency`, `transactionDate`, `order`, `approval`,
`rrn` and `intRef`, key names of its own.

| Inquiry                                                         | Answer                                                                                           |
|-----------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| signed, `ORDER` unknown                                         | `responseCode` `-24`, `statusMsg` "Transaction context mismatch" (live probe); `actionCode` `3` is a simulator decision (not recorded by the probe) |
| `ORDER` known, only transactions older than 24 hours            | `-5` (simulator decision)                                                                        |
| `ORDER` names more than one transaction inside the window       | `-6` (simulator decision)                                                                        |

## Merchant Callback

- The fields are published as quoted `"FIELD"="value"` pairs (published example). `POST`, `application/x-www-form-urlencoded`
  and server-to-server delivery are unpublished: simulator decision, matching a live probe (2026-09-29) in which the result
  reached the merchant's server.
- Signed by the gateway over `AMOUNT`, `TERMINAL`, `APPROVAL`, `RRN`, `INT_REF`, verified with the Azericard/MPI public
  key after `hex2bin` (published rule, stated three times). Empty fields contribute `-` with no length.
- Unpublished: acknowledgement rule, retry schedule, attempt cap, handling of non-2xx or timeout. **Simulator
  decision:** any `2xx` acknowledges; anything else, or no answer, is a refusal (`AzericardCallbackAcknowledgement.java`).
- Stored-card requests carry no callback URL of their own; the target is `BACKREF` or simulator configuration (inference).
- `CARD` is sent as twelve `*` and the card's last four digits when the payment's card was entered on the payer page or
  sent in the §2.3 fields (simulator decision: the published sample shows only `"CARD"="xxxxxxxx"`).
- `azericard.callback-signature`: `VALID`, `CORRUPTED` or `ABSENT` (the last two are the simulator's own).

## Status and Decision Mapping

Vendor reading of the pair:

- `ACTION=0`, `RC=00`: success (`<action>0</action><rc>00</rc><desc>Approved</desc>`),
- `ACTION=2`: declined, `RC` the issuer's field-39 code,
- `ACTION=3`: processing error, terminal; `RC` may be negative; correlation fields may be empty,
- `ACTION=1`: duplicate transaction detected,
- `ACTION=6`, `7`, `8`: repeats, defined by what is repeated, not by the repeat's outcome,
- no pending or processing value.

Normalized state (`README.md` vocabulary):

| Condition                                                                 | State                                            |
|---------------------------------------------------------------------------|--------------------------------------------------|
| awaiting the hosted form                                                  | `NOT_INITIALIZED`: the open payer page is an object of its own |

| Condition                                                                 | State                                            |
|---------------------------------------------------------------------------|--------------------------------------------------|
| `TRTYPE=0`, `ACTION=0`                                                    | `READY_FOR_CAPTURE`                              |
| `TRTYPE=1`, `ACTION=0`                                                    | `SUCCEEDED`                                      |
| `TRTYPE=21` closing the authorization                                     | `SUCCEEDED`                                      |
| `TRTYPE=21` below the hold with the remainder left capturable             | `PARTIALLY_CAPTURED`                             |
| card storage with `ACTION=0` and a `TOKEN`                                | `SUCCEEDED`                                      |
| `ACTION=2`                                                                | `FAILED`; no `retryWithAnotherSource` for any code |
| `ACTION=3`, attributable failure (authentication, rejected message)       | `FAILED`                                         |
| `ACTION=3` with correlation fields empty against an operation already begun | `FAILED`                                      |
| `ACTION=1`, `6`, `7`, `8`                                                 | no stored payment: an `ORDER` trace collision is answered on the wire, and `6`, `7` and `8` are emitted by nothing |
| signature failure, correlation mismatch, unparseable body, status after 24 hours | no stored payment: each is answered on the wire |

A payment this gateway stores is `FAILED` unless its `ACTION` is `0` (`AzericardPayment.wasApproved`,
`AzericardPayment.normalizedStatus`), so no object of this provider normalizes to `UNKNOWN`.

States this contract cannot produce:

- **`PROCESSING`**: no pending outcome is published for this gateway.
- **`CANCELLED` versus `REFUNDED`** cannot be decided from `TRTYPE`; the vendor calls both a refund. **Simulator
  decision:** a reversal of a hold that completed nothing normalizes to `CANCELLED`, a reversal of completed money to
  `REFUNDED` (`AzericardPayment.normalizedStatus`).

- **`PARTIALLY_REFUNDED`** needs partial reversal, which nothing published permits or forbids.
- An expired registration or authorization has no state; no expiry is published.
- "Registration created" and "hosted form presented" both fall under `NOT_INITIALIZED`.

## Error and Fault Scenarios

Observed shapes (live probe):

| Fault                                            | Answer                                                                                                  |
|--------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| bad `P_SIGN`                                     | `HTTP 200`, normal shape, `ACTION=3`, `RC=-17`, `desc=Access denied`, **signed**                        |
| missing required field                           | `HTTP 200`, `text/html` human page, no `ACTION`, `RC` or signature                                      |
| stale `TIMESTAMP` with a bad signature           | answered as the signature failure; a stale timestamp alone was not observed                             |
| signed `TRTYPE=90` for an unknown `ORDER`        | `HTTP 200`, status JSON, `-24`, "Transaction context mismatch", signed                                  |

Scenarios the simulator serves:

- invalid merchant signature (`-17`), missing required field (HTML page), `TIMESTAMP` outside the one-hour window
  (`-17`, simulator decision),
- `ORDER` trace collision (`ACTION=1`), unknown or unusable `TOKEN` (`-4`),
- issuer decline `ACTION=2` with a scenario `RC`; gateway error `ACTION=3` with empty `APPROVAL`, `RRN`, `INT_REF`,
- callback with an invalid or missing `P_SIGN` (`azericard.callback-signature`), and a callback of a declined payment
  with an empty `APPROVAL`,
- status inquiry for an unknown `ORDER` (`-24`) and after the 24-hour window (`-5`),
- `5xx`, dropped connection, delay, truncated or corrupted body (core transport faults),
- XML served as `text/html`.

Scenarios the simulator does not serve:

- `ACTION=6`, `7`, `8` repeat outcomes: `AzericardOutcomes` declares `0`, `1`, `2` and `3`, and no constant and no
  scenario emits another,
- a callback with duplicate parameters, or with `AMOUNT`, `TERMINAL`, `ORDER`, `CURRENCY` or `TRTYPE` mismatched
  against the request: every field of a callback is read off the stored payment (`AzericardCallback.of`).

## Test Data

Published test cards ("4.1 Test card"):

| PAN                | EXP_MONTH | EXP_YEAR | CVV   | SMS OTP |
|--------------------|-----------|----------|-------|---------|
| `4127208104942601` | `05`      | `31`     | `796` | `1111`  |
| `5167513332880780` | `05`      | `31`     | `609` | `1111`  |
| `4127214121630724` | `04`      | `31`     | `536` | `1111`  |

The third is "Tam Card Visa (For ABB Merchants)", on the Azerbaijani page "Tam Card Visa (ABB müştərilərinə)".

No per-card outcome is published. Section 4 says one can "simulate results (approved, declined)" and names no
mechanism. Simulator decision, on the payer page and in the §2.3 fields alike: the three published cards are authorized
through the scenario rules; any other number, an expiry before the current month of the run's clock, a malformed
expiry, or a CVC that is not three digits (where a CVC is expected, see `CVC2_RC`) is declined `ACTION=2`, `RC=ZZ`.
No SMS OTP step is simulated.

Published test terminals ("4.2 Test terminal"): `17205353`, `17205354` (paired with the ABB card), `17205355`.

Published keys: "4.2.1" prints a private key ("must be used to perform test operations"), "4.3 Callback Verification"
prints a public key ("must be used to verify the authenticity and integrity of the callback"). Both are 2048-bit,
`e = 65537`, different moduli: **not a pair**. The private key is the merchant's, the public key is the gateway's.

Live probes, 2026-09-25:

- a `TRTYPE=90` for an unknown `ORDER`, signed with the published private key, is answered `-24` on all three published
  terminals; the same request signed with a local key is answered `-17`. The published key is enrolled on those
  terminals,
- `77777777` (curl samples), `99999999` and `17202191` (the "8.3" example) answer `-17` even under the published key;
  they are illustrative. The "8.3" example's `P_SIGN` is reproduced by neither published key,
- the published public key verifies the `TRTYPE=90` response signature: the recovered block is well-formed PKCS#1 v1.5,
  leading bytes `00 01 ff`, then the SHA-256 DigestInfo prefix `3031300d060960864801650304020105000420`. That
  it verifies a callback is published ("4.3"), not probed.

"MPI" is the gateway itself (host `testmpi.3dsecure.az`; "MPI will send Authorization Response"). Unpublished: whether
the test public key serves all three terminals, and whether the `MPI Public Key` and `AZERICARDpublic.pem` are one file
("9" and "10" still say "File will be provided during the test").

## Seeded Personas

One persona per intent, each seeding one stored card (`AzericardPersonas`). Core rules are in `README.md`, "Seeded
Personas".

| Persona                                                 | Intent                                      | Writes                            |
|---------------------------------------------------------|---------------------------------------------|-----------------------------------|
| `azericard-stored-card-that-succeeds`                   | `a-saved-card-that-succeeds`                | `preauth-success-capture-success` |
| `azericard-stored-card-that-is-declined`                | `a-saved-card-that-is-declined`             | `preauth-success-capture-decline` |
| `azericard-stored-card-whose-completion-answer-is-lost` | `a-saved-card-whose-capture-answer-is-lost` | `capture-accepted-response-lost`  |

| What it seeds           | Value                                                                                                                     |
|-------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `alias`                 | the persona's own name                                                                                                    |
| `ownerId`               | `a-cardholder-azericard-holds-a-token-for` (`AzericardPersonas.THE_OWNER`)                                                 |
| `type`                  | `card`                                                                                                                    |
| `providerIds.token`     | minted by `AzericardStoredCard.aToken`, the same minting a card-storing authorization uses: 28 uppercase hexadecimal characters, no prefix, the width a stored-card payment sends under `TOKEN` (`AzericardPersonas.HOW_A_TOKEN_IS_MINTED`) |
| `last4`                 | `2601`, from the first published test card of "Test Data", whose `EXP_MONTH` and `EXP_YEAR` are `05` and `31`              |
| `expMonth` / `expYear`  | `5` / `2031`                                                                                                              |
| `brand`, `funding`      | `unpublished`: the published table carries a PAN, an expiry, a CVV and an SMS OTP, and names a brand for one other row only |

- Seed it into an account this run configures for Azericard: a stored card belongs to the `TERMINAL` that stored it,
  and a card with no terminal could never be charged (`AzericardPersonas.AN_INSTRUMENT_BELONGS_TO_A_TERMINAL`).
- Each persona mints a `TOKEN` of its own, so seeding one does not claim the id another needs.

## Open Items

| Gap                                                                                             | Simulator choice                                          |
|-------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| token-route MAC orders (card save, CIT, `TRTYPE=0`) unpublished                                 | `TRTYPE=0/1` order                                        |
| synchronous response `P_SIGN` order unpublished                                                 | callback order                                            |
| every `RC` other than `00`, `-19`, `-17`, `-24`                                                 | invented, see "ACTION and RC"                             |
| online/offline reversal semantics                                                               | identical behaviour                                       |
| successful status response shape                                                               | simulator's own keys                                      |
| `desc` published only for `Approved`, `Access denied`, `Authentication failed`                  | empty `desc` elsewhere                                    |
| error-page markup unpublished; wording known for `TERMINAL` only                                | simulator's own markup and per-field phrasing             |
| only three time bounds published: 1-hour `TIMESTAMP` skew, 24-hour status window, 1-year subscription | every other expiry is a knob                         |
| no response field list for `TRTYPE=21/22/24`                                                    | the authorization's fields                                |
| completion and reversal not ordered against each other                                          | completion refused after any reversal                     |
| `ACTION=1` not tied to a rule                                                                   | trace collision answers `ACTION=1`                        |
| no MAC calculator for the token endpoint                                                        | —                                                         |
| no readable registration state before its callback                                              | `NOT_INITIALIZED`                                         |
| completion `CURRENCY` has no published relation to the payment                                  | must equal the payment's                                  |
| no customer identifier on any route (`NAME` is the cardholder's name)                          | —                                                         |
| payer page fields, markup and the browser's destination after it are unpublished                | see "Payer Page"                                          |
| `NOTIFY_URL` appears only in the sandbox form                                                   | accepted, not read                                        |
| section 4 publishes test terminals while "2.2" says the terminal "will be presented during the test" | recorded, unresolved                                 |
| no answer published for an address it does not serve or a method one of its addresses does not answer | `text/plain` of the simulator's own, never the HTML page: that page is HTTP 200 and names a request field |
| the vendors' tables disagree: `ORDER` 6-32/6-20, `INT_REF` 1-32/1-128, `RRN` 12/1, `MERCH_RN_ID` 16/8-32 bytes, `TERMINAL` 8/7-char example, `CURRENCY` alpha/`944`, `MERCH_GMT`/`MERCHANT_GMT` | the simulator picks one |
| no successful operation observed, with or without the published credentials                     | —                                                         |
