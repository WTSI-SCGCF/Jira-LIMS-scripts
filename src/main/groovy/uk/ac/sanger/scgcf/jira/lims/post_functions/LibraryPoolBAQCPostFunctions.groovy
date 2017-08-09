package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.AttachmentManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueFieldConstants
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.attachment.Attachment
import com.atlassian.jira.issue.attachment.FileSystemAttachmentDirectoryAccessor
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.GPathResult
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code LibraryPoolBAQCPostFunctions} class holds post functions for the
 * Library Pool BioAnalyzer QC project
 *
 * Created by as28 on 04/08/2017.
 */

@Slf4j(value = "LOG")
class LibraryPoolBAQCPostFunctions {

    /**
     * Generate the list of barcodes from the sources scanned in and write it to a text field in the issue.
     * The Users will copy and paste this list into the BioAnalyzer samples list.
     *
     * @param LQCIssue
     */
    public static void generateBarcodesList(Issue LQCIssue) {

        // Get the 'Number of Tubes' custom field value
        int iExpectedNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Expected number of LIB plates = ${iExpectedNumTubes}"

        // Get the LPL tubes linked to this issue
        List<Issue> linkedContainers = WorkflowUtils.getContainersLinkedToGroup(LQCIssue)

        // check that some tubes are linked
        if(linkedContainers.size() <= 0) {
            LOG.error "Expected to find LPL tube issues linked to the Library Pool BA QC but none found, cannot continue"
            return
        }

        // check if all tubes have been scanned yet
        if(linkedContainers.size() != iExpectedNumTubes) {
            LOG.debug "Number of linked LPL tubes does not match total expected, scanning not yet complete"
            return
        }

        Map<String, String> LPLBarcodesMap = getLPLTubeBarcodesMap(LQCIssue)

        if(LPLBarcodesMap.size() != iExpectedNumTubes) {
            LOG.error "Number of barcodes mapped <${LPLBarcodesMap.size()}> does not match expected, cannot continue"
            return
        }

        // generate the barcodes list string
        def barcodesSB = ''<<''
        (1..11).each { int iPosn ->
            String sPosn = Integer.toString(iPosn)
            if(iPosn <= iExpectedNumTubes) {
                barcodesSB <<= LPLBarcodesMap[sPosn]
            } else {
                barcodesSB <<= sPosn + '_EMPTY'
            }
            if(iPosn < 11) {
                barcodesSB <<= System.getProperty("line.separator")
            }
        }

        //   Set CF 'Barcodes List'
        JiraAPIWrapper.setCFValueByName(LQCIssue, ConfigReader.getCFName("BARCODES_LIST"), barcodesSB.toString())

    }

