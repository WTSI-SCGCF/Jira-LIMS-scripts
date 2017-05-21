package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.dnaquantification

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.ReagentLinker

/**
 * Link DNA Quantification reagents
 *
 * Created by as28 on 19/05/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Attempting to link DNA Quantification Run standard reagent(s) to current issue"

ReagentLinker reagentLinker = new ReagentLinker(curIssue, "CURRENT_QUANT_STANDARDS")
reagentLinker.execute()
