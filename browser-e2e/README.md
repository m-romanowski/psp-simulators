# The journeys and the layout, through a real browser

Everything else that tests these screens talks to Spring through `MockMvc` or `TestRestTemplate` and reads the HTML
back as a string; none of it ever loaded a page.

This is a separate Gradle build with its own wrapper, outside the root `settings.gradle.kts`, that drives the
simulator's screens with [Playwright for Java](https://playwright.dev/java/) in a real headless Chromium.

## Run it

```sh
./gradlew test
```

Nothing has to be running first. It needs Docker and a JDK.

## What it walks

| Journey                                 | What it walks                                                                                                                                                                                                                                                                                                                                                          |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `AnOperatorTakesAPaymentByClickingTest` | configures a grow account on the Configuration screen, picking how many refunds are allowed from the list of what that field accepts rather than typing a wire value, is refused once for leaving a required field empty, finds the pick still picked and the secret emptied, configures it properly, walks both steps of the origination wizard, starts a hold, finds the payment, finishes the hosted page as the payer, is refused a settlement it did not confirm, confirms it and reads `SUCCEEDED` back on the payment's own screen |
| `APayerFinishesGrowsHostedPageTest`     | the shop's server starts a process with `createPaymentProcess`; the app's webview opens the `url` grow answers, the payer picks a card and pays, and lands on the shop's `successUrl`; the shop's `notifyUrl` receives `statusCode` `11` naming the process, and `getPaymentProcessInfo` reads the same transaction back |
| `APayerFinishesWorldlinesHostedTokenizationPageTest` | the shop's server opens a hosted tokenization; the app's webview opens `hostedTokenizationUrl` directly, and again inside a shop page's `iframe`, where the payer never leaves the shop's page; the payer stores the published test card; the shop reads the token with `GET /v2/{merchantId}/hostedtokenizations/{id}` and charges it with `POST /v2/{merchantId}/payments` naming the `hostedTokenizationId` |
| `APayerSavesACardOnAutrasHostedPageTest` | the shop's server opens a card session; the app's webview opens `hostedCardUrl`, the payer picks a card by alias and saves it, and the page prints the slugs; the shop charges the `slugToken` over autra's sale route, and a slug the page did not hand back is refused |
| `APayerSavesACardInAzericardsWebviewTest` | the shop's server signs a card save; the app's webview posts it to `/token/cgi_link`; the payer types a published test card, reads **Payment successful** and returns to the shop's `MERCH_URL`; the shop gets the signed callback with a `TOKEN` and a masked `CARD`, reads the order with `TRTYPE=90`, then charges the card with `MERCH_TRAN_STATE=M` and gets its callback. A second walk types an unknown card, reads **Payment failed**, returns to the shop, and the callback and `TRTYPE=90` say it failed |
| `APayerHoldsFundsInAzericardsWebviewTest` | the same webview posts a signed `TRTYPE=0` hold to `/cgi-bin/cgi_link`; the payer returns to the shop; after the callback the shop completes it in full, in part, or releases it with `TRTYPE=22`, and reads the payment back with `TRTYPE=90` |
| `APayerFinishesTranzilasPaymentPageTest` | the shop's server locks the sum with `POST /v2/handshake/create`; the app's webview opens the DirectNG page with the `thtk`, the payer picks the published card and pays a `VK` hold, and the page posts the payer back to the shop's `success_url_address`; the shop's `notify_url_address` receives the return's fields and `ConfirmationCode`, and the shop's server forces the J5 with the `TranzilaTK` it was returned |
| `APayerFinishesTranzilasHostedFieldsTest` | the shop's server locks the sum with `POST /v2/handshake/create`; the app's webview opens the shop's own checkout, which loads `thostedf.js` from the simulator's Hosted Fields host and frames a third party's widget; paying with empty fields is refused by the frames in the vendor's `err` shape; the payer types the published test card, its expiry and its CVV into the frames and pays a `V` hold; the shop's page hears the vendor script's events; the charge callback hands it the answer, which it posts to the shop's server; the shop's `notify_url` receives the transaction data; the server verifies `response_hash` with the account secret and forces the J5 with the returned `token`; neither the shop's page outside the frames, nor any message its window or the third party's frame heard, nor its server, nor any answer or notify carries the card number, its first six or the CVV. The widget first hears a probe the shop's page posts it, so its silence about the card is a listener's. A second walk pays an amount the handshake does not lock, and the callback receives the hashed `912791` refusal; a third pays an Isracard number Luhn refuses and the Isracard check accepts, answered and notified as card type `5` |
| `APayerPassesTranzilasThreeDSecureChallengeTest` | the shop's server sends a `debit` of the published challenge card with `activate_3ds` `Y` and an `auth_3ds_redirect`; the app's webview opens the `challengeUrl` tranzila answers, the payer enters `555` and is posted back to the shop's redirect address with its params and the `track_id`; the shop's server completes the debit with `POST /v1/transaction/credit_card/3ds/complete`, reads the approved answer with its `t3ds_data`, and a second completion is refused `900`; the webview never sees the card number or its first six. A second walk types the challenge card into the Hosted Fields on the shop's own checkout: the challenge opens in a frame over the checkout, the payer enters `555` in it, the frame closes, the charge callback is handed the hashed answer of the `V` hold, the shop's `notify_url` is notified once, and a third party's frame hears neither the challenge nor its result |
| `APayerPaysWithAWalletOnTranzilasPaymentPageTest` | the app's webview opens the DirectNG page with `apple_pay=1` and `google_pay=1`; the payer taps the stand-in **Apple Pay** button, picks the published card as the card in the wallet on the stand-in sheet and confirms, and the page posts the payer back to the shop's `success_url_address` with the fields of a card `debit`; the shop's `notify_url_address` receives them and `ConfirmationCode`, and the shop's server reads the transaction with `POST /v1/transactions`, whose `broker` is `apple_pay` |
| `ATrafficRowOpensTheExchangeItIsTest`   | configures a grow account by clicking, has the shop's server start a process and read it back, then on the dashboard clicks a traffic row's **This exchange** and lands on the trail narrowed to that one audit id, clicks the same row's **Exchanges** and lands on every exchange of the object it touched, and clicks **This exchange** on a row that names no object at all |
| `ATesterReadsThePublishedApiTest`       | follows the **API** link from **This test run**, reads the first published definition with the method and path of every operation it publishes and no other, and switches through every document `index.json` lists, reading each one's title and operations                                                                                                               |

