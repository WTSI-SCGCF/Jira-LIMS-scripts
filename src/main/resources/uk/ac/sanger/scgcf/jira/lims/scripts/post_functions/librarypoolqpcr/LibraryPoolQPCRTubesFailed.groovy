package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolqpcr

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolQPCRPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Process the PPL tubes deemed to have failed QPCR.
 *
 * Created by as28 on 13/09/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for declaring source PPL tubes that failed QPCR"

// fetch the array of selected tubes from the nFeed custom field
String cfAlias = "CURRENT_LIBRARY_PPL_TUBES_IN_QPCR"

ArrayList<String> PPLIssueIds = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

// process the tubes
if(PPLIssueIds != null) {
    LibraryPoolQPCRPostFunctions.processTubesFailed(PPLIssueIds)
}
