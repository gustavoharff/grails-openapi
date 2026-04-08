package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.EndpointInfo
import dev.harff.grails.openapi.model.ResolvedEndpoint
import grails.core.GrailsApplication
import grails.core.GrailsClass
import spock.lang.Specification

class ResponseResolverSpec extends Specification {

    GrailsApplication grailsApplication = Mock()
    Map<String, Map> schemas = [:]
    ResponseResolver resolver

    def setup() {
        resolver = new ResponseResolver(
            grailsApplication: grailsApplication,
            schemas: schemas
        )
    }

    def "DELETE method returns 204 No Content"() {
        given:
        def info = new EndpointInfo()
        def ep = ep('DELETE', 'delete', '/items/{id}', 'item')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses.size() == 1
        responses['204'] == [description: 'No Content']
    }

    def "action named 'delete' returns 204 No Content regardless of HTTP method"() {
        given:
        def info = new EndpointInfo()
        def ep = ep('POST', 'delete', '/items/{id}', 'item')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses['204'] == [description: 'No Content']
    }

    def "responsesOverride takes precedence over all other logic"() {
        given:
        def info = new EndpointInfo(responsesOverride: [
            [status: 422, description: 'Validation Error'],
            [status: 200, description: 'OK'],
        ])
        def ep = ep('POST', 'save', '/items', 'item')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses['422'] == [description: 'Validation Error']
        responses['200'] == [description: 'OK']
        !responses.containsKey('201')
    }

    def "responsesOverride takes precedence even for DELETE endpoints"() {
        given:
        def info = new EndpointInfo(responsesOverride: [[status: 200, description: 'Custom']])
        def ep = ep('DELETE', 'delete', '/items/{id}', 'item')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses['200'] == [description: 'Custom']
        !responses.containsKey('204')
    }

    def "falls back to generic 200 object response when no domain class found"() {
        given:
        def info = new EndpointInfo()
        def ep = ep('GET', 'index', '/unknown', 'unknown')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses['200'] != null
        responses['200'].content.'application/json'.schema == [type: 'object']
    }

    def "GET index returns 200 with array schema when domain class found"() {
        given:
        def domainArtefact = mockDomainArtefact('Product')
        grailsApplication.getArtefact('Domain', 'Product') >> domainArtefact

        def info = new EndpointInfo()
        def ep = ep('GET', 'index', '/products', 'product')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        def schema = responses['200'].content.'application/json'.schema
        schema.type == 'array'
        schema.items.'$ref'.contains('Product')
    }

    def "GET show returns 200 with single object schema ref when domain class found"() {
        given:
        def domainArtefact = mockDomainArtefact('Product')
        grailsApplication.getArtefact('Domain', 'Product') >> domainArtefact

        def info = new EndpointInfo()
        def ep = ep('GET', 'show', '/products/{id}', 'product')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        def schema = responses['200'].content.'application/json'.schema
        schema.'$ref'.contains('Product')
    }

    def "POST save returns 201 when domain class found"() {
        given:
        def domainArtefact = mockDomainArtefact('Order')
        grailsApplication.getArtefact('Domain', 'Order') >> domainArtefact

        def info = new EndpointInfo()
        def ep = ep('POST', 'save', '/orders', 'order')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses.containsKey('201')
        !responses.containsKey('200')
    }

    def "domain class lookup tries plural-to-singular name transformation"() {
        given:
        def domainArtefact = mockDomainArtefact('Category')
        grailsApplication.getArtefact('Domain', 'Categor') >> domainArtefact

        def info = new EndpointInfo()
        def ep = ep('GET', 'show', '/categories/{id}', 'categor')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses.containsKey('200')
    }

    def "responseType on EndpointInfo builds schema from plain class"() {
        given:
        def info = new EndpointInfo(responseType: String, responseIsList: false)
        def ep = ep('GET', 'show', '/items/{id}', 'item')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        responses['200'] != null
        def schema = responses['200'].content.'application/json'.schema
        schema.'$ref'.contains('String')
    }

    def "responseType with isList=true wraps schema in array"() {
        given:
        def info = new EndpointInfo(responseType: String, responseIsList: true)
        def ep = ep('GET', 'index', '/items', 'item')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        def schema = responses['200'].content.'application/json'.schema
        schema.type == 'array'
        schema.items.'$ref'.contains('String')
    }

    def "responseType with isList=true and domain class builds array schema"() {
        given:
        def domainArtefact = mockDomainArtefact('Widget')
        grailsApplication.getArtefact('Domain', 'String') >> domainArtefact

        def info = new EndpointInfo(responseType: String, responseIsList: true)
        def ep = ep('GET', 'index', '/widgets', 'widget')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        def schema = responses['200'].content.'application/json'.schema
        schema.type == 'array'
    }

    def "schema is cached after first resolution"() {
        given:
        def domainArtefact = mockDomainArtefact('Item')
        grailsApplication.getArtefact('Domain', 'Item') >> domainArtefact

        def info = new EndpointInfo()
        def ep1 = ep('GET', 'index', '/items', 'item')
        def ep2 = ep('GET', 'show', '/items/{id}', 'item')

        when:
        resolver.resolve(info, ep1)
        resolver.resolve(info, ep2)

        then:
        schemas.containsKey('Item')
    }

    def "wrapper response class is registered in schemas map"() {
        given:
        def info = new EndpointInfo(responseType: PagedResult, responseIsList: false)
        def ep = ep('GET', 'index', '/items', 'item')

        when:
        resolver.resolve(info, ep)

        then:
        schemas.containsKey('PagedResult')
    }

    def "wrapper response class schema has data and paging properties"() {
        given:
        def info = new EndpointInfo(responseType: PagedResult, responseIsList: false)
        def ep = ep('GET', 'index', '/items', 'item')

        when:
        resolver.resolve(info, ep)

        then:
        def schema = schemas['PagedResult']
        schema != null
        schema.properties.containsKey('data')
        schema.properties.containsKey('paging')
    }

    def "wrapper response class data property is typed as array"() {
        given:
        def info = new EndpointInfo(responseType: PagedResult, responseIsList: false)
        def ep = ep('GET', 'index', '/items', 'item')

        when:
        resolver.resolve(info, ep)

        then:
        schemas['PagedResult'].properties.data.type == 'array'
    }

    def "wrapper response class schema is referenced in response (not inlined as array)"() {
        given:
        def info = new EndpointInfo(responseType: PagedResult, responseIsList: false)
        def ep = ep('GET', 'index', '/items', 'item')

        when:
        def responses = resolver.resolve(info, ep)

        then:
        def schema = responses['200'].content.'application/json'.schema
        schema.'$ref'.contains('PagedResult')
        schema.type == null
    }

    // ---- Helpers ----

    private static ResolvedEndpoint ep(String method, String action, String path, String controller) {
        new ResolvedEndpoint(httpMethod: method, actionName: action, path: path, controllerName: controller)
    }

    private static GrailsClass mockDomainArtefact(String simpleName) {
        [getShortName: { simpleName }] as GrailsClass
    }

    // ---- Fixture classes ----

    static class PagedResult {
        List data
        Paging paging

        List getData() { data }
        Paging getPaging() { paging }
    }

    static class Paging {
        int total
        int page

        int getTotal() { total }
        int getPage() { page }
    }
}
