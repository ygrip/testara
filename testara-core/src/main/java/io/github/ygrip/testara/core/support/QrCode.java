package io.github.ygrip.testara.core.support;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.aztec.AztecWriter;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.oned.CodaBarWriter;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.oned.Code93Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.EAN8Writer;
import com.google.zxing.oned.ITFWriter;
import com.google.zxing.oned.UPCAWriter;
import com.google.zxing.oned.UPCEWriter;
import com.google.zxing.pdf417.PDF417Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

@Log4j2
public class QrCode {

  QrCode() {

  }

  public static QrCodeWriter write(String data) {
    return new QrCode.Writer().write(data);
  }

  public static QrCodeReader read(String path) {
    return new QrCode.Reader().read(path);
  }

  public static QrCodeReader read(Path path) {
    return new QrCode.Reader().read(path);
  }

  QrCodeReader getReader(String path) {
    return new QrCodeReader(path);
  }

  QrCodeReader getReader(Path path) {
    return new QrCodeReader(path);
  }

  QrCodeWriter getWriter(String data) {
    return new QrCodeWriter(data);
  }


  public static class Writer {
    Writer() {

    }

    public QrCodeWriter write(String data) {
      return new QrCode().getWriter(data);
    }
  }


  public static class Reader {
    Reader() {

    }

    public QrCodeReader read(String path) {
      return new QrCode().getReader(path);
    }

    public QrCodeReader read(Path path) {
      return new QrCode().getReader(path);
    }
  }


  public class QrCodeReader {
    private final Path path;

    QrCodeReader(String path) {
      this.path = Paths.get(path);
    }

    QrCodeReader(Path path) {
      this.path = path;
    }

    public String getData() throws IOException, NotFoundException {
      BinaryBitmap binaryBitmap =
          new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(ImageIO.read(Files.newInputStream(path)))));
      Result result = new MultiFormatReader().decode(binaryBitmap);

      return result.getText();
    }
  }


  public class QrCodeWriter {
    private final String data;
    private BarcodeFormat format;
    private int codeHeight;
    private int codeWidth;

    QrCodeWriter(String data) {
      this.data = data;
    }

    public QrCodeWriter as(BarcodeFormat format) {
      this.format = format;
      return this;
    }

    public QrCodeWriter width(int codeWidth) {
      this.codeWidth = codeWidth;
      return this;
    }

    public QrCodeWriter height(int codeHeight) {
      this.codeHeight = codeHeight;
      return this;
    }

    private int getCodeHeight() {
      return this.codeHeight == 0 ? 50 : this.codeHeight;
    }

    private int getCodeWidth() {
      return this.codeWidth == 0 ? 50 : this.codeWidth;
    }

    private BarcodeFormat getFormat() {
      return this.format == null ? BarcodeFormat.QR_CODE : this.format;
    }

    public BufferedImage generate() {
      try {
        HashMap<EncodeHintType, ErrorCorrectionLevel> hintMap = new HashMap<>();
        hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);

        com.google.zxing.Writer codeWriter;
        final BarcodeFormat selectedFormat = getFormat();
        if (selectedFormat == BarcodeFormat.QR_CODE) {
          codeWriter = new QRCodeWriter();
        } else if (selectedFormat == BarcodeFormat.CODE_128) {
          codeWriter = new Code128Writer();
        } else if (selectedFormat == BarcodeFormat.CODABAR) {
          codeWriter = new CodaBarWriter();
        } else if (selectedFormat == BarcodeFormat.CODE_93) {
          codeWriter = new Code93Writer();
        } else if (selectedFormat == BarcodeFormat.CODE_39) {
          codeWriter = new Code39Writer();
        } else if (selectedFormat == BarcodeFormat.AZTEC) {
          codeWriter = new AztecWriter();
        } else if (selectedFormat == BarcodeFormat.DATA_MATRIX) {
          codeWriter = new DataMatrixWriter();
        } else if (selectedFormat == BarcodeFormat.EAN_8) {
          codeWriter = new EAN8Writer();
        } else if (selectedFormat == BarcodeFormat.EAN_13) {
          codeWriter = new EAN13Writer();
        } else if (selectedFormat == BarcodeFormat.ITF) {
          codeWriter = new ITFWriter();
        } else if (selectedFormat == BarcodeFormat.PDF_417) {
          codeWriter = new PDF417Writer();
        } else if (selectedFormat == BarcodeFormat.UPC_A) {
          codeWriter = new UPCAWriter();
        } else if (selectedFormat == BarcodeFormat.UPC_E) {
          codeWriter = new UPCEWriter();
        } else {
          throw new RuntimeException("Format Not supported.");
        }

        BitMatrix byteMatrix = codeWriter.encode(this.data, selectedFormat, getCodeWidth(), getCodeHeight(), hintMap);

        return MatrixToImageWriter.toBufferedImage(byteMatrix);

      } catch (WriterException e) {
        e.printStackTrace();
        return null;
      }
    }

    public String generateAndSaveTo(String path) {
      return generateAndSaveTo(Paths.get(path));
    }

    public String generateAndSaveTo(Path path) {
      try {
        File outputFile = path.toFile();
        if (!outputFile.exists()) {
          if (!path.getParent().toFile().exists()) {
            Files.createDirectories(path.getParent());
          }
          Files.createFile(path);
        }
        boolean result = ImageIO.write(generate(), "png", outputFile);
        if (result) {
          return outputFile.getAbsolutePath();
        } else {
          log.warn("Fail to generate code");
          return null;
        }
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }
  }

}
