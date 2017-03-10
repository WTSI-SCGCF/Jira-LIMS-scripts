package uk.ac.sanger.scgcf.jira.lims.actions

import groovy.util.logging.Slf4j
import uk.ac.sanger.scgcf.jira.lims.utils.MockFunctions
import uk.ac.sanger.scgcf.jira.services.actions.TransferActions
import uk.ac.sanger.scgcf.jira.services.models.Labware
import uk.ac.sanger.scgcf.jira.services.models.LabwareTypes
import uk.ac.sanger.scgcf.jira.services.models.Material
import uk.ac.sanger.scgcf.jira.services.models.MaterialType
import uk.ac.sanger.scgcf.jira.services.models.Metadatum
import uk.ac.sanger.scgcf.jira.services.models.Receptacle

/**
 * The {@code UATFunctions} class contains functions for the UAT testing workflow.
 *
 * Created by as28 on 01/07/16.
 */

@Slf4j(value = "LOG")
class UATFunctions {

    /**
     * Create a list of well location names for a quadrant in a 384-well plate
     * @param iQuadrant quadrant number 1-4
     * @return list of locations
     */
    static ArrayList<String> getQuadrantLocations(int iQuadrant) {
        LOG.debug "In getQuadrantLocations"
        LOG.debug "quadrant = ${iQuadrant}"

        ArrayList<String> quadrantLocns = []

        switch (iQuadrant) {
            case 1:
                ["A", "C", "E", "G", "I", "K", "M", "O"].each { String row ->
                    1.step(24, 2) { int col ->
                        quadrantLocns.push("" + row + col.toString())
                    }
                }
                break
            case 2:
                ["A", "C", "E", "G", "I", "K", "M", "O"].each { String row ->
                    2.step(25, 2) { int col ->
                        quadrantLocns.push("" + row + col.toString())
                    }
                }
                break
            case 3:
                ["B", "D", "F", "H", "J", "L", "N", "P"].each { String row ->
                    1.step(24, 2) { int col ->
                        quadrantLocns.push("" + row + col.toString())
                    }
                }
                break
            case 4:
                ["B", "D", "F", "H", "J", "L", "N", "P"].each { String row ->
                    2.step(25, 2) { int col ->
                        quadrantLocns.push("" + row + col.toString())
                    }
                }
                break
            default:
                LOG.error "Quadrant not recognised <${iQuadrant.toString()}>"
                break
        }
        LOG.debug "quadrantLocations = ${quadrantLocns}"
        quadrantLocns
    }

    /**
     * Create the customer cell tubes and the single related material in each
     *
     * @return return a list of barcodes in a string and a details string
     */
    static ArrayList<String> createCustomerTubes() {
        LOG.debug "In createCustomerTubes"

        // create 4 tubes including barcodes and 1 metadata each
        ArrayList<Labware> tubeLabwares = (0..3).collect { indx ->
            LOG.debug "Create labware index ${indx}"
            Labware.create(
                    LabwareTypes.GENERIC_TUBE,
                    MockFunctions.createMockExternalId(),
                    [
                            new Metadatum(key: 'tube_name', value: "my_tube_${(indx + 1).toString()}")
                    ],
                    barcodePrefix: 'UATS', barcodeInfo: 'TST'
            )
        }

        // create 4 materials of type Sample with several metadata each
        ArrayList<Material> tubeMaterials = (0..3).collect { indx ->
            Material.create(
                    "sample_${(indx + 1).toString()}" as String,
                    "sample",
                    [
                            new Metadatum(key: 'cust_name', value: "John Smith"),
                            new Metadatum(key: 'cust_sample_name', value: "cust_sample_${(indx + 1).toString()}"),
                            new Metadatum(key: 'organism', value: "human"),
                            new Metadatum(key: 'cell_type', value: "epithelial")
                    ]
            )
        }

        // update the tubes to add the materials (receptacle zero)
        tubeLabwares.eachWithIndex() { curTube, tubeIndex ->
            curTube.receptacles[0].setMaterialUuid(tubeMaterials[tubeIndex].getId())
            curTube.update()
        }

        // fetch the barcodes into a comma-separated string
        ArrayList<String> tubeBarcodeList = []

        // extract other details from tubes and materials
        StringBuilder sb = new StringBuilder();
        sb.append("Tube details: \n")
        tubeLabwares.eachWithIndex { Labware curTube, int indx ->
            tubeBarcodeList.push(curTube.getBarcode())
            sb.with {
                append("Tube number: ${(indx + 1).toString()} \n")
                append("Labware UUID: ${curTube.getId().toString()} \n")
                append("Ext Id: ${curTube.getExternalId()} \n")
                append("BC: ${curTube.getBarcode()} \n")
                append("Material UUID: ${curTube.getReceptacles()[0].materialUuid} \n")
                append("Created at: ${curTube.getCreated_at()} \n\n")
            }
        }
        String tubeBarcodesString = tubeBarcodeList.join(",")
        LOG.debug("tubeBarcodesString = ${tubeBarcodesString}")
        LOG.debug("tubeDetails = ${sb.toString()}")

        // return two parameters
        [tubeBarcodesString, sb.toString()]
    }

