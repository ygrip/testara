package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import com.google.common.collect.Ordering;

import java.util.Arrays;
import java.util.List;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>SortedValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "SORTED", alias = {"sorted"}, overwrite = true)
public class SortedValidation extends ValidatorLogic<Object, String> {
  private final String[] sortType = new String[] {"ascending", "descending", "asc", "desc"};

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Data is not sorted as expected";
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("unchecked")
  public boolean validate() throws Exception {
    boolean isValid;
    Object actual = getActual();
    Object expected = getExpected();
    if (isBlank(actual) || isBlank(expected) || !CommonHelper.isCollection(actual)) {
      throw new Exception(String.format("Data %s is not valid", MapperHelper.toString(actual)));
    } else if (!(Arrays.asList(sortType).contains(getExpected().toLowerCase().trim()))) {
      throw new Exception(String.format("Please specify valid sortType\nIt should be %s but get %s",
          Arrays.toString(sortType),
          getExpected()));
    } else {
      List<?> collection = constructDataToCollections(getActual());

      String mode = getExpected().trim().toLowerCase();
      isValid = mode.equalsIgnoreCase("ascending") || mode.equalsIgnoreCase("asc") ?
          Ordering.natural()
              .nullsFirst()
              .isOrdered((Iterable<? extends Comparable<?>>) collection) :
          Ordering.natural()
              .nullsFirst()
              .reverse()
              .isOrdered((Iterable<? extends Comparable<?>>) collection);
      setReason(String.format("Data :\n%s \n\nnot sorted by %s", getActual(), mode));
    }
    return isValid;
  }
}
