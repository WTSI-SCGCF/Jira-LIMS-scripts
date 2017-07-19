package uk.ac.sanger.scgcf.jira.lims.scripts.validators

import com.atlassian.jira.issue.Issue
import com.opensymphony.workflow.InvalidInputException
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler
import uk.ac.sanger.scgcf.jira.lims.validations.SequencescapeEntityState
import uk.ac.sanger.scgcf.jira.lims.validations.SequencescapeValidator

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Validating Sequencescape Project and Study name existence"

def sequencescapeValidator = new SequencescapeValidator()

def invalidInputException = new InvalidInputException()

try {
    // get the project and study name from the issue
    String projectName = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("SEQS_PROJECT_NAME"))
    LOG.debug "The retrieved project name: '$projectName'"

    String studyName = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("SEQS_STUDY_NAME"))
    LOG.debug "The retrieved study name: '$studyName'"

    def projectState = sequencescapeValidator.validateProjectName(projectName)
    if (projectState == SequencescapeEntityState.NOT_EXISTS) {
        invalidInputException.addError(
                JiraAPIWrapper.getCFIDByAliasName("SEQS_PROJECT_NAME"),
                SequencescapeValidator.SS_PROJECT_NOT_EXISTS_ERROR_MESSAGE)
    } else if (projectState == SequencescapeEntityState.INACTIVE) {
        invalidInputException.addError(
                JiraAPIWrapper.getCFIDByAliasName("SEQS_PROJECT_NAME"),
                SequencescapeValidator.SS_PROJECT_NOT_ACTIVE_ERROR_MESSAGE)
    }

    def studyState = sequencescapeValidator.validateStudyName(studyName)
    if (studyState == SequencescapeEntityState.NOT_EXISTS) {
        invalidInputException.addError(
                JiraAPIWrapper.getCFIDByAliasName("SEQS_STUDY_NAME"),
                SequencescapeValidator.SS_STUDY_NOT_EXISTS_ERROR_MESSAGE)
    } else if (studyState == SequencescapeEntityState.INACTIVE) {
        invalidInputException.addError(
                JiraAPIWrapper.getCFIDByAliasName("SEQS_STUDY_NAME"),
                SequencescapeValidator.SS_STUDY_NOT_ACTIVE_ERROR_MESSAGE)
    }
} catch (Exception ex) {
    ValidatorExceptionHandler.throwAndLog(ex, ex.message, null)
}

if (invalidInputException.getErrors().size() > 0) {
    throw invalidInputException
}
