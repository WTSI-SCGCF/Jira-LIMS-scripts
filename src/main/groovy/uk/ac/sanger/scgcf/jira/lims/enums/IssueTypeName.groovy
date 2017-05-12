package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of issue types.
 *
 * Created by ke4 on 25/01/2017.
 */
enum IssueTypeName {

    PLATE_SS2("Plate SS2"),
    PLATE_DNA("Plate DNA"),
    PLATE_CMB("Plate CMB"),
    PLATE_ECH("Plate ECH"),
    REAGENT_LOT_OR_BATCH("Reagent Lot or Batch"),
    SUBMISSION("Submission"),
    SMARTSEQ2("Smart-seq2"),
    SPRI_PLATE_CLEANUP("SPRi Plate Cleanup")

    String issueTypeName

    public IssueTypeName(String issueTypeName) {
        this.issueTypeName = issueTypeName
    }

    @Override
    String toString() {
        issueTypeName
    }
}
