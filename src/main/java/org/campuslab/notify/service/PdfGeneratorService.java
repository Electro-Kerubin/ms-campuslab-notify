package org.campuslab.notify.service;

import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.campuslab.notify.dto.VoucherPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PdfGeneratorService {
    private final Path outputDirectory;

    public PdfGeneratorService(@Value("${notify.voucher-output-dir:/tmp/notify-vouchers}") String outputDirectory) {
        this.outputDirectory = Path.of(outputDirectory);
    }

    public byte[] generateVoucher(VoucherPayload payload) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("CampusLab - " + payload.getActionType()));
            document.add(new Paragraph("Reserva: " + payload.getBookingId()));
            document.add(new Paragraph("Recurso: " + payload.getResourceName()));
            document.add(new Paragraph("Fecha: " + payload.getDate()));
            document.close();
            Files.createDirectories(outputDirectory);
            Files.write(outputDirectory.resolve("voucher-" + payload.getBookingId() + ".pdf"), output.toByteArray());
            log.info("PDF de {} generado para reserva {}", payload.getActionType(), payload.getBookingId());
            return output.toByteArray();
        } catch (IOException | com.itextpdf.text.DocumentException exception) {
            throw new IllegalStateException("No se pudo generar el PDF del voucher", exception);
        }
    }
}