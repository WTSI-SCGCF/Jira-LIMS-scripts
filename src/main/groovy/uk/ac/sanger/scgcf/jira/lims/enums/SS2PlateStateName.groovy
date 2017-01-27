package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of work flows.
 *
 * Created by ke4 on 25/01/2017.
 */
enum SS2PlateStateName {

    PLATESS2_IN_SS2("PltSS2 In SS2"),
    PLATESS2_IN_SUBMISSION("PltSS2 In Submission"),
    PLATESS2_IN_FEEDBACK("PltSS2 In Feedback")

    String plateStateName

    public SS2PlateStateName(String plateStateName) {
        this.plateStateName = plateStateName
    }

    @Override
    String toString() {
        plateStateName
    }
}
