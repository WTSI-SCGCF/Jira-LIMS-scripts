package uk.ac.sanger.scgcf.jira.lims.utils

import com.atlassian.jira.ComponentManager
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.link.IssueLinkType
import com.atlassian.jira.issue.link.IssueLinkTypeManager
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.issue.search.SearchResults
import com.atlassian.jira.jql.builder.JqlClauseBuilder
import com.atlassian.jira.jql.builder.JqlQueryBuilder
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.util.ErrorCollection
import com.atlassian.jira.util.JiraUtils
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.jira.workflow.JiraWorkflow
import com.atlassian.jira.workflow.WorkflowTransitionUtil
import com.atlassian.jira.workflow.WorkflowTransitionUtilImpl
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.query.Query
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.GPathResult
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * Utility class for getting Jira workflow related properties.
 *
 * Created by ke4 on 11/10/2016.
 */

@Slf4j(value = "LOG")
class WorkflowUtils {

    /**
     * Link a list of plates to the specified grouping issue and transition them if appropriate.
     *
     * @param plateActionParams a <code>PlateActionParameterHolder</code> object holding all the parameters
     * needed for adding a plate to the given grouping issue
     */
    public static void addPlatesToGivenGrouping(PlateActionParameterHolder plateActionParams) {
        executePlateAction(
            plateActionParams,
            { Issue mutableIssue ->
                createIssueLink(plateActionParams.currentIssue.getId(), mutableIssue.getId(), plateActionParams.linkTypeName.toString())
            },
            { String plateIdString ->
                "Attempting to link plate with ID ${plateIdString} to ${plateActionParams.currentWorkflowName} workflow with ID ${plateActionParams.currentIssue.id}".toString()
            }
        )
    }

    /**
     * Remove the links between a list of plates and the specified grouping issue and transition them if appropriate.
     *
     * @param plateActionParams a <code>PlateActionParameterHolder</code> object holding all the parameters
     * needed for removing a plate from the given grouping issue
     */
    public static void removePlatesFromGivenGrouping(PlateActionParameterHolder plateActionParams, List<String> fieldNamesToClear) {
        executePlateAction(
                plateActionParams,
                { Issue mutableIssue ->
                    removeIssueLink(plateActionParams.currentIssue, mutableIssue, plateActionParams.linkTypeName)
                    if (fieldNamesToClear) {
                        clearFieldsValue(fieldNamesToClear, mutableIssue)
                    }
                },
                { String plateIdString ->
                    "Removing link to plate with ID ${plateIdString} from ${plateActionParams.currentWorkflowName}".toString()
                }
        )
    }

    /**
     * Performs a plate Action.
     *
     * @param plateActionParams
     * @param actionToExecute
     * @param messageClosure
     */
    private static void executePlateAction(PlateActionParameterHolder plateActionParams, Closure actionToExecute,
                                           Closure messageClosure) {

        plateActionParams.plateIds.each { String plateIdString ->
            Long plateIdLong = Long.parseLong(plateIdString)
            LOG.debug((String)messageClosure(plateIdString))

            MutableIssue mutableIssue = getMutableIssueForIssueId(plateIdLong)

            if(mutableIssue != null) {
                LOG.debug("Issue type = ${mutableIssue.getIssueType().getName()} and plate action issue type = ${plateActionParams.issueTypeName}")
            } else {
                LOG.debug("Issue null")
            }

            if(mutableIssue != null && mutableIssue.getIssueType().getName() == plateActionParams.issueTypeName) {

                actionToExecute(mutableIssue)

                String issueStatusName = mutableIssue.getStatus().getName()

                if( plateActionParams.statusToTransitionMap.keySet().contains(issueStatusName))  {
                    String transitionName = plateActionParams.statusToTransitionMap.get(issueStatusName)
                    LOG.debug("Attempting to fetch action ID for workflow ${plateActionParams.plateWorkflowName} and transition name $transitionName")
                    int actionId = ConfigReader.getTransitionActionId(plateActionParams.plateWorkflowName, transitionName)
                    LOG.debug("Action ID = ${actionId}")

                    transitionIssue(mutableIssue, actionId)
                }
            }
        }
    }

