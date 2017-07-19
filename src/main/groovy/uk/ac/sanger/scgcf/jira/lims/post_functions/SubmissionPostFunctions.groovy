package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.issue.Issue
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * The {@code SubmissionPostFunctions} class holds post functions for the Submissions project
 *
 * Created by as28 on 30/06/2017.
 */

@Slf4j(value = "LOG")
class SubmissionPostFunctions {

    /**
     * Determine and set the Submission type field.
     * N.B. To set a custom field value on an issue that field MUST be in the JIRA Edit screen for its workflow.
     *
     * @param submIssue
     */
    public static void determineAndSetSubmissionType(Issue submIssue) {

        // get pre-amp protocol value
        String preAmpPrtcl = JiraAPIWrapper.getCFValueByName(submIssue, ConfigReader.getCFName("PRE-AMP_PROTOCOL")).toString()
        LOG.debug "Pre-Amp Protocol choice =  ${preAmpPrtcl}"

        // get post-amp protocol value
        String postAmpPrtcl = JiraAPIWrapper.getCFValueByName(submIssue, ConfigReader.getCFName("POST-AMP_PROTOCOL")).toString()
        LOG.debug "Post-Amp Protocol choice =  ${postAmpPrtcl}"

        if(preAmpPrtcl == null) {
            LOG.error "Pre-Amp Protocol value is null, cannot continue"
            return
        }

        if(postAmpPrtcl == null) {
            LOG.error "Post-Amp Protocol value is null, cannot continue"
            return
        }

        if(preAmpPrtcl == 'None required') {
            if(postAmpPrtcl == 'Nextera') {
                // Set Submission type = 2
                JiraAPIWrapper.setCFValueByName(submIssue, ConfigReader.getCFName("SUBMISSION_TYPE"), "2")
            } else {
                // error unrecognised post-amp type
                LOG.error "Unrecognised Post-Amp Protocol choice =  ${postAmpPrtcl}"
            }
        } else if(preAmpPrtcl == 'Smart-seq2 (RNA)') {
            if(postAmpPrtcl == 'None required') {
                // Set Submission type = 1
                JiraAPIWrapper.setCFValueByName(submIssue, ConfigReader.getCFName("SUBMISSION_TYPE"), "1")
            } else if(postAmpPrtcl == 'Nextera') {
                // Set Submission type = 3
                JiraAPIWrapper.setCFValueByName(submIssue, ConfigReader.getCFName("SUBMISSION_TYPE"), "3")
            } else {
                // error unrecognised post-amp type
                LOG.error "Unrecognised Post-Amp Protocol choice =  ${postAmpPrtcl}"
            }
        } else {
            // error unrecognised pre-amp type
            LOG.error "Unrecognised Pre-Amp Protocol choice =  ${preAmpPrtcl}"
        }
    }


}
