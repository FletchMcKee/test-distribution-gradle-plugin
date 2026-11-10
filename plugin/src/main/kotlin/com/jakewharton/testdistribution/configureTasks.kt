package com.jakewharton.testdistribution

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

internal fun Project.configureTasks(
	base: BasePluginExtension,
	naming: Naming,
	testClasses: FileCollection,
	testDependencies: FileCollection,
	mainJar: FileCollection = files(),
	onCreateStartScripts: (CreateStartScripts) -> Unit,
) = with(naming) {
	val testJarProvider = tasks.register("jar$nameUpper", Jar::class.java) { jar ->
		jar.archiveClassifier.set(archiveClassifier)
		archiveAppendix?.let { jar.archiveAppendix.set(it) }
		jar.from(testClasses)
	}

	val testScriptsProvider = tasks.register("scripts$nameUpper", CreateStartScripts::class.java) {
		it.outputDir = layout.buildDirectory.dir("scripts/$name").get().asFile
		it.applicationName = base.archivesName.get() + "-$archiveClassifier"

		it.classpath = objects.fileCollection()
			.from(testJarProvider, mainJar, testDependencies)

		onCreateStartScripts(it)
	}

	val installProvider = tasks.register("install${nameUpper}Distribution", Sync::class.java) {
		it.group = "distribution"
		it.description = "Installs $name as a distribution as-is."
		it.destinationDir = layout.buildDirectory.dir("install/$name").get().asFile

		it.into("bin") { spec ->
			spec.from(testScriptsProvider)
		}
		it.into("lib") { spec ->
			spec.from(testJarProvider, mainJar, testDependencies)
		}
	}

	tasks.register("zip${nameUpper}Distribution", Zip::class.java) { zip ->
		zip.group = "distribution"
		zip.description = "Bundles $name as a distribution."

		zip.from(installProvider)
		zip.destinationDirectory.set(layout.buildDirectory.dir("dist"))
		archiveAppendix?.let { zip.archiveAppendix.set(it) }
		zip.archiveClassifier.set(archiveClassifier)
	}
}

internal data class Naming(
	val name: String,
	val archiveClassifier: String = name,
	val archiveAppendix: String? = null,
) {
	val nameUpper: String = name.replaceFirstChar(Char::uppercase)
}
