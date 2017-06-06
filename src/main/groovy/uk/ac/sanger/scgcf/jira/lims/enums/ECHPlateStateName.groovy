package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list holding ECH plate state names.
 *
 * Created by as28 on 01/06/2017.
 */
enum ECHPlateStateName {

    PLTECH_RDY_FOR_QUANT_ANALYSIS("PltECH Rdy for Quant Analysis"),
    PLTECH_IN_QUANT_ANALYSIS("PltECH In Quant Analysis")

    String plateStateName

    public ECHPlateStateName(String plateStateName) {
        this.plateStateName = plateStateName
    }

    @Override
    String toString() {
        plateStateName
    }
}
