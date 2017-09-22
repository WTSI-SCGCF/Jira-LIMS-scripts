package uk.ac.sanger.scgcf.jira.lims.utils

import groovy.util.logging.Slf4j
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.*
import java.io.*

@Slf4j(value = "LOG")
class ExcelParser {
    // see http://poi.apache.org/spreadsheet/quick-guide.html#Iterator

    /**
     * Parse the file and return a map of sheets
     *
     * @param path
     * @return
     */
    def static parse(String path) {

        try {
            FileInputStream excelFile = new FileInputStream(path)
            Workbook workbook = new XSSFWorkbook(excelFile)

            Map<String, Map<String, Object>> sheetsMap = [:]

            int iNumSheets = workbook.getNumberOfSheets()
            (0..(iNumSheets-1)).each { int iSheetIndex ->
                Sheet sheet = workbook.getSheetAt(iSheetIndex)
                Iterator<Row> rowIt = sheet.rowIterator()
                def rows = []
                while(rowIt.hasNext()) {
                    Row row = rowIt.next()
                    rows << getRowData(row)
                }
                sheetsMap[Integer.toString(iSheetIndex)] = ['sheet_name':sheet.getSheetName(), 'rows':rows]
            }
            sheetsMap

        } catch (FileNotFoundException e) {
            LOG.error "ExcelParser failed with file not found exception for path <${path}>"
            LOG.error e.getStackTrace().toArrayString()
        } catch (IOException e) {
            LOG.error "ExcelParser failed with io exception for path <${path}>"
            LOG.error e.getStackTrace().toArrayString()
        }
    }

    /**
     * Get the values for the cells in a row
     *
     * @param row
     * @return
     */
    private static List getRowData(Row row) {
        List rowData = []
        for (Cell cell : row) {
            getValue(cell, rowData)
        }
        rowData
    }

    /**
     * Get the value for a cell in the row
     *
     * @param cell
     * @param rowData
     * @return
     */
    private static def getValue(Cell cell, List rowData) {

        int colIndex = cell.getColumnIndex()
        def value
        switch (cell.getCellType()) {
            case Cell.CELL_TYPE_STRING:
                value = cell.getRichStringCellValue().getString()
                break
            case Cell.CELL_TYPE_NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    value = cell.getDateCellValue()
                } else {
                    value = cell.getNumericCellValue()
                }
                break
            case Cell.CELL_TYPE_BOOLEAN:
                value = cell.getBooleanCellValue()
                break
            case Cell.CELL_TYPE_FORMULA:
                value = cell.getCellFormula()
                break;
            case Cell.CELL_TYPE_BLANK:
                value = ""
                break
            default:
                value = ""
        }

        rowData[colIndex] = value
        rowData
    }
}
