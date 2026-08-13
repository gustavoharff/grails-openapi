package dev.harff.grails.openapi;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.List;

public class GrailsOpenapiPlugin implements Plugin<Project> {

    private static final String AGGREGATE_TASK_NAME = "generateOpenapi";

    @Override
    public void apply(Project project) {
        OpenapiExtension extension = project.getExtensions().create("openapi", OpenapiExtension.class);

        registerTask(project, AGGREGATE_TASK_NAME,
                "Generates the OpenAPI specification(s) from the Grails application");

        project.afterEvaluate(evaluated -> {
            List<OpenapiDocumentSpec> documents = extension.resolveDocuments();

            for (OpenapiDocumentSpec document : documents) {
                if (!document.isDefaultDocument()) {
                    registerTask(evaluated, taskNameFor(document),
                            "Generates the '" + document.getName() + "' OpenAPI specification");
                }
            }

            List<OpenapiDocumentSpec> requested = requestedDocuments(evaluated, documents);
            if (!requested.isEmpty()) {
                evaluated.getExtensions().getExtraProperties()
                        .set("args", OpenapiCommandLine.build(requested));
            }
        });
    }

    private static void registerTask(Project project, String name, String description) {
        project.getTasks().register(name, task -> {
            task.setGroup("openapi");
            task.setDescription(description);
            task.finalizedBy(project.getTasks().named("runCommand"));
        });
    }

    /**
     * The documents the invoked task asks for: every one for {@code generateOpenapi}, a single
     * one for a per-document task such as {@code generateOpenapiPublic}. Empty when this build
     * generates no specification at all, in which case the {@code args} property is left alone.
     */
    private static List<OpenapiDocumentSpec> requestedDocuments(Project project,
                                                                List<OpenapiDocumentSpec> documents) {
        List<String> taskNames = project.getGradle().getStartParameter().getTaskNames();
        if (taskNames.stream().anyMatch(name -> isTask(name, AGGREGATE_TASK_NAME))) {
            return documents;
        }

        List<OpenapiDocumentSpec> requested = new ArrayList<>();
        for (OpenapiDocumentSpec document : documents) {
            if (document.isDefaultDocument()) {
                continue;
            }
            String taskName = taskNameFor(document);
            if (taskNames.stream().anyMatch(name -> isTask(name, taskName))) {
                requested.add(document);
            }
        }
        return requested;
    }

    private static boolean isTask(String requestedName, String taskName) {
        return requestedName.equals(taskName) || requestedName.endsWith(":" + taskName);
    }

    /** {@code public} becomes {@code generateOpenapiPublic}, {@code v2-public} becomes {@code generateOpenapiV2Public}. */
    static String taskNameFor(OpenapiDocumentSpec document) {
        StringBuilder name = new StringBuilder(AGGREGATE_TASK_NAME);
        for (String part : document.getName().split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        if (name.length() == AGGREGATE_TASK_NAME.length()) {
            throw new IllegalArgumentException(
                    "An OpenAPI document name must contain letters or digits: '" + document.getName() + "'");
        }
        return name.toString();
    }
}
