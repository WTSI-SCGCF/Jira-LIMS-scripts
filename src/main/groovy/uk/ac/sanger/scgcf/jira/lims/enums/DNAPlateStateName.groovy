package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of DNA plate's state name.
 *
 * Created by ke4 on 25/01/2017.
 */
enum DNAPlateStateName {

    PLATEDNA_IN_IMD("PltSS2 In IMD"),
    PLATEDNA_IN_SUBMISSION("PltSS2 In Submission"),
    PLATEDNA_IN_FEEDBACK("PltSS2 In Feedback"),
    PLATEDNA_IN_RECEIVING("PltSS2 In Receiving"),
    PLATEDNA_WITH_CUSTOMER("PltSS2 With Customer"),
    PLATEDNA_RDY_FOR_SUBMISSION("PltSS2 Rdy for Submission"),
    PLATEDNA_RDY_FOR_IQC("PltSS2 Rdy for IQC")

    String plateStateName

    public DNAPlateStateName(String plateStateName) {
        this.plateStateName = plateStateName
    }

    @Override
    String toString() {
        plateStateName
    }
}
