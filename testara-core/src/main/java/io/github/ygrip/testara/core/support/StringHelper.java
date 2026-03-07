package io.github.ygrip.testara.core.support;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.StringUtils;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

public final class StringHelper {
  private final static String NON_THIN = "[^iIl1\\.,']";

  private StringHelper(){

  }

  /**
   * <p>prettyPrint.</p>
   *
   * @param input a {@link Object} object.
   * @return a {@link String} object.
   */
  public static String prettyPrint(Object input) {
    String result;
    try {
      result = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(input);
    } catch (JsonSyntaxException isNotCorrectJson) {
      try {
        Source xmlInput = new StreamSource(new StringReader(input.toString()));
        StringWriter stringWriter = new StringWriter();
        StreamResult xmlOutput = new StreamResult(stringWriter);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute("indent-number", 2);
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
            "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd");
        transformer.transform(xmlInput, xmlOutput);
        result = xmlOutput.getWriter().toString();
      } catch (Exception ignored) {
        result = input == null ? "" : input.toString();
      }
    } catch (Exception ignored) {
      result = input == null ? "" : input.toString();
    }
    return result;
  }

  private static int textWidth(String str) {
    return str.length() - str.replaceAll(NON_THIN, "").length() / 2;
  }

  /**
   * <p>ellipsize.</p>
   *
   * @param text a {@link String} object.
   * @param max  a int.
   * @return a {@link String} object.
   */
  public static String ellipsize(String text, int max) {
    if (StringUtils.isBlank(text))
      return text;

    if (textWidth(text) <= max)
      return text;

    // Start by chopping off at the word before max
    // This is an over-approximation due to thin-characters...
    int end = text.lastIndexOf(' ', max - 3);

    // Just one long word. Chop it off.
    if (end == -1)
      return text.substring(0, max - 3) + "...";

    // Step forward as long as textWidth allows.
    int newEnd = end;
    do {
      end = newEnd;
      newEnd = text.indexOf(' ', end + 1);

      // No more spaces.
      if (newEnd == -1)
        newEnd = text.length();

    } while (textWidth(text.substring(0, newEnd) + "...") < max);

    return text.substring(0, end) + "...";
  }

  /**
   * <p>capitalize.</p>
   *
   * @param input a {@link String} object.
   * @return a {@link String} object.
   */
  public static String capitalize(String input) {
    if (input.isEmpty()) {
      return input;
    }
    return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
  }
}
