plugins {
    java
}

group = "it.patric"
version = (findProperty("pluginVersion") as String?) ?: "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val defaultCommonLibJar = "../minecraft-common-lib/build/libs/minecraft-common-lib-2.1.0.jar"
val commonLibJarPath = providers.gradleProperty("commonLibJar").orElse(defaultCommonLibJar)
val commonLibJar = file(commonLibJarPath.get())

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(files(commonLibJar))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(mapOf("version" to project.version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    dependsOn("verifyCommonLibJar")
}

tasks.register("verifyCommonLibJar") {
    doLast {
        if (!commonLibJar.exists()) {
            throw GradleException(
                "Common-lib jar non trovato: ${commonLibJar.absolutePath}. " +
                    "Passa -PcommonLibJar=/path/minecraft-common-lib-2.1.0.jar"
            )
        }
    }
}
