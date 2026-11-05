plugins { kotlin("multiplatform") version "2.2.21" }

layout.buildDirectory.set(file("../../tmp/kmp-probe-build"))
kotlin {
    jvm()
    iosArm64 { binaries.framework { baseName = "XNotesCoreProbe" } }
    iosSimulatorArm64 { binaries.framework { baseName = "XNotesCoreProbe" } }
    sourceSets {
        commonMain {
            kotlin.srcDir("../../app/src/main/java")
            kotlin.include("com/xnotes/core/geometry/Pt.kt", "com/xnotes/probe/**")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
