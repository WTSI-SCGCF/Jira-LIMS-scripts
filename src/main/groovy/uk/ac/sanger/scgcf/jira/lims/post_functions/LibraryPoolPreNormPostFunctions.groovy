package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParameters
import com.atlassian.jira.user.ApplicationUser
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.BarcodeInfos
import uk.ac.sanger.scgcf.jira.lims.enums.BarcodePrefixes
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.ProjectName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.services.DummyBarcodeGenerator
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code LibraryPoolPreNormPostFunctions} class holds post functions for the
 * Library Pool Pre-Normalisation project
 *
 * Created by as28 on 18/08/2017.
 */

@Slf4j(value = "LOG")
class LibraryPoolPreNormPostFunctions {

//create wiki markup and set Library PNM Dilutions Table[(text multi)] custom field value.
    // return list of PPL tube ids in order


    public static List<String> createPPLTubesAndCalculateDilutions(Issue PNMIssue) {

        //TODO: should we have an equivalent validation script to check for errors before post function?

        LOG.debug "In post function to create PPL tubes for library pool pre-normalisation"

        // Get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Expected number of LPL tubes = ${Integer.toString(iNumTubes)}"

        Map<String, Map<String, String>> tubesMap = getSourceTubesMap(PNMIssue)
        List<String> listPPLBarcodes = generateBarcodesForTubes(iNumTubes)
        List<String> listPPLIds = []

        if (listPPLBarcodes == null || listPPLBarcodes.size() != iNumTubes) {
            LOG.error "List of barcodes for PPL tubes is not the expected size <${Integer.toString(iNumTubes)}>"
            return
        }

        LOG.debug "List of barcodes for PPL tubes:"
        LOG.debug listPPLBarcodes.toListString()

        (1..iNumTubes).each { int iIndx ->
            String sTubeIndx = Integer.toString(iIndx)
            String sPPLTubeBarcode = listPPLBarcodes.get(iIndx - 1)

            // store barcode in main map
            tubesMap[sTubeIndx]['dest_bc'] = sPPLTubeBarcode

            // set destination custom field on PNM issue
            String cfAlias = "SOURCE_" + sTubeIndx + "_BARCODE"
            JiraAPIWrapper.setCFValueByName(PNMIssue, ConfigReader.getCFName(cfAlias), sPPLTubeBarcode)

            // get the LPL issue by barcode
            String sLPLTubeBarcode = tubesMap[sTubeIndx]['source_bc']
            Issue LPLIssue = WorkflowUtils.getIssueForBarcode(sLPLTubeBarcode)

            // get the key fields from the LPL issue and store in map
            extractLPLDetailsIntoMap(tubesMap, sTubeIndx, LPLIssue)

            // calculate dilutions and add to map
            //calculate dilutions using LPL Library QC Concentration, aiming to get <= 10nM in a max of 200µl final volume. Assume LPL starts with around 40µl.
            calculateDilutionsForLPLTube(tubesMap, sTubeIndx)

            // create the PPL tube
            Issue PPLIssue = createPPLTubeIssue(tubesMap, sTubeIndx)
            listPPLIds.push(Long.toString(PPLIssue.getId()))

            // link the LPL issue to the PPL issue with a 'Relationships' 'is a parent to' link
            LOG.debug "Attempting to create an issue link between the source LPL tube and destination PPL tube <${sTubeIndx}>"
            WorkflowUtils.createIssueLink(LPLIssue.getId(), PPLIssue.getId(), IssueLinkTypeName.RELATIONSHIPS.getLinkTypeName())

            // link PNM issue to the PPL tube issue with a 'Group includes' 'includes containers' link
            LOG.debug "Attempting to create an issue link between the PNM group issue and destination PPL tube <${sTubeIndx}>"
            WorkflowUtils.createIssueLink(PNMIssue.getId(), PPLIssue.getId(), IssueLinkTypeName.GROUP_INCLUDES.getLinkTypeName())

        }

        // should now have a tubes map including all the information needed to build the wiki markup dilutions table
        LOG.debug "Tubes map after PPL tube creation:"
        LOG.debug tubesMap.toMapString()

        // Create wiki markup and generate tube block layout table using content in wikiMarkupMap:
        def sbMarkup = createDilutionsWikiMarkup(tubesMap)

        //   Set CF 'Library PNM Dilutions Table' to sWikiMarkup
        JiraAPIWrapper.setCFValueByName(PNMIssue, ConfigReader.getCFName("LIBRARY_PNM_DILUTIONS_TABLE"), sbMarkup.toString())

        listPPLIds

    }