    /**
     * Take a list of tube barcodes, select the tubes and their materials, and split them
     * across 4 destination 96-well plates
     *
     * @param tubeBarcodesList
     * @return the Barcodes of the 4 destination plates and details of contents
     */
    static ArrayList<String> splitCustTubes(ArrayList<String> tubeBarcodesList) {
        LOG.debug "In splitCustTubes"
        LOG.debug "tubeBarcodesList = ${tubeBarcodesList.toString()}"

        // fetch the 4 source tubes
        ArrayList<Labware> sourceTubeLabwares = (0..3).collect { indx ->
            LOG.debug "findByBarcode where barcode = ${tubeBarcodesList.get(indx)}"
            Labware.findByBarcode(tubeBarcodesList.get(indx))
        }
        LOG.debug "sourceTubes size = ${sourceTubeLabwares.size()}"

        // fetch the 4 source materials
        ArrayList<Material> sourceMaterials = (0..3).collect { indx ->
            // one material per tube, fetch the material id
            String srcMaterialUUID = sourceTubeLabwares.get(indx).materialUuids().get(0)
            LOG.debug "srcMaterialUUID = ${srcMaterialUUID}"
            Material.getMaterials([srcMaterialUUID]).get(0)
        }
        LOG.debug "sourceMaterials size = ${sourceMaterials.size()}"

        // create 4 destination 96-well plate labwares with barcodes and a single metadata
        ArrayList<Labware> destPlateLabwares = (0..3).collect { indx ->
            LOG.debug "Create labware index ${indx}"
            Labware.create(
                    LabwareTypes.GENERIC_96_PLATE,
                    MockFunctions.createMockExternalId(),
                    [
                            new Metadatum(key: 'plate_name', value: "my_sorted_plate_${(indx + 1).toString()}")
                    ],
                    barcodePrefix: 'UATS', barcodeInfo: 'TST'
            )
        }

        MaterialType destMaterialType = new MaterialType(name: 'sample')

        // for tubes 1 & 2 split into columns 1-6 and 7-12 on destination plate 1, copying metadata 'cust_name'
        // and creating 1 new metadata 'cell_initial_name' = <plate barcode>_<well_location>
        def destPlate1ALocns = []
        def destPlate1AMetaData = [:]
        ('A'..'H').each { row ->
            (1..6).each { col ->
                String curWellLocn = "${row}${col}"
                destPlate1ALocns.push(curWellLocn)
                destPlate1AMetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'cell_initial_name',
                                value: "${destPlateLabwares.get(0).getBarcode()}_${curWellLocn}"
                        )
                ]
            }
        }
        LOG.debug "destPlate1ALocns = ${destPlate1ALocns}"
        LOG.debug "destPlate1AMetaData size = ${destPlate1AMetaData.size()}"

        // split from tube 1 into plate 1
        destPlateLabwares.set(0, (Labware) TransferActions.split(sourceTubeLabwares.get(0), destPlateLabwares.get(0),
                destMaterialType, destPlate1ALocns, ['cust_sample_name'], destPlate1AMetaData))

        LOG.debug "number of materials in plate 1 at stage A = ${destPlateLabwares.get(0).materialUuids().size()}"

        def destPlate1BLocns = []
        def destPlate1BMetaData = [:]
        ('A'..'H').each { row ->
            (7..12).each { col ->
                String curWellLocn = "${row}${col}"
                destPlate1BLocns.push(curWellLocn)
                destPlate1BMetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'cell_initial_name',
                                value: "${destPlateLabwares.get(0).getBarcode()}_${curWellLocn}"
                        )
                ]
            }
        }
        LOG.debug "destPlate1BLocns = ${destPlate1BLocns}"
        LOG.debug "destPlate1BMetaData = ${destPlate1BMetaData}"

        // split from tube 2 into plate 1
        destPlateLabwares.set(0, (Labware) TransferActions.split(sourceTubeLabwares.get(1), destPlateLabwares.get(0),
                destMaterialType, destPlate1BLocns, ['cust_sample_name'], destPlate1BMetaData))

        LOG.debug "number of materials in plate 1 at stage B = ${destPlateLabwares.get(0).materialUuids().size()}"

        // for tube 3 split into destination plate 2 all wells, copying metadata 'cust_name'
        // and creating 1 new metadata 'cell_initial_name' = <plate barcode>_<well_location>
        def destPlate2Locns = []
        def destPlate2MetaData = [:]
        ('A'..'H').each { row ->
            (1..12).each { col ->
                String curWellLocn = "${row}${col}"
                destPlate2Locns.push(curWellLocn)
                destPlate2MetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'cell_initial_name',
                                value: "${destPlateLabwares.get(1).getBarcode()}_${curWellLocn}"
                        )
                ]
            }
        }
        LOG.debug "destPlate2Locns = ${destPlate2Locns}"
        LOG.debug "destPlate2MetaData size = ${destPlate2MetaData.size()}"

        // split from tube 3 into plate 2
        destPlateLabwares.set(1, (Labware) TransferActions.split(sourceTubeLabwares.get(2), destPlateLabwares.get(1),
                destMaterialType, destPlate2Locns, ['cust_sample_name'], destPlate2MetaData))

        LOG.debug "number of materials in plate 2 = ${destPlateLabwares.get(1).materialUuids().size()}"

        // for tube 4 split into destination plates 3 and 4 all wells, copying metadata 'cust_name'
        // and creating 1 new metadata 'cell_initial_name' = <plate barcode>_<well_location>

        // plate 3
        def destPlate3Locns = []
        def destPlate3MetaData = [:]
        ('A'..'H').each { row ->
            (1..12).each { col ->
                String curWellLocn = "${row}${col}"
                destPlate3Locns.push(curWellLocn)
                destPlate3MetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'cell_initial_name',
                                value: "${destPlateLabwares.get(2).getBarcode()}_${curWellLocn}"
                        )
                ]
            }
        }
        LOG.debug "destPlate3Locns = ${destPlate3Locns}"
        LOG.debug "destPlate3MetaData size = ${destPlate3MetaData.size()}"

        // split from tube 4 into plate 3
        destPlateLabwares.set(2, (Labware) TransferActions.split(sourceTubeLabwares.get(3), destPlateLabwares.get(2),
                destMaterialType, destPlate3Locns, ['cust_sample_name'], destPlate3MetaData))

        LOG.debug "number of materials in plate 3 = ${destPlateLabwares.get(2).materialUuids().size()}"

        // plate 4
        def destPlate4Locns = []
        def destPlate4MetaData = [:]
        ('A'..'H').each { row ->
            (1..12).each { col ->
                String curWellLocn = "${row}${col}"
                destPlate4Locns.push(curWellLocn)
                destPlate4MetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'cell_initial_name',
                                value: "${destPlateLabwares.get(3).getBarcode()}_${curWellLocn}"
                        )
                ]
            }
        }
        LOG.debug "destPlate4Locns = ${destPlate4Locns}"
        LOG.debug "destPlate4MetaData size = ${destPlate4MetaData.size()}"

        // split from tube 4 into plate 4
        destPlateLabwares.set(3, (Labware) TransferActions.split(sourceTubeLabwares.get(3), destPlateLabwares.get(3),
                destMaterialType, destPlate4Locns, ['cust_sample_name'], destPlate4MetaData))

        LOG.debug "number of materials in plate 4 = ${destPlateLabwares.get(3).materialUuids().size()}"

        // fetch the barcodes into a comma-separated string
        ArrayList<String> destPlatesBarcodeList = []

        // extract other details from plates and materials
        StringBuilder sb = new StringBuilder()

        LOG.debug "Split plate details:"
        sb.append("Split plate details:\n")
        destPlateLabwares.eachWithIndex { Labware curPlate, int indx ->
            destPlatesBarcodeList.push(curPlate.getBarcode())
            sb.with {
                append("Plate number: ${(indx + 1).toString()}\n")
                append("Labware UUID: ${curPlate.getId().toString()}\n")
                append("Ext Id: ${curPlate.getExternalId()}\n")
                append("BC: ${curPlate.getBarcode()}\n")
                append("Number of Materials: ${curPlate.materialUuids().size()}\n")
                append("Created at: ${curPlate.getCreated_at()}\n")

                LOG.debug("Plt num: ${(indx + 1).toString()}\n")
                LOG.debug("LW UUID: ${curPlate.getId().toString()}\n")
                LOG.debug("Ext Id: ${curPlate.getExternalId()}\n")
                LOG.debug("BC: ${curPlate.getBarcode()}\n")
                LOG.debug("Number of Materials: ${curPlate.materialUuids().size()}\n")
                LOG.debug("Created at: ${curPlate.getCreated_at()}\n")
                LOG.debug("Wells:\n")
                // well material details
                (0..95).each { wellIndx ->
                    String curMatId = curPlate.receptacles[wellIndx].materialUuid
                    String curLocnName = curPlate.receptacles[wellIndx].location.name
                    Material curMat = Material.getMaterials([curMatId])[0]
//                    String parMatId = curMat.parents[0].id
//                    LOG.debug("${curLocnName} Id = ${curMat.getName()} Parent = ${curMatId},${parMatId}\n")
                }
                append("\n")
            }
        }
        String destPlateBarcodesString = destPlatesBarcodeList.join(",")
        LOG.debug("splitPlateBarcodesString = ${destPlateBarcodesString}")

        [destPlateBarcodesString, sb.toString()]
    }

    /**
     * Combine 4 sorted 96-well cell plates into single 384-well combined plate
     *
     * @param plateBarcodesList - source plate barcodes
     * @return combined plate barcode and details
     */
    static ArrayList<String> combineSortedPlates(ArrayList<String> plateBarcodesList) {
        LOG.debug "In combineSortedPlates"
        LOG.debug "plateBarcodesList = ${plateBarcodesList.toString()}"

        // fetch the 4 source plates
        ArrayList<Labware> sourcePlateLabwares = (0..3).collect { indx ->
            LOG.debug "Calling findByBarcode where barcode = ${plateBarcodesList.get(indx)}"
            Labware.findByBarcode(plateBarcodesList.get(indx))
        }
        LOG.debug "sourcePlateLabwares size = ${sourcePlateLabwares.size()}"

        // create 1 destination 384-well plate labware with barcode and a single metadata
        Labware destCmbPlateLabware = Labware.create(
                LabwareTypes.GENERIC_384_PLATE,
                MockFunctions.createMockExternalId(),
                [
                        new Metadatum(key: 'plate_name', value: "my_combined_plate")
                ],
                barcodePrefix: 'UATS', barcodeInfo: 'TST'
        )

        MaterialType destMaterialType = new MaterialType(name: 'sample')

        // create a metadata for each well in 384-well plate
        def destCmbPlateMetaData = [:]
        ('A'..'P').each { row ->
            (1..24).each { col ->
                String curWellLocn = "${row}${col}"
                destCmbPlateMetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'cmb_plate_md',
                                value: "example_value_for_${curWellLocn}"
                        )
                ]
            }
        }

        // combine the 4 sources into the destination
        destCmbPlateLabware = (Labware) TransferActions.combine(
                sourcePlateLabwares,
                destCmbPlateLabware,
                destMaterialType,
                ["cust_sample_name", "cell_initial_name"],
                destCmbPlateMetaData
        )

        // create quadrant locations
        ArrayList<String> quadrant1Locns = getQuadrantLocations(1)
        ArrayList<String> quadrant2Locns = getQuadrantLocations(2)
        ArrayList<String> quadrant3Locns = getQuadrantLocations(3)
        ArrayList<String> quadrant4Locns = getQuadrantLocations(4)

        // extract other details from the combined 384 well plate
        StringBuilder sb = new StringBuilder();
        LOG.debug "Combined plate details:"
        sb.append("Combined plate details: \n")

        sb.with {
            append("Labware UUID: ${destCmbPlateLabware.getId().toString()} \n")
            append("Ext Id: ${destCmbPlateLabware.getExternalId()} \n")
            append("BC: ${destCmbPlateLabware.getBarcode()} \n")
            append("Created at: ${destCmbPlateLabware.getCreated_at()} \n\n")
            append("Number of quadrant 1 wells: ${quadrant1Locns.size()}")

            LOG.debug("Labware UUID: ${destCmbPlateLabware.getId().toString()} \n")
            LOG.debug("Ext Id: ${destCmbPlateLabware.getExternalId()} \n")
            LOG.debug("BC: ${destCmbPlateLabware.getBarcode()} \n")
            LOG.debug("Created at: ${destCmbPlateLabware.getCreated_at()} \n\n")
            LOG.debug("Number of quadrant 1 wells: ${quadrant1Locns.size()}")
            LOG.debug("Quadrant 1 wells:\n")
            quadrant1Locns.each { String curLocn ->
                String curMatId = destCmbPlateLabware.receptacles.find { r -> r.location.name == curLocn }.materialUuid
                Material curMat = Material.getMaterials([curMatId])[0]
                String parMatId = curMat.parents[0].getId()
                // have to then fetch the parent material as whole object is not present in current material
                Material parMat = Material.getMaterials([parMatId])[0]
                // select value(s) from parent metadata
                List<Metadatum> parMds = parMat.getMetadata()
                String parMdVal = ""
                parMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "cell_initial_name") {
                        parMdVal = curMd.getValue()
                    }
                }
                LOG.debug("${curLocn} : ${curMat.getName()} : Id = ${curMatId} : Parent Id = ${parMatId} : Cell initial name = ${parMdVal} \n")
            }
            append("\n")
            append("Number of quadrant 2 wells: ${quadrant2Locns.size()}")

            LOG.debug("Quadrant 2 wells:\n")
            quadrant2Locns.each { String curLocn ->
                String curMatId = destCmbPlateLabware.receptacles.find { r -> r.location.name == curLocn }.materialUuid
                Material curMat = Material.getMaterials([curMatId])[0]
                String parMatId = curMat.parents[0].getId()
                // have to then fetch the parent material as whole object is not present in current material
                Material parMat = Material.getMaterials([parMatId])[0]
                // select value(s) from parent metadata
                List<Metadatum> parMds = parMat.getMetadata()
                String parMdVal = ""
                parMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "cell_initial_name") {
                        parMdVal = curMd.getValue()
                    }
                }
                LOG.debug("${curLocn} : ${curMat.getName()} : Id = ${curMatId} : Parent Id = ${parMatId} : Cell initial name = ${parMdVal} \n")
            }
            append("\n")
            append("Number of quadrant 3 wells: ${quadrant3Locns.size()}")

            LOG.debug("Quadrant 3 wells:\n")
            quadrant3Locns.each { String curLocn ->
                String curMatId = destCmbPlateLabware.receptacles.find { r -> r.location.name == curLocn }.materialUuid
                Material curMat = Material.getMaterials([curMatId])[0]
                String parMatId = curMat.parents[0].getId()
                // have to then fetch the parent material as whole object is not present in current material
                Material parMat = Material.getMaterials([parMatId])[0]
                // select value(s) from parent metadata
                List<Metadatum> parMds = parMat.getMetadata()
                String parMdVal = ""
                parMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "cell_initial_name") {
                        parMdVal = curMd.getValue()
                    }
                }
                LOG.debug("${curLocn} : ${curMat.getName()} : Id = ${curMatId} : Parent Id = ${parMatId} : Cell initial name = ${parMdVal} \n")
            }
            append("\n")
            append("Number of quadrant 4 wells: ${quadrant4Locns.size()}")

            LOG.debug("Quadrant 4 wells:\n")
            quadrant4Locns.each { String curLocn ->
                String curMatId = destCmbPlateLabware.receptacles.find { r -> r.location.name == curLocn }.materialUuid
                Material curMat = Material.getMaterials([curMatId])[0]
                String parMatId = curMat.parents[0].getId()
                // have to then fetch the parent material as whole object is not present in current material
                Material parMat = Material.getMaterials([parMatId])[0]
                // select value(s) from parent metadata
                List<Metadatum> parMds = parMat.getMetadata()
                String parMdVal = ""
                parMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "cell_initial_name") {
                        parMdVal = curMd.getValue()
                    }
                }
                LOG.debug("${curLocn} : ${curMat.getName()} : Id = ${curMatId} : Parent Id = ${parMatId} : Cell initial name = ${parMdVal} \n")
            }
            append("\n")

        }
        LOG.debug("cmbPlateBarcode = ${destCmbPlateLabware.getBarcode()}")
        LOG.debug("cmbPlate details string = ${sb.toString()}")

        // return two parameters
        [destCmbPlateLabware.getBarcode(), sb.toString()]

    }

    /**
     * Cherry pick specific wells from the combine plate into a new plate
     *
     * @param sourcePlateBarcode
     * @return cherry pick plate barcode and details
     */
    static ArrayList<String> cherryPickPlates(ArrayList<String> plateBarcodesList) {
        LOG.debug "In cherryPickPlates"
        LOG.debug "plateBarcodesList = ${plateBarcodesList.toString()}"

        // fetch the 4 source plates
        ArrayList<Labware> sourcePlateLabwares = (0..3).collect { indx ->
            LOG.debug "Calling findByBarcode where barcode = ${plateBarcodesList.get(indx)}"
            Labware.findByBarcode(plateBarcodesList.get(indx))
        }
        LOG.debug "sourcePlateLabwares size = ${sourcePlateLabwares.size()}"

        // create 1 destination 96-well plate labware with barcode and a single metadata
        Labware destChryPickPlateLabware = Labware.create(
                LabwareTypes.GENERIC_96_PLATE,
                MockFunctions.createMockExternalId(),
                [
                        new Metadatum(key: 'plate_name', value: "my_cherrypick_plate")
                ],
                barcodePrefix: 'UATS', barcodeInfo: 'TST'
        )

        MaterialType destMaterialType = new MaterialType(name: 'sample')

        ArrayList<String> sourcePlate1LocationsToCopy = []
        ArrayList<String> sourcePlate2LocationsToCopy = []
        ArrayList<String> sourcePlate3LocationsToCopy = []
        ArrayList<String> sourcePlate4LocationsToCopy = []

        // for this
        ['A', 'B'].each { String row ->
            (1..12).each { int col ->
                sourcePlate1LocationsToCopy.push("" + row + col.toString())
            }
        }
        ['C', 'D'].each { String row ->
            (1..12).each { int col ->
                sourcePlate2LocationsToCopy.push("" + row + col.toString())
            }
        }
        ['E', 'F'].each { String row ->
            (1..12).each { int col ->
                sourcePlate3LocationsToCopy.push("" + row + col.toString())
            }
        }
        ['G', 'H'].each { String row ->
            (1..12).each { int col ->
                sourcePlate4LocationsToCopy.push("" + row + col.toString())
            }
        }
        LOG.debug "sourcePlate1LocationsToCopy = ${sourcePlate1LocationsToCopy}"
        LOG.debug "sourcePlate2LocationsToCopy = ${sourcePlate2LocationsToCopy}"
        LOG.debug "sourcePlate3LocationsToCopy = ${sourcePlate3LocationsToCopy}"
        LOG.debug "sourcePlate4LocationsToCopy = ${sourcePlate4LocationsToCopy}"

        ArrayList<String> destLocns = []
        ('A'..'H').each { String row ->
            (1..12).each { int col ->
                destLocns.push("" + row + col.toString())
            }
        }

        LOG.debug "destLocns = ${destLocns.toString()}"
        LOG.debug "destLocns size = ${destLocns.size()}"

        // set up the transfers
        ArrayList<TransferActions.Mapping> transferMappings = []
        destLocns.eachWithIndex { String curLocn, int destLocnIndx ->
            if (destLocnIndx < 24) {
                transferMappings.push(new TransferActions.Mapping(
                        sourceBarcode: sourcePlateLabwares[0].barcode,
                        destinationBarcode: destChryPickPlateLabware.barcode,
                        sourceLocation: sourcePlate1LocationsToCopy[destLocnIndx],
                        destinationLocation: curLocn
                ))
            } else if (destLocnIndx >= 24 && destLocnIndx < 48) {
                transferMappings.push(new TransferActions.Mapping(
                        sourceBarcode: sourcePlateLabwares[1].barcode,
                        destinationBarcode: destChryPickPlateLabware.barcode,
                        sourceLocation: sourcePlate2LocationsToCopy[destLocnIndx - 24],
                        destinationLocation: curLocn
                ))
            } else if (destLocnIndx >= 48 && destLocnIndx < 72) {
                transferMappings.push(new TransferActions.Mapping(
                        sourceBarcode: sourcePlateLabwares[2].barcode,
                        destinationBarcode: destChryPickPlateLabware.barcode,
                        sourceLocation: sourcePlate3LocationsToCopy[destLocnIndx - 48],
                        destinationLocation: curLocn
                ))
            } else if (destLocnIndx >= 72) {
                transferMappings.push(new TransferActions.Mapping(
                        sourceBarcode: sourcePlateLabwares[3].barcode,
                        destinationBarcode: destChryPickPlateLabware.barcode,
                        sourceLocation: sourcePlate4LocationsToCopy[destLocnIndx - 72],
                        destinationLocation: curLocn
                ))
            }
        }

        LOG.debug "transferMappings size = ${transferMappings.size()}"

        // create a metadata for each well in 96-well plate
        def destChryPickPlateMetaData = [:]
        ('A'..'H').each { row ->
            (1..12).each { col ->
                String curWellLocn = "" + row + col
                destChryPickPlateMetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'chrypick_plate_md',
                                value: "example_value_for_${curWellLocn}"
                        )
                ]
            }
        }

        // perform the cherry pick transfer
        ArrayList<Labware> destChryPickLabwares = [destChryPickPlateLabware]
        destChryPickLabwares = (ArrayList<Labware>) TransferActions.cherrypick(
                sourcePlateLabwares,
                destChryPickLabwares,
                new MaterialType(name: 'sample'),
                transferMappings,
                ["cell_initial_name"],
                destChryPickPlateMetaData
        )

        LOG.debug "Number of labwares returned from cherry pick = ${destChryPickLabwares.size()}"
        destChryPickPlateLabware = destChryPickLabwares[0]

        // extract other details from plate and materials
        StringBuilder sb = new StringBuilder();
        LOG.debug "Cherry Pick plate details:"
        sb.append("Cherry Pick plate details: \n")
        sb.with {
            append("Labware UUID: ${destChryPickPlateLabware.getId().toString()} \n")
            append("Ext Id: ${destChryPickPlateLabware.getExternalId()} \n")
            append("BC: ${destChryPickPlateLabware.getBarcode()} \n")
            append("Number of Materials: ${destChryPickPlateLabware.materialUuids().size()} \n")
            append("Created at: ${destChryPickPlateLabware.getCreated_at()} \n")

            LOG.debug("Labware UUID: ${destChryPickPlateLabware.getId().toString()} \n")
            LOG.debug("Ext Id: ${destChryPickPlateLabware.getExternalId()} \n")
            LOG.debug("BC: ${destChryPickPlateLabware.getBarcode()} \n")
            LOG.debug("Number of Materials: ${destChryPickPlateLabware.materialUuids().size()} \n")
            LOG.debug("Created at: ${destChryPickPlateLabware.getCreated_at()} \n")
            LOG.debug("Wells: \n")
            // well material details
            (0..95).each { wellIndx ->
                String curMatId = destChryPickPlateLabware.receptacles[wellIndx].materialUuid
                String curLocnName = destChryPickPlateLabware.receptacles[wellIndx].location.name
                Material curMat = Material.getMaterials([curMatId])[0]
                List<Metadatum> curMds = curMat.getMetadata()
                String curMdVal = ""
                curMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "chrypick_plate_md") {
                        curMdVal = curMd.getValue()
                    }
                }
                String parMatId = curMat.parents[0].getId()
                // have to then fetch the parent material as whole object is not present in current material
                Material parMat = Material.getMaterials([parMatId])[0]
                // select value(s) from parent metadata
                List<Metadatum> parMds = parMat.getMetadata()
                String parMdVal = ""
                parMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "cell_initial_name") {
                        parMdVal = curMd.getValue()
                    }
                }
                LOG.debug("${curLocnName} : ${curMat.getName()} : Id = ${curMatId} : Val = ${curMdVal} : Parent Id = ${parMatId} : Cell initial name = ${parMdVal} \n")
            }
            append("\n")
        }

        LOG.debug("cherryPickPlate barcode = ${destChryPickPlateLabware.getBarcode()}")
        LOG.debug("cherryPickPlate details string = ${sb.toString()}")

        // return two parameters
        [destChryPickPlateLabware.getBarcode(), sb.toString()]

    }

    /**
     * Stamp the 384-well combine plate into a new 384-well plate, well for well
     *
     * @param sourcePlateBarcode
     * @return stamped plate barcode and details
     */
    static ArrayList<String> stampPlate(String sourcePlateBarcode) {
        LOG.debug "In stampPlate"
        LOG.debug "sourcePlateBarcode = ${sourcePlateBarcode}"

        // fetch the source labware
        Labware srcPlateLabware = Labware.findByBarcode(sourcePlateBarcode)

        // create 1 destination 384-well plate labware with barcode and a single metadata
        Labware destStampPlateLabware = Labware.create(
                LabwareTypes.GENERIC_384_PLATE,
                MockFunctions.createMockExternalId(),
                [
                        new Metadatum(key: 'plate_name', value: "my_stamped_plate")
                ],
                barcodePrefix: 'UATS', barcodeInfo: 'TST'
        )

        // create materialType
        MaterialType destMaterialType = new MaterialType(name: 'sample')

        // create two new metadata for each well in 384-well plate
        def destStampPlateMetaData = [:]
        Random random = new Random()
        ('A'..'P').each { row ->
            (1..24).each { col ->
                String curWellLocn = "${row}${col}"
                int iConc = random.nextInt(100 + 1) // generate a random concentration 0-100
                int iPass = 0
                if (iConc > 10) {
                    iPass = 1
                } // pass approx 90% of samples
                destStampPlateMetaData[curWellLocn] = [
                        new Metadatum(
                                key: 'cDNA_conc',
                                value: "${iConc.toString()}" // rndm value 0 -> 100
                        ),
                        new Metadatum(
                                key: 'passed_for_selective_stamp',
                                value: "${iPass.toString()}" // 1 or 0 for true or false
                        )
                ]
            }
        }

        // stamp the plate
        destStampPlateLabware = (Labware) TransferActions.stamp(
                srcPlateLabware,
                destStampPlateLabware,
                destMaterialType,
                ["cust_sample_name", "cell_initial_name"],
                destStampPlateMetaData
        )

        // extract other details from plate and materials
        StringBuilder sb = new StringBuilder();
        LOG.debug "Stamp plate details:"
        sb.append("Stamp plate details: \n")
        sb.with {
            append("Labware UUID: ${destStampPlateLabware.getId().toString()} \n")
            append("Ext Id: ${destStampPlateLabware.getExternalId()} \n")
            append("BC: ${destStampPlateLabware.getBarcode()} \n")
            append("Number of Materials: ${destStampPlateLabware.materialUuids().size()} \n")
            append("Created at: ${destStampPlateLabware.getCreated_at()} \n")

            LOG.debug("Labware UUID: ${destStampPlateLabware.getId().toString()} \n")
            LOG.debug("Ext Id: ${destStampPlateLabware.getExternalId()} \n")
            LOG.debug("BC: ${destStampPlateLabware.getBarcode()} \n")
            LOG.debug("Number of Materials: ${destStampPlateLabware.materialUuids().size()} \n")
            LOG.debug("Created at: ${destStampPlateLabware.getCreated_at()} \n")
            LOG.debug("Wells: \n")
            // well material details
            (0..383).each { wellIndx ->
                String curMatId = destStampPlateLabware.receptacles[wellIndx].materialUuid
                String curLocnName = destStampPlateLabware.receptacles[wellIndx].location.name
                Material curMat = Material.getMaterials([curMatId])[0]
                List<Metadatum> curMds = curMat.getMetadata()
                String curMdConc = ""
                String curMdPass = ""
                curMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "cDNA_conc") {
                        curMdConc = curMd.getValue()
                    }
                    if (curMd.getKey() == "passed_for_selective_stamp") {
                        curMdPass = curMd.getValue()
                    }
                }
                String parMatId = curMat.parents[0].getId()
                // have to then fetch the parent material as whole object is not present in current material
                Material parMat = Material.getMaterials([parMatId])[0]
                // select value(s) from parent metadata
                List<Metadatum> parMds = parMat.getMetadata()
                String parMdVal = ""
                parMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "cell_initial_name") {
                        parMdVal = curMd.getValue()
                    }
                }
                LOG.debug("${curLocnName} : ${curMat.getName()} : Id = ${curMatId} : Conc = ${curMdConc} : Pass = ${curMdPass} : Parent Id = ${parMatId} : Cell initial name = ${parMdVal} \n")
            }
            append("\n")
        }

        LOG.debug("stampPlateBarcode = ${destStampPlateLabware.getBarcode()}")
        LOG.debug("stampPlate details string = ${sb.toString()}")

        // return two parameters
        [destStampPlateLabware.getBarcode(), sb.toString()]
    }

    /**
     * Selectively stamp materials from the stamp plate to a new selective stamp plate
     * just taking those wells that have a metadata indicating they're passed
     *
     * @param sourcePlateBarcode
     * @return destination plate barcode and details
     */
    static ArrayList<String> selectiveStampPlate(String sourcePlateBarcode) {
        LOG.debug "In selectiveStampPlate"
        LOG.debug "sourcePlateBarcode = ${sourcePlateBarcode}"

        // fetch the source labware
        Labware srcPlateLabware = Labware.findByBarcode(sourcePlateBarcode)

        // go through the metadata values, build list of location names for any with a passed_for_selective_stamp == 1
        ArrayList<String> locnsToStamp = []
        srcPlateLabware.getReceptacles().each { Receptacle curReceptacle ->
            String locnName = curReceptacle.getLocation().getName()
            Material curMat = Material.getMaterials([curReceptacle.getMaterialUuid()])[0]
            List<Metadatum> curMds = curMat.getMetadata()
            String curMdPass = ""
            curMds.each { Metadatum curMd ->
                if (curMd.getKey() == "passed_for_selective_stamp") {
                    curMdPass = curMd.getValue()
                }
            }
            if (curMdPass == "1") {
                locnsToStamp.push(locnName)
            }
        }

        LOG.debug "Locations to stamp = ${locnsToStamp}"

        // create 1 destination 384-well plate labware with barcode and a single metadata
        Labware destSelStampPlateLabware = Labware.create(
                LabwareTypes.GENERIC_384_PLATE,
                MockFunctions.createMockExternalId(),
                [
                        new Metadatum(key: 'plate_name', value: "my_stamped_plate")
                ],
                barcodePrefix: 'UATS', barcodeInfo: 'TST'
        )

        // create materialType
        MaterialType destMaterialType = new MaterialType(name: 'sample')

        // create three new metadata for each well in 384-well plate
        def destSelStampPlateMetaData = [:]
        Random random = new Random()
        ('A'..'P').each { row ->
            (1..24).each { col ->
                String curWellLocn = "${row}${col}"
                // only set metadata for those wells that passed from stamp plate
                if (locnsToStamp.contains(curWellLocn)) {
                    int iRnd = random.nextInt(100 + 1) // generate a random number 0-100
                    int iPass = 0
                    if (iRnd > 10) {
                        iPass = 1
                    } // pass approx 90% of remaining samples for pooling
                    destSelStampPlateMetaData[curWellLocn] = [
                            new Metadatum(
                                    key: 'tag_1',
                                    value: "tag_${row}" // represents seq tag 1
                            ),
                            new Metadatum(
                                    key: 'tag_2',
                                    value: "tag_${col.toString()}" // represents seq tag 2
                            ),
                            new Metadatum(
                                    key: 'passed_for_library_prep',
                                    value: "${iPass.toString()}" // represents nextera decision on pass/fail
                            )
                    ]
                }
            }
        }

        // selectively stamp the source to the destination, adding new metadata for tag_1, tag_2 and passed_for_library_prep
        TransferActions.selectiveStamp(
                srcPlateLabware,
                destSelStampPlateLabware,
                destMaterialType,
                locnsToStamp,
                ["cust_sample_name", "cell_initial_name"],
                destSelStampPlateMetaData
        )

        // extract other details from plate and materials
        StringBuilder sb = new StringBuilder();
        LOG.debug "Selective Stamp plate details:"
        sb.append("Selective Stamp plate details: \n")
        sb.with {
            append("Labware UUID: ${destSelStampPlateLabware.getId().toString()} \n")
            append("Ext Id: ${destSelStampPlateLabware.getExternalId()} \n")
            append("BC: ${destSelStampPlateLabware.getBarcode()} \n")
            append("Number of Materials: ${destSelStampPlateLabware.materialUuids().size()} \n")
            append("Created at: ${destSelStampPlateLabware.getCreated_at()} \n")

            LOG.debug("Labware UUID: ${destSelStampPlateLabware.getId().toString()} \n")
            LOG.debug("Ext Id: ${destSelStampPlateLabware.getExternalId()} \n")
            LOG.debug("BC: ${destSelStampPlateLabware.getBarcode()} \n")
            LOG.debug("Number of Materials: ${destSelStampPlateLabware.materialUuids().size()} \n")
            LOG.debug("Created at: ${destSelStampPlateLabware.getCreated_at()} \n")
            LOG.debug("Wells: \n")
            // well material details
            (0..383).each { wellIndx ->
                String curWellLocn = destSelStampPlateLabware.receptacles[wellIndx].location.name
                // only display those wells that passed from stamp plate
                if (locnsToStamp.contains(curWellLocn)) {
                    String curMatId = destSelStampPlateLabware.receptacles[wellIndx].materialUuid
                    Material curMat = Material.getMaterials([curMatId])[0]
                    List<Metadatum> curMds = curMat.getMetadata()
                    String curMdTag1, curMdTag2, curMdPass
                    curMds.each { Metadatum curMd ->
                        if (curMd.getKey() == "tag_1") {
                            curMdTag1 = curMd.getValue()
                        }
                        if (curMd.getKey() == "tag_2") {
                            curMdTag2 = curMd.getValue()
                        }
                        if (curMd.getKey() == "passed_for_library_prep") {
                            curMdPass = curMd.getValue()
                        }
                    }
                    String parMatId = curMat.parents[0].getId()
                    // have to then fetch the parent material as whole object is not present in current material
                    Material parMat = Material.getMaterials([parMatId])[0]
                    // select value(s) from parent metadata
                    List<Metadatum> parMds = parMat.getMetadata()
                    String parMdVal = ""
                    parMds.each { Metadatum curMd ->
                        if (curMd.getKey() == "cell_initial_name") {
                            parMdVal = curMd.getValue()
                        }
                    }
                    LOG.debug("${curWellLocn} : ${curMat.getName()} : Id = ${curMatId} : Tag1/2 = ${curMdTag1}/${curMdTag2} : Pass = ${curMdPass} : Parent Id = ${parMatId} : Cell initial name = ${parMdVal} \n")
                }
            }
            append("\n")
        }

        LOG.debug("selStampPlateBarcode = ${destSelStampPlateLabware.getBarcode()}")
        LOG.debug("selStampPlate details string = ${sb.toString()}")

        // return two parameters
        [destSelStampPlateLabware.getBarcode(), sb.toString()]
    }

    /**
     * Pool from the selective stamp plate into 4 tubes by quadrant and if metadata pass
     *
     * @param sourcePlateBarcode
     * @return destination tube barcodes and details
     */
    static ArrayList<String> poolToTubes(String sourcePlateBarcode) {
        LOG.debug "In poolToTubes"
        LOG.debug "sourcePlateBarcode = ${sourcePlateBarcode}"

        // fetch source plate labware
        Labware srcPlateLabware = Labware.findByBarcode(sourcePlateBarcode)

        // go through the metadata values for each source well, and build a list of location names
        // by quadrant for any with a passed_for_library_prep == 1
        ArrayList<String> quadrant1Locns = getQuadrantLocations(1)
        ArrayList<String> quadrant2Locns = getQuadrantLocations(2)
        ArrayList<String> quadrant3Locns = getQuadrantLocations(3)
        ArrayList<String> quadrant4Locns = getQuadrantLocations(4)

        ArrayList<String> quadrant1LocnsToPool = []
        ArrayList<String> quadrant2LocnsToPool = []
        ArrayList<String> quadrant3LocnsToPool = []
        ArrayList<String> quadrant4LocnsToPool = []

        ArrayList<String> quadrant1InitialCellNames = []
        ArrayList<String> quadrant2InitialCellNames = []
        ArrayList<String> quadrant3InitialCellNames = []
        ArrayList<String> quadrant4InitialCellNames = []

        srcPlateLabware.getReceptacles().each { Receptacle curReceptacle ->
            String locnName = curReceptacle.getLocation().getName()
            LOG.debug "source well locn name = ${locnName}"
            LOG.debug "source well locn name class = ${locnName.getClass()}"
            if (curReceptacle.getMaterialUuid() != null) {
                LOG.debug "mat id = ${curReceptacle.getMaterialUuid()}"
                Material curMat = Material.getMaterials([curReceptacle.getMaterialUuid()])[0]
                List<Metadatum> curMds = curMat.getMetadata()
                LOG.debug "curMds size = ${curMds.size()}"
                String curMdPass = ""
                String curMdCellName = ""
                curMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "passed_for_library_prep") {
                        curMdPass = curMd.getValue()
                    }
                    if (curMd.getKey() == "cell_initial_name") {
                        curMdCellName = curMd.getValue()
                    }
                }
                LOG.debug "curMdPass = ${curMdPass}"
                LOG.debug "curMdCellName = ${curMdCellName}"
                if (curMdPass != null && curMdPass == "1") {
                    LOG.debug "setting locn to pool and cell names for locn ${locnName}"
                    // add the location to the relevant pools list by quadrant
                    // add the cell initial name to the relevant list by quadrant
                    if (quadrant1Locns.contains(locnName)) {
                        quadrant1LocnsToPool.push("" + locnName)
                        quadrant1InitialCellNames.push("" + curMdCellName)
                    } else if (quadrant2Locns.contains(locnName)) {
                        quadrant2LocnsToPool.push("" + locnName)
                        quadrant2InitialCellNames.push("" + curMdCellName)
                    } else if (quadrant3Locns.contains(locnName)) {
                        quadrant3LocnsToPool.push("" + locnName)
                        quadrant3InitialCellNames.push("" + curMdCellName)
                    } else if (quadrant4Locns.contains(locnName)) {
                        quadrant4LocnsToPool.push("" + locnName)
                        quadrant4InitialCellNames.push("" + curMdCellName)
                    }
                }
            }
        }

        LOG.debug "Quadrant 1 Locations to pool = ${quadrant1LocnsToPool.toString()}"
        LOG.debug "Quadrant 1 total = ${quadrant1LocnsToPool.size()}"

        LOG.debug "Quadrant 2 Locations to pool = ${quadrant2LocnsToPool.toString()}"
        LOG.debug "Quadrant 2 total = ${quadrant2LocnsToPool.size()}"

        LOG.debug "Quadrant 3 Locations to pool = ${quadrant3LocnsToPool.toString()}"
        LOG.debug "Quadrant 3 total = ${quadrant3LocnsToPool.size()}"

        LOG.debug "Quadrant 4 Locations to pool = ${quadrant4LocnsToPool.toString()}"
        LOG.debug "Quadrant 4 total = ${quadrant4LocnsToPool.size()}"

        //TODO: would have a complex check here to make sure pool size was of a reasonable number and not zero
        //TODO: may need a compression step here if number of pool samples would be low

        // create 4 tubes including barcodes and 1 metadata each
        ArrayList<Labware> destPoolTubeLabwares = (0..3).collect { indx ->
            LOG.debug "Create labware index ${indx}"
            Labware.create(
                    LabwareTypes.GENERIC_TUBE,
                    MockFunctions.createMockExternalId(),
                    [
                            new Metadatum(key: 'tube_name', value: "my_library_pool_tube_${(indx + 1).toString()}")
                    ],
                    barcodePrefix: 'UATS', barcodeInfo: 'TST'
            )
        }

        // create materialType
        MaterialType destMaterialType = new MaterialType(name: 'library')

        // create metadata
        LOG.debug "Create quadrant 1 metadata"
        Random random = new Random()
        int iConc = random.nextInt(100 + 1) // generate a random number 0-100
        def destQuadrant1PoolMetaData = [
                new Metadatum(
                        key: 'pool_conc',
                        value: "${iConc.toString()}" // represents a pool concentration
                )
// NB. truncate the initial cell name values because of 256 char metadata value limit
//                ,
//                new Metadatum(
//                        key: 'cell_initial_names',
//                        value: "${quadrant1InitialCellNames.join(",")}" // list of parent cell initial names
//                )
        ]

        LOG.debug "Create quadrant 2 metadata"
        iConc = random.nextInt(100 + 1) // generate a random number 0-100
        def destQuadrant2PoolMetaData = [
                new Metadatum(
                        key: 'pool_conc',
                        value: "${iConc.toString()}" // represents a pool concentration
                )
// NB. truncate the initial cell name values because of 256 char metadata value limit
//                ,
//                new Metadatum(
//                        key: 'cell_initial_names',
//                        value: "${quadrant2InitialCellNames.join(",")}" // list of parent cell initial names
//                )
        ]

        LOG.debug "Create quadrant 3 metadata"
        iConc = random.nextInt(100 + 1) // generate a random number 0-100
        def destQuadrant3PoolMetaData = [
                new Metadatum(
                        key: 'pool_conc',
                        value: "${iConc.toString()}" // represents a pool concentration
                )
// NB. truncate the initial cell name values because of 256 char metadata value limit
//                ,
//                new Metadatum(
//                        key: 'cell_initial_names',
//                        value: "${quadrant3InitialCellNames.join(",")}" // list of parent cell initial names
//                )
        ]

        LOG.debug "Create quadrant 4 metadata"
        iConc = random.nextInt(100 + 1) // generate a random number 0-100
        def destQuadrant4PoolMetaData = [
                new Metadatum(
                        key: 'pool_conc',
                        value: "${iConc.toString()}" // represents a pool concentration
                )
// NB. truncate the initial cell name values because of 256 char metadata value limit
//                ,
//                new Metadatum(
//                        key: 'cell_initial_names',
//                        value: "${quadrant4InitialCellNames.join(",")}" // list of parent cell initial names
//                )
        ]

        // split quadrant 1 to tube 1
        LOG.debug "Pool to tube 1"
        destPoolTubeLabwares[0] = (Labware) TransferActions.pool(srcPlateLabware, destPoolTubeLabwares[0], destMaterialType, quadrant1LocnsToPool, destQuadrant1PoolMetaData)

        // split quadrant 2 to tube 2
        LOG.debug "Pool to tube 2"
        destPoolTubeLabwares[1] = (Labware) TransferActions.pool(srcPlateLabware, destPoolTubeLabwares[1], destMaterialType, quadrant2LocnsToPool, destQuadrant2PoolMetaData)

        // split quadrant 3 to tube 3
        LOG.debug "Pool to tube 3"
        destPoolTubeLabwares[2] = (Labware) TransferActions.pool(srcPlateLabware, destPoolTubeLabwares[2], destMaterialType, quadrant3LocnsToPool, destQuadrant3PoolMetaData)

        // split quadrant 4 to tube 4
        LOG.debug "Pool to tube 4"
        destPoolTubeLabwares[3] = (Labware) TransferActions.pool(srcPlateLabware, destPoolTubeLabwares[3], destMaterialType, quadrant4LocnsToPool, destQuadrant4PoolMetaData)

        // fetch the barcodes into a comma-separated string
        ArrayList<String> tubeBarcodeList = []

        // extract other details from tubes and materials

        StringBuilder sb = new StringBuilder();
        sb.append("Library Pool Tube details: \n")
        destPoolTubeLabwares.eachWithIndex { Labware curTube, int indx ->
            LOG.debug "building details for tube ${(indx + 1).toString()}"
            tubeBarcodeList.push(curTube.getBarcode())
            sb.with {
                append("Tube number: ${(indx + 1).toString()} \n")
                append("From quadrant: ${(indx + 1).toString()} \n")
                append("Labware UUID: ${curTube.getId().toString()} \n")
                append("Ext Id: ${curTube.getExternalId()} \n")
                append("BC: ${curTube.getBarcode()} \n")
                append("Material UUID: ${curTube.getReceptacles()[0].materialUuid} \n")
                Material curMat = Material.getMaterials([curTube.getReceptacles()[0].materialUuid])[0]
                append("Number of Parent Materials: ${curMat.getParents().size()} \n")
                append("Created at: ${curTube.getCreated_at()} \n")
//                switch (indx) {
//                    case 0:
//                        append("List initial cell names: ${quadrant1InitialCellNames.join(",")} \n")
//                        break
//                    case 1:
//                        append("List initial cell names: ${quadrant2InitialCellNames.join(",")} \n")
//                        break
//                    case 2:
//                        append("List initial cell names: ${quadrant3InitialCellNames.join(",")} \n")
//                        break
//                    case 3:
//                        append("List initial cell names: ${quadrant4InitialCellNames.join(",")} \n")
//                        break
//                    default:
//                        break
//                }
                append("\n")
            }
        }

        String tubeBarcodesString = tubeBarcodeList.join(",")
        LOG.debug("library poool tube barcodes = ${tubeBarcodesString}")
        LOG.debug("library pool tube details = ${sb.toString()}")

        // return two parameters
        [tubeBarcodesString, sb.toString()]
    }

    /**
     * Stamp 4 library pool tubes into 4 new tubes
     *
     * @param tubeBarcodesList
     * @return a list of tube barcodes in a string and some details in a second string
     */
    static ArrayList<String> stampTubes(ArrayList<String> tubeBarcodesList) {
        LOG.debug "In stampTubes"
        LOG.debug "tubeBarcodesList = ${tubeBarcodesList.toString()}"

        // fetch the 4 source tubes
        ArrayList<Labware> sourceTubeLabwares = (0..3).collect { indx ->
            LOG.debug "findByBarcode where barcode = ${tubeBarcodesList.get(indx)}"
            Labware.findByBarcode(tubeBarcodesList.get(indx))
        }
        LOG.debug "sourceTubes size = ${sourceTubeLabwares.size()}"

        // create 4 tubes including barcodes and 1 metadata each
        ArrayList<Labware> destNormTubeLabwares = (0..3).collect { indx ->
            LOG.debug "Create labware index ${indx}"
            Labware.create(
                    LabwareTypes.GENERIC_TUBE,
                    MockFunctions.createMockExternalId(),
                    [
                            new Metadatum(key: 'tube_name', value: "my_normalised_tube_${(indx + 1).toString()}")
                    ],
                    barcodePrefix: 'UATS', barcodeInfo: 'TST'
            )
        }

        // create materialType
        MaterialType destMaterialType = new MaterialType(name: 'library')

        Random random = new Random()

        // create one new metadata for each tube material
        ArrayList<Map> destStampTubeMetaDataList = (0..3).collect { indx ->
            def destStampTubeMetaData = [:]
            int iConc = random.nextInt(100 + 1) // generate a random concentration 0-100
            destStampTubeMetaData["A1"] = [
                    new Metadatum(
                            key: 'normalised_library_conc',
                            value: "${iConc.toString()}" // rndm value 0 -> 100
                    )
            ]
            destStampTubeMetaData
        }

        (0..3).each { indx ->
            // stamp the source tubes to dest tubes
            destNormTubeLabwares[indx] = TransferActions.stamp(
                    sourceTubeLabwares[indx],
                    destNormTubeLabwares[indx],
                    destMaterialType,
                    ["pool_conc"],
                    destStampTubeMetaDataList[indx]
            )
        }

        // fetch the barcodes into a comma-separated string
        ArrayList<String> tubeBarcodeList = []

        // extract other details from plate and materials
        StringBuilder sb = new StringBuilder();
        sb.append("Normalised tube details: \n")
        destNormTubeLabwares.eachWithIndex { Labware curTube, int indx ->
            tubeBarcodeList.push(curTube.getBarcode())
            sb.with {
                append("Tube number: ${(indx + 1).toString()} \n")
                append("Labware UUID: ${curTube.getId().toString()} \n")
                append("Ext Id: ${curTube.getExternalId()} \n")
                append("BC: ${curTube.getBarcode()} \n")
                append("Number of Materials: ${curTube.materialUuids().size()} \n")
                append("Created at: ${curTube.getCreated_at()} \n")

                String curMatId = curTube.receptacles[0].materialUuid
                Material curMat = Material.getMaterials([curMatId])[0]
                List<Metadatum> curMds = curMat.getMetadata()
                String curMdNormConc = ""
                String curMdPoolConc = ""
                curMds.each { Metadatum curMd ->
                    if (curMd.getKey() == "normalised_library_conc") {
                        curMdNormConc = curMd.getValue()
                    }
                    if (curMd.getKey() == "pool_conc") {
                        curMdPoolConc = curMd.getValue()
                    }
                }
                String parMatId = curMat.parents[0].getId()
                append("A1 : ${curMat.getName()} : Id = ${curMatId} : Pool Conc = ${curMdPoolConc} : Normalised Conc = ${curMdNormConc} : Parent Id = ${parMatId} \n")
                append("\n")
            }
        }

        String tubeBarcodesString = tubeBarcodeList.join(",")
        LOG.debug("tubeBarcodesString = ${tubeBarcodesString}")
        LOG.debug("tubeDetails = ${sb.toString()}")

        // return two parameters
        [tubeBarcodesString, sb.toString()]
    }

    /**
     * Follows back up the hierarchy of materials starting with those in the final normalised tubes,
     * NB. this is not flexible, it relies on the exact hierarchy of levels created by the UAT test
     * order.
     *
     * @param tubeBarcodesList
     * @return the report as a string
     */
    static String reportOnTubes(ArrayList<String> tubeBarcodesList) {
        LOG.debug "In reportOnTubes"
        LOG.debug "tubeBarcodesList = ${tubeBarcodesList.toString()}"

        // hierarchy holds the relationships between materials, listing holds each unique material and its details
        Map materialsListing = [:]

        // fetch labwares for the 4 normalised tubes
        ArrayList<Labware> normalisedTubeLabwares = (0..3).collect { indx ->
            LOG.debug "findByBarcode where barcode = ${tubeBarcodesList.get(indx)}"
            Labware.findByBarcode(tubeBarcodesList.get(indx))
        }
        LOG.debug "normalisedTubeLabwares size = ${normalisedTubeLabwares.size()}"

        def results = []

        StringBuilder sb = new StringBuilder();

        // fetch the materials for these 4 tube labwares and build report
        (0..3).each { tubeIndx ->
            sb.append("========================= \n")
            sb.append("Report for tube ${tubeIndx + 1} \n")
            sb.append("========================= \n")
            // one material per tube, fetch the material id then the material
            String normalisedMaterialUUID = normalisedTubeLabwares.get(tubeIndx).materialUuids().get(0)
            Material normalisedMaterial = Material.getMaterials([normalisedMaterialUUID]).get(0)

            materialsListing[normalisedMaterialUUID] = normalisedMaterial

            // recursively fetch all the materials ancestor parent materials
            results[tubeIndx] = getParentMaterials(materialsListing, normalisedMaterial, 0)

            // in this case we know what each level represents (NB does not get cherry pick branch)
            (0..6).each { int curLvl ->
                switch (curLvl) {
                    case 0:
                        sb.append("Normalised Library material \n")
                        break
                    case 1:
                        sb.append("Library Pool material \n")
                        break
                    case 2:
                        sb.append("Selective Stamped materials: \n")
                        break
                    case 3:
                        sb.append("Stamped materials: \n")
                        break
                    case 4:
                        sb.append("Combined materials: \n")
                        break
                    case 5:
                        sb.append("Split materials: \n")
                        break
                    case 6:
                        sb.append("Source materials: \n")
                        break
                    default:
                        LOG.error "unexpected level ${curLvl}"
                        break
                }
                sb.append("--------------------------- \n")

                int counter = 0

                // modify the list to remove duplicates e.g. from where multiple samples were split from the same parent
                List curResults = results[tubeIndx]
                LOG.debug "curResults size = ${curResults.size()}"
                List uniqueCurResults = curResults.unique(false)
                LOG.debug "uniqueCurResults size = ${uniqueCurResults.size()}"
                uniqueCurResults.each { List curResult ->
                    //TODO: can we sort the list for the current level. e.g. by location name?
                    //TODO: we need a find on receptacle by material uuid to connect the material to its location and labware
                    if (curResult[2] == curLvl) {
                        String parUuid = curResult[0]
                        String childUuid = curResult[1]

                        // fetch child and parent materials from list using ids as keys
                        Material childMat = (Material) materialsListing[childUuid]

                        if (parUuid != "0") {
                            Material parMat = (Material) materialsListing[parUuid]
                            sb.append("${counter + 1} - ${childMat.getName()} --FROM-- ${parMat.getName()} \n")
                        } else {
                            sb.append("${counter + 1} - ${childMat.getName()} \n")
                        }
                        counter++
                    }
                }
                sb.append("\n")
            }
        }

        // return report
        LOG.debug sb.toString()
        return sb.toString()
    }

    /**
     * Recursive method to fetch parent materials
     *
     * @param materialsListing - so we don't have to fetch the same material from the DB twice
     * @param curMaterial
     * @param level - 0 is for starting point, +1 for each parent level above that
     * @return
     */
    static def getParentMaterials(Map materialsListing, Material curMaterial, level = 0) {

        def results = []

        // result lines contain: [parent material id, current material id, level]

        if (curMaterial.getParents() == null || curMaterial.getParents().size() == 0) {
            // no further parents, have reach top of hierarchy
            results.add(["0", curMaterial.getId(), level])
        } else {
            curMaterial.getParents().each { parentMaterial ->
                // if this material does not already exist in list get it with uuid and add it
                if (materialsListing.containsKey(parentMaterial.getId())) {
                    parentMaterial = (Material) materialsListing[parentMaterial.getId()]
                } else {
                    parentMaterial = Material.getMaterials([parentMaterial.getId()]).get(0)
                    materialsListing[parentMaterial.getId()] = parentMaterial
                }
                // add a line to the results
                results.add([parentMaterial.getId(), curMaterial.getId(), level])

                // make recursive call
                results.addAll(getParentMaterials(materialsListing, parentMaterial, level + 1))
            }
        }
        results
    }
}
