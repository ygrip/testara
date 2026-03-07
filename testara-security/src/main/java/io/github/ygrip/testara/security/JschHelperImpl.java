package io.github.ygrip.testara.security;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.concurrent.TimeUnit;


import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.security.config.SSHProperties;
import io.github.ygrip.testara.security.model.SSHAuthenticationType;
import io.github.ygrip.testara.security.model.SSHChannel;
import io.github.ygrip.testara.security.model.SSHChannelType;
import io.github.ygrip.testara.security.model.SSHModel;

import lombok.extern.log4j.Log4j2;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

/**
 * <p>JschHelperImpl class.</p>
 *
 * @author yunaz.ramadhan on 6/11/2021
 * @version $Id: $Id
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class JschHelperImpl implements JschHelper {
  private final SSHProperties properties;
  private final ObjectConverter converter;
  private String currentService;
  private SSHClient sshClient;
  private Session session;
  private SSHChannel channel;

  /**
   * <p>Constructor for JschHelperImpl.</p>
   *
   * @param properties a {@link io.github.ygrip.testara.security.config.SSHProperties} object.
   */
  public JschHelperImpl(SSHProperties properties) {
    this.properties = properties;
    this.converter = TestFramework.context()
      .converter();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public JschHelper init(String service) throws Exception {
    disconnect();
    service = service.trim().toLowerCase();
    if (CommonHelper.isBlank(properties.getConfig())) {
      throw new Exception("No SSH properties is specified");
    }
    log.info("#Establishing SSH connection to {}", service);
    SSHModel props = properties.getConfig().getOrDefault(service, null);
    this.currentService = service;
    if (!CommonHelper.isBlank(props)) {
      if (!props.getHost().isEmpty()) {
        String username = converter.convert(props.getUsername());
        username = CommonHelper.isBlank(username) ? props.getUsername() : username;
        Properties configuration = new Properties();
        this.sshClient = new SSHClient();

        String knownHostsFileName =
            System.getProperty("user.home") + File.separator + ".ssh" + File.separator + "known_hosts";
        File knownHostsFile = new File(knownHostsFileName);

        if (knownHostsFile.exists() && props.isStrictHostKeyCheckingEnabled()) {
          log.info("Allow only known host connection");
          this.sshClient.loadKnownHosts(knownHostsFile);
        } else {
          log.info("Disable strict host key checking, all host access is not prohibited");
          this.sshClient.addHostKeyVerifier(new PromiscuousVerifier());
        }

        this.sshClient.connect(props.getHost(), props.getPort());

        if (props.getMethod().equals(SSHAuthenticationType.PASSWORD)) {
          // PASSWORD authentication
          String password = converter.convert(props.getPassword());
          password = CommonHelper.isBlank(password) ? props.getPassword() : password;

          this.sshClient.authPassword(username, password);

        } else {
          // PUBLIC KEY authentication
          String privateKey = converter.convert(props.getPrivateKey());
          privateKey = CommonHelper.isBlank(privateKey) ? props.getPrivateKey() : privateKey;

          // Attempt to ensure known host exists (similar to ssh-keyscan)
          ensureHostKeyExists(props.getHost(), knownHostsFile);

          if (CommonHelper.isBlank(privateKey)) {
            throw new Exception("ERROR: Private key is missing for SSH authentication");
          }

          // Create KeyProvider
          KeyProvider keyProvider = this.sshClient.loadKeys(privateKey, null, null);
          this.sshClient.authPublickey(username, keyProvider);
        }

        log.info("Connecting to {} for user {} using {}. Try secure connection...",
            props.getHost(),
            username,
            props.getMethod());
        this.session = this.sshClient.startSession();
      } else {
        throw new Exception("No remote host is specified");
      }
    } else {
      throw new Exception("No SSH properties is specified for " + service);
    }
    return this;
  }

  private void ensureHostKeyExists(String host, File knownHostsFile) throws IOException, InterruptedException {
    if (!knownHostsFile.exists() || Files.readAllLines(knownHostsFile.toPath())
        .stream()
        .noneMatch(line -> line.contains(host))) {
      log.info("Adding new host key for {}", host);
      ProcessBuilder pb = new ProcessBuilder("ssh-keyscan", "-t", "rsa", host);
      pb.redirectErrorStream(true);
      Process process = pb.start();
      String hostKey = IOUtils.readFully(process.getInputStream()).toString();
      process.waitFor(5, TimeUnit.SECONDS);

      Files.write(knownHostsFile.toPath(),
          hostKey.getBytes(),
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void disconnect() {
    try {
      if (isChannelOpen()) {
        log.info("Close channel connection from {}", currentService);
        this.channel.close();
      }
    } catch (Exception ignored) {

    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isSessionOpen() {
    return this.session != null && !CommonHelper.isBlank(this.session) && this.session.isOpen();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isChannelOpen() {
    return this.channel != null && !CommonHelper.isBlank(this.channel) && this.channel.isConnected();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Session getActiveSession() {
    return this.session;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SSHChannel getChannel() {
    return this.channel;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SSHChannel getChannel(SSHChannelType channelType) throws Exception {
    log.info("Get {} channel type connection", channelType.name());
    this.channel = SSHChannel.open(this.sshClient, channelType);
    return this.channel;
  }
}
