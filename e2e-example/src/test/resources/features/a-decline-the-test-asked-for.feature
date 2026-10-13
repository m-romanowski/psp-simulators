Feature: A decline the test asked for

  A vendor sandbox declines when its own rules decide to, and the only lever a test has is a
  magic card number the vendor happened to publish. Against the simulator the test says what
  the acquirer will do, and the provider answers it in its own vocabulary, over its own
  transport.

  The two providers below answer the same refusal in shapes that share nothing. Grow answers
  HTTP 200 with a JSON body under `text/html`, and puts the reason in a numeric `err.id` with a
  Hebrew message beside it. Stripe answers HTTP 402 with `application/json` and puts the reason
  in a string `error.code`. The steps do not know that. Each shop knows its own provider's word
  for an expired card, and the test only ever asks for the fact.

  Scenario Outline: A hold whose capture the acquirer refuses, in <provider>'s own words
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through <provider>
    And the next capture will be refused because the card has expired
    When a payer authorises a hold of 120.50
    Then the simulator holds the payment as READY_FOR_CAPTURE
    When the shop captures 120.50
    Then the capture is refused because the card has expired
    And no money has moved at all
    And the trail names the rule that decided the capture

    Examples:
      | provider |
      | Grow     |
      | Stripe   |

  Scenario Outline: The same hold, captured, so the refusal above is not the only thing that works
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through <provider>
    When a payer authorises a hold of 120.50
    And the shop captures 120.50
    Then the capture is accepted
    And 120.50 has been captured

    Examples:
      | provider |
      | Grow     |
      | Stripe   |
