package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolqpcr

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolQPCRPostFunctions

/**
 * Parse the Light Cycler concentration results file.
 *
 * Created by as28 on 13/09/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function to parse the Light Cycler results file and write results into PPL tubes"

LibraryPoolQPCRPostFunctions.parseResultsFile(curIssue)
