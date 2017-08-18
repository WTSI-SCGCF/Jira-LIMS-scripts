package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolprenormalisation

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolPreNormPostFunctions

/**
 * Create the PPL tubes and print their labels.
 *
 * Created by as28 on 17/08/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for creating the PPL tubes and printing their labels"

// call method to create PPL tubes and build wiki markup table (return string list of PPL ids)
List<String> listPPLIds = LibraryPoolPreNormPostFunctions.createPPLTubesAndCalculateDilutions(curIssue)

// call method to print PPL tube labels for set of issue ids (re-use this in re-print script, takes list of PPL ids)
if(listPPLIds != null && listPPLIds.size() > 0) {
    LOG.debug "Attempting to print PPL tubes labels for list of PPL ids:"
    LOG.debug listPPLIds.toListString()
    LibraryPoolPreNormPostFunctions.printPPLTubeLabelsForPPLIssueIds(listPPLIds)
} else {
    LOG.error "Failed to print PPL tube labels, no valid set of PPL ids returned from create PPLs method"
}