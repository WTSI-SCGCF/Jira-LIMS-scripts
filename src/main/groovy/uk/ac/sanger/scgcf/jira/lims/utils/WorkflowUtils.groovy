package uk.ac.sanger.scgcf.jira.lims.utils

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.IssueTypeManager
import com.atlassian.jira.exception.CreateException
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueInputParameters
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.issuetype.IssueType
import com.atlassian.jira.issue.link.IssueLink
import com.atlassian.jira.issue.link.IssueLinkManager
import com.atlassian.jira.issue.link.IssueLinkType
import com.atlassian.jira.issue.link.IssueLinkTypeManager
import com.atlassian.jira.issue.search.SearchProvider
import com.atlassian.jira.issue.search.SearchResults
import com.atlassian.jira.jql.builder.JqlClauseBuilder
import com.atlassian.jira.jql.builder.JqlQueryBuilder
import com.atlassian.jira.security.JiraAuthenticationContext
import com.atlassian.jira.web.bean.PagerFilter
import com.atlassian.jira.workflow.JiraWorkflow
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.query.Query
import groovy.util.logging.Slf4j
import groovy.util.slurpersupport.GPathResult
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueLinkTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueResolutionName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.enums.IssueTypeName
import uk.ac.sanger.scgcf.jira.lims.enums.SelectOptionId
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.LabelTemplates
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.PrintLabelAction
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
    public
    static void removePlatesFromGivenGrouping(PlateActionParameterHolder plateActionParams, List<String> fieldNamesToClear) {

        executePlateAction(
                plateActionParams,
                { Issue mutableIssue ->
                    removeIssueLink(plateActionParams.currentIssue.getId(), mutableIssue.getId(), plateActionParams.linkTypeName)
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

        // process each plate id in the list
        plateActionParams.plateIds.each { String plateIdString ->

            Long plateIdLong = Long.parseLong(plateIdString)

            // use the message closure to print a log
            LOG.debug((String) messageClosure(plateIdString))

            // get the issue for the plate id and check not null
            MutableIssue mutableIssue = getMutableIssueForIssueId(plateIdLong)
            if (mutableIssue != null) {
                LOG.debug("Issue type = ${mutableIssue.getIssueType().getName()} and plate action issue type = ${plateActionParams.issueTypeName}")
            } else {
                LOG.error("ERROR: Plate issue null for Id <${plateIdString}>, cannot process plate action")
            }

            // check the issue type of the plate matches to the expected type passed in the parameters
            if (mutableIssue != null) {
                if (mutableIssue.getIssueType().getName() == plateActionParams.issueTypeName) {
                    LOG.debug "Plate issue type name matches expected <${plateActionParams.issueTypeName}>, attempting to process action"

                    // process the action closure
                    actionToExecute(mutableIssue)

                    LOG.debug "Returned from processing plate action, now checking if plate needs to be transitioned"

                    // get the plate status
                    String issueStatusName = mutableIssue.getStatus().getName()
                    LOG.debug "Status name of plate issue = ${issueStatusName}"

                    LOG.debug "Contents of transition map:"
                    plateActionParams.statusToTransitionMap.each{ k, v -> LOG.debug "${k}:${v}" }

                    // if the plate status matches a key in the parameter transitions map, transition the plate issue state
                    if (plateActionParams.statusToTransitionMap.keySet().contains(issueStatusName)) {

                        LOG.debug "Found matching status in transition map"
                        String transitionName = plateActionParams.statusToTransitionMap.get(issueStatusName)

                        // attempt to get the transition id from the configs
                        LOG.debug("Attempting to fetch action ID for workflow ${plateActionParams.plateWorkflowName} and transition name $transitionName")
                        int actionId = ConfigReader.getTransitionActionId(plateActionParams.plateWorkflowName, transitionName)
                        LOG.debug("Action ID = ${actionId}")

                        if(actionId > 0) {
                            // attempt to transition the issue
                            transitionIssue(mutableIssue.getId(), actionId, "Automatically transitioned plate action")
                        } else {
                            LOG.error "ERROR: unable to execute transition, action Id not found in configs for workflow <${plateActionParams.plateWorkflowName}> and transition <${transitionName}>"
                        }
                    }
                } else {
                    LOG.error("ERROR: Unexpected issue type <${mutableIssue.getIssueType().getName()}>, expected <${plateActionParams.issueTypeName}>, cannot process plate action.")
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

        LOG.debug "arrayReagentIds = ${listReagentIds.toListString()}"
        listReagentIds.each { String reagentIdString ->
            LOG.debug "reagentIdString = ${reagentIdString}"

            Long reagentIdLong = Long.parseLong(reagentIdString)
            LOG.debug "Attempting to link reagent with ID $reagentIdString to issue with ID ${curIssue.id}"

            MutableIssue reagentMutableIssue = getMutableIssueForIssueId(reagentIdLong)

            if (reagentMutableIssue != null && reagentMutableIssue.getIssueType().getName() == IssueTypeName.REAGENT_LOT_OR_BATCH.toString()) {
                LOG.debug "Calling link function in WorkflowUtils to link reagent to issue with ID ${curIssue.id}"
                createIssueLink(curIssue.getId(), reagentMutableIssue.getId(), IssueLinkTypeName.USES_REAGENT.toString())

            } else {
                LOG.error "Reagent issue null or unexpected issue type when linking reagent with ID ${reagentIdString} to issue with ID ${curIssue.id}"
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
        LOG.debug "Using logged in User <${user.getName()}> with Id <${user.getId()}>"
        user
    }

    /**
     * Get the automation application user
     *
     * @return user
     */
    public static ApplicationUser getAutomationUser() {

        // TODO: get this name from the configs
        String automationUserName = 'jira_automation_user'
        ApplicationUser user = ComponentAccessor.getUserManager().getUserByName(automationUserName)
        LOG.debug "Using Automation User <${user.getName()}> with Id <${user.getId()}>"
        user
    }

    /**
     * Transitions an issue through the specified transition action
     *
     * @param issueId the Id of the issue to be transitioned
     * @param actionId the transition action id
     * @param transitionComment issue comment for transition
     * @return the error collection if the validation or transition fail, otherwise nothing
     */
    public static void transitionIssue(Long issueId, int actionId, String transitionComment) {

        LOG.debug "Attempting to transition issue with Id <${issueId}> through transition with action Id <${actionId}>"

        // set up the transition
        ApplicationUser user = getLoggedInUser()

        // set up any parameters
        IssueService issueService = ComponentAccessor.getIssueService()
        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters()
        issueInputParameters.setAssigneeId(user.getName())
        if(!transitionComment?.trim()) {
            transitionComment = "Automatically transitioned by script."
        }
        issueInputParameters.setComment(transitionComment)
        issueInputParameters.setSkipScreenCheck(true)
//        issueInputParameters.setRetainExistingValuesWhenParameterNotProvided(true, true)

        // validate the transition
        IssueService.TransitionValidationResult transitionValidationResult
        transitionValidationResult = issueService.validateTransition(user, issueId, actionId, issueInputParameters)
        if (transitionValidationResult.isValid()) {

            LOG.debug "Transition of issue with id <${issueId}> via action id <${actionId}> is valid, attempting to transition issue"

            // check for warnings
            if(transitionValidationResult.getWarningCollection() != null && transitionValidationResult.getWarningCollection().hasAnyWarnings()) {
                LOG.debug "WARNING: Transition of issue with id <${issueId}> via action id <${actionId}> is valid but had warnings: {}\n", transitionValidationResult.getWarningCollection().toString()
            }

            // perform the transition
            IssueService.IssueResult transitionResult = issueService.transition(user, transitionValidationResult)

            // check the transition happened
            if (transitionResult.isValid()) {
                // the transition succeeded
                LOG.debug "Issue with id <${issueId}> has been successfully transitioned via action id <${actionId}>."

                // check for warnings
                if(transitionResult.getWarningCollection() != null && transitionResult.getWarningCollection().hasAnyWarnings()) {
                    LOG.warn "WARNING: Issue with id <${issueId}> was transitioned via action id <${actionId}> but there were warnings: {}\n", transitionResult.getWarningCollection().toString()
                }

            } else {
                // the transition failed
                LOG.error("ERROR: Issue with id <${issueId}> has NOT been transitioned via action id <${actionId}>. Result errors: {}\n", transitionValidationResult.getErrorCollection().toString())
            }

        } else {
            // the transition was not valid
            LOG.error("ERROR: Issue with id <${issueId}> has NOT been transitioned via action id <${actionId}>. Validation errors: {}\n", transitionValidationResult.getErrorCollection().toString())
        }

    }

    /**
     * Identify the transition action id and transition the issue
     *
     * @param issueId
     * @param workflowAliasName
     * @param transitionAliasName
     */
    public static void getTransitionActionIdAndTransitionIssue(Long issueId, String workflowAliasName, String transitionAliasName) {

        LOG.debug "Attempting to transition issue with id <${issueId}>, workflow alias name <${workflowAliasName}> through transition alias name <${transitionAliasName}>"

        int transitionActionId = ConfigReader.getTransitionActionId(workflowAliasName, transitionAliasName)

        LOG.debug "Transition action id = ${transitionActionId}"

        if(transitionActionId > 0) {
            transitionIssue(issueId, transitionActionId, "Automatically transitioned by workflow alias ${workflowAliasName} through transition alias ${transitionAliasName}")
        } else {
            LOG.error "ERROR: Transition action id not found in transition configs, cannot transition issue"
        }
    }

    /**
     * Create a reciprocal issue link of the specified link type between two specified issues
     *
     * @param sourceIssueId the issue Id that is the source of the link
     * @param destinationIssueId the issue Id that is the destination of the link
     * @param linkTypeName the name of the issue link type (the main name rather than the inward/outward names)
     */
    public static void createIssueLink(Long sourceIssueId, Long destinationIssueId, String linkTypeName) {

        // get the link type
        IssueLinkType issueLinkType = getIssueLinkType(linkTypeName)

        if (issueLinkType == null) {
            LOG.error "ERROR: Failed to link source issue with id <${sourceIssueId}> to destination issue with id <${destinationIssueId}> with link type <${linkTypeName}>"
            LOG.error "ERROR: issue link type name not recognised <${linkTypeName}>"
            return
        }

        // determine the user
        ApplicationUser user = getLoggedInUser()

        // link the issues together (will create a reciprocal link)
        IssueLinkManager issLinkManager = ComponentAccessor.getIssueLinkManager()

        LOG.debug "Attempting to link two issues:"
        LOG.debug "Source issue id = ${sourceIssueId}"
        LOG.debug "Destination issue id = ${destinationIssueId}"
        LOG.debug "Link name = ${linkTypeName} and id = ${issueLinkType.getId()}"

        // throws a CreateException if it fails
        // (Long sourceIssueId, Long destinationIssueId, Long issueLinkTypeId, Long sequence, ApplicationUser remoteUser)
        try {
            issLinkManager.createIssueLink(sourceIssueId, destinationIssueId, issueLinkType.getId(), null, user)
            LOG.debug "Successfully linked source issue to destination issue"
        } catch (CreateException e) {
            LOG.error "ERROR: Failed to link source issue with id <${sourceIssueId}> to destination issue with id <${destinationIssueId}> with link type <${linkTypeName}>"
            LOG.error e.message
        }

    }

    /**
     * Remove a reciprocal issue link of the specified type between two specified issues
     *
     * @param sourceIssue the issue that is the source of the link
     * @param destinationIssue the issue that is the destination of the link
     * @param linkTypeName the name of the issue link type
     */
    public static void removeIssueLink(Long sourceIssueId, Long destinationIssueId, String linkTypeName) {

        // get the link type
        IssueLinkType issueLinkType = getIssueLinkType(linkTypeName)

        if (issueLinkType == null) {
            LOG.error "ERROR: Failed to remove link between source issue with id <${sourceIssueId}> to destination issue with id <${destinationIssueId}> with link type <${linkTypeName}>"
            LOG.error "ERROR: issue link type name not recognised <${linkTypeName}>"
            return
        }

        // determine the user
        ApplicationUser user = getLoggedInUser()

        // remove the link between the plate issue and the submission issue
        IssueLinkManager issueLinkManager = ComponentAccessor.getIssueLinkManager()
        IssueLink issueLink = issueLinkManager.getIssueLink(sourceIssueId, destinationIssueId, issueLinkType.id)

        // throws IllegalArgumentException if the specified issueLink is null
        try {
            issueLinkManager.removeIssueLink(issueLink, user)
        } catch (IllegalArgumentException e) {
            LOG.error "ERROR: IllegalArgumentException: Failed to remove link between source issue with id <${sourceIssueId}> to destination issue with id <${destinationIssueId}> with link type <${linkTypeName}>"
            LOG.error e.getMessage()
        }

    }

    /**
     * Get the issue link type for a named issue link
     *
     * @param linkName
     * @return
     */
    public static IssueLinkType getIssueLinkType(String linkTypeName) {

        // determine the issue link type
        IssueLinkTypeManager issueLinkTypeManager = ComponentAccessor.getComponent(IssueLinkTypeManager.class)
        IssueLinkType issLnkType = issueLinkTypeManager.getIssueLinkTypesByName(linkTypeName).iterator().next()

        LOG.debug "Issue link type name = ${issLnkType.getName()}"
        LOG.debug "Issue link type inward = ${issLnkType.inward}"
        LOG.debug "Issue link type outward = ${issLnkType.outward}"

        issLnkType

    }

    /**
     * Get the list of inward issue links for an issue id
     *
     * @param issueId
     * @return list of IssueLinks
     */
    public static List<IssueLink> getInwardLinksListForIssueId(Long issueId) {
        LOG.debug "Fetching Inward links for issue with Id <${issueId.toString()}>"
        IssueLinkManager issLnkMngr = ComponentAccessor.getIssueLinkManager()
        List<IssueLink> inwardLinksList = issLnkMngr.getInwardLinks(issueId)
        LOG.debug "Found <${inwardLinksList.size()}> inwards links"

        inwardLinksList
    }

    /**
     * Get the list of outward links for an issue id
     *
     * @param issueId
     * @return list of IssueLinks
     */
    public static List<IssueLink> getOutwardLinksListForIssueId(Long issueId) {
        LOG.debug "Fetching Outward links for issue with Id <${issueId.toString()}>"
        IssueLinkManager issLnkMngr = ComponentAccessor.getIssueLinkManager()
        List<IssueLink> outwardLinksList = issLnkMngr.getOutwardLinks(issueId)
        LOG.debug "Found <${outwardLinksList.size()}> outwards links"

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
     * @param fieldAliasNamesToClear
     * @param issue
     */
    private static void clearFieldsValue(List<String> fieldAliasNamesToClear, Issue issue) {
        fieldAliasNamesToClear.each { fieldAliasName ->
            JiraAPIWrapper.setCFValueByName(issue, ConfigReader.getCFName(fieldAliasName), null)
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
        if (!barcode?.trim()) {
            LOG.error "Barcode not present, cannot continue"
            return null
        }

        Issue issue = null

        try {
            CustomField cf = JiraAPIWrapper.getCFByName(ConfigReader.getCFName("BARCODE"))
            JqlClauseBuilder jqlBuilder = JqlQueryBuilder.newClauseBuilder()

            Query query = jqlBuilder.project("CNT").and().customField(cf.getIdAsLong()).like(barcode).buildQuery()
            LOG.debug "Query : ${query.toString()}"

            SearchProvider searchProvider = ComponentAccessor.getComponent(SearchProvider.class)
            SearchResults searchResults = searchProvider.search(query, getLoggedInUser(), PagerFilter.getUnlimitedFilter())
            List<Issue> issues = searchResults.getIssues()

            if (1 == issues.size()) {
                issue = issues[0]
            } else {
                if (0 == issues.size()) {
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
     * N.B. Handles both deprecated and new nFeed fields
     *
     * @param curIssue
     * @param cfAlias
     * @return
     */
    public static ArrayList<String> getIssueIdsFromNFeedField(Issue curIssue, String cfAlias) {

        LOG.debug "Attempting to fetch custom field with alias name ${cfAlias}".toString()
        def customFieldManager = ComponentAccessor.getCustomFieldManager()
        def customField = customFieldManager.getCustomFieldObject(ConfigReader.getCFId(cfAlias))

        ArrayList<String> ids = []

        if (customField != null) {
            // the value of the nFeed field varies depending on if deprecated or current type
            // the deprecated type returns a list of long issue ids
            // the current type returns an XML with structure <content><value>12345</value>...</content>
            String nFeedValueAsString = curIssue.getCustomFieldValue(customField)
            LOG.debug "nFeed field return value = ${nFeedValueAsString}"

            if (nFeedValueAsString?.trim()) {
                if (nFeedValueAsString.startsWith('<')) {
                    LOG.debug "nFeed field is returning XML, parsing to get ids"
                    GPathResult xmlContent = new XmlSlurper().parseText(nFeedValueAsString)
                    xmlContent.value.each { node ->
                        String id = node.text()
                        LOG.debug "Found node text: ${id}".toString()
                        ids.add(id)
                    }
                } else if (nFeedValueAsString.startsWith('[')) {
                    LOG.debug "nFeed field is returning array of ids, parsing to get ids"
                    String[] arrayIds = curIssue.getCustomFieldValue(customField)
                    arrayIds.each { String idString ->
                        ids.add(idString)
                    }
                } else if (nFeedValueAsString ==~ /\d+/) {
                    LOG.debug "nFeed field is returning single id"
                    ids.add(nFeedValueAsString)
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

    /**
     * Get a list of any container issues linked to the input group issue
     *
     * @param groupIssue - the group issue with linked containers
     * @return a list of container issues
     */
    public static List<Issue> getContainersLinkedToGroup(Issue groupIssue) {

        LOG.debug "Getting containers linked to the group issue with Id = <${groupIssue.getId().toString()}> and Key = <${groupIssue.getKey()}>"

        // group to plate link type
        IssueLinkType groupToContainerLinkType = getIssueLinkType(IssueLinkTypeName.GROUP_INCLUDES.toString())

        // get the outward linked issues from the group issue
        List<IssueLink> outwardLinksList = getOutwardLinksListForIssueId(groupIssue.getId())

        LOG.debug "Found <${outwardLinksList.size()}> issues linked to the group issue"

        List<Issue> contIssueList = []

        // check for linked containers
        outwardLinksList.each { IssueLink outwardIssueLink ->

            Issue curLinkedIssue = outwardIssueLink.getDestinationObject()

            if (outwardIssueLink.getIssueLinkType() == groupToContainerLinkType) {
                LOG.debug "Found linked container issue:"
                LOG.debug "Issue type = <${curLinkedIssue.getIssueType().getName()}>"
                LOG.debug "Status = <${curLinkedIssue.getStatus().getName()}>"
                LOG.debug "Id = <${curLinkedIssue.getId().toString()}>"
                LOG.debug "Key = <${curLinkedIssue.getKey()}>"
                LOG.debug "Summary = <${curLinkedIssue.getSummary()}>"

                contIssueList.add(curLinkedIssue)
            }
        }
        contIssueList

    }

    /**
     * Get a list of any parent container issues linked to the input container issue
     *
     * @param sourceContainerId - the source container issue id
     * @return a list of parent container issues
     */
    public static List<Issue> getParentContainersForContainerId(Long sourceContainerId) {

        if(sourceContainerId == null) {
            LOG.error "ERROR: Source issue null on getting parent containers, cannot continue"
            return null
        }

        LOG.debug "Getting parent containers linked to the source container issue with Id = <${sourceContainerId.toString()}>"

        // plate to plate link type
        IssueLinkType containerToContainerLinkType = getIssueLinkType(IssueLinkTypeName.RELATIONSHIPS.toString())

        // determine the ancestors of the source issue
        List<IssueLink> inwardsLinksList = getInwardLinksListForIssueId(sourceContainerId)

        LOG.debug "Found <${inwardsLinksList.size()}> issues linked to the source container issue"

        List<Issue> contIssueList = []

        // check for linked containers
        inwardsLinksList.each { IssueLink issLink ->

            // get the source of the link, i.e. the ancestor (or parent) issue
            Issue curLinkedIssue = issLink.getSourceObject()
            LOG.debug "Ancestor issue link type name = ${issLink.getIssueLinkType().getName()}"

            // we only want linked containers, not other types of issues
            if (issLink.getIssueLinkType() == containerToContainerLinkType) {

                LOG.debug "Found parent container issue:"
                LOG.debug "Issue type = <${curLinkedIssue.getIssueType().getName()}>"
                LOG.debug "Status = <${curLinkedIssue.getStatus().getName()}>"
                LOG.debug "Id = <${curLinkedIssue.getId().toString()}>"
                LOG.debug "Key = <${curLinkedIssue.getKey()}>"
                LOG.debug "Summary = <${curLinkedIssue.getSummary()}>"

                contIssueList.add(curLinkedIssue)
            }
        }
        contIssueList

    }

    /**
     * Get a list of any child container issues linked to the input container issue
     *
     * @param sourceContainerId - the source container issue id
     * @return a list of child container issues
     */
    public static List<Issue> getChildContainersForContainerId(Long sourceContainerId) {

        if(sourceContainerId == null) {
            LOG.error "ERROR: Source issue id null on getting child containers, cannot continue"
            return null
        }

        LOG.debug "Getting child containers linked to the source container issue with Id = <${sourceContainerId.toString()}>"

        // container to container link type
        IssueLinkType containerToContainerLinkType = getIssueLinkType(IssueLinkTypeName.RELATIONSHIPS.toString())

        // determine the descendants of the source issue
        List<IssueLink> outwardsLinksList = getOutwardLinksListForIssueId(sourceContainerId)

        LOG.debug "Found <${outwardsLinksList.size()}> issues linked to the source container issue"

        List<Issue> contIssueList = []

        // check for linked containers
        outwardsLinksList.each { IssueLink issLink ->

            // get the destination of the link, i.e. the descendant (or child) issue
            Issue curLinkedIssue = issLink.getDestinationObject()
            LOG.debug "Descendant issue link type name = ${issLink.getIssueLinkType().getName()}"

            // we only want linked containers, not other types of issues
            if (issLink.getIssueLinkType() == containerToContainerLinkType) {

                LOG.debug "Found child container issue:"
                LOG.debug "Issue type = <${curLinkedIssue.getIssueType().getName()}>"
                LOG.debug "Status = <${curLinkedIssue.getStatus().getName()}>"
                LOG.debug "Id = <${curLinkedIssue.getId().toString()}>"
                LOG.debug "Key = <${curLinkedIssue.getKey()}>"
                LOG.debug "Summary = <${curLinkedIssue.getSummary()}>"

                contIssueList.add(curLinkedIssue)
            }
        }
        contIssueList

    }

    /**
     * Print plate labels from the specified printer for the given number of plates with the supplied info type
     *
     * @param sourceIssue
     * @param bcInfoType
     */
    public static void printPlateLabels(Issue sourceIssue, String bcInfoType) {

        String printerName = JiraAPIWrapper.getCFValueByName(sourceIssue, ConfigReader.getCFName("PRINTER_FOR_PLATE_LABELS"))
        int numberOfLabels
        try {
            numberOfLabels = Double.valueOf(JiraAPIWrapper.getCFValueByName(sourceIssue, ConfigReader.getCFName("NUMBER_OF_PLATES"))).intValue()
        } catch (NumberFormatException e) {
            LOG.error "Number format exception, cannot determine number of plates"
            LOG.error(e.getMessage())
            numberOfLabels = 1
        }

        def labelTemplate = LabelTemplates.LABEL_STANDARD_6MM_PLATE
        def labelData = [:]

        LOG.debug "Attempting to print <${numberOfLabels.toString()}> type <${bcInfoType}> barcode labels from printer <${printerName}>"

        PrintLabelAction printLabelAction =
                new PrintLabelAction(printerName, numberOfLabels, labelTemplate, labelData, bcInfoType)
        printLabelAction.execute()

    }

    /**
     * Create a new Issue using the input user and parameters
     *
     * @param issueInputParameters
     * @return
     */
    public static Issue createIssue(IssueService issueService, ApplicationUser createUser, IssueInputParameters issueInputParameters) {

        LOG.debug "Creating a new issue with input parameters:"
        LOG.debug "Summary = ${issueInputParameters.getSummary()}"

        Issue createdIssue = null

        if(createUser != null) {
            LOG.debug "Attempting to validate creation of issue using user <${createUser.getName()}>"

            // N.B. there is a bug here whereby the user you want to create the issue MUST be set as the logged in user first
            ApplicationUser currUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
            ComponentAccessor.getJiraAuthenticationContext().setLoggedInUser(createUser)
            IssueService.CreateValidationResult createValidationResult = issueService.validateCreate(createUser, issueInputParameters)

            if (createValidationResult.isValid()) {

                // check for warnings
                if (createValidationResult.getWarningCollection() != null && createValidationResult.getWarningCollection().hasAnyWarnings()) {
                    LOG.debug "WARNING: Creation of issue is valid but had warnings: {}\n", createValidationResult.getWarningCollection().toString()
                }

                LOG.debug "Attempting to create issue"
                IssueService.IssueResult createResult = issueService.create(createUser, createValidationResult)
                if (createResult.isValid()) {

                    createdIssue = createResult.getIssue()
                    LOG.debug "Issue created successfully with summary <${createdIssue.getSummary()}> and key <${createdIssue.getKey()}>"

                    // check for warnings
                    if (createResult.getWarningCollection() != null && createResult.getWarningCollection().hasAnyWarnings()) {
                        LOG.debug "WARNING: Creation of issue with id <${createdIssue.getId().toString()}> was successful but had warnings: {}\n", createResult.getWarningCollection().toString()
                    }
                } else {
                    // TODO: what to do when failed to create issue? post function so limited options
                    LOG.error "Failed to create issue. Errors:"

                    // check for errors
                    if (createResult.getErrorCollection() != null && createResult.getErrorCollection().hasAnyErrors()) {
                        LOG.debug "WARNING: Creation of issue failed with errors: {}\n", createResult.getErrorCollection().toString()
                    }
                }
            } else {
                LOG.error "Create issue failed validation. Errors:"

                // check for errors
                if (createValidationResult.getErrorCollection() != null && createValidationResult.getErrorCollection().hasAnyErrors()) {
                    LOG.debug "WARNING: Creation of issue failed validation with errors: {}\n", createValidationResult.getErrorCollection().toString()
                }
            }
            // reset current user
            ComponentAccessor.getJiraAuthenticationContext().setLoggedInUser(currUser)

        } else {
            LOG.error "Create issue failed because automationUser was null"
        }
        createdIssue
    }

    /**
     * Finds an issue type by it's name
     *
     * @param name
     * @return
     */
    public static IssueType getIssueTypeByName(String name)
    {
        if(name == null || name.isEmpty())
        {
            return null
        }

        IssueType foundIssueType = null

        IssueTypeManager itMgr = ComponentAccessor.getComponent(IssueTypeManager.class)
        for(IssueType it : itMgr.getIssueTypes())
        {
            if(it.getName().equals(name))
            {
                foundIssueType = it
                break
            }
        }

        return foundIssueType
    }

    /**
     * Get the Submission issue linked to the source issue
     *
     * @param sourceContainerId
     * @return an issue or null if not found
     */
    public static Issue getSubmissionIssueForContainerId(Long sourceContainerId) {

        LOG.debug "Getting Submission issue linked to the source container issue with Id = <${sourceContainerId.toString()}>"

        List<Issue> linkedIssues = getSpecifiedLinkedIssues(sourceContainerId, IssueLinkTypeName.GROUP_INCLUDES.toString(), IssueTypeName.SUBMISSION.toString())

        if(linkedIssues == null || linkedIssues.size() <= 0) {
            LOG.error "No linked Submission issues found"
            return null
        }

        // assume only one
        linkedIssues.get(0)

    }

    /**
     * Get the IQC issue linked to the source issue
     *
     * @param sourceContainerId
     * @return an issue or null if not found
     */
    public static Issue getIQCIssueForContainerId(Long sourceContainerId) {

        LOG.debug "Getting IQC issue linked to the source container issue with Id = <${sourceContainerId.toString()}>"

        List<Issue> linkedIssues = getSpecifiedLinkedIssues(sourceContainerId, IssueLinkTypeName.GROUP_INCLUDES.toString(), IssueTypeName.INPUT_QC.toString())

        if(linkedIssues == null || linkedIssues.size() <= 0) {
            LOG.error "No linked IQC issues found"
            return null
        }

        // could be more than one IQC issue if IQC repeated for plate, get first with correct state / resolution
        if(linkedIssues.size() > 1) {
            LOG.debug "Found multiple IQC issues, checking state and resolution"
            linkedIssues.each { Issue iqcIssue ->
                // look for state 'IQC Done' and resolution 'Completed'
                if(iqcIssue.getStatus().getName() == IssueStatusName.IQC_DONE.toString()) {
                    if(iqcIssue.getResolution().getName() == IssueResolutionName.COMPLETED.toString()) {
                        return iqcIssue
                    }
                }
            }
        }
        linkedIssues.get(0)
    }

    /**
     * Get the QNTA issue linked to the source issue
     *
     * @param sourceContainerId
     * @return an issue or null if not found
     */
    public static Issue getQuantAnalysisIssueForContainerId(Long sourceContainerId) {

        LOG.debug "Getting QNT Analysis issue linked to the source container issue with Id = <${sourceContainerId.toString()}>"

        List<Issue> linkedIssues = getSpecifiedLinkedIssues(sourceContainerId, IssueLinkTypeName.GROUP_INCLUDES.toString(), IssueTypeName.QUANTIFICATION_ANALYSIS.toString())

        if(linkedIssues == null || linkedIssues.size() <= 0) {
            LOG.error "No linked Quantification Analysis issues found"
            return null
        }

        // could be more than one QNTA issue if Quant repeated for plate, return first with correct state / resolution
        if(linkedIssues.size() > 1) {
            LOG.debug "Found multiple QNTA issues, checking state and resolution"
            linkedIssues.each { Issue qntaIssue ->
                // look for state 'QNTA Done' and resolution 'Completed'
                if(qntaIssue.getStatus().getName() == IssueStatusName.QNTA_DONE.toString()) {
                    if(qntaIssue.getResolution().getName() == IssueResolutionName.COMPLETED.toString()) {
                        return qntaIssue
                    }
                }
            }
        }
        linkedIssues.get(0)
    }

    /**
     * Generic method to get linked issues for a source issue with a specific link type and issue type name
     *
     * @param sourceIssueId
     * @param linkTypeName
     * @param issueTypeName
     * @return a list of issues
     */
    public static List<Issue> getSpecifiedLinkedIssues(Long sourceIssueId, String linkTypeName, String issueTypeName) {

        if(sourceIssueId == null) {
            LOG.error "ERROR: Source issue null for getSpecifiedLinkedIssues, cannot continue"
            return null
        }

        if(linkTypeName == null) {
            LOG.error "ERROR: Link type name null for getSpecifiedLinkedIssues, cannot continue"
            return null
        }

        if(issueTypeName == null) {
            LOG.error "ERROR: Issue type name null for getSpecifiedLinkedIssues, cannot continue"
            return null
        }

        LOG.debug "Looking for linked issues with type <${issueTypeName}> and link type <${linkTypeName}> for source issue with Id = <${sourceIssueId.toString()}>"

        // link type
        IssueLinkType linkType = getIssueLinkType(linkTypeName)
        if(linkType == null) {
            LOG.error "ERROR: Link type null for getSpecifiedLinkedIssues, cannot continue"
            return null
        }

        // fetch all the inward links for the source issue
        List<IssueLink> inwardsLinksList = getInwardLinksListForIssueId(sourceIssueId)

        LOG.debug "Found <${inwardsLinksList.size()}> issues linked to the source container issue"

        List<Issue> contIssueList = []

        // check for linked containers
        inwardsLinksList.each { IssueLink issLink ->

            // get the source of the link, i.e. the ancestor (or parent) issue
            Issue curLinkedIssue = issLink.getSourceObject()
            LOG.debug "Ancestor issue link type name = ${issLink.getIssueLinkType().getName()}"

            // we only want issues linked in the specific way
            if (issLink.getIssueLinkType() == linkType) {

                // we only want issues of the specified issue type
                if(issLink.getSourceObject().getIssueType().getName() == issueTypeName) {

                    LOG.debug "Found linked issue with correct link and issue types:"
                    LOG.debug "Issue type = <${curLinkedIssue.getIssueType().getName()}>"
                    LOG.debug "Status = <${curLinkedIssue.getStatus().getName()}>"
                    LOG.debug "Id = <${curLinkedIssue.getId().toString()}>"
                    LOG.debug "Key = <${curLinkedIssue.getKey()}>"
                    LOG.debug "Summary = <${curLinkedIssue.getSummary()}>"

                    contIssueList.add(curLinkedIssue)
                }
            }
        }
        if(contIssueList.size() <= 0) {
            LOG.warn "Warning: No linked issues of specified type found"
            return null
        }
        LOG.debug "Found <${contIssueList.size()}> issues of specified type linked to source issue"
        contIssueList

    }

    /**
     * Generic function to round a number to specified number of digits.
     * usage: round(yourNumber, 3, BigDecimal.ROUND_HALF_UP)
     *
     * @param unrounded
     * @param precision
     * @param roundingMode
     * @return
     */
    public static double round(double unrounded, int precision, int roundingMode)
    {
        BigDecimal bd = new BigDecimal(unrounded)
        BigDecimal rounded = bd.setScale(precision, roundingMode)
        return rounded.doubleValue()
    }

    /**
     * Get the IQC Outcome option Id value given the issue
     *
     * @param curIssue
     * @return
     */
    public static String getIQCOutcomeOptionId(Issue curIssue) {

        String sIqcOutcomeOptId
        String sIqcOutcome = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("IQC_OUTCOME"))
        LOG.debug "IQC Outcome = ${sIqcOutcome}"
        if(sIqcOutcome == 'Pass') {
            sIqcOutcomeOptId = SelectOptionId.IQC_OUTCOME_PASS.toString()
        } else if(sIqcOutcome == 'Fail') {
            sIqcOutcomeOptId = SelectOptionId.IQC_OUTCOME_FAIL.toString()
        } else {
            sIqcOutcomeOptId = '-1'
        }
        LOG.debug "IQC Outcome option Id = ${sIqcOutcomeOptId}"
        sIqcOutcomeOptId

    }

    /**
     * Get the IQC Feedback option Id value given the issue
     *
     * @param curIssue
     * @return
     */
    public static String getIQCFeedbackOptionId(Issue curIssue) {

        String sIqcFeedbackOptId
        String sIqcFeedback = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("IQC_FEEDBACK"))
        LOG.debug "IQC Feedback = ${sIqcFeedback}"
        if(sIqcFeedback == 'Pass') {
            sIqcFeedbackOptId = SelectOptionId.IQC_FEEDBACK_PASS.toString()
        } else if(sIqcFeedback == 'Fail') {
            sIqcFeedbackOptId = SelectOptionId.IQC_FEEDBACK_FAIL.toString()
        } else {
            sIqcFeedbackOptId = '-1'
        }
        LOG.debug "IQC Feedback option Id = ${sIqcFeedbackOptId}"
        sIqcFeedbackOptId

    }

    /**
     * Get the SBM Cells per Library Pool option Id value given the issue
     *
     * @param iqcIssue
     * @return
     */
    public static String getSBMCellsPerPoolOptionId(Issue curIssue) {

        String sSBMCellsPerPoolOptId
        String sSBMCellsPerPool = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("CELLS_PER_LIBRARY_POOL"))
        LOG.debug "SBM Cells per Library Pool = ${sSBMCellsPerPool}"
        if(sSBMCellsPerPool == '96') {
            sSBMCellsPerPoolOptId = SelectOptionId.SBM_CELLS_PER_LIBRARY_POOL_96.toString()
        } else if(sSBMCellsPerPool == '384') {
            sSBMCellsPerPoolOptId = SelectOptionId.SBM_CELLS_PER_LIBRARY_POOL_384.toString()
        } else {
            sSBMCellsPerPoolOptId = '-1'
        }
        LOG.debug "SBM Cells per Library Pool option Id = ${sSBMCellsPerPoolOptId}"
        sSBMCellsPerPoolOptId

    }

    /**
     * Get the SBM Plate Format option Id value given the issue
     *
     * @param iqcIssue
     * @return
     */
    public static String getSBMPlateFormatOptionId(Issue curIssue) {

        String sSBMPlateFormatOptId
        String sSBMPlateFormat = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("PLATE_FORMAT"))
        LOG.debug "SBM Plate Format = ${sSBMPlateFormat}"
        if(sSBMPlateFormat == '96') {
            sSBMPlateFormatOptId = SelectOptionId.SBM_PLATE_FORMAT_96.toString()
        } else if(sSBMPlateFormat == '384') {
            sSBMPlateFormatOptId = SelectOptionId.SBM_PLATE_FORMAT_384.toString()
        } else {
            sSBMPlateFormatOptId = '-1'
        }
        LOG.debug "SBM Plate Format option Id = ${sSBMPlateFormatOptId}"
        sSBMPlateFormatOptId

    }

    /**
     * Get the LQC Outcome option Id value given the issue
     *
     * @param curIssue
     * @return
     */
    public static String getLQCOutcomeOptionId(Issue curIssue) {

        String sLQCOutcomeOptId
        String sLQCOutcome = JiraAPIWrapper.getCFValueByName(curIssue, ConfigReader.getCFName("LIBRARY_QC_OUTCOME"))
        LOG.debug "Library QC Outcome = ${sLQCOutcome}"
        if(sLQCOutcome == 'Pass') {
            sLQCOutcomeOptId = SelectOptionId.LQC_OUTCOME_PASS.toString()
        } else if(sLQCOutcome == 'Fail') {
            sLQCOutcomeOptId = SelectOptionId.LQC_OUTCOME_FAIL.toString()
        } else {
            sLQCOutcomeOptId = '-1'
        }
        LOG.debug "LQC Outcome option Id = ${sLQCOutcomeOptId}"
        sLQCOutcomeOptId

    }


}
