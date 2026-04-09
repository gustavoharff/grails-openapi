package dev.harff.grails.openapi.integration

import dev.harff.grails.openapi.ApiIgnore
import dev.harff.grails.openapi.ApiPublic
import dev.harff.grails.openapi.ApiTag
import dev.harff.grails.openapi.Description
import dev.harff.grails.openapi.OpenApiDocumentAssembler
import grails.core.GrailsApplication
import grails.validation.Validateable
import org.grails.core.DefaultGrailsControllerClass
import spock.lang.Specification

/**
 * Integration test for OpenApiDocumentAssembler.
 *
 * Wires the full assembly pipeline (UrlMappingResolver → ControllerIntrospector →
 * SchemaBuilder → ResponseResolver → SecurityResolver) using mock Grails application
 * objects. Verifies end-to-end document shape without requiring a running Grails context.
 */
class OpenApiDocumentAssemblerIntegrationSpec extends Specification {

    GrailsApplication grailsApplication = Mock()
    OpenApiDocumentAssembler assembler

    def setup() {
        assembler = new OpenApiDocumentAssembler(grailsApplication: grailsApplication)
        grailsApplication.getArtefacts('Controller') >> []
        grailsApplication.getArtefact('Domain', _) >> null
        grailsApplication.allClasses >> []
    }

    def "assembled document has required OpenAPI 3.0.3 structure"() {
        given:
        def holder = [urlMappings: []]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.openapi == '3.0.3'
        doc.info != null
        doc.servers != null
        doc.paths != null
        doc.components != null
    }

    def "document includes global bearerAuth security requirement"() {
        given:
        def holder = [urlMappings: []]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.security == [[bearerAuth: []]]
    }

    def "document includes bearerAuth security scheme definition"() {
        given:
        def holder = [urlMappings: []]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.components.securitySchemes.bearerAuth != null
        doc.components.securitySchemes.bearerAuth.type == 'http'
    }

    def "paths are empty when no URL mappings resolve to endpoints"() {
        given:
        def holder = [urlMappings: []]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths.isEmpty()
    }

    def "endpoint with @ApiIgnore is excluded from paths"() {
        given:
        def ctrl = mockController(IgnoredController, 'ignored')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'ignored') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Ignored') >> null

