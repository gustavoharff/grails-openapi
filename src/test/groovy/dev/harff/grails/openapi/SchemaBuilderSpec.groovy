package dev.harff.grails.openapi

import grails.validation.Validateable
import spock.lang.Specification

class SchemaBuilderSpec extends Specification {

    // --------------- buildObjectSchema ---------------

    def "buildObjectSchema returns object type"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(SimpleBean)

        then:
        schema.type == 'object'
    }

    def "buildObjectSchema includes fields that have public getters"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(SimpleBean)

        then:
        schema.properties.name == [type: 'string']
        schema.properties.age == [type: 'integer', format: 'int32']
    }

    def "buildObjectSchema excludes static fields"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(SimpleBean)

        then:
        !schema.properties.containsKey('CONSTANT')
    }

    def "buildObjectSchema excludes fields without a public getter"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(BeanWithPrivateField)

        then:
        schema.properties.containsKey('visible')
        !schema.properties.containsKey('hidden')
    }

    def "buildObjectSchema traverses superclass fields"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(ChildBean)

        then:
        schema.properties.containsKey('name')
        schema.properties.containsKey('extra')
    }

    def "buildObjectSchema does not duplicate superclass fields in child"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(ChildBean)

        then:
        schema.properties.keySet().count { it == 'name' } == 1
    }

    def "buildObjectSchema stops at Object boundary"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(SimpleBean)

        then:
        !schema.properties.containsKey('class')
    }

    def "buildObjectSchema with typeBindings resolves TypeVariable field"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(GenericBean, [T: String])

        then:
        schema.properties.value == [type: 'string']
    }

    def "buildObjectSchema with typeBindings resolves List of TypeVariable field"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(GenericBean, [T: Integer])

        then:
        schema.properties.items.type == 'array'
        schema.properties.items.items == [type: 'integer', format: 'int32']
    }

    def "buildObjectSchema without typeBindings treats TypeVariable as object"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(GenericBean)

        then:
        schema.properties.value == [type: 'object']
    }

    // --------------- buildCommandSchema ---------------

    def "buildCommandSchema returns object type"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(SimpleCommand)

        then:
        schema.type == 'object'
    }

    def "buildCommandSchema includes Validateable fields with getters"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(SimpleCommand)

        then:
        schema.properties.containsKey('email')
        schema.properties.containsKey('age')
    }

    def "buildCommandSchema maps field types correctly"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(SimpleCommand)

        then:
        schema.properties.email == [type: 'string']
        schema.properties.age == [type: 'integer', format: 'int32']
    }

    def "buildCommandSchema handles missing constrainedProperties gracefully"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(SimpleCommand)

        then:
        notThrown(Exception)
        schema != null
    }

    // --------------- buildDomainSchema ---------------

    def "buildDomainSchema returns object type"() {
        given:
        def mockDomain = [identifier: null, persistentProperties: []]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        schema.type == 'object'
    }

    def "buildDomainSchema includes identifier property"() {
        given:
        def mockDomain = [
            identifier         : [name: 'id', type: Long],
            persistentProperties: []
        ]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        schema.properties.id == [type: 'integer', format: 'int64']
    }

    def "buildDomainSchema includes string persistent property"() {
        given:
        def mockDomain = [
            identifier         : null,
            persistentProperties: [
                [name: 'title', type: String, association: false]
            ]
        ]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        schema.properties.title == [type: 'string']
    }

    def "buildDomainSchema maps one-to-many association to array"() {
        given:
        def mockDomain = [
            identifier         : null,
            persistentProperties: [
                [name: 'tags', type: List, association: true, oneToMany: true, manyToMany: false]
            ]
        ]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        schema.properties.tags == [type: 'array', items: [type: 'object']]
    }

    def "buildDomainSchema maps many-to-many association to array"() {
        given:
        def mockDomain = [
            identifier         : null,
            persistentProperties: [
                [name: 'categories', type: Set, association: true, oneToMany: false, manyToMany: true]
            ]
        ]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        schema.properties.categories == [type: 'array', items: [type: 'object']]
    }

    def "buildDomainSchema maps many-to-one association to object"() {
        given:
        def mockDomain = [
            identifier         : null,
            persistentProperties: [
                [name: 'author', type: Object, association: true, oneToMany: false, manyToMany: false]
            ]
        ]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        schema.properties.author == [type: 'object']
    }

    def "buildDomainSchema handles null identifier gracefully"() {
        given:
        def mockDomain = [identifier: null, persistentProperties: []]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        notThrown(Exception)
        !schema.properties.containsKey('id')
    }

    def "buildDomainSchema handles null persistentProperties gracefully"() {
        given:
        def mockDomain = [identifier: null, persistentProperties: null]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        notThrown(Exception)
        schema.type == 'object'
    }

    // ---- Test fixture classes ----

    static class SimpleBean {
        static final String CONSTANT = 'value'
        String name
        int age

        String getName() { name }
        int getAge() { age }
    }

    static class BeanWithPrivateField {
        private String hidden
        String visible

        String getVisible() { visible }
    }

    static class ParentBean {
        String name
        String getName() { name }
    }

    static class ChildBean extends ParentBean {
        String extra
        String getExtra() { extra }
    }

    static class SimpleCommand implements Validateable {
        String email
        int age

        String getEmail() { email }
        int getAge() { age }
    }

    static class GenericBean<T> {
        T value
        List<T> items

        T getValue() { value }
        List<T> getItems() { items }
    }
}
