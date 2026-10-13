# Contributing a Screen

A provider-specific screen is one HTML file dropped at
`src/main/resources/templates/extensions/<provider>/<screen>.html`. The core discovers it, serves it at
`GET /ui/x/<provider>/<screen>`, and offers a tab for it in the navigation. What the screen is, is none of the core's
business: the file says what its tab reads and where the tab sits, and the core puts a fixed set of attributes in the
model and nothing else.

`src/main/resources/templates/extensions/autra/pix.html` is the worked example.

Each rule below says what enforces it. **Refused at startup** means the application does not start and the message names
the file; the same refusals also fail `verifyClickableCapabilities`, which re-runs discovery. **Convention** means
nothing checks it.

## Where the file goes

| Rule | Enforced by |
| --- | --- |
| Exactly one folder under `extensions/`, that folder naming the provider. Deeper, or loose directly under `extensions/`, is refused: the route is built out of the folder and the file name and has room for nothing else. | `ContributedScreens`, refused at startup |
| The folder and the file name each match `[a-z][a-z0-9-]*`, because both become path segments. | `ContributedScreens`, refused at startup |
| The folder names a provider this build carries. The refusal lists the providers it does carry. | `TheRail`, refused at startup |
| Only `*.html` is discovered, by the classpath glob `classpath*:templates/extensions/**/*.html`. | `ContributedScreens` |
| Any other file under a provider folder is never read, never served and never reported. | Convention |

There is no place for a shared fragment under `extensions/<provider>/`: every `.html` there is read as a screen, so a
partial dropped in is refused for declaring no tab. Shared markup belongs in `templates/fragments.html`.

Pages are read in order of provider then screen, so the classpath never decides tab order.

Discovery runs once, when `ContributedScreens` is constructed. Adding or editing a file while the simulator runs creates
no route and re-runs none of these checks.

## The route

`ContributedScreenController` maps `/ui/x/{provider}/{screen}`, GET only, with an optional `testRunId` request
parameter. It decides nothing and delegates to `ContributedPage`. The view rendered is `extensions/<provider>/<screen>`.

An address no contributed file answers is a 404 (`UnknownContributedScreenException`) listing the paths this build does
serve. The route matches against the discovered list and opens no file by the name in the URL, so no path in the URL
reaches a file the contributors did not put there.

## The three declarations

`ContributedScreens` reads three `<meta>` names: `tab`, `group`, `order`. All three are required and none has a default.

| Declaration | What it is | Refused at startup when |
| --- | --- | --- |
| `tab` | The label the navigation prints. Nothing derives it from the file name, because a tab reading `pix` is a word a tester either already knows or does not. | missing or blank; or it reads like a tab already offered (see below) |
| `group` | Which part of a tester's work the screen belongs to. One of the four tokens below. | missing, blank, or not one of the four |
| `order` | A whole number, which sorts the tab inside its own group. | missing, blank, or not a whole number |

How the scan reads them:

- The whole file is scanned for `<meta ...>`, not only the head. A meta in the body counts exactly the same. Keeping
  them in the head is convention.
- Attribute values must be double-quoted. `name='tab'` or an unquoted value is invisible, so the declaration counts as
  missing.
- The `name` is matched case-insensitively; the `content` is taken verbatim, so a group token's case and hyphens matter.
- A missing `content` attribute reads as blank.
- The first meta of a given name wins; a later duplicate is ignored without a word.
- The declarations exist for this scan alone. The example replaces its whole `<head>` with the `head` fragment, so they
  never reach a browser.

`order` sorts only within the screen's own group, and a tie breaks alphabetically by label.

## The groups

`RailGroup` defines the whole set. The tokens are matched by exact string equality:

| Token | Heading in the navigation |
| --- | --- |
| `daily-work` | Daily work |
| `set-up` | Set up |
| `evidence` | Evidence |
| `take-it-elsewhere` | Take it elsewhere |

Groups are drawn in that order. Inside a group the contributed tab is interleaved with the core tabs by `order`, so a
screen chooses where among them it sits. A group holding no tab is not drawn at all.

