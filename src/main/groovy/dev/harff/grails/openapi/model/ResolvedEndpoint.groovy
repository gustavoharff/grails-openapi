package dev.harff.grails.openapi.model

import org.grails.core.DefaultGrailsControllerClass

class ResolvedEndpoint {
    String httpMethod
    String path
    String controllerName
    String actionName
    List<String> pathParams = []
    DefaultGrailsControllerClass controllerArtefact
}
