/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.10/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
	application
	id("tools.refinery.gradle.java-application")
	//java
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    //testImplementation(libs.junit.jupiter)

    //testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
	//implementation("org.eclipse.jetty.websocket:jetty-websocket-jetty-server:12.0.15-SNAPSHOT")
	implementation(project(":refinery-generator"))
	implementation(project(":refinery-language-web"))
	implementation(libs.gson)
	implementation(libs.jetty.server)
	implementation(libs.jetty.servlet)
	implementation(libs.jetty.websocket.api)
	implementation(libs.jetty.websocket.server)
	implementation(libs.jakarta.websocket)
	implementation(libs.jetty.ee10.websocket)
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    // Define the main class for the application.
    mainClass.set("tools.refinery.generator.server.ServerLauncher")
}

