/*
 * SPDX-FileCopyrightText: 2021-2024 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
	id("tools.refinery.gradle.java-application")
	id("tools.refinery.gradle.xtext-generated")
}

val webapp: Configuration by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = true
}

dependencies {
	implementation(project(":refinery-generator"))
	implementation(project(":refinery-language"))
	implementation(project(":refinery-language-ide"))
	implementation(project(":refinery-store-reasoning-scope"))
	implementation(libs.gson)
	implementation(libs.jetty.server)
	implementation(libs.jetty.servlet)
	implementation(libs.jetty.websocket.api)
	implementation(libs.jetty.websocket.server)
	implementation(libs.slf4j)
	implementation(libs.xtext.web)
	xtextGenerated(project(":refinery-language", "generatedWebSources"))
	webapp(project(":refinery-frontend", "productionAssets"))
	testImplementation(testFixtures(project(":refinery-language")))
	testImplementation(libs.jetty.websocket.client)
}

application {
	mainClass.set("tools.refinery.language.web.ServerLauncher")
}

tasks {
	jar {
		dependsOn(webapp)
		from(webapp) {
			into("webapp")
		}
	}

	shadowJar {
		dependsOn(webapp)
		from(project.sourceSets.main.map { it.output })
		exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "schema/*",
				".options", ".api_description", "*.profile", "about.*", "about_*.html", "about_files/*",
				"plugin.xml", "systembundle.properties", "profile.list", "META-INF/resources/xtext/**")
		append("plugin.properties")
		from(webapp) {
			into("webapp")
		}
	}

	register<JavaExec>("serve") {
		dependsOn(webapp)
		val mainRuntimeClasspath = sourceSets.main.map { it.runtimeClasspath }
		dependsOn(mainRuntimeClasspath)
		classpath(mainRuntimeClasspath)
		mainClass.set(application.mainClass)
		standardInput = System.`in`
		environment("REFINERY_BASE_RESOURCE", webapp.singleFile)
		group = "run"
		description = "Start a Jetty web server serving the Xtext API and assets."
	}

	register<JavaExec>("serveBackend") {
		val mainRuntimeClasspath = sourceSets.main.map { it.runtimeClasspath }
		dependsOn(mainRuntimeClasspath)
		classpath(mainRuntimeClasspath)
		mainClass.set(application.mainClass)
		standardInput = System.`in`
		group = "run"
		description = "Start a Jetty web server serving the Xtext API without assets."
	}
}
