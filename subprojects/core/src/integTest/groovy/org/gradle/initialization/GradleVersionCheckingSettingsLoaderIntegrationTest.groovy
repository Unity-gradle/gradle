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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.GradleVersion
import org.junit.Rule

class GradleVersionCheckingSettingsLoaderIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    HttpServer httpServer = new HttpServer()

    def setup() {
        httpServer.start()
    }

    def "gradle version does not match properties file"() {
        File json = file("versions.json")
        File wrapperProperties = file("gradle/wrapper/gradle-wrapper.properties")
        File gradleProperties = file("gradle.properties")

        gradleProperties << """
            systemProp.org.gradle.internal.services.base.url=${httpServer.uri}
            """

        wrapperProperties << """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            distributionUrl=https://services.gradle.org/distributions/gradle-9.0.0-rc-3-bin.zip
            distributionSha256Sum=2db9560bb68c367a265b10516c856c840f9bed8d
        """

        json << """
        [ {
          "version" : "9.0.0-rc-3",
          "buildTime" : "20250717124800+0000",
          "commitId" : "2db9560bb68c367a265b10516c856c840f9bed8d",
          "current" : false,
          "snapshot" : false,
          "nightly" : false,
          "releaseNightly" : false,
          "activeRc" : true,
          "rcFor" : "9.0.0",
          "milestoneFor" : "",
          "broken" : false,
          "downloadUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-rc-3-bin.zip",
          "checksumUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-rc-3-bin.zip.sha256",
          "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-9.0.0-rc-3-wrapper.jar.sha256"
        }, {
          "version" : "9.0.0-20250717015937+0000",
          "buildTime" : "20250717015937+0000",
          "commitId" : "2db9560bb68c367a265b10516c856c840f9bed8d",
          "current" : false,
          "snapshot" : true,
          "nightly" : false,
          "releaseNightly" : true,
          "activeRc" : false,
          "rcFor" : "",
          "milestoneFor" : "",
          "broken" : false,
          "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250717015937+0000-bin.zip",
          "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250717015937+0000-bin.zip.sha256",
          "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250717015937+0000-wrapper.jar.sha256"
}]
        """

        httpServer.expectGet("/versions/all", json)

        when:
        succeeds("help")

        then:
        outputContains("The Gradle version specified in the wrapper configuration (Gradle 9.0.0-rc-3) does not match the current Gradle version (${GradleVersion.current()}).")
    }

    def "gradle version does match properties file"() {
        File json = file("versions.json")
        File wrapperProperties = file("gradle/wrapper/gradle-wrapper.properties")
        File gradleProperties = file("gradle.properties")

        gradleProperties << """
            systemProp.org.gradle.internal.services.base.url=${httpServer.uri}
            """

        wrapperProperties << """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            distributionUrl=https://services.gradle.org/distributions/gradle-${GradleVersion.current().getVersion()}-bin.zip
            distributionSha256Sum=2db9560bb68c367a265b10516c856c840f9bed8d
        """

        json << """
        [ {
          "version" : "${GradleVersion.current().getVersion()}",
          "buildTime" : "20250717124800+0000",
          "commitId" : "2db9560bb68c367a265b10516c856c840f9bed8d",
          "current" : false,
          "snapshot" : false,
          "nightly" : false,
          "releaseNightly" : false,
          "activeRc" : true,
          "rcFor" : "9.0.0",
          "milestoneFor" : "",
          "broken" : false,
          "downloadUrl" : "https://services.gradle.org/distributions/gradle-${GradleVersion.current().getVersion()}-bin.zip",
          "checksumUrl" : "https://services.gradle.org/distributions/gradle-${GradleVersion.current().getVersion()}-bin.zip.sha256",
          "wrapperChecksumUrl" : "https://services.gradle.org/distributions/gradle-${GradleVersion.current().getVersion()}-wrapper.jar.sha256"
        }, {
          "version" : "9.0.0-20250717015937+0000",
          "buildTime" : "20250717015937+0000",
          "commitId" : "2db9560bb68c367a265b10516c856c840f9bed8d",
          "current" : false,
          "snapshot" : true,
          "nightly" : false,
          "releaseNightly" : true,
          "activeRc" : false,
          "rcFor" : "",
          "milestoneFor" : "",
          "broken" : false,
          "downloadUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250717015937+0000-bin.zip",
          "checksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250717015937+0000-bin.zip.sha256",
          "wrapperChecksumUrl" : "https://services.gradle.org/distributions-snapshots/gradle-9.0.0-20250717015937+0000-wrapper.jar.sha256"
}]
        """

        httpServer.expectGet("/versions/all", json)

        when:
        args("--info")
        succeeds("help")

        then:
        outputDoesNotContain("The Gradle version specified in the wrapper configuration (Gradle 9.0.0-rc-3) does not match the current Gradle version (${GradleVersion.current()}).")
        outputDoesNotContain("A gradle-wrapper.properties was found, however")
    }
}
