# Testing Support Features

What a CI harness or a tester can use above the provider APIs, and where each feature stops. What each route does and
what it answers is in `README.md`, "Common Simulator Control API". This file lists only the routes, the screens and
the gaps.

| Feature | Control route | Screen |
|---|---|---|
| Scenario presets | `POST /__simulator/{provider}/scenarios/presets/preview`, `POST .../scenarios/presets`, `.../scenarios/import` | `/ui/scenarios` |
| Payment timeline | none | `/ui/timeline` |
| Correlation inspector | none | `/ui/correlation` |
| Webhook / callback lab | `POST`/`GET /__simulator/{provider}/webhooks`, `POST .../webhooks/deliver-due` | `/ui/webhooks` |
| Retry and idempotency inspector | `GET /__simulator/{provider}/idempotency` | `/ui/idempotency` |
| Clock control | `PUT .../config` (`clock.fixedNow`), `POST .../clock/advance` | `/ui/configuration` |
| Run import / export | `GET .../run-bundle`, `POST .../run-bundle/import` | `/ui/run-bundle` |
| Bug reproduction bundle | `GET .../repro-bundle?key=`, `POST .../repro-bundle/import` | `/ui/repro-bundle` |
| Mobile pairing | none | `/ui/pairing` |
| Seeded personas | `GET`/`POST .../personas`, `POST .../personas/preview` | `/ui/personas` |
| Scenario diff | `GET .../scenarios/{id}/diff?against=`, `POST .../scenarios/{id}/copy` | `/ui/scenarios/diff` |
| Contract smoke checks | none | `/ui/smoke` |
| Read-only demo mode | none | none |

Every feature is scoped by `testRunId`, needs no database access, and records every state-changing action in the audit.
Exported JSON is stable enough to commit. Behaviour is deterministic.

- The bar shows the provider and the test run on every screen, and `/ui/audit` shows the account and the scenario id
  beside each row. The payments list and the timeline carry no account column (`templates/objects.html`,
  `templates/timeline.html`).
- An action that creates an invalid signature, a malformed payload or a dropped callback is explicit: an account
  setting (`webhookSignature`, `azericard.callback-signature`) or a scenario rule (`TRANSPORT_FAULT`,
  `DELIVERY_DROPPED`), never a side effect.

## Scenario Presets / Playbooks

The presets are `card-add-success`, `card-add-failed`, `card-add-callback-delayed`, `preauth-success-capture-success`,
`preauth-success-capture-decline`, `preauth-success-capture-timeout-then-success`, `capture-accepted-response-lost`,
`cancel-hold-success`, `refund-full-success`, `refund-partial-then-full`, `psp-5xx-then-success`, `webhook-delayed`,
`webhook-duplicate`, `webhook-out-of-order`, `webhook-invalid-signature`, `status-lookup-processing-then-success` and
`status-lookup-processing-then-failed`.

A preset expands into explicit rules over the provider's own operations, so the same name expands differently for each
provider. The preview shows the rules before anything is saved.

