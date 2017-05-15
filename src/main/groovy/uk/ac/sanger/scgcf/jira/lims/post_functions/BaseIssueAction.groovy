package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler

/**
 * Base implementation class for an issue action.
 *
 * Created by ke4 on 16/02/2017.
 */
@Slf4j(value = "LOG")
abstract class BaseIssueAction implements IssueAction {

    Issue curIssue
    String issueTypeName
    String customFieldName

    public BaseIssueAction(Issue curIssue, String issueTypeName, String customFieldName) {
        this.curIssue = curIssue
        this.issueTypeName = issueTypeName
        this.customFieldName = customFieldName
    }

    protected void validateParameters() {
        if (!(curIssue != null && issueTypeName != null && customFieldName != null)) {
            InvalidInputException invalidInputException =
                    new InvalidInputException("The passed arguments are invalid."
                            + "[curIssue: $curIssue, workflowName: $issueTypeName, customFieldName: $customFieldName]")
            ValidatorExceptionHandler.throwAndLog(invalidInputException, invalidInputException.message, null)
        }
    }

}
