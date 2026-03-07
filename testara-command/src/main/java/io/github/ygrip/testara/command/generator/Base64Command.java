package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Base64;
import java.util.List;


/**
 * <p>Base64Command class.</p>
 *
 * @author yunaz.ramadhan on 3/29/2020
 * @version $Id: $Id
 */
@CommandTag(command = "base64", overwrite = true, cacheable = true)
public class Base64Command implements CommandLogic<String> {
  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String execute(List<Object> list) throws Exception {
    return ObjectUtils.isEmpty(list) ?
        null :
        ObjectUtils.isEmpty(list.get(0)) ?
            null :
            list.size() == 1 ?
                Base64.getEncoder().encodeToString(list.get(0).toString().getBytes()) :
                String.valueOf(list.get(1)).equalsIgnoreCase("decode") ?
                    new String(Base64.getDecoder().decode(list.get(0).toString().getBytes())) :
                    String.valueOf(list.get(1)).equalsIgnoreCase("encode") ?
                        Base64.getEncoder().encodeToString(list.get(0).toString().getBytes()) :
                        list.get(0).toString();
  }
}