    /**
     * Link a list of reagents to the specified issue.
     *
     * @param listReagentIds the list of reagent issue ids
     * @param curIssue the issue to link the reagent to
     */
    public static void linkReagentsToGivenIssue(ArrayList<String> listReagentIds, Issue curIssue) {

        LOG.debug "arrayReagentIds = ${listReagentIds.toListString()}".toString()
        listReagentIds.each { String reagentIdString ->
            LOG.debug "reagentIdString = ${reagentIdString}".toString()

            Long reagentIdLong = Long.parseLong(reagentIdString)
            LOG.debug "Attempting to link reagent with ID $reagentIdString to issue with ID ${curIssue.id}".toString()

            MutableIssue reagentMutableIssue = getMutableIssueForIssueId(reagentIdLong)

            if(reagentMutableIssue != null && reagentMutableIssue.getIssueType().getName() == IssueTypeName.REAGENT_LOT_OR_BATCH.toString()) {
                LOG.debug "Calling link function in WorkflowUtils to link reagent to issue with ID ${curIssue.id}".toString()
                try {
                    createIssueLink(curIssue.getId(), reagentMutableIssue.getId(), IssueLinkTypeName.USES_REAGENT.toString())
                    LOG.debug "Successfully linked reagent with ID $reagentIdString to issue with ID ${curIssue.id}".toString()
                } catch (Exception e) {
                    LOG.error "Failed to link reagent with ID $reagentIdString to issue with ID ${curIssue.id}".toString()
                    LOG.error e.message
                }
            } else {
                LOG.error "Reagent issue null or unexpected issue type when linking reagent with ID ${reagentIdString} to issue with ID ${curIssue.id}".toString()
            }
        }
    }

    /**
     * Gets the transition name by the given {@Issue} and actionID of the bounded transition variables.
     *
     * @param issue the current issue
     * @param transitionVars the current bounded transition variables
     * @return the name of the transition by the given {@Issue} and actionID of the bounded transition variables.
     */
    public static String getTransitionName(Issue issue, Map<String, Object> transitionVars) {
        JiraWorkflow workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue)
        def wfd = workflow.getDescriptor()
        def actionid = transitionVars["actionId"] as Integer

