package uk.ac.sanger.scgcf.jira.lims.enums

/**
 * Enumerated list for select field option ids
 *
 * Created by as28 on 01/08/2017
 */
enum SelectOptionId {

    IQC_OUTCOME_PASS("11402"),
    IQC_OUTCOME_FAIL("11403"),
    IQC_FEEDBACK_PASS("11404"),
    IQC_FEEDBACK_FAIL("11405"),
    SBM_CELLS_PER_LIBRARY_POOL_96("11516"),
    SBM_CELLS_PER_LIBRARY_POOL_384("11517"),
    SBM_PLATE_FORMAT_96("11000"),
    SBM_PLATE_FORMAT_384("11001"),
    LQC_OUTCOME_PASS("11551"),
    LQC_OUTCOME_FAIL("11552")

    String selectOptionId

    public SelectOptionId(String selectOptionId) {
        this.selectOptionId = selectOptionId
    }

    @Override
    String toString() {
        selectOptionId
    }
}
