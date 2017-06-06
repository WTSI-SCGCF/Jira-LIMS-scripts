package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.exception.CreateException
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkType
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code SPRiPlateCleanupPostFunctions} class holds post functions for the SPRi Plate Cleanup project
 *
 * Created by as28 on 10/05/2017.
 */

@Slf4j(value = "LOG")
class SPRiPlateCleanupPostFunctions {

    /**
     * Processes plates that have completed SPRi successfully.
     *
     * @param spriIssue
     * @param arraySourcePlateIds
     */
    public static void processPlatesSuccessfullyCleaned(Issue spriIssue, ArrayList<String> arraySourcePlateIds) {

        Map<String, String> barcodesMap = fetchSPRiBarcodes(spriIssue)

        // get action id of transition of destination ECH via 'Ready for quantification' to 'Rdy for Quant'
        int destActionId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_ECH.toString(), TransitionName.ECH_READY_FOR_QUANTIFICATION.toString())

        // process each source plate id in the list
        arraySourcePlateIds.each { String sourcePlateId ->

            MutableIssue curSourceMutIssue

            try {
                LOG.debug "Parsing ID ${sourcePlateId} to Long"
                Long sourcePlateIdLong = Long.parseLong(sourcePlateId)

                LOG.debug "Fetching source issue for Id ${sourcePlateId}"

                // fetch the source plate mutable issue
                curSourceMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)

            } catch(NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sourcePlateId}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curSourceMutIssue != null) {
                LOG.debug "Processing source issue with Id ${sourcePlateId}"

                // fetch current source plate barcode
                String curSourceBarcode = JiraAPIWrapper.getCFValueByName(curSourceMutIssue, ConfigReader.getCFName("BARCODE")).toString()
                LOG.debug "Source barcode =  ${curSourceBarcode}"

                // get destination plate issue
                String curDestBarcode = barcodesMap[curSourceBarcode]
                if(curDestBarcode?.trim()) {
                    LOG.debug "Destination barcode = ${curDestBarcode}"

                    Issue curDestIssue = WorkflowUtils.getIssueForBarcode(curDestBarcode)
                    MutableIssue curDestMutIssue = WorkflowUtils.getMutableIssueForIssueId(curDestIssue.getId())

                    String curSourceIssueTypeName = curSourceMutIssue.getIssueType().getName()
                    LOG.debug "Source issue type = ${curSourceIssueTypeName}"

                    // identify transaction id (NB. depends on plate type)
                    int sourceActionId = -1
                    if (curSourceIssueTypeName == IssueTypeName.PLATE_SS2.toString()) {
                        sourceActionId = ConfigReader.getTransitionActionId(
                                WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_COMPLETE_SPRI.toString())
                    }
                    if (curSourceIssueTypeName == IssueTypeName.PLATE_DNA.toString()) {
                        sourceActionId = ConfigReader.getTransitionActionId(
                                WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_COMPLETE_SPRI.toString())
                    }
                    if (curSourceIssueTypeName == IssueTypeName.PLATE_CMB.toString()) {
                        sourceActionId = ConfigReader.getTransitionActionId(
                                WorkflowName.PLATE_CMB.toString(), TransitionName.CMB_COMPLETE_SPRI.toString())
                    }
                    LOG.debug "Source action id = ${sourceActionId}"

                    if(sourceActionId > 0) {
                        LOG.debug "Attempting to transition source to 'Done' (resolution 'Completed SPRi')"
                        WorkflowUtils.transitionIssue(curSourceMutIssue.getId(), sourceActionId)
                    } else {
                        LOG.error "ERROR: Source action id not found, cannot transition source for ID ${sourcePlateId}"
                    }

                    if(destActionId > 0) {
                        LOG.debug "Attempting to transition destination to 'Rdy for Quant'"
                        WorkflowUtils.transitionIssue(curDestMutIssue.getId(), destActionId)
                    } else {
                        LOG.error "ERROR: Destination action id not found, cannot transition destination with barcode ${curDestBarcode}"
                    }

                    // link the source plate to the destination issue via 'is a parent of' linking
                    LOG.debug "Attempting to create an issue link between the source and destination plates"
                    WorkflowUtils.createIssueLink(curSourceMutIssue.getId(), curDestMutIssue.getId(), IssueLinkTypeName.RELATIONSHIPS.getLinkTypeName())

                } else {
                    LOG.error "ERROR: No destination barcode for source barcode ${curSourceBarcode}"
                }

            } else {
                LOG.error "ERROR: No source issue found for id ${sourcePlateId}"
            }
        }
    }

    /**
     * Process a plate that failed the SPRi cleanup
     *
     * @param spriIssue
     * @param sourcePlateId
     */
    public static void processPlateFailedCleanup(Issue spriIssue, String sourcePlateIdStr) {

        def sSPRiFeedbackComments = JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("SPRI_FEEDBACK_COMMENTS"))

        Map<String, String> barcodesMap = fetchSPRiBarcodes(spriIssue)

        Long sourcePlateIdLong = Long.parseLong(sourcePlateIdStr)
        LOG.debug "Fetching source issue for ID ${sourcePlateIdStr}"

        // fetch the source plate mutable issue
        MutableIssue curSourceMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)

        // check for unable to identify issue from id
        if (curSourceMutIssue != null) {
            LOG.debug "Processing source issue with ID ${sourcePlateIdStr}"

            // fetch current source plate barcode
            String curSourceBarcode = JiraAPIWrapper.getCFValueByName(curSourceMutIssue, ConfigReader.getCFName("BARCODE")).toString()
            LOG.debug "Source barcode =  ${curSourceBarcode}"

            // get destination plate issue
            String curDestBarcode = barcodesMap[curSourceBarcode]
            if(curDestBarcode?.trim()) {
                LOG.debug "Destination barcode = ${curDestBarcode}"

                Issue curDestIssue = WorkflowUtils.getIssueForBarcode(curDestBarcode)
                MutableIssue curDestMutIssue = WorkflowUtils.getMutableIssueForIssueId(curDestIssue.getId())

                String curSourceIssueTypeName = curSourceMutIssue.getIssueType().getName()
                LOG.debug "Source issue type = ${curSourceIssueTypeName}"

                // identify transaction id (NB. depends on plate type)
                int sourceActionId = -1
                if (curSourceIssueTypeName == IssueTypeName.PLATE_SS2.toString()) {
                    LOG.debug "Source is an SS2 plate"
                    sourceActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_FAIL_IN_SPRI_384.toString())
                }
                if (curSourceIssueTypeName == IssueTypeName.PLATE_DNA.toString()) {
                    LOG.debug "Source is a DNA plate"
                    sourceActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_FAIL_IN_SPRI_384.toString())
                }
                if (curSourceIssueTypeName == IssueTypeName.PLATE_CMB.toString()) {
                    LOG.debug "Source is a CMB plate"
                    sourceActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_CMB.toString(), TransitionName.CMB_FAIL_IN_SPRI.toString())

                    // process the parent plates of the combine plate (may be re-runnable)
                    processCombinePlate(curSourceMutIssue, sSPRiFeedbackComments)
                }

                LOG.debug "Source action id = ${sourceActionId}"

                if(sourceActionId > 0) {
                    // transition source plate issue
                    // to either 'Failed' (resolution 'Failed in SPRi') for CMB, or to 'In SPRi Feedback' for SS2 or DNA
                    WorkflowUtils.transitionIssue(curSourceMutIssue.getId(), sourceActionId)

                    // set SPRi feedback on source plate issue (re-fetch issue first)
                    curSourceMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)
                    JiraAPIWrapper.setCustomFieldValueByName(curSourceMutIssue, ConfigReader.getCFName("SPRI_FEEDBACK_COMMENTS"), sSPRiFeedbackComments)

                } else {
                    LOG.error "Source action id not found, cannot transition source for ID ${sourcePlateIdStr}"
                }

                //  get action id for transition of destination ECH via 'Fail in SPRi' to 'Failed'
                int destActionId = ConfigReader.getTransitionActionId(
                        WorkflowName.PLATE_ECH.toString(), TransitionName.ECH_FAIL_IN_SPRI.toString())

                if(destActionId > 0) {
                    // transition destination to 'Failed' (resolution 'Failed in SPRi')
                    WorkflowUtils.transitionIssue(curDestMutIssue.getId(), destActionId)
                } else {
                    LOG.error "Destination action id not found, cannot transition destination with barcode ${curDestBarcode}"
                }

            } else {
                LOG.error "No destination barcode for source barcode ${curSourceBarcode}"
            }

        } else {
            LOG.error "No source issue found for id ${sourcePlateIdStr}"
        }

    }

    /**
     * Process the ancestor plates of a Combine plate
     *
     * @param cmbPlateMutIssue
     */
    private static void processCombinePlate(MutableIssue cmbPlateMutIssue, String sSPRiFeedbackComments) {

        LOG.debug "Processing CMB plate with Key = ${cmbPlateMutIssue.getKey()}"

        // get the issue link type
        IssueLinkType plateLinkType = WorkflowUtils.getIssueLinkType(IssueLinkTypeName.RELATIONSHIPS.toString())
        LOG.debug "Plate link type name = ${plateLinkType.getName()}"
        LOG.debug "Plate link type inward = ${plateLinkType.inward}"
        LOG.debug "Plate link type outward = ${plateLinkType.outward}"

        // get the linked plate issues from the Combine Plates issue
        List<IssueLink> outwardLinksList = WorkflowUtils.getOutwardLinksListForIssueId(cmbPlateMutIssue.getId())

        LOG.debug "Number of links from CMB plate to check = ${outwardLinksList.size()}"

        def customFieldManager  = ComponentAccessor.getCustomFieldManager()
        def cfBarcode           = customFieldManager.getCustomFieldObject(ConfigReader.getCFId('BARCODE'))
        def cfCombineQuadrant   = customFieldManager.getCustomFieldObject(ConfigReader.getCFId('COMBINE_QUADRANT'))

        // for each linked plate issue (NB. sources and destinations)
        outwardLinksList.each { IssueLink issLink ->

            LOG.debug "Issue link type name = ${issLink.issueLinkType.getName()}"

            Issue curAncestorIssue = issLink.getDestinationObject()

            LOG.debug "Found outward linked issue with Key = ${curAncestorIssue.getKey()}"

            // only want source plates (not Study or child plates etc.)
            if ((curAncestorIssue.getIssueType().name == IssueTypeName.PLATE_SS2.toString()
                    || curAncestorIssue.getIssueType().name == IssueTypeName.PLATE_DNA.toString())
                    && issLink.getIssueLinkType() == plateLinkType) {

                LOG.debug "Ancestor issue is SS2 or DNA plate and has link type relationships <${issLink.getIssueLinkType().getName()}>"

                // for each source plate issue fetch the 'Barcode', 'Combine Quadrant' value (1-4) and status
                String curBarcode           = curAncestorIssue.getCustomFieldValue(cfBarcode)
                String curCombineQuadrant   = curAncestorIssue.getCustomFieldValue(cfCombineQuadrant)
                String curStatus            = curAncestorIssue.getStatus().getName()

                LOG.debug "CMB Ancestor plate details:"
                LOG.debug "Quadrant = ${curCombineQuadrant}"
                LOG.debug "Barcode = ${curBarcode}"
                LOG.debug "Status = ${curStatus}"

                // if status status 'Done Empty' transition via 'Fail in SPRi' to 'In Feedback' (and clear resolution)
                // if status status 'Done' transition via 'Re-run requested via SPRi' to 'Rdy for Combine' (and clear resolution)
                int destActionId = -1
                if(curStatus.equals(IssueStatusName.PLTSS2_DONE_EMPTY.toString())) {
                    destActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_FAIL_IN_SPRI_96.toString())

                    // set SPRi feedback on source plate
                    LOG.debug "Source SS2 plate is Empty. Attempting to set SPRi comments on source SS2 plate"
                    JiraAPIWrapper.setCustomFieldValueByName(curAncestorIssue, ConfigReader.getCFName("SPRI_FEEDBACK_COMMENTS"), sSPRiFeedbackComments)
                }
                if(curStatus.equals(IssueStatusName.PLTSS2_DONE_NOT_EMPTY.toString())) {
                    destActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_RE_RUN_REQUESTED_BY_SPRI.toString())
                    LOG.debug "Source SS2 plate is NOT Empty"
                }
                if(curStatus.equals(IssueStatusName.PLTDNA_DONE_EMPTY.toString())) {
                    destActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_FAIL_IN_SPRI_96.toString())

                    // set SPRi feedback on source plate
                    LOG.debug "Source DNA plate is Empty. Attempting to set SPRi comments on source DNA plate"
                    JiraAPIWrapper.setCustomFieldValueByName(curAncestorIssue, ConfigReader.getCFName("SPRI_FEEDBACK_COMMENTS"), sSPRiFeedbackComments)
                }
                if(curStatus.equals(IssueStatusName.PLTDNA_DONE_NOT_EMPTY.toString())) {
                    destActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_RE_RUN_REQUESTED_BY_SPRI.toString())
                    LOG.debug "Source DNA plate is NOT Empty"
                }

                if(destActionId > 0) {
                    LOG.debug "Ancestor transition action Id = ${destActionId}"
                    // transition ancestor plate
                    WorkflowUtils.transitionIssue(curAncestorIssue.getId(), destActionId)
                } else {
                    LOG.error "Ancestor transition action Id not found, cannot transition ancestor plate with barcode ${curBarcode}"
                }
            }
        }

        LOG.debug "Combine plate processing completed"

    }

    /**
     * Helper method to fetch source and destination barcodes
     *
     * @param spriIssue
     * @return
     */
    private static Map<String, String> fetchSPRiBarcodes(Issue spriIssue) {

        // fetch common fields from SPRi issue
        int numberOfPlates = Double.valueOf(JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("NUMBER_OF_PLATES"))).intValue()
        LOG.debug "Number of plates = ${numberOfPlates}"

        Map<String, String> barcodesMap = [:]

        if (numberOfPlates > 0) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("SOURCE_1_BARCODE")),
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("DESTINATION_1_BARCODE"))
            )
        }
        if (numberOfPlates > 1) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("SOURCE_2_BARCODE")),
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("DESTINATION_2_BARCODE"))
            )
        }
        if (numberOfPlates > 2) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("SOURCE_3_BARCODE")),
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("DESTINATION_3_BARCODE"))
            )
        }
        if (numberOfPlates > 3) {
            barcodesMap.put(
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("SOURCE_4_BARCODE")),
                    JiraAPIWrapper.getCFValueByName(spriIssue, ConfigReader.getCFName("DESTINATION_4_BARCODE"))
            )
        }

        LOG.debug "Barcodes map size = ${barcodesMap.size()}"
        LOG.debug barcodesMap.toMapString()
        barcodesMap
    }

}
