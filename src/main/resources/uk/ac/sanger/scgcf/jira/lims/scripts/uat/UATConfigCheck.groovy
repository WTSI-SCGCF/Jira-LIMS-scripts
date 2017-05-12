package uk.ac.sanger.scgcf.jira.lims.scripts.uat

import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

def name = ConfigReader.getCFName("UAT_CUST_TUBE_BARCODES")
LOG.debug "ConfigReader name = ${name}"
name