A contributed tab is offered on its own provider's navigation and on no other (`TheRail`).

## The tab label

`TheRail` refuses at startup a label that reads like a tab the navigation already offers, comparing labels stripped and
lower-cased, because a tester reading two tabs of one name cannot tell which screen either one opens. The refusal names
both. It applies to:

- the core tabs, whatever group they are in. The list is `TheRail.theScreensThisSimulatorCarries`.
- another contributed tab of the same provider, whatever group it is in.

Two providers may contribute the same label, because no tester sees both rails at once.

A screen's own page titles itself with the label the navigation reads
(`TheTitleOfAScreenIsItsLabelInTheRailTest`), and words on any screen that send a tester to another one spell that
screen's label in the navigation's own case: the phrase `<name> screen` in any template or Java source of this
repository is refused unless `<name>` is the label that screen is reached by
(`AScreenIsMentionedByItsLabelInTheRailTest`). The name the code knows a screen by is refused there too, so
`/ui/pairing` is "the Connect my app screen" and never "the Pairing screen", and a contributed page is named by the
`tab` it declares and never by its file name. Both the core tabs and every
contributed tab are in that sweep. Two spellings of one place are two places to a tester who cannot read the code.

The sweep finds its own trees: every directory named `src` in this repository, outside what git ignores and what this
build generates, so a module added later is swept without that test being touched, and finding fewer trees than this
build has modules fails it. In each it reads every `.java`, `.kt` and `.html` file, so a screen named in a test — in an
assertion description, or in a measurement a test prints — is held to the label too, and a tree that contributes no
file fails the sweep, because a sweep that read nothing reports the zero a sweep that found nothing reports. It passes
over one file, its own source, where the wrong-cased names it asserts on are written, and over exactly as many of them
as it is written around: one more is a mention nothing reads in the one file it cannot look in, one fewer is an
exemption with nothing left to exempt, and a rename of that file fails it. It does not read `doc/`, because no test
here reads documentation prose.

## What the model carries

`ContributedPage` builds the model; `TheShellOnEveryScreen` adds the navigation to it afterwards. A contributed page
reads these and nothing else.

Always present:

| Attribute | Type | Note |
| --- | --- | --- |
| `providers` | `List<String>` | every provider id this build carries |
| `path` | `String` | `/ui/x/<p>/<s>`; the shell's provider and test-run form comes back here |
| `screen` | `String` | `x/<p>/<s>`; what the navigation marks `aria-current` on |
| `provider` | `String` | the folder name, re-stated as the provider id once a run is named |
| `testRunId` | `String` | may be null; non-null once a run is named |
| `theScreenIsOneProvidersOwn` | `Boolean` | always true, which is why the shell offers a link away instead of a provider picker. A contributed page draws no provider picker of its own. |
| `rail` | `List<RailGroupOnScreen>` | from `TheShellOnEveryScreen` |

Then exactly one of two branches:

**A test-run id that cannot be read** adds `refusal` (a `String`) and nothing else. Every attribute in the next table is
**absent**, not null, so a panel that does not guard on `${objects != null}` or `${refusal}` fails to render. This is
the only refusing branch a contributed page can reach: the provider is the folder name and was proved to be a real
provider at startup.

**A run named, or defaulted to `simulator.default-test-run-id`** adds:

| Attribute | Type | Note |
| --- | --- | --- |
| `objects` | `List<ContributedObject>` | at most 100 rows; each carries the provider object state, the follow-up forms it offers and its payer step |
| `theWholeRunWasRead` | `boolean` | false when the run holds more than 100 objects **or** when the provider refused the read; true for an empty run and for exactly 100. A screen that says "this run holds none" after a short read is stating something it did not read. |
| `accounts` | `List<String>` | the accounts configured for this provider and run |
| `now` | `Instant` | the run's own clock |
| `inWords` | `AMomentInWords` | renders a moment relative to `now` |
| `typed` | `WhatWasTyped` | never null; carries what a tester typed into a form that was refused |

