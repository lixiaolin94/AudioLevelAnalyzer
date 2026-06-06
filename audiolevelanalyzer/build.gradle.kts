import org.gradle.api.tasks.compile.JavaCompile

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "work.xiaolin.audiolevelanalyzer"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-options")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.lixiaolin94"
            artifactId = "AudioLevelAnalyzer"
            version = "v0.1.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Audio Level Analyzer")
                description.set("Data-only Android audio level analyzer core for PCM analysis.")
                url.set("https://github.com/lixiaolin94/AudioLevelAnalyzer")
                licenses {
                    license {
                        name.set("Proprietary")
                        url.set("https://github.com/lixiaolin94/AudioLevelAnalyzer/blob/main/LICENSE")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("lixiaolin94")
                        name.set("lixiaolin94")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/lixiaolin94/AudioLevelAnalyzer.git")
                    developerConnection.set("scm:git:https://github.com/lixiaolin94/AudioLevelAnalyzer.git")
                    url.set("https://github.com/lixiaolin94/AudioLevelAnalyzer")
                }
            }
        }
    }
}
