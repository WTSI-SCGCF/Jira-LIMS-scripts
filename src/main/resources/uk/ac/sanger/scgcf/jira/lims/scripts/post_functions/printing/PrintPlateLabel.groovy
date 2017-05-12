package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.printing

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.LabelTemplates
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.PrintLabelAction
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * This script sends plate label(s) for printing to the given label printer.
 *
 * Created by ke4 on 15/03/2017.
 */

@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

Issue curIssue = issue

LOG.debug "Printing plate's barcode label(s)"

String printerName = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("PRINTER_FOR_PLATE_LABELS"))
int numberOfLabels = Double.valueOf(JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("NUMBER_OF_PLATES"))).intValue()
//TODO: add checks for null pointer exceptions in number of plates and set numberoflabels to 1
def labelTemplate = LabelTemplates.LABEL_STANDARD_6MM_PLATE
def labelData = [:]

PrintLabelAction printLabelAction =
        new PrintLabelAction(printerName, numberOfLabels, labelTemplate, labelData)
printLabelAction.execute()
