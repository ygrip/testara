package io.github.ygrip.testara.core.file;

import io.github.ygrip.testara.core.model.ExcelType;
import io.github.ygrip.testara.core.model.UpdateExcelData;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbookFactory;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>ExcelHelper class.</p>
 *
 * @author yunaz.ramadhan on 2/14/2022
 * @version $Id: $Id
 */
public class ExcelHelper {

  private static Map<Short, CellStyle> getPredefinedCellStyle(Workbook workbook) {
    List<Integer> indexes = Arrays.asList(0, 1, 2, 22);
    Map<Short, CellStyle> result = new HashMap<>();
    for (Integer index : indexes) {
      CellStyle style = workbook.createCellStyle();
      style.setDataFormat(index.shortValue());
      result.put(index.shortValue(), style);
    }

    return result;
  }

  /**
   * <p>openExcelWorkbook.</p>
   *
   * @param file      a {@link File} object.
   * @param excelType a {@link ExcelType} object.
   * @return a {@link org.apache.poi.ss.usermodel.Workbook} object.
   * @throws Exception if any.
   */
  public static Workbook openExcelWorkbook(File file, ExcelType excelType) throws Exception {
    if (excelType.equals(ExcelType.XLSX)) {
      return XSSFWorkbookFactory.createWorkbook(OPCPackage.create(file));
    } else {
      return HSSFWorkbookFactory.createWorkbook(POIFSFileSystem.create(file));
    }
  }

  private static Sheet getActiveSheet(File inputStream, ExcelType excelType) throws Exception {
    Workbook workbook = openExcelWorkbook(inputStream, excelType);
    return workbook.getSheetAt(workbook.getActiveSheetIndex());
  }

  private static Sheet getSheetAt(File inputStream, ExcelType excelType, int sheetIndex) throws Exception {
    return openExcelWorkbook(inputStream, excelType).getSheetAt(sheetIndex);
  }

  private static Sheet getSheetAt(File inputStream, ExcelType excelType, String sheetName) throws Exception {
    return openExcelWorkbook(inputStream, excelType).getSheet(sheetName);
  }

  /**
   * <p>writeDataToExcelDocumentWithoutHeader.</p>
   *
   * @param datas    a {@link List} object.
   * @param filePath a {@link String} object.
   * @return a {@link String} object.
   * @throws Exception if any.
   */
  public static String writeDataToExcelDocumentWithoutHeader(List<List<Object>> datas, String filePath)
      throws Exception {
    return writeDataToExcelDocumentWithoutHeader(datas, filePath, "Sheet 1");
  }

