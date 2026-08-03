Feature: Deferred rerun reporting

  Scenario: deferred rerun with six steps and a real non-flaky failure
    Given deferred step one passes
    When deferred step two sleeps and passes
    Then deferred step three passes
    And deferred step four always fails
    And deferred step five is never executed
    And deferred step six is never executed
