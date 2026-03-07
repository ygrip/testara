package io.github.ygrip.testara.reporter.support;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import io.github.ygrip.testara.reporter.cucumber.Element;
import io.github.ygrip.testara.reporter.cucumber.Feature;
import io.github.ygrip.testara.reporter.cucumber.Hook;
import io.github.ygrip.testara.reporter.model.ReportColor;

public class CommonUtil {
  public static final NumberFormat PERCENT_FORMATTER;
  public static final CommonUtil INSTANCE;
  private static final NumberFormat DECIMAL_FORMATTER;
  private static final PeriodFormatter TIME_FORMATTER;

  static {
    PERCENT_FORMATTER = NumberFormat.getPercentInstance(Locale.ENGLISH);
    PERCENT_FORMATTER.setMinimumFractionDigits(2);
    PERCENT_FORMATTER.setMaximumFractionDigits(2);
    DECIMAL_FORMATTER = DecimalFormat.getInstance(Locale.ENGLISH);
    DECIMAL_FORMATTER.setMinimumFractionDigits(2);
    DECIMAL_FORMATTER.setMaximumFractionDigits(2);
    INSTANCE = new CommonUtil();
    TIME_FORMATTER = (new PeriodFormatterBuilder()).appendDays()
        .appendSeparator(" ")
        .appendHours()
        .appendSuffix("H")
        .appendSeparator(" ")
        .appendMinutes()
        .appendSuffix("m")
        .appendSeparator(" ")
        .printZeroRarelyFirst()
        .appendSeconds()
        .appendSuffix("s")
        .appendSeparator(" ")
        .printZeroNever()
        .appendMillis()
        .appendSuffix("ms")
        .toFormatter();
  }

  private CommonUtil() {
  }

  public static String formatDuration(long duration) {
    final long CONSTANT = 1000000L;
    if(duration < CONSTANT){
      return "1ms";
    }
    return TIME_FORMATTER.print(new Period(0L, duration / CONSTANT));
  }

  public static String formatAsPercentage(int value, int total) {
    float average = total == 0 ? 0.0F : (float) value / (float) total;
    return PERCENT_FORMATTER.format(average);
  }

  public static String formatAsDecimal(int value, int total) {
    float average = total == 0 ? 0.0F : 100.0F * (float) value / (float) total;
    return DECIMAL_FORMATTER.format(average);
  }

  public static String toValidFileName(String fileName) {
    return Long.toString((long) fileName.hashCode() + 2147483647L);
  }

  public static List<Hook> eliminateEmptyHooks(List<Hook> hooks) {
    return hooks.stream().filter(Hook::hasContent).collect(Collectors.toList());
  }

  public static Map<String, Integer> roundTallyTo(Map<String, Double> input, int total) {
    Map<String, Integer> result = new HashMap<>();
    int cumulatedRoundedValue = 0;
    int previousBaseLine = cumulatedRoundedValue;

    for (String key : input.keySet()) {
      if (cumulatedRoundedValue == total) {
        result.put(key, 0);
      } else {
        int current = (int) Math.round(input.get(key));
        cumulatedRoundedValue += current;
        int need = cumulatedRoundedValue - previousBaseLine;
        result.put(key, need);
      }
      previousBaseLine = cumulatedRoundedValue;
    }

    return result;
  }

  public static List<String> getTreeNode(String uri) {
    List<String> result = new ArrayList<>();
    String[] ignoredNodes = new String[] {"src", "test", "resources", "features", "feature"};
    try {
      //remove unwanted any url format encoding
      uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
    } catch (Exception ignored) {

    }

    List<String> nodes = Arrays.asList(uri.split("(\\||\\/)")); //split uri by file separator
    for (int i = nodes.size() - 1; i >= 0; i--) {
      if (!Arrays.asList(ignoredNodes).contains(nodes.get(i).trim().toLowerCase())) {
        if (i == nodes.size() - 1) {
          //this node is expected to be file
          result.add(nodes.get(i).split("\\.")[0]);
        } else {
          //this node is expected to be directory
          result.add(nodes.get(i));
        }
      } else {
        //nodes lookup has reached ignoredNodes, hence break the loop
        break;
      }
    }

    Collections.reverse(result); //reverse the result to get botton to top nodes
    return result;
  }

