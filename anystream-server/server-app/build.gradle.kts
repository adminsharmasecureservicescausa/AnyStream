import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.johnrengelman.shadow")
}

application {
    applicationName = "anystream"
    mainClass.set("io.ktor.server.netty.EngineMain")
}

val testGenSrcPath = "build/generated-kotlin"

tasks.withType<KotlinCompile>().all {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

distributions.configureEach {
    distributionBaseName.set("anystream-server")
}

tasks.withType<ShadowJar> {
    val clientWeb = projects.anystreamClientWeb.dependencyProject
    dependsOn(clientWeb.tasks.getByName("jsBrowserDistribution"))
    archiveFileName.set("anystream.jar")
    archiveBaseName.set("anystream")
    archiveClassifier.set("anystream")
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
    from(rootProject.file("anystream-client-web/build/distributions")) {
        into("anystream-client-web")
    }
}

kotlin {
    sourceSets["test"].kotlin.srcDirs(testGenSrcPath)
    target {
        compilations.all {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-Xopt-in=kotlin.RequiresOptIn",
                )
            }
        }
        compilations.getByName("test") {
            compileKotlinTask.doFirst {
                file(testGenSrcPath).also { if (!it.exists()) it.mkdirs() }
                val configFile = file("$testGenSrcPath/config.kt")
                if (!configFile.exists()) {
                    val resources = file("src/test/resources").absolutePath
                    configFile.createNewFile()
                    configFile.writeText(buildString {
                        appendLine("package anystream.test")
                        appendLine("const val RESOURCES = \"${resources.replace('\\', '/')}\"")
                    })
                }
            }
        }
    }
}

dependencies {
    implementation(projects.anystreamDataModels)
    implementation(projects.anystreamServer.serverDbModels)
    implementation(projects.anystreamServer.serverMediaImporter)
    implementation(projects.anystreamServer.serverMetadataManager)
    implementation(projects.anystreamServer.serverShared)
    implementation(projects.anystreamServer.serverStreamService)

    implementation(libs.datetime)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    implementation(libs.ktor.serialization)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.metrics)
    implementation(libs.ktor.server.partialContent)
    implementation(libs.ktor.server.defaultHeaders)
    implementation(libs.ktor.server.cachingHeaders)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.server.autoHeadResponse)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.authJwt)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.permissions)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.contentNegotiation)

    implementation(libs.bouncyCastle)

    implementation(libs.logback)

    implementation(libs.stormpot)
    implementation(libs.jdbc.sqlite)
    implementation(libs.jdbi.core)
    implementation(libs.jdbi.sqlite)
    implementation(libs.jdbi.sqlobject)
    implementation(libs.jdbi.kotlin)
    implementation(libs.jdbi.kotlin.sqlobject)

    implementation(libs.kjob.core)
    implementation(libs.kjob.jdbi)

    implementation(libs.jaffree)

    implementation(libs.tmdbapi)

    implementation(libs.qbittorrent.client)
    implementation(libs.torrentSearch)
    testImplementation(libs.ktor.server.tests)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

tasks.getByName<JavaExec>("run") {
    val clientWeb = projects.anystreamClientWeb.dependencyProject
    dependsOn(clientWeb.tasks.getByName("jsBrowserDevelopmentExecutableDistribution"))
    environment(
        "WEB_CLIENT_PATH",
        properties["webClientPath"] ?: environment["WEB_CLIENT_PATH"]
        ?: clientWeb.buildDir.resolve("developmentExecutable").absolutePath
    )
    environment(
        "DATABASE_URL",
        properties["databaseUrl"] ?: environment["DATABASE_URL"]
        ?: "sqlite:${rootDir.resolve("anystream.db")}"
    )
}
