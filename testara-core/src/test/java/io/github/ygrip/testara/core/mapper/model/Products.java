package io.github.ygrip.testara.core.mapper.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

/**
 * @author yunaz.ramadhan on 6/29/2020
 */
@Data
@JsonPropertyOrder({"products"})
@JacksonXmlRootElement(localName = "products")
public class Products {
  @JacksonXmlElementWrapper(useWrapping = false, localName = "product")
  private List<Product> product;
}
