package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of issue statuses.
 *
 * Created by ke4 on 25/01/2017.
 */
enum IssueStatusName {

    SS2_IN_PROGRESS("SS2 In Progress"),
    PLTSS2_IN_SS2("PltSS2 In SS2"),
    PLTSS2_IN_SUBMISSION("PltSS2 In Submission"),
    PLTSS2_IN_FEEDBACK("PltSS2 In Feedback"),
    PLTSS2_RDY_FOR_SUBMISSION("PltSS2 Rdy for Submission"),
    PLTSS2_WITH_CUSTOMER("PltSS2 With Customer"),
    PLTSS2_DONE_EMPTY("PltSS2 Done Empty"),
    PLTSS2_DONE_NOT_EMPTY("PltSS2 Done Not Empty"),
    PLTDNA_DONE_EMPTY("PltDNA Done Empty"),
    PLTDNA_DONE_NOT_EMPTY("PltDNA Done Not Empty"),
    PLTECH_IN_SPRI("PltECH In SPRi"),
    TUBLPL_IN_POOLING("TubLPL In Pooling"),
    IQC_DONE("IQC Done"),
    QNTA_DONE("QNTA Done")

    String issueStatusName

    public IssueStatusName(String issueStatusName) {
        this.issueStatusName = issueStatusName
    }

    @Override
    String toString() {
        issueStatusName
    }
}
