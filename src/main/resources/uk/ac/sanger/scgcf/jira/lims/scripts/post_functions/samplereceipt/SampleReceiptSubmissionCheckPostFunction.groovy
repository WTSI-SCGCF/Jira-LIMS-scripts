package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.samplereceipt

import com.atlassian.jira.issue.Issue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import groovy.transform.Field
import uk.ac.sanger.scgcf.jira.lims.post_functions.SampleReceiptPostFunctions

/**
 * This post function checks linked plates in the receiving group for links to
 * active Submissions using a function in {@code SampleReceiptPostFunctions}.
 * If appropriate the plate is transitioned automatically from Rdy for Submission
 * status to In Submission.
 *
 * Created by as28 on 15/11/2016.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue and transient variables (from binding)
Issue curIssue = issue

LOG.debug "Post-function for checking whether linked plates are connected to a Submission"

// call method to check issues
SampleReceiptPostFunctions.checkLinkedIssuesForSubmissions(curIssue)



