package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.post_functions.ReagentLinker

/**
 * This post function extracts a list of selected reagents from an nFeed custom field and links them
 * to the current Smart-seq2 issue via a function in {@code ReagentLinker}.
 *
 * Created by ke4 on 15/02/2017.
 */

// get the current issue (from binding)
Issue curIssue = issue

ReagentLinker reagentLinker = new ReagentLinker(curIssue, "CURRENT_SMART-SEQ2_REAGENTS")
reagentLinker.execute()
