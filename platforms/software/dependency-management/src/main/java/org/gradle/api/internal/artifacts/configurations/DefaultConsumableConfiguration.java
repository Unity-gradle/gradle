/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.artifacts.ConsumableConfiguration;
import org.gradle.api.artifacts.DependencyResolutionListener;
import org.gradle.api.internal.ConfigurationServicesBundle;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.ResolveExceptionMapper;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.operations.BuildOperationRunner;

import javax.inject.Inject;

/**
 * A concrete consumable {@link DefaultConfiguration} that cannot change roles.
 */
public class DefaultConsumableConfiguration extends DefaultConfiguration implements ConsumableConfiguration {

    @Inject
    public DefaultConsumableConfiguration(
        ConfigurationServicesBundle configurationServices,
        DomainObjectContext domainObjectContext,
        String name,
        ConfigurationResolver resolver,
        ListenerBroadcast<DependencyResolutionListener> dependencyResolutionListeners,
        BuildOperationRunner buildOperationRunner,
        ResolveExceptionMapper exceptionMapper,
        AttributeDesugaring attributeDesugaring,
        UserCodeApplicationContext userCodeApplicationContext,
        ProjectStateRegistry projectStateRegistry,
        DocumentationRegistry documentationRegistry
    ) {
        super(
            configurationServices,
            domainObjectContext,
            name,
            false,
            resolver,
            dependencyResolutionListeners,
            buildOperationRunner,
            exceptionMapper,
            attributeDesugaring,
            userCodeApplicationContext,
            projectStateRegistry,
            ConfigurationRoles.CONSUMABLE,
            documentationRegistry,
            true
        );
    }

}
