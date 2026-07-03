package com.itgeo.fitmate.api.rag.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.ai.document.Document;

@Data
public class RagSearchResult {

    private String question;
    private Integer finalTopK;

    private List<RagRetrievedChunk> vectorHits = new ArrayList<>();
    private List<RagRetrievedChunk> keywordHits = new ArrayList<>();
    private List<RagRetrievedChunk> fusedHits = new ArrayList<>();

    private Boolean rerankEnabled;
    private Integer fusionCandidateK;
    private List<RagRetrievedChunk> finalHits = new ArrayList<>();

    private List<Document> finalDocuments = new ArrayList<>();
}
