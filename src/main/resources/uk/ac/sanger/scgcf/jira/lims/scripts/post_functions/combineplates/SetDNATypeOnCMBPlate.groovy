package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.combineplates

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.CombinePlatesPostFunctions

/**
 * This post function takes the values of the 'DNA Type' field for the four linked
 * source plates and sets the 'DNA Type' on the Combine Plates issue
 *
 * Created by as28 on 20/04/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "PF SetDNATypeOnCMB: Post-function for setting DNA Type in a Combine Plates issue"

CombinePlatesPostFunctions.setDNATypeOnCMBPlate(curIssue)