        wfd.getAction(actionid).getName()
    }

    /**
     * Get the current logged in application user
     *
     * @return user
     */
    public static ApplicationUser getLoggedInUser() {
        // assumes there is always a logged in user
        JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext()
        ApplicationUser user = authContext.getLoggedInUser()
        LOG.debug "Logged in User : ${user.getName()}"
        user
    }

    /**
     * Transitions an issue through the specified transition action
     *
     * @param issue the issue to be transitioned
     * @param actionId the transition action id
     * @return the error collection if the validation or transition fail, otherwise nothing
     */
    public static void transitionIssue(MutableIssue issue, int actionId) {
        // set up the transition
        WorkflowTransitionUtil wfTransUtil = JiraUtils.loadComponent(WorkflowTransitionUtilImpl.class)
        wfTransUtil.setIssue(issue)
        ApplicationUser user = getLoggedInUser()
        wfTransUtil.setUserkey(user.getKey())
        wfTransUtil.setAction(actionId)

        // validate the transition
        ErrorCollection ecValidate = wfTransUtil.validate()
        if(ecValidate.hasAnyErrors()) {
            LOG.error("Validation error transitioning plate issue with ID ${issue.getId()}".toString())
            // Get all non field-specific error messages
            Collection<String> stringErrors = ecValidate.getErrorMessages()
            stringErrors.eachWithIndex { String err, int i ->
                LOG.error("Error ${i}: ${err}".toString())
            }
            return
        }

        // perform the transition
        ErrorCollection ecProgress = wfTransUtil.progress()
        if(ecProgress.hasAnyErrors()) {
            LOG.error("Progress error transitioning plate issue with ID ${issue.getId()}".toString())
            // Get all non field-specific error messages
            Collection<String> stringErrors = ecProgress.getErrorMessages()
            stringErrors.eachWithIndex { String err, int i ->
                LOG.error("Error ${i}: ${err}".toString())
            }
        }
    }

    /**
     * Create a reciprocal issue link of the specified link type between two specified issues
     *
     * @param sourceIssue the issue that is the source of the link
     * @param destinationIssue the issue that is the destination of the link
     * @param linkTypeName the name of the issue link type
     */
    public static void createIssueLink(Long sourceId, Long destinationId, String linkTypeName) {
        // determine the issue link type
        ComponentManager cManager = ComponentManager.getInstance()
        IssueLinkTypeManager issLnkTMngr = cManager.getComponentInstanceOfType(IssueLinkTypeManager.class)
        IssueLinkType issLnkType = (issLnkTMngr.getIssueLinkTypesByName(linkTypeName))[0]

        // determine the user
        ApplicationUser user = getLoggedInUser()

        // link the issues together (will create a reciprocal link)
        IssueLinkManager issLnkMngr = ComponentAccessor.getIssueLinkManager()
        // throws a CreateException if it fails
        issLnkMngr.createIssueLink(sourceId, destinationId, issLnkType.id, 1L, user)
    }

    /**
     * Remove a reciprocal issue link of the specified type between two specified issues
     *
     * @param sourceIssue the issue that is the source of the link
     * @param destinationIssue the issue that is the destination of the link
     * @param linkTypeName the name of the issue link type
     */
    public static void removeIssueLink(Issue sourceIssue, Issue destinationIssue, String linkTypeName) {
        // determine the issue link type
        ComponentManager cManager = ComponentManager.getInstance()
        IssueLinkTypeManager issLnkTMngr = cManager.getComponentInstanceOfType(IssueLinkTypeManager.class)
        IssueLinkType issLnkType  = (issLnkTMngr.getIssueLinkTypesByName(linkTypeName))[0]

        // determine the user
        ApplicationUser user = getLoggedInUser()

        // remove the link between the plate issue and the submission issue
        IssueLinkManager issLnkMngr  = ComponentAccessor.getIssueLinkManager()
        IssueLink issueLink = issLnkMngr.getIssueLink(sourceIssue.id, destinationIssue.id, issLnkType.id)
        // throws IllegalArgumentException if the specified issueLink is null
        issLnkMngr.removeIssueLink(issueLink, user)
    }

    /**
     * Get the issue link type for a named issue link
     *
     * @param linkName
     * @return
     */
    public static IssueLinkType getIssueLinkType(String linkName) {

        // get the issue link type
        ComponentManager componentManager = ComponentManager.getInstance()
        IssueLinkTypeManager issLnkTMngr = componentManager.getComponentInstanceOfType(IssueLinkTypeManager.class)
        IssueLinkType issueLinkType  = (issLnkTMngr.getIssueLinkTypesByName(linkName))[0]

        issueLinkType
    }

    /**
     * Get the list of inward issue links for an issue id
     *
     * @param issueId
     * @return list of IssueLinks
     */
    public static List<IssueLink> getInwardLinksListForIssueId(Long issueId) {
        IssueLinkManager issLnkMngr  = ComponentAccessor.getIssueLinkManager()
        List<IssueLink> inwardLinksList = issLnkMngr.getInwardLinks(issueId)

        inwardLinksList
    }

    /**
     * Get the list of outward links for an issue id
     *
     * @param issueId
     * @return list of IssueLinks
     */
    public static List<IssueLink> getOutwardLinksListForIssueId(Long issueId) {
        IssueLinkManager issLnkMngr = ComponentAccessor.getIssueLinkManager()
        List<IssueLink> outwardLinksList = issLnkMngr.getOutwardLinks(issueId)

        outwardLinksList
    }

    /**
     * Get a mutable issue for an issue id
     *
     * @param issueId
     * @return mutable version of the issue
     */
    public static MutableIssue getMutableIssueForIssueId(Long issueId) {
        IssueManager issMngr = ComponentAccessor.getIssueManager()
        MutableIssue mutableIssue = issMngr.getIssueObject(issueId)

        mutableIssue
    }

    /**
     * Clear a list of fields
     *
     * @param fieldNamesToClear
     * @param issue
     */
    private static void clearFieldsValue(List<String> fieldNamesToClear, Issue issue) {
        fieldNamesToClear.each { fieldName ->
            JiraAPIWrapper.setCustomFieldValueByName(issue, fieldName, null)
        }
    }

    /**
     * Use a JQL query to fetch the Jira issue for a specific barcode
     *
     * @param barcode
     * @return
     */
    public static Issue getIssueForBarcode(String barcode) {

        // check for null or empty barcode
        if(!barcode?.trim()) {
            LOG.error "Barcode not present, cannot continue"
            return null
        }

        Issue issue = null

        try {
            CustomField cf = JiraAPIWrapper.getCustomFieldByName(ConfigReader.getCFName("BARCODE"))
            JqlClauseBuilder jqlBuilder = JqlQueryBuilder.newClauseBuilder()

            Query query = jqlBuilder.project("CNT").and().customField(cf.getIdAsLong()).like(barcode).buildQuery()
            LOG.debug "Query : ${query.toString()}"

            SearchProvider searchProvider = ComponentAccessor.getComponent(SearchProvider.class)
            SearchResults searchResults = searchProvider.search(query, getLoggedInUser(), PagerFilter.getUnlimitedFilter())
            List<Issue> issues = searchResults.getIssues()

            if(1 == issues.size()) {
                issue = issues[0]
            } else {
                if(0 == issues.size()) {
                    LOG.error "Barcode <${barcode}> does not match any container in Jira"
                } else {
                    LOG.error "Barcode <${barcode}> returns more than one issue"
                }
            }

        } catch (Exception e) {
            LOG.error "Error fetching issue for Barcode <${barcode}>"
            LOG.error e.printStackTrace()
        }
        issue
    }

    /**
     * Fetch the array of selected issue ids from an nFeed select field.
     *
     * @param curIssue
     * @param cfAlias
     * @return
     */
    public static ArrayList<String> getIssueIdsFromNFeedField(Issue curIssue, String cfAlias) {

        LOG.debug "Attempting to fetch custom field with alias name ${cfAlias}".toString()
        def customFieldManager = ComponentAccessor.getCustomFieldManager()
        def customField =  customFieldManager.getCustomFieldObject(ConfigReader.getCFId(cfAlias))

        ArrayList<String> ids = []

        if(customField != null) {
            // the value of the nFeed field varies depending on if deprecated or current type
            // the deprecated type returns a list of long issue ids
            // the current type returns an XML with structure <content><value>12345</value>...</content>
            String nFeedValueAsString = curIssue.getCustomFieldValue(customField)
            LOG.debug "nFeed field return value = ${nFeedValueAsString}"

            if(nFeedValueAsString?.trim()) {
                if(nFeedValueAsString.startsWith('<')) {
                    LOG.debug "nFeed field is returning XML, parsing to get ids"
                    GPathResult xmlContent = new XmlSlurper().parseText(nFeedValueAsString)
                    xmlContent.value.each { node ->
                        String id = node.text()
                        LOG.debug "Found node text: ${id}".toString()
                        ids.add(id)
                    }
                } else if(nFeedValueAsString.startsWith('[')) {
                    LOG.debug "nFeed field is returning array of ids, parsing to get ids"
                    String[] arrayIds = curIssue.getCustomFieldValue(customField)
                    arrayIds.each { String idString ->
                        ids.add(idString)
                    }
                } else {
                    LOG.error "nFeed field is returning unexpected data type, cannot parse"
                }
            } else {
                // if user hasn't selected anything do nothing further
                LOG.debug("No issues selected from nFeed field with alias name <${cfAlias}>, so nothing to do")
            }
        } else {
            LOG.error("Custom field not found for nFeed custom field alias <${cfAlias}>")
        }

        LOG.debug "Returning ids : ${ids.toString()}".toString()
        ids
    }
}