  /**
   * <p>writeDataToExcelDocumentWithoutHeader.</p>
   *
   * @param datas     a {@link List} object.
   * @param filePath  a {@link String} object.
   * @param sheetName a {@link String} object.
   * @return a {@link String} object.
   * @throws Exception if any.
   */
  public static String writeDataToExcelDocumentWithoutHeader(List<List<Object>> datas,
      String filePath,
      String sheetName) throws Exception {
    boolean result = false;
    ExcelType type = ExcelType.XLS;

    if (ObjectUtils.isNotEmpty(datas)) {
      if (!filePath.endsWith(".xls") && !filePath.endsWith(".xlsx")) {
        filePath = filePath + ".xls";
      }
      if (filePath.endsWith(".xlsx")) {
        type = ExcelType.XLSX;
      }
      Workbook workbook;
      if (type.equals(ExcelType.XLS)) {
        workbook = new HSSFWorkbook();
      } else {
        workbook = new XSSFWorkbook();
      }
      Files.createDirectories(Paths.get(filePath).getParent());
      FileOutputStream outputStream = new FileOutputStream(filePath);

      try {
        Sheet sheet;
        CreationHelper helper;
        Font font;
        if (type.equals(ExcelType.XLS)) {
          sheet = ((HSSFWorkbook) workbook).createSheet(sheetName);
          helper = ((HSSFWorkbook) workbook).getCreationHelper();
          font = ((HSSFWorkbook) workbook).createFont();
          font.setFontHeightInPoints((short) 10);
          font.setBold(false);
        } else {
          sheet = ((XSSFWorkbook) workbook).createSheet(sheetName);
          helper = ((XSSFWorkbook) workbook).getCreationHelper();
          font = ((XSSFWorkbook) workbook).createFont();
          font.setFontHeightInPoints((short) 10);
          font.setBold(false);
        }
        final Map<Short, CellStyle> cellStyles = getPredefinedCellStyle(workbook);

        //create content
        createExcelContent(0, datas, sheet, font, helper, cellStyles, type);

        workbook.setFirstVisibleTab(0);
        workbook.setSelectedTab(0);
        workbook.setActiveSheet(0);
        workbook.setMissingCellPolicy(Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        workbook.setForceFormulaRecalculation(false);
        workbook.write(outputStream);
        result = true;
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        workbook.close();
        outputStream.flush();
        outputStream.close();
      }
    }
    if (result) {
      return filePath;
    } else {
      return null;
    }
  }

  /**
   * <p>writeDataToExcelDocument.</p>
   *
   * @param datas    a {@link List} object.
   * @param filePath a {@link String} object.
   * @return a {@link String} object.
   * @throws Exception if any.
   */
  public static String writeDataToExcelDocument(List<Map<String, Object>> datas, String filePath) throws Exception {
    return writeDataToExcelDocument(datas, filePath, "Sheet 1");
  }

  /**
   * <p>writeDataToExcelDocument.</p>
   *
   * @param datas     a {@link List} object.
   * @param filePath  a {@link String} object.
   * @param sheetName a {@link String} object.
   * @return a {@link String} object.
   * @throws Exception if any.
   */
  public static String writeDataToExcelDocument(List<Map<String, Object>> datas, String filePath, String sheetName)
      throws Exception {
    boolean result = false;
    ExcelType type = ExcelType.XLS;

    if (ObjectUtils.isNotEmpty(datas)) {
      if (!filePath.endsWith(".xls") && !filePath.endsWith(".xlsx")) {
        filePath = filePath + ".xls";
      }
      if (filePath.endsWith(".xlsx")) {
        type = ExcelType.XLSX;
      }
      Workbook workbook;
      if (type.equals(ExcelType.XLS)) {
        workbook = new HSSFWorkbook();
      } else {
        workbook = new XSSFWorkbook();
      }
      Files.createDirectories(Paths.get(filePath).getParent());
      FileOutputStream outputStream = new FileOutputStream(filePath);

      try {
        Sheet sheet;
        CreationHelper helper;
        Font font;
        if (type.equals(ExcelType.XLS)) {
          sheet = ((HSSFWorkbook) workbook).createSheet(sheetName);
          helper = ((HSSFWorkbook) workbook).getCreationHelper();
          font = ((HSSFWorkbook) workbook).createFont();
          font.setFontHeightInPoints((short) 10);
          font.setBold(false);
        } else {
          sheet = ((XSSFWorkbook) workbook).createSheet(sheetName);
          helper = ((XSSFWorkbook) workbook).getCreationHelper();
          font = ((XSSFWorkbook) workbook).createFont();
          font.setFontHeightInPoints((short) 10);
          font.setBold(false);
        }
        final Map<Short, CellStyle> cellStyles = getPredefinedCellStyle(workbook);
        List<String> headers = new ArrayList<>(datas.get(0).keySet());

        //create header
        createExcelHeader(headers, workbook, sheet, helper, type);

        int index = 1;
        List<List<Object>> content = new ArrayList<>();
        for (Map<String, Object> data : datas) {
          List<Object> row = new ArrayList<>();
          for (String header : headers) {
            row.add(data.getOrDefault(header, ""));
          }
          content.add(row);
        }

        //create content
        createExcelContent(1, content, sheet, font, helper, cellStyles, type);

        workbook.setFirstVisibleTab(0);
        workbook.setSelectedTab(0);
        workbook.setActiveSheet(0);
        workbook.setMissingCellPolicy(Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        workbook.setForceFormulaRecalculation(false);
        workbook.write(outputStream);
        result = true;
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        workbook.close();
        outputStream.flush();
        outputStream.close();
      }
    }
    if (result) {
      return filePath;
    } else {
      return null;
    }
  }

  private static short guessCellTypeValue(Cell cell, Object obj, CreationHelper helper) {
    if (obj instanceof Boolean) {
      cell.setCellValue(Boolean.parseBoolean(String.valueOf(obj)));
      return helper.createDataFormat().getFormat(BuiltinFormats.getBuiltinFormat(0));
    } else if (obj instanceof Integer || obj instanceof Long) {
      cell.setCellValue(Long.parseLong(String.valueOf(obj)));
      return helper.createDataFormat().getFormat(BuiltinFormats.getBuiltinFormat(1));
    } else if (obj instanceof Float || obj instanceof Double) {
      cell.setCellValue(Double.parseDouble(String.valueOf(obj)));
      return helper.createDataFormat().getFormat(BuiltinFormats.getBuiltinFormat(2));
    } else if (obj instanceof Date) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime((Date) obj);
      cell.setCellValue(calendar);
      return helper.createDataFormat().getFormat(BuiltinFormats.getBuiltinFormat(22));
    } else {
      cell.setCellValue(String.valueOf(obj));
      return helper.createDataFormat().getFormat(BuiltinFormats.getBuiltinFormat(0));
    }
  }

  /**
   * <p>readExcelFile.</p>
   *
   * @param filePath a {@link String} object.
   * @return a {@link Map} object.
   * @throws Exception if any.
   */
  public static Map<Integer, List<Object>> readExcelFile(String filePath) throws Exception {
    ExcelType type = ExcelType.XLS;
    if (!filePath.endsWith(".xls") && !filePath.endsWith(".xlsx")) {
      filePath = filePath + ".xls";
    }
    if (filePath.endsWith(".xlsx")) {
      type = ExcelType.XLSX;
    }
    Map<Integer, List<Object>> result = new HashMap<>();
    File file = FileHelper.openFile(filePath);
    Sheet sheet = getActiveSheet(file, type);
    int i = 0;
    for (Row row : sheet) {
      result.put(i, new ArrayList<>());
      for (Cell cell : row) {
        switch (cell.getCellType()) {
          case STRING:
            result.get(i).add(cell.getRichStringCellValue().getString());
            break;
          case NUMERIC:
            if (DateUtil.isCellDateFormatted(cell)) {
              result.get(i).add(cell.getDateCellValue());
            } else {
              result.get(i).add(cell.getNumericCellValue());
            }
            break;
          case BOOLEAN:
            result.get(i).add(cell.getBooleanCellValue());
            break;
          case FORMULA:
            result.get(i).add(cell.getCellFormula());
            break;
          default:
            result.get(i).add("");
        }
      }
      i++;
    }
    return result;
  }

  /**
   * <p>readExcelFileAsJsonObject.</p>
   *
   * @param filePath a {@link String} object.
   * @return a {@link List} object.
   * @throws Exception if any.
   */
  public static List<Map<String, Object>> readExcelFileAsJsonObject(String filePath) throws Exception {
    Map<Integer, List<Object>> datas = readExcelFile(filePath);
    List<Map<String, Object>> result = new ArrayList<>();
    List<String> keyset = datas.get(0).stream().map(String::valueOf).collect(Collectors.toList());
    int index = 1;
    for (int i = index; i < datas.size(); i++) {
      List<Object> row = datas.get(i);
      Map<String, Object> item = new HashMap<>();
      for (int j = 0; j < keyset.size(); j++) {
        item.put(keyset.get(j), row.get(j));
      }
      result.add(item);
    }
    return result;
  }

  /**
   * <p>updateExcelData.</p>
   *
   * @param filePath   a {@link String} object.
   * @param updateData a {@link List} object.
   * @return a boolean.
   * @throws Exception if any.
   */
  public static boolean updateExcelData(String filePath, List<UpdateExcelData> updateData) throws Exception {
    boolean result = false;
    int dataUpdated = 0;
    ExcelType type = ExcelType.XLS;
    if (!filePath.endsWith(".xls") && !filePath.endsWith(".xlsx")) {
      filePath = filePath + ".xls";
    }
    if (filePath.endsWith(".xlsx")) {
      type = ExcelType.XLSX;
    }
    File file = FileHelper.openFile(filePath);
    Workbook workbook = openExcelWorkbook(file, type);
    CreationHelper creationHelper = workbook.getCreationHelper();
    Sheet sheet = workbook.getSheetAt(workbook.getActiveSheetIndex());
    for (UpdateExcelData data : updateData) {
      try {
        int rowIndex = data.getRow() - 1;
        rowIndex = Math.max(rowIndex, 0);
        Row row = sheet.getRow(rowIndex);
        if (ObjectUtils.isEmpty(row)) {
          row = sheet.createRow(rowIndex);
        }
        int colIdx = CellReference.convertColStringToIndex(data.getColumn());
        Cell cell = row.getCell(colIdx);
        if (ObjectUtils.isEmpty(cell)) {
          cell = row.createCell(colIdx);
        }
        guessCellTypeValue(cell, data.getData(), creationHelper);
        dataUpdated++;
      } catch (Exception ignored) {

      }
    }
    Files.createDirectories(Paths.get(filePath).getParent());
    FileOutputStream outputStream = new FileOutputStream(filePath);

    try {
      workbook.write(outputStream);
      result = true;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      workbook.close();
      outputStream.flush();
      outputStream.close();
    }
    return dataUpdated == updateData.size() && result;
  }

  private static void createExcelHeader(List<String> headers,
      Workbook workbook,
      Sheet sheet,
      CreationHelper helper,
      ExcelType type) {
    if (type.equals(ExcelType.XLS)) {
      HSSFRow header = ((HSSFSheet) sheet).createRow(0);
      HSSFFont headerFont = ((HSSFWorkbook) workbook).createFont();
      headerFont.setFontHeightInPoints((short) 12);
      headerFont.setBold(true);

      HSSFCellStyle headerStyle = ((HSSFWorkbook) workbook).createCellStyle();
      headerStyle.setFont(headerFont);
      for (int i = 0; i < headers.size(); i++) {
        HSSFCell headerCell = header.createCell(i);
        short dataFormat = guessCellTypeValue(headerCell, headers.get(i), helper);
        headerCell.setCellStyle(headerStyle);
        sheet.setColumnWidth(i, headers.get(i).length() * 500);
      }
    } else {
      XSSFRow header = ((XSSFSheet) sheet).createRow(0);
      XSSFFont headerFont = ((XSSFWorkbook) workbook).createFont();
      headerFont.setFontHeightInPoints((short) 12);
      headerFont.setBold(true);

      XSSFCellStyle headerStyle = ((XSSFWorkbook) workbook).createCellStyle();
      headerStyle.setFont(headerFont);
      for (int i = 0; i < headers.size(); i++) {
        XSSFCell headerCell = header.createCell(i);
        short dataFormat = guessCellTypeValue(headerCell, headers.get(i), helper);
        headerCell.setCellStyle(headerStyle);
        sheet.setColumnWidth(i, headers.get(i).length() * 500);
      }
    }
  }

  private static void createExcelContent(int index,
      List<List<Object>> contents,
      Sheet sheet,
      Font font,
      CreationHelper helper,
      Map<Short, CellStyle> styleMap,
      ExcelType type) {
    if (type.equals(ExcelType.XLS)) {
      for (List<Object> data : contents) {
        HSSFRow row = ((HSSFSheet) sheet).createRow(index);
        for (int i = 0; i < data.size(); i++) {
          HSSFCell cell = row.createCell(i);
          short dataFormat = guessCellTypeValue(cell, data.get(i), helper);
          HSSFCellStyle rowStyle = (HSSFCellStyle) styleMap.get(dataFormat);
          rowStyle.setDataFormat(dataFormat);
          rowStyle.setFont(font);
          cell.setCellStyle(rowStyle);
        }
        index++;
      }
    } else {
      for (List<Object> data : contents) {
        XSSFRow row = ((XSSFSheet) sheet).createRow(index);
        for (int i = 0; i < data.size(); i++) {
          XSSFCell cell = row.createCell(i);
          short dataFormat = guessCellTypeValue(cell, data.get(i), helper);
          XSSFCellStyle rowStyle = (XSSFCellStyle) styleMap.get(dataFormat);
          rowStyle.setDataFormat(dataFormat);
          rowStyle.setFont(font);
          cell.setCellStyle(rowStyle);
        }
        index++;
      }
    }
  }
}
