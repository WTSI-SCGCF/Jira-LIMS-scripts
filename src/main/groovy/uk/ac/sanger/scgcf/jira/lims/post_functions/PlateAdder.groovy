package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
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
    String[] selectedValues

    public PlateAdder(Issue curIssue, String issueTypeName, String customFieldName) {
        super(curIssue, issueTypeName, customFieldName)

        initPlateActionParameterHolders()
    }

    public void execute() {
        validateParameters()

        LOG.debug "Post-function for adding plates to $issueTypeName workflow".toString()

        selectedValues = getCustomFieldValuesByName()

        //if user hasn't selected anything do nothing further
        if (selectedValues == null) {
            LOG.debug("No items selected, nothing to do")
            return
        }

        selectedValues.each { LOG.debug "Plate ID: $it has been selected to add" }

        PlateActionParameterHolder parameters = plateActionParameterHolders.get(issueTypeName)
        parameters.plateIds = selectedValues

        // link and transition the plate issue(s)
        WorkflowUtils.addPlatesToGivenGrouping(parameters)

    }

    private void initPlateActionParameterHolders() {
        plateActionParameterHolders = new HashMap<>()
        plateActionParameterHolders.put(WorkflowName.IMD.toString(),
                PlateAdderParametersCreator.getIMDParameters(curIssue))
        plateActionParameterHolders.put(WorkflowName.SUBMISSION.toString(),
                PlateAdderParametersCreator.getSubmissionParameters(curIssue))

    }
}