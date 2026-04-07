package dev.harff.grails.openapi

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

class YamlWriter {

    static void write(Map<String, Object> document, String outputPath) {
        DumperOptions opts = new DumperOptions()
        opts.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        opts.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
        opts.indent = 2
        opts.indicatorIndent = 2
        opts.indentWithIndicator = true
        opts.prettyFlow = true

        String yaml = new Yaml(opts).dump(document)
            .replaceFirst(/^---\s*\n/, '')
            .replaceAll(/\[\s+\]/, '[]')

        File file = new File(outputPath)
        file.parentFile?.mkdirs()
        file.text = yaml

        println "OpenAPI spec written to: ${outputPath}"
    }
}
