package io.github.ygrip.testara.core.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author yunaz.ramadhan on 2/14/2022
 */
@Log4j2
public class CsvHelper {
  final static CsvMapper CSV_MAPPER = loadCsvMapper();

  private CsvHelper() {
  }

  private static CsvMapper loadCsvMapper() {
    return CsvMapper.builder()
        .enable(CsvParser.Feature.WRAP_AS_ARRAY)
        .enable(CsvGenerator.Feature.ALWAYS_QUOTE_EMPTY_STRINGS)
        .enable(CsvGenerator.Feature.OMIT_MISSING_TAIL_COLUMNS)
        .build();
  }

  public static CsvWriter write() {
    return new Builder().write();
  }

  public static CsvReader read(String filePath) throws Throwable {
    return new Builder().read(filePath);
  }

  protected CsvWriter initializeWriter() {
    return new CsvWriter();
  }

  protected CsvReader initializeReader(String filePath) throws Throwable {
    return new CsvReader(filePath);
  }

  static class Builder {
    Builder() {
    }

    CsvWriter write() {
      return new CsvHelper().initializeWriter();
    }

    CsvReader read(String filePath) throws Throwable {
      return new CsvHelper().initializeReader(filePath);
    }
  }


  public class CsvWriter {
    private List<?> rows;
    private boolean withHeader;
    private char separator;
    private CsvSchema schema;

    CsvWriter() {
      this.withHeader = true;
      this.separator = ',';
      this.schema = CsvSchema.emptySchema();
    }

    public CsvWriter withHeader() {
      this.withHeader = true;
      return this;
    }

    public CsvWriter withoutHeader() {
      this.withHeader = false;
      return this;
    }

    public CsvWriter withSeparator(Character separator) {
      this.separator = separator;
      return this;
    }

    public CsvWriter fromListOfObject(List<?> inputs) {
      this.rows = inputs;
      return this;
    }

    public CsvWriter fromList(List<List<?>> inputs) {
      this.rows = ObjectUtils.isEmpty(inputs) ? new ArrayList<>() : inputs;
      return this;
    }

    public CsvWriter withSchema(CsvSchema schema) {
      this.schema = schema;
      this.separator = schema.getColumnSeparator();
      return this;
    }

    private List<List<?>> processData(List<?> inputs) {
      List<List<?>> result = new ArrayList<>();
      if (!ObjectUtils.isEmpty(inputs)) {
        List<Map<String, Object>> parsedInput = MapperHelper.toObject(inputs, new TypeReference<>() {
        });
        List<Object> keys = new ArrayList<>(parsedInput.get(0).keySet());
        if (this.withHeader) {
          result.add(keys);
        }
        for (Map<String, Object> input : parsedInput) {
          result.add(new ArrayList<>(keys.stream()
              .map(key -> String.valueOf(input.get(key)))
              .collect(Collectors.toList())));
        }
      }
      return result;
    }

    public String into(String filePath) throws Exception {
      if (ObjectUtils.isEmpty(filePath)) {
        throw new Exception("File path cannot be empty");
      }
      return FileHelper.writeFile(CSV_MAPPER.writer(this.schema.withColumnSeparator(this.separator))
          .writeValueAsString(processData(this.rows)), filePath);
    }
  }


  public class CsvReader {
    private final File file;
    private boolean withHeader;
    private char separator;
    private CsvSchema schema;

    CsvReader(String filePath) throws Throwable {
      this.file = FileHelper.openFile(filePath);
      if (!this.file.isFile()) {
        throw new Exception("Cannot read csv from unknown source at : " + filePath);
      }
      this.withHeader = true;
      this.separator = ',';
      this.schema = CsvSchema.emptySchema().withHeader();
    }

    public CsvReader withHeader() {
      this.withHeader = true;
      return this;
    }

    public CsvReader withoutHeader() {
      this.withHeader = false;
      return this;
    }

    public CsvReader withSeparator(Character separator) {
      this.separator = separator;
      return this;
    }

    public CsvReader withSchema(CsvSchema schema) {
      this.schema = schema;
      this.withHeader = schema.usesHeader();
      this.separator = schema.getColumnSeparator();
      return this;
    }

    public List<List<String>> asList() throws IOException {
      List<List<String>> result;
      try (MappingIterator<List<String>> content = CSV_MAPPER.readerFor(List.class).readValues(this.file)) {
        result = content.readAll();
      }
      return this.withHeader ? result : result.stream().skip(1).collect(Collectors.toList());
    }

    public <T> List<T> as(Class<T> clazz) throws IOException {
      List<T> result = new ArrayList<>();
      try (MappingIterator<Map<String, String>> content = readContent()) {
        while (content.hasNext()) {
          result.add(MapperHelper.toObject(content.next(), clazz));
        }
      }
      return result;
    }

    public <T> List<T> as(TypeReference<T> reference) throws IOException {
      List<T> result = new ArrayList<>();
      try (MappingIterator<Map<String, String>> content = readContent()) {
        while (content.hasNext()) {
          result.add(MapperHelper.toObject(content.next(), reference));
        }
      }
      return result;
    }

    private MappingIterator<Map<String, String>> readContent() throws IOException {
      return CSV_MAPPER.readerFor(Map.class)
          .with(this.schema.withHeader().withColumnSeparator(this.separator))
          .readValues(this.file);
    }
  }
}