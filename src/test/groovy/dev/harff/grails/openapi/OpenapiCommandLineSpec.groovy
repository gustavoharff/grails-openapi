package dev.harff.grails.openapi

import org.grails.build.parsing.CommandLineParser
import spock.lang.Specification

/**
 * The Gradle task and the command talk to each other through a command line, so these
 * specs check both what is written and what comes back after Grails has tokenised it.
 */
class OpenapiCommandLineSpec extends Specification {

    def "an unconfigured default document needs no arguments"() {
        expect:
        OpenapiCommandLine.build([document('default')]) == 'generate-openapi'
    }

    def "a lone default document is written without a document option"() {
        given:
        def spec = document('default')
        spec.title = 'Internal API'
        spec.output = 'build/openapi.yaml'

        expect:
        OpenapiCommandLine.build([spec]) == 'generate-openapi --title="Internal API" --output=build/openapi.yaml'
    }

    def "several documents are each named"() {
        given:
        def internal = document('default')
        internal.version = '1.16.0'
        def pub = document('public')
        pub.includePaths = ['/public/v1/**']

        expect:
        OpenapiCommandLine.build([internal, pub]) ==
            'generate-openapi --document=default --version=1.16.0 --document=public --include=/public/v1/**'
    }

    def "a server map becomes a url and a description"() {
        given:
        def spec = document('public')
        spec.servers = ['https://api.example.com', [url: 'https://sandbox.example.com', description: 'Sandbox']]

        expect:
        OpenapiCommandLine.build([spec]) == 'generate-openapi --document=public' +
            ' --server=https://api.example.com --server=https://sandbox.example.com|Sandbox'
    }

    def "a server map without a url is rejected"() {
        given:
        def spec = document('public')
        spec.servers = [[description: 'Sandbox']]

        when:
        OpenapiCommandLine.build([spec])

        then:
        thrown(IllegalArgumentException)
    }

    def "a value carrying a double quote is single quoted"() {
        given:
        def spec = document('default')
        spec.title = 'The "public" API'

        expect:
        OpenapiCommandLine.build([spec]) == /generate-openapi --title='The "public" API'/
    }

    def "a value carrying both quote styles is rejected"() {
        given:
        def spec = document('default')
        spec.title = /The "public" API's/

        when:
        OpenapiCommandLine.build([spec])

        then:
        thrown(IllegalArgumentException)
    }

    def "the command reads back what the task wrote"() {
        given:
        def internal = document('default')
        internal.title = 'Internal API'
        internal.version = '1.16.0'
        internal.output = 'build/openapi.yaml'

        def pub = document('public')
        pub.title = 'Public API'
        pub.version = '1.0.0'
        pub.description = 'Read your monitoring data, from outside.'
        pub.servers = ['https://api.example.com', [url: 'https://sandbox.example.com', description: 'Sandbox box']]
        pub.includePaths = ['/public/v1/**']
        pub.excludePaths = ['/public/v1/internal/**']
        pub.output = 'build/openapi-public.yaml'

        when:
        String commandLine = OpenapiCommandLine.build([internal, pub])
        def documents = OpenapiArgsParser.parse(tokenize(commandLine))

        then:
        documents.size() == 2

        and:
        documents[0].name == 'default'
        documents[0].title == 'Internal API'
        documents[0].version == '1.16.0'
        documents[0].resolveOutput() == 'build/openapi.yaml'

        and:
        documents[1].name == 'public'
        documents[1].title == 'Public API'
        documents[1].version == '1.0.0'
        documents[1].description == 'Read your monitoring data, from outside.'
        documents[1].servers == [
            [url: 'https://api.example.com'],
            [url: 'https://sandbox.example.com', description: 'Sandbox box'],
        ]
        documents[1].includePaths == ['/public/v1/**']
        documents[1].excludePaths == ['/public/v1/internal/**']
        documents[1].resolveOutput() == 'build/openapi-public.yaml'
    }

    def "an unconfigured project reads back as the default document"() {
        when:
        def documents = OpenapiArgsParser.parse(tokenize(OpenapiCommandLine.build([document('default')])))

        then:
        documents.size() == 1
        documents[0].name == 'default'
        documents[0].resolveTitle() == 'API'
        documents[0].resolveVersion() == '1.0.0'
        documents[0].resolveOutput() == 'build/openapi.yaml'
    }

    // ---- Helpers ----

    /** Splits the line the way Grails' runCommand task does before handing it to the command. */
    private static List<String> tokenize(String commandLine) {
        return CommandLineParser.translateCommandline(commandLine).toList()
    }

    private static OpenapiDocumentSpec document(String name) {
        return new OpenapiDocumentSpec(name)
    }
}
