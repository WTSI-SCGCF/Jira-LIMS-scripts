package uk.ac.sanger.scgcf.jira.lims.service_wrappers

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParameters
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
import groovy.util.logging.Slf4j
import com.atlassian.jira.issue.ModifiedValue
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField

/**
 * This class handles interactions with the Jira API
 *
 * N.B. The current issue is bound to the 'issue' variable by the scriptrunner environment
 * so you don't need to work it out.
 *
 * Created by as28 on 23/06/16.
 */

@Slf4j(value = "LOG")
class JiraAPIWrapper {

    /**
     * Get a custom field object from its name
     * @param cfName
     * @return CustomField object
     */
    static CustomField getCustomFieldByName(String cfName) {
        LOG.debug "Custom field name: ${cfName}"
        def customFieldManager = ComponentAccessor.getCustomFieldManager()
        customFieldManager.getCustomFieldObjectByName(cfName)
    }

    /**
     * Get the value of a specified custom field for an issue
     * @param curIssue
     * @param cfName
     * @return String value of custom field
     * TODO: this needs to handle custom fields other than strings
     */
    static String getCustomFieldValueByName(Issue curIssue, String cfName) {
        LOG.debug "Custom field name: ${cfName}"
        String cfValue = getCustomFieldByName(cfName).getValue(curIssue) as String
        LOG.debug("CF value: ${cfValue}")
        cfValue
    }

    /**
     * Set the value of a specified custom field for an issue
     * @param curIssue
     * @param cfName
     * @param newValue
     * TODO: this needs to handle custom fields other than strings
     */
    static void setCustomFieldValueByName(Issue curIssue, String cfName, String newValue) {
        LOG.debug "setCustomFieldValueByName: Custom field name: ${cfName}"
        LOG.debug "setCustomFieldValueByName: New value: ${newValue}"

        IssueService issueService = ComponentAccessor.getIssueService()

        // get the logged in user
        //TODO: will this work in all situations? will we always have a logged in user?
        JiraAuthenticationContext jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext()
        ApplicationUser user = jiraAuthenticationContext.getLoggedInUser()
        if (user == null) {
            LOG.error "setCustomFieldValueByName: User not found when setting custom field with name <${cfName}>, cannot set value"
            //TODO: error handling
            return
        }
        LOG.debug "user : ${user.getName()}"

        // locate the custom field for the current issue from its name
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager()
        def tgtField = customFieldManager.getCustomFieldObjects(curIssue).find { it.name == cfName }
        if (tgtField == null) {
            LOG.error "setCustomFieldValueByName: Custom field with name <${cfName}> was not found, cannot set value"
            //TODO: error handling
            return
        }

        // update the value of the field and save the change in the database
        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters()
        LOG.debug "setCustomFieldValueByName: tgtField ID : ${tgtField.getId()}"

        issueInputParameters.addCustomFieldValue(tgtField.getId(), newValue)

        IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(user, curIssue.getId(), issueInputParameters)

        if (updateValidationResult.isValid()) {
            LOG.debug "setCustomFieldValueByName: Issue update validated, running update"
            IssueService.IssueResult updateResult = issueService.update(user, updateValidationResult);
            if (!updateResult.isValid()) {
                LOG.error "setCustomFieldValueByName: Custom field with name <${cfName}> could not be updated to value <${newValue}>"
                // TODO: error handling
            }
        } else {
            LOG.error "setCustomFieldValueByName: updateValidationResult false, custom field with name <${cfName}> could not be updated to value <${newValue}>"
            // TODO: error handling
        }
    }

    /**
     * Clear the value of a specified custom field for an issue
     * @param cfName
     * TODO: this needs to handle custom fields other than strings
     */
    static void clearCustomFieldValueByName(Issue curIssue, String cfName) {
        setCustomFieldValueByName(curIssue, cfName, "")
    }
}