package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting

import spock.lang.Specification
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.StandardPlateLabelJsonCreator

/**
 * Tests for {@code LabelTemplates}
 *
 * Created by ke4 on 17/03/2017.
 */
class LabelTemplatesSpec extends Specification {

    def "Instantiating a label template can create a proper type of label json creator"() {

        setup:
        LabelTemplates ss2LysisBufferTemplate = LabelTemplates.LABEL_STANDARD_6MM_PLATE
        def labelData = [:]
        labelData['barcodes'] = ["ABCD-EFG-00000001"]
        Object[] args = [labelData]
        def labelJsonCreator = ss2LysisBufferTemplate.getInstance(args)

        expect:
        labelJsonCreator instanceof StandardPlateLabelJsonCreator
    }

    def "Instantiating a label template can return its proper type as a String"() {

        setup:
        LabelTemplates ss2LysisBufferTemplate = LabelTemplates.LABEL_STANDARD_6MM_PLATE
        String expectedType = "Standard Plate Label"

        expect:
        ss2LysisBufferTemplate.getType() == expectedType
    }
}
