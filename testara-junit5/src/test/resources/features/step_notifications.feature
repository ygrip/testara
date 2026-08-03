Feature: Step notification reporting

  Scenario: all three steps pass
    Given a first passing step
    When a second passing step
    Then a third passing step

  Scenario: second of three steps fails
    Given first step sleeps and passes
    When second step fails
    Then third step is never executed
