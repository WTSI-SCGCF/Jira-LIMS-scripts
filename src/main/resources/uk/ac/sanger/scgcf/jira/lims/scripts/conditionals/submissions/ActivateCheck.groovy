package uk.ac.sanger.scgcf.jira.lims.scripts.conditionals.submissions

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Conditional check to see if the Activate transition should be visible in a Submission.
 *
 * Created by as28 on 17/07/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Conditional to check if the Activate transition should be visible"
passesCondition = true

String sPostAmpProtocol = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("POST-AMP_PROTOCOL")).toString()
LOG.debug "Post-amp protocol =  ${sPostAmpProtocol}"

if(sPostAmpProtocol == 'Nextera') {
    String sCellsPerLibraryPool = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("CELLS_PER_LIBRARY_POOL")).toString()
    LOG.debug "Cells per pool =  ${sCellsPerLibraryPool}"

    if(sCellsPerLibraryPool == '384') {
        List<Issue> linkedContainers = WorkflowUtils.getContainersLinkedToGroup(curIssue)

        if (linkedContainers.size() > 0) {
            linkedContainers.each { Issue linkedIssue ->
                if (linkedIssue.getIssueType().getName() == IssueTypeName.PLATE_SS2.toString() || linkedIssue.getIssueType().getName() == IssueTypeName.PLATE_DNA.toString()) {

                    // get the Plate Format
                    String sPlateFormat = JiraAPIWrapper.getCFValueByName(linkedIssue, ConfigReader.getCFName("PLATE_FORMAT")).toString()
                    LOG.debug "Plate Format =  ${sPlateFormat}"

                    if (sPlateFormat == '96') {
                        LOG.debug "User has added plate with 96 wells"
                        passesCondition = false
                    }
                }
            }
        }
    }
}
LOG.debug("Check returning: " + passesCondition)