package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.spriplatecleanup

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.ReagentLinker

/**
 * This post function extracts a list of selected reagents from an nFeed custom field and links them
 * to the current SPRi Plate Cleanup issue via a function in {@code ReagentLinker}.
 *
 * Created by as28 on 10/05/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Attempting to link SPRi bead reagent(s) to current issue"

ReagentLinker reagentLinker = new ReagentLinker(curIssue, "CURRENT_SPRI_BEAD_BATCHES")
reagentLinker.execute()
