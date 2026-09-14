
repositories {

    maven("https://oss.sonatype.org/content/groups/public/")

    maven("https://repo.codemc.io/repository/maven-public/")

}

dependencies {

    compileOnly(libs.paper.api)

    compileOnly(libs.react)

    compileOnly(libs.cloud.paper)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.packetevents)

}