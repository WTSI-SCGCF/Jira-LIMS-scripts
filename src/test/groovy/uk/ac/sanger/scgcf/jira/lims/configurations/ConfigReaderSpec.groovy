package uk.ac.sanger.scgcf.jira.lims.configurations

import spock.lang.Specification
import uk.ac.sanger.scgcf.jira.lims.utils.EnvVariableAccess

class ConfigReaderSpec extends Specification {

    def setupSpec() {
        EnvVariableAccess.metaClass.static.getJiraLimsConfigFilePath = { "./src/test/resources/jira_lims_config.json" }
    }

    def 'test can read config file'() {
        expect: "test can read custom field aliases"
        assert ConfigReader.getCFName("UAT_CUST_TUBE_BARCODES") == "UAT cust tube barcodes"

        assert ConfigReader.getCFName("UAT_CUST_TUBE_DETAILS") == "UAT cust tube details"

        assert ConfigReader.getConfigElement(
                ["validation", "mandatoryFields", "ProjectName_1", "Type_1", "action_1"]) ==
                [ "FIELD_NAME_1", "FIELD_NAME_2", "FIELD_NAME_3"]

        assert ConfigReader.getConfigElement(
                ["validation", "atLeastOneFields", "ProjectName_1", "Type_1", "action_1"]) == [
                    [ "FIELD_NAME_1", "FIELD_NAME_2"]
                ]

        assert ConfigReader.getConfigElement(
                ["transitions", "Type_1", "TransitionName_1", "tactionid"]) == 411
        assert ConfigReader.getConfigElement(
                ["transitions", "Type_1", "TransitionName_1", "tname"]) == "Transition name 1"
    }

    def "test methods throw NoSuchElementException when alias is not found"() {
        setup:
        String testAlias = "THIS_DOES_NOT_EXIST"

        when:
        ConfigReader.getCFName(testAlias)

        then: "NoSuchElementException will be thrown"
        NoSuchElementException ex1 = thrown()
        ex1.message == "No configmap element found for getCFName with alias: <${testAlias}>".toString()

        when:
        ConfigReader.getCFId(testAlias)

        then: "NoSuchElementException will be thrown"
        NoSuchElementException ex2 = thrown()
        ex2.message == "No configmap element found for getCFId with alias: <${testAlias}>".toString()

        when:
        ConfigReader.getCFIdString(testAlias)

        then: "NoSuchElementException will be thrown"
        NoSuchElementException ex3 = thrown()
        ex3.message == "No configmap element found for getCFIdString with alias: <${testAlias}>".toString()
    }

    def "when reader can't find an element, then NoSuchElementException will be thrown"() {
        setup:
        List<String> keys = ["no","such","keys"]

        when:
        ConfigReader.getConfigElement(keys)

        then: "NoSuchElementException will be thrown"
        NoSuchElementException ex = thrown()
        ex.message == "No element found with the given keys: ${keys.toString()}".toString()
    }

    def "test reading sequencescape service config gives back the correct result"() {
        setup:
        def sequencescapeDetails = ConfigReader.getServiceDetails(JiraLimsServices.SEQUENCESCAPE)

        expect: "Check if details are correct"
        sequencescapeDetails['baseUrl'] == "http://seq_base_url"
        sequencescapeDetails['apiVersion'] == "/api/123"
        sequencescapeDetails['searchProjectByName'] == "searchProjectByNamePath"
        sequencescapeDetails['searchStudyByName'] == "searchStudyByNamePath"
    }

    def "test reading barcode generator service config gives back the correct result"() {
        setup:
        def barcodeGeneratorDetails = ConfigReader.getServiceDetails(JiraLimsServices.BARCODE_GENERATOR)

        expect: "Check if details are correct"
        barcodeGeneratorDetails['baseUrl'] == "http://bargenBaseUrl"
        barcodeGeneratorDetails['apiVersion'] == ""
        barcodeGeneratorDetails['contextPath'] == "barcode-generator"
        barcodeGeneratorDetails['getBatchOfBarcodesPath'] == "batch_barcodes"
    }

    def "when label printing set to off, then we should get false from the config file setting"() {
        setup:
        def labelPrintingSwitch = ConfigReader.isLabelPrintingOn()

        expect:
        labelPrintingSwitch
    }
}
