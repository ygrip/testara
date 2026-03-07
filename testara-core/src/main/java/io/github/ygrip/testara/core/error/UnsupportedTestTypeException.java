package io.github.ygrip.testara.core.error;

public class UnsupportedTestTypeException extends RuntimeException {

  public UnsupportedTestTypeException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }

  public UnsupportedTestTypeException(String errorMessage){
    super(errorMessage);
  }
}