    /**
     * Extract details from the LPL tube issue.
     * Used when creating corresponding PPL tube and for calculation of dilutions.
     *
     * @param tubesMap - map of tubes
     * @param sTubeIndx - the index of the current tube within the tubes map
     * @param LPLIssue - LPL tube issue
     */
    private
    static void extractLPLDetailsIntoMap(Map<String, Map<String, String>> tubesMap, String sTubeIndx, Issue LPLIssue) {

        LOG.debug "Extracting LPL details in to tubes map"

        String submCustomer = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("CUSTOMER"))
        LOG.debug "Customer = ${submCustomer}"
        if(submCustomer != null) { tubesMap[sTubeIndx]['customer'] = submCustomer }

        String submDueDate  = LPLIssue.getDueDate().format('d/MMM/yy')
        LOG.debug "Due Date = ${submDueDate}"
        if(submDueDate != null) { tubesMap[sTubeIndx]['due_date'] = submDueDate }

        String sSubmittedPlateFormatOptId = WorkflowUtils.getSBMPlateFormatOptionId(LPLIssue)
        LOG.debug "SBM Plate Format Opt Id = ${sSubmittedPlateFormatOptId}"
        if(sSubmittedPlateFormatOptId != null) { tubesMap[sTubeIndx]['sbm_plate_format_opt_id'] = sSubmittedPlateFormatOptId }

        String sSBMCellsPerPoolOptId = WorkflowUtils.getSBMCellsPerPoolOptionId(LPLIssue)
        LOG.debug "SBM Cells per Library Pool Opt Id = ${sSBMCellsPerPoolOptId}"
        if(sSBMCellsPerPoolOptId != null) { tubesMap[sTubeIndx]['sbm_cells_per_library_pool_opt_id'] = sSBMCellsPerPoolOptId }

        String sIqcOutcomeOptId = WorkflowUtils.getIQCOutcomeOptionId(LPLIssue)
        LOG.debug "IQC Outcome Opt Id = ${sIqcOutcomeOptId}"
        if(sIqcOutcomeOptId != null) {  tubesMap[sTubeIndx]['iqc_outcome_opt_id'] = sIqcOutcomeOptId }

        String sIqcFeedbackOptId = WorkflowUtils.getIQCFeedbackOptionId(LPLIssue)
        LOG.debug "IQC Outcome Feedback Opt Id = ${sIqcFeedbackOptId}"
        if(sIqcFeedbackOptId != null) { tubesMap[sTubeIndx]['iqc_feedback_opt_id'] = sIqcFeedbackOptId }

