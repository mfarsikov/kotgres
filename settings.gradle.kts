pluginManagement{
    repositories{
        gradlePluginPortal()
        mavenLocal()
        jcenter()
    }
}
include("kotgres-kapt", "kotgres-core", "example")
rootProject.name = "kotgres"
