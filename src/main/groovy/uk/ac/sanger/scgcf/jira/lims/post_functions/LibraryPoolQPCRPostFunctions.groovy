package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.AttachmentManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueFieldConstants
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.attachment.Attachment
import com.atlassian.jira.issue.attachment.FileSystemAttachmentDirectoryAccessor
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.ExcelParser
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code LibraryPoolQPCRPostFunctions} class holds post functions for the
 * Library Pool QPCR project
 *
 * Created by as28 on 13/09/2017.
 */

@Slf4j(value = "LOG")
class LibraryPoolQPCRPostFunctions {

    /**
     * Create the source tubes block map, link the tubes and transition them to in QPCR
     *
     * @param QPCRIssue
     */
    public static void createListSourceBarcodes(Issue QPCRIssue) {

        LOG.debug "In method to link tubes for source barcodes for QPCR"

        if (QPCRIssue == null) {
            LOG.error "QPCRIssue null, cannot continue"
            return
        }

        LOG.debug "In post function to create PPL tubes list for library pool QPCR"

        // Get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of PPL tubes = ${Integer.toString(iNumTubes)}"

        // get map of source barcodes
        Map<String, Map<String, String>> tubesMap = getSourceTubesMap(QPCRIssue, iNumTubes)

        // map of source number vs block position
        Map<String,String> sourceToBlockMap = ['1':'A1','2':'B1','3':'C1','4':'D1',
                                             '5':'A2','6':'B2','7':'C2','8':'D2',
                                             '9':'A3','10':'B3','11':'C3','12':'D3',
                                             '13':'A4','14':'B4','15':'C4','16':'D4',
                                             '17':'A5','18':'B5','19':'C5','20':'D5',
                                             '21':'A6','22':'B6','23':'C6','24':'D6']

        Map<String,String> blockMap = [:]

        (1..iNumTubes).each { int iIndx ->
            String sTubeIndx = Integer.toString(iIndx)

            String sPPLTubeBarcode = tubesMap[sTubeIndx]['source_bc']
            Issue PPLIssue = WorkflowUtils.getIssueForBarcode(sPPLTubeBarcode)

            // determine position in block on Hamilton
            String sBlockPosn = sourceToBlockMap[sTubeIndx]

            // set 'Library QPCR Block Position' on tube
            JiraAPIWrapper.setCFValueByName(QPCRIssue, ConfigReader.getCFName("LIBRARY_QPCR_BLOCK_POSITION"), sBlockPosn)

            // set block map position to barcode
            blockMap[sBlockPosn] = sPPLTubeBarcode

            // link QPCR issue to the PPL tube issue with a 'Group includes' 'includes containers' link
            LOG.debug "Attempting to create an issue link between the QPCR group issue and source PPL tube <${sTubeIndx}>"
            WorkflowUtils.createIssueLink(QPCRIssue.getId(), PPLIssue.getId(), IssueLinkTypeName.GROUP_INCLUDES.getLinkTypeName())

            // transition the tube to 'TubPPL In QPCR' via 'Start QPCR'
            int transitionId = ConfigReader.getTransitionActionId(
                    WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_START_QPCR.toString()
            )

            if(transitionId > 0) {
                LOG.debug "Attempting to transition PPL to 'In QPCR'"
                WorkflowUtils.transitionIssue(PPLIssue.getId(), transitionId, "Automatically transitioned during Library Pool QPCR scan tubes")
            } else {
                LOG.error "ERROR: Transition id not found, cannot transition PPL tube with Key <${PPLIssue.getKey()}>"
            }
        }

        // should now have a block map including all the information needed to build the wiki markup table
        LOG.debug "Block map after linking PPL tubes:"
        LOG.debug blockMap.toMapString()

        // Create wiki markup and generate tube block layout table
        def sbMarkup = createBlockWikiMarkup(blockMap)

        //   Set CF 'Tube Block Positions' to sWikiMarkup
        JiraAPIWrapper.setCFValueByName(QPCRIssue, ConfigReader.getCFName("TUBE_BLOCK_POSITIONS"), sbMarkup.toString())

    }

