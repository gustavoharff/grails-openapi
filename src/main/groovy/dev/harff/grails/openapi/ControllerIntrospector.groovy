package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.EndpointInfo
import grails.validation.Validateable
import org.grails.core.DefaultGrailsControllerClass

import java.lang.reflect.Method

class ControllerIntrospector {

    ControllerSourceAnalyzer sourceAnalyzer

    EndpointInfo introspect(DefaultGrailsControllerClass ctrl, String actionName, String httpMethod) {
        List<Method> candidates = ctrl.clazz.getMethods().findAll { it.name == actionName }
        if (!candidates) return null
        // Prefer the overload that has typed (Validateable or String) parameters over no-arg versions
        Method method = candidates.find { m ->
            m.parameters.any { p -> Validateable.isAssignableFrom(p.type) || p.type == String }
        } ?: candidates[0]

        Class<?> ctrlClass = ctrl.clazz
        EndpointInfo info = new EndpointInfo()

        info.apiIgnore = ctrlClass.isAnnotationPresent(ApiIgnore) || method.isAnnotationPresent(ApiIgnore)
        if (info.apiIgnore) return info

        Description desc = method.getAnnotation(Description)
        info.description = desc?.value() ?: ''

        ApiTag methodTag = method.getAnnotation(ApiTag)
        ApiTag classTag = ctrlClass.getAnnotation(ApiTag)
        info.apiTag = methodTag?.value() ?: classTag?.value()

        info.isPublic = ctrlClass.isAnnotationPresent(ApiPublic) || method.isAnnotationPresent(ApiPublic)
        info.deprecated = method.isAnnotationPresent(Deprecated) || ctrlClass.isAnnotationPresent(Deprecated)

        ApiResponses multiResponse = method.getAnnotation(ApiResponses)
        ApiResponse singleResponse = method.getAnnotation(ApiResponse)
        if (multiResponse) {
            info.responsesOverride = multiResponse.value().collect { r ->
                [status: r.status(), description: r.description()]
            }
        } else if (singleResponse) {
            info.responsesOverride = [[status: singleResponse.status(), description: singleResponse.description()]]
        }

        method.parameters.each { param ->
            if (param.type == String) {
                // path variable — name comes from URL pattern, type detection only
            } else if (Validateable.isAssignableFrom(param.type)) {
                info.commandClass = param.type
            }
        }

        info.commandIsBody = httpMethod in ['POST', 'PUT', 'PATCH'] && info.commandClass != null

        if (sourceAnalyzer) {
            def respondInfo = sourceAnalyzer.analyze(ctrl.clazz)?.get(actionName)
            if (respondInfo) {
                info.responseType = respondInfo.type
                info.responseIsList = respondInfo.isList
            }
        }

        return info
    }
}
