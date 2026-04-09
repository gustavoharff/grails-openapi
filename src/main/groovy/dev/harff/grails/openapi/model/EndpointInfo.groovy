package dev.harff.grails.openapi.model

class EndpointInfo {
    String description
    Class<?> commandClass
    boolean commandIsBody
    boolean apiIgnore
    String apiTag
    boolean isPublic
    List<Map> responsesOverride
    boolean deprecated
    Class<?> responseType
    boolean responseIsList
    List<Class<?>> responseTypeArguments = []
}
