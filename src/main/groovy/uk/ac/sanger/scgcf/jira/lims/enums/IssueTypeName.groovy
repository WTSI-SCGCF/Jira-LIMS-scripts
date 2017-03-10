package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of issue types.
 *
 * Created by ke4 on 25/01/2017.
 */
enum IssueTypeName {

    PLATE_SS2("Plate SS2"),
    REAGENT_LOT_OR_BATCH("Reagent Lot or Batch"),
    SUBMISSION("Submission"),
    SMARTSEQ2("Smart-seq2")

    String issueTypeName

    public IssueTypeName(String issueTypeName) {
        this.issueTypeName = issueTypeName
    }

    @Override
    String toString() {
        issueTypeName
    }
}
