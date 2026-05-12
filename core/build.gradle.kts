import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(
        groupId = project.property("GROUP").toString(),
        artifactId = "imdb-kt",
        version = project.property("VERSION_NAME").toString()
    )
    pom {
        name.set(project.property("POM_NAME").toString())
        description.set(project.property("POM_DESCRIPTION").toString())
        url.set(project.property("POM_URL").toString())
        licenses {
            license {
                name.set(project.property("POM_LICENSE_NAME").toString())
                url.set(project.property("POM_LICENSE_URL").toString())
            }
        }
        developers {
            developer {
                id.set(project.property("POM_DEVELOPER_ID").toString())
                name.set(project.property("POM_DEVELOPER_NAME").toString())
                email.set(project.property("POM_DEVELOPER_EMAIL").toString())
            }
        }
        scm {
            url.set(project.property("POM_SCM_URL").toString())
            connection.set(project.property("POM_SCM_CONNECTION").toString())
            developerConnection.set(project.property("POM_SCM_DEV_CONNECTION").toString())
        }
    }
}

detekt {
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
}

tasks.withType<Test> {
    useJUnit()
}
