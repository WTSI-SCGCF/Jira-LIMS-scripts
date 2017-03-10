package uk.ac.sanger.scgcf.jira.lims.utils

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.SS2PlateStateName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName

/**
 * This class contains static factory method to create various {@code PlateActionParameterHolder} instances
 * used by {@code PlateAdder} class.
 *
 * Created by ke4 on 03/02/2017.
 */
class PlateAdderParametersCreator {

    /**
     * Creates a {@code PlateActionParameterHolder} for adding a plate to the IMD workflow
     *
     * @param curIssue the specific issue
     * @return PlateActionParameterHolder object holding all the parameters needed for adding a plate to the
     * IMD workflow
     */
    public static PlateActionParameterHolder getIMDParameters(Issue curIssue) {
        PlateActionParameterHolder plateActionParams = getBasicPlateActionParameterHolder(curIssue)
        plateActionParams.currentWorkflowName = WorkflowName.IMD
        plateActionParams.statusToTransitionMap.put(
                SS2PlateStateName.PLATESS2_WITH_CUSTOMER.toString(), TransitionName.START_IMPORT_DECLARATION.toString())
        plateActionParams.linkTypeName = IssueLinkTypeName.GROUP_INCLUDES

        // TODO: these fields are plate specific but IMD handles more than one plate type e.g. Plate DNA
        plateActionParams.issueTypeName = IssueTypeName.PLATE_SS2
        plateActionParams.plateWorkflowName = WorkflowName.PLATE_SS2

        plateActionParams
    }

    /**
     * Creates a {@code PlateActionParameterHolder} for adding a plate to the Submission workflow
     *
     * @param curIssue the specific issue
     * @return PlateActionParameterHolder object holding all the parameters needed for adding a plate to the
     * Submission workflow
     */
    public static PlateActionParameterHolder getSubmissionParameters(Issue curIssue) {
        PlateActionParameterHolder plateActionParams = getBasicPlateActionParameterHolder(curIssue)
        plateActionParams.currentWorkflowName = WorkflowName.SUBMISSION
        plateActionParams.statusToTransitionMap.put(
                SS2PlateStateName.PLATESS2_RDY_FOR_SUBMISSION.toString(), TransitionName.START_SUBMISSION.toString())
        plateActionParams.linkTypeName = IssueLinkTypeName.GROUP_INCLUDES

        // TODO: these fields are plate specific but Submissions handles more than one plate type e.g Plate DNA
        plateActionParams.issueTypeName = IssueTypeName.PLATE_SS2
        plateActionParams.plateWorkflowName = WorkflowName.PLATE_SS2

        plateActionParams
    }

    private static PlateActionParameterHolder getBasicPlateActionParameterHolder(Issue curIssue) {
        PlateActionParameterHolder plateActionParams = new PlateActionParameterHolder()
        plateActionParams.currentIssue = curIssue

        plateActionParams
    }
}
