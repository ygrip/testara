@api @sample
Feature: Sample API health check

  Scenario: Check the sample API health endpoint
    Given user using service with alias sample
    When user process request to "files/sample/request/sample-api-request"
    Then user response statusCode should be 200
