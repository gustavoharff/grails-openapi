package dev.harff.grails.openapi

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

class TypeMapperSpec extends Specification {

    @Unroll
    def "maps #type.simpleName to OpenAPI type '#expectedType'"() {
        expect:
        TypeMapper.toSchema(type).type == expectedType

        where:
        type          | expectedType
        String        | 'string'
        GString       | 'string'
        Integer       | 'integer'
        Long          | 'integer'
        Double        | 'number'
        Float         | 'number'
        Boolean       | 'boolean'
        BigDecimal    | 'number'
        BigInteger    | 'number'
        Instant       | 'string'
        Date          | 'string'
        LocalDateTime | 'string'
        ZonedDateTime | 'string'
        LocalDate     | 'string'
        Map           | 'object'
        Object        | 'object'
        List          | 'array'
        Set           | 'array'
    }

    @Unroll
    def "maps #type.simpleName to format '#expectedFormat'"() {
        expect:
        TypeMapper.toSchema(type).format == expectedFormat

        where:
        type          | expectedFormat
        Integer       | 'int32'
        Long          | 'int64'
        Double        | 'double'
        Float         | 'float'
        Instant       | 'date-time'
        Date          | 'date-time'
        LocalDateTime | 'date-time'
        ZonedDateTime | 'date-time'
        LocalDate     | 'date'
    }

    def "maps null type to object schema"() {
        expect:
        TypeMapper.toSchema(null) == [type: 'object']
    }

    def "maps primitive int to integer/int32"() {
        expect:
        TypeMapper.toSchema(int) == [type: 'integer', format: 'int32']
    }

    def "maps primitive long to integer/int64"() {
        expect:
        TypeMapper.toSchema(long) == [type: 'integer', format: 'int64']
    }

    def "maps primitive double to number/double"() {
        expect:
        TypeMapper.toSchema(double) == [type: 'number', format: 'double']
    }

    def "maps primitive float to number/float"() {
        expect:
        TypeMapper.toSchema(float) == [type: 'number', format: 'float']
    }

    def "maps primitive boolean to boolean"() {
        expect:
        TypeMapper.toSchema(boolean) == [type: 'boolean']
    }

    def "maps unparameterized List to array with object items"() {
        when:
        def schema = TypeMapper.toSchema(List)

        then:
        schema.type == 'array'
        schema.items == [type: 'object']
    }

    def "maps String array to array type"() {
        when:
        def schema = TypeMapper.toSchema(String[])

        then:
        schema.type == 'array'
    }

    def "resolves generic List<String> items type from field"() {
        given:
        def field = GenericHolder.getDeclaredField('names')

        when:
        def schema = TypeMapper.toSchema(field.type, field.genericType)

        then:
        schema.type == 'array'
        schema.items == [type: 'string']
    }

    def "resolves generic List<Integer> items type from field"() {
        given:
        def field = GenericHolder.getDeclaredField('numbers')

        when:
        def schema = TypeMapper.toSchema(field.type, field.genericType)

        then:
        schema.type == 'array'
        schema.items == [type: 'integer', format: 'int32']
    }

    def "resolves generic List<Long> items type from field"() {
        given:
        def field = GenericHolder.getDeclaredField('ids')

        when:
        def schema = TypeMapper.toSchema(field.type, field.genericType)

        then:
        schema.type == 'array'
        schema.items == [type: 'integer', format: 'int64']
    }

    def "falls back to object items when generic type not ParameterizedType"() {
        when:
        def schema = TypeMapper.toSchema(List, null)

        then:
        schema.type == 'array'
        schema.items == [type: 'object']
    }

    static class GenericHolder {
        List<String> names
        List<Integer> numbers
        List<Long> ids
    }
}
