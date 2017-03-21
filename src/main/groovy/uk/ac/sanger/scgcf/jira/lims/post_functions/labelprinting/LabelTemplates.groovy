package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting

import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.LabelJsonCreator
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.LibraryPoolTubeLabelJsonCreator
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.SS2LysisBufferLabelJsonCreator

/**
 * Enumerated list of label templates to use with Print My Barcode application.
 *
 * Created by ke4 on 15/03/2017.
 */
enum LabelTemplates {

    SS2_LYSIS_BUFFER("SS2 Lysis Buffer", SS2LysisBufferLabelJsonCreator.class),
    LIBRARY_POOL_TUBE("Library Pool Tube", LibraryPoolTubeLabelJsonCreator.class)

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
