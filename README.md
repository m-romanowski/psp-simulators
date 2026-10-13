# PSP Simulators

A standalone simulator for five payment service providers. Point an application at it instead of the real PSP and it
answers in the vendor's own shapes: the same routes, payloads, error envelopes and callbacks. A payment journey can be
driven end to end with no sandbox account, no real card and no network nobody controls.

It has three surfaces over one application:

- a **provider-compatible contract** per provider, which the application under test calls and which never mentions the
  simulator,
- one **provider-neutral control API** under `/__simulator/{provider}` that isolates a test run, configures accounts,
  moves the clock, seeds instruments, writes the rules that decide outcomes, completes a payer's page, sends and
  retries callbacks, and reads back a sanitized trail,
- a **tester UI** under `/ui` that covers the same ground by clicking, and no more.

It is not a payment processor or a full model of any vendor. Each contract covers what order and payment orchestration
tests need: registration, hold, capture, cancellation, refund, reconciliation, retry and fault tolerance.

## Run it

Docker is the only thing this needs. From a fresh clone:

```sh
docker compose up --wait
```

That builds the simulator from this repository's own `Dockerfile` inside a JDK container and starts it beside a
PostgreSQL, so the machine needs no JDK, no Gradle and no editing a file first. The command returns once the simulator
answers its readiness probe. Then open:

