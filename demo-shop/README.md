# A demo shop that pays through the simulator

A small web shop that sells one espresso machine and pays through the simulator as Stripe, using the vendor's own
client `com.stripe:stripe-java` and its own webhook endpoint; it knows nothing else about the simulator except the
provider-neutral control API, which only its **story panel** uses.

A story is a test someone would otherwise write in code, played by clicking instead: it opens a run of its own, sets
it up through the control API, and walks you through buying the machine, with every step linking into the simulator's
screens for that run and payment so you can watch both sides.

## Run it

From this directory:

```sh
docker compose up --build
```

That builds the simulator from the repository's own `Dockerfile`, builds this shop, and starts both beside a PostgreSQL.
The first build takes a few minutes. Then open:

| What               | Where                                                |
|--------------------|------------------------------------------------------|
| the shop           | [http://localhost:8081](http://localhost:8081)       |
| the simulator's UI | [http://localhost:8082/ui](http://localhost:8082/ui) |

Both are bound to `127.0.0.1` only. `docker compose down -v` stops everything and deletes the database's volume, and
with it every run.

This stack publishes its simulator on 8082 so it can run beside the repository's own `docker compose up --wait`, which
publishes one on 8080. Inside this stack the simulator still listens on 8080, which is the address the shop's server
calls it on.

The simulator's image binds `0.0.0.0` inside its container, so run it alone with `-p 127.0.0.1:8080:8080`; a bare
`-p 8080:8080` publishes it on every host interface.

## What to click

1. On the shop's home page, press **Play A decline the test asked for**.
2. The product page opens with the story panel beside it. It lists what the story told the simulator: a Stripe account
   whose secret key belongs to this run alone, its webhooks aimed at this shop, the clock pinned, a saved Visa ending
   4242 for checkout to pay with, and a scenario rule refusing every capture of this run as `expired_card`. Each has a
   link to the simulator screen that shows it.
3. **Go to checkout**, then **Pay 120.50 USD**. The shop creates a PaymentIntent with `capture_method=manual` and
   confirms it with the saved card, so the money is held rather than taken.
4. On the order page, press **Capture 120.50 USD**. Stripe refuses it, and the order page says *Capture refused* with
   the code `expired_card`.
5. Follow **Its trail** in the story panel. The simulator's Audit screen opens on this payment, and its capture row
   names the scenario rule that decided it.

Play **A capture that goes through** next — same journey, no rule — then press **Send payment_intent.succeeded**. The
order page polls every 2 s until the webhook arrives, then shows it without a reload. A poll of a run tidied up
meanwhile gets `404` and stops. **Tidy up** resets a run and removes its
account, as far as the simulator lets a client go (see [What tidy up leaves behind](#what-tidy-up-leaves-behind));
restarting the shop forgets its runs, since they live only in its memory, while the simulator keeps them until
`docker compose down -v`.

## Where to look in the simulator

| Link in the story panel | Screen                                                           | What it shows for this run                                                  |
|-------------------------|------------------------------------------------------------------|-----------------------------------------------------------------------------|
| See it configured       | `/ui/configuration?provider=stripe&testRunId=…`                  | the account, the webhook target, the pinned clock                           |
| See the saved card      | `/ui/payment-methods?provider=stripe&testRunId=…`                | the Visa checkout pays with                                                 |
| See the rule            | `/ui/scenarios?provider=stripe&testRunId=…`                      | the rule refusing every capture as `expired_card`                           |
| The payment             | `/ui/objects?provider=stripe&testRunId=…&providerObjectId=pi_…`  | the PaymentIntent, its native and normalized status, and its balances       |
| Its timeline            | `/ui/timeline?provider=stripe&testRunId=…&key=pi_…`              | every call and callback about this payment, in order                        |
| Its trail               | `/ui/audit?provider=stripe&testRunId=…&providerObjectId=pi_…`    | the requests the shop made and the answers, with the rule that decided each |
| Its webhooks            | `/ui/webhooks?provider=stripe&testRunId=…&providerObjectId=pi_…` | each delivery and every attempt, with the headers it put on the wire        |

## How a story drives the simulator

Playing a story calls the control API under `/__simulator/stripe`:

| Call                                                                | When                                                                                                                                                                                                           |
|---------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PUT /config`                                                       | sets up the run: a fresh `testRunId`, the clock pinned, an account with its own `secretKey`, `webhookSecret` and `captureWindow` (`P7D`), and the webhook target `{callback base}/webhooks/stripe/{testRunId}` |
| `POST /fixtures/payment-methods`                                    | seeds the saved Visa as `pm_…` of this run                                                                                                                                                                     |
| `POST /scenarios`                                                   | decline story only: refuses every capture as `expired_card`                                                                                                                                                    |
| `POST /webhooks`                                                    | happy story only: asks for `payment_intent.succeeded`                                                                                                                                                          |
| `POST /reset`, then `DELETE /config/accounts/demo-shop?testRunId=…` | Tidy up. Only a `200` counts as the account removed                                                                                                                                                            |

If any of these fails, the run is not kept: the shop answers `502` and tidies up what it wrote for that run straight
away. The shop itself pays through `stripe-java`: `POST /v1/payment_intents` with `confirm=true`, then its capture. Only
the create carries an `Idempotency-Key`, `hold-order-{testRunId}`, the same for every attempt at the one order, so a
retried create reuses the same hold rather than opening a second one.

Each webhook is verified with `Webhook.constructEvent` against the run's `webhookSecret`, with Stripe's default
5-minute tolerance judged by the run's pinned clock. A malformed `Stripe-Signature` is answered `400`.

## What tidy up leaves behind

Reset clears a run's payments, saved card, scenario rules, queued webhooks and trail; removing the account takes the
account and its webhook target away. The simulator has no route that deletes a test run, so what remains of a tidied
run is its registration, its pinned clock, and the trail rows the reset and the account removal themselves wrote. Only
`docker compose down -v`, which deletes the database's volume, forgets a run completely.

## Configuration

| Property                         | Environment variable             | Default                 | What it is                                                       |
|----------------------------------|----------------------------------|-------------------------|------------------------------------------------------------------|
| `shop.simulator-api-url`         | `SHOP_SIMULATOR_API_URL`         | `http://127.0.0.1:8080` | where the shop's server calls Stripe and the control API         |
| `shop.simulator-browser-url`     | `SHOP_SIMULATOR_BROWSER_URL`     | `http://localhost:8080` | where a browser opens the simulator's screens                    |
| `shop.callback-base-url`         | `SHOP_CALLBACK_BASE_URL`         | `http://127.0.0.1:8081` | where the simulator posts this shop's webhooks                   |
| `shop.simulator-connect-timeout` | `SHOP_SIMULATOR_CONNECT_TIMEOUT` | `2s`                    | how long a call to Stripe or the control API may take to connect |
| `shop.simulator-read-timeout`    | `SHOP_SIMULATOR_READ_TIMEOUT`    | `10s`                   | how long such a call may wait for its answer                     |
| `server.port`                    | `SHOP_PORT`                      | `8081`                  |                                                                  |
| `server.address`                 | `SHOP_BIND_ADDRESS`              | `127.0.0.1`             |                                                                  |

Under compose the first and third are `http://simulator:8080` and `http://shop:8081`, the container names, while the
second is `http://localhost:8082`, the port this stack publishes the simulator on, because it is the address your
browser uses.

The shop has no CSRF protection, deliberately: it is a demo bound to loopback only, and every form in it acts on test
runs of its own.

## The tests

```sh
./gradlew test
```

Drives the shop at its own boundary — the stories, the checkout, the webhook endpoint — with an in-memory stand-in for
the simulator that plays both halves of it. Nothing needs to be running first.

```sh
./gradlew walkTheShop
```

Walks both stories in a real headless Chromium against a real simulator, clicking the buttons a person clicks. It
builds the simulator's boot jar with the repository's own wrapper and starts it beside a PostgreSQL container, so it
needs Docker and nothing running. `-Dsimulator.baseUrl=http://127.0.0.1:8080` points it at a simulator already running
on this host instead — not the one `docker compose` starts, because the walk's shop listens on `127.0.0.1` of the host
and a simulator inside a container cannot post its webhooks there. `-Dsimulator.jar=…` names a jar to run. It fetches
only Chromium's headless shell, as `browser-e2e` does.

```sh
./gradlew check
```

runs both.

## Why it is a separate build

A separate Gradle build, written from outside, for the reason `e2e-example` gives: package `com.example.shop`, HTTP
only — the Stripe routes and the control API.

## What it leaves out

Stripe only, two stories, one product: no refund, no cancel, no second provider.
