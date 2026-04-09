package dev.harff.grails.openapi

import grails.validation.Validateable

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class SchemaBuilder {

    static Map<String, Object> buildCommandSchema(Class<?> cls) {
        Map<String, Object> properties = [:]
        List<String> required = []

        Map constrainedProperties = [:]
        try {
            constrainedProperties = cls.constrainedProperties ?: [:]
        } catch (Exception ignored) {}

        collectFields(cls).each { Field field ->
            Map<String, Object> propSchema = TypeMapper.toSchema(field.type, field.genericType)

            def cp = constrainedProperties[field.name]
            if (cp != null) {
                try { if (cp.maxSize) propSchema.maxLength = cp.maxSize } catch (Exception ignored) {}
                try { if (cp.min != null) propSchema.minimum = cp.min } catch (Exception ignored) {}
                try { if (cp.max != null) propSchema.maximum = cp.max } catch (Exception ignored) {}
                try { if (cp.inList) propSchema['enum'] = cp.inList } catch (Exception ignored) {}
                try {
                    if (cp.nullable == false) required << field.name
                } catch (Exception ignored) {}
            }

            properties[field.name] = propSchema
        }

        Map<String, Object> schema = [type: 'object', properties: properties]
        if (required) schema.required = required
        return schema
    }

    static Map<String, Object> buildDomainSchema(def domainClass) {
        Map<String, Object> properties = [:]

        try {
            def identifier = domainClass.identifier
            if (identifier) {
                properties[identifier.name] = TypeMapper.toSchema(identifier.type)
            }
        } catch (Exception ignored) {}

        try {
            domainClass.persistentProperties.each { prop ->
                Map<String, Object> propSchema
                if (prop.association) {
                    if (prop.oneToMany || prop.manyToMany) {
                        propSchema = [type: 'array', items: [type: 'object']]
                    } else {
                        propSchema = [type: 'object']
                    }
                } else {
                    propSchema = TypeMapper.toSchema(prop.type)
                }
                properties[prop.name] = propSchema
            }
        } catch (Exception ignored) {}

        return [type: 'object', properties: properties]
    }

    static Map<String, Object> buildObjectSchema(Class<?> cls, Map<String, Class<?>> typeBindings = [:]) {
        Map<String, Object> properties = [:]
        List<String> required = []
        boolean kotlin = isKotlinClass(cls)

        Class<?> current = cls
        while (current != null && current != Object) {
            current.declaredFields.each { Field field ->
                if (!field.synthetic
                    && !Modifier.isStatic(field.modifiers)
                    && field.name != 'metaClass'
                    && !field.name.startsWith('$')
                    && !field.name.contains('__')
                    && field.type != Closure
                    && hasPublicGetter(current, field.name)
                    && !properties.containsKey(field.name)) {
                    Map<String, Object> propSchema = TypeMapper.toSchema(field.type, field.genericType, typeBindings)
                    if (isNullableProperty(current, field.name)) {
                        propSchema.nullable = true
                    } else if (kotlin) {
                        required << field.name
                    }
                    properties[field.name] = propSchema
                }
            }
            current = current.superclass
        }

        Map<String, Object> schema = [type: 'object', properties: properties]
        if (required) schema.required = required
        return schema
    }

    private static boolean isKotlinClass(Class<?> cls) {
        return cls.annotations.any { it.annotationType().name == 'kotlin.Metadata' }
    }

    private static boolean isNullableProperty(Class<?> cls, String fieldName) {
        if (isKotlinClass(cls)) {
            // For Kotlin classes, use kotlin-reflect (optional runtime dependency).
            // This is the only reliable approach for Kotlin 2.x, which no longer
            // emits @Nullable on getters by default.
            try {
                Class<?> mappingKt = Class.forName('kotlin.jvm.JvmClassMappingKt')
                Object kClass = mappingKt.getMethod('getKotlinClass', Class).invoke(null, cls)

                Class<?> kClassExtClass = Class.forName('kotlin.reflect.full.KClasses')
                Class<?> kClassInterface = Class.forName('kotlin.reflect.KClass')
                Collection<?> props = (Collection<?>) kClassExtClass
                    .getMethod('getMemberProperties', kClassInterface)
                    .invoke(null, kClass)

                Class<?> kCallable = Class.forName('kotlin.reflect.KCallable')
                Class<?> kType = Class.forName('kotlin.reflect.KType')
                for (Object prop : props) {
                    String name = (String) kCallable.getMethod('getName').invoke(prop)
                    if (name == fieldName) {
                        Object returnType = kCallable.getMethod('getReturnType').invoke(prop)
                        return (Boolean) kType.getMethod('isMarkedNullable').invoke(returnType)
                    }
                }
            } catch (Exception ignored) {}
            return false
        }
        // For Java/Groovy classes: check for runtime-retained @Nullable annotations on the getter
        String capitalized = fieldName.capitalize()
        java.lang.reflect.Method getter = null
        try {
            getter = cls.getMethod("get${capitalized}")
        } catch (NoSuchMethodException ignored) {
            try {
                getter = cls.getMethod("is${capitalized}")
            } catch (NoSuchMethodException ignored2) {}
        }
        if (!getter) return false
        return getter.annotations.any { ann ->
            ann.annotationType().name in [
                'javax.annotation.Nullable',           // JSR-305 (RUNTIME retention)
                'org.springframework.lang.Nullable',   // Spring (RUNTIME retention)
                'jakarta.annotation.Nullable',         // Jakarta EE (RUNTIME retention)
            ]
        }
    }

    private static List<Field> collectFields(Class<?> cls) {
        List<Field> fields = []
        Class<?> current = cls
        while (current != null && current != Object && Validateable.isAssignableFrom(current)) {
            current.declaredFields.each { Field f ->
                if (!f.synthetic
                    && !Modifier.isStatic(f.modifiers)
                    && hasPublicGetter(current, f.name)
                    && f.name != 'metaClass'
                    && !f.name.startsWith('$')
                    && !f.name.contains('__')
                    && f.type != Closure
                    && !fields.any { it.name == f.name }) {
                    fields << f
                }
            }
            current = current.superclass
        }
        return fields
    }

    private static boolean hasPublicGetter(Class<?> cls, String fieldName) {
        String capitalized = fieldName.capitalize()
        return cls.methods.any { m ->
            Modifier.isPublic(m.modifiers)
            && m.parameterCount == 0
            && (m.name == "get${capitalized}" || m.name == "is${capitalized}")
        }
    }
}
