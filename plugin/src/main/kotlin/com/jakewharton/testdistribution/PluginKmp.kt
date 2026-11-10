package com.jakewharton.testdistribution

import java.io.File
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

context(gradleSupport: GradleSupport)
internal fun Project.configureKotlinMultiplatformPlugin() {
	val base = project.extensions.getByType(BasePluginExtension::class.java)
	val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
	kotlin.targets.withType(KotlinJvmTarget::class.java).configureEach { target ->
		val mainJarProvider = project.tasks.named(target.artifactsTaskName)

		target.testRuns.configureEach { testRun ->
			val name = target.name + testRun.name.replaceFirstChar(Char::uppercase)
			val testTask = testRun.executionTask
			val testFramework = testTask.toTestFramework()
			val testClasses = files(testTask.flatMap { it.testClassesDirs.elements })
			val testDependencies = files(testTask.flatMap { it.classpath.filter(File::isFile).elements })

			configureTasks(
				base = base,
				testClasses = testClasses,
				testDependencies = testDependencies,
				mainJar = files(mainJarProvider),
				naming = Naming(
					name = name,
					archiveClassifier = testRun.name,
					archiveAppendix = target.name,
				),
			) { scripts ->
				scripts.mainClass.set(
					computeMain(testFramework, testClasses, testDependencies),
				)
			}
		}
	}
}
