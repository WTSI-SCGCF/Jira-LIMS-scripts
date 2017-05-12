package uk.ac.sanger.scgcf.jira.lims.validations

import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * The {@code MandatoryFieldValidator} class validates whether all mandatory
 * field has got a not empty value.
 *
 * Created by ke4 on 10/10/2016.
 */
@Slf4j(value = "LOG")
class MandatoryFieldValidator {

    public static String COMMON_ERROR_MESSAGE = "The fields marked below are mandatory."

    /**
     * This method validates the list of given fields whether they got any values.
     * If a field has not got a value, then the validator
     * adds an error message item to the error message map with the field id as a key.
     * If there was no error at the end of the validation then it returns true,
     * otherwise throws an {@code InvalidInputException} containing the detailed information
     * in the error message map.
     * @param issue the current issue the mandatory fields belongs to
     * @param fieldsToValidate the list of the field names to validate
     * @return true if all the fields are valid otherwise throws an {@code InvalidInputException}
     * @throws InvalidInputException is thrown if some of the mandatory fields are empty
     */
    public boolean validate(Issue issue, List<String> fieldsToValidate) throws InvalidInputException {

        LOG.debug "Mandatory field validation started"

        def invalidInputException

        fieldsToValidate.forEach {
            String customFieldName = ConfigReader.getCFName(it)
            CustomField customfieldToValidate = JiraAPIWrapper.getCustomFieldByName(customFieldName)
            String customFieldValue = JiraAPIWrapper.getCFValueByName(issue, customFieldName)
            if (customFieldValue != null) customFieldValue = customFieldValue.trim()

            LOG.debug "Validating $it mandatory field. Its value: '$customFieldValue'"
            if (customfieldToValidate && !customFieldValue) {
                if (invalidInputException)
                    invalidInputException.addError(customfieldToValidate.id, "You must specify a value for ${customfieldToValidate.name}.")
                else
                    invalidInputException = new InvalidInputException(customfieldToValidate.id, "You must specify a value for ${customfieldToValidate.name}.")
            }
        }

        LOG.debug "Mandatory field validation finished"

        if (invalidInputException) {
            invalidInputException.addError(COMMON_ERROR_MESSAGE)

            throw invalidInputException
        }

        return true
    }
}
