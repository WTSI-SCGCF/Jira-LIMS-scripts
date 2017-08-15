package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolbioanalyzerqc

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolBAQCPostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Created by as28 on 03/08/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for declaring source LPL tubes that failed BA QC"

// fetch the array of selected plates from the nFeed custom field
String cfAlias = "CURRENT_LIBRARY_POOL_TUBES_IN_PROGRESS_BA_QC"

ArrayList<String> LPLTubeIds = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

// process the plates
if(LPLTubeIds != null) {
    LibraryPoolBAQCPostFunctions.tubesFailedInLibraryQC(curIssue, LPLTubeIds)
}