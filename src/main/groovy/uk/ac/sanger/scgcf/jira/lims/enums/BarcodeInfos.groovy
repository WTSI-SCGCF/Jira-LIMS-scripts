package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list of barcode infos to use with Barcode generation.
 * These define the central three letters in the barcode: xxxx.INF.xxxxxxxx
 *
 * Created by as28 on 15/06/2017.
 */
enum BarcodeInfos {

    INFO_SS2("SS2"),
    INFO_DNA("DNA"),
    INFO_CMB("CMB"),
    INFO_ECH("ECH"),
    INFO_LIB("LIB"),
    INFO_LPL("LPL"),
    INFO_PPL("PPL"),
    INFO_NPL("NPL")

    private String type

    public BarcodeInfos(String type) {
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
