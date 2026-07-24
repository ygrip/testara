package io.github.ygrip.testara.agent.cli.command;

import java.nio.file.Path;

import io.github.ygrip.testara.agent.knowledge.JsonlKnowledgeStore;
import io.github.ygrip.testara.agent.knowledge.KnowledgeStatus;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "knowledge",
  description = "Manage Testara Agent project knowledge cache",
  subcommands = {KnowledgeCommand.StatusCommand.class, KnowledgeCommand.RefreshCommand.class,
    KnowledgeCommand.ClearCommand.class},
  mixinStandardHelpOptions = true
)
public class KnowledgeCommand implements Runnable {

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  @Command(name = "status", description = "Show knowledge cache state")
  static class StatusCommand implements Runnable {
    @Option(names = "--project", defaultValue = ".", description = "Project root")
    private Path projectRoot;

    @Override
    public void run() {
      Path root = projectRoot.toAbsolutePath()
        .normalize();
      var store = new JsonlKnowledgeStore();
      var status = store.status(root);

      System.out.println("Testara Knowledge Status\n");
      System.out.println("Cache: .testara-agent/knowledge");
      System.out.println("Storage: JSONL");
      System.out.println("Status: " + status.name()
        .toLowerCase());

      if (status == KnowledgeStatus.FRESH) {
        var snapshot = store.loadOrIndex(root);
        var stats = snapshot.stats();
        if (stats != null) {
          System.out.println("Tracked files: " + stats.trackedFiles());
          System.out.println("Features: " + stats.featureCount());
          System.out.println("Scenarios: " + stats.scenarioCount());
          System.out.println("Step definitions: " + stats.stepDefCount());
          System.out.println("Commands: " + stats.commandCount());
          System.out.println("Validations: " + stats.validationCount());
          System.out.println("Tags: " + stats.tagCount());
        }
      }
    }
  }


  @Command(name = "refresh", description = "Force re-index project knowledge")
  static class RefreshCommand implements Runnable {
    @Option(names = "--project", defaultValue = ".", description = "Project root")
    private Path projectRoot;

    @Override
    public void run() {
      Path root = projectRoot.toAbsolutePath()
        .normalize();
      var store = new JsonlKnowledgeStore();
      store.refresh(root);
      var snapshot = store.loadOrIndex(root);
      System.out.println("Knowledge refreshed: " + snapshot.stats()
        .trackedFiles() + " files, " + snapshot.stats()
        .scenarioCount() + " scenarios");
    }
  }


  @Command(name = "clear", description = "Clear knowledge cache")
  static class ClearCommand implements Runnable {
    @Option(names = "--project", defaultValue = ".", description = "Project root")
    private Path projectRoot;

    @Override
    public void run() {
      Path root = projectRoot.toAbsolutePath()
        .normalize();
      var store = new JsonlKnowledgeStore();
      store.clear(root);
      System.out.println("Knowledge cache cleared.");
    }
  }
}
