plugins {
    java
    id("com.gradleup.shadow") version "9.3.2"
}

group = "it.patric"
version = (findProperty("pluginVersion") as String?) ?: "0.2.0-bootstrap"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

fun pickLatestJar(baseDir: String, prefix: String): String? {
    val dir = file(baseDir)
    if (!dir.exists() || !dir.isDirectory) return null
    return dir.listFiles()
        ?.filter {
            it.isFile &&
                it.extension.equals("jar", ignoreCase = true) &&
                it.name.startsWith(prefix) &&
                !it.name.endsWith("-sources.jar") &&
                !it.name.endsWith("-javadoc.jar") &&
                !it.name.endsWith("-plain.jar")
        }
        ?.sortedByDescending { it.lastModified() }
        ?.firstOrNull()
        ?.path
}

val defaultHuskTownsCommonApiJar = pickLatestJar("../HuskTowns/common/build/libs", "HuskTowns-Common")
    ?: "../HuskTowns/common/build/libs/HuskTowns-Common-3.1.5-e48cdac.jar"
val defaultHuskTownsBukkitApiJar = pickLatestJar("../HuskTowns/bukkit/build/libs", "HuskTowns-Bukkit")
    ?: "../HuskTowns/bukkit/build/libs/HuskTowns-Bukkit-3.1.5-e48cdac.jar"
val paperApiCoordinate = "io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT"

val huskTownsCommonApiJarPath = providers.gradleProperty("huskTownsCommonApiJar").orElse(defaultHuskTownsCommonApiJar)
val huskTownsBukkitApiJarPath = providers.gradleProperty("huskTownsBukkitApiJar").orElse(defaultHuskTownsBukkitApiJar)
val huskTownsCommonApiJar = file(huskTownsCommonApiJarPath.get())
val huskTownsBukkitApiJar = file(huskTownsBukkitApiJarPath.get())
val vaultApiJar = file(
    providers.gradleProperty("vaultApiJar")
        .orElse("../EXTERNAL-LIBS/Vault.jar")
        .get()
)
val itemsAdderApiJar = file(
    providers.gradleProperty("itemsAdderApiJar")
        .orElse("../EXTERNAL-LIBS/ItemsAdder_4.0.16-beta-11.jar")
        .get()
)

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.william278.net/releases/")
}

dependencies {
    compileOnly(paperApiCoordinate)
    compileOnly(files(huskTownsCommonApiJar))
    compileOnly(files(huskTownsBukkitApiJar))
    compileOnly(files(vaultApiJar))
    implementation("net.dv8tion:JDA:5.2.2") {
        exclude(module = "opus-java")
    }
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    if (itemsAdderApiJar.exists()) {
        compileOnly(files(itemsAdderApiJar))
    }
    compileOnly("net.william278.huskclaims:huskclaims-bukkit:1.5.3")
    compileOnly("net.william278.cloplib:cloplib-common:2.0.11")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation(paperApiCoordinate)
    testImplementation("org.junit.jupiter:junit-jupiter")
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
    dependsOn("verifyHuskTownsApiJars")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("testFast") {
    description = "Suite rapida bootstrap"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
}

tasks.register<Test>("testFull") {
    description = "Suite completa bootstrap"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    dependsOn(tasks.testClasses)
    useJUnitPlatform()
    shouldRunAfter(tasks.test)
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

tasks.register("verifyHuskTownsApiJars") {
    doLast {
        if (!huskTownsCommonApiJar.exists()) {
            throw GradleException(
                "HuskTowns Common API jar non trovato: ${huskTownsCommonApiJar.absolutePath}. " +
                    "Compila HuskTowns/common o passa -PhuskTownsCommonApiJar=/path/HuskTowns-Common.jar"
            )
        }
        if (!huskTownsBukkitApiJar.exists()) {
            throw GradleException(
                "HuskTowns Bukkit API jar non trovato: ${huskTownsBukkitApiJar.absolutePath}. " +
                    "Compila HuskTowns/bukkit o passa -PhuskTownsBukkitApiJar=/path/HuskTowns-Bukkit.jar"
            )
        }
        if (!vaultApiJar.exists()) {
            throw GradleException(
                "Vault API jar non trovato: ${vaultApiJar.absolutePath}. " +
                    "Aggiungi ../EXTERNAL-LIBS/Vault.jar o passa -PvaultApiJar=/path/Vault.jar"
            )
        }
    }
}
