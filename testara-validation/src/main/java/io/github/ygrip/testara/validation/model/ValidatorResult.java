package io.github.ygrip.testara.validation.model;

import lombok.Builder;
import lombok.Getter;

/**
 * <p>ValidatorResult class.</p>
 *
 * @author yunaz.ramadhan on 8/11/2021
 * @version $Id: $Id
 */
@Getter
@Builder
public final class ValidatorResult {
  private final String validation;
  private final boolean success;
  private final Throwable error;
}
