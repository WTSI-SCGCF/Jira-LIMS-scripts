package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.inputqc

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.post_functions.ReagentLinker

/**
 * This post function extracts a list of selected reagents from an nFeed custom field and links them
 * to the current Input QC issue via a function in {@code ReagentLinker}.
 *
 * Created by as28 on 06/04/2017.
 */

// get the current issue (from binding)
Issue curIssue = issue

ReagentLinker reagentLinker = new ReagentLinker(curIssue, "HS_LARGE_FRAGMENT_ANALYSIS_KIT")
reagentLinker.execute()