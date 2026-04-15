pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    repositories {
        // ... 其他仓库 ...
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
    }
}

rootProject.name = "MyProxy"
include(":app")
