package uk.ac.sanger.scgcf.jira.lims.scripts.uat

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.actions.UATFunctions
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
			case "UAT Cherry Picked Plate Created":
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
	LOG.debug "UAT Processing: Cherry Pick Plates"

	// get the source plate barcodes
	String plateBarcodes = JiraAPIWrapper.getCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_SPLIT_PLT_BARCODES"))
	LOG.debug "source plate barcodes = ${plateBarcodes}"

    // split this into a list on comma and check it has 4 entries
    ArrayList<String> plateBarcodesList = plateBarcodes.split(/,/)
	LOG.debug "plateBarcodesList = ${plateBarcodesList}"

	if(plateBarcodesList.size() != 4) {
		LOG.error "Expected split plates barcode list size of 4 but got ${plateBarcodesList.size()}"
		//TODO: how to stop transition or error gracefully
		return
	}

	// send to UATFunction and return plate barcode and details
    def start = System.currentTimeMillis()

	String chryPickPlateBarcode, chryPickPlateDetails
	(chryPickPlateBarcode, chryPickPlateDetails) = UATFunctions.cherryPickPlates(plateBarcodesList)

	def elapsedTime = System.currentTimeMillis() - start
    LOG.debug "Elapsed time in cherry pick: ${elapsedTime / 1000} seconds."

	// set the barcodes custom field
	JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_CHRY_PLT_BARCODE"), chryPickPlateBarcode)

	// set the details custom field
	JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_CHRY_PLT_DETAILS"), chryPickPlateDetails)
}