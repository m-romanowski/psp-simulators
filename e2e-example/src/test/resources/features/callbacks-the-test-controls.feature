Feature: Callbacks the test controls

  A merchant's callback handler is the part of a payment integration with the least test coverage
  and the most ways to be wrong, because the events it must survive are the ones nobody can
  cause: the same event twice, an event that never arrives, an event refused and retried until
  the provider gives up. A vendor sandbox sends one callback when it feels like it.

  Here the shop runs a real HTTP endpoint, the simulator posts to it, and the test decides what
  happens: duplicate it, drop it, refuse it, retry it after the provider had already given up.
  Every attempt is readable afterwards with the headers it actually put on the wire, content type
  included, so a shop whose handler could not parse a callback reads what it was sent as from the
  attempt itself. A provider that adds no header of its own records that content type alone, and
  never an empty set.

  Scenario: The same callback arrives twice under one event id
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Grow
    When a payer authorises a hold of 33.00
    Then the shop received 1 callback
    And the callback says a hold is waiting, and names the transaction and its token
    When the simulator sends that callback again under the same event id
    Then the shop received 2 callbacks
    And both callbacks carried exactly the same bytes

  Scenario: A callback that is never sent, and a shop that reconciles instead of waiting
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Grow
    And every callback of this run will be dropped instead of sent
    When a payer pays for a hosted payment of 41.00
    Then the shop received no callback at all
    And the run recorded the delivery as dropped, with no attempt made
    And the provider's own process lookup says the payer did pay

  Scenario: A delivery the shop refuses, retried until the provider gives up, and then one more
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Grow
    And the run's retry plan is three attempts with no delay between them
    And the shop answers every callback with 500
    When a payer authorises a hold of 55.00
    Then the delivery has been attempted 1 time and is still pending
    When the simulator delivers whatever has become due
    And the simulator delivers whatever has become due
    Then the delivery has run out of attempts after 3 of them
    And every attempt names the target it was posted to and the verdict it came back with
    When the shop answers every callback with 200
    And the simulator retries the delivery the provider had given up on
    Then the delivery is marked delivered after 4 attempts
    And every attempt says it went out as application/x-www-form-urlencoded, which is what the shop received

  Scenario: Grow owes a separate acknowledgement, and a 200 from the shop does not settle it
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Grow, which acknowledges callbacks with a separate call
    When a payer authorises a hold of 61.00
    Then the shop received 1 callback
    And the delivery is still waiting for a separate acknowledgement
    When the shop acknowledges the callback with the provider's own call
    Then the delivery is marked delivered after 1 attempt

  Scenario: Stripe signs every attempt afresh, so a retry does not carry a stale signature
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Stripe
    When a payer authorises a hold of 27.00
    And the shop captures 27.00
    And the simulator sends the provider's own callback about the finished payment
    Then the shop received 1 callback
    And the callback carried a signature header
    When a day passes on the simulator's clock
    And the simulator retries that delivery
    Then the shop received 2 callbacks
    And the second attempt was signed afresh, so it does not carry the first one's signature
    And each attempt says it went out as application/json beside the signature that signed it
