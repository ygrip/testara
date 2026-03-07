package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.file.FileHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.util.List;

/**
 * <p>OpenFileCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "openfile", overwrite = true, cacheable = true)
public class OpenFileCommand implements CommandLogic<File> {
  private static final String DIRECTORY = "/src/test/resources/templates/request/file/";

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public File execute(List<Object> parameters) {
    if (ObjectUtils.isEmpty(parameters)) {
      return null;
    }
    String directory = parameters.size() > 1 ?
        System.getProperty("user.dir") + parameters.get(0) :
        System.getProperty("user.dir") + DIRECTORY;
    String filename = parameters.size() > 1 ? String.valueOf(parameters.get(1)) : String.valueOf(parameters.get(0));
    String filePath = String.format("%s%s%s", directory, File.separator, filename);
    File result = null;
    if (FileHelper.openFile(filePath).isFile()) {
      result = FileHelper.openFile(filePath);
    }
    return result;
  }
}
