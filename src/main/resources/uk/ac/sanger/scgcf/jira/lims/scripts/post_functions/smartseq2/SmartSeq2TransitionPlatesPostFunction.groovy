package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.smartseq2

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.post_functions.SmartSeq2PostFunctions

/**
 * This post function extracts a list of selected plates from an nFeed custom field and
 * transition them to 'PltSS2 In Feedback' state via a function in {@code SmartSeq2PostFunctions}.
 *
 * Created by ke4 on 26/01/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for transition plates in Smart-seq2 to 'PltSS2 in Feedback' status"

// fetch the array of selected plates from the nFeed custom field
def customFieldManager = ComponentAccessor.getCustomFieldManager()
def customField =  customFieldManager.getCustomFieldObject(ConfigReader.getCFId('GENERIC_REMOVE_PLATES'))

if(customField != null) {
    // the value of the nFeed field is a list of long issue ids for the selected plates
    String[] arrayPlateIds = curIssue.getCustomFieldValue(customField)

    // if user hasn't selected anything do nothing further
    if (arrayPlateIds == null) {
        LOG.debug("No plates selected, nothing to do")
        return
    }

    // link and transition the plate issue(s)
    SmartSeq2PostFunctions.transitionPlates(arrayPlateIds, WorkflowName.PLATE_SS2,
            IssueTypeName.PLATE_SS2, IssueStatusName.PLATESS2_IN_SS2,
            IssueStatusName.PLATESS2_IN_FEEDBACK, TransitionName.AWAITING_SS2_FEEDBACK)

} else {
    LOG.error("Failed to get the plate array custom field for adding plates to a Submission")
}