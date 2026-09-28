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
        schema.get('properties').name == [type: 'string']
        schema.get('properties').age == [type: 'integer', format: 'int32']
    }

    def "buildObjectSchema excludes static fields"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(SimpleBean)

        then:
        !schema.get('properties').containsKey('CONSTANT')
    }

    def "buildObjectSchema excludes fields without a public getter"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(BeanWithPrivateField)

        then:
        schema.get('properties').containsKey('visible')
        !schema.get('properties').containsKey('hidden')
    }

    def "buildObjectSchema traverses superclass fields"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(ChildBean)

        then:
        schema.get('properties').containsKey('name')
        schema.get('properties').containsKey('extra')
    }

    def "buildObjectSchema does not duplicate superclass fields in child"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(ChildBean)

        then:
        schema.get('properties').keySet().count { it == 'name' } == 1
    }

    def "buildObjectSchema stops at Object boundary"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(SimpleBean)

        then:
        !schema.get('properties').containsKey('class')
    }

    // --------------- nullable property detection ---------------

    def "buildObjectSchema marks field with @Nullable getter as nullable"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(BeanWithNullableGetter)

        then:
        schema.get('properties').optional.nullable == true
    }

    def "buildObjectSchema does not mark field without @Nullable as nullable"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(BeanWithNullableGetter)

        then:
        !schema.get('properties').required.containsKey('nullable')
    }

    def "buildObjectSchema does not add required list for plain Java/Groovy classes"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(SimpleBean)

        then:
        !schema.containsKey('required')
    }

    def "buildObjectSchema with typeBindings resolves TypeVariable field"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(GenericBean, [T: String])

        then:
        schema.get('properties').value == [type: 'string']
    }

    def "buildObjectSchema with typeBindings resolves List of TypeVariable field"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(GenericBean, [T: Integer])

        then:
        schema.get('properties').items.type == 'array'
        schema.get('properties').items.items == [type: 'integer', format: 'int32']
    }

    def "buildObjectSchema without typeBindings treats TypeVariable as object"() {
        when:
        def schema = SchemaBuilder.buildObjectSchema(GenericBean)

        then:
        schema.get('properties').value == [type: 'object']
    }

    def "buildObjectSchema with schemas registry generates refs and registers nested complex types"() {
        given:
        Map<String, Map> schemas = [:]

        when:
        def schema = SchemaBuilder.buildObjectSchema(GenericBean, [T: SimpleBean], schemas)

        then:
        schema.get('properties').value == ['$ref': '#/components/schemas/SimpleBean']
        schema.get('properties').items == [type: 'array', items: ['$ref': '#/components/schemas/SimpleBean']]
        schemas.containsKey('SimpleBean')
        schemas['SimpleBean'].type == 'object'
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
        schema.get('properties').containsKey('email')
        schema.get('properties').containsKey('age')
    }

    def "buildCommandSchema maps field types correctly"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(SimpleCommand)

        then:
        schema.get('properties').email == [type: 'string']
        schema.get('properties').age == [type: 'integer', format: 'int32']
    }

    def "buildCommandSchema handles missing constrainedProperties gracefully"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(SimpleCommand)

        then:
        notThrown(Exception)
        schema != null
    }

    def "buildCommandSchema marks non-nullable properties as required"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(ConstrainedCommand)

        then:
        schema.required == ['color']
    }

    def "buildCommandSchema maps inList and maxSize constraints"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(ConstrainedCommand)

        then:
        schema.get('properties').color['enum'] == ['red', 'blue']
        schema.get('properties').label.maxLength == 20
    }

    def "buildCommandSchema maps min and max constraints"() {
        when:
        def schema = SchemaBuilder.buildCommandSchema(ConstrainedCommand)

        then:
        schema.get('properties').count.minimum == 1
        schema.get('properties').count.maximum == 100
    }

    // --------------- constraintsOf ---------------

    def "constraintsOf reads the Validateable constraints map"() {
        when:
        def constraints = SchemaBuilder.constraintsOf(ConstrainedCommand)

        then:
        constraints.keySet() == ['color', 'label', 'count'] as Set
        !constraints.color.nullable
        constraints.label.nullable
    }

    def "constraintsOf returns an empty map for a class without constraints"() {
        expect:
        SchemaBuilder.constraintsOf(SimpleBean) == [:]
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
        schema.get('properties').id == [type: 'integer', format: 'int64']
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
        schema.get('properties').title == [type: 'string']
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
        schema.get('properties').tags == [type: 'array', items: [type: 'object']]
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
        schema.get('properties').categories == [type: 'array', items: [type: 'object']]
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
        schema.get('properties').author == [type: 'object']
    }

    def "buildDomainSchema handles null identifier gracefully"() {
        given:
        def mockDomain = [identifier: null, persistentProperties: []]

        when:
        def schema = SchemaBuilder.buildDomainSchema(mockDomain)

        then:
        notThrown(Exception)
        !schema.get('properties').containsKey('id')
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

    static class ConstrainedCommand implements Validateable {
        String color
        String label
        Integer count

        String getColor() { color }
        String getLabel() { label }
        Integer getCount() { count }

        static constraints = {
            color(nullable: false, inList: ['red', 'blue'])
            label(nullable: true, maxSize: 20)
            count(nullable: true, min: 1, max: 100)
        }
    }

    static class GenericBean<T> {
        T value
        List<T> items

        T getValue() { value }
        List<T> getItems() { items }
    }

    static class BeanWithNullableGetter {
        String required
        String optional

        String getRequired() { required }

        // @org.springframework.lang.Nullable has RUNTIME retention, so it's
        // visible via reflection — unlike @org.jetbrains.annotations.Nullable
        // which has CLASS retention only.
        @org.springframework.lang.Nullable
        String getOptional() { optional }
    }
}
