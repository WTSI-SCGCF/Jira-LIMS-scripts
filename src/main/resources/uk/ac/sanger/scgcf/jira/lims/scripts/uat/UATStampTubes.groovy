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
            case "UAT Stamped Tubes Created":
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
    LOG.debug "UAT Processing: Stamp Tubes"

    // get the source barcodes
    String tubeBarcodes = JiraAPIWrapper.getCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_POOL_TUBE_BARCODES"))
    LOG.debug "tubeBarcodes = ${tubeBarcodes}"

    // split this into a list on comma and check it has 4 entries
    ArrayList<String> tubeBarcodesList = tubeBarcodes.split(/,/)
    LOG.debug "tubeBarcodesList = ${tubeBarcodesList}"

    if(tubeBarcodesList.size() != 4) {
        LOG.error "Expected split tubes barcode list size of 4 but got ${tubeBarcodesList.size()}"
        //TODO how to stop transition or error gracefully
        return
    }

    // send to UATFunction and return tube barcodes and details
    def start = System.currentTimeMillis()

    String normTubeBarcodes, normTubeDetails
    (normTubeBarcodes, normTubeDetails) = UATFunctions.stampTubes(tubeBarcodesList)

    def now = System.currentTimeMillis()
    def elapsedTime = now - start
    LOG.debug "Elapsed time in stamp tubes: ${elapsedTime / 1000} seconds."

    LOG.debug "normTubeBarcodes = ${normTubeBarcodes}"
    LOG.debug "normTubeDetails = ${normTubeDetails}"

    // set the barcodes custom field
    JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_NORM_TUBE_BARCODES"), normTubeBarcodes)

    // set the details custom field
    JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_NORM_TUBE_DETAILS"), normTubeDetails)
}