package uk.ac.sanger.scgcf.jira.lims.scripts.uat

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.actions.UATFunctions
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

// check the issue type and state match those expected, if not return error
if (curIssue == null) {
    // TODO: error handling
    LOG.error "No current issue found, cannot continue"
    return
}

// check the issue type and status are as expected
def issueTypeName = curIssue.getIssueType().getName()
def issueStatusName = curIssue.getStatus().getName()

switch (issueTypeName) {
    case "Task":
        switch (issueStatusName) {
            case "UAT Pooled Tubes Created":
                process( curIssue )
                break
            default:
                // TODO: error handling
                LOG.error "Unrecognised status name ${issueStatusName}"
                break
        }
        break
    default:
        // TODO: error handling
        LOG.error "Unrecognised issue type name ${issueTypeName}"
        break
}

void process( Issue curIssue ) {
    LOG.debug "UAT Processing: Pool To Tubes"

    // get the source plate barcode
    String plateBarcode = JiraAPIWrapper.getCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_SEL_STAMP_PLT_BARCODE"))
    LOG.debug "source plate barcode = ${plateBarcode}"

    // send to UATFunction and return tube barcodes and details
	def start = System.currentTimeMillis()

	String poolTubeBarcodes, poolTubeDetails
    (poolTubeBarcodes, poolTubeDetails) = UATFunctions.poolToTubes(plateBarcode)

	def now = System.currentTimeMillis()
	def elapsedTime = now - start
	LOG.debug "Elapsed time in pool to tubes: ${elapsedTime / 1000} seconds."

	LOG.debug "poolTubeBarcodes = ${poolTubeBarcodes}"
    LOG.debug "poolTubeDetails = ${poolTubeDetails}"

    // set the barcodes custom field
    JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_POOL_TUBE_BARCODES"), poolTubeBarcodes)

    // set the details custom field
    JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_POOL_TUBE_DETAILS"), poolTubeDetails)
}