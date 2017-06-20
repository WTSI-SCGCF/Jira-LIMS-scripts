package uk.ac.sanger.scgcf.jira.lims.configurations

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.enums.BarcodeInfos
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.LabelTemplates
import uk.ac.sanger.scgcf.jira.lims.utils.EnvVariableAccess

import java.nio.file.Paths

/**
 * The {@code ConfigReader} class handles all interaction with the script configuration file.
 *
 * Created by as28 on 23/06/16.
 */

@Slf4j(value="LOG")
class ConfigReader {
    // store configurations in map
    static def configMap = null;

    /**
     * Fetch and parse jira_lims_config.json from configs
     */
    static void parseConfigFile() {
        LOG.debug "Creating config from json file"

        // set configMap from json
        JsonSlurper slurper = new JsonSlurper()
        Paths.get(EnvVariableAccess.jiraLimsConfigFilePath).withReader { Reader reader ->
            configMap = slurper.parse(reader)
        }

        if ( configMap == null ) {
            LOG.error "Failed to read the configuration file at filepath: ${EnvVariableAccess.jiraLimsConfigFilePath}"
        } else {
            LOG.debug (configMap.getClass().toString())
        }
    }

    /**
     * Gets the printer label templates section of the configuration
     * @return the section of the config map containing the label template details
     */
    static def getLabeltemplateDetails(LabelTemplates template) {
        checkConfigFileAvailability("In ConfigReader get label template details for ${template.type}")

        configMap['labelTemplateDetails']['Labels'][template.type]
    }

    /**
     * Gets the barcode infos section of the configuration
     * @return the section of the config map containing the barcode info details
     */
    static def getBarcodeInfoDetails(BarcodeInfos bcInfo) {
        checkConfigFileAvailability("In ConfigReader get barcode info details for ${bcInfo.type}")

        configMap['labelTemplateDetails']['Infos'][bcInfo.type]
    }

    /**
     * Gets the given service section of the configuration
     * @return the section of the config map containing the given service's details
     */
    static def getServiceDetails(JiraLimsServices service) {
        checkConfigFileAvailability("In ConfigReader getService details for ${service.name()}")

        configMap['services'][service.getServiceKey()]
    }

    /**
     * Checks whether the printing is ON or OFF.
     *
     * @return true if the setting for printing is turned on, otherwise returns false.
     */
    static boolean isLabelPrintingOn() {
        checkConfigFileAvailability("In ConfigReader - check printing availability")

        configMap['services'][JiraLimsServices.PRINT_MY_BARCODE.toString()]['printingOn'] == true
    }

    /**
     * Gets a configuration element from the config file using the given key list.
     * @param keys the list of the configuration keys
     * @return the required configuration element or throws {@code NoSuchElementException}
     * if there is no element belongs to the given keys
     */
    static def getConfigElement(List<String> keys) {
        checkConfigFileAvailability("In ConfigReader getConfigElement")

        def element = null
        def tmpConfigMap = configMap
        for (def key: keys) {
            element = tmpConfigMap[key]
            if (!element) break;
            tmpConfigMap = element
        }

        if (!element) {
            throw new NoSuchElementException("No element found with the given keys: ${keys.toString()}".toString())
        }

        element
    }

    /**
     * Gets the name of the custom field from the configuration file searched by the given alias name.
     * @param cfAlias the alias name of the custom field
     * @return the name of the queried custom field
     */
    static String getCFName(String cfAlias) {
        checkConfigFileAvailability("In ConfigReader getCFName with alias <${cfAlias}>".toString())

        String cfName = null
        def element = configMap['custom_fields'][cfAlias]
        if(element) {
            cfName = element['cfname']
        }

        if (!cfName) {
            LOG.error("ConfigReader getCFName: could not find cfname for alias <${cfAlias}>".toString())
            throw new NoSuchElementException("No configmap element found for getCFName with alias: <${cfAlias}>".toString())
        }
        LOG.debug("CF name = ${cfName}".toString())
        cfName
    }

    /**
     * Gets the custom field id for the alias name
     * @param cfAlias the alias name of the custom field
     * @return the id number of the custom field
     */
    static long getCFId(String cfAlias) {
        checkConfigFileAvailability("In ConfigReader getCFId with alias <${cfAlias}>".toString())

        long cfId = -1
        def element = configMap['custom_fields'][cfAlias]
        if(element) {
            cfId = element['cfid'] as long
        }

        if (cfId <= 0) {
            LOG.error("ConfigReader getCFName: could not find cfid for alias <${cfAlias}>".toString())
            throw new NoSuchElementException("No configmap element found for getCFId with alias: <${cfAlias}>".toString())
        }
        LOG.debug("CF id = ${cfId}".toString())
        cfId
    }

    /**
     * Gets the custom field id string for the alias name
     * @param cfAlias
     * @return the id string for the custom field e.g. customfield_12345
     */
    static String getCFIdString(String cfAlias) {
        checkConfigFileAvailability("In ConfigReader getCFIdString with alias ${cfAlias}")

        String cfIdString = null
        def element = configMap['custom_fields'][cfAlias]
        if(element) {
            cfIdString = element['cfidstring'] as String
        }

        if (!cfIdString) {
            LOG.error("ConfigReader getCFName: could not find cfidstring for alias <${cfAlias}>".toString())
            throw new NoSuchElementException("No configmap element found for getCFIdString with alias: <${cfAlias}>".toString())
        }
        LOG.debug("CF idString = ${cfIdString}".toString())
        cfIdString
    }

    /**
     * Get the transition id for the specified workflow and transition alias
     *
     * @param workflowName
     * @param transitionAlias
     * @return
     */
    static int getTransitionActionId(String workflowName, String transitionAlias) {
        int actionId = getConfigElement(["transitions", workflowName, transitionAlias, "tactionid"]) as int
        actionId
    }

    private static void checkConfigFileAvailability(String message) {
        LOG.debug message

        if (configMap == null) {
            parseConfigFile()
        }
    }
}
