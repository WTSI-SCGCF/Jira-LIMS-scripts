package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolqpcr

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolQPCRPostFunctions

/**
 * Build the wiki markup results table.
 *
 * Created by as28 on 22/09/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function to build the wiki markup results table from the values in the linked tubes"

LibraryPoolQPCRPostFunctions.buildWikiResultsTable(curIssue)
