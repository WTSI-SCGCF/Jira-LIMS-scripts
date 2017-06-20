package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.libraryprep

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.ReagentLinker

/**
 * Link Nextera Library prep reagents
 *
 * Created by as28 on 15/06/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Attempting to link Nextera reagent(s) to current issue"

// Index reagent
ReagentLinker reagentLinkerIndexes = new ReagentLinker(curIssue, "CURRENT_NEXTERA_INDEX_REAGENTS")
reagentLinkerIndexes.execute()

// Kappa reagent
ReagentLinker reagentLinkerKappa = new ReagentLinker(curIssue, "CURRENT_NEXTERA_KAPPA_REAGENTS")
reagentLinkerKappa.execute()

// P1 reagent
ReagentLinker reagentLinkerP1 = new ReagentLinker(curIssue, "CURRENT_NEXTERA_P1_REAGENTS")
reagentLinkerP1.execute()

// P2 reagent
ReagentLinker reagentLinkerP2 = new ReagentLinker(curIssue, "CURRENT_NEXTERA_P2_REAGENTS")
reagentLinkerP2.execute()

// SDS reagent
ReagentLinker reagentLinkerSDS = new ReagentLinker(curIssue, "CURRENT_NEXTERA_SDS_REAGENTS")
reagentLinkerSDS.execute()