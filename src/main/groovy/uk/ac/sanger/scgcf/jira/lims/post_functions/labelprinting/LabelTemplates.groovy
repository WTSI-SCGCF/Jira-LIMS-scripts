package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting

import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.LabelJsonCreator
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.StandardTubeLabelJsonCreator
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.StandardPlateLabelJsonCreator

/**
 * Enumerated list of label templates to use with Print My Barcode application.
 *
 * Created by ke4 on 15/03/2017.
 */
enum LabelTemplates {

    LABEL_STANDARD_6MM_PLATE("Standard Plate Label", StandardPlateLabelJsonCreator.class),
    LABEL_STANDARD_TUBE("Standard Tube Label", StandardTubeLabelJsonCreator.class)

    private String type
    private Class<?> clazz

    LabelTemplates(String type, Class clazz) {
        this.type = type
        this.clazz = clazz
    }

    public String getType() {
        type
    }

    public LabelJsonCreator getInstance(Object[] args) {
        (LabelJsonCreator) clazz.newInstance(args)
    }

    @Override
    String toString() {
        return type
    }
}
