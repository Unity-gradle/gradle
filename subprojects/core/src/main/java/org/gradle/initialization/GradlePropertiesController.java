/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Manages the lifecycle of {@link GradleProperties} for builds and projects within a build tree.
 *
 * <p>Properties go through three states:
 * <ul>
 * <li><b>Not loaded</b> - Initial state, accessing properties throws {@code IllegalStateException}</li>
 * <li><b>Loaded</b> - Properties loaded from {@code gradle.properties} files and available for use</li>
 * <li><b>Unloaded</b> - Build properties reset to not loaded state for reloading</li>
 * </ul>
 *
 * <p>Build-scoped properties are loaded once per build from the build root directory and can optionally
 * set system properties. Project-scoped properties merge project-local properties with their build's
 * properties, inheriting build settings while allowing project-specific overrides.
 */
@ServiceScope(Scope.BuildTree.class)
public interface GradlePropertiesController {

    /**
     * Returns build-scoped {@link GradleProperties} for the specified build.
     * Properties must be loaded first using {@link #loadGradleProperties(BuildIdentifier, File, boolean)}.
     *
     * @throws IllegalStateException if properties have not been loaded yet
     */
    GradleProperties getGradleProperties(BuildIdentifier buildId);

    /**
     * Returns project-scoped {@link GradleProperties} for the specified project.
     * Properties must be loaded first using {@link #loadGradleProperties(ProjectIdentity, File)}.
     *
     * @throws IllegalStateException if properties have not been loaded yet
     */
    GradleProperties getGradleProperties(ProjectIdentity projectId);

    /**
     * Loads build-scoped {@link GradleProperties} from the specified build root directory.
     *
     * <p>Properties are loaded from {@code gradle.properties} file in the build root directory.
     * If {@code setSystemProperties} is true, properties with {@code systemProp.} prefix are
     * set as system properties.
     *
     * <p>Can be called multiple times with the same arguments but will load only once.
     *
     * @param buildRootDir directory containing the {@code gradle.properties} file
     * @param setSystemProperties whether to set system properties from loaded properties
     * @throws IllegalStateException if called with different directory for the same build
     */
    void loadGradleProperties(BuildIdentifier buildId, File buildRootDir, boolean setSystemProperties);

    /**
     * Unloads build-scoped properties, resetting them to the not loaded state.
     *
     * <p>Subsequent calls to {@link #loadGradleProperties(BuildIdentifier, File, boolean)} will
     * reload properties and re-evaluate system property assignments.
     */
    void unloadGradleProperties(BuildIdentifier buildId);

    /**
     * Loads project-scoped {@link GradleProperties} from the specified project directory.
     *
     * <p>Properties are loaded from {@code gradle.properties} file in the project directory
     * and merged with the build-scoped properties, with project properties taking precedence.
     * System properties are never set from project-scoped properties.
     *
     * @param projectDir directory containing the project's {@code gradle.properties} file
     */
    void loadGradleProperties(ProjectIdentity projectId, File projectDir);

}