| What                   | Where                                                                                |
|------------------------|--------------------------------------------------------------------------------------|
| the tester UI          | [http://localhost:8080/ui](http://localhost:8080/ui)                                 |
| every route, generated | [http://localhost:8080/openapi/index.html](http://localhost:8080/openapi/index.html) |

The first run takes a few minutes, because it downloads the build image and resolves the Gradle dependencies;
starting again once built takes seconds. Both containers are published on `127.0.0.1` only, because the simulator
holds provider credentials and unredacted fixtures.

`docker compose down` stops both and keeps the database's volume, so a payment written before it is still there
afterwards. `docker compose down -v` deletes the volume and with it every run.

A first payment is [`doc/quickstart.md`](doc/quickstart.md). To work on the simulator itself with a JDK, run
[the database](#run-the-database) alone and the application from your IDE.

## Who it is for

- **Application teams**, who need the provider's answers without the provider: a hold that captures, a decline, a
  callback that arrives twice, a `5xx` followed by a success, an authorization that expires.
- **CI harnesses**, which get an isolated test run per job, a clock they move instead of waiting on, outcomes chosen by
  rule rather than by card number, and a run's setup as one exportable document.
- **Testers and support engineers**, who do the same from screens.

## The five providers

| Provider         | What it answers                                                                                                                                                  | Contract                               |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| Grow / Meshulam  | the hosted payment process and its payer page, the stored-card charge, settlement, refund, the callback handshake and resend, and three reconciliation lookups | [`doc/grow.md`](doc/grow.md)           |
| Azericard        | the gateway form post: authorization, pre-authorization, sales completion, both reversals, the status inquiry and card storage, signed both ways                 | [`doc/azericard.md`](doc/azericard.md) |
| Stripe           | PaymentIntents, Charges, Refunds, Customers, PaymentMethods, SetupIntents and ephemeral keys, with Stripe's idempotency and signed webhooks                      | [`doc/stripe.md`](doc/stripe.md)       |
| Autra            | the OAuth token, the acquiring session, the pre-authorization with capture and void, Pix checkout, and the hosted card session with the direct sale             | [`doc/autra.md`](doc/autra.md)         |
| Worldline Direct | hosted tokenization, the pre-authorization hold with capture and cancel, the charge of a stored token, and HMAC signing                                         | [`doc/worldline.md`](doc/worldline.md) |

[`doc/README.md`](doc/README.md) is the contract: each provider's surface, the whole control API and the shared rules.
Each provider file records what the vendor publishes, what it does not, and where the simulator departs from it.

## What it does for a test

- **Outcomes are chosen.** A scenario rule matches on account, order, instrument, amount, currency, operation or a
  request header such as `X-Simulator-Scenario`, and answers with a success, a decline in the provider's vocabulary, a
  transport fault before or after the operation, a processing state that settles later, or a lost response. No card
  number decides anything.
- **Every run is isolated.** State, fixtures, rules, clock and audit belong to a `testRunId`. A provider request is
  assigned to one by header, by route alias, by account credential, or by an object it names.
- **Time is moved, not waited on.** A run pins its clock and advances it. Every deadline is computed from that clock
  when it is read.
- **Callbacks are first-class.** They are queued, signed where the vendor signs, retried on a plan the run can change,
  and readable attempt by attempt with the headers sent. A rule can delay, duplicate or drop one.
- **Nothing leaks.** No fixture holds a card number or a CVC. Declared secrets are never printed, and a field no
  provider declares is withheld by default.
- **A failure travels.** A run's setup exports as a run bundle and imports elsewhere, with secrets supplied beside it.
  One payment exports as a bug-report bundle with its trail, callbacks and clock.

## What a tester can do without code

From `/ui`, a tester can:

- configure an account, its callback target, the clock and route aliases,
- start a payment in a wizard that asks only for the fields that provider reads, and finish the payer's page,
- capture, cancel or refund from the payment itself,
- seed a card or a persona, write a rule or expand a preset, and compare or copy rules,
- send or replay a callback, run the contract checks, and read the trail.

The screen for each control route is in [`doc/README.md`](doc/README.md), "Common Simulator Control API". The rules
the screens follow are [`doc/technical-ui-requirements.md`](doc/technical-ui-requirements.md).

## Prerequisites

- Docker. On its own it runs the whole simulator, database included ([Run it](#run-it)), and it backs the
  Testcontainers tests. See [Without Docker](#without-docker).
- A JDK, only to build the code or run it from an IDE. The build uses a Java 25 toolchain and downloads one if needed.
  `docker compose up` needs none, because the jar is built inside the image.

The Gradle wrapper is committed. The UI needs no Node toolchain.

## Run the database

Only the database, for running the application from an IDE or with Gradle:

```sh
docker compose up -d postgres
```

Naming the service leaves the simulator's own container out; a bare `docker compose up` starts both. Postgres listens
on `127.0.0.1:5432`. Database, user and password are all `pspsim`. Its data is on a volume that outlives
`docker compose down`; `docker compose down -v` removes it with its data.

## Run the application

```sh
./gradlew bootRun
```

Against that database, with a JDK. [Run it](#run-it) is the same application with no JDK at all. The service binds to
`127.0.0.1:8080`. Flyway migrates on startup, and invalid configuration stops startup. The screens are at `/ui`.
A first payment is [`doc/quickstart.md`](doc/quickstart.md).

Every route is browsable at `/openapi/index.html`, one generated OpenAPI document per surface, linked as **API** from
every screen.

Health, including database and scheduler readiness:

```sh
curl -s http://localhost:8080/actuator/health | jq
curl -s http://localhost:8080/actuator/health/readiness | jq
```

Configuration is under `simulator.*` in `src/main/resources/application.yaml`. Every property can be set by Spring
Boot's relaxed binding: environment, `-D`, `--simulator...` or an external `application.yaml`.

| Property                                          | Variable                                                             | Default                                                       | Meaning                                                                                                                      |
|---------------------------------------------------|----------------------------------------------------------------------|---------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `simulator.public-base-url`                       | `SIMULATOR_PUBLIC_BASE_URL`                                          | `http://localhost:8080`                                       | the address given to a payer and a paired application                                                                        |
| `simulator.default-test-run-id`                   | `SIMULATOR_DEFAULT_TEST_RUN_ID`                                      | `default`                                                     | the run a request without one uses                                                                                           |
| `simulator.webhook.retry-delays`                  |                                                                      | `PT0S,PT1S,PT5S,PT30S,PT2M`                                   | the retry plan of Stripe and Azericard, rules below. Grow and Autra declare their own (`doc/README.md`, "Provider settings") |
| `simulator.webhook.connect-timeout`               | `SIMULATOR_WEBHOOK_CONNECT_TIMEOUT`                                  | `PT2S`                                                        | connect timeout of a callback attempt                                                                                        |
| `simulator.webhook.read-timeout`                  | `SIMULATOR_WEBHOOK_READ_TIMEOUT`                                     | `PT5S`                                                        | read timeout of a callback attempt                                                                                           |
| `simulator.webhook.max-response-preview-bytes`    | `SIMULATOR_WEBHOOK_MAX_RESPONSE_PREVIEW_BYTES`                       | `2048`                                                        | how much of the merchant's answer is read and kept, 1 to 65536                                                               |
| `simulator.webhook.poll-interval`                 | `SIMULATOR_WEBHOOK_POLL_INTERVAL`                                    | `PT1S`                                                        | wall-clock tick that attempts due deliveries                                                                                 |
| `simulator.fault.longest-delay`                   | `SIMULATOR_FAULT_LONGEST_DELAY`                                      | `PT30S`                                                       | cap on a `DELAYED_RESPONSE` fault, at most `PT5M`                                                                            |
| `simulator.scheduler.pool-size`                   |                                                                      | `4`                                                           | threads of the simulator's task scheduler, 1 to 64                                                                           |
| `spring.datasource.url`, `.username`, `.password` | `SIMULATOR_DB_URL`, `SIMULATOR_DB_USERNAME`, `SIMULATOR_DB_PASSWORD` | `jdbc:postgresql://localhost:5432/pspsim`, `pspsim`, `pspsim` | the PostgreSQL database                                                                                                      |
| `server.address`, `server.port`                   | `SIMULATOR_BIND_ADDRESS`, `SIMULATOR_PORT`                           | `127.0.0.1`, `8080`                                           | where the service listens                                                                                                    |
| `spring.thymeleaf.cache`                          | `SIMULATOR_TEMPLATE_CACHE`                                           | `true`                                                        | template caching                                                                                                             |

Retry-plan rules, checked at startup: one delay per attempt, so the plan's length is the attempt cap, at most 20; the
first delay must be `PT0S`, an immediate first attempt; delays must not go backwards, but may repeat, so an all-zero
plan is valid. Delays run on the run's clock, not the wall clock.

Callbacks never follow redirects.

## Pointing it at a database it does not own

The simulator does not have to own its database. Three variables aim it at any reachable PostgreSQL, which is how it
runs as a standalone task in the cloud against a managed one such as RDS:

```sh
SIMULATOR_DB_URL=jdbc:postgresql://db.example:5432/pspsim \
SIMULATOR_DB_USERNAME=pspsim \
SIMULATOR_DB_PASSWORD=secret \
SIMULATOR_BIND_ADDRESS=0.0.0.0 \
java -jar build/libs/psp-simulators-*.jar
```

`./gradlew bootJar` writes that jar, under its version. Flyway migrates on startup into whatever schema the user
owns, so the user must be able to create tables; a database it cannot reach fails startup instead of serving errors
later. Set
`SIMULATOR_PUBLIC_BASE_URL` to the address a payer and a paired application will reach, since that is what the
simulator puts in the links it hands out.

The image takes the same variables, so a host with no JDK can do it too:

```sh
docker run --rm -p 127.0.0.1:8080:8080 \
  -e SIMULATOR_DB_URL=jdbc:postgresql://db.example:5432/pspsim \
  -e SIMULATOR_DB_USERNAME=pspsim \
  -e SIMULATOR_DB_PASSWORD=secret \
  psp-simulators
```

`docker build -t psp-simulators .` builds that image from the repository root. It binds `0.0.0.0` inside its
container, so publish it as `-p 127.0.0.1:8080:8080`; a bare `-p 8080:8080` offers the provider credentials and the
unredacted fixtures on every host interface.

## An end-to-end suite that points at it

[`e2e-example/`](e2e-example/README.md) is a consuming team's Cucumber suite in its own Gradle build, talking to the
simulator only over HTTP. It drives Grow and Stripe with the same steps, one feature through Stripe's own
`stripe-java` client. It starts the simulator itself:

```sh
cd e2e-example && ./gradlew test
```

`./gradlew check` does not run it.

## The screens, in a real browser

[`browser-e2e/`](browser-e2e/README.md) is a separate build that drives the screens in headless Chromium with
Playwright: a tester's journeys through the screens, a payer's journey through each provider's hosted page or webview,
and the layout at phone width. That build's **What it walks** names every one of them.

```sh
cd browser-e2e && ./gradlew test
```

## A shop that pays through it, by clicking

[`demo-shop/`](demo-shop/README.md) is a Stripe-shaped web shop in its own Gradle build, which pays through the
simulator with the vendor's own `stripe-java` client and links each step of a story into the screens that show it. It
starts the simulator beside itself:

```sh
cd demo-shop && docker compose up --build
```

That answers on [http://localhost:8081](http://localhost:8081) for the shop and
[http://localhost:8082/ui](http://localhost:8082/ui) for the simulator it starts, on `127.0.0.1` only, so it runs
beside the stack [Run it](#run-it) brings up on 8080 rather than taking its port.

`./gradlew check` does not run it either.

## Where to go next

| Document                                                                                                                                                                       | What it is                                                                         |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [`doc/README.md`](doc/README.md)                                                                                                                                               | the contract: provider surfaces, the control API and the shared rules              |
| [`doc/quickstart.md`](doc/quickstart.md)                                                                                                                                       | a first payment, by call and by clicking                                           |
| [`doc/grow.md`](doc/grow.md), [`doc/azericard.md`](doc/azericard.md), [`doc/stripe.md`](doc/stripe.md), [`doc/autra.md`](doc/autra.md), [`doc/worldline.md`](doc/worldline.md) | one per provider: the vendor's contract and where the simulator departs from it    |
| [`doc/testing-support-features.md`](doc/testing-support-features.md)                                                                                                           | presets, faults, time travel, bundles and inspectors, and their gaps               |
| [`doc/technical-ui-requirements.md`](doc/technical-ui-requirements.md)                                                                                                         | what the screens must be, and the security model                                   |
| [`doc/design-constraints.md`](doc/design-constraints.md)                                                                                                                       | the constraints the kernel imposes on every provider                               |
| [`doc/contributing-a-screen.md`](doc/contributing-a-screen.md)                                                                                                                  | the one file a provider-specific screen is, and what the core insists on           |
| [`e2e-example/README.md`](e2e-example/README.md)                                                                                                                               | an outside-in Cucumber suite over two providers                                    |
| [`browser-e2e/README.md`](browser-e2e/README.md)                                                                                                                               | the journeys and the layout, through a real browser                                |
| [`demo-shop/README.md`](demo-shop/README.md)                                                                                                                                   | a Stripe shop started by one `docker compose up --build`, linking each step to `/ui` |

The tests are the specification. Where a document and the code disagree, the tests decide.

## Release a version

The version of this simulator is one line of [`gradle.properties`](gradle.properties), which is where Gradle reads
it and the only place it is stated. A running simulator answers it on `GET /actuator/info`, so a tester reporting
what they saw and a suite pinning a behaviour can both name the build they met.

Cutting a release is one dispatch of [`.github/workflows/release.yml`](.github/workflows/release.yml) with the
component to bump:

| bump    | 0.4.2 becomes |
|---------|---------------|
| `patch` | 0.4.3         |
| `minor` | 0.5.0         |
| `major` | 1.0.0         |

That workflow runs the whole gate first and releases nothing if it is red. It then bumps the version, commits it as
`chore: release v<version>`, tags `v<version>`, pushes the commit and the tag together, builds the runnable jar of
that version and publishes a GitHub release carrying it.

The same bump is one command here:

```sh
./scripts/release.sh patch
./scripts/release.sh --version 1.0.0
```

It rewrites the version, commits and tags, and pushes nothing: what to do with the tag is the caller's decision. It
refuses a dirty tree, because a release commit carries the version and nothing else, and it refuses a version this
repo already carries a tag for.

The API views under [`src/main/resources/static/openapi`](src/main/resources/static/openapi) carry the version they
were generated from, so a bump leaves them stale and `verifyOpenApi` says so. The workflow regenerates them into the
release commit before it pushes; by hand it is `./gradlew writeOpenApi`, then `git commit --amend` and `git tag -f`
onto the commit that carries them.

## Run the quality gate

```sh
./gradlew check
```

| Task                                         | What it enforces                                                                                   |
|----------------------------------------------|----------------------------------------------------------------------------------------------------|
| `spotlessCheck`                              | google-java-format and ktfmt over every build here; whitespace and newlines in md, yaml, sql, toml |
| `compileJava`, `compileTestJava`             | Error Prone with NullAway, `-Xlint:all -Werror`                                                    |
| `checkstyleMain`, `checkstyleTest`           | the Google Checks rules no other task covers                                                       |
| `test`                                       | unit, ArchUnit and Testcontainers integration tests                                                |
| `jacocoTestCoverageVerification`             | 80% line and 70% branch coverage overall, 70% line coverage per class                              |
| `pitest`, `verifyMutationScope`              | 90% mutation score on the domain packages, measured over the right classes, with no unexecuted code |
| `verifyExclusionBudget`                      | the classes excluded from mutation stay no more complex than when their bound was measured         |
| `verifyNoComments`                           | no comment in any Java, Kotlin, Gradle Kotlin, SQL or template source in this tree                 |
| `verifyCaughtExceptionNames`                 | a caught exception is bound to a name that says what went wrong, in every build in this tree       |
| `verifyNoMergeConflicts`                     | no conflict marker in any tracked file                                                             |
| `verifyMigrationNumbers`                     | no two migrations claim one version, compared as Flyway compares one, not as a name spells it      |
| `verifyOpenApi`                              | the committed OpenAPI documents match what the build generates                                     |
| `verifyClickableCapabilities`                | every control route is reachable from a screen                                                     |
| `verifyNoBrowserValidation`                  | no form control of this UI validates what a tester typed before the provider sees it               |
| `verifyEveryFormSendsWhatItCarries`          | no form a browser submits as a query names a value only in the address it submits to               |
| `verifyNoPayerUrlOnAScreen`                  | no screen source or template hardcodes the address of a payer page any provider declares           |
| `verifyEveryPayerPageDeclaresWhatItMayLoad`  | every payer page built in Java declares a policy loading nothing but its own style                 |
| `verifyNoPayerUrlAmongRelatedObjects`        | no provider module builds the state of an object out of the address of a payer page it serves      |
| `verifyEveryProviderWithholdsWhatItAnswers`  | every provider declares what its answers are withheld under, applies it and withholds its page URL |
| `verifyTaskDescriptions`                     | every task description says what it does in a listing entry's words                                |
| `verifySonarSuppressions`                    | a rule switched off in the scanner, or a check switched off at a site or for a source set, carries its reason |
| `:build-logic:test`                          | the logic the gates decide on: the PIT invocation, the mutation report, the ratchets, the scanners |

`./gradlew spotlessApply` fixes formatting. Reports land in `build/reports/`.

A migration that has run is never edited: a change is a new numbered file.

Two gates are not `check` tasks, because `check` builds no image. CI runs `docker compose up --wait` itself, and
then runs it a second time beside the first stack, so the one command a fresh clone depends on cannot break
unnoticed. That job runs [`.github/scripts/ci-one-command-works.sh`](.github/scripts/ci-one-command-works.sh) on a
machine with no JDK and no Gradle on it, the way a newcomer does. A second job builds the demo shop's own stack with
[`.github/scripts/ci-demo-shop-one-command-works.sh`](.github/scripts/ci-demo-shop-one-command-works.sh), because
nothing else builds the shop's image or reads the compose file that wires it to the simulator. That job brings it up
beside the stack `docker compose up --wait` starts and asks each for its own address, which is the only state in which
one stack taking the other's host port shows up. Both scripts are runnable here too, and both leave nothing behind.

`browser-e2e`, `e2e-example` and `demo-shop` apply no convention plugin of this build, and each declares the versions
it needs inside its own directory, next to its own wrapper. That is what lets a reader copy one out and still have a
build that configures and runs, so their versions agreeing with the root's is a coincidence and not a rule. Each sets
`-Xlint:all -Werror` for itself.

What the root build enforces over their sources, it enforces by path: `spotlessCheck`, `verifyNoComments`,
`verifyCaughtExceptionNames` and `verifyTaskDescriptions` read every file in this tree, and so do
`verifySonarSuppressions` and the screen-name sweep of `AScreenIsMentionedByItsLabelInTheRailTest`, each walking this
tree for what it reads rather than listing the modules, which costs those three builds no plugin and no dependency on
`build-logic`. Checkstyle, Error Prone and NullAway stop at the root's own source sets, because they cannot be
moved by path: Checkstyle would make a copied-out build need `checkstyle/checkstyle.xml` from here, and NullAway is
told to annotate `dev.marcinromanowski.pspsim`, which is no package of theirs.

### Without Docker

```sh
./gradlew test -PwithoutDocker
```

skips the tests tagged `docker`. It is a fast local loop, not the gate: `check -PwithoutDocker` fails the coverage
thresholds. The full `./gradlew check` starts its own containers and needs only Docker running.
