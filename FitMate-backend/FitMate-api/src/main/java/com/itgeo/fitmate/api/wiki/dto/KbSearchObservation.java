package com.itgeo.fitmate.api.wiki.dto;

import java.util.List;
import lombok.Data;

@Data
public class KbSearchObservation {
    private List<WikiPageItem> wiki;
    private List<RawChunkItem> rag;
    private String rewrittenQuery;

    @Data
    public static class WikiPageItem {
        private Long pageId;
        private String title;
        private String pageType;
        private String content;
    }

    @Data
    public static class RawChunkItem {
        private String text;
        private String fileName;
    }
}
