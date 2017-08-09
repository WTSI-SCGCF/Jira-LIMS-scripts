package uk.ac.sanger.scgcf.jira.lims.scripts.validators.librarypoolbioanalyzerqc

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.MutableIssue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler
import uk.ac.sanger.scgcf.jira.lims.validations.LibraryPoolBAQCValidations

/**
 * This validator checks that the required BioAnalyzer files have been attached
 * and that we can parse the XML file
 *
 * Created by as28 on 03/08/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
MutableIssue curIssue = issue

LOG.debug "Executing validation for attached BioAnalyzer files"

try {
    LibraryPoolBAQCValidations.validateAttachedFiles(curIssue)
} catch(Exception ex) {
    ValidatorExceptionHandler.throwAndLog(ex, ex.message, null)
}
true