package uk.ac.sanger.scgcf.jira.lims.scripts.uat

import com.atlassian.jira.issue.Issue
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

// check the issue type is as expected
def issueTypeName = curIssue.getIssueType().getName()

// NB. do not check state as this transition can be called from all other states
LOG.debug "issueTypeName = ${issueTypeName}"
switch (issueTypeName) {
    case "Task":
        process( curIssue )
        break
    default:
        // TODO: error handling
        LOG.error "Unrecognised issue type name ${issueTypeName}"
        break
}

void process( Issue curIssue ) {
    LOG.debug "UAT Processing: Reset All Fields for issue key = ${curIssue.getKey()}"

    // fetch field names using aliases
    def fieldNames = [
            ConfigReader.getCFName("UAT_CUST_TUBE_BARCODES"),
            ConfigReader.getCFName("UAT_CUST_TUBE_DETAILS"),
            ConfigReader.getCFName("UAT_SPLIT_PLT_BARCODES"),
            ConfigReader.getCFName("UAT_SPLIT_PLT_DETAILS"),
            ConfigReader.getCFName("UAT_CMB_PLT_BARCODE"),
            ConfigReader.getCFName("UAT_CMB_PLT_DETAILS"),
            ConfigReader.getCFName("UAT_CHRY_PLT_BARCODE"),
            ConfigReader.getCFName("UAT_CHRY_PLT_DETAILS"),
            ConfigReader.getCFName("UAT_STAMP_PLT_BARCODE"),
            ConfigReader.getCFName("UAT_STAMP_PLT_DETAILS"),
            ConfigReader.getCFName("UAT_SEL_STAMP_PLT_BARCODE"),
            ConfigReader.getCFName("UAT_SEL_STAMP_PLT_DETAILS"),
            ConfigReader.getCFName("UAT_POOL_TUBE_BARCODES"),
            ConfigReader.getCFName("UAT_POOL_TUBE_DETAILS"),
            ConfigReader.getCFName("UAT_NORM_TUBE_BARCODES"),
            ConfigReader.getCFName("UAT_NORM_TUBE_DETAILS"),
            ConfigReader.getCFName("UAT_REPORT_DETAILS")
    ]
    LOG.debug fieldNames.toString()

    // clear the custom field value in the issue
    fieldNames.each { curFieldName ->
        LOG.debug "clearing field for curFieldName: ${curFieldName}"
        LOG.debug "curFieldName class = ${curFieldName.getClass()}"
        JiraAPIWrapper.clearCFValueByName( curIssue, curFieldName )
    }
}