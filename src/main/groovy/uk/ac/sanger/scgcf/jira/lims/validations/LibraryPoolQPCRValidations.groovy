package uk.ac.sanger.scgcf.jira.lims.validations

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.IssueFieldConstants
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.attachment.FileSystemAttachmentDirectoryAccessor
import com.atlassian.jira.issue.fields.CustomField
import com.opensymphony.workflow.InvalidInputException
import groovy.util.logging.Slf4j

import org.apache.poi.ss.usermodel.*
import org.apache.poi.hssf.usermodel.*
import org.apache.poi.xssf.usermodel.*
import org.apache.poi.ss.util.*
import org.apache.poi.ss.usermodel.*
import sun.invoke.empty.Empty
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.enums.IssueStatusName
import uk.ac.sanger.scgcf.jira.lims.service_wrappers.JiraAPIWrapper
import uk.ac.sanger.scgcf.jira.lims.utils.ExcelParser
import uk.ac.sanger.scgcf.jira.lims.utils.WorkflowUtils

/**
 * The {@code LibraryPoolQPCRValidations} class holds validators for the
 * Library Pool QPCR project
 *
 * Created by as28 on 13/09/2017.
 */

@Slf4j(value = "LOG")
class LibraryPoolQPCRValidations {

    public static String SCAN_TUBES_ERROR_MESSAGE = "The fields below have issues that need to be resolved:"

    public static void validateScannedTubes(MutableIssue QPCRIssue) throws Exception {

        // get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of barcodes expected = <${Integer.toString(iNumTubes)}>"

        def invalidInputException

        Map<String, String> barcodesMap = [:]

        (1..iNumTubes).each { int iTubeIndx ->
            String sTubeIndx = Integer.toString(iTubeIndx)
            String sBarcodeCFAlias = "SOURCE_" + sTubeIndx + "_BARCODE"
            String sSourceBarcode = JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName(sBarcodeCFAlias))
            CustomField CFToValidate = JiraAPIWrapper.getCFByName(ConfigReader.getCFName(sBarcodeCFAlias))

            // check Source n Barcode is initialised
            if(!sSourceBarcode?.trim()) {
                String sMsg = "You must specify a barcode for ${CFToValidate.name}, expecting ${Integer.toString(iNumTubes)} tubes."
                if (invalidInputException) {
                    invalidInputException.addError(CFToValidate.id, sMsg)
                } else {
                    invalidInputException = new InvalidInputException(CFToValidate.id, sMsg)
                }
                return
            }

            // check Source n Barcode is correct format (regex)
            def regex = /^[A-Z]{4}\.PPL\.\d{8}$/
            def isMatch = sSourceBarcode ==~ regex
            if(!isMatch) {
                String sMsg = "The value of field ${CFToValidate.name} does not match the expected Barcode format."
                if (invalidInputException) {
                    invalidInputException.addError(CFToValidate.id, sMsg)
                } else {
                    invalidInputException = new InvalidInputException(CFToValidate.id, sMsg)
                }
                return
            }

            // check Source n Barcode exists in JIRA
            Issue sourceIssue = WorkflowUtils.getIssueForBarcode(sSourceBarcode)
            if(sourceIssue == null) {
                String sMsg = "No PPL tube issue found in JIRA for barcode ${sSourceBarcode}."
                if (invalidInputException) {
                    invalidInputException.addError(CFToValidate.id, sMsg)
                } else {
                    invalidInputException = new InvalidInputException(CFToValidate.id, sMsg)
                }
                return
            } else {
                // check the state is as expected ('TubPPL Rdy for QPCR')
                if(sourceIssue.getStatus().getName() != IssueStatusName.TUBPPL_RDY_FOR_QPCR.toString()) {
                    String sMsg = "The PPL tube issue for barcode ${sSourceBarcode} is not in the correct state (Rdy for QPCR)."
                    if (invalidInputException) {
                        invalidInputException.addError(CFToValidate.id, sMsg)
                    } else {
                        invalidInputException = new InvalidInputException(CFToValidate.id, sMsg)
                    }
                    return
                }
            }

            // Source barcode does not match to any others
            if(barcodesMap.containsValue(sSourceBarcode)) {
                String sMsg = "The barcode ${sSourceBarcode} at position ${sTubeIndx} cannot be the same as previous barcodes."
                if (invalidInputException) {
                    invalidInputException.addError(CFToValidate.id, sMsg)
                } else {
                    invalidInputException = new InvalidInputException(CFToValidate.id, sMsg)
                }
                return
            }

            // add barcode to map
            barcodesMap[sTubeIndx] = sSourceBarcode
        }