        String qntAvgSampleConc = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("AVG_SAMPLE_CONCENTRATION"))
        LOG.debug "QNT Avg Sample Conc = ${qntAvgSampleConc}"
        if(qntAvgSampleConc != null) { tubesMap[sTubeIndx]['qnt_avg_sample_conc'] = qntAvgSampleConc }

        String lpoBlockColumn = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("POOLING_BLOCK_COLUMN"))
        LOG.debug "LPO Pooling Block Column = ${lpoBlockColumn}"
        if(lpoBlockColumn != null) { tubesMap[sTubeIndx]['lpo_block_column'] = lpoBlockColumn }

        String lpoBlockRow = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("POOLING_BLOCK_ROW"))
        LOG.debug "LPO Pooling Block Row = ${lpoBlockRow}"
        if(lpoBlockRow != null) { tubesMap[sTubeIndx]['lpo_block_row'] = lpoBlockRow }

        String lpoPooledFromQuadrant = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("POOLED_FROM_QUADRANT"))
        LOG.debug "LPO Pooled From Quadrant = ${lpoPooledFromQuadrant}"
        if(lpoPooledFromQuadrant != null) { tubesMap[sTubeIndx]['lpo_pooled_from_quadrant'] = lpoPooledFromQuadrant }

        String lpoParentLIBPlate = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("SOURCE_LIB_PLATE"))
        LOG.debug "LPO Source LIB Plate = ${lpoParentLIBPlate}"
        if(lpoParentLIBPlate != null) { tubesMap[sTubeIndx]['lpo_parent_lib_plt_barcode'] = lpoParentLIBPlate }

        String lqcChipPosition = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("LIBRARY_QC_CHIP_POSITION"))
        LOG.debug "LPO Source LIB Plate = ${lqcChipPosition}"
        if(lqcChipPosition != null) { tubesMap[sTubeIndx]['lqc_chip_position'] = lqcChipPosition }

        String lqcConc = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("LIBRARY_QC_CONCENTRATION"))
        LOG.debug "LQC Concentration = ${lqcConc}"
        if(lqcConc != null) { tubesMap[sTubeIndx]['lqc_concentration'] = lqcConc }

        String lqcAvgFragSize = JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("LIBRARY_QC_AVERAGE_FRAGMENT_SIZE"))
        LOG.debug "LQC Avg Fragment Size = ${lqcAvgFragSize}"
        if(lqcAvgFragSize != null) { tubesMap[sTubeIndx]['lqc_avg_frag_size'] = lqcAvgFragSize }

        String lqcPercentTotalDNA= JiraAPIWrapper.getCFValueByName(LPLIssue, ConfigReader.getCFName("LIBRARY_QC_PERCENT_TOTAL_DNA"))
        LOG.debug "LQC Percent Total DNA = ${lqcPercentTotalDNA}"
        if(lqcPercentTotalDNA != null) { tubesMap[sTubeIndx]['lqc_percent_total_dna'] = lqcPercentTotalDNA }

        String sLQCOutcomeOptId = WorkflowUtils.getLQCOutcomeOptionId(LPLIssue)
        LOG.debug "LQC Outcome Opt Id = ${sLQCOutcomeOptId}"
        if(sLQCOutcomeOptId != null) {  tubesMap[sTubeIndx]['lqc_outcome_opt_id'] = sLQCOutcomeOptId }

    }

    /**
     * Calculate the dilution required to make a final concentration of 10nM in the PPL tube.
     *
     * @param tubesMap
     * @param sTubeIndx
     */
    private static void calculateDilutionsForLPLTube(tubesMap, sTubeIndx) {

        String sLPLConc   = tubesMap[sTubeIndx]['lqc_concentration']
        if(sLPLConc == null) {
            LOG.error "Cannot calculate dilution for tube with barcode <${tubesMap[sTubeIndx]['source_bc']}>"
            return
        }

        double dLPLConc       = Double.parseDouble(sLPLConc)

        // volumes in µl, concentrations in nM
        double dStartVol      = 40.0
        double dMinFinalVol   = 40.0
        double dMaxFinalVol   = 200.0
        double dFinalVol      = 0
        double dTargetConc    = 10.0

        double dVolToTake     = 0
        double dVolToBackfill = 0
        double dVolTotal      = 0
        double dPPLConc       = 0

        if(dLPLConc <= dTargetConc) {
            // concentration low, use all the source sample
            dVolToTake = dStartVol
            dVolTotal  = dStartVol
            dPPLConc   = dLPLConc
        } else {
            // try to take all the source sample
            dFinalVol  = (dLPLConc * dStartVol) / dTargetConc
            if(dFinalVol > dMaxFinalVol) {
                // concentration very high, make the max vol and no more
                dVolToTake            = (dTargetConc * dMaxFinalVol) / dLPLConc
                dVolToBackfill        = dMaxFinalVol - dVolToTake
                dVolTotal             = dMaxFinalVol
                dPPLConc              = dTargetConc
            } else {
                // use all the source sample
                dVolToTake            = dStartVol
                dVolToBackfill        = dFinalVol - dStartVol
                dVolTotal             = dFinalVol
                dPPLConc              = dTargetConc
            }
        }
        LOG.debug "Vol to take       = ${Double.toString(dVolToTake)}"
        LOG.debug "Vol to backfill   = ${Double.toString(dVolToBackfill)}"
        LOG.debug "Vol total         = ${Double.toString(dVolTotal)}"
        LOG.debug "PPL Conc          = ${Double.toString(dPPLConc)}"

        double dVolToTakeRounded     = WorkflowUtils.round(dVolToTake, 2, BigDecimal.ROUND_HALF_UP)
        double dVolToBackfillRounded = WorkflowUtils.round(dVolToBackfill, 2, BigDecimal.ROUND_HALF_UP)
        double dVolTotalRounded      = WorkflowUtils.round(dVolTotal, 2, BigDecimal.ROUND_HALF_UP)
        double dPPLConcRounded       = WorkflowUtils.round(dPPLConc, 2, BigDecimal.ROUND_HALF_UP)

        tubesMap[sTubeIndx]['pnm_vol_to_take']     = Double.toString(dVolToTakeRounded)
        tubesMap[sTubeIndx]['pnm_vol_to_backfill'] = Double.toString(dVolToBackfillRounded)
        tubesMap[sTubeIndx]['pnm_vol_total']       = Double.toString(dVolTotalRounded)
        tubesMap[sTubeIndx]['pnm_concentration']   = Double.toString(dPPLConcRounded)

    }

    /**
     * Creates a PPL tube issue
     *
     * @param tubesMap
     * @param sTubeIndx
     * @return
     */
    private static Issue createPPLTubeIssue(Map<String, Map<String, String>> tubesMap, String sTubeIndx) {

//        Customer
//        Due Date(format)
//        Plate Format(select) - sbm_plate_format_opt_id
//        Cells Per Library Pool(select) - sbm_cells_per_library_pool_opt_id
//        IQC Outcome(select) - iqc_outcome_opt_id
//        IQC Feedback(select) - iqc_feedback_opt_id
//        Avg Sample Concentration(number) - qnt_avg_sample_conc
//        Pooling Block Column(string) - lpo_block_column
//        Pooling Block Row(string) - lpo_block_row
//        Pooled from Quadrant(string) - lpo_pooled_from_quadrant
//        Source LIB Plate(string) - lpo_parent_lib_plt_barcode
//        Library QC Chip Position(string) - lqc_chip_position
//        Library QC Concentration(number) - lqc_concentration
//        Library QC Average Fragment Size(number) - lqc_avg_frag_size
//        Library QC Percent Total DNA(number) - lqc_percent_total_dna
//        Library QC Outcome(select) - lqc_outcome_opt_id
//        Library PNM Concentration(number) - pnm_concentration

        LOG.debug "In create PPL tube issue method"
        LOG.debug "sTubeIndx = ${sTubeIndx}"
        LOG.debug "tubesMap = ${tubesMap.toMapString()}"
        ApplicationUser automationUser = WorkflowUtils.getAutomationUser()

        IssueService issueService = ComponentAccessor.getIssueService()

        IssueInputParameters issParams = issueService.newIssueInputParameters()
        issParams.setProjectId(ComponentAccessor.getProjectManager().getProjectObjByName(ProjectName.CONTAINERS.toString()).getId())
        issParams.setIssueTypeId(WorkflowUtils.getIssueTypeByName(IssueTypeName.TUBE_PPL.toString()).getId())
        issParams.setSummary("Pre-Normalised Library Pool Tube: ${tubesMap[sTubeIndx]["dest_bc"]}")

        // TODO: permissions - may need to set security level here
//                issParams.setSecurityLevelId(10000L)
        issParams.setReporterId(WorkflowUtils.getLoggedInUser().getId().toString())
        issParams.setAssigneeId(WorkflowUtils.getLoggedInUser().getId().toString())
        issParams.setComment("This issue was created automatically during Library Pool Pre-Normalisation")

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("customer")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("CUSTOMER"), tubesMap[sTubeIndx]["customer"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("due_date")) {

            issParams.setDueDate(tubesMap[sTubeIndx]["due_date"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("dest_bc")) {
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("BARCODE"), tubesMap[sTubeIndx]["dest_bc"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("sbm_plate_format_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("PLATE_FORMAT"), tubesMap[sTubeIndx]["sbm_plate_format_opt_id"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("sbm_cells_per_library_pool_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("CELLS_PER_LIBRARY_POOL"), tubesMap[sTubeIndx]["sbm_cells_per_library_pool_opt_id"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("iqc_outcome_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("IQC_OUTCOME"), tubesMap[sTubeIndx]["iqc_outcome_opt_id"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("iqc_feedback_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("IQC_FEEDBACK"), tubesMap[sTubeIndx]["iqc_feedback_opt_id"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("qnt_avg_sample_conc")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("AVG_SAMPLE_CONCENTRATION"), tubesMap[sTubeIndx]["qnt_avg_sample_conc"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lpo_block_column")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("POOLING_BLOCK_COLUMN"), tubesMap["lpo_block_column"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lpo_block_row")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("POOLING_BLOCK_ROW"), tubesMap[sTubeIndx]["lpo_block_row"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lpo_pooled_from_quadrant")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("POOLED_FROM_QUADRANT"), tubesMap[sTubeIndx]["lpo_pooled_from_quadrant"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lpo_parent_lib_plt_barcode")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("SOURCE_LIB_PLATE"), tubesMap[sTubeIndx]["lpo_parent_lib_plt_barcode"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lqc_chip_position")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("LIBRARY_QC_CHIP_POSITION"), tubesMap[sTubeIndx]["lqc_chip_position"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lqc_concentration")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("LIBRARY_QC_CONCENTRATION"), tubesMap[sTubeIndx]["lqc_concentration"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lqc_avg_frag_size")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("LIBRARY_QC_AVERAGE_FRAGMENT_SIZE"), tubesMap[sTubeIndx]["lqc_avg_frag_size"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lqc_percent_total_dna")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("LIBRARY_QC_PERCENT_TOTAL_DNA"), tubesMap[sTubeIndx]["lqc_percent_total_dna"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("lqc_outcome_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("LIBRARY_QC_OUTCOME"), tubesMap[sTubeIndx]["lqc_outcome_opt_id"].toString())
        }

        if (((Map<String, String>) tubesMap[sTubeIndx]).containsKey("pnm_concentration")) {

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("LIBRARY_PNM_CONCENTRATION"), tubesMap[sTubeIndx]["pnm_concentration"].toString())
        }

        issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("LIBRARY_PNM_SOURCE_NUMBER"), sTubeIndx)

        LOG.debug "Calling issue creation for PPL tube issue with barcode <${tubesMap[sTubeIndx]["dest_bc"]}>"
        LOG.debug "Issue parameters:"
        LOG.debug issParams.toString()
        Issue createdIssue = WorkflowUtils.createIssue(issueService, automationUser, issParams)
        if (createdIssue != null) {
            LOG.debug "Created PPL tube issue with summary <${createdIssue.getSummary()}> and key <${createdIssue.getKey()}>"
            return createdIssue
        } else {
            LOG.error "Failed to create PPL tube issue"
            return null
        }
    }

    //print the labels to the printer selected. Set the parent barcode to the matching LPL tube, and set the number on top
//of the tube to the position number for user to match to table.
//*NB. Select information and print labels json to log but do not attempt to print anything yet.
    public static void printPPLTubeLabelsForPPLIssueIds(Issue PNMIssue, List<String> PPLTubeIds) {

        ? ? ?

    }

    // get the list of PPL issue ids corresponding to the selected list of LPL tube ids
    public static List<String> printPPLTubeLabelsCorrespondingToLPLIds(Issue PNMIssue, List<String> LPLTubeIds) {

        // work out corresponding PPL list
        List<String> PPLTubeIds

        ? ? ?

        // call print function
        printPPLTubeLabelsForPPLIssueIds(PNMIssue, PPLTubeIds)
    }

    //    For each selected source LPL tube id:
//    get source LPL tube issue
//    get destination PPL tube issue
//    transition LPL tube via 'Completed pre-normalisation and empty' to 'TubLPL Done Empty'
//    transition PPL tube via 'Ready for QPCR' to 'TubPPL Rdy for QPCR'
    public static void processTubesOkEmptyForLibraryPoolPreNormalisation(Issue PNMIssue, List<String> LPLTubeIds) {

        ? ? ?

    }

//    For each selected source LPL tube id:
//    get source LPL tube issue
//    get destination PPL tube issue
//    transition LPL tube via 'Completed pre-normalisation' to 'TubLPL Done Not Empty'
//    transition PPL tube via 'Ready for QPCR' to 'TubPPL Rdy for QPCR'
    public static void processTubesOkNotEmptyForLibraryPoolPreNormalisation(Issue PNMIssue, List<String> LPLTubeIds) {

        ? ? ?

    }

//    For each selected source LPL tube id:
//    get source LPL tube issue
//    get destination PPL issue
//    transition source LPL tube via 'Fail in pre-normalisation' to 'TubLPL Failed'
//    transition destination PPL tube via 'Fail in pre-normalisation' to 'TubPPL Failed'
//    TODO: may need some group checking of tubes here, to decide if need to re-run from LIB plate or ECH plate
    public static void processTubesFailedForLibraryPoolPreNormalisation(Issue PNMIssue, List<String> LPLTubeIds) {

        ? ? ?

    }

    /**
     * Create the wiki markup string buffer for the pre-normalisation dilutions
     *
     * @param tubesMap
     * @return stringbuffer containing the wiki markup string
     */
    private static StringBuffer createDilutionsWikiMarkup(Map<String, Map<String, String>> tubesMap) {

        LOG.debug "Creating wiki markup for tubes map:"
        LOG.debug tubesMap.toMapString()

        ? ? ?

//        // line 1 = table headers for 8 columns of 1 to 11 tube results
//        def wikiSB = '{html}<div style="text-align: center;">{html}'<<''
//        wikiSB <<= System.getProperty("line.separator")
//        wikiSB <<= '|| '
//        wikiSB <<= '|| Barcode '
//        wikiSB <<= '|| Conc ' + '\\\\ ' + '(nM)'
//        wikiSB <<= '|| Avg ' + '\\\\ ' + 'Fragment ' + '\\\\ ' + 'Size (bp)'
//        wikiSB <<= '|| % Total ' + '\\\\ ' + 'DNA'
//        wikiSB <<= '|| SBM Plt ' + '\\\\ ' + 'Format'
//        wikiSB <<= '|| IQC ' + '\\\\ ' + 'Outcome'
//        wikiSB <<= '|| IQC ' + '\\\\ ' + 'Feedback'
//        wikiSB <<= '|| QNT Avg' + '\\\\' + 'Sample ' + '\\\\ ' + 'Conc (ng/&mu;l)'
//        wikiSB <<= '||'
//        wikiSB <<= System.getProperty("line.separator")
//
//        // lines 2-12 tube rows
//        (1..11).each { int iRow ->
//            String sRow = Integer.toString(iRow)
//            if(dilutionsMap.containsKey(sRow)) {
//                wikiSB <<= '|| ' + sRow
//                String sTubeBC = dilutionsMap[sRow]['barcode']
//                String[] sSplitTubeBC = sTubeBC.split('\\.')
//                LOG.debug "sSplitTubeBC = ${sSplitTubeBC.toString()}"
//                if(3 == sSplitTubeBC.size()) {
//                    wikiSB <<= '| ' + sSplitTubeBC[0] + '.' + sSplitTubeBC[1] + '. \\\\ *' + sSplitTubeBC[02] + '*'
//                } else {
//                    LOG.warn "splitTubeBC unexpected size <${sSplitTubeBC.size()}>"
//                    wikiSB <<= '| ' + sTubeBC
//                }
//                wikiSB <<= '| ' + dilutionsMap[sRow]['concentration']
//                wikiSB <<= '| ' + dilutionsMap[sRow]['fragmentsize']
//                wikiSB <<= '| ' + dilutionsMap[sRow]['percent_total']
//                wikiSB <<= '| ' + dilutionsMap[sRow]['sbm_plate_format']
//                wikiSB <<= '| ' + dilutionsMap[sRow]['iqc_outcome']
//                wikiSB <<= '| ' + dilutionsMap[sRow]['iqc_feedback']
//                wikiSB <<= '| ' + dilutionsMap[sRow]['qnt_avg_conc']
//
//            } else {
//                wikiSB <<= '|| ' + sRow
//                wikiSB <<= '| | | | | | | | '
//            }
//            wikiSB <<= '|'
//            wikiSB <<= System.getProperty("line.separator")
//        }
//        wikiSB <<= '{html}</div>{html}'
//        wikiSB

    }

    /**
     * Helper method to fetch source tubes
     *
     * @param PNMIssue
     * @return map of barcodes keyed by position
     */
    private static Map<String, Map<String, String>> getSourceTubesMap(Issue PNMIssue) {

        // fetch Number of Tubes from PNM issue
        int numberOfTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("NUMBER_OF_TUBES"))).intValue()
        LOG.debug "Number of tubes = ${numberOfTubes}"

        Map<String, Map<String, String>> barcodesMap = [:]

        if (numberOfTubes > 0) {
            barcodesMap.put(
                    "1",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_1_BARCODE"))]
            )
        }
        if (numberOfTubes > 1) {
            barcodesMap.put(
                    "2",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_2_BARCODE"))]
            )
        }
        if (numberOfTubes > 2) {
            barcodesMap.put(
                    "3",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_3_BARCODE"))]
            )
        }
        if (numberOfTubes > 3) {
            barcodesMap.put(
                    "4",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_4_BARCODE"))]
            )
        }
        if (numberOfTubes > 4) {
            barcodesMap.put(
                    "5",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_5_BARCODE"))]
            )
        }
        if (numberOfTubes > 5) {
            barcodesMap.put(
                    "6",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_6_BARCODE"))]
            )
        }
        if (numberOfTubes > 6) {
            barcodesMap.put(
                    "7",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_7_BARCODE"))]
            )
        }
        if (numberOfTubes > 7) {
            barcodesMap.put(
                    "8",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_8_BARCODE"))]
            )
        }
        if (numberOfTubes > 8) {
            barcodesMap.put(
                    "9",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_9_BARCODE"))]
            )
        }
        if (numberOfTubes > 9) {
            barcodesMap.put(
                    "10",
                    ['source_bc': JiraAPIWrapper.getCFValueByName(PNMIssue, ConfigReader.getCFName("SOURCE_10_BARCODE"))]
            )
        }

        LOG.debug "Barcodes map size = ${barcodesMap.size()}"
        LOG.debug barcodesMap.toMapString()
        barcodesMap
    }

    /**
     * Generate a number of barcodes for the tubes
     *
     * @param numBarcodesReqd
     * @return list of barcode strings
     */
    private static List<String> generateBarcodesForTubes(int numBarcodesReqd) {

        List<String> barcodesList = []

        //TODO: when barcode generator service works reliably uncomment this section
        //TODO: do we need to have a check to ensure a barcode has not already been used in JIRA? or trust the service?
//        def barcodeGenerator = new BarcodeGenerator()
//        if (numBarcodesReqd == 1) {
//            barcodesList.add(barcodeGenerator.generateSingleBarcode(BarcodePrefixes.PRFX_SCGC.toString(), BarcodeInfos.INFO_LPL.toString()))
//        } else {
//            barcodesList.addAll(barcodeGenerator.generateBatchBarcodes(BarcodePrefixes.PRFX_SCGC.toString(), BarcodeInfos.INFO_LPL.toString(), numBarcodesReqd))
//        }

        if (1 == numBarcodesReqd) {
            barcodesList.add(DummyBarcodeGenerator.generateBarcode(BarcodePrefixes.PRFX_SCGC.toString(), BarcodeInfos.INFO_PPL.toString(), 1))
            Thread.sleep(1000) // to prevent following dummy barcodes being same
        } else {
            barcodesList.addAll(DummyBarcodeGenerator.generateBarcodeBatch(BarcodePrefixes.PRFX_SCGC.toString(), BarcodeInfos.INFO_PPL.toString(), numBarcodesReqd))
            // reverse the barcodes so they are used in correct order later (mutate true to change original list)
            barcodesList.reverse(true)
            Thread.sleep(1000) // to prevent following dummy barcodes being same
        }

        barcodesList
    }

}