  public static Map<String, Object> generateReportResultsSummary(List<Feature> features) {
    HashMap<String, Object> result = new HashMap<>();
    if (features != null && !features.isEmpty()) {
      Integer totalScenario = features.stream().map(Feature::getScenarios).reduce(0, Integer::sum);
      Optional<Feature> firstFeature = features.stream().min(Comparator.comparing(Feature::getStartTime));
      Optional<Feature> lastFeature = features.stream().max(Comparator.comparing(Feature::getStopTime));

      Long allScenariosDuration = features.stream().map(Feature::getDuration).reduce(0L, Long::sum);
      Long totalExecutionTime = allScenariosDuration;
      if (firstFeature.isPresent() && lastFeature.isPresent()) {
        totalExecutionTime = firstFeature.get().getStartTime().until(lastFeature.get().getStopTime(), ChronoUnit.NANOS);
      }
      long avgExecutionTime = allScenariosDuration / totalScenario;
      Element slowest = features.stream()
          .map(Feature::getElements)
          .flatMap(Collection::stream)
          .max(Comparator.comparing(Element::getDuration))
          .orElse(new Element());
      Element fastest = features.stream()
          .map(Feature::getElements)
          .flatMap(Collection::stream)
          .min(Comparator.comparing(Element::getDuration))
          .orElse(new Element());
      result.put("totalScenarios", totalScenario);
      result.put("totalSteps", features.stream().map(Feature::getSteps).reduce(0, Integer::sum));
      result.put("fastestTest", CommonUtil.formatDuration(fastest.getDuration()));
      result.put("slowestTest", CommonUtil.formatDuration(slowest.getDuration()));
      result.put("allScenariosDuration", CommonUtil.formatDuration(allScenariosDuration));
      result.put("totalExecutionTime", CommonUtil.formatDuration(totalExecutionTime));
      result.put("averageExecutionTime", CommonUtil.formatDuration(avgExecutionTime));
    } else {
      result.put("totalScenarios", 0);
      result.put("totalSteps", 0);
      result.put("fastestTest", "-");
      result.put("slowestTest", "-");
      result.put("allScenariosDuration", "-");
      result.put("totalExecutionTime", "-");
      result.put("averageExecutionTime", "-");
    }
    return result;
  }

  public static LinkedHashMap<String, Integer> getTotalSummaryCount(List<Feature> features) {
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    if (features != null && !features.isEmpty()) {
      Arrays.stream(ReportColor.values()).sorted(Comparator.comparing(ReportColor::getOrdinal)).forEach(status -> {
        switch (status) {
          case PASSED:
            result.put(status.getCapitalizedName(),
                features.stream().map(Feature::getPassedScenarios).reduce(0, Integer::sum));
            break;
          case FAILED:
            result.put(status.getCapitalizedName(),
                features.stream().map(Feature::getFailedScenarios).reduce(0, Integer::sum));
            break;
          case SKIPPED:
            result.put(status.getCapitalizedName(),
                features.stream().map(Feature::getSkippedScenarios).reduce(0, Integer::sum));
            break;
          case PENDING:
            result.put(status.getCapitalizedName(),
                features.stream().map(Feature::getPendingScenarios).reduce(0, Integer::sum));
            break;
          case UNDEFINED:
            result.put(status.getCapitalizedName(),
                features.stream().map(Feature::getUndefinedScenarios).reduce(0, Integer::sum));
            break;
          default:
            break;
        }
      });
    } else {
      Arrays.stream(ReportColor.values())
          .sorted(Comparator.comparing(ReportColor::getOrdinal))
          .forEach(status -> result.put(status.getCapitalizedName(), 0));
    }

    return result;
  }
}
