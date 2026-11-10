package com.jakewharton.testdistribution

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.ScopedArtifacts
import com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.plugins.BasePluginExtension

context(gradleSupport: GradleSupport)
internal fun Project.configureAndroidPlugin() {
	val agpVersion = AgpVersion.parse(ANDROID_GRADLE_PLUGIN_VERSION)
	val agpMinimum = AgpVersion.parse("9.0.0")
	val androidSupport = when {
		agpVersion < agpMinimum -> {
			error("Test distribution plugin requires $agpMinimum or newer. Found $agpVersion")
		}

		agpVersion >= AgpVersion.parse("9.5.0-alpha02") -> AndroidSupport_9_5_alpha02()

		else -> AndroidSupportMinimum()
	}

	val base = extensions.getByType(BasePluginExtension::class.java)

	val androidComponents = extensions.getByType(AndroidComponentsExtension::class.java)
	androidComponents.onVariants { variant ->
		if (variant !is HasHostTests) return@onVariants

		val variantUpperName = variant.name.replaceFirstChar(Char::uppercase)
		for ((testUpperName, hostTest) in variant.hostTests) {
			val testName = testUpperName.replaceFirstChar(Char::lowercase)
			val nameUpper = variantUpperName + testUpperName

			val dummyClassesProvider = tasks.register(
				"internal${nameUpper}Classes",
				ScopedArtifactDummyTask::class.java,
			)
			hostTest.artifacts
				.forScope(ScopedArtifacts.Scope.PROJECT)
				.use(dummyClassesProvider)
				.toGet(
					ScopedArtifact.CLASSES,
					ScopedArtifactDummyTask::jars,
					ScopedArtifactDummyTask::classDirs,
				)

			val testClasses = files(dummyClassesProvider.flatMap(ScopedArtifactDummyTask::classDirs))
			val testJars = objects.fileCollection()
				.from(dummyClassesProvider.flatMap(ScopedArtifactDummyTask::jars))
				.from(
					hostTest.runtimeConfiguration.incoming
						.artifactView { view ->
							view.attributes {
								it.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "android-classes-jar")
							}
						}.files,
				)

			configureTasks(
				base = base,
				testClasses = testClasses,
				testDependencies = testJars,
				naming = Naming(
					name = hostTest.name,
					archiveAppendix = variant.name,
					archiveClassifier = testName,
				),
			) { scripts ->
				androidSupport.withTestProvider(project, hostTest) { testTask ->
					val testFramework = testTask.toTestFramework()
					scripts.mainClass.set(computeMain(testFramework, testClasses, testJars))
				}
			}
		}
	}
}
