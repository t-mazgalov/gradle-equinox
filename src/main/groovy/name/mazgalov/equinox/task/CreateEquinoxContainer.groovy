package name.mazgalov.equinox.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.ParallelizableTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Path
import java.util.jar.JarInputStream

/**
 * Created by Todor Mazgalov on 18-Sep-16.
 */
@ParallelizableTask
class CreateEquinoxContainer extends DefaultTask{

    /**
     * The name of the container which will be created.
     * The property is introduced in order to create multiple different instances.
     */
    @Input
    @Optional
    def containerName = 'equinox'

    /**
     * The Equinox kernel dependency.
     * It will be passed to the Gradle DependencyHandler with the kernel configuration.
     */
    @Input
    @Optional
    def equinoxNotation = [group:'org.eclipse', name: 'osgi', version: '3.10.0-v20140606-1445', configuration: 'runtime']

    /**
     * Created kernel configuration. It depends on the container name - <containerName>Kernel
     */
    protected Configuration kernelConfiguration

    /**
     * The Equinox output dir. It depends on the container name - <buildDir>/<containerName>
     */
    protected def equinoxBuildDir

    /**
     * Contains all bundles that must be installed in the created Equinox framework.
     * The map has format: startLevel as key and Configuration list as value.
     */
    protected def bundlesInstallationMap = [:]

    /**
     * Creates an Equinox Container configuration and startup script.
     * The task defines an Equinox kernel configuration based on the selected container name.
     * Sets the Equinox as dependency to the kernel configuration.
     * Creates an addition task which generates the Equinox startup script based on the OS.
     */
    CreateEquinoxContainer() {
        group 'Equinox Generation'
        description 'Creates an Equinox container instance'

        equinoxBuildDir = new File(project.buildDir, containerName)

        // Creation of the kernel configuration
        def kernelConfigurationName = "${containerName}Kernel"
        logger.debug "Creating configuration: $kernelConfigurationName for the the Equinox Kernel"
        kernelConfiguration = project.configurations.create kernelConfigurationName

        // Setting the kernel as dependency to the kernel configuration
        logger.debug "Adding dependency notation: $equinoxNotation to configuration: $kernelConfiguration.name"
        project.dependencies.add kernelConfiguration.name, equinoxNotation

        // Creation of the boot configuration
        def bootConfigurationName = "${containerName}Boot"
        logger.debug "Creating configuration: $bootConfigurationName for the the Equinox boot bundles"
        Configuration bootConfiguration = project.configurations.create bootConfigurationName, {
            transitive = false
        }

        // Setting the boot bundles as dependencies to the boot configuration
        project.dependencies.add bootConfiguration.name, [group:'org.apache.felix', name: 'org.apache.felix.gogo.shell', version: '0.12.0']
        project.dependencies.add bootConfiguration.name, [group:'org.apache.felix', name: 'org.apache.felix.gogo.runtime', version: '0.12.0']
        project.dependencies.add bootConfiguration.name, [group:'org.apache.felix', name: 'org.apache.felix.gogo.command', version: '0.12.0']

        install(1, bootConfiguration)

        Task startupScriptTask = project.tasks.create "create${containerName.capitalize()}StartupScript", Copy.class, {
            group 'Equinox Generation'
            description 'Generates a startup script for Equinox container instance'

            boolean isWindows = System.properties['os.name'].toLowerCase().contains('windows')
            def startupFile = "equinox.${isWindows ? 'bat' : 'sh'}"
            logger.debug "Getting Equinox startup file: $startupFile"

            from new File(CreateEquinoxContainer.classLoader.getResource("bin/$startupFile").file)
            into new File(equinoxBuildDir, 'bin')

            doFirst {
                // Specifying the filters which will replace the placeholders
                // from the included startup scripts

                filter {
                    // Setting the Equinox kernel jar file path
                    it.replace('{{OSGI_FILE}}', kernelConfiguration.incoming.files.singleFile.path)
                }

                filter {
                    // Setting the configuration directory
                    it.replace('{{CONFIG_FILE}}', "$equinoxBuildDir/configuration")
                }
            }
        }

        // The startup script generation will be execution after the
        // creation of the configuration
        finalizedBy startupScriptTask
    }

