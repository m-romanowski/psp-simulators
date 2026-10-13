# Quickstart: one payment, end to end

Two payments, Grow and Azericard, first over the calls a harness makes and then over the screens a tester clicks. Every
call below worked against a running simulator. `<processId>`, `<transactionId>`, `<transactionToken>`, `<link>` and
`<INT_REF>` stand for values the simulator drew. Integration tests walk the same journeys.

## Before the first call

Start the application and its database with one `docker compose up --wait`, which returns once
`GET http://localhost:8080/actuator/health/readiness` answers `UP`. The root `README.md` covers running it from a JDK
and against a database it does not own.

Pick a test-run id and use it everywhere. Nothing creates a run: it exists once a configuration call names it. Every read
answers `200` with an empty array for a run nothing configured, so an empty answer does not tell you that you misspelled
the id.

## Grow, from nothing to a settled hold: seven calls

Two control calls set up the run. Three are calls a merchant's server makes, and two are calls a payer's browser makes.

### 1. Configure the account and the callback target

```sh
curl -s -X PUT http://localhost:8080/__simulator/grow/config \
  -H 'Content-Type: application/json' \
  -d '{"testRunId":"journey-grow",
       "clock":{"fixedNow":"2026-09-15T10:00:00Z"},
       "accounts":[{"accountId":"merchant-1","configuration":{
         "userId":"journey-grow-user","pageCode":"journey-grow-page",
         "callbackAcknowledgement":"APPROVE_TRANSACTION"}}],
       "webhooks":[{"accountId":"merchant-1","url":"http://127.0.0.1:9100/callbacks"}]}'
```

- Answers `200` with `configuredAccounts`, `configuredWebhookTargets` and `updatedAt`, which is the pinned clock.
- Pin the clock. Grow has same-day rules, and the callback renders a `paymentDate`.
- `callbackAcknowledgement: APPROVE_TRANSACTION` makes step 6 matter. Under the default, `HTTP_2XX`, the merchant's `200`
  closes the delivery.
- Grow reads eleven configuration keys, `wireForm` among them. Send an unknown key and the refusal prints the whole
  set.

### 2. Write the scenario rule

```sh
curl -s -X POST http://localhost:8080/__simulator/grow/scenarios \
  -H 'Content-Type: application/json' \
  -d '{"testRunId":"journey-grow","priority":100,
       "match":{"amount":"120.50"},
       "action":{"type":"FIXED_SUCCESS"}}'
```

- Answers `201` with a `scn_...` id. The audit row of every exchange the rule decided carries that id.
- Example rules and the action vocabulary are in `README.md`, "Common Simulator Control API". A refusal also prints the
  action vocabulary.
- A rule with no `match` matches everything. If a narrow rule loses its `match` in an edit, it widens and does not fail.

### 3. Create the hosted payment process

```sh
curl -s -X POST http://localhost:8080/api/light/server/1.0/createPaymentProcess \
  -F pageCode=journey-grow-page -F userId=journey-grow-user \
  -F chargeType=2 -F sum=120.50 -F description='Journey hold' -F saveCardToken=1 \
  -F successUrl=https://merchant.example/thanks \
  -F cancelUrl=https://merchant.example/cancel \
  -F notifyUrl=http://127.0.0.1:9100/callbacks \
  -F 'pageField[fullName]=Israel Israeli' -F 'pageField[phone]=0505503144' \
  -F cField1=journey-session -F cField2=journey-intent
```

Answers `{"status":1,"err":"","data":{"processId":...,"processToken":...,"url":...}}`. Keep all three values.
`processToken` is the only way to read the process again over the API.

- **A typed HTTP client fails on this step.** Every Grow API answer is JSON under
  `Content-Type: text/html; charset=UTF-8`, as on the live wire. Clients that pick a converter by content type, such as
  Spring's `RestClient`, refuse the answer. Either read the body as text, or set the account's `wireForm` to
  `PUBLISHED_EXAMPLE`, which answers `application/json`. See `grow.md`, "Authentication and Transport".
