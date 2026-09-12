plugins {
// Uncomment if you are using IntelliJ.
//  idea
    java
    id("com.azuredoom.hytale-tools") version "1.+"
}


tasks.withType<Javadoc>().configureEach {
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions).addStringOption("Xdoclint:-missing", "-quiet")
}

group = project.property("group").toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(property("java_version").toString().toInt()))
}

hytaleTools {
    javaVersion = property("java_version").toString().toInt()
    hytaleVersion = property("hytale_version").toString()
    manifestServerVersion = property("manifestServerVersion").toString()
    manifestGroup = property("manifest_group").toString()
    modId = property("mod_id").toString()
    modDescription = property("mod_description").toString()
    modUrl = property("mod_url").toString()
    mainClass = property("main_class").toString()
    modCredits = property("mod_author").toString()
    manifestDependencies = property("manifest_dependencies").toString()
    manifestOptionalDependencies = property("manifest_opt_dependencies").toString()
    curseforgeId = property("curseforgeID").toString()
    disabledByDefault = property("disabled_by_default").toString().toBoolean()
    includesPack = property("includes_pack").toString().toBoolean()
    patchline = property("patchline").toString()
    injectServerJavadocsIntoSources = property("injectServerJavadocsIntoSources").toString().toBoolean()
    generateAssetsBinary = property("generateAssetsBinary").toString().toBoolean()
    hytaleHomeOverride = property("hytaleHomeOverride").toString()
}

repositories {
    mavenCentral()
}

tasks.named<Jar>("jar") {
    archiveBaseName.set(project.property("mod_name").toString())
    archiveVersion.set(project.property("version").toString())
}

// Uncomment if you are using IntelliJ.
// idea {
//     module {
//         isDownloadSources = true
//         isDownloadJavadoc = true
//     }
// }

// Parser and runtime are plain Java; test them without a server.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Tests that touch classes implementing Hytale interfaces (the JSON dialogue asset) need the server on the test classpath.
    testCompileOnly("com.hypixel.hytale:Server:0.+")
    testRuntimeOnly("com.hypixel.hytale:Server:0.+")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging { events("failed"); showStandardStreams = false }
}

// Runtime data written into the plugin folder must not be packaged into the jar.
tasks.named<ProcessResources>("processResources") {
    exclude("data/**", "lowtalk.json", "*.bak", "*.tmp")
    // Ship the example dialogues so the plugin can copy them into a fresh server.
    from("examples") { into("lowtalk-examples") }
    // The same examples inside LowTalk's own asset pack, so they show in the Asset Editor as reference material and
    // can be copied into a creator's pack with "Copy Asset". They are unbound there (npc: none) so shipping them
    // never changes any NPC; the working, role-bound copies are the ones in the plugin's dialogues folder.
    from("examples") {
        exclude("tests/**")
        into("Server/LowTalk/Dialogues/Examples")
        rename { name -> "Example_" + name }
        // The JSON Npc array may span several lines (the Node Editor writes it that way); drop the whole array,
        // not just its first line, or the file is left unparseable and the server refuses to boot.
        var skippingNpcArray = false
        filter { line ->
            when {
                skippingNpcArray -> {
                    if (line.trim().startsWith("]")) skippingNpcArray = false
                    null
                }
                line.startsWith("npc: ") -> "npc: none   # shipped example: copy this asset into your pack and set a role id or @tag here"
                line.trim().startsWith("\"Npc\":") -> {
                    val indent = line.substring(0, line.indexOf("\"Npc\""))
                    if (!line.contains("]")) skippingNpcArray = true
                    indent + "\"Npc\": [\"none\"],"
                }
                else -> line
            }
        }
    }
}

// Every shipped example must still be valid JSON with Npc: ["none"] after the filter above; a broken asset inside a
// mod jar stops the whole server from booting, so this runs before the jar is built.
val verifyShippedExamples by tasks.registering {
    group = "lowtalk"
    description = "Parses the example dialogues as packaged into the asset pack."
    dependsOn(tasks.processResources)
    val examples = layout.buildDirectory.dir("resources/main/Server/LowTalk/Dialogues/Examples")
    doLast {
        val dir = examples.get().asFile
        require(dir.isDirectory) { "No shipped examples at $dir" }
        var checked = 0
        dir.listFiles { f -> f.name.endsWith(".json") }!!.forEach { f ->
            val parsed = groovy.json.JsonSlurper().parse(f) as Map<*, *>
            require(parsed["Npc"] == listOf("none")) { "$f: Npc should be [\"none\"] after packaging, got " + parsed["Npc"] }
            checked++
        }
        dir.listFiles { f -> f.name.endsWith(".talk") }!!.forEach { f ->
            require(f.readLines().any { it.startsWith("npc: none") }) { "$f: expected an npc: none line after packaging" }
            checked++
        }
        println("verified $checked shipped example(s)")
    }
}
tasks.named("jar") { dependsOn(verifyShippedExamples) }
tasks.named("check") { dependsOn(verifyShippedExamples) }

// Validate .talk files from the command line without a server:
//   ./gradlew validate --args="examples"
tasks.register<JavaExec>("validate") {
    group = "lowtalk"
    description = "Parses and validates .talk dialogue files."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.chromecide.lowtalk.parser.ValidateMain")
}

// ---- one commit, one jar per Hytale patchline ------------------------------------------------------------------
// `build` targets the release line from gradle.properties. `buildPreRelease` re-runs the build with the pre-release
// line's properties (patchline, server version, manifest range, assets) and a version carrying the game version as
// build metadata, so both jars can sit in one GitHub release. `buildAll` does both and collects them in dist/.
val distDir = layout.buildDirectory.dir("dist")

tasks.register<Copy>("collectRelease") {
    group = "distribution"
    description = "Copies the release-line jar into build/dist."
    dependsOn("build")
    from(tasks.named<Jar>("jar").map { it.archiveFile })
    into(distDir)
}

tasks.register<Exec>("buildPreRelease") {
    group = "distribution"
    description = "Builds the jar for the Hytale pre-release line into build/dist."
    val preVersion = project.property("prerelease_hytale_version").toString()
    val modVersion = project.property("version").toString()
    workingDir = projectDir
    commandLine(
        if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "./gradlew",
        "jar", "--console=plain",
        "-Ppatchline=pre-release",
        "-Phytale_version=$preVersion",
        "-Pserver_version=$preVersion",
        "-PmanifestServerVersion=${project.property("prerelease_manifestServerVersion")}",
        "-PhytaleHomeOverride=${project.property("prerelease_hytaleHomeOverride")}",
        "-Pversion=$modVersion+hytale.$preVersion",
        "-PjarDir=${distDir.get().asFile.absolutePath}"
    )
    doFirst { distDir.get().asFile.mkdirs() }
}

tasks.register("buildAll") {
    group = "distribution"
    description = "Builds the release-line and pre-release jars into build/dist."
    dependsOn("collectRelease")
    finalizedBy("buildPreRelease")
}

// A variant build writes its jar somewhere the normal build will not overwrite.
if (project.hasProperty("jarDir")) {
    tasks.named<Jar>("jar") {
        destinationDirectory.set(file(project.property("jarDir").toString()))
    }
}
