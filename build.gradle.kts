
import dev.feedforward.markdownto.DownParser
import org.intellij.markdown.ast.getTextInNode
import java.net.HttpURLConnection
import java.net.URL

buildscript {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.20")
        classpath("com.github.AlexPl292:mark-down-to-slack:1.1.2")
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.1.0.202203080745-r")
        classpath("org.kohsuke:github-api:1.305")

        // This comes from the changelog plugin
//        classpath("org.jetbrains:markdown:0.3.1")
    }
}

plugins {
    antlr
    java
    kotlin("jvm") version "1.7.20"

    id("org.jetbrains.intellij") version "1.10.0-SNAPSHOT"
    id("org.jetbrains.changelog") version "1.3.1"

    // ktlint linter - read more: https://github.com/JLLeitschuh/ktlint-gradle
    id("org.jlleitschuh.gradle.ktlint") version "10.3.0"
}

// Import variables from gradle.properties file
val javaVersion: String by project
val kotlinVersion: String by project
val ideaVersion: String by project
val downloadIdeaSources: String by project
val instrumentPluginCode: String by project
val remoteRobotVersion: String by project
val antlrVersion: String by project

val publishChannels: String by project
val publishToken: String by project

val slackUrl: String by project

repositories {
    mavenCentral()
    maven { url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies") }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    compileOnly("org.jetbrains:annotations:23.0.0")

    // https://mvnrepository.com/artifact/com.ensarsarajcic.neovim.java/neovim-api
    testImplementation("com.ensarsarajcic.neovim.java:neovim-api:0.2.3")
    testImplementation("com.ensarsarajcic.neovim.java:core-rpc:0.2.3")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-test
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")

    // https://mvnrepository.com/artifact/org.mockito.kotlin/mockito-kotlin
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")

    testImplementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    testImplementation("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")
    testImplementation("com.automation-remarks:video-recorder-junit:2.0")
    runtimeOnly("org.antlr:antlr4-runtime:$antlrVersion")
    antlr("org.antlr:antlr4:$antlrVersion")

    api(project(":vim-engine"))

    testApi("com.squareup.okhttp3:okhttp:4.10.0")
}

configurations {
    runtimeClasspath {
        exclude(group = "org.antlr", module = "antlr4")
    }
}

// --- Compilation
// This can be moved to other test registration when issue with tests in gradle will be fixed
tasks.register<Test>("testWithNeovim") {
    group = "verification"
    systemProperty("ideavim.nvim.test", "true")
    exclude("/ui/**")
    exclude("**/longrunning/**")
    exclude("**/propertybased/**")
}

tasks.register<Test>("testPropertyBased") {
    group = "verification"
//    include("**/propertybased/**")
}

tasks.register<Test>("testLongRunning") {
    group = "verification"
//    include("**/longrunning/**")
}

tasks {
    // Issue in gradle 7.3
    val test by getting(Test::class) {
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/*Test.class")
        include("**/*test.class")
        include("**/*Tests.class")
        exclude("**/ParserTest.class")
    }

    val testWithNeovim by getting(Test::class) {
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/*Test.class")
        include("**/*test.class")
        include("**/*Tests.class")
        exclude("**/ParserTest.class")
        exclude("**/longrunning/**")
        exclude("**/propertybased/**")
    }

    val testPropertyBased by getting(Test::class) {
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/propertybased/*Test.class")
        include("**/propertybased/*test.class")
        include("**/propertybased/*Tests.class")
    }

    val testLongRunning by getting(Test::class) {
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/longrunning/**/*Test.class")
        include("**/longrunning/**/*test.class")
        include("**/longrunning/**/*Tests.class")
        exclude("**/longrunning/**/ParserTest.class")
    }

    compileJava {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion

        options.encoding = "UTF-8"
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = javaVersion
            apiVersion = "1.6"
            freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
//            allWarningsAsErrors = true
        }
    }
    compileTestKotlin {
        kotlinOptions {
            jvmTarget = javaVersion
            apiVersion = "1.6"
//            allWarningsAsErrors = true
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

gradle.projectsEvaluated {
    tasks.compileJava {
//        options.compilerArgs.add("-Werror")
        options.compilerArgs.add("-Xlint:deprecation")
    }
}

// --- Intellij plugin

intellij {
    version.set(ideaVersion)
    pluginName.set("IdeaVim")

    updateSinceUntilBuild.set(false)

    downloadSources.set(downloadIdeaSources.toBoolean())
    instrumentCode.set(instrumentPluginCode.toBoolean())
    intellijRepository.set("https://www.jetbrains.com/intellij-repository")
    // Yaml is only used for testing. It's part of the IdeaIC distribution, but needs to be included as a reference
    plugins.set(listOf("java", "AceJump:3.8.4", "yaml"))
}

tasks {
    downloadRobotServerPlugin {
        version.set(remoteRobotVersion)
    }

    publishPlugin {
        channels.set(publishChannels.split(","))
        token.set(publishToken)
    }

    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    runPluginVerifier {
        downloadDir.set("${project.buildDir}/pluginVerifier/ides")
        teamCityOutputFormat.set(true)
//        ideVersions.set(listOf("IC-2021.3.4"))
    }

    generateGrammarSource {
        maxHeapSize = "128m"
        arguments.addAll(listOf("-package", "com.maddyhome.idea.vim.vimscript.parser.generated", "-visitor"))
        outputDirectory = file("src/main/java/com/maddyhome/idea/vim/vimscript/parser/generated")
    }

    named("compileKotlin") {
        dependsOn("generateGrammarSource")
    }

    // Add plugin open API sources to the plugin ZIP
    val createOpenApiSourceJar by registering(Jar::class) {
        // Java sources
        from(sourceSets.main.get().java) {
            include("**/com/maddyhome/idea/vim/**/*.java")
        }
        // Kotlin sources
        from(kotlin.sourceSets.main.get().kotlin) {
            include("**/com/maddyhome/idea/vim/**/*.kt")
        }
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        archiveClassifier.set("src")
    }

    buildPlugin {
        dependsOn(createOpenApiSourceJar)
        from(createOpenApiSourceJar) { into("lib/src") }
    }

    // Don't forget to update plugin.xml
    patchPluginXml {
        sinceBuild.set("223")
    }
}

// --- Linting

ktlint {
    disabledRules.add("no-wildcard-imports")
    version.set("0.43.0")
}

// --- Tests

tasks {
    test {
        exclude("**/propertybased/**")
        exclude("**/longrunning/**")
        exclude("/ui/**")
    }
}

tasks.register<Test>("testUi") {
    group = "verification"
    include("/ui/**")
}

// --- Changelog

changelog {
    groups.set(listOf("Features:", "Changes:", "Deprecations:", "Fixes:", "Merged PRs:"))
    itemPrefix.set("*")
    path.set("${project.projectDir}/CHANGES.md")
    unreleasedTerm.set("To Be Released")
    headerParserRegex.set("(\\d\\.\\d+(.\\d+)?)".toRegex())
//    header = { "${project.version}" }
//    version = "0.60"
}

tasks.register("getUnreleasedChangelog") {
    group = "changelog"
    doLast {
        val log = changelog.getUnreleased().toHTML()
        println(log)
    }
}

// --- Slack notification

tasks.register("slackNotification") {
    doLast {
        if (slackUrl.isBlank()) {
            println("Slack Url is not defined")
            return@doLast
        }
        val changeLog = changelog.getLatest().toText()
        val slackDown = DownParser(changeLog, true).toSlack().toString()

        //language=JSON
        val message = """
            {
                "text": "New version of IdeaVim",
                "blocks": [
                    {
                        "type": "section",
                        "text": {
                            "type": "mrkdwn",
                            "text": "IdeaVim $version has been released\n$slackDown"
                        }
                    }
                ]
            }
        """.trimIndent()

        println("Parsed data: $slackDown")
        val post = URL(slackUrl)
        with(post.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")

            outputStream.write(message.toByteArray())

            val postRc = responseCode
            println("Response code: $postRc")
            if (postRc == 200) {
                println(inputStream.bufferedReader().use { it.readText() })
            } else {
                println(errorStream.bufferedReader().use { it.readText() })
            }
        }
    }
}

// Uncomment to enable FUS testing mode
// tasks {
//    withType<org.jetbrains.intellij.tasks.RunIdeTask> {
//        jvmArgs("-Didea.is.internal=true")
//        jvmArgs("-Dfus.internal.test.mode=true")
//    }
// }

// --- Update authors
tasks.register("updateAuthors") {
    doLast {
        val uncheckedEmails = setOf(
            "aleksei.plate@jetbrains.com",
            "aleksei.plate@teamcity",
            "aleksei.plate@TeamCity",
            "alex.plate@192.168.0.109",
            "nikita.koshcheev@TeamCity",
        )
        updateAuthors(uncheckedEmails)
    }
}

val prId: String by project

tasks.register("updateMergedPr") {
    doLast {
        if (project.hasProperty("prId")) {
            println("Got pr id: $prId")
            updateMergedPr(prId.toInt())
        } else {
            error("Cannot get prId")
        }
    }
}

tasks.register("updateChangelog") {
    doLast {
        updateChangelog()
    }
}

tasks.register("testUpdateChangelog") {
    group = "verification"
    description = "This is a task to manually assert the correctness of the update tasks"
    doLast {
        val changesFile = File("$projectDir/CHANGES.md")
        val changes = changesFile.readText()

        val changesBuilder = StringBuilder(changes)
        val insertOffset = setupSection(changes, changesBuilder, "### Changes:")

        changesBuilder.insert(insertOffset, "--Hello--\n")

        changesFile.writeText(changesBuilder.toString())
    }
}

fun updateChangelog() {
    println("Start update authors")
    println(projectDir)
    val repository = org.eclipse.jgit.lib.RepositoryBuilder().setGitDir(File("$projectDir/.git")).build()
    val git = org.eclipse.jgit.api.Git(repository)
    val lastSuccessfulCommit = System.getenv("SUCCESS_COMMIT")!!
    val messages = git.log().call()
        .takeWhile {
            !it.id.name.equals(lastSuccessfulCommit, ignoreCase = true)
        }
        .map { it.shortMessage }

    // Collect fixes
    val newFixes = mutableListOf<Change>()
    println("Last successful commit: $lastSuccessfulCommit")
    println("Amount of commits: ${messages.size}")
    println("Start emails processing")
    for (message in messages) {
        println("Processing '$message'...")
        val lowercaseMessage = message.toLowerCase()
        val regex = "^fix\\((vim-\\d+)\\):".toRegex()
        val findResult = regex.find(lowercaseMessage)
        if (findResult != null) {
            println("Message matches")
            val value = findResult.groups[1]!!.value.toUpperCase()
            val shortMessage = message.drop(findResult.range.last + 1).trim()
            newFixes += Change(value, shortMessage)
        } else {
            println("Message doesn't match")
        }
    }

    // Update changes file
    val changesFile = File("$projectDir/CHANGES.md")
    val changes = changesFile.readText()

    val changesBuilder = StringBuilder(changes)
    val insertOffset = setupSection(changes, changesBuilder, "### Fixes:")

    if (insertOffset < 50) error("Incorrect offset: $insertOffset")

    val firstPartOfChanges = changes.take(insertOffset)
    val actualFixes = newFixes
        .filterNot { it.id in firstPartOfChanges }
    val newUpdates = actualFixes
        .joinToString("") { "* [${it.id}](https://youtrack.jetbrains.com/issue/${it.id}) ${it.text}\n" }

    changesBuilder.insert(insertOffset, newUpdates)
    if (actualFixes.isNotEmpty()) {
        changesFile.writeText(changesBuilder.toString())
    }
}

fun updateAuthors(uncheckedEmails: Set<String>) {
    println("Start update authors")
    println(projectDir)
    val repository = org.eclipse.jgit.lib.RepositoryBuilder().setGitDir(File("$projectDir/.git")).build()
    val git = org.eclipse.jgit.api.Git(repository)
    val lastSuccessfulCommit = System.getenv("SUCCESS_COMMIT")!!
    val hashesAndEmailes = git.log().call()
        .takeWhile {
            !it.id.name.equals(lastSuccessfulCommit, ignoreCase = true)
        }
        .associate { it.authorIdent.emailAddress to it.name }

    println("Last successful commit: $lastSuccessfulCommit")
    println("Amount of commits: ${hashesAndEmailes.size}")
    println("Emails: ${hashesAndEmailes.keys}")
    val gitHub = org.kohsuke.github.GitHub.connect()
    val ghRepository = gitHub.getRepository("JetBrains/ideavim")
    val users = mutableSetOf<Author>()
    println("Start emails processing")
    for ((email, hash) in hashesAndEmailes) {
        println("Processing '$email'...")
        if (email in uncheckedEmails) {
            println("Email '$email' is in unchecked emails. Skip it")
            continue
        }
        if ("dependabot[bot]@users.noreply.github.com" in email) {
            println("Email '$email' is from dependabot. Skip it")
            continue
        }
        val user = ghRepository.getCommit(hash).author
        val htmlUrl = user.htmlUrl.toString()
        val name = user.name ?: user.login
        users.add(Author(name, htmlUrl, email))
    }

    println("Emails processed")
    val authorsFile = File("$projectDir/AUTHORS.md")
    val authors = authorsFile.readText()
    val parser =
        org.intellij.markdown.parser.MarkdownParser(org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor())
    val tree = parser.buildMarkdownTreeFromString(authors)

    val contributorsSection = tree.children[24]
    val existingEmails = mutableSetOf<String>()
    for (child in contributorsSection.children) {
        if (child.children.size > 1) {
            existingEmails.add(
                child.children[1].children[0].children[2].children[2].getTextInNode(authors).toString()
            )
        }
    }

    val newAuthors = users.filterNot { it.mail in existingEmails }
    if (newAuthors.isEmpty()) return

    val authorNames = newAuthors.joinToString(", ") { it.name }
    println("::set-output name=authors::$authorNames")

    val insertionString = newAuthors.toMdString()
    val resultingString = StringBuffer(authors).insert(contributorsSection.endOffset, insertionString).toString()

    authorsFile.writeText(resultingString)
}

fun List<Author>.toMdString(): String {
    return this.joinToString {
        """
          |
          |* [![icon][mail]](mailto:${it.mail})
          |  [![icon][github]](${it.url})
          |  &nbsp;
          |  ${it.name}
        """.trimMargin()
    }
}

data class Author(val name: String, val url: String, val mail: String)
data class Change(val id: String, val text: String)

fun updateMergedPr(number: Int) {
    val gitHub = org.kohsuke.github.GitHub.connect()
    println("Connecting to the repo...")
    val repository = gitHub.getRepository("JetBrains/ideavim")
    println("Getting pull requests...")
    val pullRequest = repository.getPullRequest(number)
    if (pullRequest.user.login == "dependabot[bot]") return

    val changesFile = File("$projectDir/CHANGES.md")
    val changes = changesFile.readText()

    val changesBuilder = StringBuilder(changes)
    val insertOffset = setupSection(changes, changesBuilder, "### Merged PRs:")

    if (insertOffset < 50) error("Incorrect offset: $insertOffset")
    if (pullRequest.user.login == "dependabot[bot]") return

    val prNumber = pullRequest.number
    val userName = pullRequest.user.name
    val login = pullRequest.user.login
    val title = pullRequest.title
    val section =
        "* [$prNumber](https://github.com/JetBrains/ideavim/pull/$prNumber) by [$userName](https://github.com/$login): $title\n"
    changesBuilder.insert(insertOffset, section)

    changesFile.writeText(changesBuilder.toString())
}

fun setupSection(
    changes: String,
    authorsBuilder: StringBuilder,
    sectionName: String,
): Int {
    val parser =
        org.intellij.markdown.parser.MarkdownParser(org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor())
    val tree = parser.buildMarkdownTreeFromString(changes)

    var idx = -1
    for (index in tree.children.indices) {
        if (tree.children[index].getTextInNode(changes).startsWith("## ")) {
            idx = index
            break
        }
    }

    val hasToBeReleased = tree.children[idx].getTextInNode(changes).contains("To Be Released")
    return if (hasToBeReleased) {
        var mrgIdx = -1
        for (index in (idx + 1) until tree.children.lastIndex) {
            val textInNode = tree.children[index].getTextInNode(changes)
            val foundIndex = textInNode.startsWith(sectionName)
            if (foundIndex) {
                var filledPr = index + 2
                while (tree.children[filledPr].getTextInNode(changes).startsWith("*")) {
                    filledPr++
                }
                mrgIdx = tree.children[filledPr].startOffset + 1
                break
            } else {
                val currentSectionIndex = sections.indexOf(sectionName)
                val insertHere = textInNode.startsWith("## ") ||
                    textInNode.startsWith("### ") &&
                    sections.indexOfFirst { textInNode.startsWith(it) }
                        .let { if (it < 0) false else it > currentSectionIndex }
                if (insertHere) {
                    val section = """
                        $sectionName
                        
                        
                    """.trimIndent()
                    authorsBuilder.insert(tree.children[index].startOffset, section)
                    mrgIdx = tree.children[index].startOffset + (section.length - 1)
                    break
                }
            }
        }
        mrgIdx
    } else {
        val section = """
            ## To Be Released
            
            $sectionName
            
            
        """.trimIndent()
        authorsBuilder.insert(tree.children[idx].startOffset, section)
        tree.children[idx].startOffset + (section.length - 1)
    }
}

val sections = listOf(
    "### Features:",
    "### Changes:",
    "### Fixes:",
    "### Merged PRs:",
)
