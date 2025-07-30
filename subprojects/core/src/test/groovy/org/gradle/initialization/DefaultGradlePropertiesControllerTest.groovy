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

package org.gradle.initialization

import org.gradle.api.Project
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.initialization.properties.DefaultProjectPropertiesLoader
import org.gradle.initialization.properties.DefaultSystemPropertiesInstaller
import org.gradle.initialization.properties.MutableGradleProperties
import org.gradle.initialization.properties.ProjectPropertiesLoader
import org.gradle.initialization.properties.SystemPropertiesInstaller
import org.gradle.util.Path
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import static java.util.Collections.emptyMap
import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX

class DefaultGradlePropertiesControllerTest extends Specification {

    private Map<String, String> prefixedEnvironmentVariables = emptyMap()
    private Map<String, String> prefixedSystemProperties = emptyMap()
    private Map<String, String> projectPropertiesArgs = emptyMap()
    private Map<String, String> systemPropertiesArgs = emptyMap()
    private File gradleUserHome = new File("gradleUserHome")
    private final rootBuildId = DefaultBuildIdentifier.ROOT

    private final Environment environment = Mock(Environment) {
        getSystemProperties() >> Mock(Environment.Properties) {
            byNamePrefix(SYSTEM_PROJECT_PROPERTIES_PREFIX) >> { prefixedSystemProperties }
        }
        getVariables() >> Mock(Environment.Properties) {
            byNamePrefix(ENV_PROJECT_PROPERTIES_PREFIX) >> { prefixedEnvironmentVariables }
        }
    }

    private final StartParameterInternal startParameter = Mock(StartParameterInternal) {
        getProjectProperties() >> { projectPropertiesArgs }
        getSystemPropertiesArgs() >> { systemPropertiesArgs }
        getGradleUserHomeDir() >> gradleUserHome
    }

    private final IGradlePropertiesLoader gradlePropertiesLoader = Mock(IGradlePropertiesLoader)
    private final ProjectPropertiesLoader projectPropertiesLoader = Mock(ProjectPropertiesLoader)
    private final SystemPropertiesInstaller systemPropertiesInstaller = Mock(SystemPropertiesInstaller)

    @Rule
    public SetSystemProperties sysProp = new SetSystemProperties()

    def "attached GradleProperties #method fails before loading"() {

        given:
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def properties = controller.getGradleProperties(rootBuildId)
        0 * controller.loadGradleProperties(_, _, _)

        when:
        switch (method) {
            case "find": properties.find("anything"); break
            case "getProperties": properties.getProperties(); break
            default: assert false
        }

        then:
        thrown(IllegalStateException)

        where:
        method << ["find", "getProperties"]
    }

    def "attached GradleProperties methods succeed after loading"() {

        given:
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def settingsDir = new File('.')
        def properties = controller.getGradleProperties(rootBuildId)
        def buildProperties = Mock(MutableGradleProperties)
        1 * gradlePropertiesLoader.loadGradleProperties(settingsDir) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        1 * buildProperties.updateOverrideProperties([:])
        _ * buildProperties.getProperties() >> [property: '42']
        _ * buildProperties.find("property") >> '42'

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)

