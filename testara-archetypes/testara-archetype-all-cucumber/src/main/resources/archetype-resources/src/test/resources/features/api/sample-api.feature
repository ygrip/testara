@api @sample
Feature: Sample API health check

  Scenario: Check the sample API health endpoint
    Given an API client uses "properties(api.sample.base-url)"
    When the client sends GET "properties(api.sample.path)"
    Then the response status should be 200
