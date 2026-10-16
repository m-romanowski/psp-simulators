# Technical and UI Requirements

The `/ui` screens are for testers, developers and support engineers. They seed data, watch live flows, finish hosted
pages, trigger callbacks and reproduce scenarios without any change to the client application.

## Route Separation

- The simulator has three route groups: provider-compatible APIs (`/v1/payment_intents`,
  `/api/light/server/1.0/...`, `/v2/{merchantId}/payments`, ...), the control API (`/__simulator/{provider}/...`) and
  the UI (`/ui/...`).
- Provider routes never return simulator-native payloads. Control and UI routes may.
- A UI action calls the same application service as the matching control route.
- A provider state change appears in the audit at once.
- The UI shows the surface that created each object, as its `origin`: `PROVIDER_API`, `CONTROL_API`, `UI` or
  `SCHEDULED_JOB`.

## Security Model

- There is no login for the UI or the control API. The simulator binds only to configured internal interfaces, behind a
  VPN, a private network or a local firewall, and never faces the public internet.
- It never logs a full PAN, CVV/CVC, bearer token, private key, PSP signing secret or one-time token.
- Provider authentication behaves as each provider's contract says.
- Permissive authentication, which accepts invalid or absent credentials, is a per-provider, test-only mode. It is off by
  default and shown in the UI. Only Worldline has one: `worldline.authentication` =
  `THE_SIGNATURE_IS_NOT_VERIFIED` skips the HMAC check but still requires an `apiKey` that names the account.
- The `X-Simulator-Test-Run-Id` header only chooses a test run. It weakens no credential or signature check.
- An invalid credential or an invalid signature is produced by sending one, because no scenario action answers either
  (`ScenarioGrammar.theDeclarableActionTypes`). The refusal is a trail row whose `refusal` says what it refused
  instead of naming an object (`RefusalSubject.NoObjectWasNamed`, `README.md`, "Audit trail"). An outbound callback's
  signature is corrupted or omitted by an account setting: Stripe `webhookSignature`
  (`StripeWebhookSigning.EMISSION_SETTING`) and Azericard `azericard.callback-signature`
  (`AzericardCallbackSignature`).
- The UI and the control API take no token and have no setting for one
  (`kernel/startup/SimulatorProperties.java`). Isolation is the network's job, so a shared environment that is not
  network-isolated enough has nothing in the application to fall back on.

## State Model

Durable state is kept for: provider accounts and merchant configurations; test runs; customers and owners; payment
method fixtures and provider-native tokens; hosted card and tokenization sessions; payment intents, processes and
orders; authorizations and holds; captures and completions; cancellations, reversals and refunds; provider-native status
lookup records; scheduled state transitions; webhook subscriptions, jobs and delivery attempts; the sanitized request
and response audit.

Every object carries: its simulator id, provider, test run, account, provider-native id or token, normalized status and
native status. Amounts are exact lexical strings with their currency. It also carries the operation list its balances
are derived from, the consuming application's correlation fields, its created and updated timestamps, and its `origin`.

No object carries `retryWithAnotherSource`, the indicator that a cardholder may retry with a different payment source,
which is a different thing from the same operation being retryable. No state and no payload this simulator renders
holds the field (`ProviderObjectState`, `ControlPayloads`), so what a decline says about retrying is read from the
provider's own vocabulary, which each provider file records.

## Test Run Isolation

- `testRunId` is optional. A request without one uses `default`.
- The UI lets a tester choose the active test run.
- Fixtures, scenarios, objects, audit and webhook jobs are all scoped to a test run.
- Resetting one test run leaves the others untouched. Tests in different runs never affect each other.
- Manual testing uses a named run, such as `manual-{tester}-{date}`. A CI test creates a unique run id and resets it
  before it starts.

## Scenario Model

A scenario is a deterministic rule. It decides how a provider request is answered, or how state moves on asynchronously.
A rule carries its provider and test run, and matches on the account, order, object id, customer, payment method id,
amount, currency, operation, event type and an explicit header (`DeclaredMatch`, `ScenarioGrammar.matchOf`). A payment
method is matched by `match.paymentMethodId` alone: a card token and a masked fixture card alias are no match facts.

