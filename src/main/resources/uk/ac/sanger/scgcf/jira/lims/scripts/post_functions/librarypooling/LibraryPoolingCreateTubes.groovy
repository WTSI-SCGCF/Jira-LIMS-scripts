package uk.ac.sanger.scgcf.jira.lims.scripts.post_functions.librarypooling

import com.atlassian.jira.issue.Issue
import groovy.transform.Field
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.ac.sanger.scgcf.jira.lims.post_functions.LibraryPoolingPostFunctions

/**
 * This post function script determines how many LPL tubes are required for each source LIB plate,
 * by checking the ancestor plates and Submission option for 'Cells per Pool'.
 *
 * Each LPL tube is then created and linked to both their source LIB plate and to the Library Pooling
 * issue. Each LPL tube issue will contain the information needed to print it's own label.
 *
 * Also creates a wiki markup table to display in the Library Pooling issue 'Block Layout' field
 * that shows the user how the Hamilton tube block should look.
 *
 * Created by as28 on 05/07/2017.
 */

// create logging class
@Field private final Logger LOG = LoggerFactory.getLogger(getClass())

// get the current issue (from binding)
Issue curIssue = issue

LOG.debug "Post-function for the creation of LPL tubes"

LibraryPoolingPostFunctions.createTubesForLibraryPooling(curIssue)