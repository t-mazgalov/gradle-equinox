package name.mazgalov.equinox.plugin

import name.mazgalov.equinox.task.CreateEquinoxContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

class EquinoxPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.repositories {
            // TODO Add check if the script defines own repositories. If true - do not enable Maven Central
            mavenCentral()
        }

        // Defines the default task for creation of Equinox container
        project.tasks.create 'createEquinoxContainer', CreateEquinoxContainer.class, {
            containerName = 'equinox'
        }
    }
}