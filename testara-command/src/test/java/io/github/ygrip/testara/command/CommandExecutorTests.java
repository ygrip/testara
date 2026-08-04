package io.github.ygrip.testara.command;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.command.error.CommandNotFoundException;
import io.github.ygrip.testara.command.error.InvalidCommandFormatException;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.data.DefaultRequestData;
import io.github.ygrip.testara.core.data.DefaultResponseData;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.PopulatedTag;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("command")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class CommandExecutorTests extends BaseTests {

  @Test
  public void populateCommand() {
    Map<String, Class<? extends CommandLogic<?>>> registeredCommands = CommandExecutor.getRegisteredCommands();
    assertThat(registeredCommands, not(anEmptyMap()));
    List<PopulatedTag> registeredCommandName = CommandExecutor.getRegisteredCommandNames();
    assertThat(registeredCommandName, not(empty()));
  }

  @Test
  public void parseWrongCommandFormat() {
    Exception exception =
        assertThrows(InvalidCommandFormatException.class, () -> CommandExecutor.parseCommand("nama saya yunaz("));

    assertThat(exception, notNullValue());
  }

  @Test
  public void parseUnknownCommand() {
    CommandModel command = CommandExecutor.parseCommand("yunaz()");
    assertThat(command, is(notNullValue()));
    assertThat(command.getCommand(), equalTo("yunaz"));
    assertThat(command.getParameters().size(), equalTo(0));
  }

  @Test
  public void sizeOf() {
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);

    List<String> dummy = new ArrayList<>();
    dummy.add("yunaz");
    dummy.add("gilang");
    dummy.add("ramadhan");
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("dummy", dummy);
    Integer sizeOfDummy = CommandExecutor.executeCommand("sizeof(request($['dummy']))");
    assertThat(sizeOfDummy, equalTo(dummy.size()));

    Integer[] dummier = new Integer[] {1, 2, 3, 4};
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("dummier", dummier);
    Integer sizeOfDummier = CommandExecutor.executeCommand("sizeof(request($['dummier']))");
    assertThat(sizeOfDummier, equalTo(dummier.length));

    Map<String, Integer> dummiest = new HashMap<>();
    dummiest.put("satu", 1);
    dummiest.put("dua", 2);
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("dummiest", dummiest);
    Integer sizeOfDummiest = CommandExecutor.executeCommand("sizeof(request($['dummiest']))");
    assertThat(sizeOfDummiest, equalTo(dummiest.size()));
  }

  @Test
  public void executingUnknownCommand() {
    CommandModel command = CommandExecutor.parseCommand("yunaz()");
    assertThat(command, is(notNullValue()));
    assertThat(command.getCommand(), equalTo("yunaz"));
    assertThat(command.getParameters().size(), equalTo(0));
    Exception exception = assertThrows(CommandNotFoundException.class, () -> CommandExecutor.executeCommand(command));
    assertThat(exception, notNullValue());
  }

  @Test
  public void executingUnknownCommandFromString() {
    String input = "yunaz()";
    Object result = CommandExecutor.executeCommand(input);
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(input));
  }

  @Test
  public void executingInvalidCommandFormat() {
    String input = "yunaz gilang";
    Object result = CommandExecutor.executeCommand(input);
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(input));
  }

  @Test
  public void combineString() {
    String output = "author name is yunaz.gilang";
    String input = "(author name is, ,properties(author))";
    assertThat(output, equalTo(CommandExecutor.executeCommand(input)));
  }

  @Test
  public void parseRegex() {
    String input = "^(general_remainder\\|1st_penalty_remainder\\|2nd_penalty_remainder\\|3rd_penalty_remainder\\|-)$";
    String output = CommandExecutor.executeCommand(input);
    assertThat(input, equalTo(output));
  }

  @Test
  public void whiteSpaceInCommandName() {
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);

    String output = "author name is yunaz.gilang";
    String input = "(author name is, , properties(author))";
    assertThat(output, equalTo(CommandExecutor.executeCommand(input)));
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("sentence", CommandExecutor.executeCommand(input));
    String sentence = CommandExecutor.executeCommand(" request($['sentence'])");
    assertThat(sentence, equalTo(output));
  }

  @Test
  public void usingJsonPath() {
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);

    List<Map<String, Object>> data = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      Map<String, Object> item = new HashMap<>();
      item.put("index", i);
      item.put("nama", "yunaz");
      item.put("data", new ArrayList<>().add(i * 3));
      data.add(item);
    }
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("items", data);
    Object result = CommandExecutor.executeCommand("request($['items'][?(@.index == 2)])");
    assertThat(result, is(notNullValue()));
    result = MapperHelper.toObject(result, new TypeReference<List<Map<String, Object>>>() {
    });
    assertThat(result, instanceOf(List.class));
    assertThat(((List) result).get(0), equalTo(data.get(2)));
  }

  @Test
  public void jsonPathCommand() throws Throwable {
    TestFramework.context()
        .get(DataHolder.class)
        .getRequest(DefaultRequestData.class)
        .addDefaultData("fileName",
            "https://static-uatb.testara.io/games/backend-assets/assets/2025/09/1758525955_Ini-Nama--IMG--yang-ga-friendly.jpg");
    String actual = CommandExecutor.executeCommand("jsonpath(split(request($['fileName']),/),[-1])");
    String expected = "1758525955_Ini-Nama--IMG--yang-ga-friendly.jpg";
    assertThat(actual, equalTo(expected));
  }

  @Test
  public void getOneOf() {
    List<Object> randomObject = new ArrayList<>();
    randomObject.add("yunaz");
    randomObject.add(23);
    randomObject.add("january");
    CommandModel command = CommandModel.builder().command("oneof").parameters(randomObject).build();
    Object result = CommandExecutor.executeCommand(command);
    assertThat(result, is(notNullValue()));
    assertThat(randomObject.contains(result), equalTo(true));
  }

  @Test
  public void getOneOfRequestData() {
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);

    List<Object> randomObject = new ArrayList<>();
    randomObject.add("yunaz");
    randomObject.add(23);
    randomObject.add("january");
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("coba", randomObject);
    Object result = CommandExecutor.executeCommand("oneof(request($['coba']))");
    assertThat(result, is(notNullValue()));
    assertThat(randomObject.contains(result), equalTo(true));
  }

  @Test
  public void getOneOfResponseData() {
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);

    List<Object> randomObject = new ArrayList<>();
    randomObject.add("yunaz");
    randomObject.add(23);
    randomObject.add("january");
    dataHolder.getResponse(DefaultResponseData.class).addDefaultData("coba", randomObject);
    Object result = CommandExecutor.executeCommand("oneof(response($['coba']))");
    assertThat(result, is(notNullValue()));
    assertThat(randomObject.contains(result), equalTo(true));
  }

  @Test
  public void generateRandomNumber() {
    int start = 1;
    int end = 100;
    Integer result = CommandExecutor.executeCommand(String.format("number(%s,%s)", start, end));
    assertThat(result, instanceOf(Integer.class));
    assertThat(result, is(both(greaterThanOrEqualTo(start)).and(lessThanOrEqualTo(end))));
  }

  @Test
  public void getValueFromProperties() {
    String result = CommandExecutor.executeCommand("properties(author)");
    assertThat(result, equalTo("yunaz.gilang"));
  }

  @Test
  public void generateUuid() {
    String result = CommandExecutor.executeCommand("uuid()");
    assertThat(result, is(notNullValue()));
    assertThat(result, instanceOf(String.class));
    assertThat(result.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"),
        equalTo(true));
  }

  @Test
  public void generateRandomString() {
    int length = 100;
    String result = CommandExecutor.executeCommand(String.format("random(%s)", length));
    assertThat(result, instanceOf(String.class));
    assertThat(result, notNullValue());
    assertThat(result.length(), equalTo(length));
  }

  @Test
  public void cacheableCommand() {
    CommandModel randomCommand =
        CommandModel.builder().command("random").parameters(Collections.singletonList(5)).build();
    boolean nonCacheAble = CommandExecutor.isCacheableCommand(randomCommand);
    assertThat(nonCacheAble, equalTo(false));
    CommandModel subString =
        CommandModel.builder().command("substring").parameters(Arrays.asList("automation", 4)).build();
    boolean cacheAble = CommandExecutor.isCacheableCommand(subString);
    assertThat(cacheAble, equalTo(true));
  }

  @Test
  public void cacheableCommandShouldConsiderParameters() {
    CommandModel randomCommand = CommandModel.builder().command("number").parameters(Arrays.asList(1, 5)).build();
    CommandModel subString =
        CommandModel.builder().command("substring").parameters(Arrays.asList("automation", 4)).build();
    boolean cacheAble = CommandExecutor.isCacheableCommand(subString);
    assertThat(cacheAble, equalTo(true));
    CommandModel nonCacheAblesubString =
        CommandModel.builder().command("substring").parameters(Arrays.asList("automation", randomCommand)).build();
    boolean nonCacheAble = CommandExecutor.isCacheableCommand(nonCacheAblesubString);
    assertThat(nonCacheAble, equalTo(false));
  }

  @Test
  void parseCacheStaysBoundedAndKeepsServingHitsPastCapacity() {
    for (int i = 0; i < 1200; i++) {
      CommandExecutor.parseCommand("substring(cache-fill-" + i + ",1)");
    }
    assertThat(CommandExecutor.parseCacheSize(), lessThanOrEqualTo(1000));

    CommandModel first = CommandExecutor.parseCommand("substring(cache-fill-final,1)");
    CommandModel second = CommandExecutor.parseCommand("substring(cache-fill-final,1)");
    assertThat(second, sameInstance(first));
  }

  @Test
  void parseCacheAbleCommandFromParser() {
    CommandModel randomCommand = CommandExecutor.parseCommand("random(5)");
    boolean nonCacheAble = CommandExecutor.isCacheableCommand(randomCommand);
    assertThat(nonCacheAble, equalTo(false));
    CommandModel subString = CommandExecutor.parseCommand("substring(automation,4)");
    boolean cacheAble = CommandExecutor.isCacheableCommand(subString);
    assertThat(cacheAble, equalTo(true));
  }

  @Test
  void parseCacheAbleCommandFromParserShouldConsiderParameter() {
    CommandModel nonCacheAblesubString = CommandExecutor.parseCommand("substring(automation,number(1,5))");
    boolean nonCacheAble = CommandExecutor.isCacheableCommand(nonCacheAblesubString);
    assertThat(nonCacheAble, equalTo(false));
  }

  @Test
  public void parseFromProperties() throws Throwable {
    String clientId = CommandExecutor.executeCommand("oneof(split(properties(internal.game.clients),!(,)))");
    List<String> expected =
        Arrays.asList(TestFramework.context().configuration().get("internal.game.clients", "").split(","));
    assertThat(expected.contains(clientId), equalTo(true));
  }

  @Test
  public void nonExistingCommandShouldBeCacheable() {
    CommandModel nonCacheAble = CommandExecutor.parseCommand("nonexistingcommandabcd(automation,number(1,5))");
    boolean result = CommandExecutor.isCacheableCommand(nonCacheAble);
    assertThat(result, equalTo(true));
  }

  @Test
  public void parseCorrectCommandFormat() {
    assertDoesNotThrow(() -> CommandExecutor.executeCommand("request($['variable'])"));
    assertDoesNotThrow(() -> CommandExecutor.parseCommand("random(10,NUMERIC)"));
    CommandModel command = CommandExecutor.parseCommand("random(10,NUMERIC)");
    assertThat(command, is(notNullValue()));
    assertThat(command.getCommand(), equalTo("random"));
    assertThat(command.getParameters().size(), equalTo(2));
  }

  @Test
  public void processHugeString() {
    String huge = FileHelper.readFile(System.getProperty("user.dir") + "/src/test/resources/huge-text.txt");
    assertDoesNotThrow(() -> CommandExecutor.parseCommand(huge));
    assertThat(CommandExecutor.executeCommand(huge), equalTo(huge));
  }

  @Test
  void resolvePropertyPlaceholder() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("property.name");
    String resolved = CommandExecutor.executeCommand("properties(property.name)");
    assertThat(properties.isPresent(), equalTo(true));
    String value = properties.get();
    assertThat(value, equalTo(resolved));
  }

  @Test
  void resolvePropertyPlaceholderWithFallback() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("property.fallback");
    String resolved = CommandExecutor.executeCommand("properties(property.fallback)");
    assertThat(properties.isPresent(), equalTo(true));
    String value = properties.get();
    assertThat(value, equalTo(resolved));
  }

  @Test
  void resolvePropertyPlaceholderWithCommandPattern() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("property.command");
    String resolved = CommandExecutor.executeCommand("properties(property.command)");
    assertThat(properties.isPresent(), equalTo(true));
    String value = properties.get();
    assertThat(value, equalTo(resolved));
  }

  @Test
  void resolvePropertyPlaceholderWithCommandPatternAsFallback() throws Exception {
    Optional<String> properties = TestFramework.context().configuration().get("property.command.fallback");
    String resolved = CommandExecutor.executeCommand("properties(property.command.fallback)");
    assertThat(properties.isPresent(), equalTo(true));
    String value = properties.get();
    assertThat(value, equalTo(resolved));
  }

  @Test
  public void testArithmaticCommand(){
    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("one", 1);
    dataHolder.getRequest(DefaultRequestData.class).addDefaultData("two", 2);

    Integer three = CommandExecutor.executeCommand("add(request($['one']), request($['two']))");
    assertThat(three, equalTo(3));

    Integer negative = CommandExecutor.executeCommand("subtract(request($['one']), request($['two']))");
    assertThat(negative, equalTo(-1));

    Double divide = CommandExecutor.executeCommand("divide(request($['one']), request($['two']))");
    assertThat(divide, equalTo(0.5));

    Integer multiply = CommandExecutor.executeCommand("multiply(request($['one']), request($['two']))");
    assertThat(multiply, equalTo(2));
  }

  @Test
  public void getDataFromComplexCommand() {
    DefaultRequestData requestData = TestFramework.context().get(DataHolder.class).getRequest(DefaultRequestData.class);
    String level = "CLASSIC";
    requestData.addDefaultData("level", level);
    Map<String, Integer> levels = new HashMap<>();
    levels.put("CLASSIC", 1);
    levels.put("SIGNATURE", 2);
    levels.put("PREMIER", 3);
    levels.put("INFINITE", 4);
    requestData.addDefaultData("levels", levels);

    Integer expected = levels.get(level);
    Integer actual = CommandExecutor.executeCommand("request(($['levels'][',request($['level']),']))");
    assertThat(actual, equalTo(expected));
  }

  @Test
  public void parseCommandEndWithNewLine() {
    String input =
        "mongo(\n  db(partner-voucher),\n    collection(voucher_code),\n    find(({\n        code : ',request($['codes'][1]),'\n        }))\n )";
    TestFramework.context()
        .get(DataHolder.class)
        .getRequest(DefaultRequestData.class)
        .addDefaultData("codes", Arrays.asList("yunaz", "gilang"));
    CommandModel model = CommandExecutor.parseCommand(input);
    List<Object> filtered = model.getParameters()
        .stream()
        .filter(parameter -> parameter.getClass().isAssignableFrom(CommandModel.class))
        .collect(Collectors.toList());
    assertThat(model.getParameters().size(), equalTo(filtered.size()));
  }

  @Test
  public void setOfCommand() {
    List<String> input = new ArrayList<>();
    input.add("Yunaz");
    input.add("Gilang");
    input.add("Yunaz");
    TestFramework.context().get(DataHolder.class).getRequest(DefaultRequestData.class).addDefaultData("input", input);
    Set<String> unique = new TreeSet<>(input);
    List<String> expected = new ArrayList<>(unique);
    List<String> actual = CommandExecutor.executeCommand("setof(request($['input']))");
    assertThat(actual, equalTo(expected));
  }

  @Test
  public void ignoredCommand() {
    String result = CommandExecutor.executeCommand("!(.,-,5)");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(".,-,5"));

    String result2 = CommandExecutor.executeCommand("(!((.,-,5),yunaz)");
    assertThat(result2, is(notNullValue()));
    assertThat(result2, equalTo("(.,-,5yunaz"));
  }

  @Test
  public void invalidCommandFormat() {
    String result = CommandExecutor.executeCommand(")(");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(")("));
  }

  @Test
  public void invalidMultilineCommand() {
    String input = "yunaz \n" + "gilang" + "\n ramadhan";
    String result = CommandExecutor.executeCommand(input);
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(input));
  }

  @Test
  public void combineCommand() {
    String result = CommandExecutor.executeCommand("(.,-,5)");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(".-5"));
  }

  @Test
  public void combineWithComplexInput() {
    TestFramework.context().get(DataHolder.class).getRequest(DefaultRequestData.class).addDefaultData("test", 123);
    String result = CommandExecutor.executeCommand("(?(@.price < ,request($['test']),))");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo("?(@.price < 123)"));
  }

  @Test
  public void combineWithUnknownCommand() {
    String result = CommandExecutor.executeCommand("(yunaz, ?(),gilang)");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo("yunaz ?()gilang"));
  }

  @Test
  public void loopWithOccurrenceAndSeparator() {
    String result = CommandExecutor.executeCommand("loop(.,-,5)");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(".-.-.-.-."));
  }

  @Test
  public void loopWithOccurrenceFromCommand() {
    TestFramework.context().get(DataHolder.class).getRequest(DefaultRequestData.class).addDefaultData("occurances", 5);
    String result = CommandExecutor.executeCommand("loop(.,-,request($['occurances']))");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo(".-.-.-.-."));
  }

  @Test
  public void loopWithOccurrence() {
    String result = CommandExecutor.executeCommand("loop(.,5)");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo("....."));
  }

  @Test
  public void loopWithoutOccurrence() {
    String result = CommandExecutor.executeCommand("loop(.)");
    assertThat(result, is(notNullValue()));
    assertThat(result, equalTo("."));
  }
}