        then:
        properties.find("property") == '42'
        properties.getProperties() == [property: '42']
    }

    def 'loadGradleProperties is idempotent'() {

        given:
        // use a different File instance for each call to ensure it is compared by value
        def currentDir = { new File('.') }
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def buildProperties = Mock(MutableGradleProperties)

        when: "calling the method multiple times with the same value"
        controller.loadGradleProperties(rootBuildId, currentDir(), true)
        controller.loadGradleProperties(rootBuildId, currentDir(), true)

        then:
        1 * gradlePropertiesLoader.loadGradleProperties(currentDir()) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        _ * buildProperties.updateOverrideProperties(_)
    }

    def 'loadGradleProperties fails when called with different argument'() {

        given:
        def settingsDir = new File('a')
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def buildProperties = Mock(MutableGradleProperties)
        1 * gradlePropertiesLoader.loadGradleProperties(settingsDir) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        _ * buildProperties.updateOverrideProperties(_)

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)
        controller.loadGradleProperties(rootBuildId, new File('b'), true)

        then:
        thrown(IllegalStateException)
    }

    def "environment variables have precedence over project properties in project scope"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def settingsDir = new File("settingsDir")
        def projectDir = new File("projectDir")

        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        // Set up build properties
        1 * environment.propertiesFile(propertiesFileFromDir(gradleUserHome)) >> ["prop": "user value"]
        1 * environment.propertiesFile(propertiesFileFromDir(settingsDir)) >> ["prop": "settings value"]

        // Set up project properties file
        1 * environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES)) >> ["prop": "project value"]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)  // Load build properties first
        controller.loadGradleProperties(rootProjectIdentity, projectDir)  // Load project properties
        def properties = controller.getGradleProperties(rootProjectIdentity)

        then:
        properties.find("prop") == "env value"  // Environment variables should win over project properties
    }

    def "system properties have precedence over environment variables in project scope"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def settingsDir = new File("settingsDir")
        def projectDir = new File("projectDir")

        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        // Set up build properties
        1 * environment.propertiesFile(propertiesFileFromDir(gradleUserHome)) >> ["prop": "user value"]
        1 * environment.propertiesFile(propertiesFileFromDir(settingsDir)) >> ["prop": "settings value"]

        // Set up project properties file
        1 * environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES)) >> ["prop": "project value"]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)  // Load build properties first
        controller.loadGradleProperties(rootProjectIdentity, projectDir)  // Load project properties
        def properties = controller.getGradleProperties(rootProjectIdentity)

        then:
        properties.find("prop") == "system value"  // System properties should win over environment variables
    }

    def "start parameter properties have precedence over system properties in project scope"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def settingsDir = new File("settingsDir")
        def projectDir = new File("projectDir")

        prefixedEnvironmentVariables = [(ENV_PROJECT_PROPERTIES_PREFIX + "prop"): "env value"]
        prefixedSystemProperties = [(SYSTEM_PROJECT_PROPERTIES_PREFIX + "prop"): "system value"]
        projectPropertiesArgs = ["prop": "param value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        // Set up build properties
        1 * environment.propertiesFile(propertiesFileFromDir(gradleUserHome)) >> ["prop": "user value"]
        1 * environment.propertiesFile(propertiesFileFromDir(settingsDir)) >> ["prop": "settings value"]

        // Set up project properties file
        1 * environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES)) >> ["prop": "project value"]

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)  // Load build properties first
        controller.loadGradleProperties(rootProjectIdentity, projectDir)  // Load project properties
        def properties = controller.getGradleProperties(rootProjectIdentity)

        then:
        properties.find("prop") == "param value"  // Start parameter properties should win over system properties
    }

    def "load sets system properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> [
            (Project.SYSTEM_PROP_PREFIX + ".userSystemProp"): "userSystemValue"
        ]
        1 * environment.propertiesFile(settingsPropertiesFile) >> [
            (Project.SYSTEM_PROP_PREFIX + ".userSystemProp"): "settingsSystemValue",
            (Project.SYSTEM_PROP_PREFIX + ".settingsSystemProp2"): "settingsSystemValue2"
        ]
        systemPropertiesArgs = ["systemPropArgKey": "systemPropArgValue"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def systemPropertiesInstaller = new DefaultSystemPropertiesInstaller(Mock(EnvironmentChangeTracker), startParameter)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, setSystemProperties)

        then:
        (setSystemProperties ? "userSystemValue" : null) == System.getProperty("userSystemProp")
        (setSystemProperties ? "settingsSystemValue2" : null) == System.getProperty("settingsSystemProp2")
        (setSystemProperties ? "systemPropArgValue" : null) == System.getProperty("systemPropArgKey")

        where:
        setSystemProperties << [true, false]
    }

    def "start parameter system properties have precedence over properties files"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]
        prefixedSystemProperties = [:]
        systemPropertiesArgs = ["prop": "commandline value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def systemPropertiesInstaller = new DefaultSystemPropertiesInstaller(Mock(EnvironmentChangeTracker), startParameter)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, true)

        then:
        "commandline value" == System.getProperty("prop")
    }

    def "different build identifiers maintain separate gradle properties"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        2 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        2 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)

        def rootBuildId = this.rootBuildId
        def includedBuildId = new DefaultBuildIdentifier(Path.path(":included"))

        def rootProperties = controller.getGradleProperties(rootBuildId)
        def includedProperties = controller.getGradleProperties(includedBuildId)

        when:
        controller.loadGradleProperties(rootBuildId, settingsDir, false)
        controller.loadGradleProperties(includedBuildId, settingsDir, false)

        then:
        rootProperties.find("prop") == "user value"
        includedProperties.find("prop") == "user value"

        and: "properties are separate instances"
        !rootProperties.is(includedProperties)
    }

    def "unloading gradle properties for specific build id"() {
        given:
        def settingsDir = new File("settingsDir")
        def gradleUserHomePropertiesFile = propertiesFileFromDir(gradleUserHome)
        def settingsPropertiesFile = propertiesFileFromDir(settingsDir)

        1 * environment.propertiesFile(gradleUserHomePropertiesFile) >> ["prop": "user value"]
        1 * environment.propertiesFile(settingsPropertiesFile) >> ["prop": "settings value"]

        def gradlePropertiesLoader = new DefaultGradlePropertiesLoader(startParameter, environment)
        def projectPropertiesLoader = new DefaultProjectPropertiesLoader(startParameter, environment)
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, Mock(SystemPropertiesInstaller), projectPropertiesLoader)

        def buildId = rootBuildId
        def properties = controller.getGradleProperties(buildId)

        when: "properties are loaded"
        controller.loadGradleProperties(buildId, settingsDir, false)

        then: "properties can be accessed"
        properties.find("prop") == "user value"

        when: "properties are unloaded"
        controller.unloadGradleProperties(buildId)

        and: "accessing properties fails"
        properties.find("prop")

        then:
        thrown(IllegalStateException)
    }

    def "project properties are loaded and accessible"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def projectDir = new File("project")
        def buildDir = new File("build")
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        // Set up build properties first
        def buildProperties = Mock(MutableGradleProperties)
        1 * gradlePropertiesLoader.loadGradleProperties(buildDir) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        1 * buildProperties.updateOverrideProperties([:])
        _ * buildProperties.mergeProperties([projectProp: 'projectValue']) >> [projectProp: 'projectValue']

        1 * environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES)) >> [projectProp: 'projectValue']

        when:
        controller.loadGradleProperties(rootBuildId, buildDir, false) // Load build properties first
        controller.loadGradleProperties(rootProjectIdentity, projectDir)
        def properties = controller.getGradleProperties(rootProjectIdentity)

        then:
        properties.find("projectProp") == "projectValue"
        properties.getProperties() == [projectProp: 'projectValue']
    }

    def "project properties inherit from build properties"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def projectDir = new File("project")
        def buildDir = new File("build")
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        // Set up build properties first
        def buildProperties = Mock(MutableGradleProperties)
        1 * gradlePropertiesLoader.loadGradleProperties(buildDir) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        1 * buildProperties.updateOverrideProperties([:])
        _ * buildProperties.getProperties() >> [buildProp: 'buildValue']
        _ * buildProperties.mergeProperties([projectProp: 'projectValue']) >> [buildProp: 'buildValue', projectProp: 'projectValue']

        1 * environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES)) >> [projectProp: 'projectValue']

        when:
        controller.loadGradleProperties(rootBuildId, buildDir, false)
        controller.loadGradleProperties(rootProjectIdentity, projectDir)
        def properties = controller.getGradleProperties(rootProjectIdentity)

        then:
        properties.find("buildProp") == "buildValue"
        properties.find("projectProp") == "projectValue"
        properties.getProperties() == [buildProp: 'buildValue', projectProp: 'projectValue']
    }

    def "project properties override build properties with local properties"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def projectDir = new File("project")
        def buildDir = new File("build")
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)

        // Set up build properties first
        def buildProperties = Mock(MutableGradleProperties)
        1 * gradlePropertiesLoader.loadGradleProperties(buildDir) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        1 * buildProperties.updateOverrideProperties([:])
        _ * buildProperties.getProperties() >> [sharedProp: 'buildValue', buildOnlyProp: 'buildOnlyValue']
        _ * buildProperties.mergeProperties([sharedProp: 'projectValue', projectOnlyProp: 'projectOnlyValue']) >> [sharedProp: 'projectValue', buildOnlyProp: 'buildOnlyValue', projectOnlyProp: 'projectOnlyValue']

        1 * environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES)) >> [sharedProp: 'projectValue', projectOnlyProp: 'projectOnlyValue']

        when:
        controller.loadGradleProperties(rootBuildId, buildDir, false)
        controller.loadGradleProperties(rootProjectIdentity, projectDir)
        def properties = controller.getGradleProperties(rootProjectIdentity)

        then:
        properties.find("sharedProp") == "projectValue" // project overrides build
        properties.find("buildOnlyProp") == "buildOnlyValue" // inherited from build
        properties.find("projectOnlyProp") == "projectOnlyValue" // project only
        properties.getProperties() == [sharedProp: 'projectValue', buildOnlyProp: 'buildOnlyValue', projectOnlyProp: 'projectOnlyValue']
    }

    def "different project identities maintain separate project properties"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def subProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.path(":sub"), Path.path(":sub"), "sub")
        def buildDir = new File("build")
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def buildProperties = Mock(MutableGradleProperties)

        // Load build properties first (once for the build)
        1 * gradlePropertiesLoader.loadGradleProperties(buildDir) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        1 * buildProperties.updateOverrideProperties([:])

        def rootProperties = controller.getGradleProperties(rootProjectIdentity)
        def subProperties = controller.getGradleProperties(subProjectIdentity)

        when:
        controller.loadGradleProperties(rootBuildId, buildDir, false) // Load build properties first

        then:
        "properties are separate instances"
        !rootProperties.is(subProperties)
    }

    def "project properties handle missing gradle.properties file gracefully"() {
        given:
        def rootProjectIdentity = new ProjectIdentity(DefaultBuildIdentifier.ROOT, Path.ROOT, Path.ROOT, "root")
        def projectDir = new File("project")
        def buildDir = new File("build")
        def controller = new DefaultGradlePropertiesController(environment, gradlePropertiesLoader, systemPropertiesInstaller, projectPropertiesLoader)
        def buildProperties = Mock(MutableGradleProperties)

        // Load build properties first
        1 * gradlePropertiesLoader.loadGradleProperties(buildDir) >> buildProperties
        1 * projectPropertiesLoader.loadProjectProperties() >> [:]
        1 * buildProperties.updateOverrideProperties([:])
        _ * buildProperties.mergeProperties([:]) >> [:]

        1 * environment.propertiesFile(new File(projectDir, Project.GRADLE_PROPERTIES)) >> null

        when:
        controller.loadGradleProperties(rootBuildId, buildDir, false) // Load build properties first
        controller.loadGradleProperties(rootProjectIdentity, projectDir)
        def properties = controller.getGradleProperties(rootProjectIdentity)

        then:
        properties.getProperties() == [:]
        properties.find("anyProp") == null
    }

    private static File propertiesFileFromDir(File dir) {
        new File(dir, Project.GRADLE_PROPERTIES)
    }
}
