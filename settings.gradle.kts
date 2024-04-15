pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://a8c-libs.s3.amazonaws.com/android") {
            content {
                includeGroup("org.wordpress")
                includeGroup("org.wordpress.wellsql")
                includeGroup("org.wordpress.wellsql.wellsql-processor")
                includeGroup("org.wordpress.fluxc")
                includeGroup("org.wordpress.fluxc.plugins")
            }
        }
    }
}

rootProject.name = "EncryptedLogging"
include(":encryptedlogging")
