package io.github.ygrip.testara.reporter.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import io.github.ygrip.testara.reporter.cucumber.CucumberSummaryReportGenerator;
import io.github.ygrip.testara.reporter.formatter.CucumberJsonFormatter;

public class TestaraReporter {
  private static final Logger log = Logger.getLogger(TestaraReporter.class.getName());

  private static final List<Option> availableOptions = loadOptions();
  private static List<Option> inputs = new ArrayList<>();

  private static List<Option> loadOptions() {
    List<Option> options = new ArrayList<>();
    options.add(new Option().setOption("help").setAlias("h").setRequired(false).setDescription("print user guideline"));
    options.add(new Option().setOption("project-name")
        .setAlias("pn")
        .setRequired(true)
        .setValueRequired(true)
        .setSampleValue("your-automation-project-name")
        .setDescription("your automation project name"));
    options.add(new Option().setOption("input-location")
        .setAlias("input")
        .setRequired(true)
        .setValueRequired(true)
        .setSampleValue("/target/destination/")
        .setDescription("relative path to your cucumber json report location"));
    Map<String, List<Option>> subOptionsType = new HashMap<>();
    List<Option> subOptionForAggregateSummary = new ArrayList<>();
    subOptionForAggregateSummary.add(new Option().setOption("aggregate-summary-file")
        .setAlias("asf")
        .setSampleValue("aggregate-summary.json")
        .setRequired(false)
        .setValueRequired(false)
        .setDescription("the output file path for the aggregate summary, default : aggregate-summary.json"));
    List<Option> subOptionForCleanCucumber = new ArrayList<>();
    subOptionForCleanCucumber.add(new Option().setOption("overwrite")
        .setAlias("overwrite")
        .setSampleValue("true")
        .setRequired(false)
        .setValueRequired(false)
        .setDescription("overwrite the input file, default : true"));
    List<Option> subOptionForCucumberSummary = new ArrayList<>();
    subOptionForCucumberSummary.add(new Option().setOption("single-page-location")
        .setAlias("spl")
        .setSampleValue("/target/destination/")
        .setRequired(false)
        .setValueRequired(false)
        .setDescription("single page report location, default : /target/destination/"));
    subOptionForCucumberSummary.add(new Option().setOption("single-page-template")
        .setAlias("spt")
        .setSampleValue("modern | classic | simple | single-page")
        .setRequired(false)
        .setValueRequired(false)
        .setDescription("report style, default : modern"));
    subOptionForCucumberSummary.add(new Option().setOption("single-page-report-name")
        .setAlias("spn")
        .setSampleValue("summary")
        .setRequired(false)
        .setValueRequired(false)
        .setDescription("single page report name, default : summary"));
    List<Option> subOptionForMergeCucumber = new ArrayList<>();
    subOptionForMergeCucumber.add(new Option().setOption("merged-report-name")
        .setAlias("mr")
        .setSampleValue("cucumber.json")
        .setRequired(false)
        .setValueRequired(false)
        .setDescription("merged report name, default : cucumber.json"));
    List<Option> allSubOption = new ArrayList<>();
    allSubOption.addAll(subOptionForAggregateSummary);
    allSubOption.addAll(subOptionForCleanCucumber);
    allSubOption.addAll(subOptionForCucumberSummary);
    allSubOption.addAll(subOptionForMergeCucumber);
    subOptionsType.put("aggregate-summary", subOptionForAggregateSummary);
    subOptionsType.put("clean-cucumber", subOptionForCleanCucumber);
    subOptionsType.put("cucumber-summary", subOptionForCucumberSummary);
    subOptionsType.put("merge-cucumber", subOptionForMergeCucumber);
    subOptionsType.put("full-report", allSubOption);
    options.add(new Option().setOption("type")
        .setAlias("t")
        .setRequired(false)
        .setValueRequired(true)
        .setSubOption(subOptionsType)
        .setSampleValue(String.join(" | ", subOptionsType.keySet()))
        .setDescription("to enter the plugin mode type"));
    return options;
  }

