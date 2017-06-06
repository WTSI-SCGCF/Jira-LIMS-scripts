package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.utils.PlateActionParameterHolder
import uk.ac.sanger.scgcf.jira.lims.utils.PlateRemoverParametersCreator
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * This post function extracts a list of selected plates from an nFeed custom field and removes them
 * from the current issue via a function in {@code WorkflowUtils}.
 * It removes the link and reverts the plate ticket state if appropriate.
 *
 * Created by ke4 on 24/01/2017.
 */
@Slf4j(value = "LOG")
class PlateRemover extends BaseIssueAction {

    Map<String, PlateActionParameterHolder> plateActionParameterHolders
    List<String> fieldNamesToClear
    ArrayList<String> selectedIssueIds

    public PlateRemover(Issue curIssue, String issueTypeName, String customFieldName,
                        List<String> fieldNamesToClear = new ArrayList<>()) {
        super(curIssue, issueTypeName, customFieldName)
        this.fieldNamesToClear = fieldNamesToClear

        initPlateRemovalParameterHolders()
    }

    /**
     * Removes the selected plates from the current group issue.
     */
    public void execute() {
        validateParameters()

        LOG.debug "Post-function for removing plates with issue type <${issueTypeName}> from the group issue with Key <${curIssue.getKey()}>"

        selectedIssueIds = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, customFieldName)

        // if user hasn't selected anything do nothing further
        if (selectedIssueIds == null) {
            LOG.debug("No items selected, nothing to do")
            return
        }

        selectedIssueIds.each { LOG.debug "Plate ID: $it has been selected to add" }

        PlateActionParameterHolder parameters = plateActionParameterHolders.get(issueTypeName)
        parameters.plateIds = selectedIssueIds

        // de-link and transition the plates
        WorkflowUtils.removePlatesFromGivenGrouping(parameters, fieldNamesToClear)
    }

    /**
     * Initialise the plate action parameter holders map.
     *
     * N.B. key is the issue type name of the GROUP issue.
     */
    private void initPlateRemovalParameterHolders() {
        plateActionParameterHolders = new HashMap<>()
        plateActionParameterHolders.put(IssueTypeName.SMART_SEQ2.toString(),
                PlateRemoverParametersCreator.getSmartSeq2Parameters(curIssue))
        plateActionParameterHolders.put(IssueTypeName.IMPORT_DECLARATION.toString(),
                PlateRemoverParametersCreator.getIMDParameters(curIssue))
        plateActionParameterHolders.put(IssueTypeName.SUBMISSION.toString(),
                PlateRemoverParametersCreator.getSubmissionParameters(curIssue))
        plateActionParameterHolders.put(IssueTypeName.SAMPLE_RECEIPT.toString(),
                PlateRemoverParametersCreator.getSampleReceiptsParameters(curIssue))
    }
}