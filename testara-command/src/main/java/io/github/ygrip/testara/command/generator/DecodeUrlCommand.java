package io.github.ygrip.testara.command.generator;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;

@CommandTag(command = "decode_url", overwrite = true)
public class DecodeUrlCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) throws Exception {
    try {
      return URLDecoder.decode(String.valueOf(parameters.get(0)), StandardCharsets.UTF_8);
    } catch (Exception ignored){
      return String.valueOf(parameters.get(0));
    }
  }
}
