Feature: An answer that never arrived

  The two failures a payment integration is most often wrong about are the ones a sandbox will
  not produce on demand: a call the provider accepted whose answer never reached the caller, and
  a `502` from in front of the provider that the caller must retry without paying twice.

  The first one decides whether the shop double-charges. The second decides whether it charges at
  all. Both are arranged here by naming the fault, and the shop then does what a shop does:
  reconcile, or retry.

  One thing worth knowing before writing the retry: the `502` here is answered by the gateway in
  front of the provider, and its body is the gateway's own prose under `text/plain` — never a
  provider error envelope, because no provider answered. It also reads the same whether the fault
  replaced the call or followed it, so a shop cannot learn from the answer whether the provider
  processed anything. That is the whole reason the retry below has to be safe to make blind.

  Scenario Outline: The provider captured the money and the answer was lost on the way back
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through <provider>
    And the answer to the next capture will be lost on the way back
    When a payer authorises a hold of 90.00
    And the shop captures 90.00
    Then no answer came back at all
    When the shop reconciles the payment with the provider's own lookup
    Then the lookup says the money did move, and 90.00 has been captured

    Examples:
      | provider |
      | Grow     |
      | Stripe   |

  Scenario Outline: A 502 in front of the provider, and a retry that must not pay twice
    Given a test run of our own, with the clock pinned to 2026-09-15T10:00:00Z
    And a shop that takes payments through <provider>
    And the next capture will be answered 502 without reaching the provider at all
    When a payer authorises a hold of 75.00
    And the shop captures 75.00
    Then the shop was handed a server error with no provider envelope in it
    And no money has moved at all
    When the shop captures 75.00 again
    Then the capture is accepted
    And 75.00 has been captured

    Examples:
      | provider |
      | Grow     |
      | Stripe   |
