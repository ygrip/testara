package io.github.ygrip.testara.api.support;

import io.github.ygrip.testara.api.model.CookieModel;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.restassured.http.Cookie;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

public final class CookieHelper {
  private static final ObjectConverter converter = ObjectConverterLoader.instance();

  private CookieHelper() {

  }

  public static Cookie buildCookie(CookieModel model) {
    if (model != null && model.getName() != null) {
      if (!model.getName().trim().isEmpty()) {
        Cookie.Builder builder = new Cookie.Builder(model.getName(), converter.convert(model.getValue()));
        if (!isBlank(model.getDomain())) {
          builder.setDomain(model.getDomain());
        }
        if (!isBlank(model.getSameSite())) {
          builder.setSameSite(model.getSameSite());
        }
        if (!isBlank(model.getExpiryDate())) {
          builder.setExpiryDate(model.getExpiryDate());
        }
        if (!isBlank(model.getVersion())) {
          builder.setVersion(model.getVersion());
        }
        if (!isBlank(model.getMaxAge())) {
          builder.setMaxAge(model.getMaxAge());
        }
        if (!isBlank(model.getPath())) {
          builder.setPath(model.getPath());
        }
        builder.setHttpOnly(model.isHttpOnly());
        builder.setComment(model.getComment());
        builder.setSecured(model.isSecured());

        return builder.build();
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

}
