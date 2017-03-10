package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.submission

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.post_functions.PlateRemover

Issue curIssue = issue

/**
 * This post function extracts a list of selected plates from an nFeed custom field and removes them
 * from the current Submission issue via a function in {@code PlateAdder}.
 * This removes the links of the issues and reverts the plate ticket state if appropriate.
 *
 * Created by ke4 on 06/02/2017.
 */

PlateRemover plateRemover = new PlateRemover(curIssue, WorkflowName.SUBMISSION.toString(), "GENERIC_REMOVE_PLATES")
plateRemover.execute()