## What a mobile app's webview may ask for

The payer journeys extend `AJourneyInAMobileAppsWebview`. Each walks one chain: the shop's server calls the
provider's API, the app opens what the answer names in a phone-sized webview, the payer finishes the page, the payer
lands back at the shop where the provider sends one, the shop's endpoint receives the provider's callback where the
provider sends one, and the shop's server reads the result over the provider's API.

Only test setup reaches the simulator's own routes: `TheTestSetup` configures accounts and callback targets and resets
the run over `/__simulator`. Everything else is refused:

- A path counts as `/__simulator` or `/ui` after it is percent-decoded until it settles, its empty and `.` segments and
  `;` parameters are dropped, its `..` segments are resolved and it is lower-cased. The query and fragment never count.
- `TheWebview` records every request its browser context makes to such a path, from any page or frame, and aborts every
  one Playwright routes. Playwright routes only the first request of a redirect chain, so a redirect hop to such a path
  is recorded and not aborted.
- `TheProviderApi` is the only HTTP client the shop's server has; it refuses and records any call to such a path.
- `EveryPayerJourneyKeepsToTheProviderApiTest` reads every payer journey's source and fails the build if one uses an
  `APIRequestContext`, `.request()`, an HTTP client or connection of its own, `TheTestSetup`, a `TheProviderApi` of its
  own or a browser of its own, or calls `setup()` anywhere but a `@BeforeEach` method.
