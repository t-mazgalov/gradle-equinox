package name.mazgalov.equinox.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskContainer

import java.nio.file.Path

class EquinoxPlugin implements Plugin<Project> {
    private Project project

    @Override
    void apply(Project project) {
        this.project = project

        ConfigurationContainer configurations = project.configurations
        DependencyHandler dependencies = project.dependencies
        TaskContainer tasks = project.tasks

        project.repositories {
            mavenCentral()
        }

        configurations.create 'equinoxKernel'
        dependencies.add 'equinoxKernel', [group:'org.eclipse', name: 'osgi', version: '3.10.0-v20140606-1445', configuration: 'runtime']

        tasks.create 'buildEquinoxConfig', {
            doLast {
                def equinoxBuildDir = new File("$project.buildDir/equinox")
                Path equinoxKernelFile =
                        configurations.equinoxKernel.incoming.files.singleFile.toPath() // Equinox Kernel file

                Properties configurationProperties = new Properties()
                configurationProperties['osgi.install.area'] = equinoxBuildDir.toPath().toAbsolutePath().toUri().toString()
                configurationProperties['osgi.framework'] = "file:${equinoxBuildDir.toPath().relativize(equinoxKernelFile)}".toString()
                configurationProperties['osgi.noShutdown'] = 'true'
                configurationProperties['eclipse.consoleLog'] = 'true'
                configurationProperties['eclipse.ignoreApp'] = 'true'

                def equinoxConfigurationDir = new File("$equinoxBuildDir/configuration")
                equinoxConfigurationDir.mkdirs()
                def equinoxConfigurationFile = new File(equinoxConfigurationDir, 'config.ini')
                equinoxConfigurationFile.withOutputStream {
                    configurationProperties.store it, null/*No comments*/
                }
            }
        }

        tasks.create 'copyEquinoxRunScripts', Copy.class, {
            from new File (EquinoxPlugin.classLoader.getResource('bin/equinox.bat').file)
            into "$project.buildDir/equinox/bin"
            filter {
                it.replace('{{OSGI_FILE}}', project.configurations.equinoxKernel.incoming.files.singleFile.path)
            }
            filter {
                it.replace('{{CONFIG_FILE}}', "$project.buildDir/equinox/configuration")
            }
        }
    }
}