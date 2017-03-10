package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of work flows.
 *
 * Created by ke4 on 25/01/2017.
 */
enum WorkflowName {

    PLATE_SS2("Plate SS2"),
    SMART_SEQ2("Smart-seq2"),
    SUBMISSION("Submission"),
    IMD("Import Declarations"),
    SAMPLE_RECEIPT("Sample Receipt")

    String workflowName

    public WorkflowName(String workflowName) {
        this.workflowName = workflowName
    }

    @Override
    String toString() {
        workflowName
    }
}
