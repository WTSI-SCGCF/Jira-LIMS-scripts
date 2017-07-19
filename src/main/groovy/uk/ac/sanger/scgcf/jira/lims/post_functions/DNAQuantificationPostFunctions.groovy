package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code DNAQuantificationPostFunctions} class holds post functions for the DNA Quantification project
 *
 * Created by as28 on 02/06/2017.
 */

@Slf4j(value = "LOG")
class DNAQuantificationPostFunctions {

    /**
     * Processing for the post function called when failing a Quant Analysis outright
     *
     * @param quantAnalysisIssue
     */
    public static void quantFailedOutrightProcess(Issue quantAnalysisIssue) {

        LOG.debug "In Fail outright process for Quant Analysis:"
        LOG.debug "Id = <${quantAnalysisIssue.getId().toString()}>"
        LOG.debug "Key = <${quantAnalysisIssue.getKey()}>"

        // get the Feedback comments to copy to any plates sent to feedback states
        String feedbackComments = JiraAPIWrapper.getCFValueByName(quantAnalysisIssue, ConfigReader.getCFName("QNT_FEEDBACK_COMMENTS"))
        LOG.debug "QNT Feedback Comments = <${feedbackComments}>"

        LOG.debug "Attempting to fetch the linked ECH plate"
        List<Issue> linkedContainers = WorkflowUtils.getContainersLinkedToGroup(quantAnalysisIssue)

        if(linkedContainers.size() <= 0) {
            LOG.error "Expected to find an ECH plate issue linked to the Quant Analysis but none found, cannot continue"
            return
        }

        linkedContainers.each { Issue curContainerIssue ->

            if (curContainerIssue.getIssueType().getName() == IssueTypeName.PLATE_ECH.toString()) {
                LOG.debug "Found linked ECH plate, processing"
                processECHPlateQuantFailedOutright(curContainerIssue, feedbackComments)
            } else {
                LOG.warn "Unexpected container issue type connected to Quant Analysis issue:"
                LOG.warn "Id = <${curContainerIssue.getId().toString()}>"
                LOG.warn "Key = <${curContainerIssue.getKey()}>"
            }
        }
    }

    /**
     * Process the ECH plate linked to the Quant Analysis
     *
     * @param ECHPlateIssue
     * @return
     */
    private static processECHPlateQuantFailedOutright(Issue ECHPlateIssue, String feedbackComments) {

        LOG.debug "In process ECH plate in fail outright process for Quant Analysis:"
        LOG.debug "Id = <${ECHPlateIssue.getId().toString()}>"
        LOG.debug "Key = <${ECHPlateIssue.getKey()}>"

        LOG.debug "Attempting to fetch the linked parent plate"
        List<Issue> parentContainers = WorkflowUtils.getParentContainersForContainerId(ECHPlateIssue.getId())

        if(parentContainers.size() <= 0) {
            LOG.error "Expected to find a parent plate issue linked to the ECH plate but none found, cannot continue"
            return
        }

        parentContainers.each { Issue curContainerIssue ->

            // process depending on what type of plate it is
            if (curContainerIssue.getIssueType().getName() == IssueTypeName.PLATE_CMB.toString()) {

                LOG.debug "Found ancestor CMB plate"

                // transition ECH to 'Failed in Quant Analysis' via 'Fail in quantification analysis'
                WorkflowUtils.getTransitionActionIdAndTransitionIssue(
                        ECHPlateIssue.getId(),
                        WorkflowName.PLATE_ECH.toString(),
                        TransitionName.ECH_FAIL_IN_QUANTIFICATION_ANALYSIS.toString()
                )

                // check CMB ancestors (the 4 combined source plates)
                processCMBPlateQuantFailedOutright(curContainerIssue, feedbackComments)

            } else if (curContainerIssue.getIssueType().getName() == IssueTypeName.PLATE_SS2.toString()
                    || curContainerIssue.getIssueType().getName() == IssueTypeName.PLATE_DNA.toString()) {

                LOG.debug "Found ancestor SS2 or DNA plate"

                // transition ECH plate to 'PltECH Awaiting QNT Feedback Fail Outright' via 'Fail outright and request quant feedback'
                WorkflowUtils.getTransitionActionIdAndTransitionIssue(
                        ECHPlateIssue.getId(),
                        WorkflowName.PLATE_ECH.toString(),
                        TransitionName.ECH_FAIL_OUTRIGHT_AND_REQUEST_QUANT_FEEDBACK.toString()
                )

                // copy feedback comments into ECH plate
                LOG.debug "Attempting to copy feedback comments into ECH plate"
                JiraAPIWrapper.setCFValueByName(ECHPlateIssue, ConfigReader.getCFName("QNT_FEEDBACK_COMMENTS"), feedbackComments)

            } else {
                LOG.error "Unexpected ancestor plate type <${curContainerIssue.getIssueType().getName()}> linked to the ECH plate, ignoring"
            }

        }

    }