- **`chargeType` decides what the payment becomes.** `2` makes a hold that `settleSuspendedTransaction` settles. `3`
  saves a card that `createTransactionWithToken` charges. `cField2` does not decide this. It is what a merchant reads on
  the callback to tell the two flows apart. See `grow.md`, "The Hosted Page, and What Completing It Produces".
- The `url` is `/far?l=<link>` on `simulator.public-base-url` (default `http://localhost:8080`). If the simulator listens
  elsewhere, keep the `l=` value and build the URL around it. A link stops working when its run is reset.

### 4. Open the page the payer was sent to

```sh
curl -s -i 'http://localhost:8080/far?l=<link>'
```

- Answers `200`, HTML under `text/html; charset=utf-8`. The page shows the `description`, the `sum` and
  `pageField[fullName]`. It has a `<select name="card">` and one submit button for each outcome: `PAID`, `DECLINED` and
  `ABANDONED`.
- Cards are named by alias, never by number. Every run offers `sandbox-4580`, `sandbox-0000` and `sandbox-1121`, plus any
  card seeded through `POST /__simulator/grow/fixtures/payment-methods`, under that fixture's alias.
- The page is the simulator's invention. Grow publishes the URL and nothing behind it. Pin tests to what the completion
  produces, not to the page's markup.
- An unknown link answers `404`.

### 5. Pay it

```sh
curl -s -i -X POST http://localhost:8080/far \
  -d 'l=<link>' -d 'outcome=PAID' -d 'card=sandbox-4580'
```

| `outcome`   | answer                                                                                  | callback                                                                      |
|-------------|-----------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| `PAID`      | `303` to `successUrl?cField1=...&cField2=...`, carrying only the `cField` values         | form-encoded, `data[statusCode]=11`, `data[transactionId]`, `data[transactionToken]` |
| `DECLINED`  | `303` to `cancelUrl`                                                                    | failure envelope: `status=0`, an `err` object, empty `data`                   |
| `ABANDONED` | `303` to `cancelUrl`                                                                    | none. Reconcile with `getPaymentProcessInfo`                                  |

- The redirect carries no outcome. The callback carries it.
- `transactionId` and `transactionToken` arrive **only** on the callback. No control read returns a token.
- `card` is read only for `PAID`. If you name an alias the run does not offer, the `422` lists the aliases it does offer.
- A second post of the same link answers `422`. To send a duplicate callback, use the control route below.
- The `notifyUrl` must be one of the run's configured `webhooks` targets. If it is not, the post answers `422`, sends
  nothing and leaves the process completable: fix step 1, then post again. `ABANDONED` needs no target.

### 6. Acknowledge the callback

```sh
curl -s -X POST http://localhost:8080/api/light/server/1.0/approveTransaction \
  -F pageCode=journey-grow-page \
  -F transactionId='<transactionId>' -F transactionToken='<transactionToken>'
```

- Answers `{"status":1,"err":"","data":""}`.
- Before this call, `GET .../webhooks` shows the delivery `PENDING`, with its attempt `AWAITING_SEPARATE_CALL`. After it,
  the delivery is `DELIVERED`.
