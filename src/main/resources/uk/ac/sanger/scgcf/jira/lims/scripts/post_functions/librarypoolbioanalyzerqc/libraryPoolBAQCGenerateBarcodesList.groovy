package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolbioanalyzerqc

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolBAQCPostFunctions

/**
 * Create a source barcodes list and write it to the custom field.
 * The users will copy and paste this into the BioAnalyzer software as the sample names.
 *
 * Created by as28 on 03/08/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for the generation of a barcodes list for all linked LPL tubes"

LibraryPoolBAQCPostFunctions.generateBarcodesList(curIssue)

