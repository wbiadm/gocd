/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server.service.materials;

import com.thoughtworks.go.config.materials.PluggableSCMMaterial;
import com.thoughtworks.go.config.materials.SubprocessExecutionContext;
import com.thoughtworks.go.domain.config.Configuration;
import com.thoughtworks.go.domain.config.ConfigurationProperty;
import com.thoughtworks.go.domain.materials.Modification;
import com.thoughtworks.go.domain.materials.Modifications;
import com.thoughtworks.go.domain.materials.Revision;
import com.thoughtworks.go.domain.materials.scm.PluggableSCMMaterialRevision;
import com.thoughtworks.go.domain.scm.SCM;
import com.thoughtworks.go.plugin.access.scm.SCMExtension;
import com.thoughtworks.go.plugin.access.scm.SCMProperty;
import com.thoughtworks.go.plugin.access.scm.SCMPropertyConfiguration;
import com.thoughtworks.go.plugin.access.scm.revision.ModifiedAction;
import com.thoughtworks.go.plugin.access.scm.revision.ModifiedFile;
import com.thoughtworks.go.plugin.access.scm.revision.SCMRevision;
import com.thoughtworks.go.util.json.JsonHelper;

import java.io.File;
import java.util.List;

import static java.util.Arrays.asList;

public class PluggableSCMMaterialPoller implements MaterialPoller<PluggableSCMMaterial> {

    private SCMExtension scmExtension;

    public PluggableSCMMaterialPoller(SCMExtension scmExtension) {
        this.scmExtension = scmExtension;
    }

    @Override
    public List<Modification> latestModification(final PluggableSCMMaterial material, File baseDir, SubprocessExecutionContext execCtx) {
        SCMPropertyConfiguration scmPropertyConfiguration = buildSCMPropertyConfigurations(material.getScmConfig());
        SCMRevision scmRevision = scmExtension.getLatestRevision(material.getPluginId(), scmPropertyConfiguration, baseDir.getAbsolutePath());
        return scmRevision == null ? new Modifications() : new Modifications(getModification(scmRevision));
    }

    @Override
    public List<Modification> modificationsSince(final PluggableSCMMaterial material, File baseDir, final Revision revision, SubprocessExecutionContext execCtx) {
        SCMPropertyConfiguration scmPropertyConfiguration = buildSCMPropertyConfigurations(material.getScmConfig());
        PluggableSCMMaterialRevision pluggableSCMMaterialRevision = (PluggableSCMMaterialRevision) revision;
        SCMRevision previouslyKnownRevision = new SCMRevision(pluggableSCMMaterialRevision.getRevision(), pluggableSCMMaterialRevision.getTimestamp(), null, null, pluggableSCMMaterialRevision.getData(), null);
        List<SCMRevision> scmRevisions = scmExtension.latestModificationSince(material.getPluginId(), scmPropertyConfiguration, baseDir.getAbsolutePath(), previouslyKnownRevision);
        return getModifications(scmRevisions);
    }

    private SCMPropertyConfiguration buildSCMPropertyConfigurations(SCM scmConfig) {
        SCMPropertyConfiguration scmPropertyConfiguration = new SCMPropertyConfiguration();
        populateConfiguration(scmConfig.getConfiguration(), scmPropertyConfiguration);
        return scmPropertyConfiguration;
    }

    private void populateConfiguration(Configuration configuration, com.thoughtworks.go.plugin.api.config.Configuration pluginConfiguration) {
        for (ConfigurationProperty configurationProperty : configuration) {
            pluginConfiguration.add(new SCMProperty(configurationProperty.getConfigurationKey().getName(), configurationProperty.getValue()));
        }
    }

    private List<Modification> getModifications(List<SCMRevision> scmRevisions) {
        Modifications modifications = new Modifications();
        if (scmRevisions == null || scmRevisions.isEmpty()) {
            return modifications;
        }
        for (SCMRevision scmRevision : scmRevisions) {
            modifications.add(getModification(scmRevision));
        }
        return modifications;
    }

    private Modification getModification(SCMRevision scmRevision) {
        Modification modification = new Modification(scmRevision.getUser(), scmRevision.getRevisionComment(), null,
                scmRevision.getTimestamp(), scmRevision.getRevision(), JsonHelper.toJsonString(scmRevision.getData()));
        if (scmRevision.getModifiedFiles() != null && !scmRevision.getModifiedFiles().isEmpty()) {
            for (ModifiedFile modifiedFile : scmRevision.getModifiedFiles()) {
                modification.createModifiedFile(modifiedFile.getFileName(), null, convertAction(modifiedFile.getAction()));
            }
        }
        return modification;
    }

    private com.thoughtworks.go.domain.materials.ModifiedAction convertAction(ModifiedAction modifiedFile) {
        if (modifiedFile == ModifiedAction.added) {
            return com.thoughtworks.go.domain.materials.ModifiedAction.added;
        } else if (modifiedFile == ModifiedAction.modified) {
            return com.thoughtworks.go.domain.materials.ModifiedAction.modified;
        } else if (modifiedFile == ModifiedAction.deleted) {
            return com.thoughtworks.go.domain.materials.ModifiedAction.deleted;
        }
        return com.thoughtworks.go.domain.materials.ModifiedAction.unknown;
    }
}
