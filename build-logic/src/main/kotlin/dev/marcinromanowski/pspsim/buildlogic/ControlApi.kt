package dev.marcinromanowski.pspsim.buildlogic

object ControlApi {

    val controlRoutePrefix = "/__simulator/{provider}"

    val openApiControlAnswers =
        mapOf(
            "POST " + controlRoutePrefix + "/reset" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.control.ControlPayloads\$ResetResponse"),
            "PUT " + controlRoutePrefix + "/config" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ConfigurationResponse"),
            "POST " + controlRoutePrefix + "/clock/advance" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.control.ControlPayloads\$ClockResponse"),
            "DELETE " + controlRoutePrefix + "/config/accounts/{accountId}" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$AccountRemovedResponse"),
            "DELETE " + controlRoutePrefix + "/config/route-aliases" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$RouteAliasReleasedResponse"),
            "POST " + controlRoutePrefix + "/fixtures/payment-methods" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$FixtureResponse"),
            "GET " + controlRoutePrefix + "/fixtures/payment-methods" to
                ("ARRAY" to "dev.marcinromanowski.pspsim.control.ControlPayloads\$FixtureResponse"),
            "DELETE " + controlRoutePrefix + "/fixtures/payment-methods/{fixtureId}" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$FixtureRemovedResponse"),
            "POST " + controlRoutePrefix + "/webhooks" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$WebhookResponse"),
            "POST " + controlRoutePrefix + "/webhooks/deliver-due" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$DeliverDueResponse"),
            "GET " + controlRoutePrefix + "/webhooks" to
                ("ARRAY" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$WebhookDeliveryView"),
            "POST " + controlRoutePrefix + "/scenarios" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ScenarioResponse"),
            "GET " + controlRoutePrefix + "/scenarios" to
                ("ARRAY" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ScenarioResponse"),
            "POST " + controlRoutePrefix + "/scenarios/presets" to
                ("ARRAY" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ScenarioResponse"),
            "POST " + controlRoutePrefix + "/scenarios/presets/preview" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument"),
            "POST " + controlRoutePrefix + "/scenarios/import" to
                ("ARRAY" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ScenarioResponse"),
            "GET " + controlRoutePrefix + "/scenarios/{scenarioId}" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ScenarioResponse"),
            "DELETE " + controlRoutePrefix + "/scenarios/{scenarioId}" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ScenarioResponse"),
            "POST " + controlRoutePrefix + "/scenarios/{scenarioId}/copy" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ScenarioResponse"),
            "GET " + controlRoutePrefix + "/scenarios/{scenarioId}/diff" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.kernel.spi.ScenarioComparison\$Comparison\$Compared"),
            "GET " + controlRoutePrefix + "/personas" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$PersonaCatalogueResponse"),
            "POST " + controlRoutePrefix + "/personas" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$PersonaSeededResponse"),
            "POST " + controlRoutePrefix + "/personas/preview" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$PersonaPlanResponse"),
            "GET " + controlRoutePrefix + "/address" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$AddressResponse"),
            "GET " + controlRoutePrefix + "/config/fields" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$AccountFieldsResponse"),
            "GET " + controlRoutePrefix + "/operations" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$OperationsResponse"),
            "GET " + controlRoutePrefix + "/originations" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$OriginationsResponse"),
            "POST " + controlRoutePrefix + "/originations" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$OriginationResponse"),
            "GET " + controlRoutePrefix + "/reads" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.control.ControlPayloads\$ReadsResponse"),
            "GET " + controlRoutePrefix + "/reads/answer" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$ReadAnswerResponse"),
            "GET " + controlRoutePrefix + "/run-bundle" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.kernel.bundle.RunBundle"),
            "POST " + controlRoutePrefix + "/run-bundle/import" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.kernel.bundle.BundleReport"),
            "GET " + controlRoutePrefix + "/repro-bundle" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle"),
            "POST " + controlRoutePrefix + "/repro-bundle/import" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.kernel.repro.ReproductionReport"),
            "POST " + controlRoutePrefix + "/hosted/{providerObjectId}/complete" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$HostedCompletionResponse"),
            "GET " + controlRoutePrefix + "/state/{providerObjectId}" to
                ("OBJECT" to "dev.marcinromanowski.pspsim.control.ControlPayloads\$StateResponse"),
            "GET " + controlRoutePrefix + "/idempotency" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$KeyInspectorResponse"),
            "GET " + controlRoutePrefix + "/objects" to
                ("ARRAY" to "dev.marcinromanowski.pspsim.control.ControlPayloads\$StateResponse"),
            "POST " + controlRoutePrefix + "/objects/{providerObjectId}/follow-ups/{name}" to
                ("OBJECT" to
                    "dev.marcinromanowski.pspsim.control.ControlPayloads\$FollowUpResponse"),
            "GET " + controlRoutePrefix + "/audit" to
                ("ARRAY" to "dev.marcinromanowski.pspsim.control.ControlPayloads\$AuditRecordView"),
        )

    val openApiRefusalRecords =
        listOf(
            "dev.marcinromanowski.pspsim.control.ControlPayloads\$ControlError",
            "dev.marcinromanowski.pspsim.control.ControlPayloads\$ControlRefusal",
        )
}
