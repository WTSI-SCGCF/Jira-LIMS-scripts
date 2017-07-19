package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of issue types.
 *
 * Created by ke4 on 25/01/2017.
 */
enum IssueTypeName {

    COMBINE_PLATES("Combine Plates"),
    FEEDBACK("Feedback"),
    IMPORT_DECLARATION("Import Declaration"),
    INPUT_QC("Input QC"),
    LIBRARY_POOLING("Library Pooling"),
    LYSIS_BUFFER_REQUEST("Lysis Buffer Request"),
    NORM_AND_NEXTERA("Normalisation and Nextera"),
    PLATE_CMB("Plate CMB"),
    PLATE_DNA("Plate DNA"),
    PLATE_ECH("Plate ECH"),
    PLATE_LIB("Plate LIB"),
    PLATE_SS2("Plate SS2"),
    QUANTIFICATION_ANALYSIS("Quantification Analysis"),
    QUANTIFICATION_RUN("Quantification Run"),
    REAGENT_LOT_OR_BATCH("Reagent Lot or Batch"),
    REAGENT_TEMPLATE("Reagent Template"),
    SPRI_PLATE_CLEANUP("SPRi Plate Cleanup"),
    SAMPLE_RECEIPT("Sample Receipt"),
    SMART_SEQ2("Smart-seq2"),
    STUDY("Study"),
    SUBMISSION("Submission"),
    TUBE_LPL("Tube LPL"),
    TUBE_PPL("Tube NPL"),
    TUBE_IPL("Tube IPL"),
    TUBE_NPL("Tube PPL")

    String issueTypeName

    public IssueTypeName(String issueTypeName) {
        this.issueTypeName = issueTypeName
    }

    @Override
    String toString() {
        issueTypeName
    }
}
