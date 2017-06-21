package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.component.ComponentAccessor
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
 * The {@code LibraryPrepPostFunctions} class holds post functions for the Normalisation and Nextera project
 *
 * Created by as28 on 16/06/2017.
 */

@Slf4j(value = "LOG")
class LibraryPrepPostFunctions {

    /**
     * Processes plates that have completed Normalisation and Nextera successfully.
     *
     * @param libPrepIssue
     * @param arraySourcePlateIds
     */
    public static void processPlatesReadyForPooling(Issue libPrepIssue, ArrayList<String> arraySourcePlateIds) {

        LOG.debug "Processing plates for ready for pooling"

        Map<String, String> barcodesMap = fetchLibraryPrepBarcodes(libPrepIssue)

        // identify transaction id (NB. depends on plate type)
        int sourceActionId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_ECH.toString(), TransitionName.ECH_LIBRARY_PREP_COMPLETED.toString())

        // get action id of transition of destination LIB via 'Ready for library pooling' to 'Rdy for Pooling'
        int destActionId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_LIB.toString(), TransitionName.LIB_READY_FOR_LIBRARY_POOLING.toString())

        LOG.debug "Source action id = ${sourceActionId}"
        LOG.debug "Destination action id = ${destActionId}"

        if(sourceActionId <= 0) {
            LOG.error "ERROR: Source action id not found, cannot process source ECH plates to done"
            return
        }

        if(destActionId <= 0) {
            LOG.error "ERROR: Destination action id not found, cannot process destination LIB plates to ready for pooling"
            return
        }

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

                    LOG.debug "Attempting to transition source to 'Done' (resolution 'Completed')"
                    WorkflowUtils.transitionIssue(curSourceMutIssue.getId(), sourceActionId)

                    LOG.debug "Attempting to transition destination to 'Rdy for Pooling'"
                    WorkflowUtils.transitionIssue(curDestMutIssue.getId(), destActionId)

                    // link the source plate to the destination issue via 'is a parent of' linking
                    LOG.debug "Attempting to create an issue link between the source and destination plates"
                    WorkflowUtils.createIssueLink(curSourceMutIssue.getId(), curDestMutIssue.getId(), IssueLinkTypeName.RELATIONSHIPS.getLinkTypeName())

                } else {
                    LOG.error "ERROR: No destination barcode for source barcode ${curSourceBarcode}"
                }

            } else {
                LOG.error "ERROR: No source issue found for id ${sourcePlateId.toString()}"
            }
        }
    }

    /**
     * Processes plates that require re-running after Normalisation and Nextera library prep.
     *
     * @param libPrepIssue
     * @param arraySourcePlateIds
     */
    public static void processPlatesForReRunning(Issue libPrepIssue, ArrayList<String> arraySourcePlateIds) {

        LOG.debug "Processing plates for re-running library prep"

        Map<String, String> barcodesMap = fetchLibraryPrepBarcodes(libPrepIssue)

        // get source transaction id
        int sourceActionId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_ECH.toString(), TransitionName.ECH_REVERT_TO_READY_FOR_LIBRARY_PREP.toString())

        // get action id of transition of destination LIB via 'Fail in library prep' to 'Failed'
        int destActionId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_LIB.toString(), TransitionName.LIB_FAIL_IN_LIBRARY_PREP.toString())

        LOG.debug "Source action id = ${sourceActionId}"
        LOG.debug "Destination action id = ${destActionId}"

        if(sourceActionId <= 0) {
            LOG.error "ERROR: Source action id not found, cannot revert source ECH plates to ready for library prep"
            return
        }

        if(destActionId <= 0) {
            LOG.error "ERROR: Destination action id not found, cannot process destination LIB plates to failed"
            return
        }

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

                    LOG.debug "Attempting to transition source to 'Rdy for Library Prep'"
                    WorkflowUtils.transitionIssue(curSourceMutIssue.getId(), sourceActionId)

                    LOG.debug "Attempting to transition destination to 'Failed' (resolution 'Failed in Library Prep)"
                    WorkflowUtils.transitionIssue(curDestMutIssue.getId(), destActionId)

                    // de-link the source plate from this issue
                    LOG.debug "Attempting to de-link the source ECH plate"
                    WorkflowUtils.removeIssueLink(libPrepIssue.getId(), curSourceMutIssue.getId(), IssueLinkTypeName.GROUP_INCLUDES.getLinkTypeName())

                } else {
                    LOG.error "ERROR: No destination barcode for source barcode ${curSourceBarcode}"
                }

            } else {
                LOG.error "ERROR: No source issue found for id ${sourcePlateId.toString()}"
            }
        }
    }

    /**
     * Processes plates that fail after Normalisation and Nextera library prep.
     *
     * @param libPrepIssue
     * @param arraySourcePlateIds
     */
    public static void processPlatesForFailing(Issue libPrepIssue, ArrayList<String> arraySourcePlateIds) {

        LOG.debug "Processing plates failing after library prep"

        //  get action id for transition of destination LIB via 'Fail in library prep' to 'Failed'
        int destActionId = ConfigReader.getTransitionActionId(
                WorkflowName.PLATE_LIB.toString(), TransitionName.LIB_FAIL_IN_LIBRARY_PREP.toString())

        if (destActionId <= 0) {
            LOG.error "Destination action id not found, cannot process plates to failed in library prep"
            return
        }

        def sLibraryPrepFeedbackComments = JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("NXT_FEEDBACK_COMMENTS"))

        Map<String, String> barcodesMap = fetchLibraryPrepBarcodes(libPrepIssue)

        // process each source plate id in the list
        arraySourcePlateIds.each { String sSourcePlateId ->

            MutableIssue curSourceMutIssue

            Long sourcePlateIdLong = 0L

            try {
                LOG.debug "Parsing ID ${sSourcePlateId} to Long"
                sourcePlateIdLong = Long.parseLong(sSourcePlateId)

                LOG.debug "Fetching source issue for Id ${sSourcePlateId}"

                // fetch the source plate mutable issue
                curSourceMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)

            } catch(NumberFormatException e) {
                LOG.error "Failed to parse Id to Long for input Id ${sSourcePlateId}"
                LOG.error e.getMessage()
            }

            // check for unable to identify issue from id
            if (curSourceMutIssue != null) {
                LOG.debug "Processing source issue with ID ${sSourcePlateId}"

                // fetch current source plate barcode
                String curSourceBarcode = JiraAPIWrapper.getCFValueByName(curSourceMutIssue, ConfigReader.getCFName("BARCODE")).toString()
                LOG.debug "Source barcode =  ${curSourceBarcode}"

                // get destination plate issue
                String curDestBarcode = barcodesMap[curSourceBarcode]
                if (curDestBarcode?.trim()) {
                    LOG.debug "Destination barcode = ${curDestBarcode}"

                    Issue curDestIssue = WorkflowUtils.getIssueForBarcode(curDestBarcode)
                    MutableIssue curDestMutIssue = WorkflowUtils.getMutableIssueForIssueId(curDestIssue.getId())

                    String curSourceIssueTypeName = curSourceMutIssue.getIssueType().getName()
                    LOG.debug "Source issue type = ${curSourceIssueTypeName}"

                    // get the ancestor plate of the source ECH plate and process depending on type
                    // it should be either a 384-well SS2/DNA plate or a 384-well CMB plate
                    List<IssueLink> inwardLinksList = WorkflowUtils.getInwardLinksListForIssueId(curSourceMutIssue.getId())
                    LOG.debug "Number of inward links from ECH plate = ${inwardLinksList.size()}"

                    // get the issue link type
                    IssueLinkType plateLinkType = WorkflowUtils.getIssueLinkType(IssueLinkTypeName.RELATIONSHIPS.toString())

                    // for each issue linked to the ECH plate (NB. not just plate links)
                    inwardLinksList.each { IssueLink issLink ->

                        LOG.debug "Issue link type name = ${issLink.issueLinkType.getName()}"

                        Issue curAncestorIssue = issLink.getSourceObject()

                        LOG.debug "Found linked issue with Key = ${curAncestorIssue.getKey()}"

                        // identify transaction id (NB. depends on plate type)
                        int sourceActionId = -1

                        // only want source plates (not Study or child plates etc.)
                        if(issLink.getIssueLinkType() == plateLinkType) {

                            LOG.debug "Linked issue is a plate"

                            if ((curAncestorIssue.getIssueType().name == IssueTypeName.PLATE_SS2.toString())
                                    || (curAncestorIssue.getIssueType().name == IssueTypeName.PLATE_DNA.toString())) {

                                LOG.debug "Source of ECH plate is an SS2/DNA plate"

                                // SS2/DNA so transition source ECH via 'fail and request library prep feedback' to 'In NXT Feedback' and copy NXT Feedback Comments to ECH
                                sourceActionId = ConfigReader.getTransitionActionId(
                                        WorkflowName.PLATE_ECH.toString(), TransitionName.ECH_FAIL_AND_REQUEST_LIBRARY_PREP_FEEDBACK.toString())

                                if (sourceActionId > 0) {
                                    // transition source ECH plate issue 'In Library Prep Feedback'
                                    WorkflowUtils.transitionIssue(curSourceMutIssue.getId(), sourceActionId)

                                    // set Library prep feedback on source ECH plate issue (re-fetch issue first)
                                    curSourceMutIssue = WorkflowUtils.getMutableIssueForIssueId(sourcePlateIdLong)
                                    JiraAPIWrapper.setCustomFieldValueByName(curSourceMutIssue, ConfigReader.getCFName("NXT_FEEDBACK_COMMENTS"), sLibraryPrepFeedbackComments)

                                } else {
                                    LOG.error "Source action id not found, cannot transition ECH source with ID <${sSourcePlateId}> to in Feedback"
                                }

                            } else if (curAncestorIssue.getIssueType().name == IssueTypeName.PLATE_CMB.toString()) {
                                LOG.debug "Source of ECH plate is a CMB plate"

                                // process the source plates of the CMB plate (checking if they may be re-runnable)
                                processCombinePlate(curAncestorIssue, sLibraryPrepFeedbackComments)

                                // process the ECH plate
                                sourceActionId = ConfigReader.getTransitionActionId(
                                        WorkflowName.PLATE_ECH.toString(), TransitionName.ECH_FAIL_IN_LIBRARY_PREP.toString())

                                if (sourceActionId > 0) {
                                    // transition source ECH plate issue 'Failed' (resolution 'Failed in Library Prep')
                                    WorkflowUtils.transitionIssue(curSourceMutIssue.getId(), sourceActionId)

                                } else {
                                    LOG.error "Source action id not found, cannot transition ECH source with ID ${sSourcePlateId}"
                                }
                            }
                        }
                    }

                    // transition destination LIB plate to 'Failed' (resolution 'Failed in Library Prep')
                    WorkflowUtils.transitionIssue(curDestMutIssue.getId(), destActionId)

                } else {
                    LOG.error "No destination barcode for source barcode ${curSourceBarcode}"
                }

            } else {
                LOG.error "No source issue found for id ${sSourcePlateId}"
            }
        }
    }

    /**
     * Process the ancestor plates of a Combine plate
     *
     * @param cmbPlateMutIssue
     */
    private static void processCombinePlate(MutableIssue cmbPlateMutIssue, String sNXTFeedbackComments) {

        LOG.debug "Processing CMB plate with Key = ${cmbPlateMutIssue.getKey()}"

        // get the issue link type
        IssueLinkType plateLinkType = WorkflowUtils.getIssueLinkType(IssueLinkTypeName.RELATIONSHIPS.toString())

        // get the linked plate issues from the Combine Plates issue
        List<IssueLink> inwardLinksList = WorkflowUtils.getInwardLinksListForIssueId(cmbPlateMutIssue.getId())

        def customFieldManager  = ComponentAccessor.getCustomFieldManager()
        def cfBarcode           = customFieldManager.getCustomFieldObject(ConfigReader.getCFId('BARCODE'))
        def cfCombineQuadrant   = customFieldManager.getCustomFieldObject(ConfigReader.getCFId('COMBINE_QUADRANT'))

        // for each linked issue
        inwardLinksList.each { IssueLink issLink ->

            LOG.debug "Issue link type name = ${issLink.issueLinkType.getName()}"

            Issue curAncestorIssue = issLink.getSourceObject()

            LOG.debug "Found inward linked issue with Key = ${curAncestorIssue.getKey()}"

            // only want plates (not Study or other groupings)
            if(issLink.getIssueLinkType() == plateLinkType) {
                if ((curAncestorIssue.getIssueType().name == IssueTypeName.PLATE_SS2.toString())
                        || (curAncestorIssue.getIssueType().name == IssueTypeName.PLATE_DNA.toString())) {

                    LOG.debug "Ancestor issue is SS2 or DNA plate and has link type relationships <${issLink.getIssueLinkType().getName()}>"

                    // for each source plate issue fetch the 'Barcode', 'Combine Quadrant' value (1-4) and status
                    String curBarcode = curAncestorIssue.getCustomFieldValue(cfBarcode)
                    String curCombineQuadrant = curAncestorIssue.getCustomFieldValue(cfCombineQuadrant)
                    String curStatus = curAncestorIssue.getStatus().getName()

                    LOG.debug "CMB Ancestor plate details:"
                    LOG.debug "Quadrant = ${curCombineQuadrant}"
                    LOG.debug "Barcode = ${curBarcode}"
                    LOG.debug "Status = ${curStatus}"

                    // if 'Done Empty' then transition CMB source SS2/DNA plates via 'Fail in library prep' to 'In Library Prep Feedback' (and clear resolution)
                    // if 'Done Not Empty' then transition CMB source SS2/DNA plates via 'Re-run requested by library prep' to 'Rdy for Combining' (and clear resolution)
                    int destActionId = -1
                    if (curStatus.equals(IssueStatusName.PLTSS2_DONE_EMPTY.toString())) {
                        destActionId = ConfigReader.getTransitionActionId(
                                WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_FAIL_IN_LIBRARY_PREP.toString())

                        // set NXT feedback on source plate
                        LOG.debug "Source SS2 plate is Empty. Attempting to set NXT feedback comments on source SS2 plate"
                        JiraAPIWrapper.setCustomFieldValueByName(curAncestorIssue, ConfigReader.getCFName("NXT_FEEDBACK_COMMENTS"), sNXTFeedbackComments)
                    }
                    if (curStatus.equals(IssueStatusName.PLTSS2_DONE_NOT_EMPTY.toString())) {
                        destActionId = ConfigReader.getTransitionActionId(
                                WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_RE_RUN_REQUESTED_BY_LIBRARY_PREP.toString())
                        LOG.debug "Source SS2 plate is NOT Empty"
                    }
                    if (curStatus.equals(IssueStatusName.PLTDNA_DONE_EMPTY.toString())) {
                        destActionId = ConfigReader.getTransitionActionId(
                                WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_FAIL_IN_LIBRARY_PREP.toString())

                        // set NXT feedback on source plate
                        LOG.debug "Source DNA plate is Empty. Attempting to set NXT feedback comments on source DNA plate"
                        JiraAPIWrapper.setCustomFieldValueByName(curAncestorIssue, ConfigReader.getCFName("NXT_FEEDBACK_COMMENTS"), sNXTFeedbackComments)
                    }
                    if (curStatus.equals(IssueStatusName.PLTDNA_DONE_NOT_EMPTY.toString())) {
                        destActionId = ConfigReader.getTransitionActionId(
                                WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_RE_RUN_REQUESTED_BY_LIBRARY_PREP.toString())
                        LOG.debug "Source DNA plate is NOT Empty"
                    }

                    if (destActionId > 0) {
                        LOG.debug "Ancestor transition action Id = ${destActionId}"
                        // transition ancestor plate
                        WorkflowUtils.transitionIssue(curAncestorIssue.getId(), destActionId)
                    } else {
                        LOG.error "Ancestor transition action Id not found, cannot transition ancestor plate with barcode ${curBarcode}"
                    }
                }
            }
        }

        LOG.debug "Combine plate processing completed"

    }

    /**
     * Helper method to fetch source and destination barcodes
     *
     * @param libPrepIssue
     * @return
     */
    private static Map<String, String> fetchLibraryPrepBarcodes(Issue libPrepIssue) {

        LOG.debug "Creating barcodes map for Library Prep"
        Map<String, String> barcodesMap = [:]

        barcodesMap.put(
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("SOURCE_1_BARCODE")),
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("DESTINATION_1_BARCODE"))
        )
        barcodesMap.put(
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("SOURCE_2_BARCODE")),
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("DESTINATION_2_BARCODE"))
        )
        barcodesMap.put(
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("SOURCE_3_BARCODE")),
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("DESTINATION_3_BARCODE"))
        )
        barcodesMap.put(
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("SOURCE_4_BARCODE")),
                JiraAPIWrapper.getCFValueByName(libPrepIssue, ConfigReader.getCFName("DESTINATION_4_BARCODE"))
        )

        LOG.debug "Barcodes map size = ${barcodesMap.size()}"
        LOG.debug barcodesMap.toMapString()
        barcodesMap
    }

}
