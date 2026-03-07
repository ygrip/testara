package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/**
 * <p>UUIDGeneratorCommand class.</p>
 *
 * @author yunaz.ramadhan on 2/15/2020
 * @version $Id: $Id
 */
@CommandTag(command = "uuid", overwrite = true)
public class UUIDGeneratorCommand implements CommandLogic<String> {
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
  public String execute(List<Object> parameters) throws Exception {
    MessageDigest salt = MessageDigest.getInstance("SHA-256");
    salt.update(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
    UUID uuid = UUID.nameUUIDFromBytes(salt.digest());
    return uuid.toString();
  }
}
