package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolqpcr

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolQPCRPostFunctions

/**
 * Links the scanned PPL tubes, creates a wiki markup table for the Hamilton deck layout,
 * and generates and attaches a csv of 'position (1-24), PPL barcode' for possible use in
 * Hamilton method.
 *
 * Created by as28 on 13/09/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for creating wiki markup table and barcodes csv for entered source PPL tube barcodes"

LibraryPoolQPCRPostFunctions.createListSourceBarcodes(curIssue)
