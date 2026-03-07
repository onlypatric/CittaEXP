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
val defaultItemsAdderAdapterJar = "../minecraft-common-lib/adapter-itemsadder/build/libs/adapter-itemsadder-3.0.0.jar"
val itemsAdderAdapterJarPath = providers.gradleProperty("itemsAdderAdapterJar").orElse(defaultItemsAdderAdapterJar)
val itemsAdderAdapterJar = file(itemsAdderAdapterJarPath.get())
val defaultVaultApiJar = "../ShopperEXP/.bench/downloads/Vault.jar"
val vaultApiJarPath = providers.gradleProperty("vaultApiJar").orElse(defaultVaultApiJar)
val vaultApiJar = file(vaultApiJarPath.get())
val defaultHuskClaimsApiJar = "../minecraft-common-lib/adapter-huskclaims/build/libs/adapter-huskclaims-3.0.0.jar"
val huskClaimsApiJarPath = providers.gradleProperty("huskClaimsApiJar").orElse(defaultHuskClaimsApiJar)
val huskClaimsApiJar = file(huskClaimsApiJarPath.get())
val defaultHuskClaimsAdapterJar = "../minecraft-common-lib/adapter-huskclaims/build/libs/adapter-huskclaims-3.0.0.jar"
val huskClaimsAdapterJarPath = providers.gradleProperty("huskClaimsAdapterJar").orElse(defaultHuskClaimsAdapterJar)
val huskClaimsAdapterJar = file(huskClaimsAdapterJarPath.get())
val defaultClassificheApiJar = "../ClassificheExp/build/libs/ClassificheExp-0.2.0.jar"
val classificheApiJarPath = providers.gradleProperty("classificheApiJar").orElse(defaultClassificheApiJar)
val classificheApiJar = file(classificheApiJarPath.get())

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(files(commonLibJar))
    implementation(files(itemsAdderAdapterJar))
    implementation(files(huskClaimsAdapterJar))
    compileOnly(files(vaultApiJar))
    compileOnly(files(huskClaimsApiJar))
    compileOnly(files(classificheApiJar))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation(files(commonLibJar))
    testImplementation(files(vaultApiJar))
    testImplementation(files(huskClaimsApiJar))
    testImplementation(files(classificheApiJar))
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
    dependsOn(
        "verifyCommonLibJar",
        "verifyItemsAdderAdapterJar",
        "verifyVaultApiJar",
        "verifyHuskClaimsApiJar",
        "verifyHuskClaimsAdapterJar",
        "verifyClassificheApiJar"
    )
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

tasks.register("verifyVaultApiJar") {
    doLast {
        if (!vaultApiJar.exists()) {
            throw GradleException(
                "Vault API jar non trovato: ${vaultApiJar.absolutePath}. " +
                    "Passa -PvaultApiJar=/path/Vault.jar"
            )
        }
    }
}

tasks.register("verifyHuskClaimsApiJar") {
    doLast {
        if (!huskClaimsApiJar.exists()) {
            throw GradleException(
                "HuskClaims API jar non trovato: ${huskClaimsApiJar.absolutePath}. " +
                    "Passa -PhuskClaimsApiJar=/path/HuskClaims.jar"
            )
        }
    }
}

tasks.register("verifyHuskClaimsAdapterJar") {
    doLast {
        if (!huskClaimsAdapterJar.exists()) {
            throw GradleException(
                "HuskClaims adapter jar non trovato: ${huskClaimsAdapterJar.absolutePath}. " +
                    "Passa -PhuskClaimsAdapterJar=/path/adapter-huskclaims-3.0.0.jar"
            )
        }
    }
}

tasks.register("verifyClassificheApiJar") {
    doLast {
        if (!classificheApiJar.exists()) {
            throw GradleException(
                "ClassificheEXP API jar non trovato: ${classificheApiJar.absolutePath}. " +
                    "Passa -PclassificheApiJar=/path/ClassificheExp.jar"
            )
        }
    }
}
