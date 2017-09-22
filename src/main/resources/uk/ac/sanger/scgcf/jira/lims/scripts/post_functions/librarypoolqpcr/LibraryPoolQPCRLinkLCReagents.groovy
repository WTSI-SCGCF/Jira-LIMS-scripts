package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolqpcr

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolQPCRPostFunctions
import uk.ac.sanger.scgcf.jira.lims.post_functions.ReagentLinker

/**
 * This post function extracts lists of selected reagents from two nFeed custom fields and links them
 * to the current Library Pool QPCR issue via a function in {@code ReagentLinker}.
 *
 * Created by as28 on 13/09/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Attempting to link Library QPCR reagents"

ReagentLinker reagentLinkerChips = new ReagentLinker(curIssue, "CURRENT_LIBRARY_QPCR_LC_KITS")
reagentLinkerChips.execute()

ReagentLinker reagentLinkerRegs = new ReagentLinker(curIssue, "CURRENT_LIBRARY_QPCR_LC_STANDARDS")
reagentLinkerRegs.execute()

