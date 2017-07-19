package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.submission

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.post_functions.PlateRemover

/**
 * This post function extracts a list of selected plates from an nFeed custom field and removes them
 * from the current Submission issue via a function in {@code PlateAdder}.
 * This removes the links of the issues and reverts the plate ticket state if appropriate.
 *
 * Created by ke4 on 06/02/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for removing plates from the Submission"

PlateRemover plateRemover = new PlateRemover(curIssue, IssueTypeName.SUBMISSION.toString(), "GENERIC_REMOVE_PLATES")
plateRemover.execute()