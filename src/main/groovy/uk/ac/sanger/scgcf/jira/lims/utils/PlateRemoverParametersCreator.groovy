package uk.ac.sanger.scgcf.jira.lims.utils

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.SS2PlateStateName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName

/**
 * This class contains static factory method to create various {@code PlateActionParameterHolder} instances
 * used by {@code PlateRemover} class.
 *
 * Created by ke4 on 03/02/2017.
 */
class PlateRemoverParametersCreator {

    /**
     * Creates a {@code PlateActionParameterHolder} for removing a plate from the Smart-seq2 workflow
     *
     * @param curIssue the specific issue
     * @return PlateActionParameterHolder object holding all the parameters needed for removing a plate from the
     * Smart-seq2 workflow
     */
    public static PlateActionParameterHolder getSmartSeq2Parameters(Issue curIssue) {
        PlateActionParameterHolder plateActionParams = getBasicPlateRemovalParameterHolder(curIssue)
        plateActionParams.plateWorkflowName = WorkflowName.PLATE_SS2
        plateActionParams.currentWorkflowName = WorkflowName.SMART_SEQ2
        plateActionParams.transitionName = TransitionName.REVERT_TO_READY_FOR_SS2
        plateActionParams.previousPlateState = SS2PlateStateName.PLATESS2_IN_SS2
        plateActionParams.linkTypeName = IssueLinkTypeName.GROUP_INCLUDES
        plateActionParams.issueTypeName = IssueTypeName.PLATE_SS2

        plateActionParams
    }

    /**
     * Creates a {@code PlateActionParameterHolder} for removing a plate from the IMD workflow
     *
     * @param curIssue the specific issue
     * @return PlateActionParameterHolder object holding all the parameters needed for removing a plate from the
     * IMD workflow
     */
    public static PlateActionParameterHolder getIMDParameters(Issue curIssue) {
        PlateActionParameterHolder plateActionParams = getBasicPlateRemovalParameterHolder(curIssue)
        plateActionParams.plateWorkflowName = WorkflowName.PLATE_SS2
        plateActionParams.currentWorkflowName = WorkflowName.IMD
        plateActionParams.transitionName = TransitionName.REVERT_TO_WITH_CUSTOMER
        plateActionParams.previousPlateState = SS2PlateStateName.PLATESS2_IN_IMD
        plateActionParams.linkTypeName = IssueLinkTypeName.GROUP_INCLUDES
        plateActionParams.issueTypeName = IssueTypeName.PLATE_SS2

        plateActionParams
    }

    /**
     * Creates a {@code PlateActionParameterHolder} for removing a plate from the Submission workflow
     *
     * @param curIssue the specific issue
     * @return PlateActionParameterHolder object holding all the parameters needed for removing a plate from the
     * Submission workflow
     */
    public static PlateActionParameterHolder getSubmissionParameters(Issue curIssue) {
        PlateActionParameterHolder plateActionParams = getBasicPlateRemovalParameterHolder(curIssue)
        plateActionParams.plateWorkflowName = WorkflowName.PLATE_SS2
        plateActionParams.currentWorkflowName = WorkflowName.SUBMISSION
        plateActionParams.transitionName = TransitionName.REVERT_TO_READY_FOR_SUBMISSION
        plateActionParams.previousPlateState = SS2PlateStateName.PLATESS2_IN_SUBMISSION
        plateActionParams.linkTypeName = IssueLinkTypeName.GROUP_INCLUDES
        plateActionParams.issueTypeName = IssueTypeName.PLATE_SS2

        plateActionParams
    }

    private static PlateActionParameterHolder getBasicPlateRemovalParameterHolder(Issue curIssue) {
        PlateActionParameterHolder plateActionParams = new PlateActionParameterHolder()
        plateActionParams.currentIssue = curIssue

        plateActionParams
    }
}
