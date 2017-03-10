package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import groovy.util.logging.Slf4j
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
    String[] selectedValues

    public PlateRemover(Issue curIssue, String issueTypeName, String customFieldName,
                        List<String> fieldNamesToClear = new ArrayList<>()) {
        super(curIssue, issueTypeName, customFieldName)
        this.fieldNamesToClear = fieldNamesToClear

        initPlateRemovalParameterHolders()
    }

    public void execute() {
        validateParameters()

        LOG.debug "Post-function for removing plates from a $issueTypeName workflow".toString()

        selectedValues = getCustomFieldValuesByName()

        //if user hasn't selected anything do nothing further
        if (selectedValues == null) {
            LOG.debug("No items selected, nothing to do")
            return
        }

        selectedValues.each { LOG.debug "Plate ID: $it has been selected to add" }

        PlateActionParameterHolder parameters = plateActionParameterHolders.get(issueTypeName)
        parameters.plateIds = selectedValues

        // unlink and revert the plate issue(s)
        WorkflowUtils.removePlatesFromGivenGrouping(parameters, fieldNamesToClear)
    }

    private void initPlateRemovalParameterHolders() {
        plateActionParameterHolders = new HashMap<>()
        plateActionParameterHolders.put(WorkflowName.SMART_SEQ2.toString(),
                PlateRemoverParametersCreator.getSmartSeq2Parameters(curIssue))
        plateActionParameterHolders.put(WorkflowName.IMD.toString(),
                PlateRemoverParametersCreator.getIMDParameters(curIssue))
        plateActionParameterHolders.put(WorkflowName.SUBMISSION.toString(),
                PlateRemoverParametersCreator.getSubmissionParameters(curIssue))
        plateActionParameterHolders.put(WorkflowName.SAMPLE_RECEIPT.toString(),
                PlateRemoverParametersCreator.getSampleReceiptsParameters(curIssue))
    }
}