Its action is one of `FIXED_SUCCESS`, `TERMINAL_FAILURE`, `TRANSPORT_FAULT`, `TRANSPORT_FAULT_INSTEAD_OF_OPERATION`,
`PROCESSING_THEN_TERMINAL`, `DELIVERY_DELAYED`, `DELIVERY_DUPLICATED` and `DELIVERY_DROPPED`
(`ScenarioGrammar.theDeclarableActionTypes`), and what each one does is in `README.md`, "Common Simulator Control API".
A validation or configuration error, an invalid credential or signature, and a duplicate or idempotency conflict come
only from a request that really is invalid, never from a rule.

## E2E Modes and Data Retention

- Automated tests use the provider APIs and the control API, with no manual step. Every scenario can be reproduced from
  control-API JSON. Cleanup is deterministic. Callback timing is controlled without long sleeps. Idempotent replay and
  lost-response scenarios can be tested.
- Live manual testing: a client drives a hosted session and a tester finishes it in the UI. No screen polls, so a new
  session appears on the next load of `/ui/objects`. The tester needs no database ids. The UI shows enough native ids
  to match the client's logs, and shows exactly what the completion will produce. Ambiguous correlation is flagged
  `UNKNOWN` and needs an explicit choice.
- Data is kept until an explicit reset, for one run or for all runs. Audit records are redacted. The UI shows data age
  and object counts. Fixtures and scenarios export and import as JSON.

## UI Principles

- The first screen is a dashboard, not a landing page.
- Screens are built for scanning, filtering and fast operation. Dense tables are preferred for audit and state.
- Every state-changing action is explicit and needs a `confirm`.
- Nothing is hidden: scenarios, provider state and scheduled actions are all visible.
- Provider-native terms and normalized terms appear side by side.
- The screens work on desktop and tablet. Mobile is secondary.
- For any audited request, the UI shows which scenario matched and why.

## Screen Rules

- The UI covers what the API covers and no more. A screen action exists only where a call already does.
- No screen asks for a credential. Where a request would be signed with a key the simulator does not hold, the screen
  says the payment does not travel the signed wire.
- A screen finishes a hosted page by the id it printed, and no screen source or template writes a payer page's
  address itself. `verifyNoPayerUrlOnAScreen` enforces that floor, and reads no list of provider vocabulary: it takes
  the address of the payer page each provider declares as `ThePayerStep.APageThisSimulatorServes` out of the compiled
  declaration, and refuses a template of this UI or a `dev.marcinromanowski.pspsim.ui` source that hardcodes that address. A provider
  that serves a page as `text/html` and declares none fails the same gate, so the set of addresses cannot fall
  behind the providers without an edit in core; simulator decision. Two places in the shipped product print a payer
  page's route and are inside the floor: the sentence
  `ThePayerStep.APageThisSimulatorServes.asATesterReadsIt` returns, which `OriginationForm.wayOf` puts in the
  origination wizard, and the correlation table's route of a recorded hit.
