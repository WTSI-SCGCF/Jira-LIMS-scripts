package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * This post function links the reagents selected from an nFeed custom field to the given issue.
 *
 * Created by ke4 on 10/02/2017.
 */
@Slf4j(value = "LOG")
class ReagentLinker {

    Issue curIssue
    String cfAlias

    public ReagentLinker(Issue curIssue, String cfAlias) {
        this.curIssue = curIssue
        this.cfAlias = cfAlias
    }

    public void execute() {
        if (!(curIssue != null && cfAlias != null)) {
            InvalidInputException invalidInputException =
                    new InvalidInputException("The passed arguments are invalid."
                            + "[curIssue: $curIssue, customFieldName: $cfAlias]")
            ValidatorExceptionHandler.throwAndLog(invalidInputException, invalidInputException.message, null)
        }

        LOG.debug "Post-function for adding reagents to issue with id ${curIssue.id}".toString()

        ArrayList<String> ids = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

        // link and transition the plate issue(s)
        if(ids != null) {
            WorkflowUtils.linkReagentsToGivenIssue(ids, curIssue)
        }
    }
}
