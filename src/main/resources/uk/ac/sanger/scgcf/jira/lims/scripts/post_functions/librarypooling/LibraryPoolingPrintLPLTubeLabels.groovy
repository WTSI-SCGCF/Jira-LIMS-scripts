package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypooling

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolingPostFunctions

/**
 * This post function gets the LPL tube details from each tube linked to the Library Pool issue,
 * then orders and sends a print request to the selected printer.
 *
 * Created by as28 on 05/07/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for the printing of LPL tube labels for all linked LIB plates"

LibraryPoolingPostFunctions.printLibraryPoolingTubeLabels(curIssue)