- What a payer follows is the route plus the one-time link token. That token is withheld from every trail a tester
  reads, under `SENSITIVE_RESPONSE_FIELDS` per provider, which
  `verifyEveryProviderWithholdsWhatItAnswers` requires every provider to declare, to apply at every row it answers a
  client with, and to name the address of the page it serves a payer in. That is a rule about the trail, not about
  every screen: a provider's `relatedObjects` is prose it hands the object screen, which redacts nothing, so a
  provider that put a working payer address there would print it in full. `verifyNoPayerUrlAmongRelatedObjects`
  refuses that in the build rather than at run time: it reads each declared payer page's address out of the compiled
  `ThePayerStep.APageThisSimulatorServes`, walks the compiled calls out of every place a provider module builds a
  `ProviderObjectState`, and fails when a method reached that way carries a string literal that is the address of
  the page that same module declares, naming the whole way from the state to the literal. Every string constant of
  that closure is read, the name a map entry is given among them, because telling a key constant from a value
  constant needs an operand stack the gate does not simulate, and a module naming its own payer page route anywhere
  in that closure is refused whichever side of the `put` it lands on. A module naming another module's route is read
  and is no offence: that path is answered for another provider's object and carries no token this module mints. It
  reads no value of any run, so the client free text these maps answer back cannot fail it:
  autra's `description`, `orderId` and `nickname`, worldline's `merchantReference` and `merchantCustomerId` and
  azericard's `order` are pattern-validated nowhere, and a run-time refusal over any of them would answer a 500 on
  the control read, the object screen, the timeline, the correlation screen and the inspector for an object the
  provider accepted. The build is where this belongs because the address worldline computes at run time walked past
  `verifyNoPayerUrlOnAScreen`, which sweeps sources and templates only, and because a provider added later is held by
  compiling rather than by an edit to a list. Five shapes are missed on purpose, each named in the gate's own refusal,
  which counts them from the length of the list it joins them out of: an address a module computes into a static field
  at class initialisation, one it stores on a persisted row and hands over from there, one assembled from literals too
  short to name a whole path segment of a declared route, a value the address floor admits (a route counts only where
  nothing but an address leads up to it and the value ends where the route does, at its end or at a `?` or `#`, which
  keeps a refusal message, a quoted vendor example and a longer path this build answers nowhere from failing it), and
  a call the closure does not follow because this build compiles no body for the method under the owner the call
  names, which lets through an address minted behind an interface, an abstract method or the abstract method an enum
  leaves to each of its constants. The closure stops at no such call in this build: the `name()` and `ordinal()` it
  reads on an owner compiled with `java.lang.Enum` as its superclass reach a body of that class and of no class here,
  because `java.lang.Enum` declares both final, and a constant's own name is an identifier carrying no `/` while its
  ordinal is no string, so the gate counts those reads apart from the calls it did not follow rather than excusing
  them among them; a call left in that list is an address the gate would not see, and
  `RelatedObjectAddressConventionTest` compiles a provider module of its own that mints one behind each kind of
  bodiless declaration to keep that reading honest. No module keeps such a row any more: grow carried `hostedPageUrl`
  and autra `hostedCardUrl` as record components persisted beside the token each is built from, and V81 dropped both
  columns, so each address is built where it is answered and kept nowhere.
  The shape stays listed because a provider added later could keep one, and it is
  left open in the build rather than closed over the words of the name a value is handed over under, because
  `successUrl`, `cancelUrl` and `notifyUrl` are names grow carries for the merchant's own addresses, which
  `TrailWithholdingConvention.THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR` registers as readable; simulator
  decision. What the build cannot read, a journey does: `JourneyClient.page` hands every page it serves under
  `/ui` that names a provider in its query to `NoWorkingPayerAddressOnAScreen`, which splits that page into the
  values printed on it and holds each against the same address floor, for the payer pages that provider declares.
  The reach is the client and not a method name, so every reader in `dev.marcinromanowski.pspsim.journey` is covered by construction;
  readers outside that package, such as `FollowUpUiIntegrationTest`, use their own client and are not. That costs
  no detection: the four screens that read an object's related map — `ObjectScreen`, `TimelineScreen`,
  `CorrelationScreen`, `InspectorScreen` — print the same map of the same object, so a swept read of that object's
  own screen stands for all of them: the objects listing of a run prints no value of that map at all, and
  `/ui/timeline` prints one only where a `key` selects that same object as its subject.
  `EveryScreenThatPrintsARelatedObjectTest` pins those four classes and the three templates that name the map, so a
  fifth reader forces the question whether the object it prints is one a journey opens. Showing the sweep refuse an
  address kept on a row means giving a provider such a component first, because no module of this build keeps one;
  the address of every payer page is built where it is answered. A page this simulator serves a payer is not under
  `/ui` and is not swept, because such a page
  carries its own address as the action of the form a payer submits. The route as the wizard promises it is read and
  is no offence, and so is any value carrying the brace of a path variable: the wizard prints that route in the
  sentence promising the page, the segment of a variable reads as any run of characters that is not a separator so
  the comma of that sentence stands where an identifier would, and a browser asking for a segment spelled
  `{hostedCardSessionId}` reaches nothing this build mints. The build is where a module naming its own route is
  refused, whichever side of a `put` it lands on. A provider that declares `NoPageThisSimulatorServes` is registered as serving none rather than
  missing from the register, and a provider in neither is refused; both registers are held to the declarations at run
  time by `the_pages_a_module_declares_are_the_pages_its_own_screens_are_swept_for`. Two limits, named rather than
  guessed at: this sweep reads values of a run, so a value a tester typed into a field the screen reads back is
  refused like one the module composed, and so is a merchant's own address that ends where a route of this build
  does. A floor over the host was measured to be worse than none, because providers mint the address from
  `simulator.public-base-url`, which is `http://localhost:8080` while a journey's own server answers on a port of the
  run, so comparing hosts read grow's live leak as somebody else's address and passed it. Keeping the address out of that map costs nothing: the
  `hostedTokenizationId` it is built from is the row's own `providerObjectId`, and a tester finishes the page
  through the payer step on the object's own screen. The
  gate does not read the tester-facing prose a provider supplies as
  `NoPageThisSimulatorServes(whatStandsInItsPlace)` and rendered on that same screen, so a provider writing its own
  payer URL into that sentence passes it; inference from where the gate sweeps, not a published rule.