- `pageCode` is the only credential. Grow says this call does not change the transaction (`grow.md`, "Approve
  Transaction Callback").

### 7. Settle the hold

```sh
curl -s -X POST http://localhost:8080/api/light/server/1.0/settleSuspendedTransaction \
  -F userId=journey-grow-user \
  -F transactionId='<transactionId>' -F transactionToken='<transactionToken>' \
  -F sum=120.50
```

- Answers Grow's transaction record with `statusCode` `"2"` and `status` `שולם`. Every scalar is a JSON string.
- `userId` is the only credential. The amount rule is in `grow.md`, "Settle Suspended Transaction".
- Read `grow.md`, "The Hosted Page, and What Completing It Produces" before you assert on the card fields.

## Callbacks and completions a payer cannot cause

`POST /__simulator/grow/webhooks` names the **process**, not the transaction:

```sh
curl -s -X POST http://localhost:8080/__simulator/grow/webhooks \
  -H 'Content-Type: application/json' \
  -d '{"testRunId":"journey-grow","providerObjectId":"<processId>",
       "eventType":"payment_process.completed","reuseEventId":"<eventId>"}'
```

- `reuseEventId` sends a duplicate that is byte-identical to the first.
- `delayMs` schedules the attempt for later.
- `{"operation":"RETRY","webhookJobId":"..."}` makes one more attempt on an existing delivery.
- `eventType: payment_process.failed` on an unfinished process sends the failure callback without a payer.
- Naming a different outcome for a process that already has one answers `422`. An unknown id answers `404`. A process
  whose `notifyUrl` is not a configured target answers `422` and names the mismatch.

A harness with no browser can finish the page through `POST /__simulator/grow/hosted/<processId>/complete` with
`{"testRunId":"...","outcome":"PAID","card":"sandbox-4580"}`. This runs the same path as the page, and a finished process
is `422` either way. A payment the page made records `origin` `PROVIDER_API`. A payment a control route made records
`CONTROL_API`.

## Grow, reading it back: six reads

```sh
curl -s 'http://localhost:8080/__simulator/grow/objects?testRunId=journey-grow'
curl -s 'http://localhost:8080/__simulator/grow/state/<transactionId>?testRunId=journey-grow'
curl -s 'http://localhost:8080/__simulator/grow/webhooks?testRunId=journey-grow'
curl -s 'http://localhost:8080/__simulator/grow/audit?testRunId=journey-grow'
curl -s -X POST http://localhost:8080/api/light/server/1.0/getTransactionInfo \
  -F pageCode=journey-grow-page \
  -F transactionId='<transactionId>' -F transactionToken='<transactionToken>'
curl -s -X POST http://localhost:8080/api/light/server/1.0/getPaymentProcessInfo \
  -F pageCode=journey-grow-page -F processId='<processId>' -F processToken='<processToken>'
```

- The transaction's state: `nativeStatus` `2`, `normalizedStatus` `SUCCEEDED` and `origin` `PROVIDER_API`. It has two
  operations: an `AUTHORIZATION` of `120.50` with `closesAuthorization` `false`, and a `CAPTURE` of `120.50` with
  `closesAuthorization` `true`. Its balances are `capturedAmount` `120.50`, `capturableRemainder` `0` and
  `refundableRemainder` `120.50`, with `capturesAccepted` `false` and `refundsAccepted` `true`.
- **A completed Grow payment is two objects.** The list holds the transaction and the process. `state/<id>` accepts
  either id. Each object names the other under `relatedObjects`: a transaction has `processId`, and a process has
  `transactionId`. This is the only way to tell them apart. After settlement the process stays at `11`
  `READY_FOR_CAPTURE`, with no operations and zero balances, and both `...Accepted` flags `false`.
- `currency` is `null` on every Grow object, because Grow's API has no currency.
- An unknown id answers `404`. The message names the run it searched, which is `default` when the read named none.

## Azericard, from nothing to a completed sale: five calls

The requests are signed, the callback is signed back, and there is no acknowledgement call. This journey sends the card
in the S2S fields, so the gateway answers at once and a control call delivers the callback. The same request without
the card fields opens the payer page instead (`azericard.md`, "Payer Page").

Generate two RSA key pairs, one for the merchant and one for the gateway. The commands are in `azericard.md`,
"Authentication, Signing and Transport". Put the merchant's **public** key in `apiKey` and the gateway's **private** key
in `apiSecret`. Keep the gateway's public key to verify the callback.

### 1. Configure the terminal and both keys

```sh
curl -s -X PUT http://localhost:8080/__simulator/azericard/config \
  -H 'Content-Type: application/json' \
  -d '{"testRunId":"journey-azericard",
       "clock":{"fixedNow":"2026-09-15T10:00:00Z"},
       "accounts":[{"accountId":"merchant-1","configuration":{
         "terminalId":"77777777",
         "apiKey":"-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n",
         "apiSecret":"-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"}}],
       "webhooks":[{"accountId":"merchant-1","url":"http://127.0.0.1:9100/callbacks"}]}'
```

- Send each PEM as a JSON string with `\n` escapes. The audit shows `apiKey` in full and `apiSecret` as `[redacted]`.
- Pin the clock. A `TIMESTAMP` more than an hour from the run's clock is refused.

### 2. Write the scenario rule

The same as Grow's, matching `"amount":"25.00"` with `FIXED_SUCCESS`. The `match.operation` values are the provider's
own: `TRTYPE=0`, `TRTYPE=0 TOKEN_ACTION=REGISTER`, `TRTYPE=0 TOKEN`, `TRTYPE=1`, `TRTYPE=1 TOKEN_ACTION=REGISTER`,
`TRTYPE=1 TOKEN`, `TRTYPE=21`, `TRTYPE=22`, `TRTYPE=24` and `TRTYPE=90`. An unknown value is refused, and the refusal
prints this list.

### 3. Pre-authorize

`POST /cgi-bin/cgi_link`, `application/x-www-form-urlencoded`, with `TERMINAL`, `TRTYPE=0`, `ORDER`, `AMOUNT`,
`CURRENCY`, `TIMESTAMP`, `NONCE`, `BACKREF`, `MERCH_URL`, `P_SIGN` and a published test card in `CARD`, `EXP`,
`EXP_YEAR` and `CVC2` (`4127208104942601`, `05`, `31`, `796`). The card fields are not signed.

- `P_SIGN` signs the authorization field order from `azericard.md`, "Signature Field Order": `AMOUNT`, `CURRENCY`,
  `TERMINAL`, `TRTYPE`, `TIMESTAMP`, `NONCE`, `MERCH_URL`. Do not take the order from the request field table.
- Each value is prefixed with its decimal length. An absent field is written as `-`. Sign with `SHA256withRSA`, and send
  the hex in either case.
- Worked example: for `AMOUNT` `25.00`, `CURRENCY` `AZN`, `TERMINAL` `77777777`, `TRTYPE` `0`, `TIMESTAMP`
  `20260915100000`, `NONCE` `F2B2DD7E603A7ADA` and a 32-character `MERCH_URL`, the source begins
  `525.003AZN87777777710142026091510000016F2B2DD7E603A7ADA32http://...`.
- Answers HTTP `200`, XML under `Content-Type: text/html`. Read `<action>0</action>` and `<rc>00</rc>`. Keep `<intref>`,
  `<rrn>` and `<approval>`. `INT_REF` (spelled `<intref>` in the XML) addresses everything after this.

### 4. Deliver the authorization callback to `BACKREF`

```sh
curl -s -X POST http://localhost:8080/__simulator/azericard/webhooks \
  -H 'Content-Type: application/json' \
  -d '{"testRunId":"journey-azericard","providerObjectId":"<INT_REF>"}'
```

- **The authorization does not send the callback.** The merchant receives nothing until this call.
- Answers `201` with `status` `DELIVERED`, because the merchant's `200` closes the delivery.
- Verify the callback's `P_SIGN` with the gateway public key, over `AMOUNT`, `TERMINAL`, `APPROVAL`, `RRN` and `INT_REF`,
  using the same length rule.
- The callback's `NONCE` is fresh. Its `TIMESTAMP` is the request's.

### 5. Complete the sale

`POST /cgi-bin/cgi_link` with `TRTYPE=21`, the authorization's `ORDER`, `RRN` and `INT_REF`, the amount, `TIMESTAMP` and
`NONCE`. `P_SIGN` signs the **completion** order: `AMOUNT`, `CURRENCY`, `TERMINAL`, `TRTYPE`, `ORDER`, `RRN`, `INT_REF`.
The request carries `TIMESTAMP` and `NONCE`, but the MAC leaves them out. Answers the same XML with `<action>0</action>`
and `<trtype>21</trtype>`.

## Azericard, reading it back: five reads

`objects`, `state/<INT_REF>`, `webhooks` and `audit` read as Grow's do. The fifth read is the gateway's status inquiry:
`POST /cgi-bin/cgi_link` with `TRTYPE=90`, `TRAN_TRTYPE`, `ORDER`, `TERMINAL`, `TIMESTAMP`, `NONCE` and a `P_SIGN` over
`ORDER`, `TERMINAL`, `TRTYPE`, `TIMESTAMP`, `NONCE`. It finds the payment by `ORDER` and answers JSON under
`application/json`, with the keys `actionCode`, `responseCode`, `statusMsg`, `amount`, `currency`, `transactionDate`,
`order`, `approval`, `rrn`, `intRef`, `tr_type`, `nonce`, `signature` and `timestamp`. Seven of these keys are the
simulator's invention (`azericard.md`, "Transaction Status").

## The same two payments, from the screens

A tester can do both journeys at `http://localhost:8080/ui` without making any call. The screens never hand out a token
or a payer link, so a tester carries no drawn value from step to step. Every screen has provider and test-run fields at
the top, with a **Switch** button. Every link carries the run. The account form creates the run. With no account
configured, the start-a-payment wizard links to that form instead.

### Grow, without a call

1. **Configuration → Configure an account.** Enter `merchant-1`, `userId`, `pageCode`, and the callback target. Leave
   `callbackAcknowledgement` empty: no screen makes `approveTransaction`. A refused form comes back with the secret
   fields empty.
2. **Configuration → This test run's clock.** Set **fixed now** to `2026-09-15T10:00:00Z`. The clock belongs to the run,
   so it applies to every provider in it. A completion still makes its first delivery attempt at once.
3. **Start a payment.** Choose **Hold the money on the payer's card and settle it later**. Step 2 draws Grow's fields,
   and offers `notifyUrl` as a list of the configured targets. No credential is asked for: `userId`, `pageCode` and
   `chargeType` are filled in. The answer, **This payment exists now**, links to the payment.
4. **Payments.** One row: the process, at provider status `0`, `NOT_INITIALIZED`, **created by** `UI`.
5. **Open the row → Take the payer's own step on this payment.** Pick `PAID` with a card (shown as
   `Visa •••• 4580, 0424 (sandbox-4580)`), `DECLINED` or `ABANDONED`, tick **confirm**, and submit. This runs what
   `/hosted/<processId>/complete` runs. A card named with `DECLINED` or `ABANDONED` is refused.

   | outcome     | Payments list                                   | merchant receives            |
   |-------------|-------------------------------------------------|------------------------------|
   | `PAID`      | two rows, both `11` `READY_FOR_CAPTURE`         | `payment_process.completed`  |
   | `DECLINED`  | one row, `0` `FAILED`                           | `payment_process.failed`     |
   | `ABANDONED` | one row, `0` `NOT_INITIALIZED`                  | nothing                      |

   After an outcome, the form is replaced by the provider's own sentence.
