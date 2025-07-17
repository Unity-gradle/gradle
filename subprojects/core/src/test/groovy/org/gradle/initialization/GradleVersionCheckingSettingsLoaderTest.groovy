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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.TextResource
import org.gradle.api.resources.TextResourceFactory
import org.gradle.initialization.layout.BuildLayout
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class GradleVersionCheckingSettingsLoaderTest extends Specification {

    TextResourceFactory textResourceFactory = Mock(TextResourceFactory)
    SettingsLoader delegate = Mock(SettingsLoader)
    GradleInternal gradle = Mock(GradleInternal)
    BuildLayoutFactory layoutFactory = Mock(BuildLayoutFactory)
    @Rule
    TemporaryFolder tempFolder = new TemporaryFolder()
    final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def 'emits warning when loading settings with Gradle version check and Gradle version does not match'() {
        given:
        def loader = new GradleVersionCheckingSettingsLoader(delegate, textResourceFactory, layoutFactory)
        def rootDir = tempFolder.newFolder()
        def wrapperDir = new File(rootDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        new File(wrapperDir, "gradle-wrapper.properties").text = """distributionUrl=https://services.gradle.org/distributions/gradle-7.6-rc-3-bin.zip"""

        when:
        loader.findAndLoadSettings(gradle)

        then:
        1 * gradle.getStartParameter() >> Mock(StartParameterInternal) {
            getCurrentDir() >> rootDir
        }
        1 * textResourceFactory.fromUri(_) >> { uri ->
            return Mock(TextResource) {
                asString() >> """[{"version" : "7.6", "downloadUrl": "https://services.gradle.org/distributions/gradle-7.6-bin.zip"},
                    {"version" : "7.6-rc-3", "downloadUrl": "https://services.gradle.org/distributions/gradle-7.6-rc-3-bin.zip"},
                    {"version" : "9.0.3", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.0.3-bin.zip"},
                    {"version" : "9.1", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.1-bin.zip"},
                    {"version" : "9.0.0-rc-1", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.0.0-rc-1-bin.zip"}]"""
            }
        }

        1 * layoutFactory.getLayoutFor(_) >> Mock(BuildLayout) {
            getRootDirectory() >> rootDir
        }

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        events.size() == 1
        events[0].message.startsWith('The Gradle version specified in the wrapper configuration (Gradle 7.6-rc-3) does not match the current Gradle version')
    }

    def 'emits no message when loading settings with Gradle version check and Gradle version does match'() {
        given:
        def loader = new GradleVersionCheckingSettingsLoader(delegate, textResourceFactory, layoutFactory)
        def rootDir = tempFolder.newFolder()
        def wrapperDir = new File(rootDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        new File(wrapperDir, "gradle-wrapper.properties").text = """distributionUrl=https://services.gradle.org/distributions/gradle-current-bin.zip"""

        when:
        loader.findAndLoadSettings(gradle)

        then:
        1 * gradle.getStartParameter() >> Mock(StartParameterInternal) {
            getCurrentDir() >> rootDir
        }

        1 * textResourceFactory.fromUri(_) >> { uri ->
            return Mock(TextResource) {
                asString() >> """[{"version" : "7.6", "downloadUrl": "https://services.gradle.org/distributions/gradle-7.6-bin.zip"},
                    {"version" : "7.6-rc-3", "downloadUrl": "https://services.gradle.org/distributions/gradle-7.6-rc-3-bin.zip"},
                    {"version" : "9.0.3", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.0.3-bin.zip"},
                    {"version" : "9.1", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.1-bin.zip"},
                    {"version" : "9.0.0-rc-1", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.0.0-rc-1-bin.zip"}
                    {"version" : "${GradleVersion.current()}", "downloadUrl": "https://services.gradle.org/distributions/gradle-current-bin.zip"}]"""
            }
        }

        1 * layoutFactory.getLayoutFor(_) >> Mock(BuildLayout) {
            getRootDirectory() >> rootDir
        }

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        events.size() == 0
    }

    def 'do Nothing when loading settings with Gradle version check and there is no Gradle wrapper properties file'() {
        given:
        def loader = new GradleVersionCheckingSettingsLoader(delegate, textResourceFactory, layoutFactory)
        def rootDir = tempFolder.newFolder()
        def wrapperDir = new File(rootDir, "gradle/wrapper")
        wrapperDir.mkdirs()

        when:
        loader.findAndLoadSettings(gradle)

        then:
        1 * gradle.getStartParameter() >> Mock(StartParameterInternal) {
            getCurrentDir() >> rootDir
        }

        0 * textResourceFactory.fromUri(_)

        1 * layoutFactory.getLayoutFor(_) >> Mock(BuildLayout) {
            getRootDirectory() >> rootDir
        }

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        events.size() == 0
    }

    def 'emits info message when loading settings with Gradle version check and Gradle properties file has no url'() {
        given:
        def loader = new GradleVersionCheckingSettingsLoader(delegate, textResourceFactory, layoutFactory)
        def rootDir = tempFolder.newFolder()
        def wrapperDir = new File(rootDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        new File(wrapperDir, "gradle-wrapper.properties").text = """1111111"""

        when:
        loader.findAndLoadSettings(gradle)

        then:
        1 * gradle.getStartParameter() >> Mock(StartParameterInternal) {
            getCurrentDir() >> rootDir
        }

        0 * textResourceFactory.fromUri(_)

        1 * layoutFactory.getLayoutFor(_) >> Mock(BuildLayout) {
            getRootDirectory() >> rootDir
        }

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.INFO}
        events.size() == 1
        events[0].throwable instanceof RuntimeException
        events[0].message.startsWith("A gradle-wrapper.properties was found, however we failed to resolve the version from the wrapper configuration url. This means we cannot validate the version against the current Gradle version.")
    }

    def 'emits info message when loading settings with Gradle version check and the service returns an error'() {
        given:
        def loader = new GradleVersionCheckingSettingsLoader(delegate, textResourceFactory, layoutFactory)
        def rootDir = tempFolder.newFolder()
        def wrapperDir = new File(rootDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        new File(wrapperDir, "gradle-wrapper.properties").text = """distributionUrl=https://services.gradle.org/distributions/gradle-current-bin.zip"""

        when:
        loader.findAndLoadSettings(gradle)

        then:
        1 * gradle.getStartParameter() >> Mock(StartParameterInternal) {
            getCurrentDir() >> rootDir
        }

        1 * textResourceFactory.fromUri(_) >> { uri ->
            throw new MissingResourceException("Missing Resource")
        }

        1 * layoutFactory.getLayoutFor(_) >> Mock(BuildLayout) {
            getRootDirectory() >> rootDir
        }

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.INFO}
        events.size() == 1
        events[0].throwable instanceof MissingResourceException
        events[0].message.startsWith("A gradle-wrapper.properties was found, however we failed to resolve the version from the wrapper configuration url. This means we cannot validate the version against the current Gradle version.")
    }

    def 'emits info message when loading settings with Gradle version check and the url can\'t be found'() {
        given:
        def loader = new GradleVersionCheckingSettingsLoader(delegate, textResourceFactory, layoutFactory)
        def rootDir = tempFolder.newFolder()
        def wrapperDir = new File(rootDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        new File(wrapperDir, "gradle-wrapper.properties").text = """distributionUrl=https://services.gradle.org/distributions/gradle-9.100.0-bin.zip"""

        when:
        loader.findAndLoadSettings(gradle)

        then:
        1 * gradle.getStartParameter() >> Mock(StartParameterInternal) {
            getCurrentDir() >> rootDir
        }

        1 * textResourceFactory.fromUri(_) >> { uri ->
            return Mock(TextResource) {
                asString() >> """[{"version" : "7.6", "downloadUrl": "https://services.gradle.org/distributions/gradle-7.6-bin.zip"},
                    {"version" : "7.6-rc-3", "downloadUrl": "https://services.gradle.org/distributions/gradle-7.6-rc-3-bin.zip"},
                    {"version" : "9.0.3", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.0.3-bin.zip"},
                    {"version" : "9.1", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.1-bin.zip"},
                    {"version" : "9.0.0-rc-1", "downloadUrl": "https://services.gradle.org/distributions/gradle-9.0.0-rc-1-bin.zip"}]"""
            }
        }

        1 * layoutFactory.getLayoutFor(_) >> Mock(BuildLayout) {
            getRootDirectory() >> rootDir
        }

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.INFO}
        events.size() == 1
        events[0].throwable instanceof IllegalArgumentException
        events[0].message.startsWith("A gradle-wrapper.properties was found, however we failed to resolve the version from the wrapper configuration url. This means we cannot validate the version against the current Gradle version.")
    }
}
