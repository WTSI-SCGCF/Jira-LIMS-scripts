package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of work flows.
 *
 * Created by ke4 on 25/01/2017.
 */
enum WorkflowName {

    // TODO: are we using WorkflowName incorrectly? looks same as IssueTypeName? should be SeqPL: etc
    PLATE_SS2("Plate SS2"),
    PLATE_DNA("Plate DNA"),
    PLATE_CMB("Plate CMB"),
    PLATE_ECH("Plate ECH"),
    SMART_SEQ2("Smart-seq2"),
    SUBMISSION("Submission"),
    IMD("Import Declarations"),
    SAMPLE_RECEIPT("Sample Receipt"),
    SPRI_PLATE_CLEANUP("SPRi Plate Cleanup"),
    QUANT_RUN("Quant")

    String workflowName

    public WorkflowName(String workflowName) {
        this.workflowName = workflowName
    }

    @Override
    String toString() {
        workflowName
    }
}
