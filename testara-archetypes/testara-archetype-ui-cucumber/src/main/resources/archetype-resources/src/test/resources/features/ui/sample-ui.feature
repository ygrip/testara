@ui @sample
Feature: Sample UI navigation

  Scenario: Open a generic page
    Given user using chrome in desktop
    When user open "sample" page
    Then user is in "sample" page
