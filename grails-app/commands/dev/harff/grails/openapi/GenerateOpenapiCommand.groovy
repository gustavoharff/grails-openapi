package dev.harff.grails.openapi

import grails.core.GrailsApplication
import grails.dev.commands.GrailsApplicationCommand

class GenerateOpenapiCommand implements GrailsApplicationCommand {

    GrailsApplication grailsApplication

    String description = 'Generates an OpenAPI specification from the Grails application'

    @Override
    boolean handle() {
        def urlMappingsHolder = applicationContext.getBean('grailsUrlMappingsHolder')

        OpenApiDocumentAssembler assembler = new OpenApiDocumentAssembler(
            grailsApplication: grailsApplication
        )

        Map<String, Object> doc = assembler.assemble(urlMappingsHolder)

        String outputPath = System.getProperty('user.dir') + '/build/openapi.yaml'
        YamlWriter.write(doc, outputPath)

        println "Generated ${doc.paths.size()} path(s)"
        return true
    }
}
