plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
}

group = "it.patric"
version = (findProperty("pluginVersion") as String?) ?: "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val defaultCommonLibJar = "../minecraft-common-lib/build/libs/minecraft-common-lib-3.0.0.jar"
val commonLibJarPath = providers.gradleProperty("commonLibJar").orElse(defaultCommonLibJar)
val commonLibJar = file(commonLibJarPath.get())
val defaultInvUiAdapterJar = "../minecraft-common-lib/adapter-invui/build/libs/adapter-invui-3.0.0.jar"
val invUiAdapterJarPath = providers.gradleProperty("invUiAdapterJar").orElse(defaultInvUiAdapterJar)
val invUiAdapterJar = file(invUiAdapterJarPath.get())
val defaultItemsAdderAdapterJar = "../minecraft-common-lib/adapter-itemsadder/build/libs/adapter-itemsadder-3.0.0.jar"
val itemsAdderAdapterJarPath = providers.gradleProperty("itemsAdderAdapterJar").orElse(defaultItemsAdderAdapterJar)
val itemsAdderAdapterJar = file(itemsAdderAdapterJarPath.get())

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(files(commonLibJar))
    implementation(files(invUiAdapterJar))
    implementation(files(itemsAdderAdapterJar))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation(files(commonLibJar))
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.106.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    dependsOn("verifyCommonLibJar", "verifyInvUiAdapterJar", "verifyItemsAdderAdapterJar")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.register("verifyCommonLibJar") {
    doLast {
        if (!commonLibJar.exists()) {
            throw GradleException(
                "Common-lib jar non trovato: ${commonLibJar.absolutePath}. " +
                    "Passa -PcommonLibJar=/path/minecraft-common-lib-3.0.0.jar"
            )
        }
    }
}

tasks.register("verifyInvUiAdapterJar") {
    doLast {
        if (!invUiAdapterJar.exists()) {
            throw GradleException(
                "Adapter InvUI jar non trovato: ${invUiAdapterJar.absolutePath}. " +
                    "Passa -PinvUiAdapterJar=/path/adapter-invui-3.0.0.jar"
            )
        }
    }
}

tasks.register("verifyItemsAdderAdapterJar") {
    doLast {
        if (!itemsAdderAdapterJar.exists()) {
            throw GradleException(
                "Adapter ItemsAdder jar non trovato: ${itemsAdderAdapterJar.absolutePath}. " +
                    "Passa -PitemsAdderAdapterJar=/path/adapter-itemsadder-3.0.0.jar"
            )
        }
    }
}
