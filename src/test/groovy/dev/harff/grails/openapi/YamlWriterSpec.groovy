package dev.harff.grails.openapi

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class YamlWriterSpec extends Specification {

    @TempDir
    Path tempDir

    def "writes document to specified file path"() {
        given:
        def outputPath = "${tempDir}/openapi.yaml"
        def document = [openapi: '3.0.3', info: [title: 'Test API', version: '1.0.0']]

        when:
        YamlWriter.write(document, outputPath)

        then:
        new File(outputPath).exists()
    }

    def "written YAML contains expected keys"() {
        given:
        def outputPath = "${tempDir}/openapi.yaml"
        def document = [openapi: '3.0.3', info: [title: 'Test API', version: '1.0.0']]

        when:
        YamlWriter.write(document, outputPath)
        def content = new File(outputPath).text

        then:
        content.contains('openapi: 3.0.3')
        content.contains('title: Test API')
    }

    def "written YAML does not start with '---'"() {
        given:
        def outputPath = "${tempDir}/openapi.yaml"
        def document = [openapi: '3.0.3']

        when:
        YamlWriter.write(document, outputPath)

        then:
        !new File(outputPath).text.startsWith('---')
    }

    def "creates parent directories that do not exist"() {
        given:
        def outputPath = "${tempDir}/nested/output/openapi.yaml"
        def document = [openapi: '3.0.3']

        when:
        YamlWriter.write(document, outputPath)

        then:
        new File(outputPath).exists()
    }

    def "empty security list is rendered as inline []"() {
        given:
        def outputPath = "${tempDir}/openapi.yaml"
        def document = [paths: ['/test': [get: [security: []]]]]

        when:
        YamlWriter.write(document, outputPath)

        then:
        new File(outputPath).text.contains('security: []')
    }

    def "nested map structure is rendered in YAML block style"() {
        given:
        def outputPath = "${tempDir}/openapi.yaml"
        def document = [
            components: [
                schemas: [
                    User: [type: 'object', properties: [id: [type: 'integer']]]
                ]
            ]
        ]

        when:
        YamlWriter.write(document, outputPath)
        def content = new File(outputPath).text

        then:
        content.contains('components:')
        content.contains('schemas:')
        content.contains('User:')
    }

    def "overwrites existing file at path"() {
        given:
        def outputPath = "${tempDir}/openapi.yaml"
        YamlWriter.write([openapi: '3.0.0'], outputPath)

        when:
        YamlWriter.write([openapi: '3.0.3'], outputPath)

        then:
        new File(outputPath).text.contains('3.0.3')
        !new File(outputPath).text.contains('3.0.0')
    }
}
