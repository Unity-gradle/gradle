/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.version.GradleVersionResolver;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;

import java.io.File;

public class GradleVersionCheckingSettingsLoader implements SettingsLoader {

    private final SettingsLoader delegate;
    private final TextResourceFactory textResourceFactory;
    private final BuildLayoutFactory buildLayoutFactory;
    private static final Logger LOGGER = Logging.getLogger(GradleVersionCheckingSettingsLoader.class);

    public GradleVersionCheckingSettingsLoader(SettingsLoader delegate, TextResourceFactory textResourceFactory, BuildLayoutFactory buildLayoutFactory) {
        this.delegate = delegate;
        this.textResourceFactory = textResourceFactory;
        this.buildLayoutFactory = buildLayoutFactory;
    }

    @Override
    public SettingsState findAndLoadSettings(GradleInternal gradle) {
        BuildLayout layout = buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(gradle.getStartParameter()));
        File wrapperPropertiesFile = new File(layout.getRootDirectory(), "gradle/wrapper/gradle-wrapper.properties");

        if (wrapperPropertiesFile.exists()) {
            try {
                WrapperConfiguration configuration = WrapperExecutor.forWrapperPropertiesFile(wrapperPropertiesFile).getConfiguration();

                GradleVersionResolver gradleVersionResolver = new GradleVersionResolver(textResourceFactory);
                GradleVersion gradleVersionFromUrl = gradleVersionResolver.getGradleVersionFromUrl(configuration.getDistribution().toString());

                if (!gradleVersionFromUrl.equals(GradleVersion.current())) {
                    LOGGER.warn("The Gradle version specified in the wrapper configuration ({}) does not match the current Gradle version ({}). " +
                            "This may lead to unexpected behavior. Please ensure that the wrapper is configured correctly.",
                        gradleVersionFromUrl, GradleVersion.current());
                }
            } catch (Throwable e) {
                LOGGER.info("A gradle-wrapper.properties was found, however we failed to resolve the version from the wrapper configuration url. " +
                    "This means we cannot validate the version against the current Gradle version.", e);
            }
        }

        return delegate.findAndLoadSettings(gradle);
    }
}
