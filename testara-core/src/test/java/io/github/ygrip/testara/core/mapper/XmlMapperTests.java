package io.github.ygrip.testara.core.mapper;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.model.Products;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Tag("mapper")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class XmlMapperTests extends BaseTests {

  @Test
  public void xmlToObject() {
    String filePath = System.getProperty("user.dir") + "/src/test/resources/product.xml";
    Products products = MapperHelper.xmlToObject(FileHelper.openFile(filePath), Products.class);
    assertThat(ObjectUtils.isEmpty(products), equalTo(false));
  }

  @Test
  public void xmlToJson() {
    String filePath = System.getProperty("user.dir") + "/src/test/resources/product.xml";
    String products = MapperHelper.xmlToJsonArrayNodeString(FileHelper.openFile(filePath));
    assertThat(ObjectUtils.isEmpty(products), equalTo(false));
  }
}
