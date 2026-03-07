package io.github.ygrip.testara.command.generator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;

@CommandTag(command = "encode_url", overwrite = true)
public class EncodeUrlCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) throws Exception {
    try {
      return URLEncoder.encode(String.valueOf(parameters.get(0)), StandardCharsets.UTF_8);
    } catch (Exception ignored){
      return String.valueOf(parameters.get(0));
    }
  }
}
