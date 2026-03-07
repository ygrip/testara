package io.github.ygrip.testara.security.model;

import java.io.IOException;

import lombok.Getter;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;

@Getter
public class SSHChannel implements AutoCloseable {
  private final SSHClient client;
  private final Session session;
  private final SFTPClient sftpClient;
  private final SSHChannelType type;
  private Session.Command command;
  private Session.Shell shell;
  private Session.Subsystem subsystem;

  private SSHChannel(SSHClient client, Session session, SFTPClient sftpClient, SSHChannelType type) {
    this.client = client;
    this.session = session;
    this.sftpClient = sftpClient;
    this.type = type;
  }

  public static SSHChannel open(SSHClient client, SSHChannelType type) throws IOException {
    switch (type) {
      case SSHChannelType.EXEC -> {
        return new SSHChannel(client, client.startSession(), null, SSHChannelType.EXEC);
      }
      case SSHChannelType.SHELL -> {
        return new SSHChannel(client, client.startSession(), null, SSHChannelType.SHELL);
      }
      case SSHChannelType.SFTP -> {
        return new SSHChannel(client, null, client.newSFTPClient(), SSHChannelType.SFTP);
      }
      case SSHChannelType.SUBSYSTEM -> {
        return new SSHChannel(client, client.startSession(), null, SSHChannelType.SUBSYSTEM);
      }
      case SSHChannelType.SESSION -> {
        return new SSHChannel(client, client.startSession(), null, SSHChannelType.SESSION);
      }
      default -> throw new IllegalArgumentException("Unsupported channel type: " + type);
    }
  }

  // EXEC
  public String exec(String commandLine) throws IOException {
    ensureType(SSHChannelType.EXEC);
    this.command = session.exec(commandLine);
    String output = IOUtils.readFully(command.getInputStream()).toString();
    this.command.join();
    return output;
  }

  // SHELL
  public void startShell() throws IOException {
    ensureType(SSHChannelType.SHELL);
    this.shell = session.startShell();
  }

  // SFTP
  public void sftpGet(String remotePath, String localPath) throws IOException {
    ensureType(SSHChannelType.SFTP);
    sftpClient.get(remotePath, localPath);
  }

  public void sftpPut(String localPath, String remotePath) throws IOException {
    ensureType(SSHChannelType.SFTP);
    sftpClient.put(localPath, remotePath);
  }

  // SUBSYSTEM
  public void startSubsystem(String name) throws IOException {
    ensureType(SSHChannelType.SUBSYSTEM);
    this.subsystem = session.startSubsystem(name);
  }

  private void ensureType(SSHChannelType expected) {
    if (this.type != expected) {
      throw new IllegalStateException("This channel is not of type " + expected);
    }
  }

  public boolean isConnected(){
    return this.client.isConnected();
  }

  @Override
  public void close() throws IOException {
    if (command != null) command.close();
    if (shell != null) shell.close();
    if (subsystem != null) subsystem.close();
    if (sftpClient != null) sftpClient.close();
    if (session != null) session.close();
    this.client.close();
  }
}
