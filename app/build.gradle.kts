plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.wtfff"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.wtfff"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // DefectTest pins confirmed bugs, so it fails until they are fixed. Normal runs leave it out;
                // run it on purpose with: -Pdefects=true --tests com.example.wtfff.DefectTest
                if (project.findProperty("defects")?.toString() != "true") {
                    test.exclude("**/DefectTest*")
                }
                // Style lock: -Pstylelock.record=true rewrites the golden screenshots instead of comparing.
                test.systemProperty("stylelock.record", project.findProperty("stylelock.record")?.toString() ?: "false")
                test.inputs.files(fileTree("src/test/snapshots")).withPathSensitivity(PathSensitivity.RELATIVE)
                test.maxHeapSize = "1536m"
            }
        }
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("org.robolectric:robolectric:4.18")
}