- A payer page a provider hands out (Grow's `/far`, Worldline's `hostedTokenizationUrl`) is a provider route, not a
  screen. These rules do not apply to it: it is built for a phone, may be framed, and takes a card by alias.
- Who consumes a value decides the control it is typed into, on the payment wizard and on the Look a payment up screen
  alike. A value the provider reads is a box a tester types anything into, whatever is offered for it: the values the
  provider declares, or the values this test run has already produced, are suggested on the box as a `<datalist>` and
  printed beside it, each under the sentence whoever offered it wrote. An offering states what is on offer and never
  what may be sent, so nothing between the box and the provider judges what was typed and every refusal the provider
  publishes is reachable by clicking. Stripe refuses an unpublished `confirm` and `usage`, worldline an unpublished
  `authorizationMode` and autra an unpublished `transactionType`, each in its own words; grow refuses no `paymentType`
  at all and reads none of it, and a wizard that refused one would be inventing a refusal grow does not have. A
  run-produced value is forwarded to the provider too, which is why it is a box as well: grow answers `err.id 140` for
  a `cardToken` that business never saved and autra `400 INVALID_PAYMENT_ID` and `404 PAYMENT_NOT_FOUND` for a
  `paymentId` it holds no payment under, and the three fields drawn that way (`GrowOrigination`, `GrowReads`,
  `AutraReads`) reach those refusals by typing. The field naming a callback target is the one exception and the only
  list left: this simulator is the party that posts a callback, so it is the party that reads that address and the
  party that refuses one this run does not configure (`OriginationControl`, whose refusal says so, and
  `AFieldATesterFillsIn.thisSimulatorIsTheOneThatConsumesTheValue`, which is what both screens ask). A read declares no
  callback target at all (`DeclaredRead` refuses one, because a read posts nothing anywhere), so the Look a payment up
  screen draws no list for a field and the two ports render one way; `ReadPageTest` holds that, for a run-produced
  value and for a vocabulary a provider publishes.
- A field the provider declares as a
  span of time is stated as a number beside a unit, picked from seconds, minutes, hours and days, opening on the unit
  the provider's own example is counted in; the clock's advance box is the same control, opening on hours.
  `ui/ADurationInWords.java` composes the two into the ISO-8601 duration the column and the control API hold, and
  reads a span back as the largest unit that counts it without a remainder, which is exact in both directions: a span
  the form states and sends again is the string it was handed. Where a screen prints a stored field, the words lead
  and the stored value stays beside them, which is the order for every value a screen reads back and not only a span:
  the words are what a tester came for, and the value beside them is their only bridge to the control API. The field's
  own explanation and the provider's refusals name both forms, because one sentence is read by a tester and by whoever
  writes the value into a call.
- The span control validates nothing, which is the floor: it carries no `min`, `max`, `step`, `pattern` or `required`
  and is no `type="number"`, whatever it composes the provider judges, and a value it cannot count travels on as it
  was written, so every refusal `StripeCaptureWindow`, `StripeWebhookSigning`, `GrowHoldWindow` and `ClockAdvance`
  report of a span is reachable by clicking. It is a floor and not a claim about the UI as a whole, in one measured
  respect: `inputmode="numeric"` raises a digits-only
  keypad on a phone, where a minus sign cannot be typed, so the negative span a provider refuses is reachable from a
  desk and not from a phone keyboard; the attribute hints a keyboard and constrains nothing, and a value of any shape
  pasted into the box is sent as it was pasted. `verifyNoBrowserValidation` fails the build on any
  control of this UI that carries one of those attributes, reading whole elements over every template including the
  ones a provider contributes, and on any source under `kernel` or `ui` that asks a field whether a provider publishes
  the value a tester typed; the core reads no declared list at all and a screen reads one only to offer it. The demo
  shop's own templates are outside it: that application is a merchant's, where a
  required field is the merchant's business and no provider of this simulator is the validator.