`Selection.Unnamed` puts a `choose` attribute, and the `choose` fragment exists for it, but that branch cannot be
reached through `/ui/x/**`, because the provider is always named by the path.

`refusal`, `done` and `typed` also arrive as flash attributes after a form posted from this screen. Otherwise `refusal`
and `done` are absent.

## Fragments

`templates/fragments.html` defines: `head(title)`, `bar`, `refusal`, `done`, `fieldLabel(field)`, `whatItIsFor(field)`,
`whatAFormRequires(required, has, secrets)`, `status(badge)`, `choose`, `fields(title, fields)`,
`whatThatIdNames(objects)`, `personalData`.

There is no separate shell, rail or topbar fragment. `bar` is all of the chrome: the `div.shell`, the topbar with the
skip link and the provider and test-run form, and `nav.rail`.

| Fragment | Standing |
| --- | --- |
| `bar` | Mandatory in practice. `EveryContributedScreenObeysTheCoreTest` in the browser suite fails a contributed screen that does not render exactly one `nav.rail`, or that marks a tab other than its own as the one a reader is on. Only `bar` satisfies both. |
| `status(badge)` | The only sanctioned way to colour a status; a status tone is `StatusBadge`'s to decide (`ColourBelongsToStatusTest`). |
| `head(title)` | Convention. Omit it and the page is served with no stylesheet, no title and no htmx, and nothing refuses it. |
| `refusal`, `done` | Convention. Omit them and a tester sent back here by a form sees nothing saying what happened. |
| the rest | Available, unused by the example. |

`xmlns:th` on `<html>`, a `<main>` wrapper, and the class vocabulary of `src/main/resources/static/ui.css` are
convention.

## Forms

A contributed page has no controller. It posts to the core's own routes.

All five of those routes read a `returnTo` field: `POST /ui/objects/start` (`OriginationController`), and
`POST /ui/objects/complete`, `POST /ui/objects/follow-up`, `POST /ui/objects/act` and `POST /ui/objects/webhook`
(`ObjectController`). Every one of them honours it on its refusals as well as on the answer it gives when the provider
did the thing, so a contributed screen keeps the tester where they clicked.

`TheRail.theScreenToReturnTo` checks `returnTo` against an allow-list by **exact string equality**: every core
destination that carries the provider and test-run selection, plus the `path()` of every contributed screen. There is no
prefix match and no normalisation, because a form that came back wherever a parameter told it to would be an open
redirect. So a query string, a trailing slash, an absolute URL and `/openapi/index.html` all fail it.

Nothing refuses a form that omits `returnTo` or misspells it. The post succeeds, the allow-list falls back to the screen
the form posted to, and the tester is redirected there with the refusal or the notice drawn on that screen instead.

A refusal carries what the tester typed back with it, in a `WhatWasTyped` flashed alongside the refusal and put in the
model as `typed`. A page redraws a field from it, or the tester types the value again:

| Form | What `typed` carries | Read it as |
| --- | --- | --- |
| `/ui/objects/start` | every input the provider declared, under the bare field name | `typed.valueOf('amount')` |
| `/ui/objects/complete` | the payer step and the card, against the object they were chosen for | `typed.theStepChosenFor(id)`, `typed.theCardChosenFor(id)` |
| `/ui/objects/act` | the amount, keyed by the action it was typed against | `ObjectScreen.Offered.amountDrawnFrom(typed)` |
| `/ui/objects/webhook` | the event type | `typed.valueOf(DeliveryForm.THE_EVENT_TYPE_A_SEND_NAMES)` |
| `/ui/objects/follow-up` | nothing; the form has no field to type in | |

The last two are keyed apart from the bare field names, because a page drawing a start form reads those names and would
otherwise fill a box the tester never typed in. The action's amount carries its action in the key for the same reason:
one payment draws one amount box per offered action, all named `amount`.

Other fields these routes read:

- `provider` and `testRunId` on every form; `providerObjectId` on every form but `start`, which creates the object.
- `confirm` must be exactly `yes` on `complete`, `follow-up`, `act` and `webhook`, which each refuse without it.
- `/ui/objects/start` reads the provider's own declared inputs under the `field.` prefix
  (`TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL`, the one declaration both ports and both surfaces read)
  and the kind of payment under `startedAs`, whose value is the provider's own wire spelling.

There is no CSRF token to carry: this build has no Spring Security, and no `/ui` form has one.

There is no message bundle. Labels are literal English in the markup.

## A page reaches no bean of this application

A contributed page reads the model its screen handed it and nothing else. A page that can reach one bean can reach the
one holding the database password, and contributing a screen is dropping a file in, so this is enforced twice, both
through `ATemplateReadsOnlyItsModel`:

- **At startup**, `ContributedScreens` scans the markup and refuses a file that names any `@bean` inside a `${}`, `*{}`
  or `#{}` expression, listing what it reached. Character references are resolved before the scan, so `&commat;` does
  not slip past; text inside single quotes is skipped.
- **At render time**, `TheEngineTheScreensAreRenderedBy` replaces Thymeleaf's Spring dialect with one that takes the
  bean resolver off the evaluation context. A file edited after startup therefore fails its own request rather than
  being served, with a message naming the bean it asked for and no value from it.

Any other unreadable expression is a 500 whose body names the file, the line and the property, drawn inside the shell.

## What the build and the suites refuse

| Check | What it does to a contributed screen |
| --- | --- |
| `verifyNoComments` | Scans `src/main/resources/templates/**/*.html` for the four characters that open a markup comment, wherever they stand, and fails the build naming the file and the line. There is no exemption: put the fact in a test, or in `doc/`. |
| `verifyClickableCapabilities` | Constructs `ContributedScreens` itself, so **every refusal above is a Gradle failure before anything boots**: a contributed file that is wrong is named by `./gradlew verifyClickableCapabilities` without starting the application. It also requires each `/ui/x/<p>/<s>` the navigation offers to be answered by a route, and the route `/ui/x/{provider}/{screen}` to be reached by some destination — a build shipping no contributed screen at all fails it, because no page links that route and only a concrete destination can match it. |
| `EveryContributedScreenObeysTheCoreTest` (browser suite) | Walks every provider's navigation, clicks every `/ui/x/` tab it finds, and fails a screen that prints a card number in rendered text or in delivered markup, delivers an HTML comment, marks another tab as current, renders other than one `nav.rail`, or pushes the document wider than a 390px phone viewport. It reads the navigation rather than a list, so a new screen is held to this without being named anywhere. |
| `verifyEveryFormSendsWhatItCarries` | Reads every `<form>` of every template, including a contributed one, and fails a form a browser submits as a query that names a value only in the address it submits to. A browser replaces the query of that address with the fields of the form, so such a value never reaches the route; name it in a hidden input beside the fields a tester fills in. A form naming no method at all is one of these, and a path variable the address binds is not a value the query carries. |
| `spotless`, `checkstyle` | Neither touches HTML. |

A contributed screen needs no entry in `screensThatOfferAControlCapability`: its route reads and changes nothing, and
the example reuses screen routes that are already bound. A **new control route** does need one.

## Tests that hold the contract

`ContributedScreensTest`, `TheRailTest`, `ContributedPageTest`, `RailGroupTest`, `ATemplateReadsOnlyItsModelTest`,
`TheEngineTheScreensAreRenderedByTest`, `TheScreenAFormIsAnsweredOnTest`, `NoTemplateReachesTheApplicationTest`,
`AScreenThisBuildDoesNotServeIntegrationTest`, `AContributedPageThatReachesForABeanIntegrationTest`,
`AContributedPageThatCannotRenderIntegrationTest`, `APixScreenAnswersItsOwnFormsIntegrationTest`,
`ThePixScreenSaysOnlyWhatItReadTest`, `TheWayOffAScreenOfOneProvidersOwnTest`.
