package dev.harff.grails.openapi

import grails.validation.Validateable
import org.grails.core.DefaultGrailsControllerClass
import spock.lang.Specification

class ControllerIntrospectorSpec extends Specification {

    ControllerSourceAnalyzer sourceAnalyzer = Mock()
    ControllerIntrospector introspector

    def setup() {
        introspector = new ControllerIntrospector(sourceAnalyzer: sourceAnalyzer)
    }

    private DefaultGrailsControllerClass mockCtrl(Class<?> clazz) {
        def ctrl = Mock(DefaultGrailsControllerClass)
        ctrl.clazz >> clazz
        return ctrl
    }

    def "returns null when action method does not exist on controller"() {
        given:
        def ctrl = mockCtrl(EmptyController)

        when:
        def info = introspector.introspect(ctrl, 'nonExistentAction', 'GET')

        then:
        info == null
    }

    def "returns EndpointInfo with apiIgnore=true when class annotated with @ApiIgnore"() {
        given:
        def ctrl = mockCtrl(IgnoredController)

        when:
        def info = introspector.introspect(ctrl, 'index', 'GET')

        then:
        info != null
        info.apiIgnore == true
    }

    def "returns EndpointInfo with apiIgnore=true when method annotated with @ApiIgnore"() {
        given:
        def ctrl = mockCtrl(ControllerWithIgnoredAction)

        when:
        def info = introspector.introspect(ctrl, 'ignoredAction', 'GET')

        then:
        info.apiIgnore == true
    }

    def "apiIgnore=false when no @ApiIgnore annotation present"() {
        given:
        def ctrl = mockCtrl(PlainController)

        when:
        def info = introspector.introspect(ctrl, 'index', 'GET')

        then:
        info.apiIgnore == false
    }

    def "reads description from @Description annotation on method"() {
        given:
        def ctrl = mockCtrl(AnnotatedController)

        when:
        def info = introspector.introspect(ctrl, 'listItems', 'GET')

        then:
        info.description == 'List all items'
    }

    def "description is empty string when @Description annotation absent"() {
        given:
        def ctrl = mockCtrl(PlainController)

        when:
        def info = introspector.introspect(ctrl, 'index', 'GET')

        then:
        info.description == ''
    }

    def "reads apiTag from @ApiTag annotation on method"() {
        given:
        def ctrl = mockCtrl(AnnotatedController)

        when:
        def info = introspector.introspect(ctrl, 'listItems', 'GET')

        then:
        info.apiTag == 'Items'
    }

    def "reads apiTag from @ApiTag annotation on class when method has no tag"() {
        given:
        def ctrl = mockCtrl(TaggedController)

        when:
        def info = introspector.introspect(ctrl, 'show', 'GET')

        then:
        info.apiTag == 'ClassTag'
    }

    def "method @ApiTag takes precedence over class @ApiTag"() {
        given:
        def ctrl = mockCtrl(OverriddenTagController)

        when:
        def info = introspector.introspect(ctrl, 'create', 'POST')

        then:
        info.apiTag == 'MethodTag'
    }

    def "isPublic=true when method annotated with @ApiPublic"() {
        given:
        def ctrl = mockCtrl(PublicMethodController)

        when:
        def info = introspector.introspect(ctrl, 'publicAction', 'GET')

        then:
        info.isPublic == true
    }

    def "isPublic=true when class annotated with @ApiPublic"() {
        given:
        def ctrl = mockCtrl(PublicClassController)

        when:
        def info = introspector.introspect(ctrl, 'anything', 'GET')

        then:
        info.isPublic == true
    }

    def "isPublic=false when no @ApiPublic annotation present"() {
        given:
        def ctrl = mockCtrl(PlainController)

        when:
        def info = introspector.introspect(ctrl, 'index', 'GET')

        then:
        info.isPublic == false
    }

    def "deprecated=true when method annotated with @Deprecated"() {
        given:
        def ctrl = mockCtrl(DeprecatedMethodController)

        when:
        def info = introspector.introspect(ctrl, 'oldAction', 'GET')

        then:
        info.deprecated == true
    }

