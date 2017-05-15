package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.smartseq2

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.TransitionName
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.post_functions.SmartSeq2PostFunctions
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

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
String cfAlias = "GENERIC_REMOVE_PLATES"

ArrayList<String> ids = WorkflowUtils.getIssueIdsFromNFeedField(curIssue, cfAlias)

if(ids != null) {
    // link and transition the plate issue(s)
    SmartSeq2PostFunctions.transitionPlates(ids, WorkflowName.PLATE_SS2,
            IssueTypeName.PLATE_SS2, IssueStatusName.PLTSS2_IN_SS2,
            IssueStatusName.PLTSS2_IN_FEEDBACK, TransitionName.SS2_AWAITING_SS2_FEEDBACK)
}