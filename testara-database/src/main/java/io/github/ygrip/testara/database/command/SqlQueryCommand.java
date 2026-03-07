package io.github.ygrip.testara.database.command;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.database.sql.SqlHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;
import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>SqlQueryCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "sql", overwrite = true)
public class SqlQueryCommand implements CommandLogic<List<Map<String, Object>>> {
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
  public List<Map<String, Object>> execute(List<Object> parameters) throws Exception {
    if (isBlank(parameters) || parameters.size() < 2) {
      return null;
    } else {
      String service = "";
      String query = "";

      for (Object parameter : parameters) {
        CommandModel sub = (CommandModel) parameter;
        String command = sub.getCommand().trim().toLowerCase();
        List<Object> params = sub.getParameters();
        if (command.equalsIgnoreCase("query")) {
          List<Object> parsed = new ArrayList<>();
          for (Object param : params) {
            Object res = null;
            if (param instanceof CommandModel) {
              res = executeCommand((CommandModel) param);
            }
            parsed.add(isBlank(res) ? param : res);
          }
          sub.setParameters(parsed);
          query = sub.printParameters();
        } else if (command.equalsIgnoreCase("db")) {
          service = String.valueOf(params.get(0));
        }
      }
      SqlHelper sql = TestFramework.context().get(SqlHelper.class);

      return sql.init(service).query(query);
    }
  }
}
