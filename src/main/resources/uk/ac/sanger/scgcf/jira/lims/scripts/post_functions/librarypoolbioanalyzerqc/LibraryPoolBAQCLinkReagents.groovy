package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypoolbioanalyzerqc

import com.atlassian.jira.issue.Issue
import uk.ac.sanger.scgcf.jira.lims.post_functions.ReagentLinker

/**
 * This post function extracts lists of selected reagents from two nFeed custom fields and links them
 * to the current Library Pool BioAnalyzer QC issue via a function in {@code ReagentLinker}.
 *
 * Created by as28 on 03/08/2017.
 */

// get the current issue (from binding)
Issue curIssue = issue

ReagentLinker reagentLinkerChips = new ReagentLinker(curIssue, "CURRENT_LIBRARY_QC_HS_CHIPS")
reagentLinkerChips.execute()

ReagentLinker reagentLinkerRegs = new ReagentLinker(curIssue, "CURRENT_LIBRARY_QC_HS_REAGENTS")
reagentLinkerRegs.execute()