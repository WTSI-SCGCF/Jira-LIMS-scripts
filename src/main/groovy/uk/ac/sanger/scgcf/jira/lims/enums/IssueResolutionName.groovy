package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for the name of issue resolutions
 *
 * Created by as28 on 27/07/2017
 */
enum IssueResolutionName {

    COMPLETED("Completed")

    String issueResolutionName

    public IssueResolutionName(String issueResolutionName) {
        this.issueResolutionName = issueResolutionName
    }

    @Override
    String toString() {
        issueResolutionName
    }
}
