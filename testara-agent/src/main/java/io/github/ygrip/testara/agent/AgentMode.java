package io.github.ygrip.testara.agent;

public enum AgentMode {
  /** Analyze only — never modify files or execute tests. */
  READ_ONLY,
  /** Produce structured recommendations or run-plan, no file writes. */
  PLAN,
  /** Produce file patches (unified diff or structured patch objects). */
  PATCH,
  /** Write files or execute commands when explicitly requested. */
  APPLY
}
