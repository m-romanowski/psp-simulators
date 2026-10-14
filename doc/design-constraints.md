# Design Constraints

## Objective

These are the constraints the kernel places on every provider. Each exists because at least one provider contract breaks
an assumption shared code would otherwise make. Grow is not the baseline. Where two contracts take one rule
differently, both shapes are recorded. This file is the merge checklist for provider-neutral code: routing, filters,
request binding, response rendering, error handling, persistence, status mapping and fault injection.

## Constraints

### Outcome is not the HTTP status

Shared code must not derive success, failure or processing from the HTTP status of a provider response.

| Provider | Where the outcome is | Citation |
|---|---|---|
| Grow | body `status=1` or `status != 1` with `err`, always HTTP 200, even for credential failures. `status` is `1`, `"0"` or `0` | `grow.md`, "Authentication and Transport", "Invalid Credentials", "Response Envelope" |
| Azericard | the pair `ACTION`+`RC` in a signed body. A refusal is HTTP 200 with `ACTION=3` | `azericard.md`, "ACTION and RC", "Error and Fault Scenarios" |
| Autra | `status` or `errors` present in the body, on the same endpoint as success. Capture alone reads 2xx as success | `autra.md`, "Where the Outcome Lives", "Capture Pre-Authorization" |

### Authentication cannot run as a pre-routing filter

Provider authentication runs inside provider code, after the body is parsed and the operation is known.

| Provider | Why | Citation |
|---|---|---|
| Grow | credentials are multipart body fields, and differ per endpoint (`userId`+`pageCode`, `userId` only, or `pageCode` only). Whether credentials are checked before business rules also varies by operation | `grow.md`, "Authentication and Transport", "Invalid Credentials" |
| Azericard | `P_SIGN` covers operation-specific fields in a published order, with length prefixes. The algorithm is set per terminal. Four orders are published and two are the simulator's own. The operation is the body field `TRTYPE` | `azericard.md`, "Authentication, Signing and Transport", "Signature Field Order", "Shared Fields" |

### The operation is not derivable from method and path

