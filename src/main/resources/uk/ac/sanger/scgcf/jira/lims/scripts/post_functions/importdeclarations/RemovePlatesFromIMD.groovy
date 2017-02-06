package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.importdeclarations

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.post_functions.PlateRemover

/**
 * This post function extracts a list of selected plates from an nFeed custom field and removes them
 * from the current Import Declaration issue via a function in {@code PlateAdder}.
 * This removes the links of the issues and reverts the plate ticket state if appropriate.
 *
 * Created by ke4 on 06/02/2017.
 */
Issue curIssue = issue

PlateRemover plateRemover = new PlateRemover(curIssue, WorkflowName.IMD.toString(), "GENERIC_REMOVE_PLATES")
plateRemover.execute()