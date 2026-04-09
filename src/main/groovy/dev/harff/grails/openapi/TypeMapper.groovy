package dev.harff.grails.openapi

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

class TypeMapper {

    static Map<String, Object> toSchema(Class<?> type, Type genericType = null, Map<String, Class<?>> typeBindings = [:]) {
        if (type == null) return [type: 'object']

        // Resolve a type variable (e.g. field declared as T) using the provided bindings
        if (genericType instanceof TypeVariable) {
            String varName = ((TypeVariable) genericType).name
            Class<?> bound = typeBindings[varName]
            if (bound) return toSchema(bound, null, typeBindings)
            return [type: 'object']
        }

        if (type == String || type == GString) return [type: 'string']
        if (type == Integer || type == int) return [type: 'integer', format: 'int32']
        if (type == Long || type == long) return [type: 'integer', format: 'int64']
        if (type == Double || type == double) return [type: 'number', format: 'double']
        if (type == Float || type == float) return [type: 'number', format: 'float']
        if (type == Boolean || type == boolean) return [type: 'boolean']
        if (type == BigDecimal || type == BigInteger) return [type: 'number']
        if (type == Instant || type == Date || type == LocalDateTime || type == ZonedDateTime) {
            return [type: 'string', format: 'date-time']
        }
        if (type == LocalDate) return [type: 'string', format: 'date']

        if (Collection.isAssignableFrom(type) || type.isArray()) {
            Map<String, Object> items = resolveItemsSchema(type, genericType, typeBindings)
            return [type: 'array', items: items]
        }

        if (Map.isAssignableFrom(type)) return [type: 'object']

        return [type: 'object']
    }

    private static Map<String, Object> resolveItemsSchema(Class<?> type, Type genericType, Map<String, Class<?>> typeBindings = [:]) {
        if (genericType instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) genericType).actualTypeArguments
            if (args.length > 0) {
                Type arg = args[0]
                if (arg instanceof Class) {
                    return toSchema((Class<?>) arg, null, typeBindings)
                }
                if (arg instanceof TypeVariable) {
                    String varName = ((TypeVariable) arg).name
                    Class<?> bound = typeBindings[varName]
                    if (bound) return toSchema(bound, null, typeBindings)
                }
            }
        }
        return [type: 'object']
    }
}