    /**
     * Parse the concentration values from the results excel file, write them into the tube issues and
     * determine which have automatically passed QC. Then build wiki markup tables to display the results.
     *
     * @param QPCRIssue
     */
    public static void parseResultsFile(MutableIssue QPCRIssue) {

        LOG.debug "In method to parse concentration results for QPCR"

        if (QPCRIssue == null) {
            LOG.error "QPCRIssue null, cannot continue"
            return
        }

        Map<String, String> concsMap = getConcentrationsFromFile(QPCRIssue)
        if(concsMap == null || concsMap.size() <= 0) {
            LOG.error "ERROR: failed to retrieve concentrations from the results file, cannot continue"
            return
        }

        // get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of PPL tubes = <${Integer.toString(iNumTubes)}>"

//        Map<String, Map<String, String>> resultsMapPassed = [:]
//        Map<String, Map<String, String>> resultsMapUndecided = [:]

        (1..iNumTubes).each { int iTubeIndx ->
            String sTubeIndx = Integer.toString(iTubeIndx)

            // get the Source n Barcode
            String sCFAlias = "SOURCE_" + sTubeIndx + "_BARCODE"
            String sPPLTubeBarcode = JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName(sCFAlias))

            // get the PPL issue for the barcode
            if(sPPLTubeBarcode?.trim()) {
                Issue PPLIssue = WorkflowUtils.getIssueForBarcode(sPPLTubeBarcode)

                if(PPLIssue != null) {
                    // get concentration from map
                    String sConc = concsMap[sTubeIndx]
                    BigDecimal bdConc = new BigDecimal(sConc)

                    // set tube custom field 'Library QPCR Concentration'
                    JiraAPIWrapper.setCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_QPCR_CONCENTRATION"), sConc)

//                    // get values from PPL tube
//                    String sPlateFormat = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("PLATE_FORMAT"))
//                    String sIQCOutcome  = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("IQC_OUTCOME"))
                    String sIQCFeedback = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("IQC_FEEDBACK"))
//                    String sBAQCOutcome = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_QC_OUTCOME"))
//                    String sPreNormConc = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_PNM_CONCENTRATION"))

                    // determine if automatically passes (if >= 1nM or if customer override pass at IQC)
                    if(bdConc > 1.0 || (sIQCFeedback != null && sIQCFeedback == 'Pass')) {

                        // set custom field 'Library QPCR Outcome' to 'Pass'
                        JiraAPIWrapper.setCFSelectValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_QPCR_OUTCOME"), 'Pass')

//                        // passed - set fields in results map for passed tubes
//                        resultsMapPassed[sTubeIndx] = [
//                                "barcode"            : sPPLTubeBarcode,
//                                "sbm_plate_format"   : sPlateFormat,
//                                "qpcr_conc"          : sConc,
//                                "iqc_outcome"        : sIQCOutcome,
//                                "iqc_feedback"       : sIQCFeedback,
//                                "library_qc_outcome" : sBAQCOutcome,
//                                "prenorm_conc"       : sPreNormConc,
//                                "qpcr_outcome"       : 'Pass'
//                        ]

                    }
//                    else {
//                        // undecided - set fields in results map for undecided tubes
//                        resultsMapUndecided[sTubeIndx] = [
//                                "barcode"            : sPPLTubeBarcode,
//                                "sbm_plate_format"   : sPlateFormat,
//                                "qpcr_conc"          : sConc,
//                                "iqc_outcome"        : sIQCOutcome,
//                                "iqc_feedback"       : sIQCFeedback,
//                                "library_qc_outcome" : sBAQCOutcome,
//                                "prenorm_conc"       : sPreNormConc
//                        ]
//                    }
                }
            }
        }