- After every journey, whether it passed or not, both records must be empty and no card number may have leaked, or the
  journey fails. These assertions run before the webview closes and the run is reset; a failing reset is attached to a
  failing assertion as suppressed, and fails the journey on its own otherwise.

A card number has leaked when a provider answer (status, headers and body), a callback, a body posted back to the
shop's return page, an answer of the Hosted Fields the shop's page posted to its server, a document the webview
received, a page it rendered, the page it showed last or an address it asked for or navigated a frame to carries:

- a run of 13 to 19 digits, taken across groups joined by a space or `-`, that begins with an issuer prefix and passes
  Luhn, or that is a number the payer typed or picked;
- the first six digits of a number the payer typed or picked, masked, next to its last four.

A page is read when it loads and once more at the end; a change a script makes to a page without loading it is read
only if it is still there at the end. A leak is reported by the card's last four digits, never the whole number.

Every callback is found by its own identity, the process id for grow, the `ORDER` for azericard and the
`transaction_id` for tranzila, never by its place in the order of arrival. The grow account and the azericard and
tranzila terminals each have two callback targets, and the shop's `notifyUrl`, `BACKREF` or `notify_url_address` names
the second: the callback must arrive there and nothing at the first.

| Step                                   | Grow                        | Worldline                                   | Autra                               | Azericard                                  | Tranzila | Tranzila Hosted Fields | Tranzila 3-D Secure | Tranzila wallet |
|----------------------------------------|-----------------------------|---------------------------------------------|-------------------------------------|--------------------------------------------|----------|------------------------|---------------------|-----------------|
| the shop's server calls the provider   | `createPaymentProcess`      | `POST .../hostedtokenizations`              | `POST /v1/acquiring/card-sessions`  | signs the form; no call                    | `POST /v2/handshake/create` | `POST /v2/handshake/create` | `POST /v1/transaction/credit_card/create` with `activate_3ds` `Y` | none: the app opens the page with the shop's parameters |
| what the app opens                     | `url`                       | `hostedTokenizationUrl`, bare or framed      | `hostedCardUrl`                     | the signed form, posted to `cgi_link`      | the DirectNG page with the `thtk` | the shop's own checkout loading `thostedf.js` | `challengeUrl` | the DirectNG page with `apple_pay=1` and `google_pay=1` |
| the payer finishes                     | picks a card, pays          | picks a card, stores it                     | picks a card, saves it              | types a card, pays                         | picks a card, pays | types a card, its expiry and CVV into the frames, pays | enters `555` | taps **Apple Pay**, picks the card in the wallet on the stand-in sheet, confirms |
| the payer lands back at the shop       | `successUrl`                | stays on the shop's page when framed         | not asserted: autra publishes no redirect | `MERCH_URL`                          | `success_url_address`, by an auto-submitted `POST` | stays on the shop's page; the charge callback is called | `auth_3ds_redirect` `url`, by an auto-submitted `POST` carrying its `params` and the `track_id` | `success_url_address`, by an auto-submitted `POST` |
| the shop's endpoint is called back     | `notifyUrl`, not the account's other target, `statusCode` `11` | not asserted: webhooks are not served     | not asserted: no tokenization webhook is published, no card webhook is served | `BACKREF`, not the account's other target, signed, masked `CARD`, found by `ORDER` | `notify_url_address`, not the account's other target, found by `transaction_id` | the charge's `notify_url`, not the account's other target, found by `transaction_id` | not asserted: the API publishes no notify | `notify_url_address`, not the account's other target, found by `transaction_id` |
| the shop's server reads the result     | `getPaymentProcessInfo`     | `GET .../hostedtokenizations/{id}`, then a payment | the sale's answer; `GET /v1/acquiring/payments/{id}` is not served | `TRTYPE=90`, and the MIT charge's answer | a `force` of the J5 with the returned `TranzilaTK` | verifies `response_hash`, then a `force` of the J5 with the returned `token` | `POST /v1/transaction/credit_card/3ds/complete` by `track_id`, once | `POST /v1/transactions` by `transaction_index`, reading `broker` `apple_pay` |