6. **Open the transaction → Ask grow to capture or complete the hold.** The transaction is the newest row. Related ids
   are shown as text, not links. The amount is prefilled. Tick **confirm**. The row moves to `2` `SUCCEEDED`, capturable
   **no**, refundable **yes**. Grow's own `settleSuspendedTransaction` runs underneath.
7. **Read it back.** Webhooks shows the delivery `DELIVERED`, with one attempt, `ACKNOWLEDGED`. Audit shows one `UI` row
   for each screen action (`/ui/configuration/account`, `/ui/configuration/clock`, `/ui/objects/start`,
   `/ui/objects/complete`, `/ui/objects/act`), plus the callback attempt under `SCHEDULED_JOB`.

### Azericard, without a call

1. **Configuration → Configure an account.** Enter `terminalId`, `apiKey` and `apiSecret`, as over the calls, and the
   callback target. The form requires both PEMs even though nothing on this journey signs. Pinning the clock is
   optional: the wizard fills in `TIMESTAMP` itself.
2. **Start a payment.** Choose **Hold the funds on the card and complete the sale later** (`TRTYPE=0`), or **Take the
   funds in one call** (`TRTYPE=1`). Step 2 draws `ORDER`, `AMOUNT`, `CURRENCY`, and optionally `DESC` and `EMAIL`, with
   `BACKREF` as a list of the configured targets. The simulator holds only the merchant's public key, so **a payment
   started here never travels the signed wire**. The object is the same, and the trail records `UI`.