//        // build the wiki markup for the results
//        if(resultsMapUndecided.size() + resultsMapPassed.size() > 0) {
//            if(resultsMapUndecided.size() + resultsMapPassed.size() != iNumTubes) {
//                LOG.error "Results map sizes do not add up to to number of tubes <${Integer.toString(iNumTubes)}>"
//            }
//
//            // generate wiki markup table
//            StringBuffer sbMarkup = createQPCRResultsWikiMarkup(iNumTubes, resultsMapUndecided, resultsMapPassed)
//
//            // write markup to text field 'Library QC Results'
//            if(sbMarkup != null) {
//                JiraAPIWrapper.setCFValueByName(QPCRIssue, ConfigReader.getCFName("LIBRARY_QPCR_RESULTS"), sbMarkup.toString())
//            }
//        } else {
//            LOG.error "No results extracted, cannot draw results table"
//        }
    }

    /**
     * Create the wiki markup string buffer for the QPCR results
     *
     * @param resultsMap
     * @return
     */
    private static StringBuffer createQPCRResultsWikiMarkup(int iNumTubes, Map<String, Map<String, String>> resultsMapUndecided, Map<String, Map<String, String>> resultsMapDecided) {

        LOG.debug "In method to create QPCR results wiki markup"

        if (iNumTubes <= 0) {
            LOG.error "No tubes, cannot continue"
            return null
        }

        if ((resultsMapUndecided == null || resultsMapUndecided.size() <= 0) && (resultsMapDecided == null  || resultsMapDecided.size() <= 0)) {
            LOG.error "No results, cannot continue"
            return null
        }

        LOG.debug "Creating wiki markup for QPCR results:"
        StringBuffer wikiSB = '{html}<div style="text-align: center;">{html}'<<''

        // display any undecided results
        if(resultsMapUndecided != null && resultsMapUndecided.size() > 0) {

            LOG.debug "Undecided results:"
            LOG.debug resultsMapUndecided.toMapString()

            wikiSB <<= System.getProperty("line.separator")
            wikiSB <<= "h2. Undecided Results"
            createQPCRResultsWikiHeaderRow(wikiSB)
            createQPCRResultsWikiTableBody(iNumTubes, wikiSB, resultsMapUndecided)

        }

        // display results where already decided
        if(resultsMapDecided != null && resultsMapDecided.size() > 0) {

            LOG.debug "Decided results:"
            LOG.debug resultsMapDecided.toMapString()

            wikiSB <<= System.getProperty("line.separator")
            wikiSB <<= "h2. Decided Results"
            createQPCRResultsWikiHeaderRow(wikiSB)
            createQPCRResultsWikiTableBody(iNumTubes, wikiSB, resultsMapDecided)

        }

        wikiSB <<= '{html}</div>{html}'
        wikiSB

    }

    /**
     * Build the wiki markup results table.
     *
     * @param QPCRIssue
     */
    public static void buildWikiResultsTable(Issue QPCRIssue) {

        // get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of PPL tubes = <${Integer.toString(iNumTubes)}>"

        Map<String, Map<String, String>> resultsDecided = [:]
        Map<String, Map<String, String>> resultsMapUndecided = [:]

        (1..iNumTubes).each { int iTubeIndx ->
            String sTubeIndx = Integer.toString(iTubeIndx)

            // get the Source n Barcode
            String sCFAlias = "SOURCE_" + sTubeIndx + "_BARCODE"
            String sPPLTubeBarcode = JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName(sCFAlias))

            // get the PPL issue for the barcode
            if(sPPLTubeBarcode?.trim()) {
                Issue PPLIssue = WorkflowUtils.getIssueForBarcode(sPPLTubeBarcode)

                if(PPLIssue != null) {

                    // get values from PPL tube
                    String sPlateFormat = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("PLATE_FORMAT"))
                    String sIQCOutcome  = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("IQC_OUTCOME"))
                    String sIQCFeedback = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("IQC_FEEDBACK"))
                    String sBAQCOutcome = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_QC_OUTCOME"))
                    String sPreNormConc = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_PNM_CONCENTRATION"))
                    String sQPCRConc    = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_QPCR_CONCENTRATION"))
                    String sQPCROutcome = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_QPCR_OUTCOME"))

                    // check if result decided or not
                    if(sQPCROutcome?.trim()) {

                        // decided - set fields in results map for decided tubes
                        resultsDecided[sTubeIndx] = [
                                "barcode"            : sPPLTubeBarcode,
                                "sbm_plate_format"   : sPlateFormat,
                                "iqc_outcome"        : sIQCOutcome,
                                "iqc_feedback"       : sIQCFeedback,
                                "library_qc_outcome" : sBAQCOutcome,
                                "prenorm_conc"       : sPreNormConc,
                                "qpcr_conc"          : sQPCRConc,
                                "qpcr_outcome"       : sQPCROutcome
                        ]

                    } else {
                        // undecided - set fields in results map for undecided tubes
                        resultsMapUndecided[sTubeIndx] = [
                                "barcode"            : sPPLTubeBarcode,
                                "sbm_plate_format"   : sPlateFormat,
                                "iqc_outcome"        : sIQCOutcome,
                                "iqc_feedback"       : sIQCFeedback,
                                "library_qc_outcome" : sBAQCOutcome,
                                "prenorm_conc"       : sPreNormConc,
                                "qpcr_conc"          : sQPCRConc
                        ]
                    }
                }
            }
        }

        // build the wiki markup for the results
        if(resultsMapUndecided.size() + resultsDecided.size() > 0) {
            if(resultsMapUndecided.size() + resultsDecided.size() != iNumTubes) {
                LOG.error "Results map sizes do not add up to to number of tubes <${Integer.toString(iNumTubes)}>"
            }

            // generate wiki markup table
            StringBuffer sbMarkup = createQPCRResultsWikiMarkup(iNumTubes, resultsMapUndecided, resultsDecided)

            // write markup to text field 'Library QC Results'
            if(sbMarkup != null) {
                JiraAPIWrapper.setCFValueByName(QPCRIssue, ConfigReader.getCFName("LIBRARY_QPCR_RESULTS"), sbMarkup.toString())
            }
        } else {
            LOG.error "No results extracted, cannot draw results table"
        }

    }

    /**
     * Create headers for wiki results table
     *
     * @param wikiSB
     */
    private static void createQPCRResultsWikiHeaderRow(StringBuffer wikiSB) {

        LOG.debug "In method to create wiki results header"

        if (wikiSB == null) {
            LOG.error "No wiki string buffer, cannot continue"
            return
        }

        // line 1 = table headers
        wikiSB <<= System.getProperty("line.separator")
        wikiSB <<= '|| '
        wikiSB <<= '|| Barcode '
        wikiSB <<= '|| SBM Plate ' + '\\\\ ' + 'Format'
        wikiSB <<= '|| IQC ' + '\\\\ ' + 'Outcome'
        wikiSB <<= '|| IQC ' + '\\\\ ' + 'Feedback'
        wikiSB <<= '|| Library QC ' + '\\\\ ' + 'Outcome'
        wikiSB <<= '|| Pre-Norm ' + '\\\\ ' + 'Conc (nM)'
        wikiSB <<= '|| QPCR ' + '\\\\ ' + 'Conc (nM)'
        wikiSB <<= '|| QPCR ' + '\\\\ ' + 'Outcome'
        wikiSB <<= '||'
        wikiSB <<= System.getProperty("line.separator")
    }

    /**
     * Create table body for results
     *
     * @param iNumTubes
     * @param wikiSB
     * @param resultsMap
     */
    private static void createQPCRResultsWikiTableBody(int iNumTubes, StringBuffer wikiSB, Map<String, Map<String, String>> resultsMap) {

        LOG.debug "In method to create wiki results body"

        if (iNumTubes <= 0) {
            LOG.error "No tubes, cannot continue"
            return
        }

        if (wikiSB == null) {
            LOG.error "No wiki string buffer, cannot continue"
            return
        }

        if (resultsMap == null || resultsMap.size() <= 0) {
            LOG.error "No results, cannot continue"
            return
        }

        // lines 2-n tube rows
        (1..iNumTubes).each { int iRow ->
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
                wikiSB <<= '| ' + resultsMap[sRow]['sbm_plate_format']

                if(resultsMap[sRow].containsKey('iqc_outcome')) {
                    String sIQCOutcome = resultsMap[sRow]['iqc_outcome']
                    LOG.debug "sIQCOutcome = ${sIQCOutcome}"
                    if(sIQCOutcome == 'Pass') {
                        wikiSB <<= '| {html}<span style="color:green;font-weight:bold;font-size:15px;">Pass</span>{html}'
                    } else if(sIQCOutcome == 'Fail') {
                        wikiSB <<= '| {html}<span style="color:red;font-weight:bold;font-size:15px;">Fail</span>{html}'
                    } else if(sIQCOutcome == null) {
                        wikiSB <<= '| '
                    } else {
                        wikiSB <<= '| ' + sIQCOutcome
                    }
                } else {
                    wikiSB <<= '| '
                }

                if(resultsMap[sRow].containsKey('iqc_feedback')) {
                    String sIQCFeedback = resultsMap[sRow]['iqc_feedback']
                    LOG.debug "sIQCFeedback = ${sIQCFeedback}"
                    if(sIQCFeedback == 'Pass') {
                        wikiSB <<= '| {html}<span style="color:green;font-weight:bold;font-size:15px;">Pass</span>{html}'
                    } else if(sIQCFeedback == 'Fail') {
                        wikiSB <<= '| {html}<span style="color:red;font-weight:bold;font-size:15px;">Fail</span>{html}'
                    } else if(sIQCFeedback == null) {
                        wikiSB <<= '| '
                    } else {
                        wikiSB <<= '| ' + sIQCFeedback
                    }
                } else {
                    wikiSB <<= '| '
                }

                if(resultsMap[sRow].containsKey('library_qc_outcome')) {
                    String sLibQCOutcome = resultsMap[sRow]['library_qc_outcome']
                    LOG.debug "sLibQCOutcome = ${sLibQCOutcome}"
                    if(sLibQCOutcome == 'Pass') {
                        wikiSB <<= '| {html}<span style="color:green;font-weight:bold;font-size:15px;">Pass</span>{html}'
                    } else if(sLibQCOutcome == 'Fail') {
                        wikiSB <<= '| {html}<span style="color:red;font-weight:bold;font-size:15px;">Fail</span>{html}'
                    } else if(sLibQCOutcome == null) {
                        wikiSB <<= '| '
                    } else {
                        wikiSB <<= '| ' + sLibQCOutcome
                    }
                } else {
                    wikiSB <<= '| '
                }

                wikiSB <<= '| ' + resultsMap[sRow]['prenorm_conc']
                wikiSB <<= '| ' + resultsMap[sRow]['qpcr_conc']

                if(resultsMap[sRow].containsKey('qpcr_outcome')) {
                    String sQPCROutcome = resultsMap[sRow]['qpcr_outcome']
                    LOG.debug "sQPCROutcome = ${sQPCROutcome}"
                    if(sQPCROutcome == 'Pass') {
                        wikiSB <<= '| {html}<span style="color:green;font-weight:bold;font-size:15px;">Pass</span>{html}'
                    } else if(sQPCROutcome == 'Fail') {
                        wikiSB <<= '| {html}<span style="color:red;font-weight:bold;font-size:15px;">Fail</span>{html}'
                    } else if(sQPCROutcome == null) {
                        wikiSB <<= '| '
                    } else {
                        wikiSB <<= '| ' + sQPCROutcome
                    }
                } else {
                    wikiSB <<= '| '
                }
            }
            wikiSB <<= '|'
            wikiSB <<= System.getProperty("line.separator")
        }

    }

    /**
     * Parse the concentrations from the results file
     *
     * @param QPCRIssue
     * @return
     */
    private static Map<String, String> getConcentrationsFromFile(MutableIssue QPCRIssue) {

        LOG.debug "In method to get concentration values from results file"

        if (QPCRIssue == null) {
            LOG.error "No QPCR issue, cannot continue"
            return null
        }

        def attachmentDirectoryAccessor = ComponentAccessor.getComponent(FileSystemAttachmentDirectoryAccessor)
        def temporaryAttachmentDirectory = attachmentDirectoryAccessor.getTemporaryAttachmentDirectory()

        LOG.debug "Attempting to get attachments from modified fields"
        def attachmentTempFileNames = QPCRIssue.getModifiedFields().get(IssueFieldConstants.ATTACHMENT)?.newValue

        // check for attachments
        if (attachmentTempFileNames == null || attachmentTempFileNames.size() != 1) {
            LOG.error "Expecting one (and only one) results xlsx file from the Light Cycler to be attached, cannot parse"
            return null
        }

        LOG.debug "Number of attachments found = <${attachmentTempFileNames.size()}>"

        // get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of PPL tubes = <${Integer.toString(iNumTubes)}>"

        String sLightCyclerBC = ""
        Map<String, String> concsMap = [:]

        // check each file attached in this transition (does not check other existing attachments)
        for (String sResultsTempFileName in attachmentTempFileNames) {
            LOG.debug "Found an attachment"
            LOG.debug "Temporary filename = ${sResultsTempFileName}"

            // TODO: how determine this is the excel sheet we want? i.e. how tell file is xlsx?

            if (sResultsTempFileName?.trim()) {
                String sFilepath = new File(temporaryAttachmentDirectory, sResultsTempFileName).getPath()
                try {
                    def sheetMaps = ExcelParser.parse(sFilepath)

                    // check for the presence of the results tab
                    for (sheetMap in sheetMaps) {
                        String sSheetName = sheetMap.value["sheet_name"]
                        if (sSheetName == "QPCR Results") {
                            def rows = sheetMap.value["rows"]

                            int iRowIndex = 0
                            rows.each { List<String> row ->
                                if (Integer.valueOf(iRowIndex) == 0) {
                                    if (row.get(0).toUpperCase() == "LIGHT CYCLER PLATE BARCODE") {
                                        sLightCyclerBC = new BigDecimal(row.get(1)).toPlainString() // to get rid of the scientific notation
                                        if (sLightCyclerBC == null) {
                                            LOG.error "ERROR: No Light Cycler barcode found in cell A2 on QPCR Results tab of spreadsheet, cannot parse"
                                            return null
                                        }
                                        LOG.debug "Light Cycler Barcode from file = <${sLightCyclerBC}>"
                                    } else {
                                        LOG.error "ERROR: Light Cycler Plate Barcode field label not found in cell A1 on QPCR Results tab of spreadsheet, cannot parse"
                                        return null
                                    }
                                } else {
                                    if(iRowIndex <= iNumTubes) {
                                        String sTubePosnRaw = row.get(0) // tube position, double
                                        String sTubePosn = Integer.toString(Double.valueOf(sTubePosnRaw).intValue()) // convert position to integer
                                        String sConcRaw = row.get(1) // concentration, double
                                        String sConc = new BigDecimal(sConcRaw).toPlainString()
                                        if(sTubePosn?.trim() && sTubePosn == Integer.toString(iRowIndex)) {
                                            if(sConc?.trim()) {
                                                concsMap[sTubePosn] = sConc
                                            } else {
                                                LOG.error "ERROR: No concentration found in results file for tube posn ${Integer.toString(iRowIndex)}, cannot parse"
                                                return null
                                            }
                                        } else {
                                            LOG.error "ERROR: No tube position found in results file for tube ${Integer.toString(iRowIndex)}, cannot parse"
                                            return null
                                        }
                                    }
                                }
                                iRowIndex++
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.error "ERROR: Exception attempting to parse Light Cycler results file:"
                    LOG.error e.getMessage()
                    return null
                }
            } else {
                LOG.error "ERROR: Filename empty, cannot parse results file"
                return null
            }
        }

        concsMap
    }

    /**
     * Interpret the QPCR Outcome and transition the tube issue
     *
     * @param QPCRIssue
     */
    public static void interpretResults(Issue QPCRIssue) {

        LOG.debug "In method to interpret concentration results"

        if (QPCRIssue == null) {
            LOG.error "No QPCR issue, cannot continue"
            return
        }

        // get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of PPL tubes = <${Integer.toString(iNumTubes)}>"

        (1..iNumTubes).each { int iTubeIndx ->
            String sTubeIndx = Integer.toString(iTubeIndx)

            // get the Source n Barcode
            String sCFAlias = "SOURCE_" + sTubeIndx + "_BARCODE"
            String sPPLTubeBarcode = JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName(sCFAlias))

            // get the PPL issue for the barcode
            if (sPPLTubeBarcode?.trim()) {
                Issue PPLIssue = WorkflowUtils.getIssueForBarcode(sPPLTubeBarcode)
                if (PPLIssue != null) {
                    LOG.debug "Identified PPL tube issue, getting mutable version"
                    MutableIssue mutPPLIssue = WorkflowUtils.getMutableIssueForIssueId(PPLIssue.getId())

                    if (mutPPLIssue != null) {
                        LOG.debug "Identified mutable PPL tube issue"

                        // get the QPCR Outcome value
                        String sQPCROutcome = JiraAPIWrapper.getCFValueByName(mutPPLIssue, ConfigReader.getCFName("LIBRARY_QPCR_OUTCOME"))
                        LOG.debug "QPCR Outcome for this tube = ${sQPCROutcome}"

                        // if a 'Pass' transition the tube state to 'TubPPL Rdy for Norm' via 'Ready for normalisation'
                        if (sQPCROutcome?.trim() && sQPCROutcome == 'Pass') {
                            LOG.debug "This tube has passed QPCR and can be progressed to ready for normalisation"

                            // transition the tube to 'TubPPL In Pre-Norm' via 'Start pre-normalisation'
                            int transitionId = ConfigReader.getTransitionActionId(
                                    WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_READY_FOR_NORMALISATION.toString()
                            )

                            if (transitionId > 0) {
                                LOG.debug "Attempting to transition PPL to 'Rdy for Norm'"
                                WorkflowUtils.transitionIssue(mutPPLIssue.getId(), transitionId, "Automatically transitioned during Library QPCR interpret results")
                            } else {
                                LOG.error "ERROR: Transition id not found, cannot transition PPL tube with Key <${mutPPLIssue.getKey()}>"
                            }
                        } else {
                            LOG.debug "This tube has NOT passed QPCR and needs a user decision"
                        }
                    } else {
                        LOG.error "Mutable PPLIssue not found for id ${PPLIssue.getId()}"
                    }
                } else {
                    LOG.error "PPLIssue not found for barcode ${sPPLTubeBarcode}"
                }
            }
        }

    }

    /**
     * Process tubes that have passed QPCR
     *
     * @param PPLIssueIds
     */
    public static void processTubesPassed(ArrayList<String> PPLIssueIds) {

        LOG.debug "In method to pass selected PPL tubes"

        if (PPLIssueIds == null || PPLIssueIds.size() <= 0) {
            LOG.error "No PPL issues passed, cannot continue"
            return
        }

        PPLIssueIds.each { String sPPLTubeID ->

            MutableIssue curPPLTubeMutIssue

            try {
                LOG.debug "Parsing ID ${sPPLTubeID} to Long"
                Long sourceTubeIdLong = Long.parseLong(sPPLTubeID)

                LOG.debug "Fetching source issue for Id ${sPPLTubeID}"

                // fetch the mutable issue
                curPPLTubeMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourceTubeIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sPPLTubeID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curPPLTubeMutIssue != null) {

                // set outcome to 'Pass'
                JiraAPIWrapper.setCFValueByName(curPPLTubeMutIssue, ConfigReader.getCFName("LIBRARY_QPCR_OUTCOME"), 'Pass')

                // transition action id to transition tube to 'TubPPL Rdy for Norm' via 'Ready for normalisation'
                int transitionPPLId = ConfigReader.getTransitionActionId(
                        WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_READY_FOR_NORMALISATION.toString()
                )
                if (transitionPPLId > 0) {
                    LOG.debug "Attempting to transition PPL tube to 'TubPPL Rdy for Norm'"
                    WorkflowUtils.transitionIssue(curPPLTubeMutIssue.getId(), transitionPPLId, "Automatically transitioned during Library Pool QPCR pass process")
                } else {
                    LOG.error "ERROR: Transition id for PPL tube not found, cannot continue"
                }
            }
        }
    }

    /**
     * Process tubes that need to re-run QPCR
     *
     * @param PPLIssueIds
     */
    public static void processTubesReRun(ArrayList<String> PPLIssueIds) {

        LOG.debug "In method to re-run selected PPL tubes"

        if (PPLIssueIds == null || PPLIssueIds.size() <= 0) {
            LOG.error "No PPL issues passed, cannot continue"
            return
        }

        PPLIssueIds.each { String sPPLTubeID ->

            MutableIssue curPPLTubeMutIssue

            try {
                LOG.debug "Parsing ID ${sPPLTubeID} to Long"
                Long sourceTubeIdLong = Long.parseLong(sPPLTubeID)

                LOG.debug "Fetching source issue for Id ${sPPLTubeID}"

                // fetch the mutable issue
                curPPLTubeMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourceTubeIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sPPLTubeID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curPPLTubeMutIssue != null) {

                // transition action id to transition tube to 'TubPPL Rdy for QPCR' via 'Revert to ready for QPCR'
                int transitionPPLId = ConfigReader.getTransitionActionId(
                        WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_REVERT_TO_READY_FOR_QPCR_FOR_RERUN.toString()
                )
                if (transitionPPLId > 0) {
                    LOG.debug "Attempting to transition PPL tube back to 'TubPPL Rdy for QPCR'"
                    WorkflowUtils.transitionIssue(curPPLTubeMutIssue.getId(), transitionPPLId, "Automatically transitioned during Library Pool QPCR re-run process")
                } else {
                    LOG.error "ERROR: Transition id for PPL tube not found, cannot continue"
                }

                // re-fetch the issue and clear fields
                LOG.debug "Re-fetching the issue"
                curPPLTubeMutIssue = WorkflowUtils.getMutableIssueForIssueId(curPPLTubeMutIssue.getId())

                // clear field 'Library QPCR Concentration' on PPL tube
                if(curPPLTubeMutIssue != null) {
                    LOG.debug "Attempting to clear fields"
                    JiraAPIWrapper.clearCFValueByName(curPPLTubeMutIssue, ConfigReader.getCFName("LIBRARY_QPCR_CONCENTRATION"))
                } else {
                    LOG.debug "Re-fetched issue was null, cannot clear"
                }
            }
        }
    }

    /**
     * Process tubes that have failed QPCR
     *
     * @param PPLIssueIds
     */
    public static void processTubesFailed(ArrayList<String> PPLIssueIds) {

        LOG.debug "In method to fail selected PPL tubes"

        if (PPLIssueIds == null || PPLIssueIds.size() <= 0) {
            LOG.error "No PPL issues passed, cannot continue"
            return
        }

        PPLIssueIds.each { String sPPLTubeID ->

            MutableIssue curPPLTubeMutIssue

            try {
                LOG.debug "Parsing ID ${sPPLTubeID} to Long"
                Long sourceTubeIdLong = Long.parseLong(sPPLTubeID)

                LOG.debug "Fetching source issue for Id ${sPPLTubeID}"

                // fetch the mutable issue
                curPPLTubeMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourceTubeIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sPPLTubeID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curPPLTubeMutIssue != null) {

                // set outcome to 'Fail'
                JiraAPIWrapper.setCFSelectValueByName(curPPLTubeMutIssue, ConfigReader.getCFName("LIBRARY_QPCR_OUTCOME"), 'Fail')

                // transition action id to transition tube to 'TubPPL Failed' with resolution 'Failed in QPCR' via 'Fail in QPCR'
                int transitionPPLId = ConfigReader.getTransitionActionId(
                        WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_FAIL_IN_QPCR.toString()
                )
                if (transitionPPLId > 0) {
                    LOG.debug "Attempting to transition PPL tube to 'TubPPL Failed'"
                    WorkflowUtils.transitionIssue(curPPLTubeMutIssue.getId(), transitionPPLId, "Automatically transitioned during Library Pool QPCR fail process")
                } else {
                    LOG.error "ERROR: Transition id for PPL tube not found, cannot continue"
                }
            }
        }
    }

    /**
     * Delete all attachments from the issue.
     *
     * @param QPCRIssue
     */
    public static void deleteAttachments(Issue QPCRIssue) {

        LOG.debug "Deleting all attachments from the issue with Id <${QPCRIssue.getId()}>"
        AttachmentManager attachMgr = ComponentAccessor.getAttachmentManager();
        List<Attachment> attachments = attachMgr.getAttachments(QPCRIssue);
        if (!attachments.isEmpty()) {
            LOG.debug "Found ${Integer.toString(attachments.size())} attachments to delete"
            attachments.each{ Attachment attachment ->
                attachMgr.deleteAttachment(attachment)
            }
        }
    }

    /**
     * Create the wiki markup string buffer for the 1 in 1000 dilutions on the Hamilton
     *
     * @param blockMap
     * @return stringbuffer containing the wiki markup string
     */
    private static StringBuffer createBlockWikiMarkup(Map<String, String> blockMap) {

        LOG.debug "Creating wiki markup for block map:"
        LOG.debug blockMap.toMapString()

        // line 1 = table headers for 6 columns of tubes
        def wikiSB = '{html}<div style="text-align: center;">{html}'<<''
        wikiSB <<= System.getProperty("line.separator")
        wikiSB <<= "h2. Hamilton Tube Block Layout"
        wikiSB <<= System.getProperty("line.separator")
        wikiSB <<= '|| '
        wikiSB <<= '|| Column 1 '
        wikiSB <<= '|| Column 2 '
        wikiSB <<= '|| Column 3 '
        wikiSB <<= '|| Column 4 '
        wikiSB <<= '|| Column 5 '
        wikiSB <<= '|| Column 6 '
        wikiSB <<= '||'
        wikiSB <<= System.getProperty("line.separator")

        // lines 2-5 tube rows
        (1..4).each { int iRowIndx ->
            String sRowLetter = getRowLtr(iRowIndx)

            // row header
            wikiSB <<= '|| Row ' + sRowLetter + ' '

            // 6 row positions
            (1..6).each { int iColIndx ->
                String sColIndx = Integer.toString(iColIndx)
                if(blockMap.containsKey(sRowLetter + sColIndx)) {
                    // source PPL barcode
                    String sSrcTubeBC = blockMap[sRowLetter + sColIndx]
                    String[] sSplitSrcTubeBC = sSrcTubeBC.split('\\.')
                    LOG.debug "sSplitSrcTubeBC = ${sSplitSrcTubeBC.toString()}"
                    if (3 == sSplitSrcTubeBC.size()) {
                        wikiSB <<= '| ' + sSplitSrcTubeBC[0] + '.' + sSplitSrcTubeBC[1] + '. \\\\ *' + sSplitSrcTubeBC[02] + '*'
                    } else {
                        LOG.warn "splitSrcTubeBC unexpected size <${sSplitSrcTubeBC.size()}>"
                        wikiSB <<= '| ' + sSrcTubeBC
                    }
                } else {
                    wikiSB <<= '| '
                }
            }

            wikiSB <<= '|'
            wikiSB <<= System.getProperty("line.separator")

        }
        wikiSB <<= '{html}</div>{html}'
        wikiSB

    }

    /**
     * Convert a number to a row letter
     *
     * @param iRowNum
     * @return
     */
    private static String getRowLtr(int iRowNum) {
        if(1 == iRowNum) {
            return 'A'
        }
        if(2 == iRowNum) {
            return 'B'
        }
        if(3 == iRowNum) {
            return 'C'
        }
        if(4 == iRowNum) {
            return 'D'
        }
        LOG.error "ERROR: unexpected row number <${iRowNum}>"
        return null

    }

    /**
     * Helper method to fetch source tubes
     *
     * @param QPCRIssue
     * @return map of barcodes keyed by position
     */
    private static Map<String, Map<String, String>> getSourceTubesMap(Issue QPCRIssue, int iNumberOfTubes) {

        LOG.debug "Get source tubes map, number of tubes = ${iNumberOfTubes}"

        Map<String, Map<String, String>> barcodesMap = [:]

        (1..iNumberOfTubes).each{ int indx ->
            String sIndx = Integer.toString(indx)
            String sCFAlias = "SOURCE_" + sIndx + "_BARCODE"
            barcodesMap.put(
                    sIndx,
                    ['source_bc': JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName(sCFAlias))]
            )
        }

        LOG.debug "Barcodes map size = ${barcodesMap.size()}"
        LOG.debug barcodesMap.toMapString()
        barcodesMap
    }
}
