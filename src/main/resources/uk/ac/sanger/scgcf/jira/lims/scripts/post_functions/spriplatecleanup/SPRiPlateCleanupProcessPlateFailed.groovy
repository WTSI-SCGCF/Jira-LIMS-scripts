package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.spriplatecleanup

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.post_functions.SPRiPlateCleanupPostFunctions

/**
 * Created by as28 on 11/05/2017.
 */

/**
 * Processes a plate that has failed SPRi.
 *
 * Created by as28 on 11/05/2017.
 */


// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for a plate that failed SPRi cleanup"

// fetch the array of selected plates from the nFeed custom field (should only be one)
def customFieldManager = ComponentAccessor.getCustomFieldManager()
def customField =  customFieldManager.getCustomFieldObject(ConfigReader.getCFId('CURRENT_SPRI_PLATES_IN_PROGRESS_FOR_FAILING'))

if(customField != null) {
    // the value of the nFeed field is a list of long issue ids for the selected plates (should be one here)
    String[] arraySourcePlateIds = curIssue.getCustomFieldValue(customField)

    // if user hasn't selected anything do nothing further
    if (arraySourcePlateIds == null) {
        LOG.error("No plates selected, nothing to do")
        return
    }

    // check we have one plate
    if(arraySourcePlateIds.size() > 1) {
        LOG.error("More than one plate selected, unexpected")
    }

    // process the plate
    SPRiPlateCleanupPostFunctions.processPlateFailedCleanup(curIssue, arraySourcePlateIds[0])

} else {
    LOG.error("Failed to get the plate array custom field for adding plates to a Submission")
}