- No read of this build declares a vocabulary: autra declares its `status` and `type` as boxes with the accepted
  values in the sentence beside each, because a status is a comma-separated list of two and a list of single values
  could send neither it nor the unpublished value `400 INVALID_STATUS` and `400 INVALID_TYPE` are reached with. One
  that did declare a vocabulary would now be drawn as a box with its values suggested on it, exactly as the wizard
  draws one.
- Any other field the provider declares no values for stays a box: a credential and a code the provider publishes as a
  grammar are free text by nature. `GET /__simulator/{provider}/config/fields` publishes the same declaration for a
  client with no screen, and the configuration call still refuses a value no field accepts.
- Every box on the account form hints a worked example of a value its provider accepts, never the key the provider
  reads it under. `ProviderAccountFields` refuses a declaration whose box carries no example, because the only other
  thing such a box could hint is that key, which is a developer's name for the field;
  `ProviderAccountFieldsIntegrationTest` configures an account of each provider from the hints its own boxes show. A
  field offered as a list hints nothing, having no box drawn for it.
- A field whose value is a whole block of many lines is `FieldShape.A_BLOCK_OF_MANY_LINES`, drawn as a `<textarea>` so a
  tester can paste the block, and hints the line the block begins with rather than a value: azericard's two PEM boxes are
  the only ones, a 2048-bit key being too long for any hint, and the sentence beside each names the shape its reader
  takes (`AzericardKeys.PUBLIC_KEY_SHAPE`, `PRIVATE_KEY_SHAPE`) and the `openssl` commands azericard publishes for
  writing a pair. The hint alone is refused as a key, which is what makes it a shape and not a value.
- A refusal printed on the account screen names the box it refused as that box is labelled: `terminal id is required by
  provider azericard, ...` where the property is `accounts[0].configuration.terminalId`. The property is the right name
  for the field of a request body, so the control API answer and the `refusedBecause` fact of the trail row keep it
  verbatim; `AccountForm.View.theRefusalAsTheBoxesOnThisScreenAreNamed` rewrites the leading property of the screen's
  copy alone, from the labels of the boxes the form draws, and leaves a refusal naming no box as it stands.
- A refusal of a value outside a published shape names the pattern and then says that shape in words, so the suite
  author who matches on the pattern and the tester reading it beside a box are both answered in one sentence:
  `testRunId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}, which is at most 64 characters, the first a letter or a digit
  and the rest a letter, a digit, a dot, a hyphen or an underscore, for example checkout-smoke`. `AShapeSaidInWords`
  reads the length, the characters and the first character off the pattern by measuring it, so the words cannot drift
  from what is enforced: every character of a kind is tried before that kind is named, every length up to one past the
  counts the pattern writes is tried before a longest is stated or left unstated, and a worked example the pattern does
  not accept is refused. `TestRunId`, `ProviderId`, `DeclaredPersona`, `simulator.default-test-run-id` and autra's
  `documentId` are said this way. A bound a box enforces beside its pattern is one these words cannot see, so it is
  written into the pattern instead: autra's `documentId` is `[0-9]{1,32}`, the width its store holds. A clause of
  whoever published a shape is composed into the measured words before the worked example
  (`AShapeSaidInWords.theRefusalOf(name, clause)`, `DeclaredPersona`), where it qualifies the pattern and not the
  example.
