package dev.marcinromanowski.pspsim.buildlogic

object ClickableCapabilities {

    private val controlRoutePrefix = ControlApi.controlRoutePrefix

    val screensThatOfferAControlCapability =
        listOf(
            Triple(
                "GET " + controlRoutePrefix + "/address",
                "GET /ui/pairing",
                "dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing#providerBaseUrlWithin",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/audit",
                "GET /ui/audit",
                "dev.marcinromanowski.pspsim.kernel.audit.AuditReads#pageOf",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/clock/advance",
                "POST /ui/configuration/clock/advance",
                "dev.marcinromanowski.pspsim.kernel.spi.ClockAdvanceAdmission#advance",
            ),
            Triple(
                "PUT " + controlRoutePrefix + "/config",
                "POST /ui/configuration/account",
                "dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission#apply",
            ),
            Triple(
                "PUT " + controlRoutePrefix + "/config",
                "POST /ui/configuration/clock",
                "dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission#apply",
            ),
            Triple(
                "PUT " + controlRoutePrefix + "/config",
                "POST /ui/configuration/route-alias",
                "dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission#apply",
            ),
            Triple(
                "PUT " + controlRoutePrefix + "/config",
                "POST /ui/configuration/settings",
                "dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission#apply",
            ),
            Triple(
                "DELETE " + controlRoutePrefix + "/config/accounts/{accountId}",
                "POST /ui/configuration/account/remove",
                "dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission#forget",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/config/fields",
                "GET /ui/configuration/account",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#accountFields",
            ),
            Triple(
                "DELETE " + controlRoutePrefix + "/config/route-aliases",
                "POST /ui/configuration/route-alias/release",
                "dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission#releaseRouteAlias",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/fixtures/payment-methods",
                "GET /ui/payment-methods",
                "dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures#of",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/fixtures/payment-methods",
                "POST /ui/payment-methods/seed",
                "dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission#seed",
            ),
            Triple(
                "DELETE " + controlRoutePrefix + "/fixtures/payment-methods/{fixtureId}",
                "POST /ui/payment-methods/remove",
                "dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission#forget",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/hosted/{providerObjectId}/complete",
                "POST /ui/objects/complete",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#complete",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/idempotency",
                "GET /ui/idempotency",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#keyedExchanges",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/objects",
                "GET /ui/objects",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#objects",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/objects/{providerObjectId}/follow-ups/{name}",
                "POST /ui/objects/follow-up",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#followUp",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/operations",
                "GET /ui",
                "dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary#operations",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/originations",
                "GET /ui/objects/new",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#originationFields",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/originations",
                "POST /ui/objects/start",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#originate",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/personas",
                "GET /ui/personas",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#personas",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/personas",
                "POST /ui/personas/seed",
                "dev.marcinromanowski.pspsim.kernel.spi.PersonaAdmission#seed",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/personas/preview",
                "POST /ui/personas/seed",
                "dev.marcinromanowski.pspsim.kernel.spi.PersonaAdmission#whatItWouldCreate",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/reads",
                "GET /ui/reads",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#reads",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/reads/answer",
                "GET /ui/reads/ask",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#read",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/repro-bundle",
                "GET /ui/repro-bundle",
                "dev.marcinromanowski.pspsim.kernel.spi.RecordedEvidence#of",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/repro-bundle/import",
                "POST /ui/repro-bundle/import",
                "dev.marcinromanowski.pspsim.kernel.spi.ReproductionAdmission#takeBack",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/reset",
                "POST /ui/dashboard/reset",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#reset",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/run-bundle",
                "GET /ui/run-bundle",
                "dev.marcinromanowski.pspsim.kernel.spi.RunBundles#of",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/run-bundle/import",
                "POST /ui/run-bundle/import",
                "dev.marcinromanowski.pspsim.kernel.spi.BundleAdmission#takeBack",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/scenarios",
                "GET /ui/scenarios",
                "dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules#of",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/scenarios",
                "POST /ui/scenarios/write",
                "dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission#write",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/scenarios/import",
                "POST /ui/scenarios/import",
                "dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission#takeBack",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/scenarios/presets",
                "POST /ui/scenarios/preset",
                "dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission#expand",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/scenarios/presets/preview",
                "POST /ui/scenarios/preset",
                "dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission#preview",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/scenarios/{scenarioId}",
                "GET /ui/scenarios/diff",
                "dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules#find",
            ),
            Triple(
                "DELETE " + controlRoutePrefix + "/scenarios/{scenarioId}",
                "POST /ui/scenarios/delete",
                "dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules#remove",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/scenarios/{scenarioId}/copy",
                "POST /ui/scenarios/diff/copy",
                "dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission#write",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/scenarios/{scenarioId}/diff",
                "GET /ui/scenarios/diff",
                "dev.marcinromanowski.pspsim.kernel.spi.ScenarioComparison#of",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/state/{providerObjectId}",
                "GET /ui/objects",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#state",
            ),
            Triple(
                "GET " + controlRoutePrefix + "/webhooks",
                "GET /ui/webhooks",
                "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher#attemptsOf",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/webhooks",
                "POST /ui/webhooks/send",
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#schedule",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/webhooks",
                "POST /ui/webhooks/retry",
                "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher#retry",
            ),
            Triple(
                "POST " + controlRoutePrefix + "/webhooks/deliver-due",
                "POST /ui/webhooks/deliver-due",
                "dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher#deliverDue",
            ),
        )

    val facetsOnlyTheApiOffers =
        listOf(
            listOf(
                "POST " + controlRoutePrefix + "/reset",
                "dev.marcinromanowski.pspsim.kernel.spi.ResetScope#declared",
                "A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD",
                "the dashboard resets the one test run a tester is looking at, which is the only " +
                    "scope a screen can name without one tester wiping another tester's run",
            ),
            listOf(
                "POST " + controlRoutePrefix + "/scenarios/{scenarioId}/copy",
                "dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy#of",
                "A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD",
                "the diff screen copies a rule by showing it in a form the tester edits and submits, " +
                    "so the copy grammar of properties given and cleared is read by no screen",
            ),
        )
}
