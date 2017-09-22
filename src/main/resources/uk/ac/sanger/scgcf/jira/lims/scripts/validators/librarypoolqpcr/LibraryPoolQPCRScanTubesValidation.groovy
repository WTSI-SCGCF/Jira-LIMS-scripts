package uk.ac.sanger.scgcf.jira.lims.scripts.validators.librarypoolqpcr

import com.atlassian.jira.issue.MutableIssue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler
import uk.ac.sanger.scgcf.jira.lims.validations.LibraryPoolQPCRValidations

/**
 * This method validates the PPL tube scanning.
 *
 * Created by as28 on 15/09/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
MutableIssue curIssue = issue

LOG.debug "Executing validation for scanned tubes"

try {
    LibraryPoolQPCRValidations.validateScannedTubes(curIssue)
} catch(Exception ex) {
    ValidatorExceptionHandler.throwAndLog(ex, ex.message, null)
}
true