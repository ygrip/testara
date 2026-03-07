package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>MatchPatternValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/11/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "MATCH_PATTERN", alias = {"match pattern", "match regex"}, overwrite = true)
public class MatchPatternValidation extends ValidatorLogic<Object, String> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return String.format("Data not match pattern %s, %s", getExpected(), getAdditionalMessages());
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    boolean isValid = true;
    List<?> collection = constructDataToCollections(getActual());
    if (getExpected() == null) {
      if (collection == null || collection.isEmpty()) {
        return true;
      } else {
        for (Object item : collection) {
          isValid = item == null;
          if (!isValid) {
            addMessage(String.format("data %s is not match with specified pattern",
                item.toString()));
            break;
          }
        }
        return isValid;
      }
    } else {
      Pattern pattern = Pattern.compile(getExpected(), Pattern.DOTALL);

      for (Object item : collection) {
        String itemToCheck = item == null ? "" : item.toString();
        Matcher matcher = pattern.matcher(itemToCheck);
        isValid = matcher.find();
        if (!isValid) {
          addMessage(String.format("data %s is not match with specified pattern", itemToCheck));
          break;
        }
      }
      return isValid;
    }
  }
}
