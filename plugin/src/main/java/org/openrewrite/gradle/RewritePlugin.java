/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite.gradle;

import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;

/**
 * Adds the RewriteExtension to the current project and registers tasks per-sourceSet.
 * Only needs to be applied to projects with java sources. No point in applying this to any project that does
 * not have java sources of its own, such as the root project in a multi-project builds.
 */
@SuppressWarnings("unused")
public class RewritePlugin implements Plugin<Project> {
    /*
     Note on compatibility:
     As written this project doesn't use any APIs not present as of Gradle 4.0.
     That predates Gradle supporting Java 11, which came in Gradle 5.0.
     */

    @Override
    public void apply(Project rootProject) {
        // Only apply to the root project
        if(!":".equals(rootProject.getPath())) {
            return;
        }
        DefaultRewriteExtension maybeExtension = rootProject.getExtensions().findByType(DefaultRewriteExtension.class);
        if (maybeExtension == null) {
            maybeExtension = rootProject.getExtensions().create("rewrite", DefaultRewriteExtension.class, rootProject);
        }
        final DefaultRewriteExtension extension = maybeExtension;

        // Rewrite module dependencies put here will be available to all rewrite tasks
        Configuration rewriteConf = rootProject.getConfigurations().maybeCreate("rewrite");

        // We use this method of task creation because it works on old versions of Gradle
        // Don't replace with TaskContainer.register() (introduced in 4.9), or another overload of create() (introduced in 4.7)
        ResolveRewriteDependenciesTask resolveRewriteDependenciesTask = rootProject.getTasks().create("rewriteResolveDependencies", ResolveRewriteDependenciesTask.class)
                .setConfiguration(rewriteConf);

        Task rewriteRun = rootProject.getTasks().create("rewriteRun", RewriteRunTask.class)
                .setExtension(extension)
                .setResolveDependenciesTask(resolveRewriteDependenciesTask);

        Task rewriteDryRun = rootProject.getTasks().create("rewriteDryRun", RewriteDryRunTask.class)
                .setExtension(extension)
                .setResolveDependenciesTask(resolveRewriteDependenciesTask);

        rootProject.getTasks().create("rewriteDiscover", RewriteDiscoverTask.class)
                .setExtension(extension)
                .setResolveDependenciesTask(resolveRewriteDependenciesTask);

        rootProject.getTasks().create("rewriteClearCache", RewriteClearCacheTask.class)
                .setExtension(extension)
                .setResolveDependenciesTask(resolveRewriteDependenciesTask);

        rootProject.allprojects(project -> {

            // DomainObjectCollection.all() accepts a function to be applied to both existing and subsequently added members of the collection
            // Do not replace all() with any form of collection iteration which does not share this important property
            project.getPlugins().all(plugin -> {
                if(plugin instanceof CheckstylePlugin) {
                    // A multi-project build could hypothetically have different checkstyle configuration per-project
                    // In practice all projects tend to have the same configuration
                    CheckstyleExtension checkstyleExtension = project.getExtensions().getByType(CheckstyleExtension.class);
                    extension.checkstyleConfigProvider = checkstyleExtension::getConfigFile;
                    extension.checkstylePropertiesProvider = checkstyleExtension::getConfigProperties;
                }
                if(!(plugin instanceof JavaBasePlugin)) {
                    return;
                }

                //Collect Java metadata for each project (used for Java Provenance)
                //Using the older javaConvention because we need to support older versions of gradle.
                @SuppressWarnings("deprecation")
                JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

                javaConvention.getSourceSets().all(sourceSet -> {
                    // This is intended to ensure that any Groovy/Kotlin/etc. sources are available for type attribution during parsing
                    // This may not be necessary if sourceSet.getCompileClasspath() guarantees that such sources will have been compiled
                    Task compileTask = project.getTasks().getByPath(sourceSet.getCompileJavaTaskName());
                    rewriteRun.dependsOn(compileTask);
                    rewriteDryRun.dependsOn(compileTask);
                });
            });
        });
    }
}
