package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.DocumentConfig
import grails.core.GrailsApplication
import org.apache.grails.core.cli.GrailsApplicationCommand
import groovy.transform.PackageScope

class GenerateOpenapiCommand implements GrailsApplicationCommand {

    GrailsApplication grailsApplication

    String description = 'Generates an OpenAPI specification from the Grails application'

    @Override
    boolean handle() {
        def urlMappingsHolder = applicationContext.getBean('grailsUrlMappingsHolder')

        OpenApiDocumentAssembler assembler = new OpenApiDocumentAssembler(
            grailsApplication: resolveGrailsApplication()
        )

        OpenapiArgsParser.parse(commandArguments()).each { DocumentConfig config ->
            Map<String, Object> doc = assembler.assemble(urlMappingsHolder, config)

            YamlWriter.write(doc, resolveOutputPath(config.resolveOutput()))

            println "Generated ${doc.paths.size()} path(s)"
        }

        return true
    }

    /**
     * The raw command line, options included. {@code getArgs()} drops everything Grails
     * parsed as an option, which is precisely what drives this command, so it only serves
     * as a fallback for a context that leaves the raw arguments unset.
     */
    @PackageScope
    List<String> commandArguments() {
        try {
            String[] raw = executionContext?.commandLine?.rawArguments
            if (raw) return raw.toList()
        } catch (Exception ignored) {}
        try {
            return args ?: []
        } catch (Exception ignored) {
            return []
        }
    }

    /**
     * Grails 8 runs commands from the cli tier, which does not promise to autowire them, so the
     * application is taken from the context when nothing injected it.
     */
    private GrailsApplication resolveGrailsApplication() {
        grailsApplication ?: applicationContext.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)
    }

    private static String resolveOutputPath(String output) {
        File file = new File(output)
        return file.absolute ? file.path : System.getProperty('user.dir') + '/' + output
    }
}
