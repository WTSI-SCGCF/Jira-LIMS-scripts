package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.dnaquantification

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.post_functions.PlateAdder

/**
 * This post function extracts the selected plates from an nFeed custom field and adds them
 * to the current Quant Analysis issue via a function in {@code PlateAdder}.
 * This links the issue and transitions the plate ticket state if appropriate.
 *
 * Created by as28 on 01/06/2017.
 */

Issue curIssue = issue

PlateAdder plateAdder = new PlateAdder(curIssue, IssueTypeName.QUANTIFICATION_ANALYSIS.toString(), "CURRENT_QUANT_PLATES_READY_FOR_ANALYSIS")
plateAdder.execute()
