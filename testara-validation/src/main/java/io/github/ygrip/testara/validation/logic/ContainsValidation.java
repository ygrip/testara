package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>ContainsValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "CONTAINS", overwrite = true)
public class ContainsValidation extends ValidatorLogic<Object, Object> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "Some expected data is not found in the actual data";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    boolean isValid;
    Object actual = getActual();
    Object expected = getExpected();
    if (isBlank(actual) || isBlank(expected) || !CommonHelper.isCollection(actual)) {
      isValid = false;
      setReason("Data to compare is not valid");
    } else {
      List<?> collection = constructDataToCollections(getActual());
      List<Object> transformed = new ArrayList<>();
      if (!CommonHelper.isCollection(expected)) {
        for (Object item : collection) {
          if (getExpected() instanceof String) {
            if (item instanceof String) {
              transformed.add(item);
            } else {
              transformed.add(MapperHelper.toString(item));
            }
          } else {
            if (item instanceof String) {
              transformed.add(CommonHelper.parseStringToObject((String) item));
            } else {
              transformed.add(MapperHelper.toObject(item, expected.getClass()));
            }
          }
        }
      } else {
        transformed.addAll(collection);
      }
      List<?> otherCollection = constructDataToCollections(expected);
      isValid = new HashSet<>(transformed).containsAll(otherCollection);
    }
    return isValid;
  }
}