3. **Payments.** One row under the printed `INT_REF`: `0/00`, `READY_FOR_CAPTURE`, with the amount and `AZN`. It stays
   one row.
4. **Same screen → Send a callback about this payment.** This runs what `/__simulator/azericard/webhooks` runs, with no
   delay. The merchant's `200` makes it `DELIVERED`, and its `P_SIGN` verifies.
5. **Same screen → Ask azericard to capture or complete the hold**, then **confirm**. The row moves to `0/00`
   `SUCCEEDED`. A cancel button stands beside it.

## Where the two providers differ

| | Grow | Azericard |
|---|---|---|
| callback caused by | the payer's submission | a control call or screen panel |
| acknowledgement call | `approveTransaction` | none |
| `AUTHORIZATION.closesAuthorization` | `false` | `null` |
| attempt cap | `6` | `5` |
| delivery `eventType` | `payment_process.completed` | none |
| refusals on its API | HTTP `200`, numeric `err.id` | HTTP `200`, `action=3`. A missing required field gets an HTML page with no fields |
| objects after completion | two (process and transaction) | one |
| `currency` | `null`, shown as `none recorded` | set |
| cancel on the payment screen | no. Grow publishes no card void, only settle then refund, which ends `REFUNDED` | yes |
| `origin` | `PROVIDER_API` over calls, `UI` from screens | `PROVIDER_API` over calls, `UI` from screens |

