package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates

/**
 * This is just an empty template like class to show how a label creator builds up
 * @TODO: build the real library pool tube label creator
 *
 * Created by ke4 on 16/03/2017.
 */
class StandardTubeLabelJsonCreator implements LabelJsonCreator {

    StandardTubeLabelJsonCreator(def labelData) {
        // @TODO add some initializing code
    }

    @Override
    def createLabelBody() {
        def labelJson = labelJson()

        labelJson
    }

    @Override
    def labelJson() {
        []
    }
}