    /**
     * The task action which creates the config.ini file.
     */
    @TaskAction
    def build() {
        if(equinoxBuildDir.exists()) {
            // If the build directory for the container already exists,
            // the task assume that another container with the same name is available
            throw new GradleException("Container with name $containerName already exists.")
        }

        logger.info "Creating container: $containerName with directory: $equinoxBuildDir"

        Path equinoxKernelFile = kernelConfiguration.incoming.files.singleFile.toPath()
        // Definition of the configuration properties
        // TODO Add a mechanism allowing override of the default configuration properties
        Properties configurationProperties = new Properties()
        configurationProperties['osgi.install.area'] = equinoxBuildDir.toPath().toAbsolutePath().toUri().toString()
        configurationProperties['osgi.framework'] = "file:${equinoxBuildDir.toPath().relativize(equinoxKernelFile)}".toString()
        configurationProperties['osgi.noShutdown'] = 'true'
        configurationProperties['eclipse.consoleLog'] = 'true'
        configurationProperties['eclipse.ignoreApp'] = 'true'
        configurationProperties['osgi.bundles'] = generateInstallationBundlesList()

        logger.debug 'Generated config.ini file:'
        logger.debug configurationProperties.toMapString()

        // Storing the config.ini file
        def equinoxConfigurationDir = new File(equinoxBuildDir, 'configuration')
        equinoxConfigurationDir.mkdirs()
        def equinoxConfigurationFile = new File(equinoxConfigurationDir, 'config.ini')
        equinoxConfigurationFile.withOutputStream {
            logger.debug "Storing the config.ini file to $equinoxConfigurationFile.path"
            configurationProperties.store it, null/*No comments*/
        }
    }

    /**
     * Adds bundles for installation in the Equinox framework with specific start level.
     * @param startLevel The start level of the configuration dependencies
     * @param configuration A configuration with already defined dependencies. The task will extract the bundles
     * and install them in the created framework.
     */
    void install(int startLevel, Configuration configuration) {
        if(bundlesInstallationMap[startLevel] == null) {
            bundlesInstallationMap[startLevel] = [configuration]
        } else {
            bundlesInstallationMap[startLevel] << configuration
        }
    }

    /**
     * Adds bundles for installation in the Equinox framework with default start level - 4
     * @param configuration A configuration with already defined dependencies. Tha task will extract the bundles
     * and install them in the created framework.
     */
    void install(Configuration configuration) {
        install(4, configuration)
    }

    /**
     * Generates a list with bundles which will be installed in the Equinox framework.
     * @return A comma-separated list with the bundles for installation with format:
     * reference:file:&lt;file-path&gt;@&lt;start-lervel&gt;[:&lt;auto-start-flag&gt;]
     */
    protected String generateInstallationBundlesList() {
        def result = bundlesInstallationMap.collect {int startLevel, List configurations ->
            configurations.findResults { configuration ->
                configuration.findResults { file ->
                    file.withInputStream { stream ->
                        JarInputStream jarInputStream = null
                        try {
                            jarInputStream = new JarInputStream(stream)
                            java.util.jar.Manifest mf = jarInputStream.manifest
                            if(mf != null) {
                                mf.mainAttributes.containsKey(new java.util.jar.Attributes.Name('Fragment-Host')) ?
                                        "reference:file:$file.path@$startLevel" :
                                        "reference:file:$file.path@$startLevel:start"
                            } else {
                                logger.warn "File with null manifest found: $file"
                                "reference:file:$file.path@$startLevel"
                            }
                        } finally {
                            if(jarInputStream != null) {
                                jarInputStream.close()
                            }
                            if(stream != null) {
                                stream.close()
                            }
                        }
                    }
                }.join(',')
            }.join(',')
        }.join(',')
        logger.info "Generated installation property value: $result"
        result
    }
}
/*def bundlesList = configurations.bundlesLoader.findResults { jar ->
                    if(!jar.name.contains('sources')) { // Exclude sources
                        jar.withInputStream { stream ->
                            JarInputStream jarInputStream = null
                            try {
                                jarInputStream = new JarInputStream(stream)
                                java.util.jar.Manifest mf = jarInputStream.getManifest();
                                if(mf != null) {
                                    if(mf.mainAttributes.containsKey(new java.util.jar.Attributes.Name('Fragment-Host'))) {
                                        "reference:file:$jar.path@1"
                                    } else {
                                        "reference:file:$jar.path@1:start"
                                    }
                                }
                            } finally {
                                if(jarInputStream != null) {
                                    jarInputStream.close()
                                }
                                if(stream != null) {
                                    stream.close()
                                }
                            }
                        }
                    }
                }*/