        def holder = [urlMappings: [
            concreteMapping('/ignored', 'GET', 'index', 'ignored')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        !doc.paths.containsKey('/ignored')
    }

    def "endpoint with no special annotations produces minimal operation object"() {
        given:
        def ctrl = mockController(SimpleController, 'simple')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'simple') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Simple') >> null

        def holder = [urlMappings: [
            concreteMapping('/simple', 'GET', 'index', 'simple')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/simple'] != null
        def operation = doc.paths['/simple'].get
        operation != null
        operation.tags != null
        operation.operationId != null
        operation.responses != null
    }

    def "operationId is derived from HTTP method and path segments"() {
        given:
        def ctrl = mockController(SimpleController, 'simple')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'simple') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Simple') >> null

        def holder = [urlMappings: [
            concreteMapping('/simple', 'GET', 'index', 'simple')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/simple'].get.operationId == 'getSimple'
    }

    def "operationId capitalises each path segment"() {
        given:
        def ctrl = mockController(SimpleController, 'user')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'user') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'User') >> null

        def holder = [urlMappings: [
            concreteMapping('/admin/users', 'GET', 'index', 'user')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/admin/users'].get.operationId == 'getAdminUsers'
    }

    def "operationId converts path parameter to ById suffix"() {
        given:
        def ctrl = mockController(SimpleController, 'user')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'user') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'User') >> null

        def holder = [urlMappings: [
            concreteMapping('/admin/users/(.*)', 'PUT', 'update', 'user', [constraint('id')])
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/admin/users/{id}'].put.operationId == 'putAdminUsersById'
    }

    def "operationId handles multiple path parameters"() {
        given:
        def ctrl = mockController(SimpleController, 'order')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'order') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Order') >> null

        def holder = [urlMappings: [
            concreteMapping('/users/(.*)/(.*)', 'GET', 'show', 'order', [constraint('userId'), constraint('orderId')])
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/users/{userId}/{orderId}'].get.operationId == 'getUsersByUserIdByOrderId'
    }

    def "operationId handles hyphenated path segments"() {
        given:
        def ctrl = mockController(SimpleController, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null

        def holder = [urlMappings: [
            concreteMapping('/some-resource', 'DELETE', 'delete', 'item')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/some-resource'].delete.operationId == 'deleteSomeResource'
    }

    def "endpoint with @Description has summary in operation"() {
        given:
        def ctrl = mockController(DescribedController, 'described')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'described') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Described') >> null

        def holder = [urlMappings: [
            concreteMapping('/described', 'GET', 'listAll', 'described')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/described'].get.summary == 'List everything'
    }

    def "endpoint with @ApiTag uses the specified tag"() {
        given:
        def ctrl = mockController(TaggedController, 'tagged')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'tagged') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Tagged') >> null

        def holder = [urlMappings: [
            concreteMapping('/tagged', 'GET', 'index', 'tagged')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/tagged'].get.tags == ['CustomTag']
    }

    def "endpoint without @ApiTag derives tag from controller name"() {
        given:
        def ctrl = mockController(SimpleController, 'myProduct')
        ctrl.logicalPropertyName >> 'myProduct'
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'myProduct') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'MyProduct') >> null

        def holder = [urlMappings: [
            concreteMapping('/my-product', 'GET', 'index', 'myProduct')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        def path = doc.paths.find { it.value.get != null }
        path.value.get.tags[0] != null
    }

    def "@ApiPublic endpoint has empty security list in operation"() {
        given:
        def ctrl = mockController(PublicController, 'public')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'public') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Public') >> null

        def holder = [urlMappings: [
            concreteMapping('/public', 'GET', 'open', 'public')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/public'].get.security == []
    }

    def "private endpoint does not have security key (inherits global)"() {
        given:
        def ctrl = mockController(SimpleController, 'simple')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'simple') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Simple') >> null

        def holder = [urlMappings: [
            concreteMapping('/simple', 'GET', 'index', 'simple')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        !doc.paths['/simple'].get.containsKey('security')
    }

    def "deprecated endpoint has deprecated flag in operation"() {
        given:
        def ctrl = mockController(DeprecatedController, 'deprecated')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'deprecated') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Deprecated') >> null

        def holder = [urlMappings: [
            concreteMapping('/deprecated', 'GET', 'oldAction', 'deprecated')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/deprecated'].get.deprecated == true
    }

    def "path parameter in URL pattern appears in operation parameters"() {
        given:
        def ctrl = mockController(SimpleController, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null

        def holder = [urlMappings: [
            concreteMapping('/items/(.*)', 'GET', 'show', 'item', [constraint('id')])
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        def operation = doc.paths['/items/{id}'].get
        def pathParam = operation.parameters.find { it.name == 'id' && it.in == 'path' }
        pathParam != null
        pathParam.required == true
    }

    def "POST with Validateable command class produces requestBody in operation"() {
        given:
        def ctrl = mockController(CommandController, 'command')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'command') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Command') >> null

        def holder = [urlMappings: [
            concreteMapping('/command', 'POST', 'save', 'command')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        def operation = doc.paths['/command'].post
        operation.requestBody != null
        operation.requestBody.required == true
        operation.requestBody.content.'application/json' != null
    }

    def "POST with Validateable command class registers schema in components"() {
        given:
        def ctrl = mockController(CommandController, 'command')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'command') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Command') >> null

        def holder = [urlMappings: [
            concreteMapping('/command', 'POST', 'save', 'command')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.components.schemas.containsKey('CreateCommand')
    }

    def "GET with Validateable command class produces query parameters, not requestBody"() {
        given:
        def ctrl = mockController(CommandController, 'command')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'command') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Command') >> null

        def holder = [urlMappings: [
            concreteMapping('/command', 'GET', 'search', 'command')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        def operation = doc.paths['/command'].get
        operation.requestBody == null
        operation.parameters != null
        operation.parameters.any { it.in == 'query' }
    }

    def "DELETE endpoint returns 204 response"() {
        given:
        def ctrl = mockController(SimpleController, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null

        def holder = [urlMappings: [
            concreteMapping('/items/{id}', 'DELETE', 'delete', 'item')
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.paths['/items/{id}'].delete.responses['204'] != null
    }

    def "paths are sorted alphabetically in output document"() {
        given:
        def ctrl = mockController(SimpleController, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'b') >> null
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'B') >> null

        def ctrlB = mockController(SimpleController, 'b')
        ctrlB.logicalPropertyName >> 'b'
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'b') >> ctrlB

        def holder = [urlMappings: [
            concreteMapping('/z-items', 'GET', 'index', 'item'),
            concreteMapping('/a-items', 'GET', 'index', 'b'),
        ]]

        when:
        def doc = assembler.assemble(holder)
        def keys = doc.paths.keySet().toList()

        then:
        keys.indexOf('/a-items') < keys.indexOf('/z-items')
    }

    def "two commands with same simple name get qualified schema names"() {
        given:
        def ctrl = mockController(TwoCommandsController, 'multi')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'multi') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Multi') >> null

        def holder = [urlMappings: [
            concreteMapping('/multi/a', 'POST', 'saveA', 'multi'),
            concreteMapping('/multi/b', 'POST', 'saveB', 'multi'),
        ]]

        when:
        def doc = assembler.assemble(holder)

        then:
        doc.components.schemas.size() >= 2
        // Both schema names must be distinct
        def schemaNames = doc.components.schemas.keySet()
        schemaNames.toList().unique().size() == schemaNames.size()
    }

    // ---- Helpers ----

    private DefaultGrailsControllerClass mockController(Class<?> clazz, String logicalName) {
        def ctrl = Mock(DefaultGrailsControllerClass)
        ctrl.clazz >> clazz
        ctrl.logicalPropertyName >> logicalName
        return ctrl
    }

    private static def concreteMapping(String pattern, String method, String action, String controller,
                                       List constraints = []) {
        [
            urlData       : [urlPattern: pattern],
            httpMethod    : method,
            actionName    : action,
            controllerName: controller,
            constraints   : constraints,
        ]
    }

    private static def constraint(String name) {
        [propertyName: name]
    }

    // ---- Test controllers ----

    @ApiIgnore
    static class IgnoredController {
        void index() {}
    }

    static class SimpleController {
        void index() {}
        void show() {}
        void save() {}
        void update() {}
        void delete() {}
    }

    static class DescribedController {
        @Description('List everything')
        void listAll() {}
    }

    @ApiTag('CustomTag')
    static class TaggedController {
        void index() {}
    }

    @ApiPublic
    static class PublicController {
        void open() {}
    }

    static class DeprecatedController {
        @Deprecated
        void oldAction() {}
    }

    static class CreateCommand implements Validateable {
        String name
        String getName() { name }
    }

    static class CommandController {
        void save(CreateCommand cmd) {}
        void search(CreateCommand cmd) {}
    }

    static class TwoCommandsController {
        void saveA(PackageA.CreateCommand cmd) {}
        void saveB(PackageB.CreateCommand cmd) {}
    }

    // Simulate two commands with identical simple names in different packages
    static class PackageA {
        static class CreateCommand implements Validateable {
            String fieldA
            String getFieldA() { fieldA }
        }
    }

    static class PackageB {
        static class CreateCommand implements Validateable {
            String fieldB
            String getFieldB() { fieldB }
        }
    }
}
