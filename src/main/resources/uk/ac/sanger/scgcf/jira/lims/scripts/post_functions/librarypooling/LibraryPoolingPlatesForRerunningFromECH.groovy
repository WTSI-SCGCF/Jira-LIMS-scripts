package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypooling

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolingPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * This post function allows the user to select which source LIB plates need to be re-run from
 * their ECH source plates, repeating library preparation.
 *
 * Created by as28 on 05/07/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for declaring source LIB plates that need to be re-run from their ECH plates"

// fetch the array of selected plates from the nFeed custom field
String cfAlias = "CURRENT_LIBRARY_POOL_PLATES_IN_PROGRESS"

ArrayList<String> LIBPlateIds = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

// process the plates
if(LIBPlateIds != null) {
    LibraryPoolingPostFunctions.platesForReRunningFromECH(curIssue, LIBPlateIds)
}