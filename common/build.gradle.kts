
repositories {

    maven("https://oss.sonatype.org/content/groups/public/")

    maven("https://repo.codemc.io/repository/maven-public/")

}

dependencies {

    compileOnly(libs.react.api)
    compileOnly(libs.react.common)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.adventure.api)

}
