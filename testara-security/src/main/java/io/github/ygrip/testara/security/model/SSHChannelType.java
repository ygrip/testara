package io.github.ygrip.testara.security.model;

/**
 * <p>SSHChannelType class.</p>
 *
 * @author yunaz.ramadhan on 6/10/2021
 * @version $Id: $Id
 */
public enum SSHChannelType {
  SHELL("shell"), SFTP("sftp"), EXEC("exec"), SUBSYSTEM("subsystem"), SESSION("session");

  /**
   * <p>Getter for the field <code>label</code>.</p>
   *
   * @return a {@link String} object.
   */
  public final String label;

  public String getLabel(){
    return this.label;
  }

  SSHChannelType(String label) {
    this.label = label;
  }
}
