package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.utils.PlateActionParameterHolder
import uk.ac.sanger.scgcf.jira.lims.utils.PlateAdderParametersCreator
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * This post function extracts a list of selected plates from an nFeed custom field and adds them
 * to the current issue via a function in {@code WorkflowUtils}.
 * It adds a link and transition the plate ticket state if appropriate.
 *
 * Created by ke4 on 24/01/2017.
 */
@Slf4j(value = "LOG")
class PlateAdder extends BaseIssueAction {

    Map<String, PlateActionParameterHolder> plateActionParameterHolders
    ArrayList<String> selectedIssueIds

    public PlateAdder(Issue curIssue, String issueTypeName, String customFieldName) {
        super(curIssue, issueTypeName, customFieldName)

        initPlateActionParameterHolders()
    }

    /**
     * Adds the selected plates to the current group issue.
     */
    public void execute() {
        validateParameters()

        LOG.debug "Post-function for adding plates with issue type <${issueTypeName}> to the group issue with Key <${curIssue.getKey()}>"

        selectedIssueIds = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, customFieldName)

        // if user hasn't selected anything do nothing further
        if (selectedIssueIds == null) {
            LOG.debug("No plates selected for adding, nothing to do")
            return
        }

        selectedIssueIds.each { LOG.debug "Plate ID: <$it> has been selected to be added" }

        PlateActionParameterHolder parameters = plateActionParameterHolders.get(issueTypeName)
        parameters.plateIds = selectedIssueIds

        // link and transition the plate issue(s)
        WorkflowUtils.addPlatesToGivenGrouping(parameters)

    }

    /**
     * Initialise the plate action parameter holders map.
     *
     * N.B. key is the issue type name of the GROUP issue.
     */
    private void initPlateActionParameterHolders() {
        plateActionParameterHolders = new HashMap<>()
        plateActionParameterHolders.put(IssueTypeName.IMPORT_DECLARATION.toString(),
                PlateAdderParametersCreator.getIMDParameters(curIssue))
        plateActionParameterHolders.put(IssueTypeName.SUBMISSION.toString(),
                PlateAdderParametersCreator.getSubmissionParameters(curIssue))
        plateActionParameterHolders.put(IssueTypeName.QUANTIFICATION_ANALYSIS.toString(),
                PlateAdderParametersCreator.getQNTAParameters(curIssue))
    }
}