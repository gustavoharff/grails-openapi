package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.DocumentConfig
import grails.core.GrailsApplication
import grails.dev.commands.GrailsApplicationCommand
import groovy.transform.PackageScope

class GenerateOpenapiCommand implements GrailsApplicationCommand {

    GrailsApplication grailsApplication

    String description = 'Generates an OpenAPI specification from the Grails application'

    @Override
    boolean handle() {
        def urlMappingsHolder = applicationContext.getBean('grailsUrlMappingsHolder')

        OpenApiDocumentAssembler assembler = new OpenApiDocumentAssembler(
            grailsApplication: grailsApplication
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

    private static String resolveOutputPath(String output) {
        File file = new File(output)
        return file.absolute ? file.path : System.getProperty('user.dir') + '/' + output
    }
}
