import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

tasks.withType<KotlinCompile>().all {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-Xopt-in=kotlinx.coroutines.FlowPreview",
                    "-Xopt-in=kotlin.time.ExperimentalTime",
                    "-Xopt-in=kotlin.RequiresOptIn",
                )
            }
        }
    }
}

dependencies {
    implementation(projects.anystreamDataModels)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    implementation(libs.ktor.client.core)

    implementation(libs.bouncyCastle)

    implementation(libs.logback)

    implementation(libs.jdbi.core)

    implementation(libs.jaffree)

    implementation(libs.tmdbapi)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
