package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkType
import com.atlassian.jira.util.ErrorCollection
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Created by as28 on 15/11/2016.
 */

@Slf4j(value = "LOG")
class SampleReceiptPostFunctions {

    /**
     * Check the issue for a link to a Submission
     *
     * @param linkedIssueId the id of the issue to be checked
     * @return boolean flag
     */
    private static boolean checkForSubmissionLink(Long linkedIssueId) {
        LOG.debug("checkForSubmissionLink: Checking links from issue with id ${linkedIssueId}".toString())
        // get the issue link type
        IssueLinkType issLnkType = WorkflowUtils.getIssueLinkType(IssueLinkTypeName.GROUP_INCLUDES.toString())

        // get the inward links from the linked issue
        List<IssueLink> inwardLinksList = WorkflowUtils.getInwardLinksListForIssueId(linkedIssueId)

        // check if the linked issue has itself got a link to a Submission
        IssueLink resultLink = inwardLinksList.find { IssueLink issLink ->
            issLink.getIssueLinkType() == issLnkType &&
                    issLink.getSourceObject().getIssueType().name == IssueTypeName.SUBMISSION.toString()
        }
        if (resultLink) {
            return true
        }
        false
    }

    /**
     * For a sample receipt issue check all linked plates for themselves having a link
     * to a Submission, and if so transition the plate to 'In Submission'
     *
     * @param sampleReceiptIssue
     */
    public static void checkLinkedIssuesForSubmissions(Issue sampleReceiptIssue) {
        LOG.debug("Checking for plates linked to a Submission")
        // get the issue link type
        IssueLinkType plateLinkType = WorkflowUtils.getIssueLinkType(IssueLinkTypeName.GROUP_INCLUDES.toString())

        // get the outward linked plate issues from the Sample Receipt issue
        List<IssueLink> outwardLinksList = WorkflowUtils.getOutwardLinksListForIssueId(sampleReceiptIssue.getId())

        // get the transition action id
        int actionId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_SS2.toString(), TransitionName.START_SUBMISSION.toString())

        // for each issue linked to the sample receipt
        outwardLinksList.each { IssueLink issLink ->
            Issue linkedIssue = issLink.getDestinationObject()

            // only check links in plates
            if (linkedIssue.getIssueType().name == IssueTypeName.PLATE_SS2.toString()
                    && issLink.getIssueLinkType() == plateLinkType) {
                if (checkForSubmissionLink(linkedIssue.getId())) {
                    LOG.debug("Transitioning linked plate with Id ${linkedIssue.getId()} and summary ${linkedIssue.getSummary()}".toString())
                    MutableIssue mutableIssue = WorkflowUtils.getMutableIssueForIssueId(linkedIssue.getId())
                    if (mutableIssue != null && mutableIssue.getIssueType().getName() == IssueTypeName.PLATE_SS2.toString()
                            && mutableIssue.getStatus().getName() == IssueStatusName.PLATESS2_RDY_FOR_SUBMISSION.toString()) {
                        // transition the issue to In Submission
                        WorkflowUtils.transitionIssue(mutableIssue, actionId)
                    }
                }
            }
        }
    }
}
