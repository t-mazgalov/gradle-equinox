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
                .withPluginClasspath(pluginClasspath)
                .build()

        then:
        printTestOutput result
        result.output.contains('Success!!!')
    }

    void printTestOutput(def testResult) {
        println "=================== Test: ${name.getMethodName()} ==================="
        println testResult.output
        println "====================================================================="
    }
}
