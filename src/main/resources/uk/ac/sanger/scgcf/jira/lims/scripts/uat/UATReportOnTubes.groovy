package uk.ac.sanger.scgcf.jira.lims.scripts.uat

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.user.ApplicationUser
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
            case "UAT Report Created":
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
    LOG.debug "UAT Processing: Report On Tubes"

    // get the source barcodes
    String tubeBarcodes = JiraAPIWrapper.getCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_NORM_TUBE_BARCODES"))
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

	String reportDetails
    reportDetails = UATFunctions.reportOnTubes(tubeBarcodesList)

	def now = System.currentTimeMillis()
	def elapsedTime = now - start
	LOG.debug "Elapsed time in creating report on tubes: ${elapsedTime / 1000} seconds."

	// set the details custom field
    String repString = "Check attachment for report details"
    JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("UAT_REPORT_DETAILS"), repString)

    def attachmentManager = ComponentAccessor.getAttachmentManager()

    JiraAuthenticationContext jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext()
    ApplicationUser user = jiraAuthenticationContext.getLoggedInUser()
    if (user == null) {
        LOG.error "User not found when creating report, cannot create attachment"
        //TODO: error handling
        return
    }
    LOG.debug "User for writing attachment : ${user.getName()}"

    // create File object
    File reportFile = File.createTempFile("temp", null)

    BufferedWriter writer
    try {
        LOG.debug "Write to file"
        writer = new BufferedWriter(new FileWriter(reportFile))
        writer.write(reportDetails)
    } catch (Exception e) {
        LOG.error "Exception writing attachment file for report"
        LOG.error e.printStackTrace()
    } finally {
        if (writer != null) writer.close()
    }

    now = new Date()
    String fileName = "report_${now.format("yyyyMMdd_HHmmss")}.txt"
    LOG.debug "fileName : ${fileName}"

    // write the attachment to the issue
    def bean = new CreateAttachmentParamsBean.Builder()
            .file(reportFile)
            .filename(fileName)
            .contentType("text/html")
            .author(user)
            .issue(curIssue)
            .build()
    attachmentManager.createAttachment(bean)

}