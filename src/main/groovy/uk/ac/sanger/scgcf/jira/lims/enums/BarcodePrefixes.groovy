package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list of barcode prefixes to use with Barcode generation application.
 * These define the initial four letters in the barcode: PRFX.xxx.xxxxxxxx
 *
 * Created by as28 on 10/07/2017.
 */
enum BarcodePrefixes {

    PRFX_SCGC("SCGC"),
    PRFX_TEST("TEST")

    private String type

    public BarcodePrefixes(String type) {
        this.type = type
    }

    public String getType() {
        type
    }

    @Override
    String toString() {
        return type
    }

}
