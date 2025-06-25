// File: src/main/java/com/example/latexpdf/PdfController.java

package com.example.latexpdf;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

@RestController
public class PdfController {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertLatexToPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".tex")) {
            return ResponseEntity.badRequest().body("Invalid .tex file");
        }

        try {
            File workDir = Files.createTempDirectory("texbuild").toFile();
            File texFile = new File(workDir, file.getOriginalFilename());
            file.transferTo(texFile);

            ProcessBuilder pb = new ProcessBuilder(
                    "pdflatex",
                    "-interaction=nonstopmode",
                    "-halt-on-error",
                    "-output-directory", workDir.getAbsolutePath(),
                    texFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return ResponseEntity.status(408).body("LaTeX compile timed out");
            }

            File pdfFile = new File(workDir, texFile.getName().replace(".tex", ".pdf"));
            if (!pdfFile.exists()) {
                return ResponseEntity.status(500).body("PDF generation failed");
            }

            InputStreamResource resource = new InputStreamResource(new FileInputStream(pdfFile));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"output.pdf\"")
                    .contentLength(pdfFile.length())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Server error: " + e.getMessage());
        }
    }
}
