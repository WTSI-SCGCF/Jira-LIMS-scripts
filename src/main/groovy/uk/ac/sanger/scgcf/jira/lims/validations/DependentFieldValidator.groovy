package uk.ac.sanger.scgcf.jira.lims.validations

import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * The {@code DependentFieldValidator} class validates whether the given
 * dependent field has got a not empty value.
 *
 * Created by ke4 on 08/11/2016.
 */
@Slf4j(value = "LOG")
class DependentFieldValidator {

    public static String COMMON_ERROR_MESSAGE = "One or more dependent fields has not been filled."

    /**
     * This method validates the given dependent field whether it got any value.
     * If the field has not got a value, then the validator
     * adds an error message item to the error message map with the field id as a key.
     * If there was no error at the end of the validation then it returns true,
     * otherwise throws an {@code InvalidInputException} containing the detailed information
     * in the error message map.
     *
     * @param issue the current issue the mandatory fields belongs to
     * @param parentFieldAlias the field alias name the dependent field belongs to
     * @param parentFieldValue the value of the parent field, which determines the dependent field mandatory to fill
     * @param dependentFieldAlias the field alias name of the dependent field
     * @return true if the field is valid otherwise throws an {@code InvalidInputException}
     * @throws InvalidInputException is thrown if the dependent field has to be filled and it is empty
     */
    public boolean validate(Issue issue, String parentFieldAlias, String parentFieldValue,
                            String dependentFieldAlias) throws InvalidInputException {
        LOG.debug "Dependent field validation started"

        def invalidInputException

        String parentFieldName = ConfigReader.getCFName(parentFieldAlias)
        String dependentFieldName = ConfigReader.getCFName(dependentFieldAlias)

        Object parentFieldActualValue = JiraAPIWrapper.getCFValueByName(issue, parentFieldName)

        if ( parentFieldActualValue.equals(parentFieldValue)) {
            Object dependentFieldValue = JiraAPIWrapper.getCFValueByName(issue, dependentFieldName)
            CustomField dependentFieldToValidate = JiraAPIWrapper.getCFByName(dependentFieldName)

            LOG.debug "Validating $dependentFieldName field's value. Its value: '$dependentFieldValue'"
            if (dependentFieldToValidate && !dependentFieldValue) {
                invalidInputException = new InvalidInputException(dependentFieldToValidate.id, "You must specify a value for ${dependentFieldToValidate.name}.")
            }
        }

        LOG.debug "Dependent field validation finished"

        if (invalidInputException) {
            invalidInputException.addError(COMMON_ERROR_MESSAGE)

            throw invalidInputException
        }

        return true
    }
}
