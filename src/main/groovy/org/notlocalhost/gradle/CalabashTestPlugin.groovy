package org.notlocalhost.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.Exec

class CalabashTestPlugin implements Plugin<Project> {
    private static final String TEST_TASK_NAME = 'calabash'
    def outFile;

    void apply(Project project) {
        project.extensions.create("calabashTest", CalabashTestPluginExtension)

        def hasAppPlugin = project.plugins.hasPlugin AppPlugin
        def hasLibraryPlugin = project.plugins.hasPlugin LibraryPlugin

        // Ensure the Android plugin has been added in app or library form, but not both.
        if (!hasAppPlugin && !hasLibraryPlugin) {
            throw new IllegalStateException("The 'com.android.application' or 'com.android.library' plugin is required.")
        } else if (hasAppPlugin && hasLibraryPlugin) {
            throw new IllegalStateException(
                    "Having both 'com.android.application' and 'com.android.library' plugin is not supported.")
        }

        def variants = hasAppPlugin ? project.android.applicationVariants :
                project.android.libraryVariants

        def apkFilePath = "$project.buildDir/outputs/apk"

        variants.all { variant ->
            def buildTypeName = variant.buildType.name.capitalize()
            def projectFlavorNames = [""]
            if (hasAppPlugin) {
                projectFlavorNames = variant.productFlavors.collect { it.name.capitalize() }
                if (projectFlavorNames.isEmpty()) {
                    projectFlavorNames = [""]
                }
            }
            def projectFlavorName = projectFlavorNames.join('-')
            def variationName = "${projectFlavorNames.join()}$buildTypeName"

            def apkName = ""
            if(projectFlavorName != "") {
                apkName = "${project.name}-${projectFlavorName.toLowerCase()}-${buildTypeName.toLowerCase()}-unaligned.apk"
            } else {
                apkName = "${project.name}-${buildTypeName.toLowerCase()}-unaligned.apk"
            }

            project.logger.debug "==========================="
            project.logger.debug "$apkFilePath/$apkName"
            project.logger.debug "${project.getPath()}"
            project.logger.debug "==========================="


            def taskRunName = "$TEST_TASK_NAME$variationName"
            def testRunTask = project.tasks.create(taskRunName, Exec)
            testRunTask.dependsOn project["assemble${variationName}"]
            testRunTask.description = "Run Calabash Tests for '$variationName'."
            testRunTask.group = JavaBasePlugin.VERIFICATION_GROUP

            def apkFile = "$apkFilePath/$apkName"
            testRunTask.workingDir "${project.rootDir}/"
            def os = System.getProperty("os.name").toLowerCase()

            def outFileDir = project.file("build/reports/calabash/${variationName}")

            Iterable commandArguments = constructCommandLineArguments(project, apkFile, outFileDir)

            if (!os.contains("windows")) { // assume Linux
                testRunTask.environment("SCREENSHOT_PATH", "${outFileDir}/")
                testRunTask.environment("RESET_BETWEEN_SCENARIOS", project.calabashTest.resetBetweenScenario? "1":"0")
            }

            testRunTask.commandLine commandArguments

            testRunTask.doFirst {
                if(!outFileDir.exists()) {
                    project.logger.debug "Making dir path $outFileDir.canonicalPath"
                    if(!outFileDir.mkdirs()) {
                        throw new IllegalStateException("Could not create reporting directories")
                    }
                }
            }

            testRunTask.doLast {
                println "\r\nCalabash HTML Report: file://$outFile.canonicalPath"
            }
        }
    }

    Iterable constructCommandLineArguments(Project project, String apkFile, File outFileDir) {
        def os = System.getProperty("os.name").toLowerCase()

        java.util.ArrayList<String> commandArguments = new ArrayList<String>()

        if (os.contains("windows")) {
            // you start commands in Windows by kicking off a cmd shell
            commandArguments.add("cmd")
            commandArguments.add("/c")
        }

        String calabashPath = System.properties['calabashPath'] == null ? "" : System.properties['calabashPath']

        commandArguments.add(calabashPath + "calabash-android");
        commandArguments.add("run")
        commandArguments.add(apkFile)


        String featuresPath = System.properties['featuresPath'] == null ? project.calabashTest.featuresPath : System.properties['featuresPath']

        if (featuresPath != null) {
            commandArguments.add(featuresPath)
        }

        if(project.calabashTest.profile != null) {
            commandArguments.add("--profile")
            commandArguments.add(project.calabashTest.profile)
        }

        String[] outFileFormats = project.calabashTest.formats
        if (outFileFormats == null) {
            outFileFormats = ["html"]
        }

        for (String outFileFormat : outFileFormats) {
            outFile = new File(outFileDir, "report."+outFileFormat)
            commandArguments.add("--format")
            commandArguments.add(outFileFormat)
            commandArguments.add("--out")
            commandArguments.add(outFile.canonicalPath)
        }

        String tagExpression = project.calabashTest.tagExpression
        if(project.calabashTest.tagExpression != null){
            commandArguments.add("--tags")
            commandArguments.add(tagExpression)

        }

        commandArguments.add("-v")

        return commandArguments;
    }
}
