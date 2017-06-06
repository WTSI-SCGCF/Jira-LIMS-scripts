package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the names of workflows.
 *
 * Created by ke4 on 25/01/2017.
 */
enum WorkflowName {

    STUDIES("Studies"),
    PLATE_SS2("Plate SS2"),
    PLATE_DNA("Plate DNA"),
    PLATE_CMB("Plate CMB"),
    PLATE_ECH("Plate ECH"),
    REAGENT_INSTANCES("Reagent Instances"),
    REAGENT_TEMPLATES("Reagent Templates"),
    LYSIS_BUFFER_REQUESTS("Lysis Buffer Requests"),
    IMPORT_DECLARATIONS("Import Declarations"),
    SAMPLE_RECEIPTS("Sample Receipts"),
    SUBMISSIONS("Submissions"),
    PRE_AMP_SMART_SEQ2("Pre-Amp Smart-seq2"),
    INPUT_QC("Input QC"),
    COMBINE_PLATES("Combine Plates"),
    SPRI_PLATE_CLEANUP("SPRi Plate Cleanup"),
    DNA_QUANTIFICATION_RUN("DNA Quantification Run"),
    DNA_QUANTIFICATION_ANALYSIS("DNA Quantification Analysis")

    //TODO: add plate LIB, tubes and remaining workflows

    String workflowName

    public WorkflowName(String workflowName) {
        this.workflowName = workflowName
    }

    @Override
    String toString() {
        workflowName
    }
}
