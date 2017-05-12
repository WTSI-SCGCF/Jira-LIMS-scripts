package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.spriplatecleanup

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.post_functions.SPRiPlateCleanupPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Processes one or more plates that have completed SPRi successfully.
 *
 * Created by as28 on 10/05/2017.
 */


// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for plates successfully SPRi cleaned into ECH destinations"

// fetch the array of selected plates from the nFeed custom field
String cfAlias = "CURRENT_SPRI_PLATES_IN_PROGRESS_FOR_OK"

ArrayList<String> ids = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

// process the plates
if(ids != null) {
    SPRiPlateCleanupPostFunctions.processPlatesSuccessfullyCleaned(curIssue, ids)
}