package uk.ac.sanger.scgcf.jira.lims.validations

import com.atlassian.jira.action.issue.customfields.MockCustomFieldType
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.MockCustomField
import com.atlassian.jira.mock.component.MockComponentWorker
import com.opensymphony.workflow.InvalidInputException
import spock.lang.Specification
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * Created by ke4 on 08/11/16.
 */
class DependentFieldValidatorSpec extends Specification {

    def "when the dependent field is not filled in then it throws a validation error"() {

        setup: "Create mocked custom fields and instantiate the validator"
        new MockComponentWorker().init();

        def mandatoryFieldAliases = [ "parentFieldNameAlias", "dependentFieldNameAlias"]
        def mandatoryFieldNames = [ "parentFieldName", "dependentFieldName"]
        def mockedCustomFieldIDs = [ "parentMockedCF", "dependentMockedCF"]
        def mockedCustomFieldNames = [ "parentMockedCFName", "dependentMockedCFName"]
        def issueStub = Stub(Issue)
        def mockCustomFieldType = new MockCustomFieldType()
        def final parentFieldValue = "Other"

        GroovyMock(ConfigReader, global:true)
        ConfigReader.getCFName(mandatoryFieldAliases[0]) >> mandatoryFieldNames[0]
        ConfigReader.getCFName(mandatoryFieldAliases[1]) >> mandatoryFieldNames[1]

        GroovyMock(JiraAPIWrapper, global:true)
        JiraAPIWrapper.getCFByName(mandatoryFieldNames[0]) >>
                new MockCustomField(mockedCustomFieldIDs[0], mockedCustomFieldNames[0], mockCustomFieldType)
        JiraAPIWrapper.getCFByName(mandatoryFieldNames[1]) >>
                new MockCustomField(mockedCustomFieldIDs[1], mockedCustomFieldNames[1], mockCustomFieldType)
        JiraAPIWrapper.getCFValueByName(issueStub, mandatoryFieldNames[0]) >> parentFieldValue
        JiraAPIWrapper.getCFValueByName(issueStub, mandatoryFieldNames[1]) >> ""

        def dependentFieldValidator = new DependentFieldValidator()

        when: "do the validation"
        dependentFieldValidator.validate(
                issueStub, mandatoryFieldAliases[0], parentFieldValue, mandatoryFieldAliases[1])

        then: "InvalidInputException should be thrown"
        InvalidInputException ex = thrown()
        Map<String, String> errorMessagesMap = ex.getErrors()
        ex.genericErrors[0] == DependentFieldValidator.COMMON_ERROR_MESSAGE
        errorMessagesMap.size() == 1
        errorMessagesMap.get(mockedCustomFieldIDs[1]) == "You must specify a value for ${mockedCustomFieldNames[1]}.".toString()
    }

    def "when the dependent field is filled in then the validator returns true"() {

        setup: "Create mocked custom fields and instantiate the validator"
        new MockComponentWorker().init();

        def mandatoryFieldAliases = [ "parentFieldNameAlias", "dependentFieldNameAlias"]
        def mandatoryFieldNames = [ "parentFieldName", "dependentFieldName"]
        def mockedCustomFieldIDs = [ "parentMockedCF", "dependentMockedCF"]
        def mockedCustomFieldNames = [ "parentMockedCFName", "dependentMockedCFName"]
        def issueStub = Stub(Issue)
        def mockCustomFieldType = new MockCustomFieldType()
        def final parentFieldValue = "Other"

        GroovyMock(ConfigReader, global:true)
        ConfigReader.getCFName(mandatoryFieldAliases[0]) >> mandatoryFieldNames[0]
        ConfigReader.getCFName(mandatoryFieldAliases[1]) >> mandatoryFieldNames[1]

        GroovyMock(JiraAPIWrapper, global:true)
        JiraAPIWrapper.getCFByName(mandatoryFieldNames[0]) >>
                new MockCustomField(mockedCustomFieldIDs[0], mockedCustomFieldNames[0], mockCustomFieldType)
        JiraAPIWrapper.getCFByName(mandatoryFieldNames[1]) >>
                new MockCustomField(mockedCustomFieldIDs[1], mockedCustomFieldNames[1], mockCustomFieldType)
        JiraAPIWrapper.getCFValueByName(issueStub, mandatoryFieldNames[0]) >> parentFieldValue
        JiraAPIWrapper.getCFValueByName(issueStub, mandatoryFieldNames[1]) >> "alien"

        def dependentFieldValidator = new DependentFieldValidator()

        expect: "validation passes"
        assert dependentFieldValidator.validate(
                issueStub, mandatoryFieldAliases[0], parentFieldValue, mandatoryFieldAliases[1])
    }
}
