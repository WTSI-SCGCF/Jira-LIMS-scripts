package uk.ac.sanger.scgcf.jira.lims.post_functions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkType
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code CombinePlatesPostFunctions} class holds post functions for the Combine Plates project
 *
 * Created by as28 on 20/04/2017.
 */

@Slf4j(value = "LOG")
class CombinePlatesPostFunctions {

    /**
     * This post function takes the values of the 'DNA Type' field for the four linked
     * source plates and sets the 'DNA Type' on the Combine Plates issue
     *
     * @param curIssue
     */

    public static void setDNATypeOnCMBPlate(Issue curIssue) {

        // get the issue link type
        IssueLinkType plateLinkType = WorkflowUtils.getIssueLinkType(IssueLinkTypeName.GROUP_INCLUDES.toString())

        // get the outward linked plate issues from the Combine Plates issue
        List<IssueLink> outwardLinksList = WorkflowUtils.getOutwardLinksListForIssueId(curIssue.getId())

        // create map to hold 4 DNA Types
        HashMap<String, String> dnaTypesMap = [:]

        def customFieldManager = ComponentAccessor.getCustomFieldManager()
        def cfDNAType          = customFieldManager.getCustomFieldObject(ConfigReader.getCFId('DNA_TYPE'))
        def cfCombineQuadrant  = customFieldManager.getCustomFieldObject(ConfigReader.getCFId('COMBINE_QUADRANT'))

        // for each linked plate issue (NB. sources and destinations)
        outwardLinksList.each { IssueLink issLink ->
            Issue curPlateIssue = issLink.getDestinationObject()

            // only want source plates
            if ((curPlateIssue.getIssueType().name == IssueTypeName.PLATE_SS2.toString()
                    || curPlateIssue.getIssueType().name == IssueTypeName.PLATE_DNA.toString())
                    && issLink.getIssueLinkType() == plateLinkType) {

                // for each source plate issue fetch the 'Combine Quadrant' value (1-4) and the 'DNA Type',
                String curDNAType         = curPlateIssue.getCustomFieldValue(cfDNAType)
                String curCombineQuadrant = curPlateIssue.getCustomFieldValue(cfCombineQuadrant)

                LOG.debug "PF SetDNATypeOnCMB: Found source plate:"
                LOG.debug "PF SetDNATypeOnCMB: Quadrant = ${curCombineQuadrant}"
                LOG.debug "PF SetDNATypeOnCMB: DNA Type = ${curDNAType}"

                // map the quadrant vs DNA Type value e.g. 1, cDNA; 2, cDNA; 3, cDNA; 4, cDNA
                dnaTypesMap.put(curCombineQuadrant, curDNAType)
            }
        }

        // check for 4 entries
        int numTypesFound = dnaTypesMap.size()
        LOG.debug "PF SetDNATypeOnCMB: Number of source plates = ${numTypesFound}"
        if(numTypesFound != 4) {
            LOG.debug "PF SetDNATypeOnCMB: ERROR: Unexpected number of source plates!"
            JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("DNA_TYPE"), "ERROR check combine sources")
            return
        }

        def matchingKeys = dnaTypesMap.findAll { it.value == dnaTypesMap.get("1") }*.key
        if(matchingKeys.size() == 4) {
            // if all the same v1 == v2 == v3 == v4 set DNA Type on CMB issue to 1 value
            LOG.debug "PF SetDNATypeOnCMB: all DNA Types matched"
            JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("DNA_TYPE"), dnaTypesMap.get("1"))
        } else {
            // set DNA Type to 'Mixed(v1,v2,v3,v4)' in quadrant order
            LOG.debug "PF SetDNATypeOnCMB: DNA Types mixed"
            String outDNAType = "Mixed(" + [ dnaTypesMap.get("1"), dnaTypesMap.get("2"), dnaTypesMap.get("3"), dnaTypesMap.get("4") ].join(',') + ")"
            JiraAPIWrapper.setCustomFieldValueByName(curIssue, ConfigReader.getCustomFieldName("DNA_TYPE"), outDNAType)
        }
    }

}
