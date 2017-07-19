package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypooling

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolingPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * This post function gets the LPL tube details from each tube linked to the selected LIB plate issues,
 * then orders and sends a print request to the selected printer.
 *
 * Created by as28 on 05/07/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for the re-printing of LPL tube labels"

// fetch the array of selected plates from the nFeed custom field
String cfAlias = "CURRENT_LIBRARY_POOL_PLATES_IN_PROGRESS"

ArrayList<String> LIBPlateIds = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

// process the plates
if(LIBPlateIds != null) {
    LibraryPoolingPostFunctions.reprintTubeLabelsForPlateIds(curIssue, LIBPlateIds)
}


