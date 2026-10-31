package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StripeResponseSchemaTest {

  private static final StripeResponseSchema SCHEMA =
      new StripeResponseSchema(
          StripeJsonReader.read(
              """
      {
        "sample": {
          "type": "object",
          "properties": {
            "integer": {"type": "integer"},
            "number": {"type": "number"},
            "flag": {"type": "boolean"},
            "status": {"type": "string", "enum": ["succeeded"]},
            "future_status": {"type": "string", "enum": ["succeeded"], "x-stripeBypassValidation": true},
            "optional": {"type": "string", "nullable": true},
            "relation": {"nullable": true, "anyOf": [{"type": "string"}, {"$ref": "#/components/schemas/target"}]},
            "list": {"type": "array", "items": {"$ref": "#/components/schemas/target"}},
            "map": {"type": "object", "additionalProperties": {"type": "integer"}},
            "unique": {"oneOf": [{"type": "string"}, {"type": "integer"}]}
          }
        },
        "target": {"type": "object", "properties": {"object": {"type": "string", "enum": ["customer"]}, "id": {"type": "string"}}}
      }
      """));

  @Test
  void unknown_fields_wrong_types_nulls_and_enums_are_rejected() {
    assertThat(
            problems(
                """
        {"missing": true, "integer": 1.5, "number": "1", "flag": 0, "status": "failed", "optional": 3}
        """))
        .containsExactlyInAnyOrder(
            new StripeResponseSchema.Problem("sample.missing", "unpublished"),
            new StripeResponseSchema.Problem("sample.integer", "type:integer"),
            new StripeResponseSchema.Problem("sample.number", "type:number"),
            new StripeResponseSchema.Problem("sample.flag", "type:boolean"),
            new StripeResponseSchema.Problem("sample.status", "enum"),
            new StripeResponseSchema.Problem("sample.optional", "type:string"));
    assertThat(problems("{\"status\":null}"))
        .containsExactly(new StripeResponseSchema.Problem("sample.status", "non-nullable"));
  }

  @Test
  void expandable_relations_accept_ids_nulls_and_the_target_object() {
    assertThat(problems("{\"relation\":\"cus_1\"}")).isEmpty();
    assertThat(problems("{\"relation\":null}")).isEmpty();
    assertThat(problems("{\"relation\":{\"object\":\"customer\",\"id\":\"cus_1\"}}")).isEmpty();
    assertThat(problems("{\"relation\":{\"object\":\"charge\",\"id\":4}}"))
        .containsExactlyInAnyOrder(
            new StripeResponseSchema.Problem("sample.relation.object", "enum"),
            new StripeResponseSchema.Problem("sample.relation.id", "type:string"));
    assertThat(problems("{\"relation\":4}"))
        .containsExactly(new StripeResponseSchema.Problem("sample.relation", "type:string"));
  }

  @Test
  void arrays_and_maps_check_every_value() {
    assertThat(problems("{\"list\":[{\"id\":false}],\"map\":{\"pln\":\"0\"}}"))
        .containsExactlyInAnyOrder(
            new StripeResponseSchema.Problem("sample.list[].id", "type:string"),
            new StripeResponseSchema.Problem("sample.map.pln", "type:integer"));
    assertThat(problems("{\"list\":{},\"map\":[]}"))
        .containsExactlyInAnyOrder(
            new StripeResponseSchema.Problem("sample.list", "type:array"),
            new StripeResponseSchema.Problem("sample.map", "type:object"));
  }

  @Test
  void nullable_fields_and_bypassed_enums_still_keep_their_json_types() {
    assertThat(
            problems(
                "{\"optional\":null,\"future_status\":\"future\",\"integer\":1000,\"number\":1.5,\"flag\":false,\"unique\":3}"))
        .isEmpty();
    assertThat(problems("{\"future_status\":true}"))
        .containsExactly(new StripeResponseSchema.Problem("sample.future_status", "type:string"));
    assertThat(problems("{\"future_status\":null}"))
        .containsExactly(new StripeResponseSchema.Problem("sample.future_status", "non-nullable"));
  }

  private static List<StripeResponseSchema.Problem> problems(String answer) {
    return SCHEMA.problems("sample", StripeJsonReader.read(answer));
  }
}
