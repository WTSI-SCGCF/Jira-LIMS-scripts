package uk.ac.sanger.scgcf.jira.lims.services

import groovy.util.logging.Slf4j
import groovyx.net.http.Method
import uk.ac.sanger.scgcf.jira.lims.configurations.ConfigReader
import uk.ac.sanger.scgcf.jira.lims.configurations.JiraLimsServices
import uk.ac.sanger.scgcf.jira.lims.exceptions.RestServiceException
import uk.ac.sanger.scgcf.jira.lims.utils.RestService
import uk.ac.sanger.scgcf.jira.lims.utils.ValidatorExceptionHandler

import static groovyx.net.http.ContentType.JSON

/**
 * The <@code BarcodeGenerator> class communicating with the Barcode Generator micro service
 * to get a single or given number of barcodes.
 * If the service is down it will response to a user with a friendly error message.
 * Created by ke4 on 13/03/2017.
 */
@Slf4j(value = "LOG")
class BarcodeGenerator {

    def static barcodeGeneratorDetails = ConfigReader.getServiceDetails(JiraLimsServices.BARCODE_GENERATOR)
    RestService restService = new RestService(getBarcodeGeneratorServicePath())
    def responseCode

    /**
     * Returns the list of current barcodes.
     *
     * @return a List of current barcodes.
     */
    public def getCurrentBarcodes() {
        callBarcodeGenerator(Method.GET, null, singleBarcodePath())
    }

    /**
     * Calls the Barcode Generator service to generate a single barcode.
     *
     * @param barcodePrefix The prefix of the required barcode
     * @param infoText the middle part of the required barcode
     * @return a generated barcode with the given prefix and info part included.
     */
    public String generateSingleBarcode(String barcodePrefix, String infoText) {
        def requestBody = [
            "prefix": barcodePrefix,
            "info": infoText
        ]
        callBarcodeGenerator(Method.POST, requestBody, singleBarcodePath())["fullBarcode"]
    }

    /**
     * Calls the Barcode Generator service to generate given number of barcodes.
     *
     * @param barcodePrefix The prefix of the required barcode
     * @param infoText the middle part of the required barcode
     * @param numberOfBarcodes the number of barcodes to generate
     * @return a List of generated barcode with the given prefix and info part included.
     */
    public List<String> generateBatchBarcodes(String barcodePrefix, String infoText, int numberOfBarcodes) {
        def requestBody = [
                "prefix": barcodePrefix,
                "info": infoText,
                "numberOfBarcodes": numberOfBarcodes
        ]

        List barcodes = (List<Map<String, String>>) callBarcodeGenerator(Method.POST, requestBody, batchBarcodePath())
        barcodes*.fullBarcode
    }

    /**
     * Call the REST service to generate barcodes from the database.
     *
     * @param method
     * @param requestBody
     * @param servicePath
     * @return
     */
    private def callBarcodeGenerator(Method method, def requestBody, String servicePath) {
        LOG.debug("Calling REST service for barcode generation")
        LOG.debug("servicePath: ${servicePath}")
        LOG.debug("request body: ${requestBody.toString()}")
        LOG.debug("method: ${method.toString()}")
        //TODO: add try catch for java.net.ConnectException
        def responseMap = restService.request(method, [:], JSON, servicePath, requestBody)
        def response = responseMap['response']
        def reader = responseMap['reader']
        LOG.debug(responseMap.toString())
        responseCode = response.status

        if (responseCode == 503) {
            def errorMessage = "The barcode generation failed (HTTP status code: $responseCode)."
            def additionalMessage= "The error message is: $reader. URL: ${restService.httpBuilder.uri}/${servicePath}, Request: $requestBody".toString()

            def barcodeGenerationError = new RestServiceException(errorMessage)

            ValidatorExceptionHandler.throwAndLog(barcodeGenerationError, errorMessage, additionalMessage)
        }

        reader
    }

    /**
     * Returns the URI for BarcodeGenerator service.
     * @return the URI for BarcodeGenerator service.
     */
    public static String getBarcodeGeneratorServicePath() {
        String.format("%s:%s",
            barcodeGeneratorDetails['baseUrl'],
            barcodeGeneratorDetails['port']
        )
    }

    /**
     * Returns the context path.
     *
     * @return the context path.
     */
    public static String getContextPath() {
        String.format("%s%s",
            barcodeGeneratorDetails['contextPath'],
            barcodeGeneratorDetails['apiVersion']
        )
    }

    /**
     * Returns the URI for generating a single barcode.
     *
     * @return the URI for generating a single barcode.
     */
    public static String singleBarcodePath() {
        "/${getContextPath()}/${barcodeGeneratorDetails['getSingleBarcodePath']}".toString()
    }

    /**
     * Returns the URI for generating a batch of barcodes.
     *
     * @return the URI for generating a batch of barcodes.
     */
    public static String batchBarcodePath() {
        "/${getContextPath()}/${barcodeGeneratorDetails['getBatchOfBarcodesPath']}".toString()
    }
}
