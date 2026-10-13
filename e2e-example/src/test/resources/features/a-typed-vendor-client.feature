Feature: A typed vendor client, not a hand-rolled one

  Every other feature here drives the simulator with a client this example wrote, which means
  every other feature here can only find the mistakes this example thought to look for. A vendor's
  own SDK is fussy in ways a hand-rolled client is not: it picks a body reader from the content
  type, it parses into its own model classes and refuses a field of the wrong kind, and it reads
  the error envelope's shape rather than its prose. A simulator that answers a shape close enough
  for `curl` can still be unusable by the library the application actually ships with.

  So the Stripe scenarios below run through `com.stripe:stripe-java`, the real published client,
  pointed at the simulator with nothing else changed. What they assert is not that the simulator
  is right — it is that the vendor's own client accepts it.

  The last scenario is the counterexample, and it is Grow's, because Grow's answers are JSON under
  `Content-Type: text/html`. That is the vendor's behaviour, faithfully reproduced, and it breaks
  any client that chooses a body reader from the content type. A shop that meets it here finds out
  in a test instead of in production.

  The 5xx scenario is here rather than beside the other fault scenarios because this is where the
  fussy client is. A transport fault answers a body under `text/plain`, which the SDK cannot read
  as a Stripe object and does not try to: it raises an exception of its own carrying the status,
  which is what a shop's retry logic branches on. What it must not do is raise nothing at all —
  a `502` with no body at all leaves `stripe-java` dereferencing an absent error stream.

  Scenario: The vendor's own client drives a saved card to a captured hold
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Stripe
    When the vendor's own client saves a card to a new customer
    Then the vendor's client parsed a customer, a setup intent and a payment method
    And the saved card is attached to that customer and listed under it
    When the vendor's own client takes a hold of 42.00 off-session and captures it
    Then the vendor's client parsed a captured payment intent worth 42.00
    And every answer the vendor's client read was application/json
    And the vendor's client authenticated with bearer tokens, which this contract accepts beside basic auth

  Scenario: The vendor's own client parses the error envelope of a refusal
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Stripe
    When the vendor's own client asks for a payment intent that was never created
    Then the vendor's client raised an invalid request naming resource_missing with status 404
    And the error it parsed carries a type, a message and a documentation link

  Scenario: The vendor's own client reads the refusals of doing the same thing twice
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Stripe
    When the vendor's own client saves a card to a new customer
    And the vendor's own client confirms that setup a second time
    Then the vendor's client raised an invalid request naming setup_intent_unexpected_state
    When the vendor's own client takes a hold of 12.00 off-session and captures it
    And the vendor's own client captures it a second time
    Then the vendor's client raised an invalid request naming payment_intent_unexpected_state with status 400
    And the error it parsed embeds the payment intent, which reports itself as succeeded

  Scenario: A repeat under one idempotency key is answered as a replay, and charges once
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Stripe
    When a payer authorises a hold of 64.00
    And the shop captures 64.00 under an idempotency key of its own
    Then the capture is accepted
    When the shop captures 64.00 again under the same idempotency key
    Then the capture is accepted
    And the answer announced itself as a replay
    And 64.00 has been captured, once

  Scenario: A 502 in front of the provider reaches the vendor's own client as an error it can read
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Stripe
    And the next capture will be answered 502 without reaching the provider at all
    When a payer authorises a hold of 30.00
    And the vendor's own client captures 30.00 and meets the gateway instead of the provider
    Then the vendor's client raised an exception of its own carrying status 502
    And the shop can tell it was the gateway that answered and not the provider
    And no money has moved at all

  Scenario: Grow answers JSON under a content type that says HTML, and a typed client must be told
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Grow
    When a payer authorises a hold of 19.00
    And the shop captures 19.00
    Then the capture is accepted
    But the answer arrived under content type text/html;charset=UTF-8
    And the same shop configured to answer as its published example uses application/json
