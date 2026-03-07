package io.github.ygrip.testara.security;

import io.github.ygrip.testara.security.model.SSHChannel;
import io.github.ygrip.testara.security.model.SSHChannelType;

import net.schmizz.sshj.connection.channel.direct.Session;

/**
 * <p>JschHelper interface.</p>
 *
 * @author yunaz.ramadhan on 6/11/2021
 * @version $Id: $Id
 */
public interface JschHelper {
  /**
   * <p>init.</p>
   *
   * @param service a {@link String} object.
   * @return a {@link io.github.ygrip.testara.security.JschHelper} object.
   * @throws Exception if any.
   */
  JschHelper init(String service) throws Exception;

  /**
   * <p>disconnect.</p>
   */
  void disconnect();

  /**
   * <p>isSessionOpen.</p>
   *
   * @return a boolean.
   */
  boolean isSessionOpen();

  /**
   * <p>isChannelOpen.</p>
   *
   * @return a boolean.
   */
  boolean isChannelOpen();

  /**
   * <p>getActiveSession.</p>
   *
   * @return a {@link net.schmizz.sshj.connection.channel.direct.Session} object.
   */
  Session getActiveSession();

  /**
   * <p>getChannel.</p>
   *
   * @return a {@link net.schmizz.sshj.connection.channel.Channel} object.
   */
  SSHChannel getChannel();

  /**
   * <p>getChannel.</p>
   *
   * @param channelType a {@link Class} object.
   * @return a SSHChannel object.
   * @throws Exception if any.
   */
  SSHChannel getChannel(SSHChannelType channelType) throws Exception;
}
