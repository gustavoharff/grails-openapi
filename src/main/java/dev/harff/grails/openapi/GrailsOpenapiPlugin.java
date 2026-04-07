package dev.harff.grails.openapi;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class GrailsOpenapiPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        boolean isGenerateOpenapiRequested = project.getGradle().getStartParameter().getTaskNames()
                .stream()
                .anyMatch(name -> name.equals("generateOpenapi") || name.endsWith(":generateOpenapi"));

        if (isGenerateOpenapiRequested) {
            project.getExtensions().getExtraProperties().set("args", "generate-openapi");
        }

        project.getTasks().register("generateOpenapi", task -> {
            task.setGroup("openapi");
            task.setDescription("Generates an OpenAPI specification from the Grails application");
            task.finalizedBy(project.getTasks().named("runCommand"));
        });
    }

}
