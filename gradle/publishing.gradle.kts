// Shared publishing config for library modules. Applied from each
// publishable module's build.gradle.kts (`:llm`, `:llm-catalog-qwen`,
// `:llm-catalog-gemma`).
//
// Outputs:
//   - mavenLocal (~/.m2/repository) — always available, used by
//     `./gradlew publishToMavenLocal`
//   - GitHub Packages — enabled when GITHUB_USER + GITHUB_TOKEN env vars
//     (or `gpr.user` / `gpr.token` Gradle properties) are present
//
// Maven Central / Sonatype is intentionally not wired up; that needs
// signing keys and an OSSRH account. Add `signing` + the
// `io.github.gradle-nexus.publish-plugin` when ready.

plugins.apply("maven-publish")

group = "io.github.fadizg.kmpai"
version = providers.gradleProperty("kmp-ai.version").orNull ?: "0.1.0-SNAPSHOT"

extensions.configure<PublishingExtension> {
    repositories {
        val ghUser = providers.gradleProperty("gpr.user").orNull
            ?: System.getenv("GITHUB_USER")
        val ghToken = providers.gradleProperty("gpr.token").orNull
            ?: System.getenv("GITHUB_TOKEN")
        if (!ghUser.isNullOrBlank() && !ghToken.isNullOrBlank()) {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/fadizg/kmp-ai")
                credentials {
                    username = ghUser
                    password = ghToken
                }
            }
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("kmp-ai · ${project.name}")
            description.set("Kotlin Multiplatform offline LLM library backed by llama.cpp")
            url.set("https://github.com/fadizg/kmp-ai")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set("fadizg")
                    name.set("fadizg")
                    url.set("https://github.com/fadizg")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/fadizg/kmp-ai.git")
                developerConnection.set("scm:git:ssh://git@github.com/fadizg/kmp-ai.git")
                url.set("https://github.com/fadizg/kmp-ai")
            }
        }
    }
}
