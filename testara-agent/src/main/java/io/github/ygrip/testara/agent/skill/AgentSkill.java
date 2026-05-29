package io.github.ygrip.testara.agent.skill;

public interface AgentSkill<I, O> {
  String name();
  O execute(I input, AgentContext context);
}
