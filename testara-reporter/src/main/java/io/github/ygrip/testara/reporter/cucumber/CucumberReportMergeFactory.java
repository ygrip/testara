package io.github.ygrip.testara.reporter.cucumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;

import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.model.AggregateSummary;
import io.github.ygrip.testara.reporter.parser.CucumberReportParser;
import io.github.ygrip.testara.reporter.support.CommonUtil;
import io.github.ygrip.testara.reporter.support.ObjectMapperHelper;

public class CucumberReportMergeFactory {
  private final Logger log = Logger.getLogger(CucumberReportMergeFactory.class.getName());
  private final List<String> paths;
  private final Map<String, Feature> mapped;
  private final static ObjectMapper MAPPER = ObjectMapperHelper.mapper();

  private CucumberReportMergeFactory(List<String> paths) {
    this.paths = paths;
    this.mapped = new ConcurrentHashMap<>(); // Use ConcurrentHashMap for thread safety
  }

  public AggregateSummary aggregate(String filePath) throws Exception {
    if (filePath == null || filePath.trim().isEmpty()) {
      throw new Exception("No filepath specified");
    } else if (!filePath.trim().endsWith(".json")) {
      throw new Exception("Invalid report file path, target file path should be a json file");
    } else {
      Stopwatch stopwatch = Stopwatch.createStarted();
      List<Feature> features = combine();
      AggregateSummary aggregateSummary = new AggregateSummary();
      Map<String, Object> summary = CommonUtil.generateReportResultsSummary(features);
      aggregateSummary.setSummary(CommonUtil.getTotalSummaryCount(features));
      aggregateSummary.setTotalScenarios(Integer.parseInt(summary.get("totalScenarios").toString()));
      aggregateSummary.setTotalSteps(Integer.parseInt(summary.get("totalSteps").toString()));
      aggregateSummary.setFastestTest(summary.get("fastestTest").toString());
      aggregateSummary.setSlowestTest(summary.get("slowestTest").toString());
      aggregateSummary.setTotalExecutionTime(summary.get("totalExecutionTime").toString());
      log.info(String.format("Aggregate cucumber report took %s ms . Aggregated report location : %s",
          stopwatch.stop().elapsed(TimeUnit.MILLISECONDS),
          filePath));
      FileHelper.writeToFile(MAPPER.writeValueAsString(aggregateSummary), filePath);
      return aggregateSummary;
    }
  }

  public String mergeAs(String filePath) throws Exception {
    if (filePath == null || filePath.trim().isEmpty()) {
      throw new Exception("No filepath specified");
    } else if (!filePath.trim().endsWith(".json")) {
      throw new Exception("Invalid report file path, target file path should be a json file");
    } else {
      Stopwatch stopwatch = Stopwatch.createStarted();
      List<Feature> features = combine();
      for (String path : this.paths) {
        int attempt = 0;
        while (true) {
          try {
            boolean deleted = FileHelper.deleteFile(path);
            if (deleted) {
              break;
            } else {
              attempt++;
            }
          } catch (Exception ignored) {
            attempt++;
            continue;
          }
          if (attempt > 3) {
            break;
          }
        }
      }
      log.info(String.format("Merge cucumber report took %s ms\nMerged report location : %s",
          stopwatch.stop().elapsed(TimeUnit.MILLISECONDS),
          filePath));
      return FileHelper.writeToFile(MAPPER.writeValueAsString(features), filePath);
    }
  }

  public List<Feature> getMergedFeatures() {
    Stopwatch stopwatch = Stopwatch.createStarted();
    log.info(String.format("Merge cucumber features took %s ms", stopwatch.stop().elapsed(TimeUnit.MILLISECONDS)));
    return combine();
  }