  public static void main(String[] args) throws Exception {
    inputs = parseInputs(args);

    if (has("help")) {
      if (has("type")) {
        Option option = findMatchingOption("type").orElse(null);
        if (option != null) {
          List<Option> subOptions = option.getSubOptions().getOrDefault(getInputs("type"), new ArrayList<>());
          if (subOptions.isEmpty()) {
            throw new IllegalArgumentException("Unrecognized type value, please choose one of these : " + String.join(
                " | ",
                availableOptions.stream()
                    .filter(o -> o.getOption().equalsIgnoreCase("type"))
                    .findFirst()
                    .get()
                    .getSubOptions()
                    .keySet()));
          }
          System.out.println("\tThere are some sub options for option " + getInputs("type") + " : ");
          printOptions(subOptions, 1);
        } else {
          printAvailableOptions();
        }
      } else {
        printAvailableOptions();
      }
    } else if (has("type")) {
      //main logic here
      String type = getInputs("type");
      String projectName = getInputs("project-name");
      String path = getInputs("input-location");
      if (projectName == null) {
        throw new IllegalArgumentException("Please provide a valid project name");
      }
      if (type != null) {
        process(type, projectName, path);
      } else {
        throw new IllegalArgumentException("Type value cannot be empty");
      }
    } else if (inputs.isEmpty()) {
      throw new IllegalArgumentException("No valid argument has been found. try to run with --help option");
    }
  }

  private static void process(String type, String projectName, String path) throws Exception {
    //main logic here
    if (projectName == null) {
      throw new IllegalArgumentException("Please provide a valid project name");
    }
    if (type != null) {
      Option inputType = findMatchingOption("type", inputs).orElse(null);
      type = type.trim();
      if (inputType == null) {
        throw new IllegalArgumentException("Not a valid input type value");
      }
      switch (type) {
        case "aggregate-summary":
          checkSubOption(inputType);
          String report = inputType.getSubOption("aggregate-summary-file", "aggregate-summary.json");
          log.info(String.format("Start processing custom reporter for %s", projectName));
          CucumberSummaryReportGenerator.fromLocation(path).aggregate(report);
          break;
        case "clean-cucumber":
          checkSubOption(inputType);
          log.info(String.format("Start processing custom reporter for %s", projectName));
          boolean overwrite = Boolean.parseBoolean(inputType.getSubOption("overwrite", "true"));
          CucumberJsonFormatter.fromTargetLocation(path).overwrite(overwrite).rewriteScenarioWithOutlines();
          break;
        case "cucumber-summary":
          checkSubOption(inputType);
          String targetPath = inputType.getSubOption("single-page-location", "/target/destination/");
          String template = inputType.getSubOption("single-page-template", "modern");
          String reportFileName = inputType.getSubOption("single-page-report-name", "summary");
          log.info(String.format("Start generating cucumber custom summary report for %s", projectName));
          CucumberSummaryReportGenerator.fromLocation(path)
              .withOutputFileName(reportFileName)
              .withReportTemplate(template)
              .withReportName(projectName)
              .toLocation(targetPath)
              .generateReport();
          break;
        case "merge-cucumber":
          checkSubOption(inputType);
          String mergedReportName = inputType.getSubOption("merged-report-name", "cucumber.json");
          log.info(String.format("Start processing custom reporter for %s", projectName));
          CucumberSummaryReportGenerator.fromLocation(path).mergeReportAs(mergedReportName);
          break;
        case "full-report":
          checkSubOption(inputType);
          log.info(String.format("Start processing custom reporter for %s", projectName));
          CucumberJsonFormatter.fromTargetLocation(path).overwrite(true).rewriteScenarioWithOutlines();
          String mergedReportFileName = inputType.getSubOption("merged-report-name", "cucumber.json");
          CucumberSummaryReportGenerator.fromLocation(path).mergeReportAs(mergedReportFileName, false);
          String aggregateReportName = inputType.getSubOption("aggregate-summary-file", "aggregate-summary.json");
          String targetSinglePagePath = inputType.getSubOption("single-page-location", "/target/destination/");
          String templateReport = inputType.getSubOption("single-page-template", "modern");
          String singlePageName = inputType.getSubOption("single-page-report-name", "summary");
          log.info(String.format("Start generating cucumber custom summary report for %s", projectName));
          CucumberSummaryReportGenerator.fromLocation(path)
              .withOutputFileName(singlePageName)
              .withReportTemplate(templateReport)
              .toLocation(targetSinglePagePath)
              .withReportName(projectName)
              .generateReport(false);
          CucumberSummaryReportGenerator.fromLocation(path).aggregate(aggregateReportName, false);
          break;
        default:
          throw new IllegalArgumentException(
              "Unrecognized type value, please choose one of these : list-test-project | archive-test-plan | generate-test-plan | clean-unused-test");
      }

    } else {
      throw new IllegalArgumentException("Type value cannot be empty");
    }
  }

