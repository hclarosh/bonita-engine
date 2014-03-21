/**
 * Copyright (C) 2013 BonitaSoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA 02110-1301, USA.
 **/
package org.bonitasoft.engine.data.instance.model.impl;

import java.io.Serializable;

import org.bonitasoft.engine.data.definition.model.SDataDefinition;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

/**
 * @author Matthieu Chaffotte
 * @author Celine Souchet
 */
public final class SXMLObjectDataInstanceImpl extends SDataInstanceImpl {

    private static final long serialVersionUID = 3477539801307784883L;

    private String value;

    public SXMLObjectDataInstanceImpl() {
        super();
    }

    public SXMLObjectDataInstanceImpl(final SDataDefinition dataDefinition) {
        super(dataDefinition);
    }

    @Override
    public Serializable getValue() {
        return revert(value);
    }

    @Override
    public void setValue(final Serializable value) {
        this.value = convert(value);
    }

    @Override
    public String getDiscriminator() {
        return SXMLObjectDataInstanceImpl.class.getSimpleName();
    }

    private String convert(final Serializable value) {
        final XStream xStream = new XStream(new StaxDriver());
        return xStream.toXML(value);
    }

    private Serializable revert(final String value) {
        if (value != null) {
            final XStream xstream = new XStream(new StaxDriver());
            return (Serializable) xstream.fromXML(value);
        }
        return null;
    }

}