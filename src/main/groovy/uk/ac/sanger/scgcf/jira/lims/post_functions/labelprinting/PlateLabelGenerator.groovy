package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting

import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.configurations.JiraLimsServices
import uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting.templates.LabelJsonCreator
import uk.ac.sanger.scgcf.jira.lims.services.BarcodeGenerator

/**
 * Generates a plate label for sending it to print with a label printer.
 *
 * Created by ke4 on 15/03/2017.
 */
@Slf4j(value="LOG")
class PlateLabelGenerator {

    String printerName
    int numberOfLabels
    def templateJson
    LabelTemplates labelTemplate
    Map<String, LabelTemplates> plateLabelJsonCreators
    String barcodePrefix
    String barcodeInfo
    def labelData
    String bcInfoType
    List<String> barcodes = new ArrayList<>()
    BarcodeGenerator barcodeGenerator

    /**
     * Constructor for {@code PlateLabelGenerator}.
     *
     * @param printerName the name of the printer to print with
     * @param numberOfLabels the number of label to print
     * @param labelTemplate the template to use to print the label(s)
     * @param labelData contains the data to print on the label
     * @param bcInfoType contains the type of barcode info e.g SS2
     */
    PlateLabelGenerator(String printerName, int numberOfLabels, LabelTemplates labelTemplate, def labelData, String bcInfoType) {
        this.printerName = printerName
        this.numberOfLabels = numberOfLabels
        this.labelTemplate = labelTemplate
        this.labelData = labelData
        this.bcInfoType = bcInfoType

        barcodeGenerator = new BarcodeGenerator()

        initBarcodeProperties()
    }

    /**
     * Creates the label to print.
     * Generates the given number of barcodes (the number of the plates). It iterates through
     * the generated barcodes and creates a label for them, then it returns the
     * assembled labels ready to print with the given label printer.
     *
     * @return assembled JSON ready for printing with the selected label printer.
     */
    public def createLabel() {
        generateBarcode()

        labelData['barcodes'] = barcodes

        LOG.debug("labelData: ${labelData.toString()}")

        Object[] labelTemplateArg= [labelData]

        LabelJsonCreator labelJsonCreator = labelTemplate.getInstance(labelTemplateArg)
        def labelBody = labelJsonCreator.createLabelBody()

        templateJson.data.attributes.labels.body = labelBody

        templateJson
    }

    private void generateBarcode() {
        if (numberOfLabels == 1) {
            barcodes.add(barcodeGenerator.generateSingleBarcode(barcodePrefix, barcodeInfo))
        } else {
            barcodes.addAll(barcodeGenerator.generateBatchBarcodes(barcodePrefix, barcodeInfo, numberOfLabels))
        }
    }

    private initBarcodeProperties() {
        Map<String, String> labelTemplateDetails = (Map<String, String>) ConfigReader.getLabeltemplateDetails(LabelTemplates.LABEL_STANDARD_6MM_PLATE)
        Map<String, String> bcInfoDetails = (Map<String, String>) ConfigReader.getBarcodeInfoDetails(bcInfoType)

        def labelTemplateId = labelTemplateDetails.id

        templateJson = [
            "data": [
                "attributes": [
                    "printer_name": printerName,
                    "label_template_id": labelTemplateId,
                    "labels": [
                        "body": []
                    ]
                ]
            ]
        ]

        Map<String, String> printMyBarcodeDetails = (Map<String, String>) ConfigReader.getServiceDetails(JiraLimsServices.PRINT_MY_BARCODE)
        barcodePrefix = printMyBarcodeDetails["barcodePrefix"]
        barcodeInfo = bcInfoDetails["info"]

        plateLabelJsonCreators = new HashMap<>()
        plateLabelJsonCreators.put(LabelTemplates.LABEL_STANDARD_6MM_PLATE.type, LabelTemplates.LABEL_STANDARD_6MM_PLATE)
    }


}