  private static void checkSubOption(Option inputType) {
    if (!inputType.hasSubOptions()) {
      Option option = findMatchingOption("type").orElse(null);
      if (option != null) {
        System.out.println("\tThere are some sub options for option " + inputType.getValue() + " : ");
        printOptions(option.getSubOptions().getOrDefault(inputType.getValue(), new ArrayList<>()), 1);
      }
      throw new IllegalArgumentException("Please provide all mandatory sub option for your input type");
    }
  }

  private static void printAvailableOptions() {
    System.out.println("Arguments following the testlink integration");
    System.out.println("include:");
    printOptions(availableOptions, 1);
  }

  private static void printOptions(List<Option> options, int indent) {
    String indentString = new String(new char[indent]).replace("\0", "\t");
    String subIndentString = new String(new char[indent * 2]).replace("\0", "\t");
    for (Option option : options) {
      if (option.isValueRequired() && !option.getSampleValue().isEmpty()) {
        String commandLine =
            "--" + option.getOption() + " | -" + option.getAlias() + "  <" + option.getSampleValue() + ">...";
        System.out.println(indentString + commandLine);
      } else {
        String commandLine = "--" + option.getOption() + " | -" + option.getAlias() + "...";
        System.out.println(indentString + commandLine);
      }
      if (!option.getDescription().isEmpty()) {
        System.out.println(subIndentString + option.getDescription());
      }
      if (option.hasSubOptions()) {
        for (String key : option.getSubOptions().keySet()) {
          System.out.println(subIndentString + "There are some sub options for option " + key + " : ");
          List<Option> subOptions = option.getSubOptions().get(key);
          printOptions(subOptions, 3);
        }
      }
    }
  }

  private static boolean has(String option) {
    return findMatchingOption(option, inputs).isPresent();
  }

  private static List<Option> parseInputs(String[] args) {
    Map<String, String> result = new HashMap<>();
    if (args.length > 0) {
      String currentOption = "";
      String currentValue = null;
      for (int i = 0; i < args.length; i++) {
        if (args[i].charAt(0) == '-') {
          if (!currentOption.isEmpty()) {
            result.put(currentOption, currentValue);
            currentValue = null;
          }
          if (args[i].length() < 2)
            throw new IllegalArgumentException("Not a valid argument: " + args[i]);
          if (args[i].charAt(1) == '-') {
            if (args[i].length() < 3)
              throw new IllegalArgumentException("Not a valid argument: " + args[i]);
            // --opt
            currentOption = args[i].substring(2);
          } else {
            // -opt
            currentOption = args[i].substring(1);
          }
        } else {
          if (currentValue != null) {
            String builder = currentValue + " " + args[i];
            currentValue = builder;
          } else {
            currentValue = args[i];
          }
        }
        if (i == args.length - 1) {
          if (!currentOption.isEmpty()) {
            result.put(currentOption, currentValue);
            currentOption = "";
            currentValue = null;
          }
        }
      }
    } else {
      throw new IllegalArgumentException("No argument provided, try to run with --help option");
    }
    List<Option> parsed = new ArrayList<>();
    if (!result.isEmpty()) {
      for (String key : result.keySet()) {
        Optional<Option> matching = findMatchingOption(key);
        if (matching.isPresent()) {
          Option selected = matching.get();
          Option option = new Option().setOption(selected.getOption())
              .setAlias(selected.getAlias())
              .setRequired(selected.isRequired())
              .setValue(result.get(key))
              .setValueRequired(selected.isValueRequired());
          parsed.add(option);
        }
      }
      for (Option option : parsed) {
        Option selected = findMatchingOption(option.getOption()).orElse(null);
        if (selected != null) {
          if (selected.hasSubOptions()) {
            List<Option> subOptions =
                selected.getSubOptions().getOrDefault(option.getValue().trim(), new ArrayList<>());
            if (!subOptions.isEmpty()) {
              Map<String, List<Option>> mappedSubOptions = new HashMap<>();
              List<Option> parsedSubOptions = new ArrayList<>();
              for (String key : result.keySet()) {
                Option selectedSubOption = findMatchingOption(key, subOptions).orElse(null);
                if (selectedSubOption != null) {
                  Option subOption = new Option().setOption(selectedSubOption.getOption())
                      .setAlias(selectedSubOption.getAlias())
                      .setRequired(selectedSubOption.isRequired())
                      .setValue(result.get(key))
                      .setValueRequired(selectedSubOption.isValueRequired());
                  parsedSubOptions.add(subOption);
                }
              }
              mappedSubOptions.put(option.getValue().trim(), parsedSubOptions);
              option.setSubOption(mappedSubOptions);
            }
          }
        }
      }
    }
    return parsed;
  }

