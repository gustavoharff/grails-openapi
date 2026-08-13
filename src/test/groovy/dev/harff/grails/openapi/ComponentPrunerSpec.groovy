package dev.harff.grails.openapi

import spock.lang.Specification

class ComponentPrunerSpec extends Specification {

    def "drops a schema no path references"() {
        given:
        Map schemas = [
            Used  : [type: 'object', properties: [name: [type: 'string']]],
            Unused: [type: 'object', properties: [name: [type: 'string']]],
        ]
        Map paths = pathWithResponseRef('Used')

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, paths)

        then:
        pruned.keySet() == ['Used'] as Set
    }

    def "keeps a schema referenced only through a request body"() {
        given:
        Map schemas = [CreateCommand: [type: 'object'], Other: [type: 'object']]
        Map paths = [
            '/orders': [post: [
                requestBody: [content: ['application/json': [schema: ref('CreateCommand')]]],
                responses  : ['204': [description: 'No Content']],
            ]]
        ]

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, paths)

        then:
        pruned.keySet() == ['CreateCommand'] as Set
    }

    def "keeps a schema reachable only through a nested ref"() {
        given:
        Map schemas = [
            Order   : [type: 'object', properties: [customer: ref('Customer')]],
            Customer: [type: 'object', properties: [address: ref('Address')]],
            Address : [type: 'object', properties: [city: [type: 'string']]],
            Unused  : [type: 'object'],
        ]
        Map paths = pathWithResponseRef('Order')

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, paths)

        then:
        pruned.keySet() == ['Order', 'Customer', 'Address'] as Set
    }

    def "follows refs through array items"() {
        given:
        Map schemas = [
            Page: [type: 'object', properties: [items: [type: 'array', items: ref('Item')]]],
            Item: [type: 'object'],
        ]
        Map paths = pathWithResponseRef('Page')

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, paths)

        then:
        pruned.keySet() == ['Page', 'Item'] as Set
    }

    def "follows refs through allOf, oneOf and anyOf"() {
        given:
        Map schemas = [
            Root : [allOf: [ref('Base')], properties: [
                either: [oneOf: [ref('Left')]],
                any   : [anyOf: [ref('Right')]],
            ]],
            Base : [type: 'object'],
            Left : [type: 'object'],
            Right: [type: 'object'],
            Gone : [type: 'object'],
        ]
        Map paths = pathWithResponseRef('Root')

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, paths)

        then:
        pruned.keySet() == ['Root', 'Base', 'Left', 'Right'] as Set
    }

    def "follows refs through additionalProperties"() {
        given:
        Map schemas = [
            Bag  : [type: 'object', additionalProperties: ref('Value')],
            Value: [type: 'object'],
        ]
        Map paths = pathWithResponseRef('Bag')

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, paths)

        then:
        pruned.keySet() == ['Bag', 'Value'] as Set
    }

    def "survives a circular reference"() {
        given:
        Map schemas = [
            Node: [type: 'object', properties: [parent: ref('Node'), leaf: ref('Leaf')]],
            Leaf: [type: 'object', properties: [owner: ref('Node')]],
        ]
        Map paths = pathWithResponseRef('Node')

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, paths)

        then:
        pruned.keySet() == ['Node', 'Leaf'] as Set
    }

    def "drops every schema when no path is emitted"() {
        given:
        Map schemas = [Order: [type: 'object']]

        when:
        def pruned = ComponentPruner.pruneSchemas(schemas, [:])

        then:
        pruned.isEmpty()
    }

    def "keeps a security scheme an operation inherits from the document"() {
        given:
        Map paths = ['/orders': [get: [operationId: 'getOrders']]]

        when:
        def pruned = ComponentPruner.pruneSecuritySchemes(
            [bearerAuth: [type: 'http']], [[bearerAuth: []]], paths)

        then:
        pruned.keySet() == ['bearerAuth'] as Set
    }

    def "drops a security scheme when every operation opts out"() {
        given:
        Map paths = ['/public/v1/comments': [get: [operationId: 'getComments', security: []]]]

        when:
        def pruned = ComponentPruner.pruneSecuritySchemes(
            [bearerAuth: [type: 'http']], [[bearerAuth: []]], paths)

        then:
        pruned.isEmpty()
    }

    def "keeps a scheme when at least one operation still requires it"() {
        given:
        Map paths = [
            '/public/v1/comments': [get: [operationId: 'getComments', security: []]],
            '/orders'            : [get: [operationId: 'getOrders']],
        ]

        when:
        def pruned = ComponentPruner.pruneSecuritySchemes(
            [bearerAuth: [type: 'http']], [[bearerAuth: []]], paths)

        then:
        pruned.keySet() == ['bearerAuth'] as Set
    }

    def "keeps a scheme an operation names explicitly"() {
        given:
        Map paths = ['/orders': [get: [operationId: 'getOrders', security: [[apiKey: []]]]]]

        when:
        def pruned = ComponentPruner.pruneSecuritySchemes(
            [bearerAuth: [type: 'http'], apiKey: [type: 'apiKey']], [[bearerAuth: []]], paths)

        then:
        pruned.keySet() == ['apiKey'] as Set
    }

    // ---- Helpers ----

    private static Map ref(String name) {
        return ['$ref': "#/components/schemas/${name}".toString()]
    }

    private static Map pathWithResponseRef(String schemaName) {
        return ['/things': [get: [
            operationId: 'getThings',
            responses  : ['200': [
                description: 'Success',
                content    : ['application/json': [schema: ref(schemaName)]],
            ]],
        ]]]
    }
}
