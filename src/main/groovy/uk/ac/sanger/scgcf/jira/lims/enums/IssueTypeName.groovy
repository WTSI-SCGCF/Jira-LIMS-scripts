package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of issue types.
 *
 * Created by ke4 on 25/01/2017.
 */
enum IssueTypeName {

    STUDY("Study"),
    PLATE_SS2("Plate SS2"),
    PLATE_DNA("Plate DNA"),
    PLATE_CMB("Plate CMB"),
    PLATE_ECH("Plate ECH"),
    PLATE_LIB("Plate LIB"),
    TUBE_LIBRARY_POOL("Tube Library Pool"),
    TUBE_LIBRARY_POOL_PRE_NORM("Tube Library Pool Pre-Norm"),
    TUBE_LIBRARY_POOL_NORM("Tube Library Pool Norm"),
    REAGENT_LOT_OR_BATCH("Reagent Lot or Batch"),
    REAGENT_TEMPLATE("Reagent Template"),
    LYSIS_BUFFER_REQUEST("Lysis Buffer Request"),
    IMPORT_DECLARATION("Import Declaration"),
    SAMPLE_RECEIPT("Sample Receipt"),
    SUBMISSION("Submission"),
    SMART_SEQ2("Smart-seq2"),
    INPUT_QC("Input QC"),
    SPRI_PLATE_CLEANUP("SPRi Plate Cleanup"),
    QUANTIFICATION_RUN("Quantification Run"),
    QUANTIFICATION_ANALYSIS("Quantification Analysis")

    String issueTypeName

    public IssueTypeName(String issueTypeName) {
        this.issueTypeName = issueTypeName
    }

    @Override
    String toString() {
        issueTypeName
    }
}
