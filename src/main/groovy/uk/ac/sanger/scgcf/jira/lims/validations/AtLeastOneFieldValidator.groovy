package uk.ac.sanger.scgcf.jira.lims.validations

import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper

/**
 * The {@code AtLeastOneFieldValidator} class validates whether any of the given custom fields
 * has got a not empty value.
 *
 * Created by ke4 on 09/11/2016.
 */
@Slf4j(value = "LOG")
class AtLeastOneFieldValidator {

    public static String COMMON_ERROR_MESSAGE = "At least one of these fields should be filled/selected: "

    /**
     * This method validates the list of given fields whether they got any values.
     * From the given list of fields at least one should have got a not empty value.
     * If none of the fields has got any value, then the validator
     * sends an error message.
     * If there was no error at the end of the validation then it returns true,
     * otherwise throws an {@code InvalidInputException} containing the detailed information.
     *
     * @param issue the current issue the mandatory fields belongs to
     * @param fieldNameAliases the list of field aliases to validate
     * @return true if at least one field has got a value, otherwise throws an {@code InvalidInputException}
     * @throws InvalidInputException is thrown if all of the fields are empty
     */
    public boolean validate(Issue issue, List<String> fieldNameAliases) throws InvalidInputException {
        LOG.debug "At least one field validation started"

        List<String> fieldNames = new ArrayList<>()

        for (String fieldNameAlias: fieldNameAliases) {
            String customFieldName = ConfigReader.getCFName(fieldNameAlias)
            CustomField customFieldToValidate = JiraAPIWrapper.getCustomFieldByName(customFieldName)
            Object customFieldValue = JiraAPIWrapper.getCFValueByName(issue, customFieldName)

            LOG.debug "Validating $customFieldName field. Its value: '$customFieldValue'"

            if (customFieldToValidate && customFieldValue) {
                LOG.debug "At least one field validation finished"

                return true;
            }

            fieldNames.add(customFieldName)
        }

        LOG.debug "At least one field validation finished with error"

        throw new InvalidInputException(COMMON_ERROR_MESSAGE + fieldNames);
    }
}
