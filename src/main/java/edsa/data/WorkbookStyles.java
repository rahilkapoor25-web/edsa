package edsa.data;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * The handful of cell styles the exported workbook uses, created once and shared.
 *
 * <p>A spreadsheet holds a limited number of distinct styles, and creating one per cell runs
 * into that limit on a real session of several thousand seats. Every style is therefore made
 * here, once, and handed out.
 *
 * <p>The palette is deliberately plain: one dark heading, one grey for what is unavailable,
 * and borders to separate things. It has to photocopy.
 */
final class WorkbookStyles {

    final CellStyle title;
    final CellStyle subtitle;
    final CellStyle sectionTitle;
    final CellStyle sectionDetail;
    final CellStyle columnHeader;
    final CellStyle rowHeader;
    final CellStyle seat;
    final CellStyle seatId;
    final CellStyle emptySeat;
    final CellStyle unavailableSeat;
    final CellStyle legend;
    final CellStyle data;
    final CellStyle dataNumeric;

    WorkbookStyles(Workbook book) {
        this.title = style(book, bold(book, 16, IndexedColors.BLACK));
        this.subtitle = style(book, plain(book, 10, IndexedColors.GREY_50_PERCENT));

        this.sectionTitle = style(book, bold(book, 12, IndexedColors.BLACK));
        this.sectionDetail = style(book, plain(book, 10, IndexedColors.BLACK));

        this.columnHeader = bordered(book, bold(book, 10, IndexedColors.WHITE));
        this.columnHeader.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        this.columnHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        this.columnHeader.setAlignment(HorizontalAlignment.CENTER);
        this.columnHeader.setVerticalAlignment(VerticalAlignment.CENTER);
        this.columnHeader.setWrapText(true);

        this.rowHeader = bordered(book, bold(book, 10, IndexedColors.BLACK));
        this.rowHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        this.rowHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        this.rowHeader.setAlignment(HorizontalAlignment.CENTER);
        this.rowHeader.setVerticalAlignment(VerticalAlignment.CENTER);

        this.seat = bordered(book, plain(book, 9, IndexedColors.BLACK));
        this.seat.setWrapText(true);
        this.seat.setAlignment(HorizontalAlignment.LEFT);
        this.seat.setVerticalAlignment(VerticalAlignment.TOP);

        this.seatId = bordered(book, bold(book, 9, IndexedColors.BLACK));
        this.seatId.setWrapText(true);
        this.seatId.setAlignment(HorizontalAlignment.LEFT);
        this.seatId.setVerticalAlignment(VerticalAlignment.TOP);

        this.emptySeat = bordered(book, plain(book, 9, IndexedColors.GREY_50_PERCENT));
        this.emptySeat.setAlignment(HorizontalAlignment.CENTER);
        this.emptySeat.setVerticalAlignment(VerticalAlignment.CENTER);

        this.unavailableSeat = bordered(book, plain(book, 9, IndexedColors.GREY_50_PERCENT));
        this.unavailableSeat.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        this.unavailableSeat.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        this.unavailableSeat.setAlignment(HorizontalAlignment.CENTER);
        this.unavailableSeat.setVerticalAlignment(VerticalAlignment.CENTER);

        this.legend = style(book, plain(book, 9, IndexedColors.GREY_50_PERCENT));

        this.data = bordered(book, plain(book, 10, IndexedColors.BLACK));
        this.data.setVerticalAlignment(VerticalAlignment.TOP);
        this.data.setWrapText(true);

        this.dataNumeric = bordered(book, plain(book, 10, IndexedColors.BLACK));
        this.dataNumeric.setAlignment(HorizontalAlignment.RIGHT);
        this.dataNumeric.setVerticalAlignment(VerticalAlignment.TOP);
    }

    private static Font bold(Workbook book, int points, IndexedColors colour) {
        Font font = book.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) points);
        font.setColor(colour.getIndex());
        return font;
    }

    private static Font plain(Workbook book, int points, IndexedColors colour) {
        Font font = book.createFont();
        font.setFontHeightInPoints((short) points);
        font.setColor(colour.getIndex());
        return font;
    }

    private static CellStyle style(Workbook book, Font font) {
        CellStyle style = book.createCellStyle();
        style.setFont(font);
        return style;
    }

    private static CellStyle bordered(Workbook book, Font font) {
        CellStyle style = style(book, font);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        return style;
    }
}
