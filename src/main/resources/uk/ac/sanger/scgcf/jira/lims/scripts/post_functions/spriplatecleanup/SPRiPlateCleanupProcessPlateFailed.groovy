package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.spriplatecleanup

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.SPRiPlateCleanupPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Processes a plate that has failed SPRi.
 *
 * Created by as28 on 11/05/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for a plate that failed SPRi cleanup"

// fetch the array of selected plates from the nFeed custom field
String cfAlias = "CURRENT_SPRI_PLATES_IN_PROGRESS_FOR_FAILING"

ArrayList<String> ids = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

if(ids != null) {
    if(ids.size() > 1) {
        LOG.error "Unexpected number of plates <${ids.size()}>, should only be one"
    }

    // process the plate
    SPRiPlateCleanupPostFunctions.processPlateFailedCleanup(curIssue, ids[0])
}
