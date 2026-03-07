package io.github.ygrip.testara.core.error;

public class InvalidConfigurationPropertiesException extends RuntimeException {

  public InvalidConfigurationPropertiesException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }

  public InvalidConfigurationPropertiesException(String errorMessage){
    super(errorMessage);
  }
}
