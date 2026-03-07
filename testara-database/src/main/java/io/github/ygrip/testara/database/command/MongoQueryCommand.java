package io.github.ygrip.testara.database.command;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.support.StringHelper;
import io.github.ygrip.testara.database.nosql.MongoHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;
import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>MongoQueryCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "mongo", overwrite = true,
    subCommands = {"find", "aggregate", "update", "delete", "insert", "count", "indexes"})
public class MongoQueryCommand implements CommandLogic<Object> {
  private final List<String> commands = this.info().subCommands();
  private final String[] subCommands = new String[] {"set", "project", "sort", "many", "multi", "unset"};

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object execute(List<Object> parameters) throws IOException {
    if (isBlank(parameters) || parameters.size() < 3) {
      return null;
    } else {
      Map<String, String> identifier = new HashMap<>();
      String service = "";
      String mode = "";
      String collection = "";
      int limit = 0;
      int skip = 0;
      boolean useMany;

      for (Object parameter : parameters) {
        if (parameter instanceof CommandModel sub) {
          String command = sub.getCommand().toLowerCase().trim();
          List<Object> params = sub.getParameters();
          StringHelper GlobalHelper;
          if (command.equalsIgnoreCase("limit")) {
            limit = Integer.parseInt(String.valueOf(params.get(0)));
          } else if (command.equalsIgnoreCase("skip")) {
            skip = Integer.parseInt(String.valueOf(params.get(0)));
          } else if (commands.contains(command)) {
            mode = command;
            List<Object> parsed = new ArrayList<>();
            for (Object param : params) {
              Object res = null;
              if (param instanceof CommandModel) {
                res = executeCommand((CommandModel) param);
              }
              parsed.add(isBlank(res) ? param : res);
            }
            sub.setParameters(parsed);
            String query = sub.getParameters().size() == 1 && sub.getParameters().get(0) instanceof Map ?
                StringHelper.prettyPrint(sub.getParameters().get(0)) :
                sub.printParameters();
            identifier.put("query", query);
          } else if (Arrays.asList(subCommands).contains(command)) {
            List<Object> parsed = new ArrayList<>();
            for (Object param : params) {
              Object res = null;
              if (param instanceof CommandModel) {
                res = executeCommand((CommandModel) param);
              }
              parsed.add(isBlank(res) ? param : res);
            }
            sub.setParameters(parsed);
            String query = sub.getParameters().size() == 1 && sub.getParameters().get(0) instanceof Map ?
                StringHelper.prettyPrint(sub.getParameters().get(0)) :
                sub.printParameters();
            identifier.put(command, query);
          } else if (command.equalsIgnoreCase("db")) {
            service = String.valueOf(params.get(0));
          } else if (command.equalsIgnoreCase("collection")) {
            collection = String.valueOf(params.get(0));
          }
        }
      }

      useMany = Boolean.parseBoolean(identifier.getOrDefault("many", "false"));
      if (!isBlank(mode) && !isBlank(service) && !isBlank(collection)) {
        try {
          MongoHelper mongo = TestFramework.context().get(MongoHelper.class);
          mongo.init(service).selectCollection(collection);
          if (mode.equalsIgnoreCase("find")) {
            return limit > 0 ?
                mongo.rawQuery(identifier.getOrDefault("query", "{}"),
                    identifier.getOrDefault("sort", "{}"),
                    identifier.getOrDefault("project", "{}"),
                    limit,
                    skip) :
                mongo.rawQuery(identifier.getOrDefault("query", "{}"),
                    identifier.getOrDefault("sort", "{}"),
                    identifier.getOrDefault("project", "{}"),
                    0,
                    skip);
          } else if (mode.equalsIgnoreCase("aggregate")) {
            return mongo.aggregate(identifier.getOrDefault("query", "[{$match:{}}]"));
          } else if (mode.equalsIgnoreCase("update")) {
            return mongo.update(identifier.getOrDefault("query", "{}"), identifier.getOrDefault("set", "{}"), useMany);
          } else if (mode.equalsIgnoreCase("delete")) {
            return isBlank(identifier.get("query")) || identifier.get("query").trim().equals("{}") ?
                null :
                mongo.delete(identifier.get("query"), identifier.getOrDefault("sort", "{}"), useMany);
          } else if (mode.equalsIgnoreCase("insert")) {
            return mongo.insert(identifier.getOrDefault("query", null));
          } else if (mode.equalsIgnoreCase("count")) {
            return mongo.count(identifier.getOrDefault("query", null));
          } else if (mode.equalsIgnoreCase("indexes")) {
            return mongo.getIndexes();
          }
        } catch (Exception ignored) {
        }
      }

      return null;
    }
  }
}
