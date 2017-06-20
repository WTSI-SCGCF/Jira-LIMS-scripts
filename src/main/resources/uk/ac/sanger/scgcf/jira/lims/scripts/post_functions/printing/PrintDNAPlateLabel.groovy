package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.printing

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.enums.BarcodeInfos
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * Print DNA plate labels
 *
 * Created by as28 on 15/06/2017.
 */

@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

Issue curIssue = issue

LOG.debug "Printing DNA plate barcode labels"

WorkflowUtils.printPlateLabels(curIssue, BarcodeInfos.INFO_DNA.toString())
