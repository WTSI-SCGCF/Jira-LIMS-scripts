package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.submission

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.post_functions.PlateAdder

/**
 * This post function extracts a list of selected SS2 plates from an nFeed custom field and adds them
 * to the current Submission via a function in {@code PlateAdder}.
 * This links the issues and transitions the plate ticket state if appropriate.
 *
 * Created by as28 on 04/11/2016.
 * Modified by ke4 on 06/02/2017.
 * Modified by as28 on 30/06/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function to add SS2 plates to the Submission"

PlateAdder plateAdder = new PlateAdder(curIssue, IssueTypeName.SUBMISSION.toString(), "CURRENT_SS2_PLATES_FOR_SUBMISSION")
plateAdder.execute()