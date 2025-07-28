/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.cc.impl

import groovy.lang.MissingMethodException
import groovy.lang.MissingPropertyException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.metaobject.DynamicObject
import java.util.Locale


internal
class CrossProjectModelAccessTrackingParentDynamicObject(
    private val ownerProject: ProjectInternal,
    private val delegate: DynamicObject,
    private val referrerProject: ProjectInternal,
    private val problems: ProblemsListener,
    private val coupledProjectsListener: CoupledProjectsListener,
    private val problemFactory: ProblemFactory,
    private val dynamicCallProblemReporting: DynamicCallProblemReporting
) : DynamicObject {
    override fun hasMethod(name: String, vararg arguments: Any?): Boolean {
        val result = delegate.hasMethod(name, *arguments)
        if (result) {
            // Only report access if the method exists on the delegate.
            onAccess(MemberKind.METHOD, name)
        }
        return result
    }

    override fun tryInvokeMethod(name: String, vararg arguments: Any?): DynamicInvokeResult {
        return withAccessReporting(name, MemberKind.METHOD) {
            delegate.tryInvokeMethod(name, *arguments)
        }
    }

    override fun hasProperty(name: String): Boolean {
        val result = delegate.hasProperty(name)
        if (result) {
            // Only report access if the property exists on the delegate.
            onAccess(MemberKind.PROPERTY, name)
        }
        return result
    }

    override fun tryGetProperty(name: String): DynamicInvokeResult {
        return withAccessReporting(name, MemberKind.PROPERTY) {
            delegate.tryGetProperty(name)
        }
    }

    override fun trySetProperty(name: String, value: Any?): DynamicInvokeResult {
        return withAccessReporting(name, MemberKind.PROPERTY) {
            delegate.trySetProperty(name, value)
        }
    }

    override fun trySetPropertyWithoutInstrumentation(name: String, value: Any?): DynamicInvokeResult {
        return withAccessReporting(name, MemberKind.PROPERTY) {
            delegate.trySetPropertyWithoutInstrumentation(name, value)
        }
    }

    override fun getProperties(): MutableMap<String, *> {
        onAccess(MemberKind.PROPERTY, null)
        return delegate.properties
    }

    override fun getMissingProperty(name: String): MissingPropertyException {
        // Intentionally do nothing, a missing property is not a dynamic access.
        return delegate.getMissingProperty(name)
    }

    override fun setMissingProperty(name: String): MissingPropertyException {
        // Intentionally do nothing, a missing property is not a dynamic access.
        return delegate.setMissingProperty(name)
    }

    override fun methodMissingException(name: String, vararg params: Any?): MissingMethodException {
        // Intentionally do nothing, a missing method is not a dynamic access.
        return delegate.methodMissingException(name, *params)
    }

    override fun getProperty(name: String): Any? {
        return withLegacyAccessReporting(name, MemberKind.PROPERTY) {
            delegate.getProperty(name)
        }
    }

    override fun setProperty(name: String, value: Any?) {
        withLegacyAccessReporting(name, MemberKind.PROPERTY) {
            delegate.setProperty(name, value)
        }
    }

    override fun invokeMethod(name: String, vararg arguments: Any?): Any? {
        return withLegacyAccessReporting(name, MemberKind.METHOD) {
            delegate.invokeMethod(name, *arguments)
        }
    }

    private
    enum class MemberKind {
        PROPERTY, METHOD
    }

    private fun <T : Any?> withLegacyAccessReporting(name: String, memberKind: MemberKind, tryCode: () -> T): T {
        val key = Any()
        dynamicCallProblemReporting.enterDynamicCall(key)
        val result = try {
            tryCode()
        } catch (t: Throwable) {
            rethrowIfMissingException(memberKind, t)
            try {
                // Assume this means that the thing exists, and threw an exception when accessed.
                onAccess(memberKind, name)
            } catch (accessError: Throwable) {
                // If there is an access error, we primarily want to report the original exception,
                // but leave the access error as a suppressed exception.
                t.addSuppressed(accessError)
            }
            throw t
        } finally {
            dynamicCallProblemReporting.leaveDynamicCall(key)
        }
        // Only report access if the thing exists on the delegate.
        onAccess(memberKind, name)
        return result
    }

    private fun rethrowIfMissingException(memberKind: MemberKind, t: Throwable) {
        when (memberKind) {
            MemberKind.METHOD -> if (t is MissingMethodException) {
                throw t
            }

            MemberKind.PROPERTY -> if (t is MissingPropertyException) {
                throw t
            }
        }
    }

    private fun withAccessReporting(name: String, memberKind: MemberKind, tryCode: () -> DynamicInvokeResult): DynamicInvokeResult {
        val key = Any()
        dynamicCallProblemReporting.enterDynamicCall(key)
        val result = try {
            tryCode()
        } catch (t: Throwable) {
            try {
                // Assume this means that the thing exists, and threw an exception when accessed.
                onAccess(memberKind, name)
            } catch (accessError: Throwable) {
                // If there is an access error, we primarily want to report the original exception,
                // but leave the access error as a suppressed exception.
                t.addSuppressed(accessError)
            }
            throw t
        } finally {
            dynamicCallProblemReporting.leaveDynamicCall(key)
        }
        if (result.isFound) {
            // Only report access if the thing exists on the delegate.
            onAccess(memberKind, name)
        }
        return result
    }

    private
    fun onAccess(memberKind: MemberKind, memberName: String?) {
        coupledProjectsListener.onProjectReference(referrerProject.owner, ownerProject.owner)
        maybeReportProjectIsolationViolation(memberKind, memberName)
    }

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    private
    fun maybeReportProjectIsolationViolation(memberKind: MemberKind, memberName: String?) {
        if (dynamicCallProblemReporting.unreportedProblemInCurrentCall(PROBLEM_KEY)) {
            val problem = problemFactory.problem {
                text("Project ")
                reference(referrerProject.identityPath.toString())
                text(" cannot dynamically look up a ")
                text(memberKind.name.lowercase(Locale.ENGLISH))
                text(" in the parent project ")
                reference(ownerProject.identityPath.toString())
            }
                .mapLocation { location ->
                    when (memberKind) {
                        MemberKind.PROPERTY -> {
                            if (memberName != null)
                                PropertyTrace.Property(PropertyKind.PropertyUsage, memberName, PropertyTrace.Project(referrerProject.path, location))
                            else location
                        }

                        // method lookup is more clear from the stack trace, so keep the minimal trace pointing to the location:
                        MemberKind.METHOD -> location
                    }
                }
                .exception()
                .build()
            problems.onProblem(problem)
        }
    }

    companion object {
        val PROBLEM_KEY = Any()
    }
}
