package com.itgeo.fitmate.api.rag.infrastructure.parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class CsvDocumentParser implements DocumentParser {
    @Override
    public boolean supports(String fileExtension) {
        return "csv".equalsIgnoreCase(fileExtension);
    }

    @Override
    public String parse(String filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.replace(",", " ")).append("\n");
            }
        }
        return sb.toString();
    }
}
