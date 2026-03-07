package io.github.ygrip.testara.engine.model;

/**
 * Defines different strategies for handling failed scenario reruns
 */
public enum RerunStrategy {
  /**
   * No rerun - scenarios run once only
   */
  NONE,

  /**
   * Immediate retry - retry failed scenarios immediately within the same execution
   * This is the existing behavior where scenarios are retried right after they fail
   */
  IMMEDIATE,

  /**
   * Deferred rerun - collect failed scenarios during execution and rerun them all at the end
   * Wait for all scenarios to complete, then execute failed scenarios in a separate phase
   */
  DEFERRED,

  /**
   * Combined rerun - use both IMMEDIATE and DEFERRED strategies
   * Failed scenarios are retried immediately, and any remaining failures
   * are collected for a final deferred rerun phase
   */
  COMBINE
} 