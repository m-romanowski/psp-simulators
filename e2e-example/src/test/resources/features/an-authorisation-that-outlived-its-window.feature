Feature: An authorisation that outlived its window

  A hold expires seven days after it is taken. Against a vendor sandbox that test costs seven
  days, so it is never written, and the branch that handles the expiry ships unexercised. Here
  the test run owns its clock: it pins it, takes the hold, moves the clock eight days and reads
  the refusal, in under a second and with nothing sleeping.

  Two providers answer the same eight-day question differently in their own vocabularies, and
  the neutral read is what a shop decides from. Grow publishes two deadlines, so on day eight it
  still holds every unit it never captured; Stripe publishes one, so on day eight the hold is
  already cancelled and holds nothing. The one answer a shop acts on is the same from both: no
  capture would be taken, so do not try.

  Scenario Outline: A capture eight days after the hold was taken
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through <provider>
    When a payer authorises a hold of 50.00
    Then the simulator holds the payment as READY_FOR_CAPTURE
    And the simulator says a capture would still be taken
    When 8 days pass on the simulator's clock
    Then the simulator says no capture would be taken
    And the simulator holds the payment as <status>, with <remainder> of it never captured
    When the shop captures 50.00
    Then the capture is refused because the authorisation is too old
    And no money has moved at all

    Examples:
      | provider | status            | remainder |
      | Grow     | READY_FOR_CAPTURE | 50.00     |
      | Stripe   | CANCELLED         | 0         |
