/*
 * Created on Oct 5, 2011
 * Copyright 2010 by Eduard Weissmann (edi.weissmann@gmail.com).
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
package org.sejda.conversion;

import org.sejda.model.pdf.viewerpreference.PdfDirection;

/**
 * Adapter class for enum {@link PdfDirection}. Provides initialization from string
 * 
 * @author Eduard Weissmann
 * 
 */
public class PdfDirectionAdapter extends EnumAdapter<PdfDirection> {

    public PdfDirectionAdapter(String userFriendlyName) {
        super(userFriendlyName, PdfDirection.class, "direction");
    }
}
