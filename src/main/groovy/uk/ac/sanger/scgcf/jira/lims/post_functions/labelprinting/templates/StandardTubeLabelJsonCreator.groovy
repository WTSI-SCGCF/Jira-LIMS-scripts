package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates

import groovy.util.logging.Slf4j

/**
 * This is just an empty template like class to show how a label creator builds up
 * @TODO: build the real library pool tube label creator
 *
 * Created by ke4 on 16/03/2017.
 */

@Slf4j(value="LOG")
class StandardTubeLabelJsonCreator implements LabelJsonCreator {

    List<Map<String, String>> tubes

    StandardTubeLabelJsonCreator(def labelData) {
        this.tubes = labelData.tubes
    }

    @Override
    def createLabelBody() {
        List body = []

        Date now = new Date()
        String curr_date = now.format("YYYY-MM-DD")

        tubes.each { tubeMap ->
            def labelJson = labelJson()
            labelJson.main_label.first_text = tubeMap['barcode']
            labelJson.main_label.second_text = "from parent -"
            labelJson.main_label.third_text = tubeMap['parent_barcode']
            labelJson.main_label.fourth_text = curr_date
            labelJson.main_label.fifth_text = tubeMap['pooled_from_quadrant']
            labelJson.main_label.lid_top_text = tubeMap['barcode_info']
            labelJson.main_label.lid_middle_text = tubeMap['barcode_number']
            labelJson.main_label.lid_btm_text = tubeMap['pooled_from_quadrant']
            labelJson.main_label.label_barcode = tubeMap['barcode']
            body.add(labelJson)
        }

        // TODO: may want to reverse the order of the JSON -> labels_json.reverse()

        LOG.debug("Created label body: $body")

        body
    }

    @Override
    def labelJson() {
        [
            "main_label": [
                "first_text": "",
                "second_text": "",
                "third_text": "",
                "fourth_text": "",
                "fifth_text": "",
                "lid_top_text": "",
                "lid_middle_text": "",
                "lid_btm_text": "",
                "label_barcode": ""
            ]
        ]
    }

//    labels_json.reverse()

//    "data": {
//        "attributes": {
//            "printer_name": printer_name,
//            "label_template_id": label_template_id,
//            "labels": {
//                "body": labels_json
//            }
//        }
//    }
}