  private static Optional<Option> findMatchingOption(String option, List<Option> source) {
    source = source != null && !source.isEmpty() ? source : availableOptions;
    return source.stream()
        .filter(opt -> opt.getOption().equals(option.trim()) || opt.getAlias().equals(option.trim()))
        .findFirst();
  }

  private static Optional<Option> findMatchingOption(String option) {
    return findMatchingOption(option, null);
  }

  private static String getInputs(String option, String defaultValue) {
    Option match = findMatchingOption(option, inputs).orElse(null);
    if (match != null) {
      return match.getValue();
    } else {
      return defaultValue;
    }
  }

  private static String getInputs(String option) {
    return getInputs(option, null);
  }


  private static class Option {
    private final Map<String, List<Option>> subOptions;
    private String option;
    private String alias;
    private String value;
    private String sampleValue;
    private String description;
    private boolean required;
    private boolean valueRequired;
    private boolean command;

    public Option() {
      this.subOptions = new HashMap<>();
    }

    public boolean hasSubOptions() {
      if (this.subOptions.isEmpty()) {
        return false;
      } else {
        if (this.value != null) {
          return !this.subOptions.getOrDefault(this.value.trim(), new ArrayList<>()).isEmpty();
        } else {
          return true;
        }
      }
    }

    public String getSubOption(String subOption) {
      return this.getSubOption(subOption, null);
    }

    public String getSubOption(String subOption, String defaultValue) {
      if (hasSubOptions()) {
        List<Option> selection = getSubOptions().getOrDefault(getValue().trim(), new ArrayList<>());
        if (selection.isEmpty()) {
          return defaultValue;
        } else {
          Option selected = findMatchingOption(subOption, selection).orElse(null);
          if (selected != null) {
            return selected.getValue() != null ? selected.getValue() : defaultValue;
          } else {
            return defaultValue;
          }
        }
      }
      return null;
    }

    public Map<String, List<Option>> getSubOptions() {
      return this.subOptions;
    }

    public Option setSubOption(Map<String, List<Option>> options) {
      this.subOptions.clear();
      this.subOptions.putAll(options);
      return Option.this;
    }

    public String getValue() {
      return value;
    }

    public Option setValue(String value) {
      this.value = value;
      return Option.this;
    }

    public boolean isValueRequired() {
      return valueRequired;
    }

    public Option setValueRequired(boolean valueRequired) {
      this.valueRequired = valueRequired;
      return Option.this;
    }

    public boolean isRequired() {
      return required;
    }

    public Option setRequired(boolean required) {
      this.required = required;
      return Option.this;
    }

    public String getSampleValue() {
      return sampleValue;
    }

    public Option setSampleValue(String sampleValue) {
      this.sampleValue = sampleValue;
      return Option.this;
    }

    public String getAlias() {
      return alias;
    }

    public Option setAlias(String alias) {
      this.alias = alias;
      return Option.this;
    }

    public String getOption() {
      return option;
    }

    public Option setOption(String option) {
      this.option = option;
      return Option.this;
    }

    public String getDescription() {
      return description;
    }

    public Option setDescription(String description) {
      this.description = description;
      return Option.this;
    }
  }
}