`/ui/scenarios` offers each preset with what it writes for the provider on screen, in words read off that provider's
own expansion of it (`APresetInWords`): one line per rule, never joined into a sequence, because a preset's rules wait
for the calls they narrow on and the expansion says nothing about which call comes first. A line for a rule that is a
step of a story names the story, what it is kept per (`FLOW` reads "the flow of one call", `TEST_RUN` "the whole test
run") and the states the step is taken at and leaves. The chooser carries the preset's name and how many rules it
writes. A preset the provider cannot express writes no rule and carries the provider's own reason.

The words are computed from the expansion on every render, so no sentence about a preset is stored anywhere to drift
from it, and `APresetInWords` refuses rather than renders when a rule carries something the words do not read: a
`match` narrowing other than the operation and the event type, a header narrowing, an action that shapes a call on a
rule naming no operation, or an action that shapes a delivery on a rule naming one. Each provider's own control test
reads every preset back through it, so a provider that adds such a narrowing fails the build instead of rendering a
sentence that leaves it out.

Grow specifics:

- `cancel-hold-success` expands to `settleSuspendedTransaction` for the full hold, then `refundTransaction` for the same
  amount, because Grow has no card void (`grow.md`, "Voiding a Suspended Transaction"). Assert the normalized state, not
  `statusCode`.
- `preauth-success-capture-decline` and `card-add-failed` are driven by a scenario, because the Grow sandbox has no
  decline card. Assert the failure envelope and a numeric `err.id` (`grow.md`, "Declines").

Not supported:

- If a provider's contract cannot express a preset, the preset is refused, and the refusal says what is missing.
  `preauth-success-capture-timeout-then-success` and `webhook-invalid-signature` expand for no provider at all.
- On Autra, `cancel-hold-success`, `refund-full-success` and `refund-partial-then-full` all expand to one successful
  `VOID`. `refund-partial-then-full` has no partial first leg, and it is not refused either.
- A preset asserts nothing, and its rules carry no amount. The Grow `cancel-hold-success` rules are not pinned to the
  held sum.

## Payment Timeline / Trace View

`/ui/timeline` reads one payment by one key. The key is resolved in this order: an object's identity, a related-object
field, a callback event or delivery id, then a value sent under one of the provider's keys. If two objects carry the
key, no timeline is shown. The timeline is the audit trail in arrival order. A retry shows its attempt number and the
callback's next scheduled attempt, and an accepted operation whose response was lost is flagged on its row
(`TimelineScreen.Entry`, `TimelineScreen.Callback`). The ledger and the callbacks are shown in panels beside it
(`design-constraints.md`, "A timeline is one store's order, and the other stores hang off it").

Not supported:

- An order or merchant reference, a payment-method token, or a customer id finds a payment only where the provider
  lists that value among the object's related fields. Grow and Stripe list no order reference, and Grow lists no
  `cardToken`.
- A state transition, a callback being scheduled, and an idempotent replay or conflict are not events on the timeline.
  The screen says so. Replays are on `/ui/idempotency`.
- The timeline does not record who took a manual action. The simulator has no users.
- A request is paired with its response by position. The trail does not record the pairing.

## Correlation Inspector

`/ui/correlation` explains why a request landed in a test run. It reads the audit trail. An ambiguous assignment lands
in the reserved run `UNKNOWN`, and each ambiguous field links to the objects that carry it. That run is read on this
screen and on `/ui/audit`, which holds its whole trail, requests and answers; each screen links the other.

Not supported:

- Only the selected `testRunId`, the resolution rule and the matched object ids are recorded. The matched account, the
  owner, the scenario's priority and the rules it beat, the unmatched fields, and the candidates of an ambiguity are
  reconstructed from the run's current state, or are not shown. The screen can explain a decision but not help repair
  one.
- The existing-object-correlation rung never fires, because no provider offers a candidate for it.

## Webhook / Callback Lab

A delivery goes only to a target configured for the run's accounts. It does not follow redirects. It enforces connect
and read timeouts and keeps a bounded, redacted response preview. Every attempt is written to the audit. Retry plans are
in `README.md`, "Provider settings".

| Action | How |
|---|---|
| send now, schedule later, retry, replay the same event id, duplicate | lab route or screen (`delayMs`, `RETRY`, `reuseEventId`) |
| another configured target for one attempt | screen only |
| drop, delay, duplicate as a rule | scenario rule on `/ui/scenarios` |
| out of order | delay one event with a rule. Grow and Azericard have one callback per object, and Worldline sends none, so none of the three can reorder |
| corrupt signature | account setting: Stripe `webhookSignature`, Azericard `azericard.callback-signature` (`CORRUPTED`, `ABSENT`). No other provider has one |
| remove a required field, change the native status | not supported. Change the object, not the message |

Not supported: a preview of the payload before a manual send (headers and body appear once the delivery exists), and a
route alias as a delivery target.

## Retry, Idempotency and Correlation Inspector

`/ui/idempotency` and `GET /__simulator/{provider}/idempotency` show, for each key: the provider, the operation, the
key's role, the first request, every replay and its answer, whether state changed, and whether a timeout landed before
or after the operation. The screen has no write, so an established replay cannot be altered. A tester changes a future
answer only by writing a scenario rule.

| Provider | True idempotency key | Duplicate or correlation keys | Differing payload under one key |
|---|---|---|---|
| Stripe | `Idempotency-Key` | — | refused (`stripe.md`, "Idempotency") |
| Worldline | `X-GCS-Idempotence-Key` (SDK) and `X-GCS-Idempotency-Key` (signed by non-SDK clients). Both are read | `order.references.merchantReference`, `payment.id` (a new one for each operation) | replayed, never a conflict (`worldline.md`, "Authentication and Transport") |
| Grow | none | `transactionUniqueIdentifier`: any repeat is a duplicate error, never a replay, and it also retraces a lost stored-card charge. `transactionGroupIdentifier`: a duplicate only against a successful payment. `transactionId`/`transactionToken`. A refund has an id and no token | no published rule |
| Autra | none | `orderId`: a sale `409`. A pre-authorization `409` is released when the attempt fails. PIX has no idempotency. `paymentId` is used for lookup. `rrn` is used for a reversal. `transactionalToken` is single-use | payload not compared (`autra.md`, "Idempotency") |
| Azericard | none | `TERMINAL`+`ORDER` (last six digits, per terminal, per day), `RRN`, `INT_REF`. Status inquiry uses terminal, order and original type | no published rule |

A request's body is its answer's fields as its audit trail row keeps them, with the provider's withheld fields
redacted. Its fingerprint is keyed per test run: it tells two requests of one run apart and identifies no input.
Requests are numbered in the order they were recorded, so a duplicate refusal answered while the request that claimed
the key was still running appears before that request.

The inspector names only conditions the provider publishes. A condition that is new to a provider gets its own name.
Worldline shows no duplicate-key rejection. An Autra `409` names no payment, so the original payment id is shown only
here, from the simulator's own record (`DUPLICATE_KEY_REFUSAL`), and never on the wire.

Not supported:

- The request fingerprint of each replay appears on the control route, but not on the screen.
- No surface reports "this provider publishes no rule" for a differing payload.
- Grow's `processId`/`processToken` and Azericard's `TERMINAL` are not declared keys. Grow's retrace read writes no keyed
  record.

## Time Travel / Clock Control

Pin a run's clock and advance it. `/ui/configuration` shows the run's effective clock. Every jump is audited, and
advancing an unpinned clock is refused. The clock belongs to the run, so it moves every provider in that run. Scheduled
deliveries and provider signatures use this clock.

To expire a session, a hold or a token, make a callback due, or advance the retry queue: pin the clock, create the
object, then advance past its deadline. There is no separate expiry control, on purpose (`README.md`, "Common Simulator
Control API").

## Scenario Import / Export

Bundle format and import rules are in `README.md`, "Run Bundle Schema".

Not carried: webhook jobs, a run-level audit snapshot (use the reproduction bundle), payments (drive them again),
and a transition queue (a delayed rule travels as a rule). A run holding a `match.header` rule cannot be exported: the
export answers `422`.

## Mobile Pairing Helpers

`/ui/pairing` shows the public base URL, each provider's path, the run and its header, the callback targets, and the
plain (non-secret) account settings. It links to the run's provider traffic in `/ui/audit`. Details are in `README.md`,
"Connect my app".

Not supported: a drawn QR code (the payload is printed as text), a copyable snippet block, editing an alias here (that
is done in `/ui/configuration`), or a merchant id separate from the account id.

## Seeded Personas

Details are in `README.md`, "Seeded Personas". A persona writes the provider's presets and seeds one fixture in one
press. It has no state of its own: what it creates are ordinary rules and fixtures.

Not supported: rules narrowed to the persona's own instrument (give each persona its own run), editing, customer objects
other than Stripe's, any persona for Grow, and 3-D Secure or Pix personas. Three intents are offered: success, decline,
and capture answer lost.

## Contract Compatibility Smoke Tests

Details are in `README.md`, "Contract Compatibility Checks". `/ui/smoke` runs each provider's checks against run `smoke`.
Each check names the provider, the operation, the field and the vendor sample.

Not supported: fixture and scenario creation is checked only through Grow and Stripe. Azericard and Autra have no retry
or idempotency check. Grow's idempotency check is missing, because it needs a `cardToken`, which only a merchant callback
carries.

## Bug Reproduction Bundle

One payment is packaged by the id the provider answered with. The `replayable` half (setup) imports. The `readable` half (state,
ledger, trail, callbacks) never imports. Ids are preserved unless `remapProviderIds` is sent. An id collision answers
`409`, and a bad remap answers `422`. Schema: `README.md`, "Run Bundle Schema".

Not supported: signature verification of the carried bytes (the bundle carries no key), writing trail rows, callbacks or
payments on import, lookup by any key except the object id, keyed-request records, and a trail over 4,000 rows
(`evidence.trailTruncated`).

## Scenario Diff

Details are in `README.md`, "Scenario Diff". It compares two rules of one provider field by field. A copy writes a new
rule with the edits applied.

Not supported: resting state, the delivery plan beyond one delivery, damaged wire bytes, a story's current state, a
header's value (only its name), and rules from two providers.

## Read-Only Demo Mode

There is no read-only switch (`kernel/startup/SimulatorProperties.java`), so nothing disables a write and no provider
API can be isolated. What a demo run is filled with comes from the features above: a persona writes a provider's
presets and seeds a fixture in one press, a reproduction bundle imports an example run, and no bundle carries a secret.
