package io.github.ygrip.testara.api.model;

import io.restassured.http.ContentType;
import io.restassured.http.Cookies;
import io.restassured.http.Headers;
import lombok.Builder;
import lombok.Getter;

/**
 * <p>CommonResponseModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Getter
@Builder(toBuilder = true)
public class CommonResponseModel {
  private String sessionId;
  private ContentType contentType;
  private Headers headers;
  private Cookies cookies;
  private Object body;
  private long responseTimeMillis;

  /**
   * <p>clearData.</p>
   */
  public void clearData(){
    this.sessionId = null;
    this.contentType = null;
    this.cookies = null;
    this.body = null;
  }
}
