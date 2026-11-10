package com.jakewharton.testdistribution

import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.testing.base.TestingExtension

@Suppress("UnstableApiUsage")
internal fun Project.configureJavaPlugin(gradleSupport: GradleSupport) {
	val base = extensions.getByType(BasePluginExtension::class.java)
	val testing = extensions.getByType(TestingExtension::class.java)

	testing.suites.withType(JvmTestSuite::class.java).configureEach { suite ->
		val builtIn = suite.name == JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME
		val mainJar = objects.fileCollection()
			.apply { if (builtIn) from(tasks.named(JAR_TASK_NAME)) }

		val testClasses = files(suite.sources.output)
		val testDependencies = files(configurations.named(suite.sources.runtimeClasspathConfigurationName))

		suite.targets.configureEach { target ->
			val testFrameworkProvider = target.testTask.toTestFramework()
			configureTasks(
				base = base,
				testClasses = testClasses,
				testDependencies = testDependencies,
				mainJar = mainJar,
				naming = Naming(suite.name)
			) { scripts ->
				scripts.mainClass.set(
					computeMain(gradleSupport, testFrameworkProvider, testClasses, testDependencies)
				)
			}
		}
	}
}
