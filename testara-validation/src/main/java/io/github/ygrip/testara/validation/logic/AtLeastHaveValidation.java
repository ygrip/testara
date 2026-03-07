package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;

import java.util.ArrayList;
import java.util.List;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>ContainsValidation class.</p>
 *
 * @author yunaz.ramadhan on 6/2/2024
 * @version $Id: $Id
 */
@ValidationTag(command = "AT_LEAST_HAVE", alias = {"at least have"}, overwrite = true)
public class AtLeastHaveValidation extends ValidatorLogic<Object, Object> {

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
    boolean isValid = false;
    Object actual = getActual();
    Object expected = getExpected();
    if (isBlank(actual) || isBlank(expected) || !CommonHelper.isCollection(actual)) {
      setReason("Data to compare is not valid");
    } else {
      List<?> collection = constructDataToCollections(actual);
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
              transformed.add(MapperHelper.toObject(item, getExpected().getClass()));
            }
          }
        }
      } else {
        transformed.addAll(collection);
      }
      List<?> otherCollection = constructDataToCollections(getExpected());
      for (Object checker : otherCollection) {
        isValid = transformed.contains(checker);
        if (isValid) {
          break;
        }
      }
    }
    return isValid;
  }
}
