/*
 * Copyright 2017 by Eduard Weissmann (edi.weissmann@gmail.com).
 * This file is part of Sejda.
 *
 * Sejda is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Sejda is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Sejda.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.sejda.impl.sambox;

import static java.util.Objects.nonNull;
import static org.sejda.common.ComponentsUtility.nullSafeCloseQuietly;
import static org.sejda.core.notification.dsl.ApplicationEventsNotifier.notifyEvent;
import static org.sejda.core.support.io.IOUtils.createTemporaryBuffer;
import static org.sejda.core.support.io.model.FileOutput.file;
import static org.sejda.core.support.prefix.NameGenerator.nameGenerator;
import static org.sejda.core.support.prefix.model.NameGenerationRequest.nameRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.sejda.core.support.io.MultipleOutputWriter;
import org.sejda.core.support.io.OutputWriters;
import org.sejda.impl.sambox.component.DefaultPdfSourceOpener;
import org.sejda.impl.sambox.component.PDDocumentHandler;
import org.sejda.impl.sambox.component.PdfScaler;
import org.sejda.model.exception.TaskException;
import org.sejda.model.input.PdfSource;
import org.sejda.model.input.PdfSourceOpener;
import org.sejda.model.PageSize;
import org.sejda.model.parameter.ResizePagesParameters;
import org.sejda.model.pdf.encryption.PdfAccessPermission;
import org.sejda.model.scale.ScaleType;
import org.sejda.model.task.BaseTask;
import org.sejda.model.task.TaskExecutionContext;
import org.sejda.sambox.pdmodel.PDPage;
import org.sejda.sambox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SAMBox implementation of a task that can resize pages or add page margins
 * 
 * @author Eduard Weissmann
 *
 */
public class ResizePagesTask extends BaseTask<ResizePagesParameters> {

    private static final Logger LOG = LoggerFactory.getLogger(ResizePagesTask.class);

    private int totalSteps;
    private PDDocumentHandler documentHandler = null;
    private MultipleOutputWriter outputWriter;
    private PdfSourceOpener<PDDocumentHandler> documentLoader;

    @Override
    public void before(ResizePagesParameters parameters, TaskExecutionContext executionContext) throws TaskException {
        super.before(parameters, executionContext);
        totalSteps = parameters.getSourceList().size();
        documentLoader = new DefaultPdfSourceOpener();
        outputWriter = OutputWriters.newMultipleOutputWriter(parameters.getExistingOutputPolicy(), executionContext);
    }

    @Override
    public void execute(ResizePagesParameters parameters) throws TaskException {
        int currentStep = 0;

        for (PdfSource<?> source : parameters.getSourceList()) {
            executionContext().assertTaskNotCancelled();
            currentStep++;
            LOG.debug("Opening {}", source);

            try {
                documentHandler = source.open(documentLoader);
                documentHandler.getPermissions().ensurePermission(PdfAccessPermission.MODIFY);
                documentHandler.setCreatorOnPDDocument();

                File tmpFile = createTemporaryBuffer(parameters.getOutput());
                LOG.debug("Created output on temporary buffer {}", tmpFile);

                Collection<PDPage> pages = new ArrayList<>();
                if(parameters.getPageSelection().isEmpty()) {
                    // all pages
                    for(PDPage p : documentHandler.getPages()) {
                        pages.add(p);
                    }
                } else {
                    // specific pages
                    for(int pageNumber: parameters.getPages(documentHandler.getNumberOfPages())){
                        pages.add(documentHandler.getPage(pageNumber));
                    }
                }

                // we either apply margins or change the page size
                if (nonNull(parameters.getMargins())) {

                    LOG.debug("Adding margins of {} (inches) to {} pages", parameters.getMargins(), pages.size());
                    PdfScaler.margin(documentHandler.getUnderlyingPDDocument(), pages, parameters.getMargins());
                } else {

                    PageSize pageSize = parameters.getPageSize();
                    if (pageSize != null) {
                        PDRectangle desiredPageSize = new PDRectangle(pageSize.getWidth(), pageSize.getHeight());
                        LOG.debug("Resizing {} pages to match {}", pages.size(), desiredPageSize);

                        PdfScaler scaler = new PdfScaler(ScaleType.PAGE);
                        scaler.changePageSize(documentHandler.getUnderlyingPDDocument(), pages, desiredPageSize);
                    }
                }

                documentHandler.setVersionOnPDDocument(parameters.getVersion());
                documentHandler.setCompress(parameters.isCompress());
                documentHandler.savePDDocument(tmpFile);

                String outName = nameGenerator(parameters.getOutputPrefix())
                        .generate(nameRequest().originalName(source.getName()).fileNumber(currentStep));
                outputWriter.addOutput(file(tmpFile).name(outName));
            } finally {
                nullSafeCloseQuietly(documentHandler);
            }

            notifyEvent(executionContext().notifiableTaskMetadata()).stepsCompleted(currentStep).outOf(totalSteps);
        }

        parameters.getOutput().accept(outputWriter);
        LOG.debug("Input documents scaled and written to {}", parameters.getOutput());
    }

    @Override
    public void after() {
        nullSafeCloseQuietly(documentHandler);
    }
}
