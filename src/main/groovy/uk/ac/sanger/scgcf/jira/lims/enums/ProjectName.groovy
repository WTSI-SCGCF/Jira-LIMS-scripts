package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the names of projects.
 *
 * Created by as28 on 12/07/2017
 */
enum ProjectName {

    COMBINE_PLATES("SeqPL: Combine Plates"),
    CONTAINERS("SeqPL: Containers"),
    DNA_QUANTIFICATION("SeqPL: DNA Quantification"),
    IMPORT_DECLARATIONS("SeqPL: Import Declarations"),
    INPUT_QC("SeqPL: Input Quality Control"),
    LYSIS_BUFFER_REQUESTS("SeqPL: LB Plate Requests"),
    LIBRARY_POOLING("SeqPL: Library Pooling"),
    NORM_AND_NEXTERA("SeqPL: Normalisation and Nextera"),
    SMART_SEQ2("SeqPL: Pre-Amp Smart-seq2"),
    REAGENTS("SeqPL: Reagents"),
    SAMPLE_RECEIPTS("SeqPL: Sample Receipts"),
    SPRI_PLATE_CLEANUP("SeqPL: SPRi Plate Cleanup"),
    STUDIES("SeqPL: Studies"),
    SUBMISSIONS("SeqPL: Submissions")

    //TODO: add remaining projects

    String projectName

    public ProjectName(String projectName) {
        this.projectName = projectName
    }

    @Override
    String toString() {
        projectName
    }
}
