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
import uk.ac.sanger.scgcf.jira.lims.enums.SelectOptionId
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.services.DummyBarcodeGenerator
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code LibraryPoolingPostFunctions} class holds post functions for the Library Pooling project
 *
 * Created by as28 on 06/07/2017.
 */

@Slf4j(value = "LOG")
class LibraryPoolingPostFunctions {

    /**
     * This post function script determines how many LPL tubes are required for each source LIB plate,
     * by checking the ancestor plates and Submission option for 'Cells per Pool'.
     *
     * Each LPL tube is then created and linked to both their source LIB plate and to the Library Pooling
     * issue. Each LPL tube issue will contain the information needed to print it's own label.
     *
     * Also creates a wiki markup table to display in the Library Pooling issue 'Block Layout' field
     * that shows the user how the Hamilton tube block should look.
     *
     * @param LPOIssue the library pooling issue
     */
    public static void createTubesForLibraryPooling(Issue LPOIssue) {

        //TODO: should have an equivalent validation script to check for errors before post function

        LOG.debug "In post function to create tubes for library pooling"

        // Get the number of plates custom field value
        int expectedNumPlates = Double.valueOf(JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("NUMBER_OF_PLATES")))
        LOG.debug "Expected number of LIB plates = ${expectedNumPlates}"

        // Get the LIB plates linked to this issue
        List<Issue> linkedContainers = WorkflowUtils.getContainersLinkedToGroup(LPOIssue)

        if(linkedContainers.size() <= 0) {
            LOG.error "Expected to find LIB plate issues linked to the Library Pooling but none found, cannot continue"
            return
        }

        Map<String, String> libBarcodesMap = getLIBPlateBarcodesMap(LPOIssue)
        Map<String, Issue> libIssuesMap = [:]
        Map<String, Map> blockMap = [:]

        linkedContainers.each { Issue linkedContainer ->
            // NB. the plates will not be ordered as we get them back here, as source number 1 to 6
            if (linkedContainer.getIssueType().getName() == IssueTypeName.PLATE_LIB.toString()) {

                LOG.debug "Found linked LIB plate with key <${linkedContainer.getKey()}>"

                // get barcode of LIB plate
                String sLIBPlateBarcode = JiraAPIWrapper.getCFValueByName(linkedContainer, ConfigReader.getCFName("BARCODE"))
                LOG.debug "LIB plate barcode = ${sLIBPlateBarcode}"

                // determine block column from barcode
                if (libBarcodesMap.containsKey(sLIBPlateBarcode)) {

                    String sBlockCol = libBarcodesMap[sLIBPlateBarcode]
                    libIssuesMap[sBlockCol] = linkedContainer

                }
            } else {
                LOG.warn "Unexpected container issue type connected to Library Pooling issue with Id <${LPOIssue.getId()}>:"
                LOG.warn "Id = <${linkedContainer.getId().toString()}>"
                LOG.warn "Key = <${linkedContainer.getKey()}>"
            }
        }

        // we should now have a map of source number (= block column) vs LIB plate issue
        if(libIssuesMap.size() != expectedNumPlates) {
            LOG.error "ERROR: Number of LIB plates found as links <${libIssuesMap.size()}>, is different than expected <${expectedNumPlates}>"
        }

        // determine how many tubes each LIB plate needs to be pooled to
        (1..expectedNumPlates).each { int indx ->
            String sIndx = Integer.toString(indx)
            LOG.error "Source LIB number <${sIndx}>"
            if(libIssuesMap.containsKey(sIndx)) {
                Map<String, Object> tubesMap = createTubesForLibraryPlate(LPOIssue, libIssuesMap[sIndx], libBarcodesMap)

                // add the tubes for this source LIB to the block layout map
                if (tubesMap == null) {
                    LOG.error "ERROR: TubesMap null on return from trying to determine library pool tubes for LIB issue with Key <${libIssuesMap[sIndx].getKey()}>"
                } else {
                    String sBlockCol = tubesMap["block_column"]
                    blockMap[sBlockCol] = tubesMap
                }
            } else {
                LOG.error "ERROR: Failed to find key <${sIndx}> in LIB issues map"
            }
        }

        // should now have a block map the same size as the expected number of plates, containing a map of tubes
        // for each plate (column) in the block
        if(blockMap.size() != expectedNumPlates) {
            LOG.warn "Number of columns for block layout <${blockMap.size()}>, is different than expected <${expectedNumPlates}>"
        }

        LOG.debug "Block map after Tube generation:"
        LOG.debug blockMap.toMapString()
        //[1:[block_column:1, number_of_tubes:1, 1:[parent_barcode:TEST.LIB.16014901, block_row:A, pooled_from_quadrant:All, barcode:SCGC.LPL.10061901]]]

        // Create wiki markup and generate tube block layout table using content in wikiMarkupMap:
        def sbMarkup = createBlockLayoutWikiMarkup(blockMap)