Neither provider answers a non-`200` on its API routes. Grow's hosted page is the exception: it answers `303`, `404` and
`422`.

## What the screens do not do that the calls do

The screens cover what the API covers and no more (`README.md`, "The Screens a Tester Drives").

- **Acknowledge a Grow callback.** No screen offers `approveTransaction`. Under `APPROVE_TRANSACTION`, a delivery stays
  `PENDING`, every attempt is `AWAITING_SEPARATE_CALL`, and retries run until it is `EXHAUSTED`.
- **Provider reconciliation reads.** `getTransactionInfo`, `getPaymentProcessInfo` and `TRTYPE=90` are on no screen.
- **Shape a delivery from the payment screen.** It sends one callback now. A delay or a reused event id is composed on
  `/ui/webhooks`, and a drop is a rule on `/ui/scenarios`.
- **Four Hosted Sessions actions** from `technical-ui-requirements.md`, "Hosted Sessions": expire session, complete and
  save method, complete as hold, and fail with a provider error. Hold versus save is chosen when the payment is started.
  A decline is chosen by a scenario rule. An expiry comes from advancing the clock.
- **Tell two Grow rows apart.** Open one and read `relatedObjects`.

## What could not be done at all

- **Charge a Grow stored card without first completing a hosted process.** A Grow payment-method fixture takes no
  provider-native id, and `createTransactionWithToken` answers `140` to a seeded token. Get the `cardToken` from a
  completed `chargeType=3` process. The wizard offers this as **Save the payer's card and take no money at all**.