    /**
     * Parse the BioAnalyzer XML file to extract the Library results details.
     *
     * @param LQCIssue
     */
    public static void parseBioAnalyzerXML(MutableIssue LQCIssue) {

        // Get the 'Number of Tubes' custom field value
        int iNumberOfTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(LQCIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of LPL tubes = ${Integer.toString(iNumberOfTubes)}"

        // extract the source barcodes map according to Number of Tubes ([1:BC1, 2:BC2 etc.]
        Map<String, String> LPLBarcodesMap = getLPLTubeBarcodesMap(LQCIssue)

        if(LPLBarcodesMap.size() != iNumberOfTubes) {
            LOG.error "Source barcodes map size does not match number of tubes"
            return
        }

        def attachmentDirectoryAccessor = ComponentAccessor.getComponent(FileSystemAttachmentDirectoryAccessor)
        def temporaryAttachmentDirectory = attachmentDirectoryAccessor.getTemporaryAttachmentDirectory()

        LOG.debug "Attempting to get attachments from modified fields"
        def attachmentTempFileNames = LQCIssue.getModifiedFields().get(IssueFieldConstants.ATTACHMENT)?.newValue
        LOG.debug "Number of attachments found = <${attachmentTempFileNames.size()}>"

        String sXMLTempFileName
        Boolean foundXML = false

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
                    foundXML = true
                }
            }
        }

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
        def Samples = Chipset.Chips.Chip.find { it.'@key' == "0" }.Files.File.find { it.'@key' == "0" }.Samples

        // check we can fetch all the samples
        LOG.debug "Number of Samples = ${Samples.Sample.size()}"
        if (Samples.Sample.size() == 12) {
            LOG.debug "Samples size is 12 as expected"

            // extract values from XML for each tube into a map
            Map<String, Map<String, String>> resultsMap = [:]

            (1..iNumberOfTubes).each{ int iTubeNumber ->

                String sTubeNumber = Integer.toString(iTubeNumber)
                String sSampleNumber = Integer.toString(iTubeNumber - 1)
                GPathResult Sample = Samples.Sample.find { it.'@key' == sSampleNumber }

                LOG.debug "Fetching name for sample number <${sSampleNumber}>"
                String sSampleName = Sample.Name.text()
                LOG.debug "Sample Name = ${sSampleName}"

                LOG.debug "Fetching barcode for tube number <${sTubeNumber}>"
                String sTubeBarcode = LPLBarcodesMap[sTubeNumber]
                LOG.debug "Barcode = ${sTubeBarcode}"
                if(sSampleName == sTubeBarcode) {

                    // extract values of conc and fragment size from XML
                    GPathResult Channel = Sample.DAResultStructures.DARSmearAnalysis.Channel.find { it.'@key' == '1' }
                    if(!Channel.isEmpty()) {
                        GPathResult Region = Channel.RegionsMolecularResults.Region.find { it.RegionName.text() == 'Library' }
                        if (!Region.isEmpty()) {
                            LOG.debug "Found XML Region with name <${Region.RegionName.text()}>"

                            // concentration
                            String sConcPM = Region.RegionMolarity.text() // picoMolar, we want nM to 2 decimal places
                            LOG.debug "Concentration = <${sConcPM}> pM"
                            double dConcNM = sConcPM.toDouble() / 1000
                            LOG.debug "Concentration = <${Double.toString(dConcNM)}> nM"
                            double dConcNMRounded = WorkflowUtils.round(dConcNM, 2, BigDecimal.ROUND_HALF_UP)
                            String sConc = Double.toString(dConcNMRounded)
                            LOG.debug "Concentration rounded = <${sConc}> nM"

                            // fragment size
                            String sFragSizeRaw = Region.AverageSize.text()
                            LOG.debug "Fragment size = <${sFragSizeRaw}> bp"
                            double dFragSizeRaw = sFragSizeRaw.toDouble()
                            double dFragSizeRounded = WorkflowUtils.round(dFragSizeRaw, 2, BigDecimal.ROUND_HALF_UP)
                            String sFragSize = Double.toString(dFragSizeRounded)
                            LOG.debug "Fragment size rounded = <${sFragSize}> nM"

                            // percentage total DNA
                            String sPercentTotalDNARaw = Region.PercentTotal.text()
                            LOG.debug "Percent total DNA = <${sPercentTotalDNARaw}>"
                            double dPercentTotalDNARaw = sPercentTotalDNARaw.toDouble()
                            double dPercentTotalDNARounded = WorkflowUtils.round(dPercentTotalDNARaw, 2, BigDecimal.ROUND_HALF_UP)
                            String sPercentTotalDNA = Double.toString(dPercentTotalDNARounded)
                            LOG.debug "Percent total DNA rounded = <${sPercentTotalDNA}> nM"

                            // fetch tube issue using barcode
                            Issue curTubeIssue = WorkflowUtils.getIssueForBarcode(sTubeBarcode)

                            if (curTubeIssue != null) {

                                // set CF values for conc and fragment size in the tube issue
                                JiraAPIWrapper.setCFValueByName(curTubeIssue, ConfigReader.getCFName("LIBRARY_QC_CONCENTRATION"), sConc)
                                JiraAPIWrapper.setCFValueByName(curTubeIssue, ConfigReader.getCFName("LIBRARY_QC_AVERAGE_FRAGMENT_SIZE"), sFragSize)
                                JiraAPIWrapper.setCFValueByName(curTubeIssue, ConfigReader.getCFName("LIBRARY_QC_PERCENT_TOTAL_DNA"), sPercentTotalDNA)

                                // get CF values for 'IQC Outcome', 'IQC Feedback' (may be null), 'Average Sample Concentration' and "Plate Format" and add to map
                                String sIQCOutcome = JiraAPIWrapper.getCFValueByName(curTubeIssue, ConfigReader.getCFName("IQC_OUTCOME"))
                                String sIQCFeedback = JiraAPIWrapper.getCFValueByName(curTubeIssue, ConfigReader.getCFName("IQC_FEEDBACK"))
                                if(sIQCFeedback == null || sIQCFeedback.isEmpty()) {
                                    sIQCFeedback = 'n/a'
                                }
                                String sQNTAvgConc = JiraAPIWrapper.getCFValueByName(curTubeIssue, ConfigReader.getCFName("AVG_SAMPLE_CONCENTRATION"))
                                String sSBMPltFormat = JiraAPIWrapper.getCFValueByName(curTubeIssue, ConfigReader.getCFName("PLATE_FORMAT"))

                                // build results map for use in wiki table building
                                resultsMap[sTubeNumber] = [
                                        "barcode"         : sTubeBarcode,
                                        "concentration"   : sConc,
                                        "fragmentsize"    : sFragSize,
                                        "percent_total"   : sPercentTotalDNA,
                                        "iqc_outcome"     : sIQCOutcome,
                                        "iqc_feedback"    : sIQCFeedback,
                                        "qnt_avg_conc"    : sQNTAvgConc,
                                        "sbm_plate_format": sSBMPltFormat
                                ]
                            } else {
                                LOG.error "Failed to fetch tube issue for barcode <${sTubeBarcode}> when parsing XML."
                            }
                        } else {
                            LOG.error "Failed to find Library region in XML for barcode <${sTubeBarcode}> when parsing XML."
                        }
                    } else {
                        LOG.error "Failed to find Channel region in XML for barcode <${sTubeBarcode}> when parsing XML."
                    }
                } else {
                    LOG.error "For position <${sTubeNumber}> the Sample name <${sSampleName}> did not match to the JIRA barcode <${sTubeBarcode}>"
                }
            }

            if(resultsMap.size() > 0) {
                if(resultsMap.size() != iNumberOfTubes) {
                    LOG.error "XML results map size <${Integer.toString(resultsMap.size())}> does not match to number of tubes <${Integer.toString(iNumberOfTubes)}>"
                }

                // generate wiki markup table
                StringBuffer sbMarkup = createBAQCResultsWikiMarkup(resultsMap)

                // write markup to text field 'Library QC Results'
                JiraAPIWrapper.setCFValueByName(LQCIssue, ConfigReader.getCFName("LIBRARY_QC_RESULTS"), sbMarkup.toString())
            } else {
                LOG.error "No results extracted from XML file, cannot draw results table"
            }

        } else {
            LOG.error "XML Samples size was not as expected, cannot extract information from XML"
        }
    }

    /**
     * Create the wiki markup string buffer for the BioAnalyzer QC results
     *
     * @param resultsMap
     * @return
     */
    private static StringBuffer createBAQCResultsWikiMarkup(Map<String, Map<String, String>> resultsMap) {

        LOG.debug "Creating wiki markup for BA QC results map:"
        LOG.debug resultsMap.toMapString()

        // line 1 = table headers for 8 columns of 1 to 11 tube results
        def wikiSB = '{html}<div style="text-align: center;">{html}'<<''
        wikiSB <<= System.getProperty("line.separator")
        wikiSB <<= '|| '
        wikiSB <<= '|| Barcode '
        wikiSB <<= '|| Conc ' + '\\\\ ' + '(nM)'
        wikiSB <<= '|| Avg ' + '\\\\ ' + 'Fragment ' + '\\\\ ' + 'Size (bp)'
        wikiSB <<= '|| % Total ' + '\\\\ ' + 'DNA'
        wikiSB <<= '|| IQC ' + '\\\\ ' + 'Outcome'
        wikiSB <<= '|| IQC ' + '\\\\ ' + 'Feedback'
        wikiSB <<= '|| QNT Avg' + '\\\\' + 'Sample ' + '\\\\ ' + 'Conc (ng/&mu;l)'
        wikiSB <<= '|| SBM Plt ' + '\\\\ ' + 'Format'
        wikiSB <<= '||'
        wikiSB <<= System.getProperty("line.separator")

        // lines 2-12 tube rows
        (1..11).each { int iRow ->
            String sRow = Integer.toString(iRow)
            if(resultsMap.containsKey(sRow)) {
                wikiSB <<= '|| ' + sRow
                String sTubeBC = resultsMap[sRow]['barcode']
                String[] sSplitTubeBC = sTubeBC.split('\\.')
                LOG.debug "sSplitTubeBC = ${sSplitTubeBC.toString()}"
                if(3 == sSplitTubeBC.size()) {
                    wikiSB <<= '| ' + sSplitTubeBC[0] + '.' + sSplitTubeBC[1] + '. \\\\ *' + sSplitTubeBC[02] + '*'
                } else {
                    LOG.warn "splitTubeBC unexpected size <${sSplitTubeBC.size()}>"
                    wikiSB <<= '| ' + sTubeBC
                }
                wikiSB <<= '| ' + resultsMap[sRow]['concentration']
                wikiSB <<= '| ' + resultsMap[sRow]['fragmentsize']
                wikiSB <<= '| ' + resultsMap[sRow]['percent_total']
                wikiSB <<= '| ' + resultsMap[sRow]['iqc_outcome']
                wikiSB <<= '| ' + resultsMap[sRow]['iqc_feedback']
                wikiSB <<= '| ' + resultsMap[sRow]['qnt_avg_conc']
                wikiSB <<= '| ' + resultsMap[sRow]['sbm_plate_format']
            } else {
                wikiSB <<= '|| ' + sRow
                wikiSB <<= '| | | | | | | | '
            }
            wikiSB <<= '|'
            wikiSB <<= System.getProperty("line.separator")
        }
        wikiSB <<= '{html}</div>{html}'
        wikiSB

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

    /**
     * Delete all attachments from the issue.
     *
     * @param LQCIssue
     */
    public static void deleteAttachments(Issue LQCIssue) {

        LOG.debug "Deleting all attachments from the issue with Id <${LQCIssue.getId()}>"
        AttachmentManager attachMgr = ComponentAccessor.getAttachmentManager();
        List<Attachment> attachments = attachMgr.getAttachments(LQCIssue);
        if (!attachments.isEmpty()) {
            LOG.debug "Found ${Integer.toString(attachments.size())} attachments to delete"
            attachments.each{ Attachment attachment ->
                attachMgr.deleteAttachment(attachment)
            }
        }
    }

    /**
     * Process the tubes that have passed in BioAnalyzer QC.
     *
     * @param LQCIssue
     * @param LPLTubeIds
     * @return
     */
    public static tubesPassedInLibraryQC(Issue LQCIssue, ArrayList<String> LPLTubeIds) {

        LOG.debug "In method to process LPL tubes that passed BA QC"

        if (LQCIssue == null) {
            LOG.error "LQCIssue null, cannot continue"
            return
        }

        if (LPLTubeIds == null || 0 == LPLTubeIds.size()) {
            LOG.error "LPLTubeIds null or empty, cannot continue"
            return
        }

        // transition action id to transition tube to 'TubLPL Rdy for Pre-normalisation' via 'Ready for pre-normalisation
        int transitionLPLPassId = ConfigReader.getTransitionActionId(
                WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_RDY_FOR_PRE_NORMALISATION.toString()
        )
        if (transitionLPLPassId <= 0) {
            LOG.error "ERROR: Transition id for LPL tube not found, cannot continue"
            return
        }

        LPLTubeIds.each { String sLPLTubeID ->

            MutableIssue curLPLTubeMutIssue

            try {
                LOG.debug "Parsing ID ${sLPLTubeID} to Long"
                Long sourceTubeIdLong = Long.parseLong(sLPLTubeID)

                LOG.debug "Fetching source issue for Id ${sLPLTubeID}"

                // fetch the mutable issue
                curLPLTubeMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourceTubeIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sLPLTubeID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curLPLTubeMutIssue != null) {

                // Set CF 'Library QC Outcome' = 'Pass'
                JiraAPIWrapper.setCFSelectValueByName(curLPLTubeMutIssue, ConfigReader.getCFName("LIBRARY_QC_OUTCOME"), 'Pass')

                //transition source LPL tube via 'Ready for pre-normalisation' to 'TubLPL Rdy for Pre-normalisation'
                LOG.debug "Attempting to transition LPL tube to 'TubLPL Rdy for Pre-normalisation'"
                WorkflowUtils.transitionIssue(curLPLTubeMutIssue.getId(), transitionLPLPassId, "Automatically transitioned during Library Pool BA QC Pass process")

            }
        }
    }

    /**
     * Process the tubes that have failed in BioAnalyzer QC.
     *
     * @param LQCIssue
     * @param LPLTubeIds
     * @return
     */
    public static tubesFailedInLibraryQC(Issue LQCIssue, ArrayList<String> LPLTubeIds) {

        LOG.debug "In method to process LPL tubes that failed BA QC"

        if (LQCIssue == null) {
            LOG.error "LQCIssue null, cannot continue"
            return
        }

        if (LPLTubeIds == null || 0 == LPLTubeIds.size()) {
            LOG.error "LPLTubeIds null or empty, cannot continue"
            return
        }

        // transition action id to transition tube to 'TubLPL Failed' via 'Fail in BA QC'
        int transitionLPLFailedId = ConfigReader.getTransitionActionId(
                WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_FAIL_IN_BA_QC.toString()
        )
        if (transitionLPLFailedId <= 0) {
            LOG.error "ERROR: Transition id for LPL tube failed in BA QC not found, cannot continue"
            return
        }

        // transition action id to transition tube to 'TubLPL Rdy for Pre-normalisation' via 'Ready for pre-normalisation
        int transitionLPLPassId = ConfigReader.getTransitionActionId(
                WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_RDY_FOR_PRE_NORMALISATION.toString()
        )
        if (transitionLPLPassId <= 0) {
            LOG.error "ERROR: Transition id for LPL tube not found, cannot continue"
            return
        }

        LPLTubeIds.each { String sLPLTubeID ->

            MutableIssue curLPLTubeMutIssue

            try {
                LOG.debug "Parsing ID ${sLPLTubeID} to Long"
                Long sourceTubeIdLong = Long.parseLong(sLPLTubeID)

                LOG.debug "Fetching source issue for Id ${sLPLTubeID}"

                // fetch the mutable issue
                curLPLTubeMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourceTubeIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sLPLTubeID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curLPLTubeMutIssue != null) {

                // Set CF 'Library QC Outcome' = 'Fail'
                JiraAPIWrapper.setCFSelectValueByName(curLPLTubeMutIssue, ConfigReader.getCFName("LIBRARY_QC_OUTCOME"), 'Fail')

                // get CF 'IQC Outcome'
                String sIQCOutcome = JiraAPIWrapper.getCFValueByName(curLPLTubeMutIssue, ConfigReader.getCFName("IQC_OUTCOME"))

                if(sIQCOutcome == 'Pass') {

                    //transition source LPL tube via 'Fail in BA QC' to 'TubLPL Failed'
                    LOG.debug "Attempting to transition LPL tube to 'TubLPL Failed'"
                    WorkflowUtils.transitionIssue(curLPLTubeMutIssue.getId(), transitionLPLFailedId, "Automatically transitioned during Library Pool BA QC Fail process.")

                } else {

                    // get CF 'IQC Feedback'
                    String sIQCFeedback = JiraAPIWrapper.getCFValueByName(curLPLTubeMutIssue, ConfigReader.getCFName("IQC_FEEDBACK"))

                    if (sIQCFeedback == 'Pass') {

                        // transition source LPL tube via 'Ready for pre-normalisation' to 'TubLPL Rdy for Pre-normalisation'
                        LOG.debug "Attempting to transition LPL tube to 'TubLPL Rdy for Pre-normalisation'"
                        WorkflowUtils.transitionIssue(curLPLTubeMutIssue.getId(), transitionLPLPassId, "Automatically transitioned during Library Pool BA QC Fail process, customer pass at IQC so tube continues despite BA QC .")

                    } else {

                        LOG.error "Tube with Id <${curLPLTubeMutIssue.getId()}> should not have IQC Outcome of 'Fail' and IQC Feedback of 'Fail'"

                    }
                }

            }
        }
    }
}
