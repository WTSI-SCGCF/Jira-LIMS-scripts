package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of SS2 plate's state name.
 *
 * Created by ke4 on 25/01/2017.
 */
enum SS2PlateStateName {

    PLATESS2_IN_IMD("PltSS2 In IMD"),
    PLATESS2_IN_SS2("PltSS2 In SS2"),
    PLATESS2_IN_SUBMISSION("PltSS2 In Submission"),
    PLATESS2_IN_FEEDBACK("PltSS2 In Feedback"),
    PLATESS2_IN_RECEIVING("PltSS2 In Receiving"),
    PLATESS2_WITH_CUSTOMER("PltSS2 With Customer"),
    PLATESS2_RDY_FOR_SUBMISSION("PltSS2 Rdy for Submission"),
    PLATESS2_RDY_FOR_SS2("PltSS2 Rdy for SS2"),
    PLATESS2_RDY_FOR_IQC("PltSS2 Rdy for IQC")

    String plateStateName

    public SS2PlateStateName(String plateStateName) {
        this.plateStateName = plateStateName
    }

    @Override
    String toString() {
        plateStateName
    }
}
