package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolprenormalisation

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolPreNormPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Set the LPL tubes which completed Library Pool Pre-normalisation successfully but are now empty
 * and so cannot be used again.
 *
 * Created by as28 on 17/08/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for declaring source LPL tubes that completed pre-normalisation successfully but are now empty"

// fetch the array of selected tubes from the nFeed custom field
String cfAlias = "CURRENT_LIBRARY_POOL_TUBES_IN_PROGRESS_PNM"

ArrayList<String> LPLTubeIds = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

// process the tubes
if(LPLTubeIds != null) {
    LibraryPoolPreNormPostFunctions.processTubesOkEmptyForLibraryPoolPreNormalisation(curIssue, LPLTubeIds)
}
