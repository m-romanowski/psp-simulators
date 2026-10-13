# Autra Simulator Contract

## What This File Covers

The vendor contract for the Autra acquiring API, and what this simulator answers on each route: OAuth, hosted card
sessions, the acquiring session, direct sale with a tokenized card, pre-authorization with capture and void, payment
lookup, Pix checkout and acquiring webhooks. The acquirer behind Autra is Dock ("**Provedor adquirente:** Dock. Você não
fala com o adquirente — só com a API da Autra."), and several irregular shapes below are Dock's, passed through.

Each statement names its evidence class (`README.md`, "Evidence Classes"). **No statement rests on a live probe except
one** (see "Payment Initialize"): a tenant of either environment is released only "exclusivamente por meio do canal
oficial", "após validação do contexto da parceria", with an allow-listed IP, and there is no self-serve signup.

| Route                                                  | What the simulator does                                                                                     | Key deviations                                                                                                                                            |
|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /v1/oauth/token`                                 | Basic credential, `grant_type` query; camelCase body; `400 {"error": ...}` on failure                        | `autra.oauth-failure` and `autra.oauth-field-casing` reach the guide's `401 invalid_client` and snake_case                                                |
| `POST /v1/acquiring/card-sessions`                     | session with a 15-minute `expiresAt`; `hostedCardUrl` is this simulator's card page                         | `nickname` collected here; session statuses are the simulator's; `409 ACQUIRING_NOT_READY` and the CPF/CNPJ `422` unreachable |
| `GET`/`POST /tokenize/sessions/{hostedCardSessionId}`  | the hosted card page: the payer picks a card by alias and saves it, or leaves                               | token in the query, not the fragment; no card number typed; the result is printed on the page, not posted to an SDK                                       |
| `POST /v1/acquiring/payments/initialize`               | `{"transactionalToken"}`                                                                                    | takes the **merchant's** `documentId` (live probe), not the cardholder's; the token never expires and is not consumed                                     |
| `POST /v1/acquiring/payments`                          | the off-session sale; `CREDIT` or `DEBIT`; decline answer per `autra.sale-decline-status`                    | `tokenData` naming no slug refused `400`, naming no card of the run `422 TOKEN_NOT_AVAILABLE`; zero accepted, negative refused                            |
| `POST /v1/acquiring/payments/pre-authorization`        | `ACCEPTED` hold; `orderId` claimed before the acquirer                                                      | request shape per `autra.pre-authorization-request-shape`                                                                                                 |
| `POST /v1/acquiring/payments/{paymentId}/capture`      | `CAPTURED`; requires `transactionalToken` and `documentId` (inference)                                      | response body inferred                                                                                                                                    |
| `POST /v1/acquiring/payments/{paymentId}/void`         | releases a hold, reverses a sale or capture, in full or in part                                              | body per `autra.void-request-shape`; a hold is released whole only; never `REFUND_PENDING`                                                                |
| `GET /v1/acquiring/payments/{paymentId}`               | the payment and the timeline of its steps, read-only, from the wire or the **Look a payment up** screen     | `maskedCard` and `brandReferenceId` never answered; `TENANT_REQUIRED`, `403` and `500 PAYMENT_LOOKUP_ERROR` unreachable; `MERCHANT_NOT_FOUND` at `404` and not at `autra.merchant-not-found-status` |
| `GET /v1/acquiring/payments`                           | the establishment's payments in a date window, newest first, page-based, from the wire or the **Look a payment up** screen | `status` takes the seventeen and nothing else; a `to` the call does not name passes the present moment; `INVALID_DOCUMENT` is a shape check and not the CPF/CNPJ check digits |
| `POST /v1/acquiring/pix/initialize`                    | `{"code": "ACCEPTED", "transactionalToken"}`                                                                | `code` is a constant                                                                                                                                      |
| `POST /v1/acquiring/pix/checkout`                      | `PENDING` Pix, QR lives 5 minutes; completed through the payer step                                         | `qrCode` is not a valid BR Code; `403` code `UNAUTHORIZED` is an inference; `amount` must be above zero                                                   |
| acquiring webhooks                                     | `acquiring.financial_transaction` and `acquiring.payout` for Pix, published retry plan                      | unsigned without `webhookSecret`; signed once; no card webhooks                                                                                           |
| `POST /v1/acquiring/tokenize-card`                     | **not served**: its `200` has no schema                                                                     |                                                                                                                                                           |

Account settings that choose between vendor contradictions or supply what the vendor leaves open:

| Setting                                  | Values (default first)                                                   | Chooses between                                                             |
|------------------------------------------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| `autra.sale-decline-status`              | `TWO_HUNDRED_WITH_ERRORS`, `UNPROCESSABLE_WITH_ERRORS`                   | the declined `200` of the schema, the `422` of the prose                    |
| `autra.oauth-failure`                    | `BAD_REQUEST_WITH_ERROR_STRING` (`400`), `UNAUTHORIZED_INVALID_CLIENT` (`401`) | reference, guide                                                      |
| `autra.oauth-field-casing`               | `CAMEL_CASE`, `SNAKE_CASE`                                               | reference, guide                                                            |
| `autra.pre-authorization-request-shape`  | `PUBLISHED_SCHEMA`, `PUBLISHED_GUIDE_EXAMPLE`                            | six required fields, the guide's three                                     |
| `autra.void-request-shape`               | `PUBLISHED_SCHEMA`, `PUBLISHED_GUIDE_EXAMPLE`                            | the schema's body, the guide's bodyless call                                |
| `autra.merchant-not-found-status`        | `NOT_FOUND` (`404`), `FORBIDDEN` (`403`), `UNPROCESSABLE` (`422`)        | the three statuses `MERCHANT_NOT_FOUND` is published at                     |
| `autra.authorizer-rejected-message`      | `05 - Do not honor`; any `NN - text`                                     | the `msg` beside `AUTHORIZER_REJECTED`                                      |
| `autra.acquirer-transaction-identifier`  | `REPORTED`, `NOT_REPORTED`                                               | an acquirer that names the transaction, one that names none                  |
| `webhookSecret`                          | none; 32+ characters                                                     | the tenant HMAC secret                                                      |

## Sources

All fetched on 2026-09-11; the four acquiring response schemas read field by field on 2026-10-06. Section names are
Autra's, Portuguese where Autra writes them.

| Page                                   | URL (under `https://autra.readme.io/reference/`) | Sections used                                                                                                                                  |
|----------------------------------------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Acesso à API                           | `setup`                                           | "Parte 2 — Gerar token de acesso (a cada hora)", "Parte 3 — Usar o token em chamadas", "Resumo dos mecanismos de segurança", "Erros comuns"     |
| Introdução                             | `introdução`                                      | "Ambientes"                                                                                                                                    |
| Segurança                              | `segurança`                                       |                                                                                                                                                |
| Configuração                           | `configuração-1`                                  |                                                                                                                                                |
| Autra Acquiring                        | `acquiring`                                       | "Conceitos fundamentais", "Resumo dos endpoints", "Status do pagamento", "Idempotência — `orderId`", "Erros comuns", "Boas práticas", "FAQ"      |
| Generate access token                  | `post_v1-oauth-token`                             |                                                                                                                                                |
| Initialize acquiring session           | `post_v1-acquiring-payments-initialize`           |                                                                                                                                                |
| Create credit/debit card payment       | `post_v1-acquiring-payments`                      |                                                                                                                                                |
| Create pre-authorization               | `post_v1-acquiring-payments-pre-authorization`    |                                                                                                                                                |
| Capture pre-authorization              | `post_v1-acquiring-payments-paymentid-capture`    |                                                                                                                                                |
| Void payment (total ou parcial)        | `post_v1-acquiring-payments-paymentid-void`       |                                                                                                                                                |
| Get payment (status e histórico)       | `get_v1-acquiring-payments-paymentid`             |                                                                                                                                                |
| List payments (por estabelecimento)    | `get_v1-acquiring-payments`                       |                                                                                                                                                |
| Create hosted card session             | `createhostedcardsession`                         |                                                                                                                                                |
| Tokenize card                          | `post_v1-acquiring-tokenize-card`                 |                                                                                                                                                |
| React Native Card SDK                  | `react-native-card-sdk`                           | "How it works", "Quick start", "Props reference", "Callback data", "Security & PCI"                                                             |
| Initialize Pix session                 | `post_v1-acquiring-pix-initialize`                |                                                                                                                                                |
| Create Pix checkout                    | `post_v1-acquiring-pix-checkout`                  |                                                                                                                                                |
| Pix Checkout                           | `pix-checkout`                                    | "Conceitos fundamentais", "State machine", "Idempotência — `orderId`", "Erros comuns", "Limitações conhecidas"                                  |
| Outbound Webhooks                      | `outbound-webhooks`                               | "Domínios suportados", "Formato do payload (envelope Autra)", "Headers enviados", "Validação do `X-Autra-Signature` (HMAC-SHA256)", "Política de entrega + retry", "Como cadastrar a subscription" |
| Checkout Embed SDK                     | `checkout`                                        | "Callback data"                                                                                                                                |

The reference pages come from one OpenAPI document, `Autra Platform API` `1.5.0`, at
https://dash.readme.com/api/v1/api-registry/1tzdu6xmtm6h0qr ("published schema"). The prose pages are the "published
guide". Autra indexes itself at `llms.txt` and serves each page as markdown with `.md` appended; the pages are not
publicly indexed by search engines.

Published but out of scope: banking, cards, payment links, boleto, Pix Automático, split, 3-D Secure, terminals, fee
simulation, merchant listing. `paymentSplit` is an optional field of the sale this simulator does serve.

## Authentication and Transport

- Base URL `https://api.autra.io` ("Resumo dos endpoints"; the schema's single "Production" server). Hosted card page
  origin `https://cards.autra.io` ("Quick start"). Served here under `simulator.public-base-url` (see "Hosted Card
  Page").
- OAuth: `POST /v1/oauth/token?grant_type=client_credentials`; `grant_type` is a required **query** parameter, "Único
  valor aceito é `client_credentials`". Credentials: "`Authorization: Basic <base64(clientId:clientSecret)>`".
- Every other call: `Authorization: Bearer <JWT>`, `Content-Type: application/json`. Schema: `bearer`, `bearerFormat:
  JWT`.
- Bearer validity: "A validade padrão é de 1 hora. Recomenda-se cachear o token e renová-lo apenas próximo da expiração."
  "Não há refresh token — é sempre uma nova chamada de `/oauth/token`." Simulator: one hour, fixed.
- `transactionalToken`: "gerado por `/initialize` e usado nas chamadas de pagamento. Válido por poucos minutos." "Posso
  reaproveitar o `transactionalToken` em várias vendas? Não. Gere um novo a cada venda." No numeric TTL.
- IP allow-list: most acquiring operations declare `403` "Token inválido ou IP não autorizado" (initialize,
  pre-authorization, capture, void, get payment, Pix checkout, tokenize card); card sessions say "Tenant não autenticado
  ou IP fora da allow-list". A client cannot attribute such a `403`.
- `401` is declared on one operation only: the sale, "Token de acesso inválido", beside `403` "IP não autorizado ou
  merchant não encontrado". Prose elsewhere: "`401`/`403` token ou IP não autorizado"; the Pix guide gives `401` "JWT
  expirado" and `403` "IP não autorizado ou token inválido". Published split: `401` bad or expired bearer, `403` IP.
  Applying it to operations that declare only `403` is an inference.
- `MERCHANT_NOT_FOUND` is published at `403` (sale), `404` (Pix initialize, both read routes) and `422` (acquiring "Erros
  comuns").

Simulator: an unknown or expired bearer is `401` with code `UNAUTHORIZED` on every route (`AutraApi.acquiring`). There
is no IP allow-list, so the address half of Autra's `403` is produced by nothing. The two `403`s this simulator answers
are both attributable: `autra.merchant-not-found-status=FORBIDDEN` (`AutraMerchantNotFound.FORBIDDEN`) and the Pix
checkout's sessionless token (`CreatePixCheckout.FORBIDDEN`).

## Where the Outcome Lives

**A card decline lives in the body, never in the status line.** Every page agrees:

- "Status do pagamento": "Quando o adquirente recusa, a resposta vem com `errors: [{code, msg}]` (ex.:
  `INSUFFICIENT_FUNDS`). **Não** confunda com `400` — `200` com `errors[]` é 'transação processada e recusada', `400` é
  'request mal-formado'." "Erros comuns": "**Status `200` com `errors[]` ≠ erro de API.**" "Boas práticas": "**Trate
  `200 + errors[]` como recusa**, não como erro de servidor."
- The sale's `200` schema is a `oneOf`: an object requiring `paymentID`, `status`, `amount`, `currency`, or one requiring
  `paymentID` and `errors`. The discriminator is **which field is present**. Its description: "Pagamento processado.
  Verifique o campo `status` (sucesso) ou `errors` (recusado pelo adquirente)." The saved example `Recusado` sits at
  `200`.
- The same operation's error-table preamble says `422`: "Quando a transação não é aprovada a resposta é `422` com
  `errors[]`." Its declared `422` is about something else: "`documentId` do split não é um merchant cadastrado no
  tenant".

**Which status accompanies a body-carried decline is the vendor contradicting itself**: `200` (schema) or `422` (prose).
The machine-readable `200` is the default; `autra.sale-decline-status` reaches both.

| Setting value              | Status | Body                                     | Consequence                                         |
|----------------------------|--------|------------------------------------------|-----------------------------------------------------|
| `TWO_HUNDRED_WITH_ERRORS`  | `200`  | `paymentID` and `errors[]`               | the payment's identity is on the wire               |
| `UNPROCESSABLE_WITH_ERRORS`| `422`  | `errors[]` only (`components.schemas.Error` has no other property) | the identity is lost, as on the `409` |

Either way the payment exists here, `DECLINED`, normalized to `FAILED`.

Elsewhere:

- pre-authorization and capture put a decline in `422` and publish no declined `200`. Both declare a `200` schema of
  eight properties with no `required` list at all, so the vendor marks nothing mandatory there,
- void may answer a provisional `200` ("aceito para análise"),
- OAuth has its own envelope and statuses.

## Minimum API Surface

### OAuth

Autra publishes the endpoint twice:

|                      | Guide, "Parte 2 — Gerar token de acesso (a cada hora)"                     | Reference, "Generate access token"                                                     |
|----------------------|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| Credential transport | JSON body `client_id`, `client_secret`, `grant_type`                        | `Authorization: Basic <base64(clientId:clientSecret)>`, `grant_type` in the query      |
| Success fields       | `access_token`, `token_type`, `expires_in`                                 | `accessToken`, `tokenType`, `expiresIn`, all `required`                                |
| Failure status       | `401 invalid_client` ("Erros comuns": "`client_id`/`client_secret` errados ou trocados") | `400`                                                                   |
| Failure body         | not published                                                              | `{"error": "<mensagem>"}`, `error` required                                            |
| Lifetime             | "Cache o token por até 3600s"                                              | "A validade padrão é de 1 hora"                                                        |

Simulator decision: the reference by default (it is the OpenAPI and enumerates ten messages); the two settings reach
the guide's body casing and status. The transport is always Basic.

Success (published example `OK`):

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

`400`: "Erro na requisição, credenciais inválidas ou tenant inativo. O corpo devolve `{"error": "<mensagem>"}`."

| Message                                                     | Cause                                        |
|-------------------------------------------------------------|----------------------------------------------|
| `missing Authorization header`                              | no `Authorization` header                    |
| `missing grant_type`                                        | no `grant_type` query                        |
| `unsupported grant_type`                                    | `grant_type` other than `client_credentials` |
| `missing Authorization Basic`                               | `Authorization` without the `Basic ` prefix  |
| `invalid base64 in Authorization header`                    | payload after `Basic ` is not valid base64   |
| `invalid Basic Auth format, expected clientId:clientSecret` | no `:` after decoding                        |
| `invalid clientId or inactive credentials`                  | unknown or inactive client id                |
| `invalid clientSecret`                                      | secret does not match the stored hash        |
| `tenant not found`                                          | the credential's tenant does not exist       |
| `tenant inactive`                                           | the tenant exists but is disabled            |

### Card Session Creation

`POST /v1/acquiring/card-sessions`

Request: `documentId`, required, the **merchant's**: "CPF ou CNPJ do merchant dono da sessão (somente dígitos) — não é
o documento do portador."

```json
{
  "documentId": "12345678000190"
}
```

Response (published example):

```json
{
  "hostedCardSessionId": "03294670-67f0-4dca-a121-cf0e0fb2a92f",
  "hostedCardUrl": "https://cards.autra.io/tokenize/sessions/03294670-67f0-4dca-a121-cf0e0fb2a92f#token=...",
  "expiresAt": "2026-07-27T22:41:13Z"
}
```

- `hostedCardUrl`: "Curta duração e uso único"; SDK: "hostedCardUrl is short-lived and single-use — mint one per
  attempt."
- `expiresAt`: always present, RFC 3339 UTC with `Z`, "É a criação mais 15 minutos (TTL)." **The only numeric TTL Autra
  publishes** in this surface; imitated and enforced by the store (`autra_card_session`).
- "Server-to-server: chame do seu backend com o access token, nunca do app. O IP do backend precisa estar na allow-list
  do tenant (senão `403`)."
- "After `expiresAt` the session no longer loads — create a new one."
- Failures: `400` invalid body or missing `documentId`, `403` tenant or IP, `404` merchant not found, `409`
  `ACQUIRING_NOT_READY` "Merchant não está pronto para adquirência", `422` invalid CPF/CNPJ.
- No webhook covers tokenization: "Domínios suportados" lists nine domains, none for it.

SDK result, `AutraCardTokenizedResult` ("Callback data"; names complete, types unpublished):

```json
{
  "slugStoredCard": "stored-card-slug",
  "slugToken": "token-slug",
  "tokenExpirationDate": "122030",
  "brand": "VISA",
  "first4": "4111",
  "last4": "1111",
  "expirationMonth": 12,
  "expirationYear": 2030,
  "nickname": "Personal Visa",
  "requestId": "sdk-request-1"
}
```

| Field                 | Published gloss                                                        | Notes                                                                                |
|-----------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `slugStoredCard`      | "Reference your backend uses to charge the card later"                 | pre-authorization schema: "Cartão armazenado — expira junto com o cartão físico"     |
| `slugToken`           | "Single-use token reference"                                           | pre-authorization schema: "Network token — preferir quando disponível"               |
| `tokenExpirationDate` | "Expiration of the token reference"                                    | `MMAAAA` is an inference from `card.expirationDate` ("Formato MMAAAA, sem barra", `122028`) |
| `first4`, `last4`     | BIN prefix and final four, PCI-safe                                    |                                                                                      |
| `nickname`            | "an optional alias the cardholder types on the hosted page", "Empty/absent if left blank" |                                                                   |
| `requestId`           | "Correlation id for support"                                           |                                                                                      |
| `expirationMonth`, `expirationYear`, `brand` | —                                               | integer and uppercase network name are inferences                                    |

The two pages disagree on which slug to prefer for a repeat charge; the simulator follows the pre-authorization schema
(`slugToken`), the page describing the charging endpoint.

SDK error object (closed: "The table below is the **complete** set of `code` values."): `{ code, message, requestId?,
retryable? }`, codes `configuration_error`, `token_expired`, `message_origin_rejected`, `message_invalid`,
`validation_error`, `tokenization_failed`. "When `retryable` is `true`, create a **new** session and retry."

Simulator:

- the session is its own provider object with **no amount and no currency**,
- `documentId` comes from the account; `nickname` comes from the create body, because the card page does not ask
  for it (see "Hosted Card Page"),
- `hostedCardUrl` is `{simulator.public-base-url}/tokenize/sessions/{hostedCardSessionId}?token=<24 hex>`, served by
  this simulator; only the create answer carries it: no screen, `/state` or audit row of autra names it,
- statuses `PENDING`, `TOKENIZED`, `EXPIRED` are the simulator's (Autra publishes none), normalized to `PROCESSING`,
  `SUCCEEDED`, `CANCELLED`,
- `409 ACQUIRING_NOT_READY` and the CPF/CNPJ `422` have no producer,
- `card-add-success` and `card-add-failed` expand onto this endpoint; `card-add-callback-delayed` is not expressible,
  because Autra publishes no tokenization webhook.

Payer step: the hosted card page (see "Hosted Card Page") or
`POST /__simulator/autra/hosted/{hostedCardSessionId}/complete` (also on the Payments screen), with the same effect. Autra
publishes no test card ("request them from Autra"); the payer step offers the SDK example's card
(`VISA`, `4111`/`1111`, 12/2030, alias `published-4111`) plus every payment-method fixture of the run.

| Completion           | Result                                                                                                   |
|----------------------|----------------------------------------------------------------------------------------------------------|
| `PAID`               | `TOKENIZED`, recording `slugToken`, `slugStoredCard`, `tokenExpirationDate`, `brand`, `first4`, `last4` |
| `ABANDONED`          | `EXPIRED`                                                                                                |
| `DECLINED`           | refused: no SDK error code is a session state, and a card page charges nothing                           |
| past `expiresAt`     | refused; the session becomes `EXPIRED`                                                                   |
| second completion    | refused ("uso único")                                                                                    |

- Slugs are minted as uppercase hex (Autra prints only placeholders: `"token-slug"`, `"stored-card-slug"`,
  `slug-token-abc`, `slug-stored-card-xyz`). A fixture's `providerIds.token` and `providerIds.paymentMethodId` are
  handed back unchanged when present.
- A seeded fixture holds no card number, so its session names `last4` and no `first4`.
- `requestId` is not carried.

`POST /v1/acquiring/tokenize-card` (body `{ "documentId": ... }`, the **cardholder's**, "CPF ou CNPJ do portador") has
no `200` schema, only "Cartão tokenizado"; "A tokenização real acontece no **lado do cliente** com o SDK Dock — esta rota
da Autra **prepara a sessão** para a tokenização." **Not served.**

### Hosted Card Page

`GET /tokenize/sessions/{hostedCardSessionId}?token=<token>` and `POST /tokenize/sessions/{hostedCardSessionId}`: the
page `hostedCardUrl` names, opened by a shop's webview.

Every answer carrying the page carries `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`,
`Referrer-Policy: no-referrer` and `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline';
form-action 'self'`, in that order (simulator decision): the page is one self-contained document with inline style, no
script and no external resource, and its form is posted to this origin.

Published ("Card Session Creation"): origin `https://cards.autra.io`, path `/tokenize/sessions/{hostedCardSessionId}`,
token after `#token=`; single use; "After `expiresAt` the session no longer loads"; `nickname` is typed on the page; the
app receives `AutraCardTokenizedResult` or an SDK error from the SDK. Unpublished: the page's transport to the SDK, any
redirect, any read of a card session.

| Request                                                             | Answer                                                                                          |
|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `GET`, session waiting                                              | `200`: `card` select of the run's aliases; buttons `outcome=PAID` "Save this card", `outcome=ABANDONED` "Leave without saving" |
| `POST` `token`, `outcome=PAID`, `card=<alias>`                      | `200`; `TOKENIZED`; `AutraCardTokenizedResult` JSON in `<pre id="autra-card-tokenized-result">` |
| `POST` `outcome=ABANDONED`                                          | `200`; `EXPIRED`                                                                                |
| `POST` other or no outcome, alias not offered                       | `422` with the refusal; session unchanged                                                       |
| `GET` of a session tokenized, expired or past `expiresAt`           | `410` with the reason                                                                           |
| `POST` to such a session                                            | `422` with the reason                                                                           |
| unknown `token`, or another session's token                         | `404`; nothing changed                                                                          |

The `POST` runs `HostedCompletionControl` then `SimulatorProvider#complete`, as the control route does. The audit trail
records it at the page's path, without the token.

Simulator decisions (inference):

- token in the query, not the fragment: a fragment never reaches a server and the page runs no script,
- the test run is resolved from the token, unique across runs by index; `hostedCardSessionId` is unique only per run.
  Rows stored before the column get their URL's token, else a random one,
- a card is picked by alias (`published-4111` and the run's fixtures), never typed: the control completion's list,
- `DECLINED` is not offered: the provider refuses it,
- the result is printed on the page, no redirect: Autra publishes neither transport nor redirect; `requestId` not
  carried,
- no `nickname` field: a completion carries none, so the create body's field is used,
- no merchant credential and no card number on the page.

### Payment Initialize

`POST /v1/acquiring/payments/initialize`

Published: `documentId` is the cardholder's, "CPF ou CNPJ do portador (somente números)", example `12345678901`; the
guide: at initialize "`documentId` aqui é o **portador do cartão** (o pagador)", at the payment call "o **merchant**".

**Live probe, 2026-09-22:** a live integration against `api.autra.io` sends the **merchant's** CPF/CNPJ here,
as at pre-authorization, Pix initialize and the off-session path, and the API takes it. The simulator requires the
merchant's document. This is the one place this file knowingly contradicts the vendor.

```json
{
  "documentId": "25055662000190"
}
```

Response ("Passo 1: Inicializar a sessão"); `transactionalToken` is not `required` in the schema:

```json
{
  "transactionalToken": "eyJhbGciOi..."
}
```

- "**Curta validade** (alguns minutos) — não armazene em cache longo"; one sale per token ("FAQ").
- Only published failure: `403` "Token inválido ou IP não autorizado".
- A `transactionalToken` of this simulator neither expires nor is consumed: a session carries the moment it was opened
  and no deadline, and nothing marks one spent (`AutraSession`, `AutraSessions`). The only refusal is a token that
  names no session, so one token drives sale after sale here and one sale at Autra.

### Direct Sale With Tokenized Card

`POST /v1/acquiring/payments`

Required: `transactionalToken`, `documentId` (merchant), `transactionType`, `amount`, `currency`, `installments`.
Optional: `orderId`, `paymentSplit`. Exactly one of `card` or `tokenData`.

```json
{
  "transactionalToken": "eyJhbGciOi...",
  "documentId": "12345678000190",
  "transactionType": "CREDIT",
  "amount": 150.00,
  "currency": "BRL",
  "installments": 1,
  "orderId": "pedido-abc-123",
  "tokenData": {
    "slugToken": "slug-token-abc",
    "slugStoredCard": "slug-stored-card-xyz"
  }
}
```

- `transactionType`: `CREDIT` or `DEBIT`. `installments`: "`1` = à vista. `2-12` = parcelado."
- `orderId`: "não / **Recomendado** — chave de idempotência".
- "**Use `card` OU `tokenData`, nunca os dois.** Se enviar os dois, o backend recusa." Prose only; the schema does not
  encode it.
- `currency`: a string, "Use `BRL`", not an enum. Simulator: `BRL` only.
- "Com `amount: 0` funciona como **validação de cartão (zero-dollar)**: verifica o cartão junto ao emissor sem cobrar."
- "**Sempre cheque o campo `status` ou `errors`** antes de declarar pago."

The `200` is a `oneOf` of two branches (published rule). The approved branch declares ten properties in this order,
of which four are required: `paymentID`, `status`, `amount`, `currency`, then optional `installments`,
`trackingNumber`, `nsu`, `rrn`, `authorizationCode`, `brandReferenceId`. The declined branch is `paymentID` and
`errors`, both required.

The guide's own approved example (published example) shows four of those ten, and the two publications disagree about
how much an approved sale answers:

```json
{
  "paymentID": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED",
  "amount": 150.00,
  "currency": "BRL"
}
```

The simulator answers the schema's branch, which is the fuller of the two and the one the reference pages are generated
from, minus `brandReferenceId` (see below):

```json
{
  "paymentID": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED",
  "amount": 150.00,
  "currency": "BRL",
  "installments": 1,
  "trackingNumber": "400111222333",
  "nsu": "400111222333",
  "rrn": "112676199769",
  "authorizationCode": "A1B2C3"
}
```

The declined branch (example `Recusado`, also `200`):

```json
{
  "paymentID": "550e8400-e29b-41d4-a716-446655440001",
  "errors": [
    {
      "code": "INSUFFICIENT_FUNDS",
      "msg": "insufficient funds"
    }
  ]
}
```

Simulator:

- the only endpoint here accepting `DEBIT`; nothing inside `card` is read or stored,
- `tokenData` naming neither slug: `400 BAD_REQUEST` (narrowing; the vendor publishes no required member),
- `tokenData` naming no card of the run: `422 TOKEN_NOT_AVAILABLE` (inference: the published code for an ineligible
  token). A card of the run is one tokenized on the hosted card page or a payment-method fixture's
  `providerIds.token`/`paymentMethodId`; every named half must belong to that one card,
- `transactionalToken` naming no session: `400 BAD_REQUEST` (inference, as on pre-authorization),
- `amount >= 0`; negative is `400 INVALID_AMOUNT` (inference). A zero sale writes no monetary operation,
- success `ACCEPTED` → `SUCCEEDED`, one `CAPTURE` operation,
- void of a sale is a refund: `VOIDED` / `PARTIALLY_VOIDED` → `REFUNDED` / `PARTIALLY_REFUNDED`,
- capture of a sale is refused: capture is published on pre-authorization alone, and "`PREAUTH` permanece mesmo após
  captura",
- decline `DECLINED` → `FAILED`, answer per `autra.sale-decline-status`,
- repeat of an approved `orderId`: `409 ORDER_ALREADY_APPROVED` (see "Idempotency"),
- **recurring series are not served**, and `brandReferenceId` is therefore never answered anywhere, on the sale and on
  the lookup alike (simulator decision; see "Payment Lookup"). The
  request schema publishes eleven top-level members and `recurringData` is one of them, optional, "Omitido = venda
  comum", holding one of two fields: `isFirstRecurring` (boolean, enum `true`, "Primeira venda da serie") or
  `brandReferenceId` (string, `minLength` 1, `maxLength` 256, pattern `^\S+$`, "Identificador da serie, devolvido na
  primeira venda"), to be sent "nas vendas seguintes, normalmente com `tokenData.slugStoredCard` e sem CVV". The
  response's `brandReferenceId` is "Presente so na primeira venda de uma serie recorrente" (all published rules). This
  simulator keeps no series, so a sale carrying `recurringData` is **refused** `400 BAD_REQUEST`
  (`ChargeStoredCard.A_RECURRING_SERIES_IS_NOT_SERVED`) rather than accepted and answered without the identifier. The
  refusal is itself an infidelity, since real autra would charge that sale; accepting the member answers `200` with no
  `brandReferenceId`, which leaves the second charge of the series impossible and says so nowhere. Grow's `paymentNum`
  and worldline's partial cancellation are refused the same way. `recurringData` is published on the sale alone; the
  pre-authorization request has nine members and names neither it nor `paymentSplit`.

### Pre-Authorization

`POST /v1/acquiring/payments/pre-authorization`

Schema required: `transactionalToken`, `documentId`, `transactionType`, `currency`, `amount`, `installments`; `orderId`,
`card`, `tokenData` optional, "Envie `card` OU `tokenData` (um dos dois é obrigatório)". `documentId`: "Documento do
merchant (CPF ou CNPJ, sem máscara)", example `25055662000190`. `transactionType`: **`CREDIT` only**.

The guide's example sends three fields, with a cardholder-shaped document:

```http
POST /v1/acquiring/payments/pre-authorization

{
  "documentId": "12345678901",
  "amount":     500.00,
  "currency":   "BRL"
}
```

`autra.pre-authorization-request-shape` chooses which is enforced.

Response: the schema publishes these eight properties in this order, none of them marked required — it carries no
`required` list for this response at all, unlike the sale's. `installments` is a request member here and is **not** in
the response. The vendor publishes no whole-response example, only per-property ones — `status` `ACCEPTED`, `amount`
`150`, `currency` `BRL` — so the values below are what the simulator answers, not what the vendor prints:

```json
{
  "paymentID": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ACCEPTED",
  "amount": 500.00,
  "currency": "BRL",
  "trackingNumber": "400111222333",
  "nsu": "400111222333",
  "rrn": "112676199769",
  "authorizationCode": "A1B2C3"
}
```

`authorizationCode` is "Codigo de autorizacao do emissor"; `rrn` the card network's (see "The Acquirer's Transaction
Identifier" for the two names of one value). The schema's own `status` example on this page is `ACCEPTED` (published
example) and it declares no `status` enum, here or anywhere: a sweep of all 189 published pages found `PRE_AUTHORIZED`
in 0 of 274 enums. The guide's example of this response spells `paymentId` and shows `status` alone, where this page's
schema spells `paymentID`; the vendor mixes the two spellings on its own schema pages too, `{paymentId}` in every path
template against `paymentID` in every response body but the void's.

- "O limite do cartão está reservado, mas o cliente **ainda não foi cobrado**".
- "**Posso capturar mais que o pré-autorizado?** Não. Apenas valor igual ou menor. Se precisar mais, faça uma nova venda
  direta pela diferença."
- "**Quanto tempo dura uma pré-autorização?** Padrão da bandeira — geralmente 5 a 7 dias. Após isso, expira
  automaticamente e o limite volta." Any expiry is a knob.
- Amount "Valor a ser reservado (> 0)"; no minimum published.
- Failures: `400` invalid body, `403` token or IP, `409` `ORDER_ALREADY_PREAUTHORIZED`, `422` declined ("o body traz
  `errors[]` com código e mensagem").
- The `409` has a description and **no `content`**; its body here is the sale's `409` envelope with this code
  (inference).
- `tokenData` naming neither slug: `400 BAD_REQUEST` (narrowing, as on the sale),
- `tokenData` naming a slug of no card of the run: `422 TOKEN_NOT_AVAILABLE`, as on the sale (see "Direct Sale With
  Tokenized Card"); nothing is reserved.

### Capture Pre-Authorization

`POST /v1/acquiring/payments/{paymentId}/capture`

The schema publishes one optional property, `amount` ("pode ser menor ou igual ao pré-autorizado"); the guide sends
`{"amount": 480.00}`. The error table also names `MISSING_DOCUMENT_ID` and `MISSING_TRANSACTIONAL_TOKEN`. Simulator
(inference): requires all three, so both codes are reachable:

```json
{
  "transactionalToken": "eyJhbGciOi...",
  "documentId": "12345678000190",
  "amount": 80.00
}
```

The `200` carries no example, but it does declare a schema (published rule): eight properties in this order, none
marked required, the same eight the pre-authorization answers. The values below are the simulator's answer, the vendor
printing none:

```json
{
  "paymentID": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CAPTURED",
  "amount": 80.00,
  "currency": "BRL",
  "trackingNumber": "400111222333",
  "nsu": "400111222333",
  "rrn": "112676199769",
  "authorizationCode": "A1B2C3"
}
```

| HTTP  | `code`                                                                                 | Published meaning                                                                                        |
|-------|----------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `400` | `INVALID_JSON`, `MISSING_DOCUMENT_ID`, `MISSING_TRANSACTIONAL_TOKEN`, `INVALID_AMOUNT` | "Body inválido (`amount` deve ser > 0, não envie `card`/`tokenData` na captura)."                        |
| `404` | `PAYMENT_NOT_FOUND`                                                                    | "`paymentId` não existe no seu tenant."                                                                  |
| `422` | `INVALID_STATUS`                                                                       | "Pré-autorização não elegível: já capturada, já cancelada, falhou ou expirou. A mensagem diz qual caso." |
| `422` | `INVALID_AMOUNT`                                                                       | "`amount` maior que o valor pré-autorizado."                                                             |
| `422` | `AUTHORIZER_REJECTED` in `errors[]`                                                    | "O emissor recusou a captura, `msg` traz o código de resposta."                                          |
| `422` | `MSG_GENERAL_ERROR` in `errors[]`                                                      | "Erro técnico transitório no processamento — retente, a reserva continua de pé."                         |

- Capture publishes no declined `200`; every decline is `422`.
- `MSG_GENERAL_ERROR` is a retryable `4xx`; the hold survives.
- No `500` is published on capture (only on the sale, `ACQUIRER_COMMUNICATION_ERROR`).
- `INVALID_STATUS` messages are unpublished; the simulator's texts are its own.

### Card Payment Status Expectations

Real Autra answers `ACCEPTED` to a pre-authorization, observed in production logs (live probe) and published by every
schema.

The status of an open reservation is published in the **eight** places tabled below, which do not agree. The simulator
answers `ACCEPTED` to an open reservation and leans on `type` to tell it from a sale (simulator decision), because that
is what the schemas publish and what the listing's own headline query requires.

Four vocabularies name a reserved hold, and three of them name it something: `PRE_AUTHORIZED` in the "Acquiring"
guide, `PREAUTH` in the "Payment Links" guide, `AUTHORIZED` in the "Pagamento com Cartão" guide, and `ACCEPTED` in
every schema. Of the three guide answers, two are not accepted by the listing's own filter; `AUTHORIZED` is (published
rule), and it loses on three measurements:

- `PRE_AUTHORIZED` appears on 1 page of 189 and in 0 of 274 enums (published example count).
- "Pagamento com Cartão" is the oldest acquiring page, `updatedAt 2026-01-08`, against `2026-09-09` for the lookup
  and the listing (published metadata); its `CREATED` state appears in **no payment-status schema anywhere** — its one
  schema occurrence in the whole corpus is an `eventType` enum on the payment-links read (published example); and its
  `CARD_PAYMENT_*` event names appear on that one page of 189 and in no webhook catalogue, where
  `outbound-webhooks` publishes `acquiring.*` and `card.*` instead (published rule).
- `PREAUTH` is the value of `type` in every schema that declares it, never of `status` (published rule).

| Source                                   | Values                                                                                                                                  |
|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| guide "Acquiring", "Status do pagamento" | `ACCEPTED` "Aprovado pelo adquirente. Para venda direta, é o status final feliz."; `CAPTURED` "Pré-autorização foi capturada. Status final feliz para o fluxo pré-auth."; `PRE_AUTHORIZED` "Limite reservado, ainda não capturado."; `DECLINED` "Recusado pelo adquirente (saldo, fraude, etc.)."; `VOIDED` "Cancelado/estornado." |
| guide "Payment Links", card table        | `ACCEPTED`, `CAPTURED`, `PREAUTH` "Pré-autorizado, aguardando captura ou expiração", `VOIDED`, `AUTH_REJECTED`, `CHALLENGE_REJECTED`, `SETUP_REJECTED`, `FAILED`; and "a linha muda `PREAUTH` → `CAPTURED`" |
| guide "Pagamento com Cartão", "Estados" | `CREATED`, `AUTHORIZED` "O pagamento foi autorizado pela adquirência", `DECLINED`, `CAPTURED`, `VOIDED` "A transação foi cancelada antes da captura", beside "Nem toda autorização resulta em captura automática" — so `AUTHORIZED` **is** this page's reserved hold, and it is one of the seventeen the listing accepts |
| pre-authorization and capture `200`      | `status` is `{"type": "string", "example": "ACCEPTED"}`, no enum, on both |
| listing `status` filter                  | "Aceitos: PENDING, ACCEPTED, AUTHORIZED, APPROVED, CAPTURED, VOIDED, PARTIALLY_VOIDED, REFUND_PENDING, PARTIAL_REFUND_PENDING, REFUNDED, SETTLED, PAID, NOT_FOUND, FAILED, ERROR, EXPIRED, DECLINED. Valor desconhecido → `400 INVALID_STATUS`." — seventeen, without `PRE_AUTHORIZED` |
| listing endpoint description             | "Caso de uso principal: descobrir **quais pré-autorizações ainda estão abertas** (`?status=ACCEPTED&type=PREAUTH`) para cancelá-las via *Void payment*" |
| void response, closed enum               | `VOIDED`, `PARTIALLY_VOIDED`, `REFUND_PENDING`, `PARTIAL_REFUND_PENDING`                                                                |
| lookup, **open**                         | "Valores usuais: PENDING, ACCEPTED, AUTHORIZED, APPROVED, CAPTURED, VOIDED, PARTIALLY_VOIDED, REFUND_PENDING, PARTIAL_REFUND_PENDING, REFUNDED, SETTLED, PAID, FAILED, ERROR, EXPIRED, DECLINED, NOT_FOUND. Trate valores desconhecidos como "outro" (a lista pode crescer)." |

Normalization (the native values are Autra's; the mapping is this project's). That an open reservation's native status
is `ACCEPTED` is an invariant this provider maintains at the one site that takes a reservation, not one the store
enforces: no row constraint can see the operation list, so a row could be written with the two disagreeing and the
normalization would still answer `READY_FOR_CAPTURE` off the reservation (simulator decision):

| Native                                        | Normalized                                  |
|-----------------------------------------------|---------------------------------------------|
| a reservation nothing has closed, whatever its native status | `READY_FOR_CAPTURE`: `AutraPayment.normalizedStatus` answers this from the reservation itself — typed `PREAUTH`, an `AUTHORIZATION` and no `CAPTURE` on its ledger, nothing stamped `hold_released_at` — before it reads the native word at all |
| `ACCEPTED` on anything else                   | `SUCCEEDED`                                 |
| `CAPTURED`                                    | `SUCCEEDED`                                 |
| `VOIDED`, nothing captured                    | `CANCELLED`                                 |
| `VOIDED` / `PARTIALLY_VOIDED` of captured money | `REFUNDED` / `PARTIALLY_REFUNDED`         |
| `DECLINED`, `AUTHORIZER_REJECTED` in `errors[]` | `FAILED`                                  |
| `REFUND_PENDING`, `PARTIAL_REFUND_PENDING`    | `UNKNOWN`: `AutraPayment.normalizedStatus` has a case for neither |
| unrecognized                                  | `UNKNOWN`                                   |

Autra publishes `REFUND_PENDING` and `PARTIAL_REFUND_PENDING` on the void endpoint alone, as "aceito para análise" with
the definitive result arriving later on the `acquiring.financial_cycle` webhook, correlated by `rrn`. It publishes that
payload nowhere and never says which status a resolved reversal lands on, so no path of this simulator writes either
status and a reversal here is answered as settled or refused (`AutraOperations.NO_PROCESSING_OUTCOME`).

A card payment of this simulator is stored at five of the seventeen: `ACCEPTED`, `CAPTURED`, `VOIDED`,
`PARTIALLY_VOIDED`, `DECLINED`. The other twelve have no card flow here — `PENDING`, `AUTHORIZED`, `APPROVED`,
`SETTLED`, `PAID`, `FAILED`, `ERROR`, `EXPIRED`, `REFUNDED`, `NOT_FOUND`, and the two above. A card session's statuses
are not in this vocabulary.

Identifier spelling, per endpoint in the vendor's own schema:

| Endpoint                                             | Field        |
|------------------------------------------------------|--------------|
| `POST /v1/acquiring/payments` (both `200` variants)  | `paymentID`  |
| `POST /v1/acquiring/payments/pre-authorization`      | `paymentID`  |
| `GET /v1/acquiring/payments/{paymentId}`             | `paymentID`  |
| `POST /v1/acquiring/payments/{paymentId}/void`       | `paymentId`  |
| guide's pre-authorization example                    | `paymentId`  |
| path parameter everywhere                            | `{paymentId}`|

The simulator emits each spelling where Autra does.

### Payment Lookup

`GET /v1/acquiring/payments/{paymentId}`. "Somente leitura — nunca chama o adquirente." Card has a lookup; Pix does not.
A read takes no scenario decision, records no keyed exchange and changes nothing a later read would see, so it is the
one acquiring route here that never needs the establishment to be ready to transact.

- Merchant document required: header `X-Merchant-Document` ("**Preferido**: mantém o documento fora da URL e dos logs de
  acesso") or query `documentId`; "Um dos dois (header ou `documentId`) é obrigatório."
- "O pagamento só é devolvido se pertencer ao mesmo tenant **e** ao mesmo estabelecimento informado, caso contrário a
  resposta é `404 PAYMENT_NOT_FOUND`."

The vendor publishes the shape and **no example of it**, so the block below is this file's illustration rather than a
publication: the values are made up. The schema's own order has `errors` after `trackingNumber` and `brandReferenceId`
last, which the block leaves out. `nsu` is **not** among the properties; `trackingNumber` is, and carries no description
on this page — the sentence that cross-references this route as `{paymentID}` is on the sale, the pre-authorization and
the capture pages and not on this one (see "The Acquirer's Transaction Identifier").

```json
{
  "paymentID": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": "pedido-abc-123",
  "type": "PREAUTH",
  "status": "CAPTURED",
  "voidRetryable": false,
  "amount": 500.00,
  "refundedAmount": 0.00,
  "currency": "BRL",
  "documentId": "12345678000190",
  "transactionType": "CREDIT",
  "transactionMethod": "TOKEN",
  "installments": 1,
  "maskedCard": "411111******1111",
  "authorizationCode": "A1B2C3",
  "rrn": "010008094933",
  "trackingNumber": "",
  "createdAt": "2026-07-27T22:18:42Z",
  "updatedAt": "2026-07-27T22:20:10Z",
  "events": [
    { "type": "PREAUTH", "status": "ACCEPTED", "amount": 500.00, "at": "2026-07-27T22:18:42Z" },
    { "type": "CAPTURE", "status": "CAPTURED", "amount": 500.00, "at": "2026-07-27T22:20:10Z" }
  ]
}
```

| Field               | Published rule                                                                                                                 |
|---------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `type`              | `SALE` or `PREAUTH`; "`PREAUTH` permanece mesmo após captura"                                                                  |
| `voidRetryable`     | "vale a pena chamar *Void payment* agora?"; true "quando ainda faz sentido chamar o void (reserva/venda ativa)", false "quando já foi cancelado, capturado, liquidado ou nunca reservou valor" |
| `transactionType`   | "`CREDIT`, `DEBIT` ou `PRE_AUTHORIZATION` (marcador de pré-autorização)"                                                       |
| `transactionMethod` | `CARD` or `TOKEN`                                                                                                              |
| `refundedAmount`    | "Soma já devolvida via void/refund"; the only total besides `amount`                                                           |
| `rrn`               | "Use para correlacionar com o webhook `acquiring.financial_cycle`"                                                              |
| `errors[]`          | may be present, `{code, msg}`                                                                                                  |
| `events[]`          | "Linha do tempo em ordem cronológica": `type` ("SALE, PREAUTH, CAPTURE, VOID, REFUND..."), `status`, `amount`, `at`, `errorCode`, `msg`; open on `type` and `status` |

No `capturedAmount` or `voidedAmount` exists. The `200` schema declares **no `required` array at all**, so no property
is published as guaranteed (published rule). Failures: `400` `INVALID_PAYMENT_ID`, `TENANT_REQUIRED`,
`MISSING_DOCUMENT_ID`; `403`; `404` `PAYMENT_NOT_FOUND` "(inexistente, de outro tenant ou de outro estabelecimento)" or
`MERCHANT_NOT_FOUND`; `422` `INVALID_DOCUMENT` "documento não é CPF/CNPJ válido"; `500` `PAYMENT_LOOKUP_ERROR`.
"Diferente de capture e void, esta rota não exige que o estabelecimento esteja pronto para transacionar." `403` names no
code at all, unlike every other failure of this page, and only the `500` declares a response body schema — the codes of
`400`, `403`, `404` and `422` live in the prose of their descriptions (published rule).

What this simulator answers, property by property, in the vendor's own declared order:

| # | Property            | Answered from                                                                                 |
|---|---------------------|-----------------------------------------------------------------------------------------------|
| 1 | `paymentID`         | the stored `payment_id`, capital `D`, where the path parameter is `paymentId`                   |
| 2 | `orderId`           | the stored `order_id`, absent when the call that made the payment sent none                     |
| 3 | `type`              | the stored `payment_type`, `SALE` or `PREAUTH`, set when the payment is made                    |
| 4 | `status`            | the stored `native_status`                                                                      |
| 5 | `voidRetryable`     | the published rule: an active reservation or an active sale, and value left to return. **Not** the predicate the void route admits a call by — see below |
| 6 | `amount`            | the stored `amount`                                                                             |
| 7 | `refundedAmount`    | the operation log's refunded total, `0` when nothing was returned                               |
| 8 | `currency`          | the stored `currency`                                                                           |
| 9 | `documentId`        | the stored `document_id`                                                                        |
| 10| `transactionType`   | the stored `transaction_type`                                                                   |
| 11| `transactionMethod` | `TOKEN` when the payment names a slug, `CARD` when it names neither (inference)                 |
| 12| `installments`      | the stored `installments`                                                                       |
| 13| `maskedCard`        | **never answered**; the only published property of this read this simulator cannot answer       |
| 14| `authorizationCode` | the stored `authorization_code`                                                                 |
| 15| `rrn`               | the stored `rrn`                                                                                |
| 16| `trackingNumber`    | the stored `tracking_number`, absent when the acquirer named none                                |
| 17| `errors`            | the code and `msg` of every step of the timeline that was refused, chronological (inference)    |
| 18| `createdAt`         | the stored `created_at`                                                                         |
| 19| `updatedAt`         | the stored `updated_at`                                                                         |
| 20| `events`            | `autra_payment_event`, one row per step                                                         |
| 21| `brandReferenceId`  | never answered, because no payment here is the first sale of a recurring series; real autra would omit it on these payments too |

- **`maskedCard` is the one published property of this read that this simulator answers with nothing.** It is declared
  a bare `{"type": "string"}` at every schema site, with no description, example, format or pattern anywhere; and this simulator keeps no card number on a payment at all, because the sale's `card` member is redacted
  out of the audit trail by design and the `tokenData` path never sees a number. A mask invented over digits this build
  does not hold would put a made-up value where a reader expects the acquirer's, so the property is absent instead. The
  `200` schema marks nothing required, so absence is inside the published contract.
- A payment made before `payment_type` existed carries the type the backfill gave it, read off the **operation log**: a
  payment holding an `AUTHORIZATION` operation, or one whose hold was released, is a `PREAUTH`; everything else is a
  `SALE`. The backfill does not read the status, because a sale reversed in part reads `PARTIALLY_VOIDED` with no
  authorization ever taken and a status-reading backfill would type it a pre-authorization. The one shape it cannot
  tell apart is a **pre-authorization the acquirer declined**: it reserved nothing, so it holds no authorization
  operation and nothing else in the old shape distinguishes it from a declined sale, and it backfills as `SALE`. A
  payment the route typed is never guessed at.
- `voidRetryable` follows the published rule and not this simulator's own void route, and the two differ. The vendor
  publishes `true` "quando ainda faz sentido chamar o void (reserva/venda ativa)" and `false` "quando já foi cancelado,
  **capturado**, liquidado ou nunca reservou valor" (published rule). The flag is answered as
  `AutraPayment.isAReservationOrSaleNothingHasClosed()` — a hold nothing has closed, or a sale the acquirer accepted —
  and value left to return, which maps the vendor's two positive cases and its "nunca reservou valor" onto this
  contract's own statuses: `CAPTURED`, `VOIDED`, `PARTIALLY_VOIDED`, `DECLINED`, a released hold and a zero-amount sale
  all read `false`. **This simulator's void route is wider than that flag.** It admits a reversal of money a capture
  already took, so a `CAPTURED` payment reads `voidRetryable` `false` and a void of it is nevertheless answered `200`.
  The vendor's wording is advisory — "vale a pena", "faz sentido" — and it publishes the flag on the two read addresses
  and on the void address nowhere, so it never binds the flag to what the void route admits.
- `brandReferenceId` is absent as a **consequence** of recurring series not being served: a sale carrying
  `recurringData` is refused `400`, so no payment here is the first sale of a series and there is none to name.
  `includeRecurring` is accepted and changes nothing, for the same reason; refusing a published parameter would be the
  visible difference.
- `events[]` is a timeline this simulator records itself, one row per step, written by the sale, the pre-authorization,
  the capture and the void, on the step the acquirer took **and** on the step it refused. A refused step carries
  `errorCode` and `msg` and the `status` the payment keeps rather than a new one; the store enforces that the two halves
  of a refusal are both present or both absent. A reversal is a `VOID` step and never a `REFUND` one, because this
  contract publishes void and no refund. A payment stored before `autra_payment_event` existed has an empty `events`:
  the timeline records steps that were observed, and no step is synthesised for a payment whose steps were never
  recorded.
- `MERCHANT_NOT_FOUND` is answered at `404` here and **not** at `autra.merchant-not-found-status`. That setting
  reproduces a vendor ambiguity — the code is published at `403`, `404` and `422` across the surface — and these two read
  routes publish one status for it.
- `INVALID_DOCUMENT` is answered to a document that is not digits only or is longer than the 32 characters the store
  holds, which is the shape account configuration already enforces. The vendor also runs the CPF and CNPJ check digits;
  this simulator does not, because an account here is configured with any digits-only document and a check-digit rule
  would refuse the very merchants a run holds (simulator decision).
- Both reads are questions a tester asks by clicking, on the **Look a payment up** screen, which performs these two
  routes themselves (`AutraReads.DECLARED`, `kernel/spi/DeclaredRead`). The look-up asks for `paymentId` alone and
  offers the card payments the chosen establishment has taken in the run, newest first, so no identifier is typed. The
  listing asks for `from`, `to`, `status`, `type`, `page` and `limit` as text, because a value this route refuses has to
  reach it to come back as `INVALID_STATUS`, `INVALID_TYPE`, `INVALID_FROM`, `INVALID_TO`, `INVALID_RANGE`,
  `RANGE_TOO_LARGE`, `INVALID_PAGE` or `INVALID_LIMIT`, and because `status` is a comma-separated list that no list of
  single values could offer. The establishment is the account's `documentId`, sent in `X-Merchant-Document`, which both
  reads declare under `DeclaredRead.fieldsItFillsInItself` with the sentence saying where the value comes from
  (`AutraReads.THE_ESTABLISHMENT_IS_THE_ACCOUNTS_OWN`), because a field the simulator fills in is one the port refuses
  to also ask a tester for; so `MISSING_DOCUMENT_ID`, `INVALID_DOCUMENT` and `MERCHANT_NOT_FOUND` are reachable from
  the wire alone, and so are `INVALID_PAYMENT_ID` and `PAYMENT_NOT_FOUND`, because the one field the look-up asks for
  offers identifiers this run holds and nothing else.
- An answer is one row per payment, one fact per member, under the name autra answers it under: a step of the timeline
  is answered under `events` as the list of objects autra nests there, and a collected refusal under `errors`, so no
  fact is named after a member and the place that member was answered in, which is a name autra answers nothing under.
  A member answered as an empty list, as an object with no member of its own, or with no value at all is drawn as the
  answer autra gave rather than dropped, because a fact missing from a row reads as a member autra never answered.
- The listing's `page`, `limit`, `hasMore`, `from` and `to` are answered about the question rather than as a payment of
  the establishment, whether a payment matched or not, so a tester whose window matched nothing reads both the window
  autra applied and the sentence saying what an empty `items` means. The look-up answers nothing of its own about the
  question, because autra reads the identifier back inside the payment under `paymentID`.
- The look-up declares that it is never answered with nothing at all: autra answers the whole payment or refuses the
  call, `PAYMENT_NOT_FOUND` at 404 and `INVALID_PAYMENT_ID` at 400, so the screen prints what it answers in place of
  nothing where the listing prints what an empty `items` means. A read that cannot answer nothing and answers no row
  anyway is refused by the port rather than printed under a sentence about a case this contract has no case for.
- The rail's **Read back** tab, the contributed screen `extensions/autra/read-back.html`, prints the establishment's
  card payments newest first with every property the listing answers that the payment has a value for — `errors`
  excepted, whose refusals are printed on the steps of the timeline they happened on rather than collected a second
  time — the timeline each one answers under `events`, and a panel of the pre-authorizations still open, which is what
  the vendor publishes the listing for. A captured hold leaves that panel, because `voidRetryable` reads `false` once a
  capture has taken the money and the API query the panel names excludes it too. The screen reads the same state the two
  routes answer from, through the provider-neutral `objects` and `state` of the kernel SPI, and performs neither route:
  it is the whole run at a glance with no form to fill in, where the **Look a payment up** screen is one question
  answered. It documents no query parameter of its own, because each one is documented beside the field a tester types
  it into, and two surfaces describing one query drift apart. Each payment links on to the Payments screen, where a read
  is paged, and to the audit trail. The screen holds no form control at all.
- `TENANT_REQUIRED`, `403` and `500 PAYMENT_LOOKUP_ERROR` are **unreachable**. The tenant is always resolved (see
  "Test Run Resolution" in `README.md`); there is no IP allow-list, so the address half of autra's `403` is produced by
  nothing (see "Authentication and Transport"); and the read touches one store and no acquirer, so no published failure
  of it can occur. A dead bearer is `401`, as on every route here.

### Payment Listing

`GET /v1/acquiring/payments`. Read-only; "nunca chama o adquirente". "Lista os pagamentos (vendas e pré-autorizações) do
estabelecimento em uma janela de datas, **do mais recente para o mais antigo**." "Caso de uso principal: descobrir
**quais pré-autorizações ainda estão abertas** (`?status=ACCEPTED&type=PREAUTH`) para cancelá-las via *Void payment* ...
que os emissores penalizam."

| Parameter                                  | Rule                                                                                                      |
|--------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `X-Merchant-Document` or `documentId`      | one required; the header is "**Preferido**: mantém o documento fora da URL e dos logs de acesso"           |
| `status`                                   | "Lista separada por vírgula (ex.: `ACCEPTED,CAPTURED`)" over seventeen values; unknown → `400 INVALID_STATUS` |
| `type`                                     | `SALE`, `PREAUTH`; one value, no comma-separated list published                                            |
| `from`, `to`                               | "RFC3339 ou `YYYY-MM-DD` (UTC)", a bare date "inclui o dia inteiro", matched `createdAt >= from` and `createdAt < to` so `to` is exclusive; "Janela padrão: últimos 7 dias; máxima: 31 dias"; `to` defaults to "agora" and `from` to "`to` − 7 dias" |
| `page`, `limit`                            | `page` from 1, default 1, no maximum published; `limit` 1–200, default 50                                  |

Envelope: `items`, `page`, `limit`, `hasMore`, and the **effective** `from` and `to` — "Janela efetivamente aplicada".
Page-based: the vendor publishes no `total` and no cursor, and remarks on the absence of a total. Items are "o mesmo
formato de *Get payment*, sem `events`", with `paymentID`, so nineteen properties and no `brandReferenceId` either.
Failures: `400` `TENANT_REQUIRED`, `MISSING_DOCUMENT_ID`, `INVALID_FROM`, `INVALID_TO`, `INVALID_RANGE`,
`RANGE_TOO_LARGE` "(> 31 dias)", `INVALID_STATUS`, `INVALID_TYPE`, `INVALID_PAGE`, `INVALID_LIMIT`; `403`; `404`
`MERCHANT_NOT_FOUND`; `422` `INVALID_DOCUMENT`. No `500` on this page. `INVALID_RANGE` carries no published condition;
only `RANGE_TOO_LARGE` does. All of the above is published rule.

The published API view under `src/main/resources/static/openapi` names **none of these parameters**. It derives
`parameters` from `@PathVariable` and `@RequestParam` alone, and a provider route of this build binds neither: it reads
the servlet request, so the vendor's own wire format stays out of the records this build describes. The lookup therefore
publishes its path parameter and nothing else, and the listing publishes no parameters at all, while serving seven query
parameters and a header, every one of which selects the answer. That is a **blind spot of the convention and not a
statement that the routes take no parameters**; the parameters are specified here.

The **31-day maximum is the vendor's own number**, published twice — in the endpoint description and beside
`RANGE_TOO_LARGE` — and is not this simulator's bound. A window of exactly 31 days is taken; one second more is
`400 RANGE_TOO_LARGE`.

Simulator, and why:

- This filter takes the seventeen values `status` names and **nothing else**, which is exactly
  `AutraStatus.isOneTheVendorHasPublished`, whose seventeen are pinned against literals by `AutraStatusTest`.
  `PRE_AUTHORIZED` is not among them and is refused `400 INVALID_STATUS`, the rule the vendor publishes on the same
  line for a value it does not name, although the vendor's guide publishes `PRE_AUTHORIZED` as a payment status
  elsewhere. Accepting it would be an eighteenth value of this simulator's own invention answering `200` where Autra
  answers `400`, a difference a client could use to tell the simulator from the real thing (simulator decision).
- The vendor's own published query for its headline use case, `?status=ACCEPTED&type=PREAUTH`, selects exactly the open
  holds here. An open reservation and a charged sale both answer `ACCEPTED`, so `status` alone cannot separate them and
  `type` is what does — which is why the vendor's query names both.
- The twelve of the seventeen no card payment of this simulator ever reaches (see "Card Payment Status Expectations")
  are accepted rather than refused, because the real API accepts them and refusing a published value would be the
  visible difference; a filter naming only those matches nothing.
- A call that names **no** `to` is given the present moment **plus one second** as the effective end, and the answer
  says so. The vendor's "agora" is read against a clock that always advances, so a payment created a moment earlier is
  inside `createdAt < to`; this simulator lets a tester freeze the clock, and a frozen `to` equal to `now` would put
  every payment that run had just made exactly on the exclusive boundary and answer an empty list. One second is the
  resolution provider autra writes a moment at (simulator decision). The effective `from` stays the published `to` − 7
  days, so the default window is exactly seven days wide.
- A `from` **after** its `to` is `400 INVALID_RANGE`; a `from` equal to its `to` is admitted and answers an empty
  listing, which is an empty window rather than an impossible one. The vendor publishes no condition for
  `INVALID_RANGE`, so this split is an inference.
- The store answers every payment of the establishment, newest first, in one statement that carries no condition of its
  own, and the window, the status list, the type and the page are applied in `ListPayments`. A test run holds the
  payments one test made, so the work is the same either way, and a decision taken there is measured by the mutation
  gate where a decision inside a JDBC adapter is not (simulator decision).
- `page` has no published maximum, so a page past the end answers an empty `items` with `hasMore` false rather than a
  refusal. `limit` 200 and `limit` 1 are both answered back as the value asked for.
- A payment of another establishment of the same test run is not in the listing, and the bearer must have been issued
  for the establishment the document names.

### Void / Cancellation

`POST /v1/acquiring/payments/{paymentId}/void`

Schema: `transactionalToken`, `documentId`, `amount` required, `currency` optional. The guide calls it twice with no
body. `autra.void-request-shape` chooses.

```json
{
  "transactionalToken": "eyJhbGciOi...",
  "documentId": "12345678000190",
  "amount": 100.00,
  "currency": "BRL"
}
```

- "Envie `amount` com o valor a cancelar (`amount > 0`): menor que o valor original = cancelamento **parcial**, igual =
  cancelamento **total**." A pre-authorization that has captured nothing is released whole here, and an `amount` below
  what it reserved is refused `422 INVALID_AMOUNT` (`ReverseOrRelease.A_RESERVATION_IS_RELEASED_WHOLE`). A partial
  reversal of money already captured is served and lands at `PARTIALLY_VOIDED`.

- Response: `paymentId`, `status`, `amount`, `currency`, `rrn` ("Referência usada para correlacionar o webhook de
  settlement"), `trackingNumber` — in that order, with `rrn` before `trackingNumber`, the reverse of every other
  endpoint's order, and none marked required. This is the one acquiring response spelling the identifier `paymentId`
  with a lowercase `d`. **`nsu` is not published here**, and `trackingNumber` carries no description on this page.
- Status (closed enum): "`VOIDED` — cancelamento total aceito (síncrono, mesmo dia).", "`PARTIALLY_VOIDED` —
  cancelamento parcial aceito.", "`REFUND_PENDING` / `PARTIAL_REFUND_PENDING` — aceito para análise, o resultado
  definitivo chega depois pelo webhook `acquiring.financial_cycle`, correlacionado por `rrn`. `PENDING` nunca é retornado
  como erro."
- The trigger ("FAQ"): "Se feito no mesmo dia (antes do batch do adquirente), vira `VOIDED` e o débito **nem aparece** no
  extrato do cliente. Após o batch, vira reembolso (aparece como crédito no próximo ciclo)." Which status a resolved
  pending reversal lands on is unpublished; `REFUNDED` is the inference.
- Eligibility is the acquirer's; ineligible requests are `422` with a reason, "ex.: `AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE`,
  `CANT_REFUND_TRANSACTION_FROM_TODAY`, `ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD`, `CARD_BRAND_NOT_SUPPORTED`,
  `ORIGINAL_CYCLE_NOT_ACCEPTED`" (open list). `CANT_REFUND_TRANSACTION_FROM_TODAY` means *later*;
  `ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD` means *never*.
- `400` `INVALID_AMOUNT` (`amount <= 0`) and `INVALID_JSON`; `403`; `404`. No `500`.

Simulator: synchronous `VOIDED` / `PARTIALLY_VOIDED` only. A repeated full void (nothing remaining) is `422
INVALID_STATUS` (simulator's choice; the vendor publishes no code). An uncaptured hold reports `refundsAccepted` `true`
with `refundableRemainder` zero (`README.md`, "State response rules").

### PIX Initialize

`POST /v1/acquiring/pix/initialize`. `documentId` is the merchant's: "CPF ou CNPJ do estabelecimento (merchant)
cadastrado no tenant, somente números. Não é o documento do pagador."

```json
{
  "documentId": "12345678000190"
}
```

```json
{
  "code": "ACCEPTED",
  "transactionalToken": "eyJhbGciOi..."
}
```

`code` has one published value. Failures: `400` `documentId`, `403` token or IP, `404` "Estabelecimento não encontrado
para este tenant (`MERCHANT_NOT_FOUND`)".

Simulator: `code` is always `ACCEPTED`; the card initialize answers `transactionalToken` alone, as published; a token
from either initialize opens either payment call (nothing published forbids it).

### PIX Checkout

`POST /v1/acquiring/pix/checkout`. Required `transactionalToken`, `documentId`, `amount`, `currency`; optional
`description` ("Aparece no app do banco do pagador") and `orderId`.

```json
{
  "transactionalToken": "eyJhbGciOi...",
  "documentId": "12345678000190",
  "amount": 99.90,
  "currency": "BRL",
  "description": "Pedido #1234",
  "orderId": "pedido-abc-123"
}
```

Response ("Passo 2: Criar o Pix (gera QR Code)"):

```json
{
  "pixId": "cdb445694be7486e830f720658f9a1a1",
  "status": "PENDING",
  "qrCode": "00020126580014BR.GOV.BCB.PIX0136...",
  "amount": 99.90,
  "currency": "BRL",
  "expiresAt": "2026-05-01T18:30:00Z"
}
```

State machine ("State machine"): `PENDING` → `AUTHORIZED` → `SETTLED`, or `PENDING` → `EXPIRED`; "**Estados terminais:**
`SETTLED`, `EXPIRED`." `PENDING` "QR criado, aguardando pagamento", `AUTHORIZED` "Pagamento confirmado pelo banco (já
caiu no fluxo)", `SETTLED` "Liquidado — dinheiro disponível na conta", `EXPIRED` "QR não foi pago no prazo".

- `qrCode`: "Payload **copia-e-cola** do QR Code".
- `pixId` equals `operationData.pixId` in webhooks and the Pix `txid`.
- `expiresAt`: "Padrão atual: alguns minutos."
- "**Sem endpoint público de status** — você depende de webhooks para confirmar pagamento."
- "**Sem cancelamento** — Pix Checkout não tem rota de void. Para 'cancelar', basta deixar expirar (`expiresAt`)".
- No split, no instalments.
- "`403` (no `/checkout`) — `transactionalToken` expirou entre os passos 1 e 2 — Refazer o `/initialize`."

Simulator:

- a sessionless `transactionalToken` is `403` with code `UNAUTHORIZED` (the code is an inference; the status is the
  vendor's, and differs from pre-authorization's `400`),
- `amount` must be above zero, `400 INVALID_AMOUNT` (narrowing),
- `expiresAt` is checkout plus **5 minutes**, not configurable,
- `qrCode` is `00020126580014BR.GOV.BCB.PIX01` followed by the `pixId` (`CreatePixCheckout.theQrCodeOf`); **not a
  valid EMV BR Code**,
- normalized `PENDING` → `PROCESSING`, `AUTHORIZED` and `SETTLED` → `SUCCEEDED`, `EXPIRED` → `CANCELLED`,
- payer step `POST /__simulator/autra/hosted/{pixId}/complete`: `PAID` → `AUTHORIZED` ("**Trate `AUTHORIZED` como pago**
  na maioria dos casos"), `ABANDONED` → `EXPIRED`, `DECLINED` refused (no declined state is published), past
  `expiresAt` refused and left `EXPIRED`,
- settlement is the `settle` follow-up (`POST /__simulator/autra/objects/{pixId}/follow-ups/settle`, or "settle
  (payout)" on the payment screen): `AUTHORIZED` → `SETTLED` and `acquiring.payout` queued in one transaction; `422`
  from any other state; normalized stays `SUCCEEDED`. Tester-triggered, because Autra publishes no trigger or timing.

## Acquiring Webhooks

Published on "Outbound Webhooks" unless marked. Subscriptions are per domain, `PUT /v1/banking/webhooks/{domain}`; the
`acquiring` domain covers "Adquirência (POS + Pix Checkout)". Delivered only "para merchants cadastrados/associados na
plataforma Autra."

| Header              | Value                                                         |
|---------------------|---------------------------------------------------------------|
| `Content-Type`      | `application/json`                                            |
| `User-Agent`        | `Autra-Webhooks/1.0`                                          |
| `X-Autra-Signature` | `sha256=<hex lowercase>`                                      |
| `Authorization`     | "Opcional, conforme `auth` da subscription (Basic ou Bearer)" |

Transport auth modes: `NONE` (default), `BASIC`, `BEARER`, `HEADER`; a custom header may not be `Host`,
`Content-Length`, `Content-Type`, `X-Autra-Signature` or `User-Agent`.

Signature ("Validação do `X-Autra-Signature` (HMAC-SHA256)"):

- `"sha256=" + hex(HMAC-SHA256(secret, raw_body))`, lowercase: "Não aplique `toUpperCase` na comparação",
- secret: "uma string de 32+ chars **única por tenant** — compartilhada entre todos os domínios. Não há secret separado
  por domínio"; server-generated ones
  are 64 hex characters,
- over the raw bytes: "Capture os bytes ANTES de qualquer parse",
- "Compare a string inteira (`"sha256=" + hex`) ou strippe de ambos os lados",
- constant-time; a mismatch is answered **401**,
- every retry is re-signed with the tenant's current secret.

Event types on `acquiring` (closed for the domain): `acquiring.financial_transaction`, `acquiring.payout`,
`acquiring.financial_cycle`, `acquiring.payment_slip_notification`, `acquiring.merchant_onboarding_status`. Every
published type is dotted.

Envelope, "sempre presentes": `eventId` (UUID), `eventType`, `occurredAt` (ISO-8601 UTC), `tenantId`, `userDocument`,
`ownerId`, `status`, `counterpartyName`, optional `flavor`, `operationData`. "**Use pra idempotência** (mesmo `eventId`
pode chegar até 16 vezes em retry)." "roteie por `(tenantId, userDocument)`."

Pix transaction ("Passo 4: Receber confirmação", variant A):

```json
{
  "eventId": "a58720d1-0a70-4012-b718-936e521f81b8",
  "eventType": "acquiring.financial_transaction",
  "occurredAt": "2026-07-27T22:18:42Z",
  "status": "AUTHORIZED",
  "operationData": {
    "subject": "FINANCIAL_TRANSACTION",
    "productType": "PIX",
    "orderId": "pedido-abc-123",
    "pixId": "cdb445694be7486e830f720658f9a1a1",
    "payload": {
      "slug": "A13CA0...",
      "muid": "1a0b5369...",
      "transactionStatus": "AUTHORIZED",
      "totalAmount": 10,
      "currency": "BRL"
    }
  }
}
```

Pix settlement (variant B):

```json
{
  "eventId": "41f4ffd2-285b-40c3-aec8-655800e00676",
  "eventType": "acquiring.payout",
  "occurredAt": "2026-07-27T22:18:43Z",
  "operationData": {
    "subject": "PAYOUT",
    "productType": "PIX",
    "orderId": "pedido-abc-123",
    "pixId": "cdb445694be7486e830f720658f9a1a1",
    "payload": {
      "payoutId": "A13CA0...",
      "status": "SETTLED",
      "amount": 10,
      "expectedSettlementDate": "2026-07-27"
    }
  }
}
```

Card (the envelope sample, "Request"):

```json
{
  "eventId": "01963e6f-aa00-7c00-9d00-000000000001",
  "eventType": "acquiring.financial_transaction",
  "occurredAt": "2026-05-20T15:25:00Z",
  "tenantId": "2916bfe7-ecec-46bc-bc51-8a19aa593c1a",
  "userDocument": "25055662000190",
  "ownerId": "019d4f6e-c21d-4199-9424-9c37a2a1c65d",
  "status": "AUTHORIZED",
  "counterpartyName": "Fulano de Tal",
  "operationData": {
    "subject": "FINANCIAL_TRANSACTION",
    "productType": "CREDIT",
    "slugMerchant": "57DA076635814851A7A4B36FEDB340BB",
    "orderId": "pedido-abc-123",
    "payerName": "Fulano de Tal",
    "payerDocument": "12345678901",
    "payerEmail": "fulano@example.com",
    "payload": {
      "rrn": "010008094933",
      "muid": "26F9758DB70336D7547CCA2ECA1C76D4",
      "slug": "4C037E901ADD45E29BA9C80C00A71745",
      "totalAmount": 12.50,
      "productType": "PREPAID_DEBIT",
      "transactionStatus": "AUTHORIZED"
    }
  }
}
```

`operationData` rules:

- correlation fields `orderId`, `pixId`, `payerName`, `payerDocument`, `payerEmail`, `paymentLinkSlug` are all optional:
  "cada um só aparece se houver valor — trate todos como opcionais". No `paymentId`. A card event correlates by
  `orderId`, `slugMerchant` or `payload.rrn`,
- `productType` is open, observed `CREDIT`, `DEBIT`, `PREPAID_CREDIT`, `PREPAID_DEBIT`, `PIX`, `VOUCHER`: "Desserialize
  `productType` como string e tenha um caminho de fallback". No `CARD`,
- `payload` is passed through: "Parseie de forma **tolerante** — campos podem ser adicionados sem aviso." Its status is
  `transactionStatus`,
- "**`totalAmount`** é **decimal em reais** (ex.: `12.50`, `0.01`) — **não** é inteiro em centavos",
- "`acquiring.payout.payload.payoutId` == `acquiring.financial_transaction.payload.slug`"; `muid` differs from `pixId`,
- **the outcome sits in different places**: a transaction has top-level `status` and `payload.transactionStatus`; a
  payout example has **no top-level `status`**, only `payload.status`, although the page lists `status` as always
  present.

Meaning: `AUTHORIZED` — "**Trate `AUTHORIZED` como pago** na maioria dos casos (o valor está garantido). Só aguarde
`SETTLED` se for fluxo crítico."; `EXPIRED` — unpaid (simulator decision); payout `SETTLED` — "**`SETTLED` é quando
você pode liberar o produto/serviço.**";
`acquiring.financial_cycle` resolves a pending reversal by `rrn`, **payload unpublished**.
"Eventos **intermediários** (PROCESSING, ACCEPTED, FILES_PENDING etc.) **não** disparam webhook."

Delivery ("Política de entrega + retry"):

| Phase            | Behaviour                                                |
|------------------|----------------------------------------------------------|
| attempt 1        | synchronous at the moment of the event, 5-second timeout |
| attempts 2–11    | one per minute                                           |
| attempts 12–16   | one per ten minutes                                      |
| after attempt 16 | abandoned, recorded as `DESPREZADO`                      |

"**Total: 16 tentativas em ~60 min** antes de DESPREZAR." Only `2xx` counts; a timeout re-queues. The published receiver guidance is to answer
immediately and process asynchronously, because of the 5-second timeout.

Simulator (Pix only; deliveries appear on the **Webhooks** screen):

- renders `acquiring.financial_transaction` and `acquiring.payout` from the Pix examples, each with its own status
  placement; the other three types are refused (no payload published),
- envelope `eventId`, `eventType`, `occurredAt`, `userDocument` (the merchant's `documentId`), `status` (not on a
  payout) and `operationData`; **no** `tenantId`, `ownerId`, `counterpartyName` (none exists in this contract, and the
  Pix examples carry none),
- amounts are JSON numbers,
- the retry plan above is the default of `webhook.retryPlan`,
- nothing is queued for a `PENDING` Pix, or for a merchant with no callback target,
- a Pix past `expiresAt` reads `EXPIRED` by the clock and queues nothing; the `ABANDONED` payer step moves it to
  `EXPIRED` and queues `acquiring.financial_transaction` with `status` and `payload.transactionStatus` `EXPIRED`; SEND
  of that event on an `EXPIRED` Pix is honoured,
- `acquiring.payout` is queued by `settle`; `payload.status` is the Pix's stored status. SEND of `acquiring.payout`
  is a resend: `SETTLED` only; `AUTHORIZED` is `422` naming `settle`,
- **unsigned when `webhookSecret` is not set**; otherwise signed once at queueing, not re-signed per retry,
- no card webhooks: the one card example carries `slugMerchant`, `payerName`, `payerDocument`, `payerEmail`, which no
  request here supplies.

## The Acquirer's Transaction Identifier

The vendor publishes one value under two names. Verbatim from the response schemas (published rule):

- `trackingNumber`: "Identificador da transação no adquirente (o \"NSU\"). Use para conciliação. Também retornado em
  `GET /v1/acquiring/payments/{paymentID}`. Ausente se o adquirente não informar."
- `nsu`: "Mesmo valor de `trackingNumber`, mantido por compatibilidade."

So `trackingNumber` is the primary name, `nsu` repeats the same value and exists only for compatibility, and either may
be absent when the acquirer reports none. The two descriptions appear word for word on three independent reference
pages — the direct sale, the pre-authorization and the capture — which is where the whole reconciliation story lives:
the prose guide names neither field, shows neither in any example, and never uses the words `NSU`, `conciliação` or
`compatibilidade`. The last sentence of the `trackingNumber` description cross-references
`GET /v1/acquiring/payments/{paymentID}`, whose path parameter the vendor publishes as `{paymentId}`; the vendor's own
cross-reference does not match the vendor's own path.

Which endpoints publish which name (published rule):

| Endpoint                                        | `trackingNumber` | `nsu` | Order                                    |
|-------------------------------------------------|------------------|-------|------------------------------------------|
| `POST /v1/acquiring/payments`                   | yes              | yes   | after `installments`, before `rrn`        |
| `POST /v1/acquiring/payments/pre-authorization` | yes              | yes   | after `currency`, before `rrn`            |
| `POST .../{paymentId}/capture`                  | yes              | yes   | after `currency`, before `rrn`            |
| `POST .../{paymentId}/void`                     | yes              | **no** | **after** `rrn`, and undescribed         |
| `GET /v1/acquiring/payments/{paymentId}`        | yes              | **no** | after `rrn`, before `errors`             |
| `GET /v1/acquiring/payments`                    | yes              | **no** | after `rrn`, before `errors`             |

Simulator: a payment holds **one** transaction identifier, and the two names are two spellings of it rather than two
values. `autra_payment` has a single nullable `tracking_number` column; there is no `nsu` column and no second
generator, so the two names have nothing to disagree about. `AutraAcquirerTransaction` holds the only two occurrences
of either name that reach a wire or a screen, as private constants, and is the only class that reads
`AutraPayment.trackingNumber()`; `AutraFields` carries neither name. Both words also appear as prose, in
`AutraAcquirerReport`'s accepted-value sentences and in `AutraMerchant`'s field explanation, which no reader parses.
Twelve digits is the simulator's own decision (see "Amounts, Currencies and Identifiers"), enforced by a check
constraint rather than by convention.

`autra.acquirer-transaction-identifier` drives the published absence. `REPORTED`, the default, is an acquirer that
names the transaction: every response above that publishes a name carries it. `NOT_REPORTED` is an acquirer that names
none: the payment is stored with no identifier at all, so every response about **that payment** leaves the field out,
`nsu` with it where `nsu` is published, and nothing in the inspector names it either. The setting is read once, where
the payment is created, so a payment taken while the account said `REPORTED` keeps the identifier it was given and
still carries it on a capture or a void after the account is flipped; flipping the setting changes what the next
payment is stored with, never what an existing one holds. The absence is a property of the
payment, not of the rendering, which is why no representation can contradict another.

## Amounts, Currencies and Identifiers

- Amounts are **decimal units, never minor units** (`number`, examples `150`, `99.9`, `0.1`, `12.50`).
- No decimal-place rule and no minimum or maximum. `> 0` on pre-authorization, capture and void; `0` meaningful on the
  sale.
- `currency` is never an enum; "Use `BRL`". Restricting to `BRL` is a simulator decision.
- `documentId`: CPF or CNPJ, digits only. Merchant on the sale, card sessions, pre-authorization, void, lookup, both Pix
  calls, and (live probe) the payment initialize. Cardholder on `tokenize-card` per the page, unobserved. The
  pre-authorization guide example sends a cardholder-shaped value.
- `paymentID` / `paymentId` per endpoint, see "Card Payment Status Expectations"; a UUID.
- `pixId`: 32 lowercase hex characters in the example; the Pix `txid`.
- `rrn`, `nsu`, `authorizationCode`, `trackingNumber`, `slug`, `muid`, `payoutId`, `slugMerchant`: no published format,
  pattern, length, bound or example value anywhere — not in the guide, not on any reference page, and not in any
  example payload, none of which shows these four fields at all; `slug`, `muid`, `slugMerchant` are uppercase hex in
  every example. The shapes this simulator emits are therefore its own decisions: `trackingNumber` and `nsu` are twelve
  digits, `rrn` twelve digits, `authorizationCode` six uppercase hex characters, `nsu` whatever `trackingNumber` is.
  The twelve digits are enforced over the store by `autra_payment_tracking_number_is_twelve_digits`, whose pattern is
  `^[0-9]{12}$`, so a value of any other shape cannot be written at all. The one field of this contract that does
  carry a published format is the request's `recurringData.brandReferenceId`: `minLength` 1, `maxLength` 256, pattern
  `^\S+$` (published rule), on a member this simulator does not serve.

## Idempotency

No idempotency header. State-derived from the body field `orderId`, with a different policy on each endpoint:

| Endpoint          | Published rule                                                                                                                                                                                                                  | Conflict                                  | Simulator                                                                                     |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------|-----------------------------------------------------------------------------------------------|
| sale              | "Se um pagamento com o mesmo `orderId` já estiver `ACCEPTED` ou `CAPTURED`, retorna `409` sem criar um novo débito."                                                                                                            | `409 ORDER_ALREADY_APPROVED`, "this orderId already has an approved payment" | no claim; refuses only once an `ACCEPTED` or `CAPTURED` payment holds the `orderId` |
| pre-authorization | "com escopo por merchant (`documentId`) dentro do seu tenant"; "A ordem é reivindicada **antes** da chamada ao adquirente"; "Tentativas que terminaram em falha (`FAILED`/`ERROR`/`EXPIRED`) liberam o `orderId` para reuso"; "**Sem `orderId`** (ausente ou vazio) a idempotência fica desligada" | `409 ORDER_ALREADY_PREAUTHORIZED`         | claims before the acquirer; a failure releases the key                                        |
| Pix checkout      | "Não é tecnicamente uma chave de idempotência (a API não bloqueia 2 Pix com mesmo `orderId` hoje)"; "**Bloqueie duplo-clique no botão 'Pagar'** no front"                                                                        | none                                      | two checkouts give two `pixId`s; no keyed exchange recorded                                   |

Consequences of the sale's rule: a declined sale leaves the `orderId` free; two concurrent sales are both charged; an
`orderId` held by a **live reservation is refused**, because a reservation answers `ACCEPTED` and the rule names that
status (published rule); one whose hold was captured is refused for the same reason. A zero-dollar card validation at
`ACCEPTED` refuses a later sale under its `orderId` too, although it took no money: the rule is keyed on the status and
not on whether anything was charged (published rule). Nothing in the 189 pages admits a sale under an `orderId` a
reservation holds — 11 pages mention `orderId`, and the sale route's rule and its `409` description
("`orderId` já possui um pagamento aprovado") are the only statements of the condition. `MSG_GENERAL_ERROR`
on pre-authorization: "retente com o **mesmo `orderId`**".

The partial index `autra_payment_by_approved_order` (`V67`) is defined on exactly `native_status in ('ACCEPTED',
'CAPTURED')`, the predicate this route queries. Whether the planner uses it is not claimed: the two values are bound as
parameters rather than inlined, so the implication is not provable at plan time (inference).

The simulator's key declaration names `orderId` once, in the pre-authorization's role; sale rows appear on the Retries
screen under that role.

The `409` **carries no payment identity** (published example):

```json
{
  "errors": [
    {
      "code": "ORDER_ALREADY_APPROVED",
      "msg": "this orderId already has an approved payment"
    }
  ]
}
```

Autra publishes no lookup by `orderId`, so a merchant that loses a sale's response has no published way back except the
`acquiring.financial_transaction` webhook's `orderId`. Simulator: the refusal is recorded as `DUPLICATE_KEY_REFUSAL` with
`providerObjectId` set to the approved payment, so the Retries screen shows it; nothing on the wire does.

## Error Catalogue

Two shapes:

- acquiring (`components.schemas.Error`): `{"errors": [{"code": "BAD_REQUEST", "msg": "missing documentId"}]}`; `msg`,
  not `message`; `errors` is the only, required, top-level property. On the declined sale `200` it sits beside
  `paymentID`,
- OAuth: `{"error": "invalid clientSecret"}`.

| Code                                          | HTTP                | Published gloss                                                                     | Class              |
|-----------------------------------------------|---------------------|-------------------------------------------------------------------------------------|--------------------|
| `BAD_REQUEST`                                 | `400`               | "Campo obrigatório faltando ou inválido. Cheque o JSON."                            | validation         |
| `INVALID_JSON`                                | `400`               | capture                                                                             | validation         |
| `MISSING_DOCUMENT_ID`                         | `400`               | capture; lookup and listing, when neither the header nor the query names a document  | validation         |
| `MISSING_TRANSACTIONAL_TOKEN`                 | `400`               | capture                                                                             | validation         |
| `INVALID_AMOUNT`                              | `400` / `422`       | `400` when `amount <= 0`, `422` when above the pre-authorized value                 | validation / state |
| `INVALID_PAYMENT_ID`                          | `400`               | lookup, a path parameter that is not a UUID                                         | validation         |
| `TENANT_REQUIRED`                             | `400`               | lookup, listing; unreachable here, the tenant is always resolved                    | validation         |
| `INVALID_FROM`, `INVALID_TO`                  | `400`               | listing, a `from` or `to` that is no RFC3339 moment and no `YYYY-MM-DD` day         | validation         |
| `INVALID_RANGE`                               | `400`               | listing; no published condition, answered here to a `from` after its `to`           | validation         |
| `RANGE_TOO_LARGE`                             | `400`               | listing, "> 31 dias"                                                                | validation         |
| `INVALID_TYPE`                                | `400`               | listing, a `type` that is neither `SALE` nor `PREAUTH`                              | validation         |
| `INVALID_PAGE`, `INVALID_LIMIT`               | `400`               | listing, a `page` below 1 or a `limit` outside 1-200, or either that is no number    | validation         |
| `PAYMENT_LOOKUP_ERROR`                        | `500`               | lookup; unreachable here, the read touches one store and no acquirer                | transient          |
| `INSUFFICIENT_FUNDS`                          | `200` in `errors[]` | "Cartão sem saldo. Notificar cliente."                                              | decline            |
| `CARD_DECLINED`                               | `200` in `errors[]` | "Recusa genérica. Pode ser fraude, dados errados, banco bloqueando."                | decline            |
| `AUTHORIZER_REJECTED`                         | `422` in `errors[]` | "O emissor do cartão recusou. `msg` traz o código de resposta do emissor e o texto" | decline            |
| `TOKEN_NOT_AVAILABLE`                         | `422` in `errors[]` | "O token do cartão (`tokenData.slugToken`) deixou de ser elegível."                 | final, re-tokenize |
| `INVALID_CARD_BRAND`                          | `422` in `errors[]` | "Bandeira não suportada."                                                           | final              |
| `MSG_GENERAL_ERROR`                           | `422` in `errors[]` | "Erro técnico no processamento (a `msg` traz o detalhe, ex.: `502 : badGateway`)."  | transient          |
| `UNAUTHORIZED`                                | `422` in `errors[]` | "Falha técnica transitória de autenticação no processamento."                       | transient          |
| `ORDER_ALREADY_APPROVED`                      | `409`               | "`orderId` já foi aprovada antes. Não recobrar."                                    | idempotency        |
| `ORDER_ALREADY_PREAUTHORIZED`                 | `409`               | "o `orderId` já possui uma pré-autorização ativa"                                   | idempotency        |
| `INVALID_STATUS`                              | `422`               | "Pré-autorização não elegível: já capturada, já cancelada, falhou ou expirou."      | state              |
| `PAYMENT_NOT_FOUND`                           | `404`               | "`paymentId` não existe."; also another tenant's or another establishment's          | state              |
| `MERCHANT_NOT_FOUND`                          | `403` / `404` / `422` | see "Authentication and Transport"                                                | config             |
| `SPLIT_MERCHANT_NOT_FOUND`                    | `422`               | "Algum `documentId` em `paymentSplit.splits[]` não é merchant do tenant."           | config             |
| `ACQUIRING_NOT_READY`                         | `409`               | "Merchant não está pronto para adquirência"                                         | config             |
| `INVALID_DOCUMENT`                            | `422`               | "documento não é CPF/CNPJ válido"                                                   | validation         |
| `ACQUIRER_COMMUNICATION_ERROR`                | `500`               | "Falha ao falar com o adquirente. Retry com backoff."                               | transient          |
| `AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE` | `422`               | void                                                                                | state              |
| `CANT_REFUND_TRANSACTION_FROM_TODAY`          | `422`               | void                                                                                | state, retry later |
| `ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD`        | `422`               | void                                                                                | final              |
| `CARD_BRAND_NOT_SUPPORTED`                    | `422`               | void                                                                                | final              |
| `ORIGINAL_CYCLE_NOT_ACCEPTED`                 | `422`               | void                                                                                | final              |
| `INTERNAL_ERROR`                              | `500`               | "Falha interna." (published example, the error table on each of the 14 subscription pages); answered here to a failure nothing on the way to the operation answered (inference) | transient |

`INSUFFICIENT_FUNDS` at `200` in the guide is the decline the payments schema routes through `AUTHORIZER_REJECTED` at
`422` with `msg` `51 - Not sufficient funds`.

A `5xx` is published on two namespaces, `/v1/acquiring` and `/v1/banking`, and nowhere else (published rule). Of the
189 reference pages `autra.readme.io/llms.txt` enumerates, 39 declare a `5xx` in an OpenAPI `responses` map; those 39
pages carry 40 declarations, 20 of which give the `5xx` a body, always `application/json` `$ref` to
`components.schemas.Error`, on 19 pages. The statuses are `500` (26), `502` (7) and `503` (7), and no `504` and no
`5XX` wildcard appears. 23 of the 40 are under `/v1/acquiring` (`500` 19, `502` 3, `503` 1), 18 of them with a body and
5 with none at all — the three `3ds` routes at `500`, and `capture` and `void` at `502`. The other 17 are under
`/v1/banking`.

`INTERNAL_ERROR` is the code this simulator answers with for a failure nothing planned for under `/v1/acquiring`, and
the evidence splits three ways:

- Published rule: 14 acquiring operations — every `subscription-plans` and `subscriptions` route — declare a `500`
  whose OpenAPI `description` is the bare string `INTERNAL_ERROR`, with a body `$ref` to `components.schemas.Error`.
- Published example: the error table in the prose of those same 14 pages carries the row
  `` | `INTERNAL_ERROR` | 500 | Falha interna. | Tente de novo; persistindo, fale com o suporte com o horário da
  chamada. | `` (28 rows across the 14 pages). "Falha interna." is therefore the vendor's own text for this code on
  this namespace, and this simulator sends it verbatim rather than a sentence of its own.
- Inference: that the code covers a failure nothing planned for on an acquiring operation that declares no `5xx` of its
  own. `INTERNAL_ERROR` appears as a `"code"` **value** on 0 of the 189 pages — only ever as a response `description`
  or in prose — and `components.schemas.Error`'s `code` is a bare `{"type": "string"}` with no enum, const, example or
  default. The reason to choose it: of the acquiring `5xx` descriptions, only three name a failure of Autra itself
  rather than of an acquirer, and the other two are tied to one operation each — `PAYMENT_LOOKUP_ERROR` (reading one
  payment) and `CHARGE_NOT_SENT` (a subscription charge Autra never dispatched). `INTERNAL_ERROR` is the only one that
  names no operation. The seven that name the acquirer are `POST /v1/acquiring/payments` and the three `3ds` routes at
  `500`, `capture` and `void` at `502`, and `fees/simulate` at `502`.

The strings "Erro interno ou na Autra" and "Erro na Autra" are **not** acquiring text: they are the `description` of
bodyless `500`s on `/v1/banking/pix/automatic/*` (two pages each), which name no code at all. No acquiring `5xx`
description contains "Autra". A gloss borrowed from another namespace is the thing this build's own rule about asking
a module with the address forbids, so it is not used here.

The token route declares no `5xx` at all and publishes a different shape, the single `error` string, and the hosted
card page answers html, so neither is answered in the acquiring envelope and both get a bodyless `5xx`.

Two independent sweeps of all 189 pages agree on every count above, including the 0.

Issuer detail travels **inside `msg`**: `NN - text`. Published on the sale and pre-authorization: `51 - Not sufficient
funds`, `59 - Suspected fraud`, `05 - Do not honor`, `54 - Expired card`, `46 - Identification required`, `19 - Re-enter
transaction`, `61 - Exceeds withdrawal limit`. "`19` e `46` valem uma nova tentativa pelo portador" (same card, not
`retryWithAnotherSource`).

Scenario decline codes: `INSUFFICIENT_FUNDS` (vendor `msg` `insufficient funds`), `CARD_DECLINED` (**`msg` of the
simulator's own**; none published), `AUTHORIZER_REJECTED`, `TOKEN_NOT_AVAILABLE`, `INVALID_CARD_BRAND`,
`MSG_GENERAL_ERROR`, `UNAUTHORIZED`, and the five void codes. The list is per provider, not per operation, so a scenario
may name a code on an operation where Autra never publishes it.

A scenario names a code and no text, so the `msg` beside `AUTHORIZER_REJECTED` is an account setting,
`autra.authorizer-rejected-message`, default `05 - Do not honor` and shaped `NN - text`
(`AutraAuthorizerMessage.DEFAULT`, `AutraAuthorizerMessage.SHAPE`). To decline with the issuer detail Autra publishes
inside `msg`, such as `51 - Not sufficient funds`, set that setting for the run rather than naming it on the rule.

`DO_NOT_HONOR`, `PROCESSOR_ERROR`, `REJECTED` and "No pre authorization found for this transaction" do not exist in
Autra's documentation. An unknown payment on capture is `404 PAYMENT_NOT_FOUND`.

## Seeded Personas

One persona per intent, each seeding one stored card (`AutraPersonas`). Core rules are in `README.md`, "Seeded
Personas".

| Persona                                             | Intent                                      | Writes                            |
|-----------------------------------------------------|---------------------------------------------|-----------------------------------|
| `autra-br-card-payer-that-succeeds`                 | `a-saved-card-that-succeeds`                | `preauth-success-capture-success` |
| `autra-br-card-payer-that-is-declined`              | `a-saved-card-that-is-declined`             | `preauth-success-capture-decline` |
| `autra-br-card-payer-whose-capture-answer-is-lost`  | `a-saved-card-whose-capture-answer-is-lost` | `capture-accepted-response-lost`  |

| What it seeds                   | Value                                                                                                             |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `alias`                         | the persona's own name                                                                                            |
| `ownerId`                       | `a-payer-autra-holds-slugs-for` (`AutraPersonas.THE_OWNER`)                                                       |
| `type`                          | `card`                                                                                                            |
| `providerIds.token`             | `tokenData.slugToken`                                                                                             |
| `providerIds.paymentMethodId`   | `tokenData.slugStoredCard`                                                                                        |
| both slugs                      | minted by `TokenizeOnTheHostedPage.aSlug`, the same minting the hosted card page uses when the payer names no card of their own: 16 uppercase hexadecimal characters each. Autra names a stored card by that pair and this build stores both (`AutraPersonas.HOW_THE_SLUGS_ARE_MINTED`) |
| `brand` / `last4`               | `VISA` / `1111`, from the `AutraCardTokenizedResult` example                                                      |
| `expMonth` / `expYear`          | `12` / `2030`, from the same example                                                                              |
| `funding`                       | `unpublished`, because Autra publishes no sandbox and no list of test cards (`AutraPayerCards.AUTRA_PUBLISHES_NO_TEST_CARDS`) |

- Each persona mints a fresh pair of slugs, so seeding one does not claim the ids another needs.

## Open Items

| Gap or contradiction                                                                      | Recorded in                           | Simulator choice                                        |
|-------------------------------------------------------------------------------------------|---------------------------------------|---------------------------------------------------------|
| status of a body-carried sale decline: `200` or `422`                                      | "Where the Outcome Lives"             | `autra.sale-decline-status`                             |
| OAuth published twice                                                                     | "OAuth"                               | reference by default, two settings                      |
| `MERCHANT_NOT_FOUND` at `403`, `404`, `422`                                               | "Authentication and Transport"        | `autra.merchant-not-found-status`                       |
| pre-authorization `409` body unpublished                                                  | "Pre-Authorization"                   | the sale's envelope with its own code                   |
| capture body: schema `amount` only vs error table                                         | "Capture Pre-Authorization"           | all three fields required                               |
| approved sale published twice: the schema's ten properties, the guide example's four       | "Direct Sale With Tokenized Card"     | the schema's, less `brandReferenceId`                   |
| recurring series: the vendor publishes `recurringData` on the sale and `brandReferenceId` back on the first of them | "Direct Sale With Tokenized Card" | not served; the member is refused `400`, and `brandReferenceId` is answered by no read |
| `maskedCard` published as a bare string with no description, example, format or pattern    | "Payment Lookup"                      | never answered; no card number is kept on a payment     |
| `voidRetryable` is advisory and names `capturado` among its `false` cases                  | "Payment Lookup"                      | the published rule, and the void route stays wider      |
| the published view names no parameter of a provider route                                  | "Payment Listing"                     | a blind spot of the convention; the parameters are specified here |
| lookup `200` marks nothing required                                                       | "Payment Lookup"                      | an unanswerable property is absent, never empty         |
| whether the date window has a published maximum                                           | "Payment Listing"                     | published: 31 days, in two places, and not a bound of this simulator's |
| `status` filter omits `PRE_AUTHORIZED`, which one guide page publishes as a status          | "Payment Listing"                     | the seventeen alone; `PRE_AUTHORIZED` is `400 INVALID_STATUS` as the route publishes |
| which status an open pre-authorization answers: one guide page says `PRE_AUTHORIZED`, every schema says `ACCEPTED` | "Card Payment Status Expectations" | `ACCEPTED`, with `type` telling a hold from a sale |
| `INVALID_RANGE` has no published condition                                                | "Payment Listing"                     | a `from` after its `to`; one equal to it is an empty window |
| `to` default "agora" against a clock a tester can freeze                                   | "Payment Listing"                     | the present moment plus one second, echoed as the effective `to` |
| which of the header and `documentId` is read when a call sends both                        | "Payment Listing"                     | the header, which the vendor marks preferred            |
| `INVALID_DOCUMENT` means "not a valid CPF/CNPJ"                                            | "Payment Lookup"                      | a shape check only; no check digits, so a run's own merchants stay readable |
| `MERCHANT_NOT_FOUND` on the two reads                                                     | "Payment Lookup"                      | `404`, not `autra.merchant-not-found-status`            |
| timeline of a payment stored before the reads landed                                       | "Payment Lookup"                      | empty `events`; no step is invented                     |
| no format, length or example for `trackingNumber`, `nsu`, `rrn`, `authorizationCode`       | "The Acquirer's Transaction Identifier" | twelve digits, enforced as `^[0-9]{12}$` over the store |
| whether the acquirer reports a transaction identifier at all                                | "The Acquirer's Transaction Identifier" | `autra.acquirer-transaction-identifier`               |
| pre-authorization and capture `200` mark no field required; the sale's marks four           | "The Acquirer's Transaction Identifier" | answers every published field it has a value for      |
| void body: schema vs bodyless guide                                                       | "Void / Cancellation"                 | `autra.void-request-shape`                              |
| pre-authorization body: schema vs guide                                                   | "Pre-Authorization"                   | `autra.pre-authorization-request-shape`                 |
| `tokenize-card` `200` unpublished                                                         | "Card Session Creation"               | not served                                              |
| slug origin published three ways (initialize and tokenize-card; tokenize-card; SDK `onSuccess`) | "Card Session Creation"         | payer step mints both                                   |
| `AutraCardTokenizedResult` types unpublished                                              | "Card Session Creation"               | inferences                                              |
| `acquiring.financial_cycle` payload and resolved status unpublished                       | "Void / Cancellation"                 | never emitted                                           |
| Pix initialize `code` has one value                                                       | "PIX Initialize"                      | constant                                                |
| Pix `403` has no code                                                                     | "PIX Checkout"                        | `UNAUTHORIZED`                                          |
| Pix QR payload truncated                                                                  | "PIX Checkout"                        | prefix plus `pixId`                                     |
| cross-use of the two initialize tokens unpublished                                        | "PIX Initialize"                      | allowed                                                 |
| TTLs: bearer "1 hora", `transactionalToken` and QR "alguns minutos"; only the card session has a number (15 minutes) | — | bearer 1 hour, QR 5 minutes, session 15 minutes |
| code for a repeated full void unpublished                                                 | "Void / Cancellation"                 | `422 INVALID_STATUS`                                    |
| status vocabulary and `productType` are open                                              | "Card Payment Status Expectations"    | unknown values are `UNKNOWN`                            |
| `401` declared on one operation                                                           | "Authentication and Transport"        | `401` for every dead bearer                             |
| payout example has no top-level `status`                                                  | "Acquiring Webhooks"                  | follows the example                                     |
| what triggers settlement, and when                                                        | "PIX Checkout"                        | `settle` follow-up, tester-triggered                    |
| whether an unpaid Pix reaching `EXPIRED` triggers a webhook, and with which event type and payload | "Acquiring Webhooks" | clock expiry: none; `ABANDONED` payer step: `acquiring.financial_transaction`, `EXPIRED` |
| slug preference published both ways                                                      | "Card Session Creation"               | `slugToken`                                             |
| no status vocabulary for a card session                                                   | "Card Session Creation"               | `PENDING`, `TOKENIZED`, `EXPIRED`                       |
| how the card page hands `AutraCardTokenizedResult` to the SDK is unpublished              | "Hosted Card Page"                    | printed on the page                                     |
| a sessionless `transactionalToken` has no published refusal                               | "Direct Sale With Tokenized Card"     | `400 BAD_REQUEST` (sale, pre-authorization), `403` (Pix)|
| `CARD_DECLINED` `msg` unpublished                                                         | "Error Catalogue"                     | simulator's sentence                                    |
| no code for an address autra does not serve or a method one of its addresses does not answer | "Error Catalogue"                     | `text/plain` of the simulator's own, never `errors[]`   |
| no `5xx` on the token route or the hosted card page                                       | "Error Catalogue"                     | a bodyless `5xx` there; `500 INTERNAL_ERROR` on `/v1/acquiring` |

Sandbox. Six published places say one exists: "Introdução" tables "| Sandbox | Testes e homologação |" with "Base de
dados própria", "Credenciais independentes", "Webhooks separados" and no base-URL column; "Segurança": "Manter ambientes
de sandbox e production com allowlists distintas"; "Configuração": "Sandbox e Production possuem chaves e credenciais
totalmente independentes"; `POST /v1/biometrics/sessions` requires `environment` `"production" | "sandbox"`. No sandbox
host is published: the OpenAPI declares only `https://api.autra.io` ("Production"); other hosts named are
`cards.autra.io`, `biometria.autra.io`, `app-api.autra.io`. The React Native Card SDK's "There is no separate sandbox —
validation runs against production (`api.autra.io` / `cards.autra.io`)" is true of that SDK. Acquiring publishes no
`environment` field; the configurable base URL is this project's. The three Postman links (Sandbox, Production,
Collection) all answer `404 DEPLOYMENT_NOT_FOUND` from `autra-site-beta.vercel.app`, a dead preview deployment; nothing
follows from them.
