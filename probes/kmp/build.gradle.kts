plugins { kotlin("multiplatform") version "2.2.21" }

layout.buildDirectory.set(file("../../tmp/kmp-probe-build"))
val prepareFormatProbe by tasks.registering(Exec::class) {
    commandLine("ruby", "../../scripts/ipados/prepare-format-probe.rb")
    inputs.files(fileTree("../../app/src/main/java/com/xnotes/format") { include("JsonPull.kt", "JsonWrite.kt", "RawSamples.kt") })
    inputs.file("../../scripts/ipados/prepare-format-probe.rb")
    outputs.dir("../../tmp/kmp-format-probe")
    outputs.dir("../../tmp/kmp-format-oracle")
}
tasks.configureEach {
    if (name.startsWith("compileKotlin")) dependsOn(prepareFormatProbe)
}
kotlin {
    jvm()
    macosArm64 { binaries.framework { baseName = "XNotesCoreProbe" } }
    iosArm64 { binaries.framework { baseName = "XNotesCoreProbe" } }
    iosSimulatorArm64 { binaries.framework { baseName = "XNotesCoreProbe" } }
    sourceSets {
        commonMain {
            kotlin.srcDir("../../app/src/main/java/com/xnotes/core/geometry")
            kotlin.srcDir("../../tmp/kmp-format-probe")
            kotlin.include("Pt.kt", "com/xnotes/probe/**", "com/xnotes/format/JsonPull.kt", "com/xnotes/format/JsonWrite.kt", "com/xnotes/format/RawSamples.kt", "com/xnotes/format/ProbeStreams.kt")
        }
        jvmTest { kotlin.srcDir("../../tmp/kmp-format-oracle") }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
