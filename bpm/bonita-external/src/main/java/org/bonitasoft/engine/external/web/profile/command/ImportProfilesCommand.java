/**
 * Copyright (C) 2015 BonitaSoft S.A.
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
package org.bonitasoft.engine.external.web.profile.command;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.bonitasoft.engine.api.impl.SessionInfos;
import org.bonitasoft.engine.command.SCommandExecutionException;
import org.bonitasoft.engine.command.SCommandParameterizationException;
import org.bonitasoft.engine.command.TenantCommand;
import org.bonitasoft.engine.exception.ExecutionException;
import org.bonitasoft.engine.profile.ImportPolicy;
import org.bonitasoft.engine.profile.ProfilesImporter;
import org.bonitasoft.engine.profile.xml.ProfilesNode;
import org.bonitasoft.engine.service.TenantServiceAccessor;

/**
 * Specific Command to import profiles xml content as byte[].
 * "byte[]" : xml content
 *
 * @author Zhao Na
 * @author Matthieu Chaffotte
 * @author Celine Souchet
 */
public class ImportProfilesCommand extends TenantCommand {

    /**
     * @return a List<String> is a warning message list in case of non-existing User, Group or Role to map the profile to.
     */
    @Override
    public Serializable execute(final Map<String, Serializable> parameters, final TenantServiceAccessor serviceAccessor)
            throws SCommandParameterizationException, SCommandExecutionException {
        ProfilesImporter profilesImporter = serviceAccessor.getProfilesImporter();

        try {
            byte[] xmlContent = (byte[]) parameters.get("xmlContent");
            if (xmlContent == null) {
                throw new SCommandParameterizationException("Parameters map must contain an entry  xmlContent with a byte array value.");
            }
            final ProfilesNode profiles = profilesImporter.convertFromXml(new String(xmlContent));
            return (Serializable) profilesImporter
                    .toWarnings(profilesImporter.importProfiles(profiles, ImportPolicy.DELETE_EXISTING, SessionInfos.getUserIdFromSession()));
        } catch (ExecutionException | IOException e) {
            throw new SCommandExecutionException(e);
        }
    }

}
