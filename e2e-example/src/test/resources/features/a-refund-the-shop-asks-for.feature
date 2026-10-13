Feature: A refund the shop asks for

  Money a shop took and has not returned is one question, and whether the provider would return
  any more of it is another. The two are the same number often enough that an integration reads
  one and acts on the other, and what pulls them apart is the provider's own counting rules.

  The two providers below take the same payment, return part of it, and then disagree. Grow's
  account publishes how many refunds one transaction admits and the default is a single one, so
  once that one refund is made Grow answers that no refund would be returned while still
  reporting every unit it took and did not return. Stripe publishes no such count, so it reports
  the same remainder and the opposite verdict. The remainder is arithmetic and the verdict is a
  separate question: a shop deciding from the remainder alone sends Grow a call it will not take.

  A day passes between the capture and the refund because Grow refuses a partial refund of a
  transaction charged on the same day, in a refusal of its own. The second scenario asks for that
  refusal first, so the reason the clock moves at all is read here rather than assumed.

  Scenario Outline: A partial refund, and the remainder a further call may or may not return
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through <provider>
    When a payer authorises a hold of 120.50
    And the shop captures 120.50
    Then the capture is accepted
    And the simulator says a refund would still be returned
    And the simulator holds the payment as SUCCEEDED, with 120.50 of it taken and not returned
    When a day passes on the simulator's clock
    And the shop refunds 40.00
    Then the refund is accepted
    And the simulator says <verdict>
    And the simulator holds the payment as PARTIALLY_REFUNDED, with 80.50 of it taken and not returned
    When a shop that reads that answer decides whether to refund the remaining 80.50
    Then <what the shop did>, and <left> of it is taken and not returned

    Examples:
      | provider | verdict                          | what the shop did               | left  |
      | Grow     | no refund would be returned      | no further refund was asked for | 80.50 |
      | Stripe   | a refund would still be returned | the further refund was accepted | 0     |

  Scenario: Grow's own words for the two refunds it will not make
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through Grow
    When a payer authorises a hold of 120.50
    And the shop captures 120.50
    And the shop refunds 40.00
    Then the refund is refused because a partial refund cannot be made on the day of the charge
    When a day passes on the simulator's clock
    And the shop refunds 40.00 again
    Then the refund is accepted
    And the simulator says no refund would be returned
    When a shop that ignores that answer refunds 40.00 anyway
    Then the refund is refused because this transaction has already been refunded
    And the simulator holds the payment as PARTIALLY_REFUNDED, with 80.50 of it taken and not returned
