/*
 * SPDX-FileCopyrightText: 2021-2024 The Refinery Authors <https://refinery.tools/>
 *
 * SPDX-License-Identifier: EPL-2.0
 */

plugins {
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
	implementation(project(":refinery-generator-web-library"))
	implementation(libs.gson)
	implementation(libs.jetty.server)
	implementation(libs.jetty.servlet)
	implementation(libs.jetty.websocket.api)
	implementation(libs.jetty.websocket.server)
	implementation(libs.jetty.websocket.client)
	implementation(libs.slf4j)
	implementation(libs.xtext.web)
	xtextGenerated(project(":refinery-language", "generatedWebSources"))
	webapp(project(":refinery-frontend", "productionAssets"))
	testImplementation(testFixtures(project(":refinery-language")))
	testImplementation(libs.jetty.websocket.client)
}