    /**
     * Process the CMB plate ancestor of the ECH plate linked to the Quant Analysis
     *
     * @param CMBPlateIssue
     */
    private static processCMBPlateQuantFailedOutright(Issue CMBPlateIssue, String feedbackComments) {

        LOG.debug "In process CMB plate in fail outright process for Quant Analysis:"
        LOG.debug "Id = <${CMBPlateIssue.getId().toString()}>"
        LOG.debug "Key = <${CMBPlateIssue.getKey()}>"

        LOG.debug "Attempting to fetch the linked parent plates"
        List<Issue> parentContainers = WorkflowUtils.getParentContainersForContainerId(CMBPlateIssue.getId())

        if(parentContainers.size() <= 0) {
            LOG.error "Expected to find parent plate issues linked to the CMB plate but none found, cannot continue"
            return
        }

        LOG.debug "Found <${parentContainers.size()}> sources for the CMB plate, processing each:"

        parentContainers.each { Issue curContainerIssue ->

            LOG.debug "Source plate of the CMB plate:"
            LOG.debug "Issue type = ${curContainerIssue.getIssueType().getName()}"
            String curPltStatus = curContainerIssue.getStatus().getName()
            LOG.debug "Status = ${curPltStatus}"
            LOG.debug "Id = ${curContainerIssue.getId().toString()}"
            LOG.debug "Key = ${curContainerIssue.getKey()}"
            LOG.debug "Quadrant = ${JiraAPIWrapper.getCFValueByName(curContainerIssue, ConfigReader.getCFName("COMBINE_QUADRANT"))}"

            int transActionId = -1

            // process source plates depending on status:
            // if 'PltSS2/DNA Done Empty' transition to 'In QNT Feedback' via 'Fail in Quant' because nothing can be done.
            // if 'PltSS2/DNA Done Not Empty' transition to 'Rdy for Combining' via 'Re-run requested by Quant' to re-run
            // from the source plate
            if (curContainerIssue.getIssueType().getName() == IssueTypeName.PLATE_SS2.toString()) {

                if (curPltStatus.equals(IssueStatusName.PLTSS2_DONE_EMPTY.toString())) {
                    transActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_FAIL_IN_QUANT.toString())

                    // copy QNT feedback comments into SS2 plate
                    LOG.debug "Attempting to copy feedback comments into SS2 plate"
                    JiraAPIWrapper.setCFValueByName(curContainerIssue, ConfigReader.getCFName("QNT_FEEDBACK_COMMENTS"), feedbackComments)

                } else if (curPltStatus.equals(IssueStatusName.PLTSS2_DONE_NOT_EMPTY.toString())) {
                    transActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_SS2.toString(), TransitionName.SS2_RE_RUN_REQUESTED_BY_QUANT.toString())
                }

            } else if (curContainerIssue.getIssueType().getName() == IssueTypeName.PLATE_SS2.toString()) {

                if (curPltStatus.equals(IssueStatusName.PLTDNA_DONE_EMPTY.toString())) {
                    transActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_FAIL_IN_QUANT.toString())

                    // copy QNT feedback comments into DNA plate
                    LOG.debug "Attempting to copy feedback comments into DNA plate"
                    JiraAPIWrapper.setCFValueByName(curContainerIssue, ConfigReader.getCFName("QNT_FEEDBACK_COMMENTS"), feedbackComments)

                } else if (curPltStatus.equals(IssueStatusName.PLTDNA_DONE_NOT_EMPTY.toString())) {
                    transActionId = ConfigReader.getTransitionActionId(
                            WorkflowName.PLATE_DNA.toString(), TransitionName.DNA_RE_RUN_REQUESTED_BY_QUANT.toString())
                }

            }

            if (transActionId > 0) {
                LOG.debug "Attempting to transition the CMB source plate issue"
                WorkflowUtils.transitionIssue(curContainerIssue.getId(), transActionId, "Automatically transitioned by script after DNA Quantification failed")
            } else {
                LOG.error "ERROR: Transition action id not found, cannot transition CMB source plate issue:"
                LOG.error "ERROR: Issue type = ${curContainerIssue.getIssueType().getName()}"
                LOG.error "ERROR: Status = ${curContainerIssue.getStatus().getName()}"
                LOG.error "ERROR: Id = ${curContainerIssue.getId().toString()}"
                LOG.error "ERROR: Key = ${curContainerIssue.getKey()}"
            }
        }
    }

}
