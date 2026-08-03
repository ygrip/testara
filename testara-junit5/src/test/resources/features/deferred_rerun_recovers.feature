Feature: Deferred rerun that recovers on retry

  Scenario: deferred rerun recovers after one real failure
    Given recover step one passes
    When recover step two sleeps briefly
    Then recover step three fails once then passes
