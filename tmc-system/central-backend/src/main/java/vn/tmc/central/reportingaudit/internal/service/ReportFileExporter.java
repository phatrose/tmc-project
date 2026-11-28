package vn.tmc.central.reportingaudit.internal.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import vn.tmc.central.reportingaudit.api.AuditEntry;
import vn.tmc.central.reportingaudit.api.ReportExport.Format;
import vn.tmc.central.reportingaudit.internal.model.ReportFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ReportFileExporter {
    private final String pdfFontPath;
    public ReportFileExporter(String pdfFontPath) { this.pdfFontPath = pdfFontPath; }

    public ReportFile audit(List<AuditEntry> entries, Format format) {
        var table = new ArrayList<List<?>>();
        table.add(List.of("id", "actorId", "deviceId", "factoryId", "action", "resourceType", "resourceId", "result", "occurredAt", "correlationId", "reason"));
        entries.forEach(e -> table.add(Arrays.asList(e.id(), e.actorId(), e.deviceId(), e.factoryId(),
                e.action(), e.resourceType(), e.resourceId(), e.result(),
                new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(e.occurredAt()),
                e.correlationId(), e.reason())));
        return encode(table, format, "Audit log");
    }
    public ReportFile encode(List<List<?>> table, Format format, String title) {
        Objects.requireNonNull(table);
        Objects.requireNonNull(format);
        Objects.requireNonNull(title);
        try {
            return switch (format) {
                case CSV -> new ReportFile(csv(table), "text/csv;charset=UTF-8", "csv");
                case XLSX -> new ReportFile(xlsx(table), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
                case PDF -> new ReportFile(pdf(table, title), "application/pdf", "pdf");
            };
        } catch (IOException e) { throw new ReportingException("INTERNAL_ERROR", "Không tạo được tệp báo cáo", 500); }
    }
    private static byte[] csv(List<List<?>> table) {
        var text = new StringBuilder("\uFEFF");
        for (var row : table) {
            var cells = new ArrayList<String>();
            for (Object value : row) {
                String s = Objects.toString(value, "");
                String trimmed = s.stripLeading();
                if (!(value instanceof Number) && !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) s = "'" + s;
                cells.add("\"" + s.replace("\"", "\"\"") + "\"");
            }
            text.append(String.join(",", cells)).append("\r\n");
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] xlsx(List<List<?>> table) throws IOException {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Report");
            for (int r = 0; r < table.size(); r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < table.get(r).size(); c++) {
                    Object value = table.get(r).get(c);
                    var cell = row.createCell(c);
                    if (value instanceof Number n && Math.abs(n.doubleValue()) <= 9_007_199_254_740_991D) cell.setCellValue(n.doubleValue());
                    else cell.setCellValue(Objects.toString(value, ""));
                }
            }
            sheet.createFreezePane(0, 1);
            if (!table.isEmpty()) for (int c = 0; c < table.getFirst().size(); c++) sheet.setColumnWidth(c, c == 0 ? 30 * 256 : 24 * 256);
            workbook.write(bytes); return bytes.toByteArray();
        }
    }
    private byte[] pdf(List<List<?>> table, String title) throws IOException {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            PDFont font;
            if (pdfFontPath != null && !pdfFontPath.isBlank()) font = PDType0Font.load(document, new java.io.File(pdfFontPath));
            else try (var bundledFont = PDType0Font.class.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
                if (bundledFont == null) throw new IOException("Bundled PDF font unavailable");
                font = PDType0Font.load(document, bundledFont, true);
            }
            var lines = new ArrayList<String>();
            lines.add(title); lines.add("");
            for (var row : table) {
                String text = String.join(" | ", row.stream().map(o -> Objects.toString(o, "")).toList())
                        .replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
                var line = new StringBuilder();
                float width = 0;
                for (int cp : text.codePoints().toArray()) {
                    if (cp == '\n') {
                        lines.add(line.toString()); line.setLength(0); width = 0; continue;
                    }
                    float charWidth = font.getStringWidth(new String(Character.toChars(cp))) / 1000 * 8;
                    if (width + charWidth > 770 && !line.isEmpty()) {
                        lines.add(line.toString()); line.setLength(0); width = 0;
                    }
                    line.appendCodePoint(cp); width += charWidth;
                }
                lines.add(line.toString());
            }
            for (int start = 0; start < lines.size(); start += 42) {
                var page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText(); stream.setFont(font, 8); stream.setLeading(12); stream.newLineAtOffset(30, 560);
                    for (int i = start; i < Math.min(start + 42, lines.size()); i++) { stream.showText(lines.get(i)); stream.newLine(); }
                    stream.endText();
                }
            }
            document.save(bytes); return bytes.toByteArray();
        } catch (IllegalArgumentException e) {
            throw new ReportingException("DEPENDENCY_UNAVAILABLE", "Cần font TTF hỗ trợ đầy đủ ký tự trong báo cáo", 503);
        }
    }
}
