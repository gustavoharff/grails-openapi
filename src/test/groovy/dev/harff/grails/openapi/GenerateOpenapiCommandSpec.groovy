package dev.harff.grails.openapi

import grails.core.GrailsApplication
import grails.dev.commands.ExecutionContext
import org.grails.build.parsing.CommandLineParser
import org.grails.core.DefaultGrailsControllerClass
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification
import spock.lang.TempDir
import org.yaml.snakeyaml.Yaml

class GenerateOpenapiCommandSpec extends Specification {

    @TempDir
    File tempDir

    GrailsApplication grailsApplication = Mock()

    def setup() {
        grailsApplication.getArtefacts('Controller') >> []
        grailsApplication.getArtefact('Domain', _) >> null
    }

    def "reads the documents from the command line Grails parsed"() {
        given:
        def command = commandFor('generate-openapi --document=public --title="Public API" --include=/public/v1/**')

        when:
        def documents = OpenapiArgsParser.parse(command.commandArguments())

        then:
        documents.size() == 1
        documents[0].name == 'public'
        documents[0].title == 'Public API'
        documents[0].includePaths == ['/public/v1/**']
    }

    def "falls back to the default document when nothing was passed"() {
        given:
        def command = commandFor('generate-openapi')

        when:
        def documents = OpenapiArgsParser.parse(command.commandArguments())

        then:
        documents.size() == 1
        documents[0].name == 'default'
    }

    def "survives a context that leaves the raw arguments unset"() {
        expect:
        new GenerateOpenapiCommand().commandArguments() == []
    }

    def "writes one file per document, each scoped to its own paths"() {
        given:
        def ctrl = mockController(CommentsController, 'comments')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'comments') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Comments') >> null

        def holder = [urlMappings: [
            mapping('/public/v1/comments', 'GET', 'index', 'comments'),
            mapping('/internal/comments', 'GET', 'show', 'comments'),
        ]]

        File internalFile = new File(tempDir, 'openapi.yaml')
        File publicFile = new File(tempDir, 'openapi-public.yaml')

        def command = commandFor("generate-openapi" +
            " --title=\"Internal API\" --version=1.16.0 --output=${internalFile.path}" +
            " --document=public --title=\"Public API\" --version=1.0.0" +
            " --server=https://api.example.com --include=/public/v1/** --output=${publicFile.path}")
        command.grailsApplication = grailsApplication
        command.applicationContext = Mock(ConfigurableApplicationContext) {
            getBean('grailsUrlMappingsHolder') >> holder
        }

        when:
        boolean handled = command.handle()

        then:
        handled

        and: 'the internal document describes the whole application'
        def internalDoc = new Yaml().load(internalFile.text)
        internalDoc.info == [title: 'Internal API', version: '1.16.0']
        internalDoc.servers == [[url: '/']]
        internalDoc.paths.keySet() == ['/internal/comments', '/public/v1/comments'] as Set

        and: 'the public one only its own slice'
        def publicDoc = new Yaml().load(publicFile.text)
        publicDoc.info == [title: 'Public API', version: '1.0.0']
        publicDoc.servers == [[url: 'https://api.example.com']]
        publicDoc.paths.keySet() == ['/public/v1/comments'] as Set
    }

    // ---- Helpers ----

    private static GenerateOpenapiCommand commandFor(String commandLine) {
        def command = new GenerateOpenapiCommand()
        command.executionContext = new ExecutionContext(
            new CommandLineParser().parse(CommandLineParser.translateCommandline(commandLine)))
        return command
    }

    private DefaultGrailsControllerClass mockController(Class<?> clazz, String logicalName) {
        def ctrl = Mock(DefaultGrailsControllerClass)
        ctrl.clazz >> clazz
        ctrl.logicalPropertyName >> logicalName
        return ctrl
    }

    private static Map mapping(String pattern, String method, String action, String controller) {
        return [
            urlData       : [urlPattern: pattern],
            httpMethod    : method,
            actionName    : action,
            controllerName: controller,
            constraints   : [],
        ]
    }

    static class CommentsController {
        void index() {}
        void show() {}
    }
}