        if (invalidInputException) {
            invalidInputException.addError(SCAN_TUBES_ERROR_MESSAGE)
            throw invalidInputException
        }
    }

    /**
     * Validation to check for the presence and valid content of the Light Cycler results file.
     *
     * @param QPCRIssue
     * @throws Exception
     */
    public static void validateAttachedLCFile(MutableIssue QPCRIssue) throws Exception {

        def attachmentDirectoryAccessor = ComponentAccessor.getComponent(FileSystemAttachmentDirectoryAccessor)
        def temporaryAttachmentDirectory = attachmentDirectoryAccessor.getTemporaryAttachmentDirectory()

        LOG.debug "Attempting to get attachments from modified fields"
        def attachmentTempFileNames = QPCRIssue.getModifiedFields().get(IssueFieldConstants.ATTACHMENT)?.newValue
        LOG.debug "Number of attachments found = <${attachmentTempFileNames.size()}>"

        // check for attachments
        if (attachmentTempFileNames.size() != 1) {
            throw new InvalidInputException("Expecting one (and only one) results xlsx file from the Light Cycler to be attached")
        }

        // get the number of tubes custom field value
        int iNumTubes = Double.valueOf(JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("NUMBER_OF_TUBES")))
        LOG.debug "Number of PPL tubes = <${Integer.toString(iNumTubes)}>"

        // check each file attached in this transition (does not check other existing attachments)
        for (String sResultsTempFileName in attachmentTempFileNames) {
            LOG.debug "Found an attachment"
            LOG.debug "Temporary filename = ${sResultsTempFileName}"

            // TODO: how determine this is the excel sheet we want? i.e. how tell file is xlsx?

            if (sResultsTempFileName != null) {
                String sFilepath = new File(temporaryAttachmentDirectory, sResultsTempFileName).getPath()
                try {
                    def sheetMaps = ExcelParser.parse(sFilepath)

                    String sLightCyclerBC = ""
                    Map<String, String> concsMap = [:]

                    // check for the presence of the results tab
                    for (sheetMap in sheetMaps) {
                        String sSheetName = sheetMap.value["sheet_name"]
                        if (sSheetName == "QPCR Results") {
                            def rows = sheetMap.value["rows"]

                            int iRowIndex = 0
                            rows.each { List<String> row ->
                                if(Integer.valueOf(iRowIndex) == 0) {
                                    if (row.get(0).toUpperCase() == "LIGHT CYCLER PLATE BARCODE") {
                                        sLightCyclerBC = new BigDecimal(row.get(1)).toPlainString() // to get rid of the scientific notation
                                        if (sLightCyclerBC == null) {
                                            LOG.error "No Light Cycler barcode found in cell A2 on QPCR Results tab of spreadsheet"
                                            throw new InvalidInputException("There should be a Light Cycler barcode in cell A2 on the QPCR Results sheet tab in the file")
                                        }
                                        LOG.debug "Light Cycler Barcode from file = <${sLightCyclerBC}>"
                                    } else {
                                        LOG.error "Light Cycler Plate Barcode field label not found in cell A1 on QPCR Results tab of spreadsheet"
                                        throw new InvalidInputException("Light Cycler Plate Barcode not found in cell A1 on the QPCR Results sheet tab in the file")
                                    }
                                } else {
                                    if(iRowIndex <= iNumTubes) {
                                        String sTubePosnRaw = row.get(0) // tube position, double
                                        String sTubePosn = Integer.toString(Double.valueOf(sTubePosnRaw).intValue()) // convert position to integer
                                        String sConcRaw = row.get(1) // concentration, double
                                        String sConc = new BigDecimal(sConcRaw).toPlainString()
                                        if(sTubePosn?.trim() && sTubePosn == Integer.toString(iRowIndex)) {
                                            if(sConc?.trim()) {
                                                concsMap[sTubePosn] = sConc
                                            } else {
                                                LOG.error "ERROR: No concentration found in results file for tube posn ${Integer.toString(iRowIndex)}, cannot parse"
                                                throw new InvalidInputException("No concentration found in results file for tube posn ${Integer.toString(iRowIndex)}")
                                            }
                                        } else {
                                            LOG.error "ERROR: No tube position found in results file for tube ${Integer.toString(iRowIndex)}, cannot parse"
                                            throw new InvalidInputException("No tube position found in results file for tube ${Integer.toString(iRowIndex)}")
                                        }
                                    }
                                }
                                iRowIndex++
                            }

                            // barcode should match that in the Destination 1 Barcode field on the issue
                            String sDestination1Barcode = JiraAPIWrapper.getCFValueByName(QPCRIssue, ConfigReader.getCFName("DESTINATION_1_BARCODE"))
                            LOG.debug "Destination 1 Barcode from issue = <${sDestination1Barcode}>"

                            if(sDestination1Barcode != sLightCyclerBC) {
                                LOG.error "The Light Cycler barcode in the file <${sLightCyclerBC}> does not match the barcode entered into Destination 1 Barcode in the issue <${sDestination1Barcode}>"
                                throw new InvalidInputException("The Light Cycler barcode on the QPCR Results sheet tab in the file does not match the barcode scanned into this issue")
                            }

                            if (concsMap.size() != iNumTubes) {
                                LOG.error "Number of tubes does not match to number of concentration values found in QPCR Results file"
                                throw new InvalidInputException("The number of concentration values on the QPCR Results sheet tab in the file (${concsMap.size()}) should match the number of tubes linked to this issue (${Integer.toString(iNumTubes)})")
                            }

                            LOG.debug '------------------'
                            LOG.debug "Sheet name           : <${sSheetName}>"
                            LOG.debug "Light Cycler Barcode : <${sLightCyclerBC}>"
                            LOG.debug '------------------'
                            LOG.debug 'Concentrations:'
                            LOG.debug '------------------'
                            LOG.debug concsMap.toMapString()
                            LOG.debug '------------------'

                            return
                        }
                    }
                } catch(InvalidInputException e) {
                    throw e
                } catch(Exception e) {
                    LOG.debug "File would not parse, could not validate"
                    LOG.debug e.getMessage()
                }
            }
        }

        throw new InvalidInputException("No valid Light Cycler QPCR Results file attached, please check the format of the file is as expected.")
    }
}