        //   Set CF 'Tube Block Positions' to sWikiMarkup
        JiraAPIWrapper.setCFValueByName(LPOIssue, ConfigReader.getCFName("TUBE_BLOCK_POSITIONS"), sbMarkup.toString())

    }

    /**
     * Determine the number of LPL tubes required, generate their barcodes, create the issues and link them together
     *
     * @param LIBPlateIssue
     * @param libBarcodesMap
     * @return
     */
    private static Map<String, Object> createTubesForLibraryPlate(Issue LPOIssue, Issue LIBPlateIssue, Map<String, String> libBarcodesMap) {

        // Create tubes map to hold results
        Map<String, Object> tubesMap = [:]

        // get barcode of LIB plate
        String sLIBPlateBarcode = JiraAPIWrapper.getCFValueByName(LIBPlateIssue, ConfigReader.getCFName("BARCODE"))
        LOG.debug "LIB plate barcode = ${sLIBPlateBarcode}"

        // determine block column
        if(libBarcodesMap.containsKey(sLIBPlateBarcode)) {

            String sBlockCol = libBarcodesMap[sLIBPlateBarcode]
            tubesMap["block_column"] = sBlockCol

            processParentsOfLIB(LIBPlateIssue, tubesMap, sLIBPlateBarcode)
            if(!tubesMap.containsKey('number_of_tubes')) {
                LOG.error "ERROR: Failed to determine number of library tubes, cannot continue"
                return
            }

            LOG.debug "Tubes map after back from checking LIB parents:"
            int numBarcodesReqd = (int)tubesMap['number_of_tubes']

            LOG.debug "Number of barcodes required = ${Integer.toString(numBarcodesReqd)}"
            LOG.debug tubesMap.toMapString()

            // generate barcodes:
            List<String> barcodesList = generateBarcodesForTubes(numBarcodesReqd)
            (1..numBarcodesReqd).each { int indx ->
                String sIndx = Integer.toString(indx)
                tubesMap[sIndx]['barcode'] = barcodesList.pop()
            }

            // print the content of tubesMap to debug
            LOG.debug "Tubes map after barcode generation:"
            LOG.debug tubesMap.toMapString()

            // create LPL tube issues
            Map<String, Object> tubeIssuesMap = ['num_tube_issues':0]
            int iNumTubes = Integer.valueOf(tubesMap['number_of_tubes'].toString())
            (1..iNumTubes).each { int indx ->
                String sIndx = Integer.toString(indx)
                Issue tubeIssue = createLPLTubeIssue(tubesMap, sIndx)
                if(tubeIssue == null) {
                    LOG.error "ERROR: tube creation failed and returned null"
                } else {
                    LOG.debug "Adding tube to tubes map index <${sIndx}>"
                    tubeIssuesMap['num_tube_issues'] += 1
                    tubeIssuesMap[sIndx] = tubeIssue
                }
            }

            LOG.debug "Tube issues map:"
            LOG.debug tubeIssuesMap.toMapString()

            // create issue links and transition tubes
            int iNumTubeIssues = tubeIssuesMap['num_tube_issues']
            (1..iNumTubeIssues).each { int indx ->
                String sIndx = Integer.toString(indx)
                Issue tubeIssue = (Issue)tubeIssuesMap[sIndx]

                if(tubeIssue != null) {
                    // link LIB plate issue to the LPL tube issues with a 'Relationships' 'is a parent to' link
                    LOG.debug "Attempting to create an issue link between the source LIB plate and destination tube <${sIndx}>"
                    WorkflowUtils.createIssueLink(LIBPlateIssue.getId(), tubeIssue.getId(), IssueLinkTypeName.RELATIONSHIPS.getLinkTypeName())

                    // link LPO pooling issue to the LPL tube issue with a 'Group includes' 'includes containers' link
                    LOG.debug "Attempting to create an issue link between the LPO group issue and destination tube <${sIndx}>"
                    WorkflowUtils.createIssueLink(LPOIssue.getId(), tubeIssue.getId(), IssueLinkTypeName.GROUP_INCLUDES.getLinkTypeName())

                    // transition LPL tube issue to In Progress
                    int transitionId = ConfigReader.getTransitionActionId(
                            WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_START_POOLING.toString()
                    )

                    if(transitionId > 0) {
                        LOG.debug "Attempting to transition LPL to 'In Progress'"
                        WorkflowUtils.transitionIssue(tubeIssue.getId(), transitionId, "Automatically transitioned during Library Pooling setup")
                    } else {
                        LOG.error "ERROR: Transition id not found, cannot transition LPL tube with Key <${tubeIssue.getKey()}>"
                    }
                }
            }

            return tubesMap
        } else {
            LOG.error "ERROR: Did not find LIB plate barcode <${sLIBPlateBarcode}> in barcodes map"
            return null
        }

    }

    /**
     * Creates an LPL tube issue
     *
     * @param LPOIssue
     * @param tubesMap
     * @param sTubeIndx
     * @return
     */
    private static Issue createLPLTubeIssue(Map<String, Object> tubesMap, String sTubeIndx) {

        LOG.debug "In create LPL tube issue method"
        ApplicationUser automationUser = WorkflowUtils.getAutomationUser()

        IssueService issueService = ComponentAccessor.getIssueService()

        IssueInputParameters issParams = issueService.newIssueInputParameters()
        issParams.setProjectId(ComponentAccessor.getProjectManager().getProjectObjByName(ProjectName.CONTAINERS.toString()).getId())
        issParams.setIssueTypeId(WorkflowUtils.getIssueTypeByName(IssueTypeName.TUBE_LPL.toString()).getId())
        issParams.setSummary("Library Pool Tube: ${tubesMap[sTubeIndx]["barcode"]}")
        // TODO: permissions - may need to set security level here
//                issParams.setSecurityLevelId(10000L)
        issParams.setReporterId(WorkflowUtils.getLoggedInUser().getId().toString())
        issParams.setAssigneeId(WorkflowUtils.getLoggedInUser().getId().toString())
        issParams.setComment("This issue was created automatically during Library Pooling")
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("due_date")) {
            issParams.setDueDate(tubesMap[sTubeIndx]["due_date"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("barcode")) {
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("BARCODE"), tubesMap[sTubeIndx]["barcode"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("block_row")) {
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("POOLING_BLOCK_ROW"), tubesMap[sTubeIndx]["block_row"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("block_column")) {
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("POOLING_BLOCK_COLUMN"), tubesMap["block_column"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("pooled_from_quadrant")) {
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("POOLED_FROM_QUADRANT"), tubesMap[sTubeIndx]["pooled_from_quadrant"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("customer")) {
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("CUSTOMER"), tubesMap[sTubeIndx]["customer"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("sbm_plate_format_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("PLATE_FORMAT"), tubesMap[sTubeIndx]["sbm_plate_format_opt_id"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("sbm_cells_per_library_pool_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("CELLS_PER_LIBRARY_POOL"), tubesMap[sTubeIndx]["sbm_cells_per_library_pool_opt_id"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("qnt_avg_sample_conc")) {
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("AVG_SAMPLE_CONCENTRATION"), tubesMap[sTubeIndx]["qnt_avg_sample_conc"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("iqc_outcome_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("IQC_OUTCOME"), tubesMap[sTubeIndx]["iqc_outcome_opt_id"].toString())
        }
        if(((Map<String,String>)tubesMap[sTubeIndx]).containsKey("iqc_feedback_opt_id")) {
            // N.B. the value is a select option id as string
            issParams.addCustomFieldValue(JiraAPIWrapper.getCFIDByAliasName("IQC_FEEDBACK"), tubesMap[sTubeIndx]["iqc_feedback_opt_id"].toString())
        }
//                issParams.addCustomFieldValue(myDateField.getId(), "3/Jan/2012")
//                issParams.addCustomFieldValue(myDateTime.getId(), "30/Jan/13 10:53 PM")
//                issParams.addCustomFieldValue(mySelectList.getId(), "1234")                  // 1234 is option id as string
//                issParams.addCustomFieldValue(myMultiSelectList.getId(), "1234, 5678")       // 1234 and 5678 are option id's as strings
//                issParams.addCustomFieldValue(myMultiSelectUserList.getId(), "uname1, uname2")

        LOG.debug "Calling issue creation for LPL tube issue with barcode <${tubesMap[sTubeIndx]["barcode"]}>"
        LOG.debug "Issue parameters:"
        LOG.debug issParams.toString()
        Issue createdIssue = WorkflowUtils.createIssue(issueService, automationUser, issParams)
        if(createdIssue != null) {
            LOG.debug "Created LPL tube issue with summary <${createdIssue.getSummary()}> and key <${createdIssue.getKey()}>"
            return createdIssue
        } else {
            LOG.error "Failed to create LPL tube issue"
            return null
        }
    }

    /**
     * Identify the parent ECH for the LIB plate, and determine it's parent.
     *
     * @param LIBPlateIssue
     * @param tubesMap
     * @return
     */
    private static void processParentsOfLIB(Issue LIBPlateIssue, Map<String, Object> tubesMap, String LIBPlateBarcode) {

        // Identify the parent ECH plate
        LOG.debug "Attempting to fetch the parent ECH plate of the LIB plate"
        List<Issue> parentCntrsOfLIB = WorkflowUtils.getParentContainersForContainerId(LIBPlateIssue.getId())

        if(parentCntrsOfLIB.size() <= 0) {
            LOG.error "Expected to find parent plate issues linked to the LIB plate but none found, cannot continue"
            return
        }

        LOG.debug "Found <${parentCntrsOfLIB.size()}> parents for the LIB plate, processing each:"

        parentCntrsOfLIB.each { Issue parentContainerOfLIB ->

            LOG.debug "Source plate of the LIB plate:"
            LOG.debug "Issue type = ${parentContainerOfLIB.getIssueType().getName()}"
            String parentContOfLIBStatus = parentContainerOfLIB.getStatus().getName()
            LOG.debug "Status = ${parentContOfLIBStatus}"
            LOG.debug "Id = ${parentContainerOfLIB.getId().toString()}"
            LOG.debug "Key = ${parentContainerOfLIB.getKey()}"

            if (parentContainerOfLIB.getIssueType().getName() == IssueTypeName.PLATE_ECH.toString()) {

                // get Quant avg sample concentration from linked Quant Analysis issue
                String sQntAvgSampleConc = null
                Issue qntaIssue = WorkflowUtils.getQuantAnalysisIssueForContainerId(parentContainerOfLIB.getId())
                if(qntaIssue != null) {
                    sQntAvgSampleConc = JiraAPIWrapper.getCFValueByName(parentContainerOfLIB, ConfigReader.getCFName("AVG_SAMPLE_CONCENTRATION"))
                    LOG.debug "QNT Avg Sample Concentration = ${sQntAvgSampleConc}"
                }

                // parent of the ECH may be an SS2, DNA or CMB plate
                processParentsOfECH(parentContainerOfLIB, tubesMap, LIBPlateBarcode, sQntAvgSampleConc)
            }
        }

    }

    /**
     * Get the IQC Outcome option Id value given the issue
     *
     * @param iqcIssue
     * @return
     */
    private static String getIQCOutcomeOptionId(Issue iqcIssue) {

        String sIqcOutcomeOptId
        String sIqcOutcome = JiraAPIWrapper.getCFValueByName(iqcIssue, ConfigReader.getCFName("IQC_OUTCOME"))
        LOG.debug "IQC Outcome = ${sIqcOutcome}"
        if(sIqcOutcome == 'Pass') {
            sIqcOutcomeOptId = SelectOptionId.IQC_OUTCOME_PASS.toString()
        } else if(sIqcOutcome == 'Fail') {
            sIqcOutcomeOptId = SelectOptionId.IQC_OUTCOME_FAIL.toString()
        } else {
            sIqcOutcomeOptId = '-1'
        }
        LOG.debug "IQC Outcome option Id = ${sIqcOutcomeOptId}"
        sIqcOutcomeOptId

    }

    /**
     * Get the IQC Feedback option Id value given the issue
     *
     * @param iqcIssue
     * @return
     */
    private static String getIQCFeedbackOptionId(Issue iqcIssue) {

        String sIqcFeedbackOptId
        String sIqcFeedback = JiraAPIWrapper.getCFValueByName(iqcIssue, ConfigReader.getCFName("IQC_FEEDBACK"))
        LOG.debug "IQC Feedback = ${sIqcFeedback}"
        if(sIqcFeedback == 'Pass') {
            sIqcFeedbackOptId = SelectOptionId.IQC_FEEDBACK_PASS.toString()
        } else if(sIqcFeedback == 'Fail') {
            sIqcFeedbackOptId = SelectOptionId.IQC_FEEDBACK_FAIL.toString()
        } else {
            sIqcFeedbackOptId = '-1'
        }
        LOG.debug "IQC Feedback option Id = ${sIqcFeedbackOptId}"
        sIqcFeedbackOptId

    }

    /**
     * Get the SBM Cells per Library Pool option Id value given the issue
     *
     * @param iqcIssue
     * @return
     */
    private static String getSBMCellsPerPoolOptionId(String sSBMCellsPerPool) {

        String sSBMCellsPerPoolOptId
        LOG.debug "SBM Cells per Library Pool = ${sSBMCellsPerPool}"
        if(sSBMCellsPerPool == '96') {
            sSBMCellsPerPoolOptId = SelectOptionId.SBM_CELLS_PER_LIBRARY_POOL_96.toString()
        } else if(sSBMCellsPerPool == '384') {
            sSBMCellsPerPoolOptId = SelectOptionId.SBM_CELLS_PER_LIBRARY_POOL_384.toString()
        } else {
            sSBMCellsPerPoolOptId = '-1'
        }
        LOG.debug "SBM Cells per Library Pool option Id = ${sSBMCellsPerPoolOptId}"
        sSBMCellsPerPoolOptId

    }

    /**
     * Get the SBM Plate Format option Id value given the issue
     *
     * @param iqcIssue
     * @return
     */
    private static String getSBMPlateFormatOptionId(Issue sbmIssue) {

        String sSBMPlateFormatOptId
        String sSBMPlateFormat = JiraAPIWrapper.getCFValueByName(sbmIssue, ConfigReader.getCFName("PLATE_FORMAT"))
        LOG.debug "SBM Plate Format = ${sSBMPlateFormat}"
        if(sSBMPlateFormat == '96') {
            sSBMPlateFormatOptId = SelectOptionId.SBM_PLATE_FORMAT_96.toString()
        } else if(sSBMPlateFormat == '384') {
            sSBMPlateFormatOptId = SelectOptionId.SBM_PLATE_FORMAT_384.toString()
        } else {
            sSBMPlateFormatOptId = '-1'
        }
        LOG.debug "SBM Plate Format option Id = ${sSBMPlateFormatOptId}"
        sSBMPlateFormatOptId

    }

    /**
     * Identify the parent SS2, DNA or CMB plate for the ECH plate, and determine how many tubes are
     * required for pooling.
     *
     * @param ECHPlateIssue
     * @param tubesMap
     * @param LIBPlateBarcode
     */
    private static void processParentsOfECH(Issue ECHPlateIssue, Map<String, Object> tubesMap, String LIBPlateBarcode, String sQntAvgSampleConc) {

        LOG.debug "Attempting to fetch the parent SS2, DNA or CMB plate of the ECH plate"
        List<Issue> parentCntrsOfECH = WorkflowUtils.getParentContainersForContainerId(ECHPlateIssue.getId())

        if(parentCntrsOfECH.size() <= 0) {
            LOG.error "Expected to find parent plate issues linked to the ECH plate but none found, cannot continue"
            return
        }

        LOG.debug "Found <${parentCntrsOfECH.size()}> parents for the ECH plate, processing each:"

        parentCntrsOfECH.each { Issue parentContOfECH ->

            LOG.debug "Source plate of the ECH plate:"
            LOG.debug "Issue type = ${parentContOfECH.getIssueType().getName()}"
            String parentContOfECHStatus = parentContOfECH.getStatus().getName()
            LOG.debug "Status = ${parentContOfECHStatus}"
            LOG.debug "Id = ${parentContOfECH.getId().toString()}"
            LOG.debug "Key = ${parentContOfECH.getKey()}"

            if (parentContOfECH.getIssueType().getName() == IssueTypeName.PLATE_SS2.toString() || parentContOfECH.getIssueType().getName() == IssueTypeName.PLATE_DNA.toString()) {

                // get cells per pool which should be 96 or 384
                String sSBMCellsPerPool = JiraAPIWrapper.getCFValueByName(parentContOfECH, ConfigReader.getCFName("CELLS_PER_LIBRARY_POOL"))
                String sSBMCellsPerPoolOptId = getSBMCellsPerPoolOptionId(sSBMCellsPerPool)
                LOG.debug "SBM Cells per Library Pool = ${sSBMCellsPerPool}"

                // get plate format which should be 96 or 384
                String sSubmittedPlateFormatOptId = getSBMPlateFormatOptionId(parentContOfECH)

                String sSubmCustomer
                String sIqcOutcomeOptId
                String sIqcFeedbackOptId
                String sSubmDueDate

                // get fields from linked Submission issue
                Issue submIssue = WorkflowUtils.getSubmissionIssueForContainerId(parentContOfECH.getId())
                if(submIssue != null) {
                    sSubmCustomer = JiraAPIWrapper.getCFValueByName(submIssue, ConfigReader.getCFName("CUSTOMER"))
                    LOG.debug "Customer = ${sSubmCustomer}"
                    if(submIssue.getDueDate() != null) {
                        sSubmDueDate = submIssue.getDueDate().format('d/MMM/yy')
                        LOG.debug "Due Date = ${sSubmDueDate}"
                    }
                }

                // get fields from linked IQC issue
                Issue iqcIssue = WorkflowUtils.getIQCIssueForContainerId(parentContOfECH.getId())
                if(iqcIssue != null) {
                    sIqcOutcomeOptId = getIQCOutcomeOptionId(iqcIssue)
                    sIqcFeedbackOptId = getIQCFeedbackOptionId(iqcIssue)
                }

                if(sSBMCellsPerPool == '96') {
                    // num required tubes = 4, quadrants 1-4
                    // add 4 tubes to tubesMap
                    tubesMap['number_of_tubes'] = 4
                    (1..4).each { int indx ->
                        String sIndx = Integer.toString(indx)
                        tubesMap[sIndx] = [:]
                        tubesMap[sIndx]['parent_barcode'] = LIBPlateBarcode
                        tubesMap[sIndx]['block_row'] = getRowLtr(indx) // returns 'A' -> 'D')
                        tubesMap[sIndx]['pooled_from_quadrant'] = sIndx
                        if(sSubmittedPlateFormatOptId != null) {
                            tubesMap[sIndx]['sbm_plate_format_opt_id'] = sSubmittedPlateFormatOptId
                        }
                        if(sSBMCellsPerPoolOptId != null) {
                            tubesMap[sIndx]['sbm_cells_per_library_pool_opt_id'] = sSBMCellsPerPoolOptId
                        }
                        if(sSubmCustomer != null) {
                            tubesMap[sIndx]['customer'] = sSubmCustomer
                        }
                        if(sSubmDueDate != null) {
                            tubesMap[sIndx]['due_date'] = sSubmDueDate
                        }
                        if(sIqcOutcomeOptId != null) {
                            tubesMap[sIndx]['iqc_outcome_opt_id'] = sIqcOutcomeOptId
                        }
                        if(sIqcFeedbackOptId != null) {
                            tubesMap[sIndx]['iqc_feedback_opt_id'] = sIqcFeedbackOptId
                        }
                        if(sQntAvgSampleConc != null) {
                            tubesMap[sIndx]['qnt_avg_sample_conc'] = sQntAvgSampleConc
                        }

                    }
                }

                if(sSBMCellsPerPool == '384') {
                    // num required tubes = 1, quadrants All
                    // add one tube to tubesMap:
                    tubesMap['number_of_tubes'] = 1
                    tubesMap['1'] = [:]
                    tubesMap['1']['parent_barcode'] = LIBPlateBarcode
                    tubesMap['1']['block_row'] = 'A'
                    tubesMap['1']['pooled_from_quadrant'] = 'All'
                    if(sSubmittedPlateFormatOptId != null) {
                        tubesMap['1']['sbm_plate_format_opt_id'] = sSubmittedPlateFormatOptId
                    }
                    if(sSBMCellsPerPoolOptId != null) {
                        tubesMap['1']['sbm_cells_per_library_pool_opt_id'] = sSBMCellsPerPoolOptId
                    }
                    if(sSubmCustomer != null) {
                        tubesMap['1']['customer'] = sSubmCustomer
                    }
                    if(sSubmDueDate != null) {
                        tubesMap['1']['due_date'] = sSubmDueDate
                    }
                    if(sIqcOutcomeOptId != null) {
                        tubesMap['1']['iqc_outcome_opt_id'] = sIqcOutcomeOptId
                    }
                    if(sIqcFeedbackOptId != null) {
                        tubesMap['1']['iqc_feedback_opt_id'] = sIqcFeedbackOptId
                    }
                    if(sQntAvgSampleConc != null) {
                        tubesMap['1']['qnt_avg_sample_conc'] = sQntAvgSampleConc
                    }

                }

            }
            if (parentContOfECH.getIssueType().getName() == IssueTypeName.PLATE_CMB.toString()) {

                // determine the parents of the CMB plate
                processParentsOfCMB(parentContOfECH, tubesMap, LIBPlateBarcode, sQntAvgSampleConc)
            }
        }

    }

    /**
     * Identify the parent SS2 or DNA plates for the CMB plate, and determine how many tubes are required
     * for pooling.
     *
     * @param CMBPlateIssue
     * @param tubesMap
     * @param LIBPlateBarcode
     */
    private static void processParentsOfCMB(Issue CMBPlateIssue, Map<String, Object> tubesMap, String LIBPlateBarcode, String sQntAvgSampleConc) {

        tubesMap['number_of_tubes'] = 0

        LOG.debug "Attempting to fetch the parent SS2 or DNA plate of the CMB plate"
        List<Issue> parentCntrsOfCMB = WorkflowUtils.getParentContainersForContainerId(CMBPlateIssue.getId())

        if(parentCntrsOfCMB.size() <= 0) {
            LOG.error "Expected to find parent plate issues linked to the CMB plate but none found, cannot continue"
            return
        }

        LOG.debug "Found <${parentCntrsOfCMB.size()}> parents for the CMB plate, processing each:"

        parentCntrsOfCMB.each { Issue parentContOfCMB ->

            LOG.debug "Source plate of the ECH plate:"
            LOG.debug "Issue type = ${parentContOfCMB.getIssueType().getName()}"
            String parentContOfECHStatus = parentContOfCMB.getStatus().getName()
            LOG.debug "Status = ${parentContOfECHStatus}"
            LOG.debug "Id = ${parentContOfCMB.getId().toString()}"
            LOG.debug "Key = ${parentContOfCMB.getKey()}"
            LOG.debug "Quadrant = ${JiraAPIWrapper.getCFValueByName(parentContOfCMB, ConfigReader.getCFName("COMBINE_QUADRANT"))}"

            if (parentContOfCMB.getIssueType().getName() == IssueTypeName.PLATE_SS2.toString() || parentContOfCMB.getIssueType().getName() == IssueTypeName.PLATE_DNA.toString()) {

                tubesMap['number_of_tubes'] += 1

                // get cells per pool which should be 96 or 384
                String sSBMCellsPerPool = JiraAPIWrapper.getCFValueByName(parentContOfCMB, ConfigReader.getCFName("CELLS_PER_LIBRARY_POOL"))
                String sSBMCellsPerPoolOptId = getSBMCellsPerPoolOptionId(sSBMCellsPerPool)
                LOG.debug "SBM Cells per Library Pool = ${sSBMCellsPerPool}"

                // get plate format which should be 96
                String sSubmittedPlateFormatOptId = getSBMPlateFormatOptionId(parentContOfCMB)

                // get Combine quadrant which should be 1-4
                String sCombineQuadrant = Double.valueOf(JiraAPIWrapper.getCFValueByName(parentContOfCMB, ConfigReader.getCFName("COMBINE_QUADRANT"))).toInteger()
                LOG.debug "Combine quadrant = ${sCombineQuadrant}"

                String submCustomer
                String sIqcOutcomeOptId
                String sIqcFeedbackOptId
                String submDueDate

                // get fields from linked Submission issue
                Issue submIssue = WorkflowUtils.getSubmissionIssueForContainerId(parentContOfCMB.getId())
                if(submIssue != null) {
                    submCustomer = JiraAPIWrapper.getCFValueByName(submIssue, ConfigReader.getCFName("CUSTOMER"))
                    LOG.debug "Customer = ${submCustomer}"
                    submDueDate  = submIssue.getDueDate().format('d/MMM/yy')
                    LOG.debug "Due Date = ${submDueDate}"
                }

                // get fields from linked IQC issue
                Issue iqcIssue = WorkflowUtils.getIQCIssueForContainerId(parentContOfCMB.getId())
                if(iqcIssue != null) {
                    sIqcOutcomeOptId = getIQCOutcomeOptionId(iqcIssue)
                    sIqcFeedbackOptId = getIQCFeedbackOptionId(iqcIssue)
                }

                // add tube to tubeMap:
                tubesMap[sCombineQuadrant] = [:]
                tubesMap[sCombineQuadrant]['parent_barcode'] = LIBPlateBarcode
                tubesMap[sCombineQuadrant]['block_row'] = getRowLtr((Double.valueOf(sCombineQuadrant)).toInteger()) // 'A' to 'D'
                tubesMap[sCombineQuadrant]['pooled_from_quadrant'] = sCombineQuadrant
                if(sSubmittedPlateFormatOptId != null) {
                    tubesMap[sCombineQuadrant]['sbm_plate_format_opt_id'] = sSubmittedPlateFormatOptId
                }
                if(sSBMCellsPerPoolOptId != null) {
                    tubesMap[sCombineQuadrant]['sbm_cells_per_library_pool_opt_id'] = sSBMCellsPerPoolOptId
                }
                if(submCustomer != null) {
                    tubesMap[sCombineQuadrant]['customer'] = submCustomer
                }
                if(submDueDate != null) {
                    tubesMap[sCombineQuadrant]['due_date'] = submDueDate
                }
                if(sIqcOutcomeOptId != null) {
                    tubesMap[sCombineQuadrant]['iqc_outcome_opt_id'] = sIqcOutcomeOptId
                }
                if(sIqcFeedbackOptId != null) {
                    tubesMap[sCombineQuadrant]['iqc_feedback_opt_id'] = sIqcFeedbackOptId
                }
                if(sQntAvgSampleConc != null) {
                    tubesMap[sCombineQuadrant]['qnt_avg_sample_conc'] = sQntAvgSampleConc
                }
            }
        }
    }

    /**
     * Helper method to fetch source LIB plate barcodes
     *
     * @param LPOIssue
     * @return map of barcodes keys with position values
     */
    private static Map<String, String> getLIBPlateBarcodesMap(Issue LPOIssue) {

        // fetch common fields from Library Pooling issue
        int numberOfPlates = Double.valueOf(JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("NUMBER_OF_PLATES"))).intValue()
        LOG.debug "Number of plates = ${numberOfPlates}"

        Map<String, String> barcodesMap = [:]

        if (numberOfPlates > 0) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("SOURCE_1_BARCODE")),
                    "1"
            )
        }
        if (numberOfPlates > 1) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("SOURCE_2_BARCODE")),
                    "2"
            )
        }
        if (numberOfPlates > 2) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("SOURCE_3_BARCODE")),
                    "3"
            )
        }
        if (numberOfPlates > 3) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("SOURCE_4_BARCODE")),
                    "4"
            )
        }
        if (numberOfPlates > 4) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("SOURCE_5_BARCODE")),
                    "5"
            )
        }
        if (numberOfPlates > 5) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("SOURCE_6_BARCODE")),
                    "6"
            )
        }

        LOG.debug "Barcodes map size = ${barcodesMap.size()}"
        LOG.debug barcodesMap.toMapString()
        barcodesMap
    }

    /**
     * Creates the wiki markup for use in a custom field in the Library Pooling issue.
     * This creates a table so that the user can see how the tubes should be laid out in the Hamilton tube block.
     *
     * @param blockMap
     * @return
     */
    private static StringBuffer createBlockLayoutWikiMarkup(Map blockMap) {

        LOG.debug "Creating wiki markup for block map:"
        LOG.debug blockMap.toMapString()

        // line 1 - block table headers for six columns of 0 to 4 tubes each
        def wikiSB = '{html}<div style="text-align: center;">{html}'<<''
        wikiSB <<= System.getProperty("line.separator")
        wikiSB <<= '|| '
        (1..6).each { int iPltIndx ->
            String sPltIndx = Integer.toString(iPltIndx)
            System.out.println("header col = ${iPltIndx}")
            if(blockMap.containsKey(sPltIndx)) {
                // NB. the same 'parent_barcode' is present in all tubes in each plate index, so we can just take the 1st for this header
                String sParentBC = blockMap[sPltIndx]['1']['parent_barcode']
                LOG.debug "sParentBC = <${sParentBC}>"
                String[] splitParentBC = sParentBC.split('\\.')
                LOG.debug "splitParentBC = ${splitParentBC.toString()}"
                if(3 == splitParentBC.size()) {
                    wikiSB <<= '||Column ' + sPltIndx + '\\\\ ' + '(Source ' + sPltIndx + ')\\\\ ' + splitParentBC[0] + '.' + splitParentBC[1] + '. \\\\ *' + splitParentBC[02] + '*'
                } else {
                    LOG.warn "splitParentBC unexpected size <${splitParentBC.size()}>"
                    wikiSB <<= '||Column ' + sPltIndx + '\\\\ ' + '(Source ' + sPltIndx + ')\\\\ ' + sParentBC
                }
            } else {
                wikiSB <<= '||Column ' + sPltIndx + '\\\\  \\\\ x '
            }
        }
        wikiSB <<= '||'
        wikiSB <<= System.getProperty("line.separator")

        // lines 2-5 block table rows
        (1..4).each { int iRow ->
            String sRow = Integer.toString(iRow)
            String sQuadTxt
            if(1 == iRow) {
                sQuadTxt = '(Q-1/All)'
            } else {
                sQuadTxt = '(Q-' + sRow + ')'
            }
            wikiSB <<= '||Row ' + getRowLtr(iRow) + '\\\\ ' + sQuadTxt
            (1..6).each { int plateIndx ->
                String sPltIndx = Integer.toString(plateIndx)
                if(blockMap.containsKey(sPltIndx)) {
                    if(blockMap[sPltIndx].containsKey(sRow)) {
                        String sTubeBC = blockMap[sPltIndx][sRow]['barcode']
                        LOG.debug "sTubeBC = <${sTubeBC}>"
                        String[] splitTubeBC = sTubeBC.split('\\.')
                        LOG.debug "splitTubeBC = ${splitTubeBC.toString()}"
                        if(3 == splitTubeBC.size()) {
                            wikiSB <<= '|{html}<span style="color:green;font-weight:bold;font-size:18px;">' + blockMap[sPltIndx][sRow]['block_row'] + blockMap[sPltIndx]['block_column'].toString() + '</span>{html} \\\\ ' + splitTubeBC[0] + '.' + splitTubeBC[1] + '. \\\\ {html}<span style="text-align:center;font-weight:bold;font-size:16px;">' + splitTubeBC[02] + '</span>{html}'
                        } else {
                            LOG.warn "splitTubeBC unexpected size <${splitTubeBC.size()}>"
                            wikiSB <<= '|{html}<span style="color:green;font-weight:bold;font-size:18px;">' + blockMap[sPltIndx][sRow]['block_row'] + blockMap[sPltIndx]['block_column'].toString() + '</span>{html} \\\\ ' + sTubeBC
                        }
                    } else {
                        wikiSB <<= '| \\\\ x'
                    }
                } else {
                    wikiSB <<= '| \\\\ x'
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
     * @param row
     * @return 'A' to 'D'
     */
    private static String getRowLtr(int row) {

        String rowLtr = ""
        if(1 == row) {
            rowLtr = 'A'
        }
        if(2 == row) {
            rowLtr = 'B'
        }
        if(3 == row) {
            rowLtr = 'C'
        }
        if(4 == row) {
            rowLtr = 'D'
        }
        rowLtr
    }

    /**
     * Generate a number of barcodes for the tubes
     *
     * @param numBarcodesReqd
     * @return
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
            barcodesList.add(DummyBarcodeGenerator.generateBarcode(BarcodePrefixes.PRFX_SCGC.toString(), BarcodeInfos.INFO_LPL.toString(), 1))
            Thread.sleep(1000) // to prevent following dummy barcodes being same
        } else {
            barcodesList.addAll(DummyBarcodeGenerator.generateBarcodeBatch(BarcodePrefixes.PRFX_SCGC.toString(), BarcodeInfos.INFO_LPL.toString(), numBarcodesReqd))
            // reverse the barcodes so they are used in correct order later (mutate true to change original list)
            barcodesList.reverse(true)
            Thread.sleep(1000) // to prevent following dummy barcodes being same
        }

        barcodesList
    }

    /**
     * Prints the library pool tubes for all the tubes connected to this issue, in order of source plate and quadrant.
     *
     * @param LPOIssue
     */
    public static void printLibraryPoolingTubeLabels(Issue LPOIssue) {

        // Get the number of plates custom field value
        int iExpectedNumPlates = Double.valueOf(JiraAPIWrapper.getCFValueByName(LPOIssue, ConfigReader.getCFName("NUMBER_OF_PLATES")))
        LOG.debug "Expected number of LIB plates = ${iExpectedNumPlates}"

        // Get the LIB plates linked to this issue
        List<Issue> linkedContainers = WorkflowUtils.getContainersLinkedToGroup(LPOIssue)

        if(linkedContainers.size() <= 0) {
            LOG.error "Expected to find LIB plate issues linked to the Library Pooling but none found, cannot continue"
            return
        }

        Map<String, String> LIBBarcodesMap = getLIBPlateBarcodesMap(LPOIssue)
        Map<String, Issue> LIBIssuesMap = [:]

        linkedContainers.each { Issue linkedContainer ->
            // NB. the plates will not be ordered as we get them back here, as source number 1 to 6
            if (linkedContainer.getIssueType().getName() == IssueTypeName.PLATE_LIB.toString()) {

                LOG.debug "Found linked LIB plate with key <${linkedContainer.getKey()}>"

                // get barcode of LIB plate
                String sLIBPlateBarcode = JiraAPIWrapper.getCFValueByName(linkedContainer, ConfigReader.getCFName("BARCODE"))
                LOG.debug "LIB plate barcode = ${sLIBPlateBarcode}"

                // determine block column from barcode
                if (LIBBarcodesMap.containsKey(sLIBPlateBarcode)) {

                    String sBlockCol = LIBBarcodesMap[sLIBPlateBarcode]
                    LIBIssuesMap[sBlockCol] = linkedContainer

                }
            } else {
                LOG.warn "Unexpected container issue type connected to Library Pooling issue with Id <${LPOIssue.getId()}>:"
                LOG.warn "Id = <${linkedContainer.getId().toString()}>"
                LOG.warn "Key = <${linkedContainer.getKey()}>"
            }
        }

        // we should now have a map of source number (= block column) vs LIB plate issue
        if(LIBIssuesMap.size() != iExpectedNumPlates) {
            LOG.error "ERROR: Number of LIB plates found as links <${LIBIssuesMap.size()}>, is different than expected <${iExpectedNumPlates}>"
        }

        // now have a map of source number vs LIB plate, build up a print labels Map
        List<Map<String, String>> tubeLabelsData = createTubeLabelData(LIBIssuesMap)

        LOG.debug 'Labels Data:'
        tubeLabelsData.each{ Map<String,String> labelData ->
            LOG.debug labelData.toMapString()
        }

        // TODO: run the print job with the choice of tube printer the user selected
        // see WorkflowUtils.printPlateLabels as example, need to make a Tube version
    }

    /**
     * Re-prints the library pool tubes for the selected tubes, in order of source plate and quadrant.
     *
     * @param LPOIssue
     * @param LIBPlateIds
     */
    public static void reprintTubeLabelsForPlateIds(Issue LPOIssue, ArrayList<String> LIBPlateIds) {

        Map<String, String> LIBBarcodesMap = getLIBPlateBarcodesMap(LPOIssue)
        Map<String, Issue> LIBIssuesMap = [:]

        LIBPlateIds.each{ String sLIBPlateID ->

            MutableIssue curSourceMutIssue

            try {
                LOG.debug "Parsing ID ${sLIBPlateID} to Long"
                Long sourcePlateIdLong = Long.parseLong(sLIBPlateID)

                LOG.debug "Fetching source issue for Id ${sLIBPlateID}"

                // fetch the source plate mutable issue
                curSourceMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)

            } catch(NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sLIBPlateID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curSourceMutIssue != null) {

                LOG.debug "Processing source issue with Id ${sLIBPlateID}"

                // fetch current source plate barcode
                String sLIBPlateBarcode = JiraAPIWrapper.getCFValueByName(curSourceMutIssue, ConfigReader.getCFName("BARCODE")).toString()
                LOG.debug "Source barcode =  ${sLIBPlateBarcode}"

                // determine block column from barcode
                if (LIBBarcodesMap.containsKey(sLIBPlateBarcode)) {

                    String sBlockCol = LIBBarcodesMap[sLIBPlateBarcode]
                    LIBIssuesMap[sBlockCol] = curSourceMutIssue

                }

            } else {
                LOG.error "ERROR: No source issue found for id ${sLIBPlateID}"
            }

            // we should now have a map of source number (= block column) vs LIB plate issue
            if(LIBIssuesMap.size() != LIBPlateIds.size()) {
                LOG.error "ERROR: Number of LIB plate issues <${LIBIssuesMap.size()}>, is different than expected <${LIBPlateIds.size()}>"
            }

            // now have a map of source number vs LIB plate, build up a print labels data list
            List<Map<String, String>> tubeLabelsData = createTubeLabelData(LIBIssuesMap)

            LOG.debug 'Labels Data:'
            tubeLabelsData.each{ Map<String,String> labelData ->
                LOG.debug labelData.toMapString()
            }

            // TODO: run the print job with the choice of tube printer the user selected
            // see WorkflowUtils.printPlateLabels as example, need to make a Tube version

        }
    }

    /**
     * Creates the tube label data for a set of library plate issues
     *
     * @param LIBIssuesMap
     * @return
     */
    private static List<Map<String, String>> createTubeLabelData(Map<String, Issue> LIBIssuesMap) {

        // sort the map into reverse order
        def sortedLIBIssuesMap = LIBIssuesMap.sort{ a, b -> b.key <=> a.key }

        // build up a list of labels data maps
        List<Map<String, String>> tubeLabelsData = []

        // get the map keys
        Set<String> LIBIssueKeys = sortedLIBIssuesMap.keySet()

        // for each LIB plate get its linked LPL tubes (reverse order)
        LIBIssueKeys.each{ String sMapKey ->

            String sLIBPlateBarcode = JiraAPIWrapper.getCFValueByName(LIBIssuesMap[sMapKey], ConfigReader.getCFName("BARCODE"))
            LOG.debug "LIB plate barcode = ${sLIBPlateBarcode}"

            LOG.debug "Attempting to fetch the child LPL tubes of the LIB plate"
            List<Issue> childCntrsOfLIB = WorkflowUtils.getChildContainersForContainerId(LIBIssuesMap[sMapKey].getId())

            if(childCntrsOfLIB.size() <= 0) {
                LOG.error "Expected to find child LPL tube issues linked to the LIB plate but none found, cannot continue"
                return
            }

            LOG.debug "Found <${childCntrsOfLIB.size()}> child container issues for the LIB plate, processing each:"

            Map<String, Map<String,String>> tubesMap = [:]

            childCntrsOfLIB.each { Issue childContainerOfLIB ->

                if(childContainerOfLIB.getIssueType().getName() == IssueTypeName.TUBE_LPL.toString()) {

                    LOG.debug "Child LPL of the LIB plate:"
                    LOG.debug "Issue type = ${childContainerOfLIB.getIssueType().getName()}"
                    String childContOfLIBStatus = childContainerOfLIB.getStatus().getName()
                    LOG.debug "Status = ${childContOfLIBStatus}"
                    LOG.debug "Id = ${childContainerOfLIB.getId().toString()}"
                    LOG.debug "Key = ${childContainerOfLIB.getKey()}"

                    // check status of LPL - must be state 'in pooling' (could be a tube from a previous cycle of pooling)
                    if(childContOfLIBStatus == IssueStatusName.TUBLPL_IN_POOLING.toString()) {

                        String sBarcode = JiraAPIWrapper.getCFValueByName(childContainerOfLIB, ConfigReader.getCFName("BARCODE"))
                        LOG.debug "Barcode = ${sBarcode}"

                        String sPoolingBlockRow = JiraAPIWrapper.getCFValueByName(childContainerOfLIB, ConfigReader.getCFName("POOLING_BLOCK_ROW"))
                        LOG.debug "Pooling block row = ${sPoolingBlockRow}"

                        String sPoolingBlockColumn = JiraAPIWrapper.getCFValueByName(childContainerOfLIB, ConfigReader.getCFName("POOLING_BLOCK_COLUMN"))
                        LOG.debug "Pooling block column = ${sPoolingBlockColumn}"

                        String sPooledFromQuadrant = JiraAPIWrapper.getCFValueByName(childContainerOfLIB, ConfigReader.getCFName("POOLED_FROM_QUADRANT"))
                        LOG.debug "Pooled from Quadrant = ${sPooledFromQuadrant}" // 1-4 or All

                        Map<String, String> labelData = [:]
                        labelData['barcode'] = sBarcode
                        labelData['parent_barcode'] = sLIBPlateBarcode
                        String[] splitLPLBC = sBarcode.split('\\.')
                        if (3 == splitLPLBC.size()) {
                            labelData['barcode_info'] = splitLPLBC[1]
                            labelData['barcode_number'] = splitLPLBC[2]
                        } else {
                            labelData['barcode_info'] = BarcodeInfos.INFO_LPL.toString()
                            labelData['barcode_number'] = 'unknown'
                        }
                        labelData['pooling_block_row'] = sPoolingBlockRow
                        labelData['pooling_block_column'] = sPoolingBlockColumn
                        labelData['pooled_from_quadrant'] = sPooledFromQuadrant

                        tubesMap[sPoolingBlockRow] = labelData
                    }
                }
            }

            // add tubeData to main labelData
            if(tubesMap.size() > 0) {
                (tubesMap.size()..1).each { int iTubeIndx ->
                    tubeLabelsData.add(tubesMap[getRowLtr(iTubeIndx)])
                }
            }
        }

        tubeLabelsData

    }

    /**
     * For plates that were pooled OK transition the source plates to Done and the tubes
     * to ready for BioAnalyzer QC.
     *
     * @param LPOIssue
     * @param LIBPlateIds
     */
    public static void platesOkInPooling(Issue LPOIssue, ArrayList<String> LIBPlateIds) {

        LOG.debug "In method to process LIB plates completed pooling successfully"

        if(LPOIssue == null) {
            LOG.error "LPOIssue null, cannot continue"
            return
        }

        if(LIBPlateIds == null || 0 == LIBPlateIds.size()) {
            LOG.error "LIBPlateIds null or empty, cannot continue"
            return
        }

        // transition action id to complete the LIB plates
        int transitionLIBId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_LIB.toString(), TransitionName.LIB_LIBRARY_POOLING_COMPLETED.toString()
        )
        if(transitionLIBId <= 0) {
            LOG.error "ERROR: Transition id for LIB plate not found, cannot continue"
            return
        }

        // transition action id to make ready the LPL tubes
        int transitionLPLId = ConfigReader.getTransitionActionId(
                WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_READY_FOR_BA_QC.toString()
        )
        if(transitionLPLId <= 0) {
            LOG.error "ERROR: Transition id for LPL tube not found, cannot continue"
            return
        }

        LIBPlateIds.each { String sLIBPlateID ->

            MutableIssue curLIBPlateMutIssue

            try {
                LOG.debug "Parsing ID ${sLIBPlateID} to Long"
                Long sourcePlateIdLong = Long.parseLong(sLIBPlateID)

                LOG.debug "Fetching source issue for Id ${sLIBPlateID}"

                // fetch the source plate mutable issue
                curLIBPlateMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sLIBPlateID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curLIBPlateMutIssue != null) {

                //get destination LPL tube issues in state 'TubLPL In Progress ' via linked LIB plate issue
                LOG.debug "Attempting to fetch the child LPL tubes of the LIB plate"
                List<Issue> childCntrsOfLIB = WorkflowUtils.getChildContainersForContainerId(curLIBPlateMutIssue.getId())

                if (childCntrsOfLIB.size() <= 0) {
                    LOG.error "Expected to find child LPL tube issues linked to the LIB plate but none found, cannot continue"
                } else {
                    LOG.debug "Found <${childCntrsOfLIB.size()}> child container issues for the LIB plate, processing each:"

                    childCntrsOfLIB.each { Issue childContainerOfLIB ->

                        if (childContainerOfLIB.getIssueType().getName() == IssueTypeName.TUBE_LPL.toString()) {

                            LOG.debug "Child LPL of the LIB plate:"
                            LOG.debug "Issue type = ${childContainerOfLIB.getIssueType().getName()}"
                            String childContOfLIBStatus = childContainerOfLIB.getStatus().getName()
                            LOG.debug "Status = ${childContOfLIBStatus}"
                            LOG.debug "Id = ${childContainerOfLIB.getId().toString()}"
                            LOG.debug "Key = ${childContainerOfLIB.getKey()}"

                            // check status of LPL - must be state 'in pooling' (could be a tube from a previous cycle of pooling)
                            if(childContOfLIBStatus == IssueStatusName.TUBLPL_IN_POOLING.toString()) {
                                //transition destination LPL tubes via 'Ready for BA QC' to 'TubLPL Rdy for BA QC'
                                LOG.debug "Attempting to transition LPL tube to 'Ready for BA QC'"
                                WorkflowUtils.transitionIssue(childContainerOfLIB.getId(), transitionLPLId, "Automatically transitioned during Library Pooling Ok process")
                            }
                        }
                    }
                }

                //transition source LIB via 'Library pooling completed' to 'PltLIB Done'
                LOG.debug "Attempting to transition LIB plate to 'Done' (resolution = Completed)"
                WorkflowUtils.transitionIssue(curLIBPlateMutIssue.getId(), transitionLIBId, "Automatically transitioned during Library Pooling Ok process")

            }
        }
    }

    /**
     * Processes LIB plates that need to be re-run from their ECH plate parents.
     * We fail the LIB plates and their pooled LPL tubes.
     *
     * @param LPOIssue
     * @param LIBPlateIds
     */
    public static void platesForReRunningFromECH(Issue LPOIssue, ArrayList<String> LIBPlateIds) {

        LOG.debug "In method to process LIB plates that need to be re-run from their ECH parents"

        if(LPOIssue == null) {
            LOG.error "LPOIssue null, cannot continue"
            return
        }

        if(LIBPlateIds == null || 0 == LIBPlateIds.size()) {
            LOG.error "LIBPlateIds null or empty, cannot continue"
            return
        }

        // transition action id to fail the LIB plate
        int transitionLIBId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_LIB.toString(), TransitionName.LIB_FAIL_IN_LIBRARY_POOLING.toString()
        )
        if(transitionLIBId <= 0) {
            LOG.error "ERROR: Transition id for LIB plate not found, cannot continue"
            return
        }

        // transition action id to fail the LPL tube
        int transitionLPLId = ConfigReader.getTransitionActionId(
                WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_FAIL_IN_LIBRARY_POOLING.toString()
        )
        if(transitionLPLId <= 0) {
            LOG.error "ERROR: Transition id for LPL tube not found, cannot continue"
            return
        }

        // transition action id to revert the ECH plate
        int transitionECHId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_ECH.toString(), TransitionName.ECH_REVERT_TO_READY_FOR_LIBRARY_PREP_AFTER_POOLING.toString()
        )
        if(transitionECHId <= 0) {
            LOG.error "ERROR: Transition id for ECH plate not found, cannot continue"
            return
        }

        // for each selected LIB plate
        LIBPlateIds.each { String sLIBPlateID ->

            MutableIssue curLIBPlateMutIssue

            try {
                LOG.debug "Parsing ID ${sLIBPlateID} to Long"
                Long sourcePlateIdLong = Long.parseLong(sLIBPlateID)

                LOG.debug "Fetching source issue for Id ${sLIBPlateID}"

                // fetch the source plate mutable issue
                curLIBPlateMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sLIBPlateID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curLIBPlateMutIssue != null) {

                //get destination LPL tube issues in state 'TubLPL In Progress ' via linked LIB plate issue
                LOG.debug "Attempting to fetch the child LPL tubes of the LIB plate"
                List<Issue> childCntrsOfLIB = WorkflowUtils.getChildContainersForContainerId(curLIBPlateMutIssue.getId())

                if (childCntrsOfLIB.size() <= 0) {
                    LOG.error "Expected to find child LPL tube issues linked to the LIB plate but none found, cannot continue"
                } else {
                    LOG.debug "Found <${childCntrsOfLIB.size()}> child container issues for the LIB plate, processing each:"

                    childCntrsOfLIB.each { Issue childContainerOfLIB ->

                        if (childContainerOfLIB.getIssueType().getName() == IssueTypeName.TUBE_LPL.toString()) {

                            LOG.debug "Child LPL of the LIB plate:"
                            LOG.debug "Issue type = ${childContainerOfLIB.getIssueType().getName()}"
                            String childContOfLIBStatus = childContainerOfLIB.getStatus().getName()
                            LOG.debug "Status = ${childContOfLIBStatus}"
                            LOG.debug "Id = ${childContainerOfLIB.getId().toString()}"
                            LOG.debug "Key = ${childContainerOfLIB.getKey()}"

                            // check status of LPL - must be state 'in pooling' (could be a tube from a previous cycle of pooling)
                            if(childContOfLIBStatus == IssueStatusName.TUBLPL_IN_POOLING.toString()) {
                                //transition destination LPL tubes via 'Fail in library pooling' to 'TubLPL Failed'
                                LOG.debug "Attempting to transition LPL tube to 'Failed in library pooling' (resolution = Failed in library pooling)"
                                WorkflowUtils.transitionIssue(childContainerOfLIB.getId(), transitionLPLId, "Automatically transitioned during Library Pooling Re-run from ECH process")
                            }
                        }
                    }
                }

                //transition source LIB via 'Fail in library pooling' to 'PltLIB Failed'
                LOG.debug "Attempting to transition LIB plate to 'Failed' (resolution = Failed in library pooling)"
                WorkflowUtils.transitionIssue(curLIBPlateMutIssue.getId(), transitionLIBId, "Automatically transitioned during Library Pooling Re-run from ECH process")

                //Find ancestor ECH plate and transition to repeat library prep
                LOG.debug "Attempting to fetch the parent ECH plate of the LIB plate"
                List<Issue> parentCntrsOfLIB = WorkflowUtils.getParentContainersForContainerId(curLIBPlateMutIssue.getId())

                if (parentCntrsOfLIB.size() <= 0) {
                    LOG.error "Expected to find child LPL tube issues linked to the LIB plate but none found, cannot continue"
                } else {
                    LOG.debug "Found <${parentCntrsOfLIB.size()}> child container issues for the LIB plate, processing each:"

                    parentCntrsOfLIB.each { Issue parentContainerOfLIB ->

                        if (parentContainerOfLIB.getIssueType().getName() == IssueTypeName.PLATE_ECH.toString()) {

                            LOG.debug "Parent ECH of the LIB plate:"
                            LOG.debug "Issue type = ${parentContainerOfLIB.getIssueType().getName()}"
                            String parentContOfLIBStatus = parentContainerOfLIB.getStatus().getName()
                            LOG.debug "Status = ${parentContOfLIBStatus}"
                            LOG.debug "Id = ${parentContainerOfLIB.getId().toString()}"
                            LOG.debug "Key = ${parentContainerOfLIB.getKey()}"

                            //transition parent ECH via 'Revert to ready for library prep after pooling' to 'PltECH Rdy for Library Prep'
                            LOG.debug "Attempting to transition ECH plate to 'Rdy for Library Prep'"
                            WorkflowUtils.transitionIssue(parentContainerOfLIB.getId(), transitionECHId, "Automatically transitioned during Library Pooling Re-run from ECH process")
                        }
                    }
                }
            }
        }
    }

    /**
     * Process LIB plates that need to have library pooling re-run.
     * Fail the linked LPL tubes and revert the selected LIB plates to ready for Pooling.
     *
     * @param LPOIssue
     * @param LIBPlateIds
     */
    public static void platesForReRunningFromLIB(Issue LPOIssue, ArrayList<String> LIBPlateIds) {

        LOG.debug "In method to process LIB plates that need to be re-run from their ECH parents"

        if (LPOIssue == null) {
            LOG.error "LPOIssue null, cannot continue"
            return
        }

        if (LIBPlateIds == null || 0 == LIBPlateIds.size()) {
            LOG.error "LIBPlateIds null or empty, cannot continue"
            return
        }

        // transition action id to fail the LPL tube
        int transitionLPLId = ConfigReader.getTransitionActionId(
                WorkflowName.TUBE_LPL.toString(), TransitionName.LPL_FAIL_IN_LIBRARY_POOLING.toString()
        )
        if (transitionLPLId <= 0) {
            LOG.error "ERROR: Transition id for LPL tube not found, cannot continue"
            return
        }

        // transition action id to revert the LIB plate
        int transitionLIBId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_LIB.toString(), TransitionName.LIB_REVERT_TO_READY_FOR_POOLING.toString()
        )
        if (transitionLIBId <= 0) {
            LOG.error "ERROR: Transition id for LIB plate not found, cannot continue"
            return
        }

        // for each selected LIB plate
        LIBPlateIds.each { String sLIBPlateID ->

            MutableIssue curLIBPlateMutIssue

            try {
                LOG.debug "Parsing ID ${sLIBPlateID} to Long"
                Long sourcePlateIdLong = Long.parseLong(sLIBPlateID)

                LOG.debug "Fetching source issue for Id ${sLIBPlateID}"

                // fetch the source plate mutable issue
                curLIBPlateMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)

            } catch (NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sLIBPlateID}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curLIBPlateMutIssue != null) {

                //get destination LPL tube issues in state 'TubLPL In Progress ' via linked LIB plate issue
                LOG.debug "Attempting to fetch the child LPL tubes of the LIB plate"
                List<Issue> childCntrsOfLIB = WorkflowUtils.getChildContainersForContainerId(curLIBPlateMutIssue.getId())

                if (childCntrsOfLIB.size() <= 0) {
                    LOG.error "Expected to find child LPL tube issues linked to the LIB plate but none found, cannot continue"
                } else {
                    LOG.debug "Found <${childCntrsOfLIB.size()}> child container issues for the LIB plate, processing each:"

                    childCntrsOfLIB.each { Issue childContainerOfLIB ->

                        if (childContainerOfLIB.getIssueType().getName() == IssueTypeName.TUBE_LPL.toString()) {

                            LOG.debug "Child LPL of the LIB plate:"
                            LOG.debug "Issue type = ${childContainerOfLIB.getIssueType().getName()}"
                            String childContOfLIBStatus = childContainerOfLIB.getStatus().getName()
                            LOG.debug "Status = ${childContOfLIBStatus}"
                            LOG.debug "Id = ${childContainerOfLIB.getId().toString()}"
                            LOG.debug "Key = ${childContainerOfLIB.getKey()}"

                            // check status of LPL - must be state 'in pooling' (could be a tube from a previous cycle of pooling)
                            if(childContOfLIBStatus == IssueStatusName.TUBLPL_IN_POOLING.toString()) {
                                //transition destination LPL tubes via 'Fail in library pooling' to 'TubLPL Failed'
                                LOG.debug "Attempting to transition LPL tube to 'Failed in library pooling' (resolution = Failed in library pooling)"
                                WorkflowUtils.transitionIssue(childContainerOfLIB.getId(), transitionLPLId, "Automatically transitioned during Library Pooling Re-run from ECH process")
                            }
                        }
                    }
                }

                //transition source LIB via 'Fail in library pooling' to 'PltLIB Failed'
                LOG.debug "Attempting to transition LIB plate to 'Failed' (resolution = Failed in library pooling)"
                WorkflowUtils.transitionIssue(curLIBPlateMutIssue.getId(), transitionLIBId, "Automatically transitioned during Library Pooling Re-run from ECH process")

            }
        }
    }
}
