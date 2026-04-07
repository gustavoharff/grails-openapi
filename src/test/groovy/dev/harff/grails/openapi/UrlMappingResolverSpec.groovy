package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.ResolvedEndpoint
import grails.core.GrailsApplication
import org.grails.core.DefaultGrailsControllerClass
import spock.lang.Specification
import spock.lang.Unroll

class UrlMappingResolverSpec extends Specification {

    GrailsApplication grailsApplication = Mock()
    UrlMappingResolver resolver

    def setup() {
        resolver = new UrlMappingResolver(grailsApplication: grailsApplication)
        grailsApplication.getArtefacts('Controller') >> []
    }

    def "returns empty list when no URL mappings"() {
        given:
        def holder = [urlMappings: []]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.isEmpty()
    }

    def "skips mapping with no HTTP method"() {
        given:
        def holder = [urlMappings: [
            mapping('/items', null, 'index', 'item')
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.isEmpty()
    }

    def "skips mapping with httpMethod == ANY"() {
        given:
        def holder = [urlMappings: [
            mapping('/items', 'ANY', 'index', 'item')
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.isEmpty()
    }

    def "skips mapping with no action name"() {
        given:
        def ctrl = mockController(PlainAction, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl

        def holder = [urlMappings: [
            mapping('/items', 'GET', null, 'item')
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.isEmpty()
    }

    def "skips catch-all pattern with both \$controller and \$action"() {
        given:
        def holder = [urlMappings: [
            mapping('/\$controller/\$action?/\$id?', 'GET', 'index', null)
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.isEmpty()
    }

    def "resolves simple concrete mapping to endpoint"() {
        given:
        def ctrl = mockController(PlainAction, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null

        def holder = [urlMappings: [
            concreteMapping('/items', 'GET', 'index', 'item')
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.size() == 1
        results[0].httpMethod == 'GET'
        results[0].actionName == 'index'
        results[0].controllerName == 'item'
    }

    def "skips mapping when controller artefact not found"() {
        given:
        grailsApplication.getArtefactByLogicalPropertyName('Controller', _) >> null

        def holder = [urlMappings: [
            concreteMapping('/missing', 'GET', 'index', 'nonExistent')
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.isEmpty()
    }

    def "de-duplicates endpoints with same HTTP method and path"() {
        given:
        def ctrl = mockController(PlainAction, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null

        def holder = [urlMappings: [
            concreteMapping('/items', 'GET', 'index', 'item'),
            concreteMapping('/items', 'GET', 'index', 'item'),
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.size() == 1
    }

    def "converts controller name to kebab-case in path"() {
        given:
        def ctrl = mockController(PlainAction, 'myProduct')
        ctrl.logicalPropertyName >> 'myProduct'
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'myProduct') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'MyProduct') >> null

        def holder = [urlMappings: [
            concreteMapping('/my-product', 'GET', 'index', 'myProduct')
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.size() == 1
        results[0].path.contains('my-product')
    }

    def "extracts path parameter from pattern"() {
        given:
        def ctrl = mockController(PlainAction, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null

        def holder = [urlMappings: [
            concreteMapping('/items/(.*)', 'GET', 'show', 'item', [constraint('id')])
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.size() == 1
        results[0].path.contains('{id}')
        results[0].pathParams.contains('id')
    }

    @Unroll
    def "HTTP method #method is preserved on resolved endpoint"() {
        given:
        def ctrl = mockController(PlainAction, 'item')
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'item') >> ctrl
        grailsApplication.getArtefactByLogicalPropertyName('Controller', 'Item') >> null

        def holder = [urlMappings: [
            concreteMapping('/items', method, action, 'item')
        ]]

        when:
        def results = resolver.resolveAll(holder)

        then:
        results.size() == 1
        results[0].httpMethod == method

        where:
        method  | action
        'GET'   | 'index'
        'POST'  | 'save'
        'PUT'   | 'update'
        'DELETE'| 'delete'
        'PATCH' | 'patch'
    }

    // ---- Helpers ----

    private DefaultGrailsControllerClass mockController(Class<?> clazz, String logicalName) {
        def ctrl = Mock(DefaultGrailsControllerClass)
        ctrl.clazz >> clazz
        ctrl.logicalPropertyName >> logicalName
        return ctrl
    }

    /** A mapping with urlData.urlPattern (simple literal, no regex groups) */
    private static def mapping(String pattern, String method, String action, String controller, List constraints = []) {
        [
            urlData   : [urlPattern: pattern],
            httpMethod: method,
            actionName: action,
            controllerName: controller,
            constraints: constraints,
        ]
    }

    /** A concrete mapping (has controllerName) with a literal URL pattern */
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

    static class PlainAction {
        void index() {}
        void show() {}
        void save() {}
        void update() {}
        void delete() {}
        void patch() {}
    }
}
