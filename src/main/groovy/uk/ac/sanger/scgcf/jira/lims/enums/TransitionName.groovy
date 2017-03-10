package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of transitions.
 * 
 * Created by ke4 on 25/01/2017.
 */
enum TransitionName {

    REVERT_TO_WITH_CUSTOMER("REVERT_TO_WITH_CUSTOMER"),
    REVERT_TO_READY_FOR_SS2("REVERT_TO_READY_FOR_SS2"),
    REVERT_TO_READY_FOR_SUBMISSION("REVERT_TO_READY_FOR_SUBMISSION"),
    REVERT_TO_READY_FOR_RECEIVING("REVERT_TO_READY_FOR_RECEIVING"),
    SS2_FAIL_ALL_PLATES_IN_GROUP("FAIL_ALL_PLATES_IN_GROUP"),
    AWAITING_SS2_FEEDBACK("AWAITING_SS2_FEEDBACK"),
    START_SUBMISSION("START_SUBMISSION"),
    START_IMPORT_DECLARATION("START_IMPORT_DECLARATION")

    String transitionName

    public TransitionName(String transitionName) {
        this.transitionName = transitionName
    }

    @Override
    String toString() {
        transitionName
    }
}
