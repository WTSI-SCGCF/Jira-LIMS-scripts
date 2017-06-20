package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates

import spock.lang.Specification

/**
 * Created by ke4 on 16/03/2017.
 */
class SS2LysisBufferLabelTemplateSpec extends Specification {

    def "when instantiating with a barcode, then returns the correct label JSON structure"() {

        setup: "Create StandardPlateLabelJsonCreator with a barcode"
        List<String> barcodes = ["ABCD-EFG-12345678"]
        def labelData = ['barcodes': barcodes]
        def template = new StandardPlateLabelJsonCreator(labelData)

        def expectedMessage = [
            [
                "label_1": [
                    "barcode"     : barcodes[0],
                    "barcode_text": barcodes[0]
                ]
            ]
        ]

        expect:
        template.createLabelBody() == expectedMessage
    }
}
