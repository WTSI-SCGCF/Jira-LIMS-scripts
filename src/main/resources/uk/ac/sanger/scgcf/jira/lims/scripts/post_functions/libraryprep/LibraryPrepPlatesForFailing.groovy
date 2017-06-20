package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.libraryprep

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPrepPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * User selected 1 or more plates as having failed after Normalisation and Nextera library prep.
 * Process the source and destination issues relating to the selected source issue ids from the nFeed field.
 *
 * Created by as28 on 15/06/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for plates to be re-run after Normalised and Nextera library prep"

// fetch the array of selected plates from the nFeed custom field
String cfAlias = "CURRENT_LIBRARY_PREP_PLATES_IN_PROGRESS"

ArrayList<String> ids = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

// process the plates
if(ids != null) {
    LibraryPrepPostFunctions.processPlatesForFailing(curIssue, ids)
}
