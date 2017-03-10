package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of issue statuses.
 *
 * Created by ke4 on 25/01/2017.
 */
enum IssueStatusName {

    SS2_IN_PROGRESS("SS2 In Progress"),
    PLATESS2_IN_SS2("PltSS2 In SS2"),
    PLATESS2_IN_SUBMISSION("PltSS2 In Submission"),
    PLATESS2_IN_FEEDBACK("PltSS2 In Feedback"),
    PLATESS2_RDY_FOR_SUBMISSION("PltSS2 Rdy for Submission"),
    PLATESS2_PLTSS2_WITH_CUSTOMER("PltSS2 With Customer")


    String issueStatusName

    public IssueStatusName(String issueStatusName) {
        this.issueStatusName = issueStatusName
    }

    @Override
    String toString() {
        issueStatusName
    }
}
