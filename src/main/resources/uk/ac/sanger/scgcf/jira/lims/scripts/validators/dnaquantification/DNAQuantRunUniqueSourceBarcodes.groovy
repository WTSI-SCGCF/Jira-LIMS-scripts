package uk.ac.sanger.scgcf.jira.lims.scripts.validators.dnaquantification

import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.opensymphony.workflow.InvalidInputException
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler

/**
 * Validator to check that Source barcodes 1-8 are all distinct from one another.
 *
 * Created by as28 on 19/05/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Attempting to check the DNA Quantification Run source barcodes are all distinct"

CustomField cfSourceBC1 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_1_BARCODE"))
CustomField cfSourceBC2 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_2_BARCODE"))
CustomField cfSourceBC3 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_3_BARCODE"))
CustomField cfSourceBC4 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_4_BARCODE"))
CustomField cfSourceBC5 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_5_BARCODE"))
CustomField cfSourceBC6 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_6_BARCODE"))
CustomField cfSourceBC7 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_7_BARCODE"))
CustomField cfSourceBC8 = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("SOURCE_8_BARCODE"))

InvalidInputException invalidInputException

try {
    String sCfSourceBC1 = curIssue.getCustomFieldValue(cfSourceBC1)
    if (sCfSourceBC1 == null) {
        throw new InvalidInputException(cfSourceBC1.id, "Source 1 Barcode is empty, please scan all 8 barcodes")
    }
    String sCfSourceBC2 = curIssue.getCustomFieldValue(cfSourceBC2)
    if (sCfSourceBC2 == null) {
        throw new InvalidInputException(cfSourceBC2.id, "Source 2 Barcode is empty, please scan all 8 barcodes")
    }
    String sCfSourceBC3 = curIssue.getCustomFieldValue(cfSourceBC3)
    if (sCfSourceBC3 == null) {
        throw new InvalidInputException(cfSourceBC3.id, "Source 3 Barcode is empty, please scan all 8 barcodes")
    }
    String sCfSourceBC4 = curIssue.getCustomFieldValue(cfSourceBC4)
    if (sCfSourceBC4 == null) {
        throw new InvalidInputException(cfSourceBC4.id, "Source 4 Barcode is empty, please scan all 8 barcodes")
    }
    String sCfSourceBC5 = curIssue.getCustomFieldValue(cfSourceBC5)
    if (sCfSourceBC5 == null) {
        throw new InvalidInputException(cfSourceBC5.id, "Source 5 Barcode is empty, please scan all 8 barcodes")
    }
    String sCfSourceBC6 = curIssue.getCustomFieldValue(cfSourceBC6)
    if (sCfSourceBC6 == null) {
        throw new InvalidInputException(cfSourceBC6.id, "Source 6 Barcode is empty, please scan all 8 barcodes")
    }
    String sCfSourceBC7 = curIssue.getCustomFieldValue(cfSourceBC7)
    if (sCfSourceBC7 == null) {
        throw new InvalidInputException(cfSourceBC7.id, "Source 7 Barcode is empty, please scan all 8 barcodes")
    }
    String sCfSourceBC8 = curIssue.getCustomFieldValue(cfSourceBC8)
    if (sCfSourceBC8 == null) {
        throw new InvalidInputException(cfSourceBC8.id, "Source 8 Barcode is empty, please scan all 8 barcodes")
    }
    String[] bcList = [sCfSourceBC1, sCfSourceBC2, sCfSourceBC3, sCfSourceBC4, sCfSourceBC5, sCfSourceBC6, sCfSourceBC7, sCfSourceBC8]

    if (bcList.length != 8) {
        throw new InvalidInputException("Please check all 8 Source Barcodes have been scanned")
    }

    String[] bcListUnique = bcList.toUnique()

    if (bcList.length != bcListUnique.length) {
        throw new InvalidInputException("Duplicate barcodes detected, please check you haven't scanned the same Source Barcode multiple times")
    }

} catch (Exception ex) {
    ValidatorExceptionHandler.throwAndLog(ex, ex.message, null)
}
true