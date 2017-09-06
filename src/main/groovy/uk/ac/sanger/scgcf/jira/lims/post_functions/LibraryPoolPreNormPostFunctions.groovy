package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParameters
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.user.ApplicationUser
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.BarcodeInfos
import uk.ac.sanger.scgcf.jira.lims.enums.BarcodePrefixes
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.ProjectName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
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
            return null
        }

        LOG.debug "List of barcodes for PPL tubes:"
        LOG.debug listPPLBarcodes.toListString()

        (1..iNumTubes).each { int iIndx ->
            String sTubeIndx = Integer.toString(iIndx)
            String sPPLTubeBarcode = listPPLBarcodes.pop()

            // store barcode in main map
            tubesMap[sTubeIndx]['dest_bc'] = sPPLTubeBarcode

            // set destination custom field on PNM issue
            String cfAlias = "DESTINATION_" + sTubeIndx + "_BARCODE"
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

            // link the LPL tube issue to the PPL tube issue with a 'Relationships' 'is a parent to' link
            LOG.debug "Attempting to create an issue link between the source LPL tube and destination PPL tube <${sTubeIndx}>"
            WorkflowUtils.createIssueLink(LPLIssue.getId(), PPLIssue.getId(), IssueLinkTypeName.RELATIONSHIPS.getLinkTypeName())

            // link PNM issue to the PPL tube issue with a 'Group includes' 'includes containers' link
            LOG.debug "Attempting to create an issue link between the PNM group issue and destination PPL tube <${sTubeIndx}>"
            WorkflowUtils.createIssueLink(PNMIssue.getId(), PPLIssue.getId(), IssueLinkTypeName.GROUP_INCLUDES.getLinkTypeName())

            // transition the tube to 'TubPPL In Pre-Norm' via 'Start pre-normalisation'
            int transitionId = ConfigReader.getTransitionActionId(
                    WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_START_PRE_NORMALISATION.toString()
            )

            if(transitionId > 0) {
                LOG.debug "Attempting to transition PPL to 'In Pre-Norm'"
                WorkflowUtils.transitionIssue(PPLIssue.getId(), transitionId, "Automatically transitioned during Library Pre-Normalisation print PPL labels")
            } else {
                LOG.error "ERROR: Transition id not found, cannot transition PPL tube with Key <${PPLIssue.getKey()}>"
            }

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
    private static void calculateDilutionsForLPLTube(Map<String, Map<String, String>> tubesMap, String sTubeIndx) {

        LOG.debug "Attempting to calculate dilutions for tube with barcode <${tubesMap[sTubeIndx]['source_bc']}>"

        String sLPLConc   = tubesMap[sTubeIndx]['lqc_concentration']
        if(sLPLConc == null) {
            LOG.error "Cannot calculate dilution for tube, lqc_concentration not present"
            return
        }

        double dLPLConc       = Double.parseDouble(sLPLConc)

        // volumes in µl, concentrations in nM
        double dStartVol      = 40.0
        double dMaxFinalVol   = 200.0
        double dFinalVol
        double dTargetConc    = 10.0

        double dVolToTake
        double dVolToBackfill = 0
        double dVolTotal
        double dPPLConc

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

            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("POOLING_BLOCK_COLUMN"), tubesMap[sTubeIndx]["lpo_block_column"].toString())
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
    public static void printPPLTubeLabelsForPPLIssueIds(List<String> PPLIssueIds) {

        LOG.debug "Attempting to print PPL tube labels for issue ids:"
        LOG.debug PPLIssueIds.toListString()

        List<Map<String, String>> tubeLabelsList = []

        // for each id in PPLIssueIds call
        PPLIssueIds.each { String PPLTubeId ->
            Map<String, String> tubeMap = createPPLTubeLabelMap(PPLTubeId)
            if(tubeMap != null) {
                tubeLabelsList.push(tubeMap)
            }
        }

        // TODO: may need to sort this list by number on PNM issue

        LOG.debug 'PPL tube labels data to be printed:'
        tubeLabelsList.each{ Map<String,String> labelData ->
            LOG.debug labelData.toMapString()
        }

        // TODO: run the print job with the choice of tube printer the user selected and suitable label
        // see WorkflowUtils.printPlateLabels as example, need to make a Tube version

    }

    /**
     * Print the PPL tube labels corresponding to the selected list of LPL tube ids
     *
     * @param PNMIssue
     * @param LPLIssueIds
     */
    public static void printPPLTubeLabelsCorrespondingToLPLIds(List<String> LPLIssueIds) {

        LOG.debug "Attempting to print PPL tube labels corresponding to list of LPL tube issue ids:"
        LOG.debug LPLIssueIds.toListString()

        // work out corresponding PPL list
        List<String> PPLIssueIds = []

        // for each LPL id get issue then get child PPL issue id via Relationships link
        LPLIssueIds.each { String LPLIssueId ->

            Issue childPPLIssue = getChildPPLTubeOfLPL(LPLIssueId.toLong(), IssueStatusName.TUBPPL_IN_PRE_NORM.toString())

            if(childPPLIssue != null) {
                PPLIssueIds.push(childPPLIssue.getId().toString())
            } else {
                LOG.error "ERROR: Failed to find child PPL tube issue linked to the LPL tube"
                //TODO: error here, expected to find PPL - what to do?
            }
        }

        if(LPLIssueIds.size() != PPLIssueIds.size()) {
            LOG.error "Size of LPL tube ids list <${LPLIssueIds.size()}> does not match size of derived PPL tube ids list <${PPLIssueIds.size()}>"
            //TODO: error here, list sizes do not match - what to do?
            return
        }

        // call print function
        printPPLTubeLabelsForPPLIssueIds(PPLIssueIds)
    }

    /**
     * Transition LPL issues to Done Empty and their corresponding PPL issues to Rdy for QPCR
     *
     * @param LPLIssueIds
     */
    public static void processTubesOkEmptyForLibraryPoolPreNormalisation(List<String> LPLIssueIds) {

        LOG.debug "Attempting to transition LPL and PPL issues in pre-normalisation where LPLs are empty"

        LPLIssueIds.each { String LPLIssueId ->

            LOG.debug "Processing LPL issue id <${LPLIssueId}>"

            // get source LPL tube issue
            MutableIssue LPLIssue = WorkflowUtils.getMutableIssueForIssueId(LPLIssueId.toLong())

            // get linked PPL tube issue
            Issue PPLIssue = getChildPPLTubeOfLPL(LPLIssueId.toLong(), IssueStatusName.TUBPPL_IN_PRE_NORM.toString())
            if(PPLIssue != null) {

                // transition LPL tube issue via 'Completed pre-normalisation and empty' to 'TubLPL Done Empty'
                int transitionLPLId = ConfigReader.getTransitionActionId(
                    WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_COMPLETED_PRE_NORMALISATION_AND_EMPTY.toString()
                )

                if(transitionLPLId > 0) {
                    LOG.debug "Attempting to transition LPL to 'TubLPL Done Empty'"
                    WorkflowUtils.transitionIssue(LPLIssue.getId(), transitionLPLId, "Automatically transitioned during Library Pre-Normalisation Ok Empty")
                } else {
                    LOG.error "ERROR: Transition id not found, cannot transition LPL tube with Key <${LPLIssue.getKey()}>"
                }

                // transition PPL tube issue via 'Ready for QPCR' to 'TubPPL Rdy for QPCR'
                int transitionPPLId = ConfigReader.getTransitionActionId(
                    WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_READY_FOR_QPCR.toString()
                )

                if(transitionPPLId > 0) {
                    LOG.debug "Attempting to transition PPL to 'Rdy for QPCR'"
                    WorkflowUtils.transitionIssue(PPLIssue.getId(), transitionPPLId, "Automatically transitioned during Library Pre-Normalisation Ok Empty")
                } else {
                    LOG.error "ERROR: Transition id not found, cannot transition PPL tube with Key <${PPLIssue.getKey()}>"
                }

            } else {
                LOG.error "ERROR: Failed to find child PPL tube issue linked to the LPL tube with id <${LPLIssueId}>"
                //TODO: error here, expected to find PPL - what to do?
            }
        }

    }

    /**
     * Transition LPL issues to Done Not Empty and their corresponding PPL issues to Rdy for QPCR
     *
     * @param LPLIssueIds
     */
    public static void processTubesOkNotEmptyForLibraryPoolPreNormalisation(List<String> LPLIssueIds) {

        LOG.debug "Attempting to transition LPL and PPL issues in pre-normalisation where LPLs not empty"

        LPLIssueIds.each { String LPLIssueId ->

            LOG.debug "Processing LPL issue id <${LPLIssueId}>"

            // get source LPL tube issue
            MutableIssue LPLIssue = WorkflowUtils.getMutableIssueForIssueId(LPLIssueId.toLong())

            // get linked PPL tube issue
            Issue PPLIssue = getChildPPLTubeOfLPL(LPLIssueId.toLong(), IssueStatusName.TUBPPL_IN_PRE_NORM.toString())
            if(PPLIssue != null) {

                // transition LPL tube issue via 'Completed pre-normalisation' to 'TubLPL Done Not Empty'
                int transitionLPLId = ConfigReader.getTransitionActionId(
                        WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_COMPLETED_PRE_NORMALISATION.toString()
                )

                if(transitionLPLId > 0) {
                    LOG.debug "Attempting to transition LPL to 'TubLPL Done Empty'"
                    WorkflowUtils.transitionIssue(LPLIssue.getId(), transitionLPLId, "Automatically transitioned during Library Pre-Normalisation Ok NOT Empty")
                } else {
                    LOG.error "ERROR: Transition id not found, cannot transition LPL tube with Key <${LPLIssue.getKey()}>"
                }

                // transition PPL tube issue via 'Ready for QPCR' to 'TubPPL Rdy for QPCR'
                int transitionPPLId = ConfigReader.getTransitionActionId(
                        WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_READY_FOR_QPCR.toString()
                )

                if(transitionPPLId > 0) {
                    LOG.debug "Attempting to transition PPL to 'Rdy for QPCR'"
                    WorkflowUtils.transitionIssue(PPLIssue.getId(), transitionPPLId, "Automatically transitioned during Library Pre-Normalisation Ok NOT Empty")
                } else {
                    LOG.error "ERROR: Transition id not found, cannot transition PPL tube with Key <${PPLIssue.getKey()}>"
                }

            } else {
                LOG.error "ERROR: Failed to find child PPL tube issue linked to the LPL tube with id <${LPLIssueId}>"
                //TODO: error here, expected to find PPL - what to do?
            }
        }

    }

//    For each selected source LPL tube id:
//    get source LPL tube issue
//    get destination PPL issue
//    transition source LPL tube via 'Fail in pre-normalisation' to 'TubLPL Failed'
//    transition destination PPL tube via 'Fail in pre-normalisation' to 'TubPPL Failed'
//    TODO: may need some group checking of tubes here, to decide if need to re-run from LIB plate or ECH plate
    public static void processTubesFailedForLibraryPoolPreNormalisation(List<String> LPLIssueIds) {

        LOG.debug "Attempting to transition LPL and PPL issues failed in pre-normalisation"

        LPLIssueIds.each { String LPLIssueId ->

            LOG.debug "Processing LPL issue id <${LPLIssueId}>"

            // get source LPL tube issue
            MutableIssue LPLIssue = WorkflowUtils.getMutableIssueForIssueId(LPLIssueId.toLong())

            // get linked PPL tube issue
            Issue PPLIssue = getChildPPLTubeOfLPL(LPLIssueId.toLong(), IssueStatusName.TUBPPL_IN_PRE_NORM.toString())
            if(PPLIssue != null) {

                // transition LPL tube issue via 'Fail in pre-normalisation' to 'TubLPL Failed'
                int transitionLPLId = ConfigReader.getTransitionActionId(
                        WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_FAIL_IN_PRE_NORMALISATION.toString()
                )

                if(transitionLPLId > 0) {
                    LOG.debug "Attempting to transition LPL to 'TubLPL Done Empty'"
                    WorkflowUtils.transitionIssue(LPLIssue.getId(), transitionLPLId, "Automatically transitioned during Library Pre-Normalisation Failed tubes")
                } else {
                    LOG.error "ERROR: Transition id not found, cannot transition LPL tube with Key <${LPLIssue.getKey()}>"
                }

                // transition PPL tube issue via 'Fail in pre-normalisation' to 'TubPPL Failed'
                int transitionPPLId = ConfigReader.getTransitionActionId(
                        WorkflowName.TUBE_PPL.toString(), TransitionName.PPL_FAIL_IN_PRE_NORMALISATION.toString()
                )

                if(transitionPPLId > 0) {
                    LOG.debug "Attempting to transition PPL to 'Rdy for QPCR'"
                    WorkflowUtils.transitionIssue(PPLIssue.getId(), transitionPPLId, "Automatically transitioned during Library Pre-Normalisation Failed tubes")
                } else {
                    LOG.error "ERROR: Transition id not found, cannot transition PPL tube with Key <${PPLIssue.getKey()}>"
                }

            } else {
                LOG.error "ERROR: Failed to find child PPL tube issue linked to the LPL tube with id <${LPLIssueId}>"
                //TODO: error here, expected to find PPL - what to do?
            }
        }

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

        // line 1 = table headers for 7 columns of 1 to 10 pre-normalisation tubes
        def wikiSB = '{html}<div style="text-align: center;">{html}'<<''
        wikiSB <<= System.getProperty("line.separator")
        wikiSB <<= '|| '
        wikiSB <<= '|| Source LPL ' + '\\\\ ' + 'Barcode '
        wikiSB <<= '|| Destination PPL ' + '\\\\ ' + 'Barcode '
        wikiSB <<= '|| LPL Conc ' + '\\\\ ' + '(nM) '
        wikiSB <<= '|| Volume to ' + '\\\\ ' + 'take (&mu;l) '
        wikiSB <<= '|| Volume to ' + '\\\\ ' + 'backfill (&mu;l) '
        wikiSB <<= '|| Total Volume ' + '\\\\ ' + '(&mu;l) '
        wikiSB <<= '|| PPL Conc ' + '\\\\ ' + '(nM) '
        wikiSB <<= '||'
        wikiSB <<= System.getProperty("line.separator")

        // lines 2-11 tube rows
        (1..10).each { int iTubeIndx ->
            String sTubeIndx = Integer.toString(iTubeIndx)
            if(tubesMap.containsKey(sTubeIndx)) {
                // row number
                wikiSB <<= '|| ' + sTubeIndx

                // source LPL barcode
                String sSrcTubeBC = tubesMap[sTubeIndx]['source_bc']
                String[] sSplitSrcTubeBC = sSrcTubeBC.split('\\.')
                LOG.debug "sSplitSrcTubeBC = ${sSplitSrcTubeBC.toString()}"
                if (3 == sSplitSrcTubeBC.size()) {
                    wikiSB <<= '| ' + sSplitSrcTubeBC[0] + '.' + sSplitSrcTubeBC[1] + '. \\\\ *' + sSplitSrcTubeBC[02] + '*'
                } else {
                    LOG.warn "splitSrcTubeBC unexpected size <${sSplitSrcTubeBC.size()}>"
                    wikiSB <<= '| ' + sSrcTubeBC
                }

                // destination PPL barcode
                String sDstTubeBC = tubesMap[sTubeIndx]['dest_bc']
                String[] sSplitDstTubeBC = sDstTubeBC.split('\\.')
                LOG.debug "sSplitDstTubeBC = ${sSplitDstTubeBC.toString()}"
                if (3 == sSplitDstTubeBC.size()) {
                    wikiSB <<= '| ' + sSplitDstTubeBC[0] + '.' + sSplitDstTubeBC[1] + '. \\\\ *' + sSplitDstTubeBC[02] + '*'
                } else {
                    LOG.warn "splitSrcTubeBC unexpected size <${sSplitDstTubeBC.size()}>"
                    wikiSB <<= '| ' + sDstTubeBC
                }

                // LPL concentration (nM)
                wikiSB <<= '| ' + tubesMap[sTubeIndx]['lqc_concentration']

                // Volume to take (µl)
                wikiSB <<= '| *' + tubesMap[sTubeIndx]['pnm_vol_to_take'] + '*'

                // Volume to backfill (µl)
                if(tubesMap[sTubeIndx]['pnm_vol_to_backfill'] == "0.0") {
                    wikiSB <<= '| ' + tubesMap[sTubeIndx]['pnm_vol_to_backfill']
                } else {
                    wikiSB <<= '| *' + tubesMap[sTubeIndx]['pnm_vol_to_backfill'] + '*'
                }

                // Total volume (µl)
                wikiSB <<= '| ' + tubesMap[sTubeIndx]['pnm_vol_total']

                // PPL concentration (nM)
                wikiSB <<= '| ' + tubesMap[sTubeIndx]['pnm_concentration']

                wikiSB <<= '|'
                wikiSB <<= System.getProperty("line.separator")
            }
        }
        wikiSB <<= '{html}</div>{html}'
        wikiSB

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

    /**
     * Creates the PPL tube label data for a PPL tube issue id
     *
     * @param LIBIssuesMap
     * @return
     */
    private static Map<String, String> createPPLTubeLabelMap(String PPLIssueId) {

        // create a label data map
        Map<String, String> tubeLabelMap = [:]

        MutableIssue PPLIssue = WorkflowUtils.getMutableIssueForIssueId(PPLIssueId.toLong())

        String sBarcode = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("BARCODE"))
        LOG.debug "Barcode = ${sBarcode}"
        tubeLabelMap['barcode'] = sBarcode

        String sPosition = JiraAPIWrapper.getCFValueByName(PPLIssue, ConfigReader.getCFName("LIBRARY_PNM_SOURCE_NUMBER"))
        LOG.debug "Position in PNM = ${sPosition}"
        tubeLabelMap['position'] = sPosition

        // TODO: what other fields do we need on the PPL label?

        // get the parent LPL container
        List<Issue> parentContainersOfPPL = WorkflowUtils.getParentContainersForContainerId(PPLIssue.getId())

        if(parentContainersOfPPL.size() <= 0) {
            LOG.error "Expected to find parent LPL tube issue linked to the PPL tube but nothing found, cannot continue"
            return null
        }

        LOG.debug "Found <${parentContainersOfPPL.size()}> parents for the PPL tube, assuming 1"

        Issue parentLPLIssue = parentContainersOfPPL.get(0)

        if (parentLPLIssue.getIssueType().getName() == IssueTypeName.TUBE_LPL.toString()) {

            LOG.debug "Parent LPL tube of the PPL tube:"
            LOG.debug "Issue type = ${parentLPLIssue.getIssueType().getName()}"
            String parentLPLStatus = parentLPLIssue.getStatus().getName()
            LOG.debug "Status = ${parentLPLStatus}"
            LOG.debug "Id = ${parentLPLIssue.getId().toString()}"
            LOG.debug "Key = ${parentLPLIssue.getKey()}"

            String sLPLTubeBarcode = JiraAPIWrapper.getCFValueByName(parentLPLIssue, ConfigReader.getCFName("BARCODE"))
            LOG.debug "LPL Barcode = ${sLPLTubeBarcode}"

            tubeLabelMap['parent_barcode'] = sLPLTubeBarcode
            String[] splitLPLBC = sBarcode.split('\\.')
            if (3 == splitLPLBC.size()) {
                tubeLabelMap['barcode_info'] = splitLPLBC[1]
                tubeLabelMap['barcode_number'] = splitLPLBC[2]
            } else {
                tubeLabelMap['barcode_info'] = BarcodeInfos.INFO_LPL.toString()
                tubeLabelMap['barcode_number'] = 'unknown'
            }

        } else {
            LOG.error "Expected to find parent LPL tube issue linked to the PPL tube but found issue with wrong issue type <${parentLPLIssue.getIssueType().getName()}>, cannot continue"
            return null
        }

        tubeLabelMap
    }

    /**
     * Get the child PPL tube issue from the LPL tube issue in the specified state
     *
     * @param LPLIssueId
     * @param PPLIssueStateName
     * @return
     */
    private static Issue getChildPPLTubeOfLPL(Long LPLIssueId, String PPLIssueStateName) {

        LOG.debug "Attempting to fetch the child PPL tube in state <${PPLIssueStateName}> of the LPL tube with Id <${LPLIssueId}>"
        List<Issue> childCntrsOfLPL = WorkflowUtils.getChildContainersForContainerId(LPLIssueId)

        if (childCntrsOfLPL.size() <= 0) {
            LOG.error "Expected to find child PPL tube issues linked to the LPL tube but none found for LPL tube id <${LPLIssueId}>"
            return null
        } else {
            LOG.debug "Found <${childCntrsOfLPL.size()}> child container issues for the LPL tube, look for first in correct state"

            Issue PPLIssue

            childCntrsOfLPL.find{ Issue childIssue ->

                LOG.debug "Issue type = ${childIssue.getIssueType().getName()}"
                String childIssueStatus = childIssue.getStatus().getName()
                LOG.debug "Status = ${childIssueStatus}"
                LOG.debug "Id = ${childIssue.getId().toString()}"
                LOG.debug "Key = ${childIssue.getKey()}"

                // check the issue is a PPL tube and in expected state
                if (childIssue.getIssueType().getName() == IssueTypeName.TUBE_PPL.toString()) {
                    if(childIssue.getStatus().getName() == PPLIssueStateName) {
                        LOG.debug "Found valid child PPL of the LPL tube"
                        PPLIssue = childIssue
                        return true
                    } else {
                        LOG.debug "Found child PPL of the LPL tube in incorrect state"
                    }
                } else {
                    LOG.debug "Found child of the LPL tube of incorrect type"
                }
                return false

            }

            if(PPLIssue != null) {
                return PPLIssue
            } else {
                LOG.error "Expected to find child PPL tube issues with state <${PPLIssueStateName}> to the LPL tube but none found for LPL tube id <${LPLIssueId}>"
                return null
            }
        }
    }

}
