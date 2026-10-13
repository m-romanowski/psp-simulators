# An end-to-end suite that points at the simulator

This is what a consuming team's end-to-end tests look like when the payment provider is this simulator instead of a
vendor sandbox. It is a Cucumber suite: six `.feature` files a product person can read, and step definitions that talk
to the simulator over HTTP and nothing else.

## Run it

One command, from this directory:

```sh
./gradlew test
```

Nothing has to be running first. It needs Docker and a JDK, and it takes about a minute the first time.

## What it demonstrates

A vendor sandbox can already show a successful payment. It cannot show any of this:

| `.feature`                                                                                                                           | What only a simulator gives you                                                                                                                                                                                                                                                             |
|--------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`a-decline-the-test-asked-for.feature`](src/test/resources/features/a-decline-the-test-asked-for.feature)                           | the acquirer refuses *this* capture, for the reason the test named, and the trail says which rule decided it                                                                                                                                                                                |
| [`an-authorisation-that-outlived-its-window.feature`](src/test/resources/features/an-authorisation-that-outlived-its-window.feature) | eight days pass between two calls, in under a second, so the expiry branch is exercised instead of shipped untested                                                                                                                                                                         |
| [`an-answer-that-never-arrived.feature`](src/test/resources/features/an-answer-that-never-arrived.feature)                           | the provider took the money and the answer was lost on the way back, and a `502` in front of the provider that a retry must survive without paying twice                                                                                                                                    |
| [`callbacks-the-test-controls.feature`](src/test/resources/features/callbacks-the-test-controls.feature)                             | the same callback twice under one event id, a callback that is never sent at all, one the shop refuses until the provider gives up and then one attempt more, Grow's separate-acknowledgement handshake, and Stripe signing each attempt afresh so a retry does not carry a stale signature |
| [`a-refund-the-shop-asks-for.feature`](src/test/resources/features/a-refund-the-shop-asks-for.feature)                               | money the provider took and has not returned, beside a separate answer saying whether any further call would return it, which the two providers disagree about on the same figure                                                                                                           |
| [`a-typed-vendor-client.feature`](src/test/resources/features/a-typed-vendor-client.feature)                                         | the vendor's own published SDK driving the simulator, parsing its objects and its error envelopes, and a `502` in front of the provider reaching the client as an error it can read                                                                                                         |

Read the `.feature` files first. They are the documentation: this suite carries no comments, because the Gherkin is the
prose and the step names are the rest of it.

## Two providers, because one proves nothing

|                                   | Grow                                                                                             | Stripe                                           |
|-----------------------------------|--------------------------------------------------------------------------------------------------|--------------------------------------------------|
| how a payer authorises            | a hosted page the payer's browser opens and posts                                                | off-session, no payer page exists at all         |
| request encoding                  | form fields, credentials in the body                                                             | REST with the key in `Authorization`             |
| how failure is signalled          | **HTTP 200 always**, the outcome is the body's `status`                                          | HTTP `4xx`, and the outcome is the status code   |
| what names the reason             | a numeric `err.id` with a Hebrew message                                                         | a string `error.code` with a type and a doc link |
| answer content type               | `text/html`, carrying JSON                                                                       | `application/json`                               |
| where the shop learns the outcome | a callback to the shop's own endpoint, which is the only place the settlement token ever appears | the answer to the call it made                   |
| a vendor SDK to drive it with     | none published                                                                                   | `com.stripe:stripe-java`                         |

Every step in `a-decline-the-test-asked-for.feature`, `an-authorisation-that-outlived-its-window.feature`,
`an-answer-that-never-arrived.feature` and the outline of `a-refund-the-shop-asks-for.feature` is written once and runs
against both. Each shop knows only its own provider's vocabulary — Grow's `103`, `803`, `130` and `210`, Stripe's
`expired_card` and `charge_expired_for_capture` — and the scenarios ask for the fact rather than the code.

## The typed vendor client

`a-typed-vendor-client.feature` runs `com.stripe:stripe-java` — the real published SDK, pointed at the simulator with
nothing else changed. When a Stripe scenario fails, the suite attaches every exchange the SDK made — request line,
headers, body, response — with secret keys, client secrets and bearer tokens withheld.

## Why it is a separate build

Its own `settings.gradle.kts` and wrapper, outside the root build, written **from outside** with no access to the
simulator's internals — no shared classes, no shared database, no shared fixtures — so its only contract with the
simulator is HTTP: the provider-compatible routes and the control API.

## What makes this fail when it is wrong

`./gradlew test` starts PostgreSQL in a container, builds the simulator's own boot jar, runs it as an ordinary process,
waits for `/actuator/health/readiness`, and shuts it all down afterwards. A simulator that exits or never reports ready
fails the run and prints the last 40 lines of its log.

Each scenario has its own test run, and tears down with `POST /__simulator/{provider}/reset` for that run, then
`DELETE /__simulator/{provider}/config/accounts/{accountId}`.

## Pointing it at something already running

```sh
./gradlew test -Dsimulator.baseUrl=http://127.0.0.1:8080
```

If that address does not answer readiness, the run fails; it does not start a simulator of its own.
`SIMULATOR_BASE_URL` works the same way as an environment variable. `-Dsimulator.jar=/path/to/psp-simulators.jar`
names a jar and skips the build — the explicit way to say that this jar is the one under test. Use
`-Dsimulator.baseUrl` to keep a simulator around to inspect once the run is over; one this suite started is stopped
with the run.

## Where the pieces are

| File                                            | What it is                                                                                             |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `src/test/resources/features/*.feature`         | the scenarios, and the documentation                                                                   |
| `RunTheExample.java`                            | the JUnit Platform suite that runs them                                                                |
| `SimulatorUnderTest.java`                       | starts, waits for and stops the simulator, or uses one it was given                                    |
| `Control.java`                                  | the provider-neutral control API: test runs, the clock, scenario rules, fixtures, callbacks, the trail |
| `Shop.java`, `GrowShop.java`, `StripeShop.java` | one shop per provider, each speaking its provider's own contract                                       |
| `MerchantEndpoint.java`                         | the shop's own HTTP endpoint, which receives the callbacks                                             |
| `TranscriptOfTheVendorClient.java`              | records every exchange the Stripe SDK makes, withholding secrets                                       |
| `CheckoutSteps.java`, `VendorClientSteps.java`  | the step definitions                                                                                   |
