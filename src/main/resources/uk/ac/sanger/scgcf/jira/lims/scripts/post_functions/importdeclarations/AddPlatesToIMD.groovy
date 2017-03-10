package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.importdeclarations

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.enums.WorkflowName
import uk.ac.sanger.scgcf.jira.lims.post_functions.PlateAdder

/**
 * This post function extracts a list of selected plates from an nFeed custom field and adds them
 * to the current Import Declaration issue via a function in {@code PlateAdder}.
 * This links the issues and transitions the plate ticket state if appropriate.
 *
 * Created by ke4 on 06/02/2017.
 */

Issue curIssue = issue

PlateAdder plateAdder = new PlateAdder(curIssue, WorkflowName.IMD.toString(), "ADD_PLATES_TO_IMPORT_DECLARATION")
plateAdder.execute()