  private List<Feature> combine() {
    try {
      // Parse JSON files in parallel for better performance
      List<Feature> features = parseJsonFilesInParallel(this.paths);
      
      // Process features in parallel using streams
      features.parallelStream().forEach(feature -> {
        String featureId = feature.getId();
        mapped.compute(featureId, (key, existingFeature) -> {
          if (existingFeature == null) {
            return feature;
          } else {
            // Merge features with same ID
            List<Element> oldElements = existingFeature.getElements();
            List<Element> newElements = feature.getElements();
            
            // Use the feature with earlier start time as base
            if (feature.getStartTime().isBefore(existingFeature.getStartTime())) {
              feature.setElements(mergeElements(feature.getElements(), existingFeature.getElements()));
              return feature;
            } else {
              existingFeature.setElements(mergeElements(oldElements, newElements));
              return existingFeature;
            }
          }
        });
      });
      
      return new ArrayList<>(mapped.values());
    } catch (Exception error) {
      log.warning("Fail to parse json files " + error.getMessage());
    }
    return new ArrayList<>();
  }

  /**
   * Parse JSON files in parallel for improved performance
   */
  private List<Feature> parseJsonFilesInParallel(List<String> jsonFiles) throws Exception {
    if (jsonFiles.isEmpty()) {
      throw new Exception("No report file was added!");
    }

    // Use parallel processing for file parsing
    List<CompletableFuture<List<Feature>>> futures = jsonFiles.stream()
        .map(jsonFile -> CompletableFuture.supplyAsync(() -> {
          try {
            return CucumberReportParser.parseJsonFiles(Collections.singletonList(jsonFile));
          } catch (Exception e) {
            log.warning("Failed to parse file: " + jsonFile + " - " + e.getMessage());
            return new ArrayList<Feature>();
          }
        }, ForkJoinPool.commonPool()))
        .collect(Collectors.toList());

    // Collect all results
    List<Feature> allFeatures = new ArrayList<>();
    for (CompletableFuture<List<Feature>> future : futures) {
      try {
        allFeatures.addAll(future.get());
      } catch (Exception e) {
        log.warning("Error getting parsed features: " + e.getMessage());
      }
    }

    return allFeatures;
  }

  private List<Element> mergeElements(List<Element> elements, List<Element> newElements) {
    if (newElements == null || newElements.isEmpty()) {
      return elements;
    }
    if (elements == null || elements.isEmpty()) {
      return newElements;
    }

    ConcurrentHashMap<String, Element> mappedNewElements = mapElements(newElements);
    List<Element> result = new ArrayList<>(elements.size() + newElements.size());
    
    // Process existing elements
    for (int i = 0; i < elements.size(); i++) {
      Element element = elements.get(i);
      String uniqueId = getElementUniqueId(element, i + 1 < elements.size() ? elements.get(i + 1) : null);
      
      Element newElement = mappedNewElements.remove(uniqueId);
      if (newElement != null) {
        // Choose the element with the later start time (more recent)
        if (element.getStartTime() != null && newElement.getStartTime() != null) {
          result.add(element.getStartTime().isBefore(newElement.getStartTime()) ? newElement : element);
        } else {
          result.add(newElement);
        }
      } else {
        result.add(element);
      }
    }
    
    // Add remaining new elements
    if (!mappedNewElements.isEmpty()) {
      result.addAll(mappedNewElements.values());
    }

    return result;
  }

  private ConcurrentHashMap<String, Element> mapElements(List<Element> elements) {
    ConcurrentHashMap<String, Element> result = new ConcurrentHashMap<>();
    for (int i = 0; i < elements.size(); i++) {
      int next = i + 1;
      String uniqueId;
      if (next < elements.size()) {
        uniqueId = getElementUniqueId(elements.get(i), elements.get(next));
      } else {
        uniqueId = getElementUniqueId(elements.get(i), null);
      }
      result.put(uniqueId, elements.get(i));
    }
    return result;
  }

  private String getElementUniqueId(Element element, Element nextElement) {
    if (element.isBackground()) {
      if (nextElement == null) {
        return String.format("%s-%s", element.getType(), element.getLine());
      } else {
        return String.format("%s-%s", element.getType(), nextElement.getId());
      }
    } else {
      return element.getId();
    }
  }

  public static class Builder {
    public static CucumberReportMergeFactory using(List<String> paths) {
      return new CucumberReportMergeFactory(paths);
    }
  }
}
