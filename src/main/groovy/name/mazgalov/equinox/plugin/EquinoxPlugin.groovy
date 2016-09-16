package name.mazgalov.equinox.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class EquinoxPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println 'Success!!!'
    }
}