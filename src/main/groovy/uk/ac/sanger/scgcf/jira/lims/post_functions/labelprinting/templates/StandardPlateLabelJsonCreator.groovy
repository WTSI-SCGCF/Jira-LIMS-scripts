package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates

import groovy.util.logging.Slf4j

/**
 * Label template creator for a Smart-seq2 plate for Lysis Buffer request.
 *
 * Created by ke4 on 16/03/2017.
 */
@Slf4j(value="LOG")
class StandardPlateLabelJsonCreator implements LabelJsonCreator {

    List<String> barcodes;

    StandardPlateLabelJsonCreator(def labelData) {
        this.barcodes = labelData.barcodes
    }

    @Override
    def createLabelBody() {
        List body = []
        LOG.debug("barcodes to add to the template: $barcodes")
        barcodes.each { barcode ->
            def labelJson = labelJson()
            labelJson.label_1.barcode = barcode
            labelJson.label_1.barcode_text = barcode
            body.add(labelJson)
        }

        LOG.debug("Created label body: $body")

        body
    }

    @Override
    def labelJson() {
        [
            "label_1": [
                "barcode": "",
                "barcode_text": ""
            ]
        ]
    }
}
