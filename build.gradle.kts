plugins {
    alias(libs.plugins.fpgradle)
    alias(libs.plugins.kotlin)
}

group = "com.falsepattern"

minecraft_fp {
    mod {
        modid = "forgelin"
        name = "Forgelin 2"
        rootPkg = "net.shadowfacts.forgelin"
    }
    core {
        coreModClass = "preloader.ForgelinPlugin"
    }

    kotlin {
        hasKotlinDeps = true
    }

    shadow {
        relocate = true
        minimize = true
    }

    tokens {
        tokenClass = "Tags"
    }

    publish {
        maven {
            repoName = "mavenpattern"
            repoUrl = "https://mvn.falsepattern.com/releases"
        }
    }
}

//Stuff added here will get marked compileOnlyApi, registered in the deploader, and bundled into the offline jar
val bundledKotlin = listOf(
    libs.kotlin.stdlib,
    libs.kotlin.stdlibJdk7,
    libs.kotlin.stdlibJdk8,
    libs.kotlin.reflect,
    libs.jetbrains.annotations,
    libs.kotlinx.coroutinesCore,
    libs.kotlinx.coroutinesCoreJvm,
    libs.kotlinx.coroutinesJdk8,
    libs.kotlinx.serializationCore,
    libs.kotlinx.serializationCoreJvm
)

val depLoader = configurations.register("deploader")

repositories {
    exclusive(mavenpattern(), "com.falsepattern")
}

dependencies {
    bundledKotlin.forEach { compileOnlyApi(it) }
    shadowImplementation(libs.fastutil)

    //Deploader
    depLoader(variantOf(libs.fplib) { classifier("deploader") })
    shadowImplementation(variantOf(libs.fplib) { classifier("deploader_stub") })
}

tasks.processResources {
    from(depLoader) {
        rename { "fplib_deploader.jar" }
    }
}

tasks.shadowJar {
    val root = minecraft_fp.mod.rootPkg.map { it.replace('.', '/') }
    exclude("it/unimi/dsi/fastutil/**/package-info.class")
}

val offlineJar = tasks.register<Jar>("offlineJar") {
    dependsOn(tasks.reobfJar)
    archiveClassifier = "offline"
    from(zipTree(tasks.reobfJar.map { it.archiveFile })) {
        filesMatching("META-INF/kotlindeps.json") {
            filter {
                if (it == "    \"https://repo1.maven.org/maven2/\"") {
                    ""
                } else {
                    it
                }
            }
        }
    }
    val prefix = "META-INF/falsepatternlib_repo"
    bundledKotlin.map { it.get() }.forEach { dep ->
        into("$prefix/${dep.group!!.replace('.', '/')}/${dep.name}/${dep.version!!}") {
            from(configurations["compileClasspath"]) {
                include("${dep.name}-${dep.version}.jar")
            }
        }
    }
}

tasks.named("assemble") {
    dependsOn(offlineJar)
}

tasks.processResources {
    val kt = provider{ bundledKotlin }.map { it.map { dep -> dep.get() }.map { dep -> "${dep.group}:${dep.name}:${dep.version!!}" } }
    inputs.property("bundledKotlin", kt)
    filesMatching("META-INF/kotlindeps.json") {
        expand("kotlinDeps" to run {
            val res = StringBuilder()
            kt.get().forEach { dep ->
                res.append('"').append(dep).append("\",")
            }
            res.setLength(res.length - 2)
            res.delete(0, 1)
            res.toString()
        })
    }
    from(file(".idea/icon.png")) {
        rename { "forgelin.png" }
    }
}

afterEvaluate {
    publishing {
        publications {
            named<MavenPublication>("maven") {
                artifact(offlineJar)
            }
        }
    }
}