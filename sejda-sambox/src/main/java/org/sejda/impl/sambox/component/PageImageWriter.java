/*
 * Copyright 2016 by Eduard Weissmann (edi.weissmann@gmail.com).
 *
 * This file is part of the Sejda source code
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.sejda.impl.sambox.component;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_ProfileGray;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import javax.imageio.*;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.sejda.core.support.io.IOUtils;
import org.sejda.model.exception.TaskIOException;
import org.sejda.model.input.FileSource;
import org.sejda.model.input.Source;
import org.sejda.model.input.SourceDispatcher;
import org.sejda.model.input.StreamSource;
import org.sejda.sambox.pdmodel.PDDocument;
import org.sejda.sambox.pdmodel.PDPage;
import org.sejda.sambox.pdmodel.PDPageContentStream;
import org.sejda.sambox.pdmodel.graphics.PDXObject;
import org.sejda.sambox.pdmodel.graphics.form.PDFormXObject;
import org.sejda.sambox.pdmodel.graphics.image.PDImageXObject;
import org.sejda.sambox.pdmodel.graphics.image.UnsupportedTiffImageException;
import org.sejda.sambox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.sejda.sambox.util.Matrix;
import org.sejda.sambox.util.filetypedetector.FileType;
import org.sejda.sambox.util.filetypedetector.FileTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageImageWriter {
    private static final Logger LOG = LoggerFactory.getLogger(PageImageWriter.class);

    private PDDocument document;

    public PageImageWriter(PDDocument document) {
        this.document = document;
    }

    public void append(PDPage page, PDImageXObject image, Point2D position, float width, float height,
            PDExtendedGraphicsState gs, int rotation) throws TaskIOException {
        write(page, image, position, width, height, PDPageContentStream.AppendMode.APPEND, gs, true, rotation);
    }

    public void append(PDPage page, PDFormXObject image, Point2D position, float width, float height,
            PDExtendedGraphicsState gs, int rotation) throws TaskIOException {
        write(page, image, position, width, height, PDPageContentStream.AppendMode.APPEND, gs, true, rotation);
    }

    public void prepend(PDPage page, PDImageXObject image, Point2D position, float width, float height,
            PDExtendedGraphicsState gs, int rotation) throws TaskIOException {
        write(page, image, position, width, height, PDPageContentStream.AppendMode.PREPEND, gs, false, rotation);
    }

    public void prepend(PDPage page, PDFormXObject image, Point2D position, float width, float height,
            PDExtendedGraphicsState gs, int rotation) throws TaskIOException {
        write(page, image, position, width, height, PDPageContentStream.AppendMode.PREPEND, gs, false, rotation);
    }

    private void write(PDPage page, PDXObject image, Point2D position, float width, float height,
            PDPageContentStream.AppendMode mode, PDExtendedGraphicsState gs, boolean resetContext, int rotation)
            throws TaskIOException {
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page, mode, true, resetContext)) {
            AffineTransform at = new AffineTransform(width, 0, 0, height, (float) position.getX(), (float) position.getY());
            if(rotation != 0) {
                at.rotate(Math.toRadians(rotation));
            }

            if (image instanceof PDFormXObject) {
                contentStream.drawImage((PDFormXObject) image, new Matrix(at), gs);
            } else {
                contentStream.drawImage((PDImageXObject) image, new Matrix(at), gs);
            }
        } catch (IOException e) {
            throw new TaskIOException("An error occurred writing image to the page.", e);
        }
    }

    public static PDImageXObject toPDXImageObject(Source<?> imageSource) throws TaskIOException {
        return imageSource.dispatch(new SourceDispatcher<PDImageXObject>() {
            @Override
            public PDImageXObject dispatch(FileSource source) throws TaskIOException {
                try {
                    return createFromFile(source.getSource().getPath());
                } catch (Exception e) {
                    throw new TaskIOException("An error occurred creating PDImageXObject from file source: " + imageSource.getName(), e);
                }
            }

            @Override
            public PDImageXObject dispatch(StreamSource source) throws TaskIOException {
                try {
                    String extension = FilenameUtils.getExtension(source.getName());
                    File tmp = IOUtils.createTemporaryBuffer("." + extension);
                    try (FileOutputStream fos = new FileOutputStream(tmp)) {
                        org.apache.commons.io.IOUtils.copyLarge(source.getSource(), fos);
                    }
                    return createFromFile(tmp.getPath());
                } catch (Exception e) {
                    throw new TaskIOException("An error occurred creating PDImageXObject from file source: " + imageSource.getName(), e);
                }
            }
        });
    }

    public static PDImageXObject createFromFile(String originalFilePath) throws TaskIOException, IOException {
        File file = new File(originalFilePath);
        // we might need to pre-process the image and use a different file

        Optional<File> maybeConvertedFile = convertCMYKJpegIf(file);
        if(maybeConvertedFile.isPresent()) {
            file = maybeConvertedFile.get();
        }

        maybeConvertedFile = convertICCGrayPngIf(file);
        if(maybeConvertedFile.isPresent()) {
            file = maybeConvertedFile.get();
        }

        String filePath = file.getAbsolutePath();

        try {
            return PDImageXObject.createFromFile(filePath);
        } catch (UnsupportedTiffImageException e) {
            LOG.warn("Found unsupported TIFF compression, converting TIFF to JPEG: " + e.getMessage());

            try {
                return PDImageXObject.createFromFile(convertTiffToJpg(filePath));
            } catch (UnsupportedOperationException ex) {
                if (ex.getMessage().contains("alpha channel")) {
                    LOG.warn("Found alpha channel image, JPEG compression failed, converting TIFF to PNG");
                    return PDImageXObject.createFromFile(convertTiffToPng(filePath));
                }
                throw ex;
            }
        }
    }

    public static String convertTiffToJpg(String filePath) throws IOException, TaskIOException {
        return convertImageTo(filePath, "jpeg");
    }

    public static String convertTiffToPng(String filePath) throws IOException, TaskIOException {
        return convertImageTo(filePath, "png");
    }

    private static FileType getFileType(File file) {
        try {
            return FileTypeDetector.detectFileType(file);
        } catch (IOException e) {
            return null;
        }
    }

    public static String convertImageTo(String filePath, String format) throws IOException, TaskIOException {
        BufferedImage image = ImageIO.read(new File(filePath));
        File tmpFile = IOUtils.createTemporaryBuffer("." + format);
        ImageOutputStream outputStream = new FileImageOutputStream(tmpFile);

        try {
            ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
            writer.setOutput(outputStream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (format.equals("jpeg")) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(1.0F);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            org.apache.commons.io.IOUtils.closeQuietly(outputStream);
        }

        return tmpFile.getPath();
    }

    /**
     * Checks if the input file is a JPEG using CMYK
     * If that's the case, converts to RGB and returns the file path
     */
    private static Optional<File> convertCMYKJpegIf(File file) throws IOException, TaskIOException {
        try {
            if (FileType.JPEG.equals(getFileType(file))) {
                try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
                    ImageReader reader = ImageIO.getImageReadersByFormatName("jpg").next();
                    boolean isCmyk = false;
                    try {
                        ImageIO.setUseCache(false);
                        reader.setInput(iis);
                        for (Iterator<ImageTypeSpecifier> it = reader.getImageTypes(0); it.hasNext(); ) {
                            ImageTypeSpecifier typeSpecifier = it.next();
                            if (typeSpecifier.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_CMYK) {
                                isCmyk = true;
                            }
                        }

                        if (isCmyk) {
                            LOG.debug("Detected a CMYK JPEG image, will convert to RGB and save to a new file");
                            // convert to rgb
                            // twelvemonkeys JPEG plugin already converts it to rgb when reading the image
                            // just write it out
                            BufferedImage image = reader.read(0);
                            File tmpFile = IOUtils.createTemporaryBufferWithName(file.getName());
                            ImageIO.write(image, "jpg", tmpFile);
                            return Optional.of(tmpFile);
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IIOException e) {
            if(e.getMessage().startsWith("Not a JPEG stream")) {
                // this was a different image format with a JPEG extension
            } else {
                throw e;
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if the input file is a PNG using ICC Gray color model
     * If that's the case, converts to RGB and returns the file path
     */
    private static Optional<File> convertICCGrayPngIf(File file) throws IOException, TaskIOException {
        try {
            if (FileType.PNG.equals(getFileType(file))) {
                try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
                    ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
                    boolean isICCGray = false;
                    try {
                        ImageIO.setUseCache(false);
                        reader.setInput(iis);
                        for (Iterator<ImageTypeSpecifier> it = reader.getImageTypes(0); it.hasNext(); ) {
                            ImageTypeSpecifier typeSpecifier = it.next();
                            ColorSpace colorSpace = typeSpecifier.getColorModel().getColorSpace();
                            if (colorSpace instanceof ICC_ColorSpace && ((ICC_ColorSpace) colorSpace).getProfile() instanceof ICC_ProfileGray) {
                                isICCGray = true;
                            }
                        }

                        if (isICCGray) {
                            LOG.debug("Detected a Gray PNG image, will convert to RGB and save to a new file");
                            // convert to rgb
                            BufferedImage original = reader.read(0);
                            BufferedImage rgb = toARGB(original);
                            File tmpFile = IOUtils.createTemporaryBufferWithName(file.getName());
                            ImageIO.write(rgb, "png", tmpFile);
                            return Optional.of(tmpFile);
                        }
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (IIOException e) {
            if(e.getMessage().equals("I/O error reading PNG header!")) {
                // this was a different image format with a PNG extension
            } else {
                throw e;
            }
        }

        return Optional.empty();
    }

    private static BufferedImage toARGB(BufferedImage i) {
        BufferedImage rgb = new BufferedImage(i.getWidth(null), i.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        rgb.createGraphics().drawImage(i, 0, 0, null);
        return rgb;
    }
}
