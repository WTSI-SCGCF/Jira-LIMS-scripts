package uk.ac.sanger.scgcf.jira.lims.validations

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueFieldConstants
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.attachment.FileSystemAttachmentDirectoryAccessor
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.GPathResult
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * The {@code LibraryPoolBAQCValidations} class holds validators for the
 * Library Pool BioAnalyzer QC project
 *
 * Created by as28 on 04/08/2017.
 */

@Slf4j(value = "LOG")
class LibraryPoolBAQCValidations {

    public static void validateAttachedFiles(MutableIssue LQCIssue) throws Exception {

        def attachmentDirectoryAccessor = ComponentAccessor.getComponent(FileSystemAttachmentDirectoryAccessor)
        def temporaryAttachmentDirectory = attachmentDirectoryAccessor.getTemporaryAttachmentDirectory()

        LOG.debug "Attempting to get attachments from modified fields"
        def attachmentTempFileNames = LQCIssue.getModifiedFields().get(IssueFieldConstants.ATTACHMENT)?.newValue
        LOG.debug "Number of attachments found = <${attachmentTempFileNames.size()}>"

        // check for two attachments
        if (attachmentTempFileNames.size() != 2) {
            throw new InvalidInputException("There should be two attached files taken from the BioAnalyzer, one XML file and one PDF images file")
        }

        // check for one PDF attachment and one XML
        int iNumPDFs = 0
        int iNumXMLs = 0

        String sXMLTempFileName

        attachmentTempFileNames.each { String sTempFileName ->
            LOG.debug "Found an attachment"
            LOG.debug "Temporary filename = ${sTempFileName}"
            String sFirstLine
            new File(temporaryAttachmentDirectory, sTempFileName).withReader {
                sFirstLine = it.readLine()
                if(sFirstLine.length() > 20) {
                    sFirstLine = sFirstLine.substring(0,20)
                }
                sFirstLine
            }

            if(sFirstLine != null) {
                LOG.debug "File first line: ${sFirstLine}"

                // check if XML
                if (sFirstLine.toLowerCase().contains('xml')) {
                    LOG.debug "Attachment is an XML file"
                    sXMLTempFileName = sTempFileName
                    iNumXMLs++
                }
                // check if PDF
                if (sFirstLine.toLowerCase().contains('pdf')) {
                    LOG.debug "Attachment is an PDF file"
                    iNumPDFs++
                }
            }
        }

        LOG.debug "Number of XML files = ${iNumXMLs}"
        LOG.debug "Number of PDF files = ${iNumPDFs}"

        // check for one of each file type
        if (iNumXMLs != 1 || iNumPDFs != 1) {
            throw new InvalidInputException("Please attach one XML file (detected: ${Integer.toString(iNumXMLs)}) and one PDF file (detected: ${Integer.toString(iNumPDFs)})")
        }

        // validate XML file and check the samples match to the expected barcodes
        LOG.debug "sXMLTempFileName = ${sXMLTempFileName}"
        String xmlText = new File(temporaryAttachmentDirectory, sXMLTempFileName).text
        GPathResult Chipset = new XmlSlurper().parseText(xmlText)

//        <Chipset xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
//            <Chips>
//                <Chip key="0">
//                    <Files>
//                        <File key="0">
//                            <Samples>
//                                <Sample key="0"> (0-10 are samples, 11 is the ladder)
//                                    <HasData>true</HasData>
//                                    <Category>Sample</Category>
//                                    <Name>manual 1</Name>

        // locate the samples node
        def Samples = Chipset.Chips.Chip[0].Files.File[0].Samples
        if(Samples instanceof GPathResult) {
            LOG.debug "Samples is a GPathResult"
        }
        if(Samples == null) {
            LOG.error "Samples section of attached XML not found"
            throw new InvalidInputException("Cannot identify the Samples section in the attached XML file, please check it is a valid file.")
        }

        // check we can fetch all the samples
        LOG.debug "Number of Samples = ${Samples.Sample.size()}"
        if (Samples.Sample.size() != 12) {
            LOG.error "Samples size not 12"
            throw new InvalidInputException("Cannot identify 12 samples in the Samples section in the attached XML file, please check it is a valid file.")
        }

        // get the CF value for 'Number of Tubes'
        int iNumberOfTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("NUMBER_OF_TUBES"))).intValue()
        LOG.debug "Number of tubes = ${iNumberOfTubes}"

        // get a map of the source barcodes in JIRA
        Map<String, String> barcodesMap = getLPLTubeBarcodesMap(LQCIssue)
        LOG.debug "List of JIRA barcodes = ${barcodesMap.toMapString()}"

        if(barcodesMap.size() != iNumberOfTubes) {
            LOG.error "Source barcodes map size does not match number of tubes"
            throw new InvalidInputException("The list of barcodes retrieved from JIRA does not match the number of tubes, cannot validate XML.")
        }

        // check the JIRA barcodes match the appropriate BioAnalyzer sample names
        (1..iNumberOfTubes).each{ int iTubeNumber ->
            String sTubeNumber = Integer.toString(iTubeNumber)
            String sSampleNumber = Integer.toString(iTubeNumber - 1)

            LOG.debug "Fetching name for sample number <${sSampleNumber}>"
            String sSampleName = Samples.Sample.find { it.'@key' == sSampleNumber }.Name.text()
            LOG.debug "Sample Name = ${sSampleName}"
            LOG.debug "Fetching barcode for tube number <${sTubeNumber}>"
            String sBarcode = barcodesMap[sTubeNumber]
            LOG.debug "Barcode = ${sBarcode}"
            if(sSampleName != sBarcode) {
                LOG.error "Name of the sample <${sSampleName}> at position ${sTubeNumber} in the BioAnalyser XML does not match JIRA source ${sTubeNumber} barcode <${sBarcode}>"
                throw new InvalidInputException("The name of the sample <${sSampleName}> at position ${sTubeNumber} in the BioAnalyser XML does not match JIRA source ${sTubeNumber} barcode <${sBarcode}>, please check you are uploading the correct XML and the order of samples matches and try again or cancel.")
            }
        }

        // TODO: could add a check that sample 12 (key 11) name is 'Ladder'

        LOG.debug "XML looks valid, and Sample names match Barcodes in JIRA"
    }

    /**
     * Helper method to fetch source LPL tube barcodes
     *
     * @param LQCIssue
     * @return map of barcodes keyed by position
     */
    private static Map<String, String> getLPLTubeBarcodesMap(Issue LQCIssue) {

        // fetch Number of Tubes from Library BA QC issue
        int numberOfTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("NUMBER_OF_TUBES"))).intValue()
        LOG.debug "Number of tubes = ${numberOfTubes}"

        Map<String, String> barcodesMap = [:]

        if (numberOfTubes > 0) {
            barcodesMap.put(
                    "1",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_1_BARCODE"))
            )
        }
        if (numberOfTubes > 1) {
            barcodesMap.put(
                    "2",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_2_BARCODE"))
            )
        }
        if (numberOfTubes > 2) {
            barcodesMap.put(
                    "3",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_3_BARCODE"))
            )
        }
        if (numberOfTubes > 3) {
            barcodesMap.put(
                    "4",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_4_BARCODE")),
            )
        }
        if (numberOfTubes > 4) {
            barcodesMap.put(
                    "5",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_5_BARCODE"))
            )
        }
        if (numberOfTubes > 5) {
            barcodesMap.put(
                    "6",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_6_BARCODE"))
            )
        }
        if (numberOfTubes > 6) {
            barcodesMap.put(
                    "7",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_7_BARCODE"))
            )
        }
        if (numberOfTubes > 7) {
            barcodesMap.put(
                    "8",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_8_BARCODE"))
            )
        }
        if (numberOfTubes > 8) {
            barcodesMap.put(
                    "9",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_9_BARCODE"))
            )
        }
        if (numberOfTubes > 9) {
            barcodesMap.put(
                    "10",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_10_BARCODE"))
            )
        }
        if (numberOfTubes > 10) {
            barcodesMap.put(
                    "11",
                    JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("SOURCE_11_BARCODE"))

            )
        }

        LOG.debug "Barcodes map size = ${barcodesMap.size()}"
        LOG.debug barcodesMap.toMapString()
        barcodesMap
    }
}