- A shape `AShapeSaidInWords` cannot state is refused at construction rather than stated wrongly, and belongs to
  whoever published it: a grammar (autra's `[0-9]{2} - .+`), a sign outside the character set (azericard's
  `-?[0-9A-Za-z]{1,2}`, whose code part `[0-9A-Za-z]{1,2}` the core does state) and a set admitting a character the
  core has no word for (worldline's `[^/?#]+`) each carry the provider's own sentence beside the pattern, pinned by a
  test of the values that sentence names. A class admitting part of a kind (`[0-5]{4}`), a run of lengths with a gap in
  it and counts multiplying past 512 are refused the same way. A provider composing around the measured words says what
  the measured count covers, so it is not read as the width of the whole box: azericard's reads `a response code of at
  most 2 characters, each a letter or a digit, optionally signed with a leading hyphen that count does not include`,
  and its vendor evidence follows in a sentence of its own.
- Every screen a form may come back to judges what the bar carries and prints the refusal on itself, the checks screen
  included, although the checks it runs name no run of a tester's: the bar hands its provider and its run to whatever
  screen is clicked next (`SmokeScreen.theRefusalThisScreenPrints`). The sweep over those screens requires a refusal
  from each of them.
- A card is shown as brand, last four digits, expiry and alias, never as a number.
- A screen action skips the provider's credential and signature checks, so it cannot exercise a credential failure. A
  `match.header` rule never fires from a screen, and the screen lists the run's header rules.
- A capture or refund must name an amount. A cancel takes none. An action that no longer applies is not offered.
- A screen action writes one `UI` trail row and no provider exchange rows. An action the provider refused writes nothing.
  An action a scenario rule refused writes one row that names the rule.

## Required UI Areas

### Dashboard

Shows the active test run, provider availability, recent provider traffic, pending hosted sessions, pending
captures/refunds/webhooks, failed callbacks and active scenarios. From it a tester can switch or create a run, reset it,
create a scenario, seed a payment method, and open the sessions queue and the audit.

Its run list offers each run a reset and a removal, so it lists only runs a tester can configure and holds no row for
the reserved run `UNKNOWN` (`JdbcAuditTrail.theRunsNewestTouchFirst`, which excludes it in SQL). That namespace is
reached from the Audit screen and the Correlation screen, whose whole subject is the trail of one namespace.

### This test run

One view per PSP: a summary of the account configuration, route base URLs to copy, the supported operations, current
objects, active scenarios, and provider-specific tools.

### Payment Methods

Seed a fixture for a provider, account and customer, with its type, alias, card facts, a default scenario and the
provider-native ids the provider reads; the list shows those ids beside each fixture (`PaymentMethodForm`). A fixture
is seeded or removed and nothing else, so there is no detach and no disable
(`PaymentMethodController.seedThePaymentMethod`, `PaymentMethodController.removeThePaymentMethod`).

### Hosted Sessions

A panel of the Payments screen (`/ui/objects`), not a screen of its own. For each pending session it shows the provider,
test run, account, the id the provider answered with, customer and order correlation, amount and currency,
requested operation, created and expiry times, and status. An expiry is measured against the run's clock.

Tester actions: take the payer step as `PAID` with a card fixture, as `DECLINED`, or as `ABANDONED` (`PayerOutcome`),
and send the payment's callback now from its callback panel. The provider-native ids are text on the row, for a tester
to read off.

A delayed callback is scheduled on `/ui/webhooks` rather than on the session. Hold versus save is chosen when the
payment is started, and a decline carrying a chosen provider error is written as a scenario rule (`quickstart.md`,
"What the screens do not do that the calls do"). A session is expired by advancing the run's clock past its deadline
(`testing-support-features.md`, "Time Travel / Clock Control").

### Payments and Orders

Shows the provider payment id, merchant reference, customer, payment method, amount, the captured, cancelled and refunded
balances derived from the operation list, the operation history, native and normalized status, and the available next
actions.

Actions: capture or complete a hold, cancel or reverse a hold, refund a captured payment. Those three are every object
action there is, and any other value is refused (`ObjectAction.declared`). A callback is sent or replayed from the
payment's callback panel, and a step the provider itself names is taken from the follow-up panel (`FollowUp`).

Forcing a status change and triggering a reconciliation response are reachable from nowhere: no control-API route, no
provider route and no service of this simulator performs either. Nothing marks an object unknown or for review.

### Scenarios

A rule is written, deleted, or copied with edits applied (`ScenarioController.write`, `ScenarioController.delete`,
`ScenarioDiffController.copy`). A change is a new rule: there is no edit in place and no disable. A write chooses the
provider and operation, the match conditions, the response or fault action, a delayed terminal state, the delivery plan
and the priority. Recent matches are a link to `/ui/audit` filtered by the rule's id (`templates/scenarios.html`).

### Webhook and Callback Delivery

List scheduled and completed deliveries, with the target URL, event id, the id the provider answered with, status and
attempt count.
Retry now, replay the same event id, send a duplicate, send out of order. Inspect the sanitized payload and headers.
Signature validation material is shown only at a safe level, never with secrets. Gaps are listed in
`testing-support-features.md`, "Webhook / Callback Lab".

### Audit

Filter by provider and test run from the bar, and by account, origin, direction, audit id, the id the provider answered
with, scenario id, HTTP method and status, and time range (`kernel/audit/AuditQuery.java`). Order id and customer are
no filters of this screen. Show the provider request and response, the matched scenario, latency and fault details, and
links to related objects and attempts.

An empty page says which narrowing answered nothing only where that narrowing is the only one asked for and no cursor
was carried into the page, because emptiness alone cannot tell a row that is absent from one another filter excluded or
one the cursor has already passed (`ui/AuditScreen.java`). Otherwise the page says only that nothing matches.

The reserved run `UNKNOWN` is read here and on `/ui/correlation`, the two screens whose whole subject is the trail of
one namespace (`Selection.ofAScreenWhoseSubjectIsTheTrail`); each offers a link to the other and to the namespace
itself, and the screen reading it carries one panel saying what put those rows there, what a tester changes about them,
and how to read a test run again (`ui/TheReservedRunOnAScreen.java`). The panel says each of those in a block of its
own, because a tester who has to find the one actionable sentence inside a paragraph of reasons does not find it.

Every other screen resolves its run with `Selection.of`, which refuses that id. `/ui`, `/ui/timeline`,
`/ui/idempotency` and `/ui/pairing` declare no write mapping and refuse it for the same reason the writing screens do:
what each shows is what one test run holds, and nothing of a run's own is stored under the reserved id, so a screen of
that kind reading it would draw a page with nothing of a run on it. `/ui/reads` declares no write mapping either and
performs the provider's own read under read mappings, which leaves a trail row, so it is a screen that writes whatever
it is pointed at. The refusal carries the fact the store would throw on, the box a run is typed into with a value that
box accepts, the run an empty box reads, and the two screens those requests are read on
(`ui/TheReservedRunOnAScreen.theRefusalOfAScreenThatShowsWhatARunHolds`): every rail link on the screen reading the
namespace carries the reserved id, so the refusal is where a tester who clicks one meets the way back.

The sanitized export for a bug report is per payment, on `/ui/repro-bundle`; `/ui/audit` itself exports nothing
(`templates/audit.html`).

### Configuration

`/ui/configuration` writes provider accounts with their credentials and callback targets, route aliases, the clock, and
the settings a provider declares, one field per declared setting (`ui/ConfigurationController.java`,
`ui/AccountFormController.java`, `ui/SettingsForm.java`). It shows the permissive-auth modes, which are off unless
enabled, and shows header-based test-run routing separately from them, because it routes and does not authenticate.

The public base URL, the default test run and the webhook retry schedule are startup properties. The screen shows them
and writes none of them (`../README.md`, "Run the application"), and names each retry delay in words, because that row
is read by a tester and by nobody else.

## Non-Functional Requirements

- The simulator runs as one application container and one PostgreSQL container. Nothing else is needed: the root
  `compose.yaml` starts exactly those two and builds the application's image itself, so a machine with only Docker can
  run it.
- Configuration comes from environment variables and files. Startup fails fast on invalid key or signing configuration.
  A permissive mode is an account setting written at runtime, so it exempts nothing from that check.
- The health endpoint reports database and scheduler readiness.
- The application writes no log lines of its own and sets no MDC: `src/main/java` holds no logger. The provider, test
  run id, account, the id the provider answered with and the scenario id are on the audit trail instead
  (`kernel/audit/AuditRecord.java`).
- One mechanism redacts for the screens and for the audit trail: a sensitive key's value is replaced with
  `[redacted]` (`kernel/credentials/Redaction.java`), and an audit export carries each row as it was recorded
  (`kernel/repro/RecordedExchange.java`). A run bundle withholds a secret rather than redacting it, and names the
  withheld field in `secretsItDoesNotCarry` (`kernel/bundle/PortableAccount.java`).
- UI actions are audited.
