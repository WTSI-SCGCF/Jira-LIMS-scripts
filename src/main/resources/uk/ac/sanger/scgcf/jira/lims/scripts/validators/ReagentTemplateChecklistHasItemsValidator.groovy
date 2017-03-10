package uk.ac.sanger.scgcf.jira.lims.scripts.validators

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler
import com.atlassian.jira.issue.fields.CustomField
import com.opensymphony.workflow.InvalidInputException

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue and transient variables (from binding)
Issue curIssue = issue

LOG.debug "Executing Reagent Template checklist has items validation"

String ERROR_MSG = "Please add steps to the Progress of Reagent Creation checklist."
String CHECKLIST_ALIAS = "PROGRESS_OF_REAGENT_CREATION"
String COMMON_ERROR_MESSAGE = "The checklist marked below requires items. Remember to press Enter after typing the text for each item."

try {

    CustomField customfieldToValidate = JiraAPIWrapper.getCustomFieldByName(ConfigReader.getCustomFieldName(CHECKLIST_ALIAS))

    def invalidInputException

    if(customfieldToValidate == null) {
        LOG.error "Failed to identify Reagent Template checklist custom field"
        invalidInputException = new InvalidInputException("Failed to identify checklist custom field with alias name: " + CHECKLIST_ALIAS)
    } else {
        Collection checklistItems = (Collection)curIssue.getCustomFieldValue(customfieldToValidate)
        if(checklistItems == null) {
            LOG.debug "Reagent Template checklist has no items"
            invalidInputException = new InvalidInputException(customfieldToValidate.id, ERROR_MSG)
        } else {
            if(checklistItems.size() == 0) {
                LOG.debug "Reagent Template checklist size is zero"
                invalidInputException = new InvalidInputException(customfieldToValidate.id, ERROR_MSG)
            } else {
                LOG.debug "Checklist size is: ${checklistItems.size()}"
            }
        }
    }

    LOG.debug "Reagent Template checklist has items validation finished"

    if (invalidInputException) {
        invalidInputException.addError(COMMON_ERROR_MESSAGE)

        throw invalidInputException
    }

    return true

} catch (Exception ex) {
    ValidatorExceptionHandler.throwAndLog(ex, ex.message, null)
}
