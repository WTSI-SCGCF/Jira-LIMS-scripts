package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
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
    String issueTypeName
    String customFieldName

    public ReagentLinker(Issue curIssue, String issueTypeName, String customFieldName) {
        this.curIssue = curIssue
        this.issueTypeName = issueTypeName
        this.customFieldName = customFieldName
    }

    public void execute() {
        if (!(curIssue != null && issueTypeName != null && customFieldName != null)) {
            InvalidInputException invalidInputException =
                    new InvalidInputException("The passed arguments are invalid."
                            + "[curIssue: $curIssue, issueTypeName: $issueTypeName, customFieldName: $customFieldName]")
            ValidatorExceptionHandler.throwAndLog(invalidInputException, invalidInputException.message, null)
        }

        LOG.debug "Post-function for adding reagents to a $issueTypeName".toString()

        // fetch the array of selected reagents from the nFeed custom field
        def customFieldManager = ComponentAccessor.getCustomFieldManager()
        def customField = customFieldManager.getCustomFieldObject(ConfigReader.getCFId(customFieldName))

        if(customField != null) {
            // the value of the nFeed field is a list of long issue ids for the selected reagents
            String[] arrayReagentIds = curIssue.getCustomFieldValue(customField)

            // if user hasn't selected anything do nothing further
            if (arrayReagentIds == null) {
                LOG.debug("No reagents selected, nothing to do")
                return
            }

            // link and transition the plate issue(s)
            WorkflowUtils.linkReagentsToGivenIssue(arrayReagentIds, curIssue, issueTypeName)

        } else {
            LOG.error("Failed to get the reagent list custom field for adding reagents to $issueTypeName".toString())
        }
    }
}