Operation resolution belongs to the provider and may need the parsed body. Azericard's `POST /cgi-bin/cgi_link` carries
authorization, pre-authorization, completion, reversal and status, selected by `TRTYPE` `0`/`1`, `21`, `22`/`24` and
`90`. `POST /token/cgi_link` multiplexes card storage and stored-card payments (`azericard.md`, "Routes and Response
Formats").

### Provider response bodies are bytes, not shared-serialized DTOs

Each provider renders its own bytes. Shared code does not choose the content type, the field names or the serializer.

- Azericard serves XML under `text/html` for every operation except `TRTYPE=90`, which is JSON. The field names differ
  between the two (`intref` against `intRef`). A missing field gets an HTML page. Values must reach the merchant exactly
  as signed (`azericard.md`, "Routes and Response Formats", "Authentication, Signing and Transport").
- Grow answers JSON, and its callbacks are form-encoded (`grow.md`, "Authentication and Transport").

### No shared error rendering and no shared exception handling over provider routes

Provider routes have no common exception handler and no common error body. Each provider has its own error shapes, some more than one:

| Provider | Shape | Citation |
|---|---|---|
| Stripe | `error` with `type`, `code`, `message` | `stripe.md`, "Required Error Shape" |
| Grow | `status=0` + `err`, at HTTP 200. The JSON types of `status` and `err.id` vary | `grow.md`, "Response Envelope" |
| Autra (acquiring) | `errors[]` of `code`+`msg`. A decline adds `paymentID`. A `409` names no payment | `autra.md`, "Error Catalogue", "Idempotency" |
| Autra (OAuth) | a single `error` string | `autra.md`, "Error Catalogue" |
| Worldline | `errorId` + `errors[]` of `category`, `code`, `message`, `propertyName` | `worldline.md`, "Error Shape" |
| Azericard (signed) | an ordinary signed response: `ACTION=2` with an issuer `RC`, or `ACTION=3` with a negative `RC` | `azericard.md`, "ACTION and RC" |
| Azericard (missing field) | an HTML page with no `ACTION`, `RC` or signature | `azericard.md`, "Error and Fault Scenarios" |

Provider routes never return simulator-native payloads (`technical-ui-requirements.md`, "Route Separation").

A request that reaches no operation at all — an address inside a provider's namespace that this build does not serve, a
method one of its addresses does not answer, or a failure raised before the route — is not an exception to that rule.
The core may do two things and no more: recognise which provider's namespace an address belongs to, and ask that
provider. The provider decides the status, the media type, the field names and the bytes. An address no provider claims
may belong to a surface of this simulator, which is asked the same way and decides the same things for itself. The
split is the one in `kernel/spi/ProviderWireRoutes.java` (the namespaces a module claims, matched on segment
boundaries because stripe, autra and tranzila answer under `/v1`, and autra's `/tokenize` begins with azericard's
`/token`) and `kernel/spi/ProviderWireAnswer.java`.

`ProviderWireRoutes` refuses a namespace that lies inside an address this simulator serves itself, which is one list in
one place: `kernel/ThisSimulatorsOwnAddresses.java` names `/__simulator`, `/ui` and `/actuator`, and both the route
alias grammar and the control surface read it from there. A provider module naming `/__simulator` as a wire namespace
would be asked what that address answers — modules are asked before surfaces — and would hand a tester on the control
API a vendor envelope. A documented reservation is not that invariant; the constructor is.

A provider whose contract publishes no answer for an address it does not serve, or for a method one of its addresses
does not answer, says so, and the core writes this simulator's own `text/plain` diagnostic at the status nothing else
chose, naming the provider and what its contract publishes instead. That is not a shared error body: `text/plain` is a
media type no provider of this build answers a client with, so it cannot be read as any of the seven shapes. An
envelope in the vendor's field names, invented because the vendor publishes none, is indistinguishable from the
vendor's to every client that logs one, which is why it is not written.

### A failure of the platform a provider publishes nothing for is answered with no body (simulator decision)

- The status nothing else chose, no body, and no `Content-Type`. Every provider publishes nothing for a failure of its
  own platform except autra on `/v1/acquiring`, which is answered in its own envelope; every other namespace, autra's
  other namespaces among them, is answered this way.
- A bodyless `5xx` emits nothing the vendor never sends and can be read as no vendor envelope. `text/plain` is a content
  type none of those platforms ever sends, so a generated client dispatching on content type, or one with a strict
  `Accept`, would behave differently here than against the real thing.
- Vendors publish that shape (published rule): 5 of the 23 `5xx` declarations under autra's `/v1/acquiring` carry no
  body at all, `capture` and `void` among them at `502` (`autra.md`, "Error Catalogue").
- The reason is on the trail instead, in a row written before the client can read it back.
- `WireAnswer` refuses bytes handed over without a media type, so the bodyless answer is the only unlabelled one.

### One mechanism owns this fault per namespace

- The ERROR-dispatch filter owns it: `kernel/http/internal/AWireFaultIsAnsweredByWhoeverWasAskedFilter.java` asks
  `WhoeverWasAsked`, which asks the module whose namespace covers the address and then any surface that claims it.
- No `*ApiController` catches it, so one namespace answers one fault in one media type whatever layer the failure arose
  in. A controller catching `RuntimeException` would answer database, state-machine and mapping failures itself.
- The one exception a provider catches on this path is one it decided: a held delivery abandoned or timed out is an
  answer about the simulator's own scenario machinery, not a failure nothing planned for.

A module is asked with the address as well as the fault, because a contract may publish one shape on one of its
namespaces and nothing on another: autra publishes a `500` body on `/v1/acquiring` and no `5xx` at all on its token
route.

What each provider answers, and on what evidence:

| Provider | An address it does not serve | A method an address does not answer | A failure nothing else answered | Citation |
|---|---|---|---|---|
| Stripe | `404`, `error` with `type=invalid_request_error` and no `code` | the same `404`: `405` is outside the published status list | nothing published: `api_error` is a type and no code, message or body is published for a failure of the platform, so a bodyless `5xx` | `stripe.md`, "Authentication and Transport", "Required Error Shape" |
| Worldline | `404` `errorResponse`, no `errors.id` and no `errors.errorCode` | `405`, which the vendor instructs a caller to expect outside its list | nothing published: `500`, `502`, `503` and the `IO_ERROR` category are published, and no `errors.id` or `errors.errorCode` for a failure of the platform itself, so a bodyless `5xx` | `worldline.md`, "Error Shape" |
| Grow | nothing published | nothing published | nothing published: every documented answer is HTTP `200` with the outcome in the body, and no `5xx` appears anywhere, so a bodyless `5xx` | `grow.md`, "Routes", "Error Scenarios" |
| Autra | nothing published | nothing published | `500 application/json` `{"errors":[{"code":"INTERNAL_ERROR","msg":"Falha interna."}]}` on `/v1/acquiring`; nothing published on its token route or its hosted card page, so a bodyless `5xx` there | `autra.md`, "Error Catalogue" |
| Azericard | nothing published | nothing published | nothing published for a failure of the gateway itself: what the vendor does publish is HTTP `200` carrying either a signed refusal or an unsigned HTML page, and neither is an answer to a fault of the platform behind it, so a bodyless `5xx` | `azericard.md`, "Routes and Response Formats", "Error and Fault Scenarios" |

### The control API answers a failure nothing planned for in its own shape

- An address under `/__simulator` belongs to no provider, and a surface of this simulator claims it the way a module
  claims its namespaces (`kernel/http/ASurfaceOfThisSimulator.java`, `control/TheControlApiInItsOwnWords.java`).
- A failure nothing on the way to the operation answered is answered `500 application/json` with the one `message`
  field every other refusal of that API carries. Spring Boot's own `{"timestamp", "status", "error", "path"}` is a
  second shape on the same route, which a client cannot read against one contract.
- The message names no exception, no class, no stack frame, no SQL and no file path, and does not repeat what the
  failure said: a real provider does not leak the words of what is in front of it, and nor does this simulator.
- The surface publishes no answer for an address that API does not serve.

### A failure nothing answered leaves one row on the trail

- The filter that answers the failure records one `INBOUND_REQUEST` row under the provider that owns the address — the
  module whose namespace covers it, or the provider an address of the control API names — with the cause chain under
  `causes` (`kernel/audit/ActionTrail.java`, `kernel/audit/TheCausesOfAFailure.java`). The screens read the same chain
  for a `/ui` address.
- The row names the run the request named in its query, and `UNKNOWN` otherwise: an error dispatch cannot read a run
  out of a body nothing parsed, and the reserved run is the id this build already uses for a resolution that failed.
  `GET /__simulator/{provider}/audit?testRunId=UNKNOWN` reads those rows, and so do `/ui/audit` and `/ui/correlation`,
  the two screens whose whole subject is the trail of one namespace.
- The row is written before the answer reaches the wire, so a client that meets the `500` and reads the trail on the
  same connection finds it. The answer is written whatever the record did (`try`/`finally`), so a trail that is itself
  unreachable does not cost the client its answer.
- An address no provider and no surface owns leaves no row, because the trail is keyed by provider and a row filed
  under one the address never named is a row no tester of that provider asked for.
- A status nothing decided is not a refusal: `RefusedActionStatus.refuses` is the client errors alone, and a row of a
  control surface carrying `5xx` names no `RefusalSubject` and may not name one. A refusal is a decision this
  simulator published; `500` is the absence of one.
- The row names the method the client asked with, read off the dispatch that still had it
  (`THE_METHOD_THE_CLIENT_ASKED_WITH`, remembered as the request arrives). An error dispatch reports its own method, and
  no `jakarta.servlet.error.*` attribute carries the client's, so reading it off the error dispatch would file
  `POST /v1/acquiring/payments` as `GET /v1/acquiring/payments`, a published operation of the same provider in its own
  right. `RecordedRefusals`' invariants forbid that misattribution.
- The cause chain is redacted before storage, not on the way out: each cause keeps its exception's simple class name
  and what it said, with every fully-qualified type name collapsed to its simple name and everything a driver quotes
  back in brackets replaced by the one `[redacted]` marker this build uses everywhere
  (`kernel/audit/TheCausesOfAFailure.java`). The trail travels in run bundles and in bug-report bundles, and a
  diagnostic of this build names the field and never the value. The field that carries the chain is the row's fact bag,
  the same field a control action's facts are recorded in.
- An address no provider and no surface owns leaves no row at all, and the reason is in the server log, which under the
  container deployment means `docker logs` (`AnAddressNobodyClaimsIntegrationTest`, which pins that an `ERROR` entry
  there carries the cause chain of exactly this failure). The row has no key: the `provider` column is `not null`, and
  a reserved id is not a key a tester can read a row back through. Measured (live probe)
  `GET /__simulator/nobody/audit?testRunId=UNKNOWN` answers `404`
  `{"message":"unknown provider; this simulator knows [autra, azericard, grow, stripe, worldline]"}`, because every
  control route resolves `ProviderRegistry.find` before it reads, and the screens are offered only the names the
  registry holds (inference, `ui/CorrelationController.java`). The reserved run `UNKNOWN` is a different case and still
  applies wherever a row is written: a run is a namespace inside one provider's trail, and that provider is known.
- Nothing in this build calls `HttpServletResponse.sendError`, so a `5xx` at such an address is always a raised
  failure, and the container logs every one that reaches it. A filter or a handler that swallowed such a failure and
  set the status itself would take the only channel left, which is what the test above guards.
- No `@ExceptionHandler` of this build answers an exception of this build's own packages, and an architecture test
  refuses one in either spelling of that annotation
  (`ArchitectureRulesTest.an_exception_handler_answers_only_a_fault_raised_before_a_route_decided`). Spring reads the
  exceptions a handler answers off the annotation where it names any, and off the handler's own parameters where it
  names none, so a rule reading only the attribute measures one of the two ways the same handler can be written; the
  rule reads both, and was proved red against a reinstated handler written each way. A refusal this simulator decided
  is answered and recorded by the route that decided it, which still holds the run the row belongs to, the subject it
  names and the words it was answered in, and a handler holds none of the three. An exception that escapes such a route
  is therefore a defect and reaches the mechanism above as a `5xx` carrying a row, rather than a client error carrying
  nothing a tester can read afterwards.
- That rule is a floor over one mechanism, not over every way a fault of this build could answer a client error away
  from its route, so the mechanisms beside it carry their own: nothing declares a `@ControllerAdvice`
  (`nothing_declares_a_controller_advice`), and no exception class outside the screens' own package carries a
  `@ResponseStatus` (`no_fault_outside_the_screens_carries_a_status_of_its_own`), which would answer a status from
  wherever the exception was raised, in Spring Boot's envelope and with nothing on the trail. The screens are the one
  surface excepted, because an address they do not serve is answered by a page of theirs
  (`ui/UnknownContributedScreenException.java`), and their own error attributes are the one `HandlerExceptionResolver`
  of this build (`ui/WhatAScreenCouldNotDo.java`, a `DefaultErrorAttributes`).

### A body the control API could not read is a refusal it decided, and it leaves one row

- The fault belongs to the handler that answers it and to no route: argument resolution raises it before the route body
  runs, so the handler is the last place holding the request. It is an `@ExceptionHandler` on the controller that owns
  the routes, because nothing here is a `@ControllerAdvice` (`control/SimulatorControlController.java`).
- The answer is `400` in this API's own shape, `message` beside the `properties` the reading stopped at, and not Spring
  Boot's `{"timestamp", "status", "error", "path"}`: a suite parsing what this API refuses in reads one contract. Both
  shapes a refusal of this API is answered in are published for every route of it, as an `anyOf` over `ControlError`
  and `ControlRefusal` (`control.json`), so either is a shape a suite already reads.
- The words are this build's own and never the reader's. A body nothing parsed is where a credential a caller mistyped
  is still sitting, and what the reader said about it is not published or stored anywhere; the fields alone are, read
  off the path the failure the reader raised carries and nothing else of it, and spelled the way every other refusal of
  this API spells a property, as `accounts[0].configuration` (`control/ABodyTheControlApiCouldNotRead.java`).
- The properties are absent from the row and the answer where that failure named no field — a body whose first bytes
  stopped the reader, and a route handed no body at all, which this API refuses as the one decision it is — rather than
  present and empty: an empty list would tell a tester this API named a field and lost it. Absent from the answer means
  the `message`-only shape `ControlError`, which is the shape every refusal of this API that names no property is
  answered in; a refusal that names one is a `ControlRefusal`, where `properties` is required and never empty.
- The row is a refusal and not a failure: `400` is a status this simulator decided. Its subject is `NO_OBJECT_WAS_NAMED`
  over the body itself, because nothing read the body and no object of the run was ever named in it.
- The row is filed under the run the request named in its query where the route it was heading to is one that reads its
  run from its query, and under `UNKNOWN` otherwise, the decision `WhoeverWasAsked` already makes for a failure nothing
  answered. Never under the default run this API applies to a body it read, and never off a query parameter the route
  does not read: a row under either would show a tester reading it a call that may have named another run where the
  route would have read one. Of the routes of this API that read a body, exactly one reads its run from its query,
  `POST /scenarios/{scenarioId}/copy`; the rest name it in the body, which is what nothing parsed. The route in hand is
  the `HandlerMethod` Spring offers the handler, and whether it reads a run there is read off its own
  `@RequestParam`s rather than off a list of addresses beside them, in either spelling of that annotation
  (`control/ABodyTheControlApiCouldNotRead.java`).
- An address naming a provider this build does not serve answers `404` in this API's words and leaves no row, as every
  route of it does, because the trail is keyed by provider.
- A method a route does not answer, and a `Content-Type` no route of this API reads, are different faults, each decided
  before any body is read, and each keeps the shape the table below records for it. A `Content-Type` is what a request
  says it carries: no reader of this API is chosen for one it does not read, so no reader stopped anywhere, nothing of
  the body was looked at, and the handler above holds no fields to name and no words of its own to say. Measured (live
  probe) `PUT /__simulator/grow/config -H 'Content-Type: text/plain'` answers `415`
  `{"timestamp","status","error","path"}` and leaves no row
  (`RefusedActionsIntegrationTest.a_content_type_no_route_of_this_api_reads_is_turned_away_before_any_reader_and_leaves_none`).

### The control API publishes two error shapes, and a client parsing them needs both (simulator decision)

- A failure nothing planned for answers `{"message": ...}`, the one field every refusal of that API carries.
- An address the control API does not serve, a method one of its addresses does not answer, and a `Content-Type` no
  route of it reads, answer Spring Boot's own `{"timestamp", "status", "error", "path"}` to a client asking for JSON,
  and Spring Boot's Whitelabel HTML page to a browser `Accept` (live probe) — three shapes on that namespace, not two,
  and three faults answered in the two that are not this API's own. The surface publishes no answer for those three
  faults, and a CI suite that parses control-API errors reads all three shapes until it does. Measured (live probe)
  `404` 276 bytes, `405` 285 bytes and `415` 289 bytes of Whitelabel page, and the envelope to a JSON `Accept` at each.
- A body a reader of that API was handed and could not parse is not among them: it answers this API's own shape, the
  `message` every refusal carries and the `properties` the reading stopped at where it named one (above), because the
  handler that answers it still holds the request and the route that would have decided it never ran. The three faults
  above are decided on what a request says it is — its address, its method, the type of its body — before any reader of
  this API is chosen, and this one is decided inside a reader of this API that was handed the bytes and stopped.

An address no module's namespace covers is read through the paths above it, deepest first, by
`WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp`. The root is never one of them, because every namespace of
every provider lies under `/` and reading an address through it would take the screens.

- Exactly one provider publishes under that path: that provider is asked, as if the address were inside its namespace.
  This guesses nothing — every address of that provider is reached through that path and no other provider publishes
  anything under it, so a client that mistyped the segment below it could have meant no one else. This build has five
  such paths, above three of its wire namespaces: `/api` is grow's alone, `/tokenize` is autra's, and
  `/hostedtokenization`, `/hostedtokenization/tokenization` and `/hostedtokenization/tokenization/form` are
  worldline's, the last because the published form path ends in a separator and the address without one is covered by
  nothing.
- More than one provider publishes under it: the core answers in this simulator's own voice, in
  `WhatAProviderAnswersOffItsRoutes.thePathMoreThanOneProviderPublishesUnder`, at the status nothing else chose, naming
  the contested path, every provider publishing under it and the namespaces each one answers under it. `/v1` is the
  only such path in this build — autra publishes two namespaces under it and stripe seven — so the address is inside
  nobody's namespace and nothing in it says which vendor it was aimed at. No provider is picked, for the reason an
  invented envelope is not written: a stripe envelope for an address autra could as easily have been meant by is
  indistinguishable from stripe's to every client that logs it.
- No provider publishes under any path above it, and this simulator answers the address itself: it keeps the answer it
  had. Addresses under `/ui` are claimed by no provider on purpose and keep the page a screen's failure is drawn on,
  and `/__simulator` and `/actuator` keep theirs. `/` is the fourth such address and the only one outside those three
  namespaces: it is the front door, and it redirects to `/ui`. A `GET` of it raises no fault, but a method it does not
  answer and a failure raised before routing both do — measured (live probe) `POST /` answers `405`, and `GET /` with
  the database stopped answers `500`, because the route-alias filter reads the database for it. `/` is therefore
  inside the question this section answers, and it is not nobody's.
- `/` is named once, in `kernel/ThisSimulatorsOwnAddresses.THE_ADDRESS_A_TESTER_TYPES`, which
  `platform/BareHostNameConfiguration.java` reads rather than declaring its own copy, so the address that redirects
  and the address this mechanism leaves alone cannot drift apart. It is **not** in `RESERVED_FOR_THIS_SIMULATOR`:
  that list is what refuses a provider's wire namespace and what the route-alias grammar refuses an alias *target*
  inside, and a route alias may still be bound from the bare root of a host and is still resolved there (inference,
  `RouteAliasGrammar.keyOf` refuses no path, and only the alias filter's early return skips a reserved namespace).
- No provider publishes under any path above it and no part of this simulator answers it: nobody was asked, so there is
  nobody whose words it could answer in, and it answers with no body at all — see the section below.

### An address nobody claims answers in nobody's words (simulator decision)

- The status nothing else chose, no body, and no `Content-Type`, at every fault that reaches it:
  `WhoeverWasAsked.nobodyClaims` is the last thing asked, after the modules and after the surfaces, and the address is
  nobody's when `ThisSimulatorsOwnAddresses.thisSimulatorAnswersIt` is false for it and no `ASurfaceOfThisSimulator`
  owns it.
- One address answers one shape. A `404` here and a `5xx` here are the same bytes, and so is what an API client and a
  browser receive, because a fidelity claim proved over one representation proves a fraction of itself. That holds for
  an address nobody claims and is **not** a rule of this application: at the contested path it does not hold, and the
  table below records the asymmetry as the two rows it is.
- The core writes its own `text/plain` diagnostic only where it has somebody to name — the provider whose contract
  publishes nothing, or the contested path and every provider publishing under it. At an address nobody claims there is
  nobody to name, and a diagnostic with no fact in it is prose, so none is written.
- All three faults reach such an address and all three are answered the same way. Measured (live probe): `GET /zzz`
  and `OPTIONS /zzz` answer `404`, and `TRACE /zzz` answers `405`, because the connector refuses that method before
  any filter of this application runs (tomcat's `allowTrace` is left at its default). No route of this build is mapped
  at such an address, so no handler of this application ever raises the `405` itself.
- The wire says nothing and no row can be filed, so the mechanism writes one `INFO` line of the server log naming the
  method, the address and the status, for every fault it answers at such an address
  (`WhoeverWasAsked.WHAT_THE_SERVER_LOG_SAYS`). Without it a mistyped address leaves nothing anywhere a tester can
  read: a `5xx` at such an address logs a stack trace carrying neither the path nor the method.
- That line is the only logger in the main sources, and it is the only channel left: the wire is deliberately silent,
  the trail has no key (below), and no control route or screen publishes the fault. A `text/plain` body would have said
  the same thing on the wire and is refused for the reason above.
- The method on that line is the method the client asked with for every fault a filter of this application saw, which
  is every `404` and every `5xx`. For a method the connector refused there is nothing that remembers it — no
  `jakarta.servlet.error.*` attribute carries the method — so the line reports the dispatch's own, and the address and
  the status are what identify the request. Claiming a method there would be the misattribution the trail's own
  invariants exist to prevent.
- The predicate reads the raw request URI out of `jakarta.servlet.error.request_uri`, while tomcat routes on the
  normalised path, so a path parameter on a reserved prefix segment reads as nobody's. Measured (live probe)
  `/__simulator;x=1/nothingHere` and `/ui;x=1/nothingHere` answer a bodyless `404`, while
  `/__simulator/nothingHere;x=1` and `/ui/nothingHere;x=1` answer spring's envelope.
- The surfaces half of the predicate changes no answer in this build, because the one surface owns `/__simulator`,
  which is reserved already. It states the rule — a surface that was asked and published nothing for a fault decided
  that — rather than resting on the registry holding one surface inside a reserved namespace.
- Nothing of this simulator's own reaches the wire, and no row can be filed: see "A failure nothing answered leaves one
  row on the trail" above for where the reason is instead, and why there is no key to file it under.

### Every shape this application publishes when nothing answered the request

The full set, not a subset. Every row of it is either probed against a running simulator or pinned by a test of this
build, and the rows marked `live probe` are the ones measured rather than decided. A client or a CI suite that parses
what this application answers off its routes reads exactly these. "Status nothing else chose" is the status the container
had already set when the mechanism was asked.

A provider's own envelope may carry headers beside its body, such as a payer page's policy and `Cache-Control:
no-store`. Their names are tokens and their values one line; `Content-Type` and `Content-Length` are never among them,
because the body and its media type decide them, nor `Set-Cookie` and `Transfer-Encoding`, which no answer off a route
decides.

| Where it was asked | Fault | What it answers | Evidence |
|---|---|---|---|
| a provider's namespace | one its contract publishes an answer for | that provider's own envelope: the seven shapes above | published rule |
| a provider's namespace | an address it does not serve, or a method one of its addresses does not answer, where its contract publishes nothing for that fault | this simulator's `text/plain` diagnostic naming the provider and what its contract publishes instead | simulator decision |
| a provider's namespace | a failure nothing else answered, **where its contract publishes nothing for a failure of its own platform** | no body, no `Content-Type`, at the status nothing else chose | simulator decision |
| `/v1/acquiring` | a failure nothing else answered | autra's own `500 application/json` `{"errors":[{"code":"INTERNAL_ERROR",…}]}`, because autra publishes one there — the row above is conditional and this is the condition failing. Measured with the database stopped: an unserved address of this namespace answers that envelope, while one of grow's and one of azericard's answer nothing | published rule |
| a path exactly one provider publishes under | any | whatever that provider's namespace would answer, by the three rows above | simulator decision |
| a path more than one provider publishes under (`/v1`) | an address nobody serves, a method nobody answers | this simulator's `text/plain` diagnostic naming the contested path and every provider publishing under it | simulator decision |
| a path more than one provider publishes under (`/v1`) | a failure nothing else answered | no body, no `Content-Type`. This and the row above are the asymmetry: two statuses at one address answer two shapes there, because there are providers to name for one fault and nothing to be faithful to for the other | simulator decision |
| an address nobody claims | any of the three | no body, no `Content-Type`, at the status nothing else chose, and one `INFO` line of the server log naming method, address and status | simulator decision |
| `/__simulator` | a failure nothing planned for | `500 application/json` `{"message": …}` | simulator decision |
| `/__simulator`, `/actuator` | an address not served | spring's `{"timestamp","status","error","path"}` to a client asking for JSON, and spring's Whitelabel HTML page to a browser `Accept`. The surface publishes no answer for this fault | live probe |
| `/__simulator`, `/actuator` | a method one of its addresses does not answer | the same two shapes: `PUT /__simulator/grow/audit` and `PUT /actuator/health` both answer `405`, the envelope to JSON and the Whitelabel page (285 bytes) to a browser | live probe |
| `/__simulator` | a `Content-Type` no route of it reads | the same two shapes at `415`: the envelope to JSON and the Whitelabel page (289 bytes) to a browser, and no row, because no reader of the API was chosen and nothing of the body was read. The surface publishes no answer for this fault | live probe |
| `/ui` | an address not served, browser `Accept` | the `not-served` page inside the screens' shell, carrying the cause chain where nothing decided the status (`ui/TheScreenAnErrorIsDrawnOn.java`, `ui/WhatAScreenCouldNotDo.java`) | simulator decision |
| `/ui` | a method one of its addresses does not answer, browser `Accept` | `302` with `Location` back to the address a refresh lands on, and an empty body — not the `not-served` page. `POST`, `PUT` and `TRACE` on `/ui/dashboard` all answer that way (`ui/TheScreenAnErrorIsDrawnOn.java`, `ui/WhereARefreshLands.java`) | live probe |
| `/ui` | a `Content-Type` no route reads | not a fault there: no screen reads a body through a message converter — every screen route binds query or form parameters — so the type a request declares is never consulted and the screen answers its page. Measured (live probe) `POST /ui/configuration/account` with `Content-Type: text/plain` answers `200` and the page | live probe |
| `/ui` | any, JSON `Accept` | spring's envelope widened by `message`, `provider`, `testRunId` and `theRunItWasIn`, and by `causes` where nothing decided the status. The screens are read in a browser, and this is what a client asking them for JSON gets. No screen publishes an answer for a JSON `Accept` | live probe |
| `/` | a method it does not answer | spring's envelope to a client asking for JSON and the Whitelabel page to a browser: `POST`, `PUT`, `DELETE`, `PATCH` and `TRACE` all answer `405` | live probe |
| `/` | a failure raised before routing | the same two shapes, at `500` | live probe |

A method a route does answer, a body a provider refused, a body the control API could not read, and every other
decision this simulator published are not in this table: those are answers, and this table is what is published where
nothing answered.

### Native to normalized status mapping is not a static table

The normalized status depends on the native status, the scenario and the monetary state, so it cannot be a lookup map in
configuration.

- Worldline `CREATED`, `AUTHORIZATION_REQUESTED` and `CAPTURE_REQUESTED` map to `PROCESSING` or `UNKNOWN` depending on
  the scenario, and a `CAPTURED` payment maps by its refunded total (`worldline.md`, "Status Mapping").
- Autra publishes its status list and its webhook `productType` as open ("a lista pode crescer") (`autra.md`, "Card
  Payment Status Expectations").

### Money is not a scalar

Keep the exact lexical form of an amount from end to end. A numeric column plus a formatter is not enough.

- Azericard's MAC prefixes each value with its length, so `10.00` and `10.0` sign differently. `AMOUNT` varies in form
  (`10`, `1.00`, `11.48`) and is echoed as received (`azericard.md`, "Authentication, Signing and Transport", "Shared
  Fields").
- Autra sends decimal reais, never integer cents (`autra.md`, "Amounts, Currencies and Identifiers"). Worldline sends
  minor units (`worldline.md`, "Create Payment With Stored Token"). The same business amount therefore has two wire
  forms.
- Control API amounts are strings that keep their lexical form, with the currency beside them (`README.md`, "Common
  Simulator Control API").

A derived amount keeps the scale its arithmetic produced and is always written in plain notation, never as `1E+3`.

### A shared HTTP client outlives the context that made it

- Destroy the outbound `HttpClient` explicitly. Its threads outlive a closed application context.
- A request the client rejects before sending anything (a bad target, or a header the transport reserves) is reported
  the same way as a failed connection.
- The simulator's configuration validator checks the read timeout, because `HttpRequest.timeout` accepts zero.
  `connectTimeout` rejects zero on its own.
- `HttpRequest.timeout` does not limit the streaming of the body. A chunked body that never ends has to be bounded some
  other way.

### A response preview is cut in bytes, not in characters

The stored merchant response is bounded in bytes, so a cut can split a character into U+FFFD. The body arrives as a
list of buffers, and each buffer gets only the room the earlier ones left.

### A provider object has no single opaque id

Shared code must not assume that one string names an object. Identity **may** be composite.

| Provider | Identity | Citation |
|---|---|---|
| Azericard | `(TERMINAL, ORDER)` before the answer. Completion and reversal use `(ORDER, RRN, INT_REF, TERMINAL)`. Status uses `ORDER`+`TERMINAL`. `providerObjectId` is `INT_REF`, which only the response carries | `azericard.md`, "Transaction Status", "Sales Completion", "Reversal" |
| Grow | `transactionId`+`transactionToken`, and `processId`+`processToken`. A refund has an id and no token | `grow.md`, "Get Transaction Info", "Refund Transaction", "Get Payment Process Info" |
| Stripe | one drawn id. Here the shared column **is** the identity | `stripe.md`, "PaymentIntents" |

The control read `state/{providerObjectId}` and `SimulatorProvider.state` take one string. Where the provider did not
draw that string, it is a lookup convenience, not the identity.

An Azericard merchant who lost the authorization response holds only `TERMINAL` and `ORDER`. It recovers the payment
with the `TRTYPE=90` status inquiry. The inquiry finds the payment by `ORDER` within the terminal and a 24-hour window,
answers `RRN` and `INT_REF`, and refuses when more than one payment matches (`provider/azericard/LookUpStatus.java`,
`AzericardTimestamp.STATUS_WINDOW`). The pair is not unique across days: the last six digits of `ORDER` are unique per
terminal per day (`azericard.md`, "Identifier Spaces").

A provider must not expand a preset for a journey its identity cannot complete without saying so in the answer.

### Webhook delivery is claimed by a conditional update, not by a transaction

No transaction is held across an outbound call. The delivery protocol is a sequence of conditional updates:

- A single conditional `UPDATE` claims a delivery, and writes a refused attempt's outcome, not a marker. A second drainer
  skips the delivery. A crash leaves it retried or visibly exhausted.
- The settle is conditional on the attempt count, so a slower attempt cannot overwrite a higher count.
- The settle is conditional on the delivery state, so a late attempt cannot overwrite `DELIVERED` with `PENDING`.
- A manual retry re-arms the delivery in the same statement that claims it.
- Deleting a delivery cascades to its attempts through the foreign key. Attempts are never deleted separately.
- Due-ness depends on each run's clock, so the poll scans every unfinished delivery. It reads only the columns it needs,
  never the bodies, and reads each run's clock once per drain. A pinned clock holds deliveries pending indefinitely.

### Idempotency is not a shared header interceptor

Duplicate handling differs by provider in its key, its storage and its outcome, so it lives in provider code.

| Provider | Mechanism | Citation |
|---|---|---|
| Stripe | `Idempotency-Key` header. A differing payload is refused | `stripe.md`, "Idempotency" |
| Worldline | idempotence-key header, spelled two ways. A differing payload is replayed. `409` means the first request is still running | `worldline.md`, "Authentication and Transport" |
| Autra | body `orderId`, with three policies: a sale `409`, a pre-authorization `409` that is released when the attempt fails, and no idempotency on PIX. A released key is neither new nor a duplicate | `autra.md`, "Idempotency" |
| Grow | `transactionUniqueIdentifier` gives a duplicate error. `transactionGroupIdentifier` is a duplicate only against a successful payment. Neither is a replay | `grow.md`, "Create Stored-Card Transaction" |
| Azericard | derived from state: the last six digits of `ORDER`, per terminal, per day | `azericard.md`, "Identifier Spaces" |

The per-provider key table is in `testing-support-features.md`, "Retry, Idempotency and Correlation Inspector".

### A keyed exchange is recorded by the kernel, never by a provider's own insert

A provider hands `KeyedExchangeLog` a `KeyedObservation`: the request it fingerprints, a `KeyedOutcome` (it ran the
operation, or it refused or replayed) and the fields of its answer. It never hands a fingerprint, a verdict of a request
that ran, or a body text, so no provider can number, judge or redact an exchange on its own.

- Numbering. A row takes the next free `seq` under its key and is inserted with `on conflict (<primary key>) do
  nothing`. `SequencedTable` writes that statement, so the conflict target is always the whole primary key and any other
  violated constraint still fails the request. A row that lost its number is inserted again, up to
  `SequencedRows.ATTEMPTS` times, and then the request fails. Autra's payment timeline is numbered by the same helper.
- Verdict. The verdict of a request that ran the operation is decided by the insert from the rows it follows: a first
  request only where no request that ran precedes it, otherwise the repeat its key's role names (`REPEAT_ADMITTED` for a
  duplicate check, `CORRELATED_RETRY` for a correlation key, a first request for a true idempotency key, where it means
  the provider forgot the key). The reason: a read separate from the insert lets concurrent requests all read "first".
  A key value is scoped to what the provider's duplicate rule scopes, so azericard's `ORDER` value is the UTC day of the
  trace a request holds, a space, and the trace: a trace reused on another day is a first request, and a payer page
  paid on a later day is recorded under the day its trace was held. A screen asked for the value a client sent finds
  the scoped value that ends in it (`KeyedExchange.isKeyedBy`).
- Order. Rows are numbered in the order they are recorded. A refusal keeps its verdict wherever it lands, so a
  duplicate refusal may precede the request that ran: azericard's payer page claims a trace and records nothing under
  it, and a claim is visible to a duplicate before its claimant records. A replay and a conflict answer from a stored
  answer, and stripe and worldline record the request that ran before storing its answer. When that record fails, the
  key is still completed, so a later replay or conflict has no recorded request before it; `KeyedExchange` then names
  that state (`THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED`) and every surface shows the key with it. `KeyedExchange` refuses
  only an exchange whose earliest request that ran is not a first request, which the insert cannot produce.
- Body. The stored body is the answer's fields after the provider's `ProviderKeyMechanisms.withholding`, the redaction
  its `OUTBOUND_RESPONSE` rows apply, written as JSON of each field to its values, and its `content_type` is
  `application/json`, which describes that JSON and not the wire. `EveryKeyedExchangeIsWithheldTest` reads every
  module's `SENSITIVE_RESPONSE_FIELDS` and fails when that module's withholding keeps any of them. A row with
  `answer_kept` false carries no status, content type or body, and the screens say its answer was given and not kept
  rather than that nothing was answered; every row recorded before V86 is such a row.
- Completion. Stripe and worldline complete a claimed key with its answer even when recording its exchange fails, so a
  retry is replayed rather than refused as still running. A completion that fails too is kept as suppressed on the
  recording failure, which is the one thrown.
- Fingerprint. `RequestFingerprint` is HMAC-SHA256 keyed with the run's `FingerprintKey`: 256 bits from `IdGenerator`,
  minted on first use and kept in `simulator_fingerprint_key`, which registers no run; forgetting a run removes its key,
  and a provider reset keeps it, because the other providers of the run still compare against it. The key is never
  displayed, exported or logged, so a fingerprint compares two requests of one run and can be checked against no
  guessed input. The reason: an unkeyed digest of a card number, a CVV or an account secret is a dictionary oracle.
  A fingerprint stored before V86 is rehashed with a salt V86 discards: equal ones stay equal, none can be recomputed,
  and none matches a fingerprint minted under a run's key, so a repeat of a key claimed before V86 compares as different
  parameters.

### Webhook delivery is not "2xx means delivered"

Acknowledgement belongs to the provider and may need a separate inbound call. Grow can hold a delivery until
`approveTransaction`, keeps a missing acknowledgement visible and retryable, and resends up to 5 more times (`grow.md`,
"Approve Transaction Callback"). The common model allows this (`README.md`, "Common Webhook and Callback Delivery").

### Provider account configuration is one transaction, opened explicitly

- The core's identity rows and the provider's rows are written in one transaction. The transaction is opened in code, so
  the class stays `final`.
- The call **merges by account identity**. A named account is created or replaced whole. An unnamed account is left
  alone. An empty `accounts` changes nothing. A named account's callback targets are replaced along with it.
- The test-run register is written **before** the provider applies the configuration, outside the transaction. A
  refused configuration has still named a run.
- If one request names the same account id twice, the whole request is refused. The simulator never picks a winner.
- A credential already held by an account the call leaves in place is refused by the provider, which names the property.
  The per-run unique index is the defence, and the check is the diagnostic.

### The core's account identity is a label the caller chooses, not a merchant id

The core keys an account by an opaque label, unique per provider and test run. It uses the label only to key rows, echo
the configured list, and name a webhook target's account. A provider with a merchant id maps it to the label in its own
table.

Azericard carries `TERMINAL`, not a merchant (`azericard.md`, "Shared Fields"). Grow carries `userId`/`pageCode`
(`grow.md`, "Authentication and Transport"). Worldline's `{merchantId}` path value is mapped by Worldline itself.
Test-run resolution by credential never reads the label (`README.md`, "Common Simulator Control API").

### A provider owns its account schema, the core owns the identity, the transaction and the refusal

- A provider stores its configuration in its own table, with typed columns and check constraints. The core's row holds
  only the identity and the test run. There is no untyped map in a core column. The closed-set settings
  (`GrowAcknowledgementMode`, `GrowSettlementCeiling`, `AzericardDuplicateCode`, and so on) are each a check constraint.
- The core opens the transaction and calls the provider once to store its rows inside it. Provider stores join that
  transaction through default propagation, a single `DataSource` and the same thread.
- The kernel names no provider class. Parsed configuration crosses the boundary as a kernel-declared type.
- Credentials are matched by a query on the provider's indexed columns, not by scanning bindings.
- `WebhookTarget` carries only the account identity and the URL. Signing material stays in the provider's table.
- The clock change is applied before the provider can refuse, and it is not part of the transaction.

### Provider configuration is refused before anything is written, in the call whose result is the only thing stored

`SimulatorProvider.parse` returns a `ProviderConfigurationPlan` that holds the write, the keys read, and the longest
story key each account could spell. `ProviderConfigurationPlan.store` is the only way to the write. The core refuses
unknown keys and unstorable story keys before `store` runs, so a rejected configuration leaves the existing accounts
alone.

Only the provider can spell its story key, so it answers with the longest key a value could produce, not with a list of
fields. That check is a diagnostic, not the defence: `StoryKey` refuses an unstorable key wherever one arrives (non-blank,
bounded, no NUL, no unpaired surrogate, a leading space name).

### Webhook payloads are not uniformly JSON

Encoding, nesting and parameter repetition belong to the provider. Grow and Azericard callbacks are form-encoded. Grow
nests keys (`data[customFields][cField1]`). Azericard needs a negative test with duplicate parameters, so the writer must
be able to repeat a key (`grow.md`, "Merchant Callback Contract", `azericard.md`, "Merchant Callback", "Error and Fault
Scenarios"). Stripe and Autra send JSON.

### A callback's bytes are not rendered once per delivery

Delivery storage may keep a rendered payload, but the provider must be free to render each attempt. Stripe's
`Stripe-Signature` signs `t.payload`, where `t` is the time of the attempt, and a stale timestamp must be reachable only
by configuration (`stripe.md`, "Webhooks"). The time between attempts has no upper limit in simulator time.

`AttemptRendering.renderFor(delivery, instant)` answers either nothing, which sends the stored payload, or a fresh
render. Grow (unsigned) and Azericard (whose signature covers only payment fields) register nothing. Stripe registers
`StripeWebhookRendering`, which re-signs each attempt and reaches a stale timestamp only through `webhookTimestampAge`.
A replay is an attempt and is rendered again. Worldline sends no callbacks.

### Rows of one exchange share a timestamp, so order is a column

A pinned clock gives every row of one exchange the same `created_at`. Reads that must reproduce a sequence order by a
monotonic arrival column, never by timestamp.

### An operation the object refused does not move the object's `updatedAt`

`updatedAt` is the time of the object's last change, not of the last request that addressed it. `LedgerClaim` probes,
claims, then records, and records only on `APPLIED`. A refused operation writes nothing: no row and no timestamp. A
repeated refused call shows up in the audit, not on the object (`README.md`, "State response rules").

### Outbound delivery is one POST, and a verification call would be a second operation

The transport is one `post` that returns a status and a bounded preview. Every provider built needs only that. No
contract here reads the merchant's body to decide anything. The GET-and-echo endpoint verification is Worldline
Connect's, not Direct's, and Direct publishes dedicated test endpoints instead (`worldline.md`, "Webhooks"). If a
provider needs such a call, it is a second port operation that compares the whole body. The preview cannot do this,
because it is bounded.

### Transport faults act on already-rendered, already-signed bytes

Fault injection runs after provider rendering and signing, so it can truncate or corrupt a valid signed body, or drop
the connection. The operation ran and its state is written. Only the delivery is lost (`README.md`, "Scenario
rules", `azericard.md`, "Error and Fault Scenarios").

### A fault answered instead of the operation runs no operation and writes no provider state

A fault can also answer **in place of** the operation. No ledger row, status change or object is written. The inbound
and outbound audit rows and the story's new state are still written. Both placements exist: a lost response
(`capture-accepted-response-lost`) and a request that never took effect (`psp-5xx-then-success`) are different facts.

- `TransportFault.answeredWithoutTheOperation` decides whether a fault can take this placement. Only `SERVER_ERROR` can.
  The other faults transform rendered bytes or delay them. A rule that asks for anything else is refused when written.
- The status is always 500–599 and is never chosen by the provider.
- `delivered.transportFault` on the audit row carries the fault and its placement. `INSTEAD_OF_THE_OPERATION` can only be
  stored with `SERVER_ERROR` and an `UNKNOWN` outcome.

### A 5xx a transport fault answers is the gateway's, and it says so in bytes

- A `SERVER_ERROR` answers a short non-empty sentence under `Content-Type: text/plain; charset=utf-8`
  (`kernel/fault/GatewayAnswer.java`). The kernel writes the same bytes for every provider and both placements.
- It is not a provider error envelope. That would claim the provider answered. The body must not be empty, because
  `stripe-java` throws a `NullPointerException` on an empty 5xx body instead of an `ApiException`.
- The body never says which placement happened. A real edge 5xx cannot tell the caller whether the provider processed
  the request.
- This sets a floor, not a universal rule. Worldline's own `502` is an `errors[]` body that a Worldline operation renders
  (`worldline.md`, "Error Shape"). The content type tells the two apart.
- A `DELAYED_RESPONSE` cut short answers the same way, under `text/plain; charset=utf-8`: `RESET_WHILE_HELD` (`503`,
  the run was reset), `STOPPING_WHILE_HELD` (`503`, the simulator is stopping) or `UNDELIVERED` (`504`, the request
  deadline passed first), in each provider controller (`provider/grow/GrowLightApiController.java`).
- Every `FaultedDelivery` names a non-null content type. `Connection: close` is sent only when the body is incomplete.

### A multi-step scenario is keyed on a flow the provider names, not on the object a request addresses

The provider supplies the story key, and the kernel treats it as opaque. One Grow payment addresses a `processId` and a
`transactionId` (`grow.md`, "Minimum API Surface"). An Azericard payment is addressed by different field sets
(`azericard.md`, "Transaction Status", "Sales Completion").

| | Grow | Azericard |
|---|---|---|
| flow key | the process id, drawn by the simulator | `(TERMINAL, ORDER)`, chosen by the merchant |
| drawn before the decision | yes, `CreatePaymentProcess` claims the id first | no. `INT_REF` is claimed only on the path that stores an object |
| key form | `GrowStoryKey.ofProcess` | `AzericardStoryKey.ofPayment`: `payment:` + the terminal's length + terminal + separator + order. The length keeps the pair injective |

- The key must be resolvable **before** the operation, because a pre-effect fault decides without running it.
- Drawing an id is not running the operation. A drawn id is **claimed** under a uniqueness constraint, never probed
  first, and is never released.
- The key is also the lock. One flow contends with itself. Two flows contend only when a story is keyed on the test run.
- A key names its space. `StoryKey` refuses a key without `<space>` + `StoryKey.NAMES_THE_SPACE`. A space name is
  lower-case letters, digits and hyphens, starting with a letter. The story table enforces the same rule. The kernel
  cannot check that the space is the right one.
- A rule declares `keyedOn`: the flow (the default) or the whole test run. A column holds this, not a sentinel inside the
  key. Steps of one story must declare the same key.
- A decision locks only the keys it reads, in an order the key fixes.

### A rule matches a request or a delivery, and nothing matches both

- Actions come in two sealed sub-interfaces: request actions and delivery actions. Each decision point switches
  exhaustively over its own kind.
- A delivery carries a merchant, a provider object and, for some providers, an event type. A delivery rule that narrows
  on an order, customer, payment method, amount, currency, operation or header is refused when written, and the
  database refuses it too.
- The shaping rule's id is stored on the delivery, so every audit row the delivery produces carries it. A withheld
  delivery is written to the trail when it is withheld.
- Out of order needs two deliveries. It is reached by holding the earlier one back. A provider with one callback per
  object says it has nothing to reorder.
- Three delivery actions are configurable as scenarios: `DELIVERY_DELAYED`, `DELIVERY_DUPLICATED` and
  `DELIVERY_DROPPED`. Out of order is composed from `DELIVERY_DELAYED`, not a fourth action (`README.md`, "Common
  Webhook and Callback Delivery").

### Monetary state is a ledger, not a status plus totals

An object can be in more than one monetary state at once. The control API carries an ordered operation list and derives
the balances from it (`README.md`, "Common Simulator Control API").

- A Worldline capture of 2000 of 5000 with `isFinal=false` leaves 3000 both capturable and cancellable. A full refund
  lands on status `8`, `85` or `7`, so the status alone does not give the totals (`worldline.md`, "Maintenance State
  Rules").
- A row names one object: the object whose ledger holds it. `MonetaryOperation.createdObjectId` names a second object
  the operation **created**, and is otherwise null. Grow and Stripe refunds name the refund. An Azericard
  `/token/cgi_link` registration names the stored card `TOKEN`. Azericard completion and reversal, and every Autra and
  Worldline operation, name nothing.
- `ProviderObjectState` refuses an operation that names its own object, and the operation tables make that row
  unstorable.

### The hosted-session lifecycle is not uniformly create, url, complete

Only Grow, Worldline and Autra return a hosted URL from a server-to-server create (`grow.md`, "Create Hosted Payment
Process", `worldline.md`, "Create Hosted Tokenization", `autra.md`, "Card Session Creation"). Azericard publishes no URL.
Its card storage is a signed browser post to `/token/cgi_link` that carries 3-D Secure browser data, with no published
TTL (`azericard.md`, "Card Storage and Stored-Card Payment"). Stripe completes a SetupIntent through a client-secret flow
(`stripe.md`, "SetupIntents").

### The address of a payer page is built where it is answered, and kept nowhere

A provider that answers a hosted URL builds it from the token on the row and `simulator.public-base-url`: grow from
`hosted_link`, autra from `page_token`, worldline from the `hostedTokenizationId` that is the row's own
`providerObjectId`. No row keeps the built address: V81 drops grow's `hosted_page_url` and autra's `hosted_card_url`,
because V49 and V72 had already taken out of those columns the token each address is built from, which left a value only
the row's own mapper and its insert touched. A derived address on a row is also the one shape
`verifyNoPayerUrlAmongRelatedObjects` cannot see, since it reaches a screen as no constant of this build, so it is a
shape this build does not have rather than one the gate registers.

Grow's create answers `url` and autra's answers `hostedCardUrl`, each computed where it is answered from the values
the row keeps, which is what makes the two columns derived.

### Test-run resolution can require body parsing

Rule 3 of resolution (credential binding) reads body fields: Grow's `userId`/`pageCode` and Azericard's `TERMINAL`. An
ambiguous resolution is `UNKNOWN`, so resolution must see every candidate before it chooses (`README.md`, "Common
Simulator Control API").

### A route alias routes before the body is read, and supplies a candidate rather than a verdict

- Rule 2 (route alias) is decided from the host and path, in a filter, before a handler is chosen. The filter rewrites
  the path and records the URL that was called.
- The filter adds one `TestRunCandidates` at the `ROUTE_ALIAS` rung. `TestRunResolution.of` still decides after the body
  is parsed: the header beats the alias, and the alias beats credentials.
- The filter never authenticates. The provider checks an aliased request against the accounts of that run.
- An alias `to` may not point inside `/__simulator`, `/ui` or `/actuator`, and requests to those paths are never looked
  up.
- A bound URL is unique across the whole simulator (the `simulator_route_alias` primary key). A second claim gets `422`.
  Only `DELETE /__simulator/{provider}/config/route-aliases` releases one.

### A diagnostic names the property or field, never the value

Validation errors, log lines and messages name the property path, the field or the rule. They never include the value.
This applies to configuration validation, request binding, signature verification and audit rendering. Spring's
`Errors.rejectValue` keeps the value (`rejected value [...]`), so configuration validation uses `Errors.reject` with the
path in the message.

This rule covers diagnostics, not form state. An input field may show back what its reader typed.

A redacted entry keeps its key, and one fixed-length marker per value sent. Key matching ignores case.

The reasons: a value that is redacted is redacted the same way wherever that happens, secrets arrive as values that
something validates, and the configuration surface carries credentials (`technical-ui-requirements.md`, "Security
Model", "Non-Functional Requirements", `README.md`, "Common Simulator Control API").

Withholding a secret is the other answer, not a lapse in that consistency. A run bundle carries no secret and names the
field it left out in `PortableAccount.secretsItDoesNotCarry`, so there is nothing in it to redact
(`technical-ui-requirements.md`, "Non-Functional Requirements").

### A time source is recognized by its shape, not by its name

A determinism rule matches any call that reads the host clock, however it is spelled (`now(zone)`, `Clock.system`,
`new Date()`, `System.nanoTime()`). It does not use a list of method names.

### Every provider declares what its answers are withheld under, and the address of its payer page is the floor

Each provider module declares `SENSITIVE_RESPONSE_FIELDS` in one class of its own package, and every class of that
module which writes an `OUTBOUND_RESPONSE` row reads that declaration.
`verifyEveryProviderWithholdsWhatItAnswers` reads both out of the compiled classes, so a provider added with no
declaration, or with a class that answers around it, fails the build rather than looking protected. A row is read as
carrying the `AuditDirection` named where it is built and in the methods of its own class that reach that builder, so
each row-building method whose direction no reaching method names fails the build too, because a row this gate can read
no direction for would drop its class out of the set the gate examines. A module's declaration is read from what one of
its own classes stores into its own field of that name: a store into another class's field declares nothing, and a
class storing into its own declaration twice is refused rather than read at one of the two stores.

What the gate reads is the read, not the reach. Whether the body a class hands the trail is one the declaration's keys
could match is a fact about that one site: grow's payer page answers a row keyed `normalizedStatus` while all five of
grow's declared keys are under `data.`, azericard declares no key at all so both of its answering classes can match
nothing, and autra's card page and worldline's tokenization checkout each hand the trail an empty body, so at five of
the nine sites the declaration is applied and can match nothing. The unit test of each row states what it carries; the gate states only
that the module's own declaration is what every answering class reads.

The comparison follows each module's own redactor rather than one rule for all, and the redactor is read of each
declaration separately, because one module hands its two declarations to different ones. `Redaction.redact` matches a
whole key, so for autra, azericard, grow and worldline a published name is met by a declared key equal to it or by a
declared path ending in it — which is how grow's `data.url` covers the `url` its flattened body is keyed under, and
why a bracketed key would not. Stripe's own `withholding` reads the last segment of every key, so stripe's responses
are compared that way. The key a name is finally published under is composed at run time and is invisible here, so a declaration
that can no longer match the body beside it is caught by the unit test that reads the row, never by this gate.

The floor the declaration stands on is the address of the payer page the provider declares as
`ThePayerStep.APageThisSimulatorServes`: for grow and autra it carries the one-time token a payer completes a payment
with (azericard's and worldline's do not), and its name says in its own words that it is an address. A published name
whose own words include `url`, `uri`, `link`, `href` or `location` is either withheld or registered in
`TrailWithholdingConvention.THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR` against the one provider that publishes it,
with the reason that address is no secret of that provider; the registration is the decision, and the gate reports only
that nobody made it.

The floor is that narrow on purpose. A name reading as a token or a secret names an account setting, an environment
variable, a persona or a database column across the provider modules far more often than it names a field an answer
carries, so a floor over those words would be a guess with a registry larger than the rule.

Azericard withholds nothing and is registered in
`TrailWithholdingConvention.THE_PAGES_WHOSE_ADDRESS_NOTHING_ANSWERS`: a payer reaches its page because the merchant
posts their browser to the gateway address it configured, so azericard answers no address for it, and what a merchant
may not forge is the `P_SIGN` it computes.

Worldline publishes the same page address twice, as `hostedTokenizationUrl` and as the deprecated-but-populated
`partialRedirectUrl`, and withholds both. The page asks for no token and is reached by `hostedTokenizationId` alone,
which the same trail row keeps as the object it names. So for worldline the withholding keeps the address out of the
body and nothing more; the floor holds the module to withholding every field that carries the address, not to the
address being secret. Neither address reaches the object screen either: `WorldlineProvider.relatedObjectsOfSession`
names the merchant, the deadline and the card a payer stored, and `verifyNoPayerUrlAmongRelatedObjects` holds every
module to that, because this declaration governs the body a module hands the trail while the object screen prints
`relatedObjects` verbatim.

A response header is outside this mechanism: the declaration is applied to the body a module hands the trail, and a
row's headers are what the delivery carried. Worldline's rows keep its `Location`, naming the created payment's own
resource; grow's payer page rows keep no header at all.

### Every provider declares what its deliveries are withheld under, and composing a body is what owes a declaration

The body this simulator delivers to a tester's own endpoint is kept as a row of the same trail, under
`OUTBOUND_WEBHOOK_ATTEMPT`, `OUTBOUND_WEBHOOK_DROPPED` or `OUTBOUND_WEBHOOK_NOT_SENT`. Every one of those rows is
written from `WebhookPayload.sanitizedBody` and never from the wire bytes, so the withheld body the provider handed
over is what a tester reads. That is read of each method that writes such a row, and of the methods of its own class
which reach it, at the same granularity the direction of a row is read at, so one writer in a class reading the
withheld body admits no other writer in it. Each module that mints such a body declares `SENSITIVE_DELIVERY_FIELDS` in one class of
its own package, and every class of it that composes one reads that declaration;
`verifyEveryProviderWithholdsWhatItAnswers` reads both out of the compiled classes.

A minting class is found by its construction of `WebhookPayload`, not by the direction of a row: the row is kept by the
kernel, so no provider class names a delivery direction at all. A class that mints one out of a body another class
already withheld, by reading it back through `sanitizedBody`, composes no field of its own and is held to no
declaration — `StripeWebhookRendering`, which re-signs the event stripe's scheduling class already withheld, is the
one such class. Composing, not minting, is also what obliges a module to withhold a field or register that it has
none, because a module that composes nothing would be registering a decision about fields it writes none of.

Autra, azericard, grow and stripe mint a delivery. Worldline mints none and declares none.
Between them they withhold five fields: grow's `data[processToken]`, `data[transactionToken]`, `data[cardToken]` and
`data[customFields][cField1]`, and stripe's `data.object.client_secret`.

What a delivery declaration withholds is matched the way the redactor that module hands `SENSITIVE_DELIVERY_FIELDS` to
matches a key, read of that field and not of the response declaration beside it. Autra, azericard, grow and stripe all
hand it to `Redaction.redact`, so a recorded name is met by a declared key equal to it or by a declared path ending in
that segment, and a bracketed key is a key of its own here as it is in the response direction. Stripe is the module this
separates: its responses go to its own `withholding` methods and its deliveries to `Redaction.redact`, so the two
directions of one module are compared differently. A delivery declaration handed to redactors that do not match a key
the same way fails the build rather than being read against one of them.

Autra and azericard declare an empty set and are registered in
`TrailWithholdingConvention.THE_DELIVERIES_WHOSE_BODY_HOLDS_NOTHING_TO_WITHHOLD` with the reason every field of that
body is one this build keeps readable; the registration is the decision, and the gate reports only that nobody made
it. For an empty declaration the application is carried by that gate alone: an empty declaration redacts nothing, so
no row such a class writes differs from the row it would write applying nothing, and no unit test of a row can tell
the two apart.

A field a module delivers readable on purpose is recorded in
`TrailWithholdingConvention.THE_FIELDS_A_MODULE_DELIVERS_IN_THE_CLEAR`: autra's `userDocument`, azericard's `TOKEN`
and azericard's `P_SIGN`. Nothing is required to be recorded there, so a recording is a claim rather than a
permission, and it is read back as one: a class of that module which mints a delivery names that constant, and the
module's delivery declaration withholds it in no delivery. The key a body is finally published under may be composed
at run time and reaches the gate as no constant, so what is read back is the name a minting class holds, and the unit
test of the row is what states the key.

Azericard's `TOKEN` is the 28-character handle this simulator mints for a stored card, which is that card's own
`ProviderObjectIdentity` and the handle `AzericardObjectActions.A_STORED_CARD_IS_NOT_A_TRANSACTION` tells a tester to
pay with. Withholding it from the delivery row would remove nothing and cost a tester the one row they can read it
from: this module's request declaration names the card data and never the `TOKEN`, so the token-route call a tester
makes with it keeps it in full one row later, and `AzericardProvider.relatedObjectsOf` hands it to the object screen,
which redacts nothing. Grow withholds its own card token in both directions, under `CARD_TOKEN` and
`data[cardToken]`; azericard keeps the handle readable because addressing a stored card by its id is a capability of
this product. Azericard's `P_SIGN` is the MAC this gateway computes with its own key, which the holder of the public
key can recompute, and comparing the MAC it sent with the MAC it was answered is the work a tester does against this
contract.

### A screen withholds, it does not redact

Redaction happens on write, and reads return what was stored. A screen never redacts again and never reverses a
redaction. It decides only what is visible, and it applies that uniformly. A field's name and how many times it was sent
are always shown. Its value is shown only in the one record that was opened, and only when asked for by name. The trail
keeps `pageField[...]` personal data in clear.

### A vocabulary's absences are the provider's, not this build's

A provider vocabulary may declare a fact absent only if the provider never carries it. It may not declare a fact absent
because the operations built so far do not carry it. Azericard's only absence is `CUSTOMER_ID`, which is true of
the provider. `ProviderVocabulary` refuses an operation that carries a fact the vocabulary says is never carried. The
other direction is not checked.

### A timeline is one store's order, and the other stores hang off it

The audit trail, the delivery queue, provider objects and keyed exchanges each number their rows on their own sequence.
Timestamps collide under a pinned clock. So a timeline is the audit trail in `seq` order. The ledger and the callbacks
appear in panels beside it, each in its own store's order. A screen must not invent a position for a row that its store
never ordered. A queued callback has no trail row, and a status change has no row at all.

### A provider's request field names and its response field names are two vocabularies

Request keys (`GrowFields`) and response keys (`GrowResponseFields`) are separate constant sets that never reference
each other. A renamed request field must not silently rename a response key. Envelope keys, contract-check expectations
and database column names keep their literals.
