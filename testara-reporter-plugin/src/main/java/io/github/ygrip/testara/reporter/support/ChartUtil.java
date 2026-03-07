package io.github.ygrip.testara.reporter.support;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ChartUtil {
  private final static String CHART_LINK_HOST = "https://quickchart.io/chart?c=";
  private final static String FORMATTER =
      "{type:'%s',data:{datasets:[{data:%s,backgroundColor:%s}]},width:%s,height:%s,format:'%s',options:{plugins:{datalabels:{color:'#fff',anchor:'center',align:'center',formatter:(val)=>{if(val>0){return val;}else{return null;}},font:{size:16,weight:'bold'}}}}}";

  public static String generateChartUrl(List<Integer> data,
      List<String> colors,
      String chartType,
      String outputType,
      int width,
      int height) throws UnsupportedEncodingException {
    return CHART_LINK_HOST + URLEncoder.encode(String.format(FORMATTER,
        chartType,
        generateData(data),
        generateColor(colors),
        width,
        height,
        outputType), StandardCharsets.UTF_8.toString());
  }

  private static String generateColor(List<String> colors) {
    return "[" + colors.stream().map(color -> '\'' + color + '\'').collect(Collectors.joining(","))
        + "]";
  }

  private static String generateData(List<Integer> data) {
    return "[" + data.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]";
  }
}
