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
class AtLeastOneFieldValidatorSpec extends Specification {

    def "when none of the field from the collection is filled then it throws a validation error"() {

        setup: "Create mocked custom fields and instantiate the validator"
        new MockComponentWorker().init();

        def fieldAliases = [ "fieldNameAlias1", "fieldNameAlias2", "fieldNameAlias3"]
        def fieldNames = [ "fieldName1", "fieldName2", "fieldName3"]
        def mockedCustomFieldIDs = [ "mockedCF1", "mockedCF2", "mockedCF3"]
        def mockedCustomFieldNames = [ "mockedCFName1", "mockedCFName2", "mockedCFName3"]
        def issueStub = Stub(Issue)
        def mockCustomFieldType = new MockCustomFieldType()

        GroovyMock(ConfigReader, global:true)
        ConfigReader.getCFName(fieldAliases[0]) >> fieldNames[0]
        ConfigReader.getCFName(fieldAliases[1]) >> fieldNames[1]
        ConfigReader.getCFName(fieldAliases[2]) >> fieldNames[2]

        GroovyMock(JiraAPIWrapper, global:true)
        JiraAPIWrapper.getCustomFieldByName(fieldNames[0]) >>
                new MockCustomField(mockedCustomFieldIDs[0], mockedCustomFieldNames[0], mockCustomFieldType)
        JiraAPIWrapper.getCustomFieldByName(fieldNames[1]) >>
                new MockCustomField(mockedCustomFieldIDs[1], mockedCustomFieldNames[1], mockCustomFieldType)
        JiraAPIWrapper.getCustomFieldByName(fieldNames[2]) >>
                new MockCustomField(mockedCustomFieldIDs[2], mockedCustomFieldNames[2], mockCustomFieldType)
        JiraAPIWrapper.getCFValueByName(issueStub, fieldNames[0]) >> ""
        JiraAPIWrapper.getCFValueByName(issueStub, fieldNames[1]) >> ""
        JiraAPIWrapper.getCFValueByName(issueStub, fieldNames[2]) >> ""

        def atLeastOneFieldValidator = new AtLeastOneFieldValidator()

        when: "do the validation"
        atLeastOneFieldValidator.validate(issueStub, fieldAliases)

        then: "InvalidInputException should be thrown"
        InvalidInputException ex = thrown()
        Map<String, String> errorMessagesMap = ex.getErrors()
        ex.genericErrors[0] == AtLeastOneFieldValidator.COMMON_ERROR_MESSAGE + fieldNames
    }

    def "when any of the field from the collection is filled then the validation pass"() {

        setup: "Create mocked custom fields and instantiate the validator"
        new MockComponentWorker().init();

        def mandatoryFieldAliases = [ "fieldNameAlias1", "fieldNameAlias2", "fieldNameAlias3"]
        def mandatoryFieldNames = [ "fieldName1", "fieldName2", "fieldName3"]
        def mockedCustomFieldIDs = [ "mockedCF1", "mockedCF2", "mockedCF3"]
        def mockedCustomFieldNames = [ "mockedCFName1", "mockedCFName2", "mockedCFName3"]
        def issueStub = Stub(Issue)
        def mockCustomFieldType = new MockCustomFieldType()

        GroovyMock(ConfigReader, global:true)
        ConfigReader.getCFName(mandatoryFieldAliases[0]) >> mandatoryFieldNames[0]
        ConfigReader.getCFName(mandatoryFieldAliases[1]) >> mandatoryFieldNames[1]
        ConfigReader.getCFName(mandatoryFieldAliases[2]) >> mandatoryFieldNames[2]

        GroovyMock(JiraAPIWrapper, global:true)
        JiraAPIWrapper.getCustomFieldByName(mandatoryFieldNames[0]) >>
                new MockCustomField(mockedCustomFieldIDs[0], mockedCustomFieldNames[0], mockCustomFieldType)
        JiraAPIWrapper.getCustomFieldByName(mandatoryFieldNames[1]) >>
                new MockCustomField(mockedCustomFieldIDs[1], mockedCustomFieldNames[1], mockCustomFieldType)
        JiraAPIWrapper.getCustomFieldByName(mandatoryFieldNames[2]) >>
                new MockCustomField(mockedCustomFieldIDs[2], mockedCustomFieldNames[2], mockCustomFieldType)
        JiraAPIWrapper.getCFValueByName(issueStub, mandatoryFieldNames[0]) >> ""
        JiraAPIWrapper.getCFValueByName(issueStub, mandatoryFieldNames[1]) >> ""
        JiraAPIWrapper.getCFValueByName(issueStub, mandatoryFieldNames[2]) >> "test"

        def atLeastOneFieldValidator = new AtLeastOneFieldValidator()

        expect: "validation passes"
        assert atLeastOneFieldValidator.validate(issueStub, mandatoryFieldAliases)
    }
}
