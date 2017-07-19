package uk.ac.sanger.scgcf.jira.lims.services

/**
 * Created by as28 on 26/03/2017.
 *
 * Generate a pseudo-unique barcode for use in tests
 */
abstract class DummyBarcodeGenerator {

    static int lastNumUsed = 0

    /**
     * Generate a single barcode
     *
     * @return
     */
    static String generateBarcode(String prefix, String info, int indx) {
        // generate barcode in form <prefix>.<info>.nnnnnnnn
        Date now = new Date()
        // generate pseudo unique barcode number
        String bcNumStr = now.format("HHmmss")
        if(bcNumStr.length() > 6) {
            bcNumStr = bcNumStr.substring(0,6)
        }
        if(indx <= 0) {
            indx = 1
        }
        if(indx > 99) {
            indx = 99
        }
        // pad indx to 2 chars with zeroes
        String bcNumIndx = "${indx}".padLeft( 2, '0' )
        String bc = prefix + "." + info + "." + bcNumStr + bcNumIndx
        System.out.println("Barcode generated = " + bc)
        bc
    }

    /**
     * Generate a batch of barcodes
     *
     * @param numToGenerate
     * @return
     */
    static List<String> generateBarcodeBatch(String prefix, String info, int numToGenerate) {
        List<String> bcs = []
        int indx = lastNumUsed + 1
        numToGenerate.times {
            bcs.push(generateBarcode(prefix, info, indx))
            indx++
        }
        bcs
    }
}