## What it measures of the layout

`EveryDestinationIsOneClickAwayTest` clicks every rail destination without scrolling at 1280x700, 1280x600 and
390x844, with a label too long for the rail, and checks the rail's group names reach 4.5:1 contrast.
`TheLayoutHidesNothingTest` checks that an open row is highlighted in every cell above the stripe, that nested tables do
not pin, that a control reached by shift-tab is never left under a pinned header or column or under the top bar, that a
phone keeps most of a table for the columns after the first, that long prose wraps inside a table, that a fact carrying
facts under it has them drawn as a group set in from it and ruled down its left edge, at desk and phone width in both
schemes, that the preset confirm sits beside its button, that the preset preview is a secondary button although it
posts, and that the API page has no rail gutter and none of the screens' table, details or button rules. It also checks
that a table of one `tbody` per entry stripes every second entry's summary row, that headings step up the type scale and
prose stops at 72 characters, and that every text reaches 4.5:1 and every field edge 3:1, with every `details` open and
opacity and alpha counted, on nineteen screens and the wizard's second step in the light and the dark scheme with no
sideways scroll at 390px in either. The same measure covers a success notice, a refusal, a caution and a badge of every
tone in both schemes. The API page is checked to read in the dark scheme in Swagger's own dark mode and after switching
Swagger to its light one.

The payment journey also checks that a status badge takes the colour of its tone, with a hold waiting for capture read
as pending, that a fact such as `capturable` is not painted as a status, that a lookup is a secondary button and the
wizard's "Next" a primary one, and that a green notice confirms each change it makes.

## What is asserted here that could not be asserted in a Spring test

- The script every `<head>` loads actually loaded and ran: `window.htmx` is an object, not just a mentioned path.
- No request in any journey was answered `4xx` or `5xx`, counting every request the browser made of its own accord.
- The browser console carried no error and no script threw.
- The page does not scroll sideways at 390px, on the account form, the Configuration screen, the second step of the
  wizard, the payment's own screen, the API page, grow's hosted page, worldline's hosted tokenization page, autra's
  hosted card page, azericard's card page, tranzila's payment page, its stand-in Apple Pay sheet, tranzila's 3-D Secure challenge, and a shop's checkout carrying tranzila's Hosted Fields with and without the challenge open over it.
- A form posts what the markup says it posts: every field is filled by its visible label, every button clicked by
  its visible text.
- A redirect chain ends where it should: the payer's `303` off grow's hosted page lands on the shop's `successUrl`, and
  azericard's **Return to the merchant** link on the shop's `MERCH_URL`, and tranzila's auto-submitted return on the
  shop's `success_url_address`, and its 3-D Secure challenge's on the shop's `auth_3ds_redirect` `url`.

The one thing deliberately not watched is `/favicon.ico`: the simulator ships no icon and no screen asks for one, so a
browser fetching it on its own gets a `404` that says nothing about a journey.

## What it downloads

This build fetches only Chromium's headless shell (about 195 MB on disk), not Playwright's full four-browser set:
`installTheBrowserThisSuiteDrives` runs `playwright install chromium --only-shell` and the test task sets
`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`. `-Dbrowser.headed=true` needs the full Chromium, which `--only-shell` does not
fetch; install it once with `./gradlew test -Dbrowser.headed=true -PtheWholeBrowser` and it stays cached.
`-Dbrowser.slowMo=250` slows a headed run down enough to follow.
The `playwright` dependency brings a 204 MB `driver-bundle` jar, cached once in the Gradle module cache; the browser is
cached in `~/Library/Caches/ms-playwright` (macOS) or `~/.cache/ms-playwright` (Linux).

