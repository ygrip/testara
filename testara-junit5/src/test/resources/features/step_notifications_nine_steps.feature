Feature: Step notification reporting for a longer scenario

  Scenario: nine steps with a data table argument and a mid-scenario failure
    Given a data table step
      | key   | value |
      | alpha | one   |
      | beta  | two   |
    When step two passes
    Then step three passes
    And step four fails
    And step five is never executed
    And step six is never executed
    And step seven is never executed
    And step eight is never executed
    And step nine is never executed