    def "deprecated=false when no @Deprecated annotation present"() {
        given:
        def ctrl = mockCtrl(PlainController)

        when:
        def info = introspector.introspect(ctrl, 'index', 'GET')

        then:
        info.deprecated == false
    }

    def "reads single @ApiResponse override"() {
        given:
        def ctrl = mockCtrl(SingleResponseController)

        when:
        def info = introspector.introspect(ctrl, 'create', 'POST')

        then:
        info.responsesOverride.size() == 1
        info.responsesOverride[0].status == 201
        info.responsesOverride[0].description == 'Created'
    }

    def "reads multiple @ApiResponse overrides via @ApiResponses"() {
        given:
        def ctrl = mockCtrl(MultiResponseController)

        when:
        def info = introspector.introspect(ctrl, 'update', 'PUT')

        then:
        info.responsesOverride.size() == 2
        info.responsesOverride.any { it.status == 200 && it.description == 'OK' }
        info.responsesOverride.any { it.status == 422 && it.description == 'Validation Error' }
    }

    def "commandIsBody=true for POST request with Validateable command class"() {
        given:
        def ctrl = mockCtrl(CommandController)

        when:
        def info = introspector.introspect(ctrl, 'save', 'POST')

        then:
        info.commandClass != null
        info.commandIsBody == true
    }

    def "commandIsBody=true for PUT request with Validateable command class"() {
        given:
        def ctrl = mockCtrl(CommandController)

        when:
        def info = introspector.introspect(ctrl, 'update', 'PUT')

        then:
        info.commandIsBody == true
    }

    def "commandIsBody=false for GET request even with Validateable command class"() {
        given:
        def ctrl = mockCtrl(CommandController)

        when:
        def info = introspector.introspect(ctrl, 'search', 'GET')

        then:
        info.commandIsBody == false
    }

    def "commandClass is set from Validateable parameter"() {
        given:
        def ctrl = mockCtrl(CommandController)

        when:
        def info = introspector.introspect(ctrl, 'save', 'POST')

        then:
        info.commandClass == SampleCommand
    }

    def "uses sourceAnalyzer results for responseType when provided"() {
        given:
        def ctrl = mockCtrl(PlainController)
        sourceAnalyzer.analyze(PlainController) >> [index: new dev.harff.grails.openapi.model.RespondTypeInfo(type: String, isList: false)]

        when:
        def info = introspector.introspect(ctrl, 'index', 'GET')

        then:
        info.responseType == String
        info.responseIsList == false
    }

    // ---- Test fixture controllers and commands ----

    static class EmptyController {}

    @ApiIgnore
    static class IgnoredController {
        void index() {}
    }

    static class ControllerWithIgnoredAction {
        @ApiIgnore
        void ignoredAction() {}
    }

    static class PlainController {
        void index() {}
    }

    static class AnnotatedController {
        @Description('List all items')
        @ApiTag('Items')
        void listItems() {}
    }

    @ApiTag('ClassTag')
    static class TaggedController {
        void show() {}
    }

    @ApiTag('ClassTag')
    static class OverriddenTagController {
        @ApiTag('MethodTag')
        void create() {}
    }

    static class PublicMethodController {
        @ApiPublic
        void publicAction() {}
    }

    @ApiPublic
    static class PublicClassController {
        void anything() {}
    }

    static class DeprecatedMethodController {
        @Deprecated
        void oldAction() {}
    }

    static class SingleResponseController {
        @ApiResponse(status = 201, description = 'Created')
        void create() {}
    }

    static class MultiResponseController {
        @ApiResponses([@ApiResponse(status = 200, description = 'OK'), @ApiResponse(status = 422, description = 'Validation Error')])
        void update() {}
    }

    static class SampleCommand implements Validateable {
        String name
        String getName() { name }
    }

    static class CommandController {
        void save(SampleCommand cmd) {}
        void update(SampleCommand cmd) {}
        void search(SampleCommand cmd) {}
    }
}
