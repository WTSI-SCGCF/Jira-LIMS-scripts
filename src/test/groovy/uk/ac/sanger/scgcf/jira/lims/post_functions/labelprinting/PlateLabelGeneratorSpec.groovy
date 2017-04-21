package uk.ac.sanger.scgcf.jira.lims.post_functions.labelprinting

import groovyx.net.http.Method
import spock.lang.Shared
import spock.lang.Specification
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.configurations.JiraLimsServices
import uk.ac.sanger.scgcf.jira.lims.services.BarcodeGenerator
import uk.ac.sanger.scgcf.jira.lims.utils.EnvVariableAccess
import uk.ac.sanger.scgcf.jira.lims.utils.RestService

import static groovyx.net.http.ContentType.JSON

/**
 * Created by ke4 on 17/03/2017.
 */
class PlateLabelGeneratorSpec extends Specification {

    @Shared barcodeGeneratorDetails

    def setupSpec() {
        EnvVariableAccess.metaClass.static.getJiraLimsConfigFilePath = { "./src/test/resources/jira_lims_config.json" }
        barcodeGeneratorDetails = ConfigReader.getServiceDetails(JiraLimsServices.BARCODE_GENERATOR)
    }

    def "when calling createLabel method then generates the proper label"() {

        setup:
        String printerName = "abcd123"
        int numberOfLabels = 1
        LabelTemplates labelTemplate = LabelTemplates.LABEL_STANDARD_6MM_PLATE
        def labelData = [:]
        def prefix = "ABCD"
        def info = "EFG"
        def number = 1
        def barcode = "$prefix-$info-0000000$number"

        def expectedLabel = [
            "data": [
                "attributes": [
                    "printer_name": printerName,
                    "label_template_id": 12,
                    "labels": [
                        "body": [
                            [
                                label_1: [
                                    barcode: barcode,
                                    barcode_text: barcode
                                ]
                            ]
                        ]
                    ]
                ]
            ]
        ]

        def restServiceStub = Stub(RestService)

        def responseStatus = 201
        def responseJSON = [
            "id": 123,
            "prefix": prefix,
            "info": info,
            "number": number,
            "fullBarcode": barcode
        ]
        def responseMap = [
                response: [status: responseStatus],
                reader: responseJSON
        ]

        restServiceStub.request(Method.POST, _, JSON, _, _) >> responseMap

        def barcodeGenerator = new BarcodeGenerator()
        barcodeGenerator.restService = restServiceStub

        PlateLabelGenerator plateLabelGenerator =
                new PlateLabelGenerator(printerName, numberOfLabels, labelTemplate, labelData)
        plateLabelGenerator.barcodeGenerator = barcodeGenerator

        expect:
        plateLabelGenerator.createLabel() == expectedLabel
    }

    def "when calling createLabel method for 4 plates then generates the proper label"() {

        setup:
        String printerName = "abcd123"
        int numberOfLabels = 4
        LabelTemplates labelTemplate = LabelTemplates.LABEL_STANDARD_6MM_PLATE
        def labelData = [:]
        def prefix = "ABCD"
        def info = "EFG"
        def numbers = (1..4)
        def barcodes = numbers.collect { number -> "$prefix-$info-0000000$number"}

        def expectedLabel = [
            "data": [
                "attributes": [
                    "printer_name": printerName,
                    "label_template_id": 12,
                    "labels": [
                        "body":
                            barcodes.collect { barcode ->
                                [
                                    label_1: [
                                        barcode: barcode,
                                        barcode_text: barcode
                                    ]
                                ]
                            }

                    ]
                ]
            ]
        ]

        println "BARCODES:" + barcodes
        println "EXPECTED LABEL:" + expectedLabel

        def restServiceStub = Stub(RestService)

        def responseStatus = 201
        def responseJSON = [
            [
                "id": 123,
                "prefix": prefix,
                "info": info,
                "number": 00000001,
                "fullBarcode": "ABCD-EFG-00000001"
            ],
            [
                "id": 123,
                "prefix": prefix,
                "info": info,
                "number": 00000002,
                "fullBarcode": "ABCD-EFG-00000002"
            ],
            [
                "id": 123,
                "prefix": prefix,
                "info": info,
                "number": 00000003,
                "fullBarcode": "ABCD-EFG-00000003"
            ],
            [
                "id": 123,
                "prefix": prefix,
                "info": info,
                "number": 00000004,
                "fullBarcode": "ABCD-EFG-00000004"
            ]
        ]
        def responseMap = [
                response: [status: responseStatus],
                reader: responseJSON
        ]

        restServiceStub.request(Method.POST, _, JSON, _, _) >> responseMap

        def barcodeGenerator = new BarcodeGenerator()
        barcodeGenerator.restService = restServiceStub

        PlateLabelGenerator plateLabelGenerator =
                new PlateLabelGenerator(printerName, numberOfLabels, labelTemplate, labelData)
        plateLabelGenerator.barcodeGenerator = barcodeGenerator

        expect:
        plateLabelGenerator.createLabel() == expectedLabel
    }
}
