import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

/**
 * Created by todor on 15-Sep-16.
 */
class EquinoxPluginTest extends Specification{
    @Rule TestName name = new TestName()
    def testsBuildDir = new File(System.properties['buildDir'], 'testExecution')
    def buildFile

    List<File> pluginClasspath

    def setup() {
        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }
    }

    def "apply-plugin-test"() {
        given:
        def testBuildDir = new File(testsBuildDir, name.getMethodName())
        testBuildDir.deleteDir()
        testBuildDir.mkdirs()
        buildFile = new File(testBuildDir, 'build.gradle')
        buildFile.createNewFile()

        buildFile << """
plugins {
    id 'name.mazgalov.equinox'
}
"""

        when:
        def result = GradleRunner.create()
                .withProjectDir(testBuildDir)
                .withArguments('createEquinoxContainer', '-i', '-s')
                .withPluginClasspath(pluginClasspath)
                .build()

        then:
        printTestOutput result
    }

    def "create-custom-equinox-containers-test"() {
        given:
        def testBuildDir = new File(testsBuildDir, name.getMethodName())
        testBuildDir.deleteDir()
        testBuildDir.mkdirs()
        buildFile = new File(testBuildDir, 'build.gradle')
        buildFile.createNewFile()

        buildFile << """
plugins {
    id 'name.mazgalov.equinox'
}

project.repositories {
    mavenCentral()
}

configurations {
    ds {
        transitive = false
    }
    jline
}

dependencies {
    ds group: 'org.apache.felix', name: 'org.apache.felix.scr', version: '2.0.6'
    ds group: 'org.apache.felix', name: 'org.apache.felix.configadmin', version: '1.8.10'
    jline group: 'org.apache.servicemix.bundles', name: 'org.apache.servicemix.bundles.jline', version: '0.9.94_1'
}

task 'createFirstContainer', type: name.mazgalov.equinox.task.CreateEquinoxContainer, {
    containerName = 'firstEquinox'
    install 2, configurations.ds
}

task 'createSecondContainer', type: name.mazgalov.equinox.task.CreateEquinoxContainer, {
    containerName = 'secondEquinox'
    install configurations.jline
}
"""

        when:
        println pluginClasspath
        def result = GradleRunner.create()
                .withProjectDir(testBuildDir)
                .withArguments('createFirstContainer', 'createSecondContainer', '-s')
                .withPluginClasspath(pluginClasspath)
                .build()

        then:
        printTestOutput result
    }

    void printTestOutput(def testResult) {
        println "=================== Test: ${name.getMethodName()} ==================="
        println testResult.output
        println "====================================================================="
    }
}
