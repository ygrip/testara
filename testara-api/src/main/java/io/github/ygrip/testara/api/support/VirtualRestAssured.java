package io.github.ygrip.testara.api.support;

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.concurrent.Callable;

public final class VirtualRestAssured {

  private VirtualRestAssured() {}

  public static RequestSpecification given() {
    return io.restassured.RestAssured.given();
  }

  public static RequestSpecification given(RequestSpecification requestSpecification) {
    return io.restassured.RestAssured.given(requestSpecification);
  }

  public static io.restassured.specification.RequestSender given(RequestSpecification requestSpecification, ResponseSpecification responseSpecification) {
    return io.restassured.RestAssured.given(requestSpecification, responseSpecification);
  }

  public static void async(Runnable request) {
    VirtualThreadRestAssured.run(request);
  }

  public static <T> T asyncCall(Callable<T> call) {
    return VirtualThreadRestAssured.call(call);
  }

  public static void close(){
    VirtualThreadRestAssured.shutdown();
  }
}

