/*
 * Copyright ${year} the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.lang.management.ManagementFactory

val pluginClasspath = RewritePluginTest::class.java
    .getResourceAsStream("/plugin-classpath.txt")!!
    .bufferedReader()
    .readText()
    .split("\n")
    .map(::File)

val pluginVersion = RewritePluginTest::class.java
    .getResourceAsStream("/plugin-version.txt")!!
    .bufferedReader()
    .readText()
    .trim()

val gradleVersion: String? = System.getProperty("org.openrewrite .test.gradleVersion")

interface RewritePluginTest {

    fun runGradle(testDir: File, vararg args: String): BuildResult =
        GradleRunner.create()
            .withDebug(ManagementFactory.getRuntimeMXBean().inputArguments.toString().indexOf("-agentlib:jdwp") > 0)
            .withProjectDir(testDir)
            .apply {
                if (gradleVersion != null) {
                    withGradleVersion(gradleVersion)
                }
            }
            .withArguments(*args)
            .withPluginClasspath(pluginClasspath)
            .forwardOutput()
            .build()

}