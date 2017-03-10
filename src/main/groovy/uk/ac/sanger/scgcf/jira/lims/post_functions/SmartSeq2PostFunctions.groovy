package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.MutableIssue
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code SmartSeq2PostFunctions} class holds post functions for the Smart-seq2 project
 *
 * Created by ke4 on 26/01/2017.
 */

@Slf4j(value = "LOG")
class SmartSeq2PostFunctions {

    /**
     * Link a list of plates to the specified Submission issue and transition them if appropriate.
     *
     * @param arrayPlateIds the list of plate issue ids
     * @param submIssue the Submission issue
     */
    public static void transitionPlates(String[] arrayPlateIds, WorkflowName workflowName, IssueTypeName issueTypeName,
                                        IssueStatusName fromIssueStatusName, IssueStatusName toIssueStatusName,
                                        TransitionName transitionName) {

        // get the transition action id
        int actionId = ConfigReader.getTransitionActionId(workflowName.toString(), transitionName.toString())

        // transition each plate to the given state
        arrayPlateIds.each { String plateIdString ->
            Long plateIdLong = Long.parseLong(plateIdString)
            LOG.debug("Transition plate with ID ${plateIdString} to ${toIssueStatusName}".toString())
            MutableIssue mutableIssue = WorkflowUtils.getMutableIssueForIssueId(plateIdLong)

            if(mutableIssue != null && mutableIssue.getIssueType().getName() == issueTypeName.toString()) {
                // transition the issue to 'In Submission' if it is 'Rdy for Submission'
                if(mutableIssue.getStatus().getName() == fromIssueStatusName.toString()) {
                    WorkflowUtils.transitionIssue(mutableIssue, actionId)
                }
            }
        }
    }
}
