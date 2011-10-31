/*
 * Created on 19/set/2011
 * Copyright 2011 by Andrea Vacondio (andrea.vacondio@gmail.com).
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.sejda.core.writer.model;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sejda.common.ComponentsUtility.nullSafeClose;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.xmlgraphics.image.codec.util.SeekableOutputStream;
import org.apache.xmlgraphics.image.writer.ImageWriterParams;
import org.sejda.model.exception.TaskIOException;
import org.sejda.model.image.TiffCompressionType;
import org.sejda.model.parameter.image.AbstractPdfToImageParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract implementation of an adapter for an xml graphics image writer
 * 
 * @param <T>
 *            task parameter
 * @author Andrea Vacondio
 * 
 */
abstract class AbstractXmlGraphicsImageWriterAdapter<T extends AbstractPdfToImageParameters> implements ImageWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractXmlGraphicsImageWriterAdapter.class);

    private static final Map<TiffCompressionType, String> TIFF_COMPRESSION_TYPE_CACHE;
    static {
        Map<TiffCompressionType, String> compressionTypesCache = new HashMap<TiffCompressionType, String>();
        compressionTypesCache.put(TiffCompressionType.PACKBITS, "PackBits");
        compressionTypesCache.put(TiffCompressionType.NONE, "NONE");
        compressionTypesCache.put(TiffCompressionType.JPEG_TTN2, "JPEG");
        compressionTypesCache.put(TiffCompressionType.DEFLATE, "Deflate");
        TIFF_COMPRESSION_TYPE_CACHE = Collections.unmodifiableMap(compressionTypesCache);
    }

    private OutputStream outputDestination;

    public void openWriteDestination(File destination, T params) throws TaskIOException {
        try {
            openWriteDestination(new SeekableOutputStream(new RandomAccessFile(destination, "rw")), params);
        } catch (FileNotFoundException e) {
            throw new TaskIOException("Unable to find destination file.", e);
        }
    }

    public void setOutputStream(OutputStream destination) {
        this.outputDestination = destination;
    }

    public void closeDestination() throws TaskIOException {
        try {
            nullSafeClose(outputDestination);
        } catch (IOException e) {
            throw new TaskIOException(e);
        }
    }

    /**
     * 
     * @param params
     * @param compressionType
     * @return a new {@link ImageWriterParams} for the given input.
     */
    ImageWriterParams newImageWriterParams(T params, TiffCompressionType compressionType) {
        ImageWriterParams imageWriterParams = new ImageWriterParams();
        imageWriterParams.setResolution(params.getResolutionInDpi());
        String compression = TIFF_COMPRESSION_TYPE_CACHE.get(compressionType);
        if (isNotBlank(compression)) {
            imageWriterParams.setCompressionMethod(compression);
        } else {
            LOG.warn("{} compression type is currently not supported by XML Graphics.", compressionType);
        }
        return imageWriterParams;
    }

    /**
     * 
     * @return the opened write destination or null if not opened
     */
    OutputStream getOutputDestination() {
        return outputDestination;
    }

    public void close() throws IOException {
        nullSafeClose(getOutputDestination());
    }
}
