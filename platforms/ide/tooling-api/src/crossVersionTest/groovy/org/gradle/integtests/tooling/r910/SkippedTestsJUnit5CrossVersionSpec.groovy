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

package org.gradle.integtests.tooling.r910

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.events.test.internal.DefaultTestSkippedResult
import org.junit.jupiter.api.Test

class SkippedTestsJUnit5CrossVersionSpec extends TestLauncherSpec {
    @Override
    void addDefaultTests() {
        file("src/test/java/org/example/SimpleTests.java") << """
            package org.example;

            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.Disabled;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.ValueSource;

            @DisplayName("a class display name")
            public class SimpleTests {

                @Test
                @DisplayName("and a test display name")
                void test() {
                }

                @Test
                @Disabled
                @DisplayName("a disabled test display name")
                void disabledTest() {
                }

                @Disabled
                @ParameterizedTest
                @ValueSource(strings = {"first", "second"})
                @DisplayName("bar test display name")
                void bar() {
                }
            }
        """
    }

    @Override
    String simpleJavaProject() {
        """
        allprojects{
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")

                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            test {
                useJUnitPlatform()
            }
        }
        """
    }

    @TargetGradleVersion(">=9.1.0")
    def "reports display names of class and method"() {

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test', ['org.example.SimpleTests'])
        }

        then:
        jvmTestEvents {
            task(":test") {
                suite("Gradle Test Run :test") {
                    suite("Gradle Test Executor") {
                        testClass("org.example.SimpleTests") {
                            operationDisplayName "a class display name"
                            testDisplayName "a class display name"
                            test("test()") {
                                operationDisplayName "and a test display name"
                                testDisplayName "and a test display name"
                            }
                            test("disabledTest()") {
                                operationDisplayName "a disabled test display name"
                                testDisplayName "a disabled test display name"
                                events.operation("a disabled test display name").finishEvent.result instanceof DefaultTestSkippedResult
                            }
                            testMethodSuite("bar()") {
                                operationDisplayName "bar test display name"
                                testDisplayName "bar test display name"
                                events.operation("bar test display name").finishEvent.result instanceof DefaultTestSkippedResult
                            }
                        }
                    }
                }
            }
        }
    }
}
