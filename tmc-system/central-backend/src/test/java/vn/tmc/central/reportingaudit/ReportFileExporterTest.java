package vn.tmc.central.reportingaudit;
import org.junit.jupiter.api.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import vn.tmc.central.reportingaudit.internal.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import vn.tmc.central.reportingaudit.api.AuditEntry;
import static vn.tmc.central.reportingaudit.api.ReportExport.Format.*;
import static org.assertj.core.api.Assertions.*;

class ReportFileExporterTest {
    ReportFileExporter exporter=new ReportFileExporter("");
    @Test void csvEscapesQuotesUnicodeAndFormula() {
        var file=exporter.encode(List.of(List.of("code","minutes"),List.of("=SUM(A1)",480),List.of("  +cmd",0),List.of("NV,\"Hải\"",1)),CSV,"test");
        String text=new String(file.bytes(),StandardCharsets.UTF_8);
        assertThat(text).startsWith("\uFEFF").contains("\"'=SUM(A1)\"").contains("\"'  +cmd\"").contains("\"NV,\"\"Hải\"\"\"");
    }
    @Test void realXlsxPreservesStringCellsWithoutInterpretingFormula() throws Exception {
        var file=exporter.encode(List.of(List.of("employeeId","minutes"),List.of("=1+2",480)),XLSX,"test");
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(file.bytes()))) {
            var row=wb.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("=1+2");
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(480);
        }
    }
    @Test void realPdfHasExtractableTextAndMultiplePages() throws Exception {
        var rows=new ArrayList<List<?>>(); rows.add(List.of("employeeId","minutes"));
        for(int i=0;i<100;i++) rows.add(List.of("NV"+i,480));
        var file=exporter.encode(rows,PDF,"Attendance 2026-10");
        try(var pdf=Loader.loadPDF(file.bytes())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PDFTextStripper().getText(pdf)).contains("Attendance 2026-10","NV99");
        }
    }
    @Test void unicodePdfEmbedsBundledFontAndKeepsVietnameseCharacters() throws Exception {
        var file=exporter.encode(List.of(List.of("Hải")),PDF,"test");
        try(var pdf=Loader.loadPDF(file.bytes())) { assertThat(new PDFTextStripper().getText(pdf)).contains("Hải"); }
    }
    @Test void unsupportedGlyphIsReportedRatherThanSilentlyRemoved() {
        assertThatThrownBy(()->exporter.encode(List.of(List.of("\uD83E\uDD73")),PDF,"test")).isInstanceOf(ReportingException.class).extracting("code").isEqualTo("DEPENDENCY_UNAVAILABLE");
    }
    @Test void auditUsesV1ColumnsAndSupportsNullFields() {
        var entry=new AuditEntry(ReportingFixtures.id(1),null,ReportingFixtures.id(2),null,"GATE_EMERGENCY",
                "COMMAND","command-1",AuditEntry.Result.SUCCESS,ReportingFixtures.AT,ReportingFixtures.id(3),null);
        var file=exporter.audit(List.of(entry),CSV);
        String text=new String(file.bytes(),StandardCharsets.UTF_8);
        assertThat(text).contains("\"id\",\"actorId\",\"deviceId\",\"factoryId\",\"action\",\"resourceType\",\"resourceId\",\"result\",\"occurredAt\",\"correlationId\",\"reason\"")
                .contains("GATE_EMERGENCY","command-1","2026-11-17T02:00:00.000Z").doesNotContain("departmentId","outcome");
        assertThat(file.extension()).isEqualTo("csv");
        assertThat(file.mediaType()).isEqualTo("text/csv;charset=UTF-8");
    }
    @Test void pdfSupportsMultilineText() throws Exception {
        var file=exporter.encode(List.of(List.of("Lý do:\nKiểm tra\tca đêm")),PDF,"Audit");
        try(var pdf=Loader.loadPDF(file.bytes())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Lý do:","Kiểm tra","ca đêm");
        }
    }
}
