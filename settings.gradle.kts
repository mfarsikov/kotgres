pluginManagement{
    repositories{
        gradlePluginPortal()
        mavenLocal()
        jcenter()
    }
}
include("orm", "example")
rootProject.name = "postgres-json"
