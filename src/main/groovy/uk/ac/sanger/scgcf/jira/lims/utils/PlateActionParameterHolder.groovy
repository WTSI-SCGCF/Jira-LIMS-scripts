package uk.ac.sanger.scgcf.jira.lims.utils

import com.atlassian.jira.issue.Issue

/**
 * This object holding all the parameters needed for actioning a plate in the
 * given workflow
 *
 * Created by ke4 on 25/01/2017.
 */
class PlateActionParameterHolder {
    String[] plateIds
    String plateWorkflowName
    Issue currentIssue
    String issueTypeName
    String currentWorkflowName
    Map<String, String> statusToTransitionMap = new HashMap<>()
    String linkTypeName
}