## Why it is a separate build

Its own `settings.gradle.kts` and wrapper, outside the root build, written from outside with no access to the
simulator's internals, so its only contract with the simulator is HTTP: `EveryPayerJourneyKeepsToTheProviderApiTest`
is the source rule that keeps every payer journey to a provider's own API, and `TheWebview` aborts any `/__simulator`
or `/ui` request one of them makes.

## Pointing it at something already running

```sh
./gradlew test -Dsimulator.baseUrl=http://127.0.0.1:8080
```

It then starts nothing and stops nothing, and fails rather than quietly booting one of its own if that address does
not answer readiness. `SIMULATOR_BASE_URL` works the same way as an environment variable.
`-Dsimulator.jar=/path/to/psp-simulators.jar` names a jar and skips the build. Given neither, it builds the boot jar
with the root wrapper every time.

## Where the pieces are

| File                                      | What it is                                                                                                                             |
|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `SimulatorUnderTest.java`                 | starts, waits for and stops the simulator, or uses one it was given                                                                    |
| `TheBrowser.java`                         | the browser, the viewport, and the watcher that records every console error, uncaught exception, failed request and `4xx`/`5xx` answer |
| `TheMerchant.java`                        | the shop's own endpoint: the two pages a payer is redirected or posted back to, keeping what was posted, the page that frames a provider's page, and two callback targets, with every callback found by a field it carries |
| `TheTestSetup.java`                       | the only fixture that calls `/__simulator`: configures a grow, worldline, autra, azericard or tranzila account and its callback target, and forgets the test run afterwards |
| `TheProviderApi.java`                     | the shop's server's only HTTP client, which refuses any `/__simulator` or `/ui` address and keeps every answer it got                |
| `ATesterRoute.java`                       | what counts as a `/__simulator` or `/ui` address, after the path is made canonical                                                   |
| `ACardNumber.java`                        | what counts as a card number that leaked                                                                                             |
| `EveryPayerJourneyKeepsToTheProviderApiTest.java` | the source rule every payer journey keeps                                                                                    |
| `TheWebview.java`                         | a phone-sized browser context that records and aborts requests to a `/__simulator` or `/ui` address, and keeps every address, document and page it saw |
| `NotThroughTheBrowser.java`               | what the shop's server asks of grow, worldline and autra over their own APIs                                                         |
| `AzericardShopBackend.java`               | what the shop's server signs and asks of azericard over its own API                                                                  |
| `TranzilaShopBackend.java`                | what the shop's server signs and asks of tranzila over its own API: the handshake, the force, the lookup, the 3-D Secure debit and its completion, and the `response_hash` check of a Hosted Fields answer |
| `AJourneyInARealBrowser.java`             | what every tester journey opens, closes and asserts in common                                                                        |
| `AJourneyInAMobileAppsWebview.java`       | what every payer journey opens, closes and asserts in common, including that neither the app nor the shop's server asked for a `/__simulator` or `/ui` address and that no card number leaked |
| `AnAzericardWebviewJourney.java`          | the azericard terminal, the app screen that posts a signed form, and the payer's steps on azericard's card page                      |
| `ATesterReadsThePublishedApiTest.java`    | the API journey: reads the published OpenAPI documents and switches between them                                                       |
| `ATrafficRowOpensTheExchangeItIsTest.java` | the dashboard's traffic rows: each one links to the exchange it is, narrowed to its own audit id, whether or not it touched an object |
| `EveryDestinationIsOneClickAwayTest.java` | the rail: every destination clickable unscrolled at three window sizes, and readable group names                                       |
| `TheLayoutHidesNothingTest.java`          | the layout: highlights, pins, focus, wrapping, and where the screens' rules stop                                                       |

## What this deliberately leaves out

No refund, no cancel, no clock, no scenario rule. Worldline is walked only on its hosted tokenization page, autra only on
its hosted card page.
