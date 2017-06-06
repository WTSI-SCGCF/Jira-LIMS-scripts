package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.dnaquantification

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.DNAQuantificationPostFunctions

/**
 * Called when the user chooses to fail outright the DNA Quantification.
 * Processing depends on whether the plate is derived from 96- or 384-well plates. If the ancestor plate
 * cannot be re-run the plate is put into a feedback state.
 *
 * Created by as28 on 01/06/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue quantAnalysisIssue = issue

LOG.debug "Post-function for a plate that failed Quantification Analysis outright"

DNAQuantificationPostFunctions.quantFailedOutrightProcess(quantAnalysisIssue)