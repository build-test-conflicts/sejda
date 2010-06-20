/*
 * Created on 04/giu/2010
 * Copyright (C) 2010 by Andrea Vacondio (andrea.vacondio@gmail.com).
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.sejda.core.support.io.handler;

import org.sejda.core.manipulation.model.output.AbstractPdfOutput;

/**
 * Destination where the output will be written
 * 
 * @author Andrea Vacondio
 * 
 */
public interface Destination {

    /**
     * @return the {@link AbstractPdfOutput} where the input will be written to
     */
    AbstractPdfOutput getOutputDestination();

    /**
     * @return true if the destination should be overwritten if already exists
     */
    boolean isOverwrite();

    /**
     * DSL interface for a file destination
     * 
     * @author Andrea Vacondio
     * 
     */
    public static interface FileDestination extends Destination {

        // on purpose
    }

}
