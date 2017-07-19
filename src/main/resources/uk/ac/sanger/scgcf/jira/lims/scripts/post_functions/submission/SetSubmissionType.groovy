package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.submission

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.SubmissionPostFunctions

/**
 * Determines and sets the Submission Type field
 *
 * 1. Only Pre-Amp Smart-seq2
 * 2. Only Post-Amp Nextera
 * 3. Both Pre-Amp Smart-seq2 and Post-Amp Nextera
 *
 * Created by as28 on 29/06/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for determining and setting the Submission Type"

SubmissionPostFunctions.determineAndSetSubmissionType(curIssue)