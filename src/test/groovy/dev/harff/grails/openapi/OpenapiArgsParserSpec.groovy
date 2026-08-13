package dev.harff.grails.openapi

import spock.lang.Specification

class OpenapiArgsParserSpec extends Specification {

    def "no arguments describe the single default document"() {
        when:
        def documents = OpenapiArgsParser.parse(['generate-openapi'])

        then:
        documents.size() == 1
        documents[0].name == 'default'
        documents[0].resolveTitle() == 'API'
        documents[0].resolveVersion() == '1.0.0'
        documents[0].resolveServers() == [[url: '/']]
        documents[0].resolveOutput() == 'build/openapi.yaml'
        documents[0].includePaths.isEmpty()
        documents[0].excludePaths.isEmpty()
    }

    def "a null argument list still describes the default document"() {
        expect:
        OpenapiArgsParser.parse(null).size() == 1
    }

    def "options before any document belong to the default document"() {
        when:
        def documents = OpenapiArgsParser.parse([
            'generate-openapi',
            '--title=Internal API',
            '--version=1.16.0',
            '--output=build/openapi.yaml',
        ])

        then:
        documents.size() == 1
        documents[0].name == 'default'
        documents[0].title == 'Internal API'
        documents[0].version == '1.16.0'
        documents[0].output == 'build/openapi.yaml'
    }

    def "each document option starts a new document"() {
        when:
        def documents = OpenapiArgsParser.parse([
            'generate-openapi',
            '--title=Internal API',
            '--document=public',
            '--title=Public API',
            '--include=/public/v1/**',
            '--output=build/openapi-public.yaml',
        ])

        then:
        documents.size() == 2
        documents[0].name == 'default'
        documents[0].title == 'Internal API'
        documents[0].includePaths.isEmpty()
        documents[1].name == 'public'
        documents[1].title == 'Public API'
        documents[1].includePaths == ['/public/v1/**']
        documents[1].resolveOutput() == 'build/openapi-public.yaml'
    }

    def "a leading document option keeps the default document out of the run"() {
        when:
        def documents = OpenapiArgsParser.parse([
            'generate-openapi',
            '--document=public',
            '--include=/public/v1/**',
        ])

        then:
        documents.size() == 1
        documents[0].name == 'public'
        documents[0].resolveOutput() == 'build/openapi-public.yaml'
    }

    def "include and exclude options accumulate"() {
        when:
        def documents = OpenapiArgsParser.parse([
            'generate-openapi',
            '--include=/public/v1/**',
            '--include=/open/**',
            '--exclude=/public/v1/internal/**',
        ])

        then:
        documents[0].includePaths == ['/public/v1/**', '/open/**']
        documents[0].excludePaths == ['/public/v1/internal/**']
    }

    def "a comma separated include is split into globs"() {
        when:
        def documents = OpenapiArgsParser.parse(['generate-openapi', '--include=/a/**, /b/**'])

        then:
        documents[0].includePaths == ['/a/**', '/b/**']
    }

    def "servers accumulate and may carry a description"() {
        when:
        def documents = OpenapiArgsParser.parse([
            'generate-openapi',
            '--server=https://api.example.com',
            '--server=https://sandbox.example.com|Sandbox',
        ])

        then:
        documents[0].servers == [
            [url: 'https://api.example.com'],
            [url: 'https://sandbox.example.com', description: 'Sandbox'],
        ]
        documents[0].resolveServers() == documents[0].servers
    }

    def "a description may contain spaces and punctuation"() {
        when:
        def documents = OpenapiArgsParser.parse([
            'generate-openapi',
            '--description=Read your monitoring data, from outside.',
        ])

        then:
        documents[0].description == 'Read your monitoring data, from outside.'
    }

    def "unknown options are ignored"() {
        when:
        def documents = OpenapiArgsParser.parse(['generate-openapi', '--stacktrace', '--verbose=true'])

        then:
        documents.size() == 1
        documents[0].name == 'default'
        !documents[0].title
    }

    def "an unknown option does not open a document of its own"() {
        when:
        def documents = OpenapiArgsParser.parse(['generate-openapi', '--verbose=true', '--title=API'])

        then:
        documents.size() == 1
        documents[0].title == 'API'
    }
}
