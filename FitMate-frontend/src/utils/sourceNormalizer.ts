function unwrapSourceCollection(rawSources) {
  if (!rawSources) {
    return [];
  }

  if (Array.isArray(rawSources)) {
    return rawSources;
  }

  if (typeof rawSources !== 'object') {
    return [];
  }

  var candidates =
    rawSources.items ||
    rawSources.list ||
    rawSources.sources ||
    rawSources.references ||
    rawSources.citations ||
    rawSources.docs ||
    rawSources.sourceList ||
    rawSources.sourceDocs ||
    [];

  return Array.isArray(candidates) ? candidates : [];
}

function normalizeSource(source, index) {
  if (source == null) {
    return null;
  }

  if (typeof source === 'string') {
    return {
      id: 'source-' + index,
      title: '来源 ' + (index + 1),
      snippet: source,
      extra: '',
      url: '',
    };
  }

  if (typeof source !== 'object') {
    return null;
  }

  var metadata = source.metadata || {};
  var url =
    source.url ||
    source.link ||
    source.href ||
    metadata.url ||
    metadata.link ||
    metadata.href ||
    '';
  var title =
    source.title ||
    source.name ||
    source.sourceTitle ||
    source.sourceName ||
    metadata.title ||
    metadata.name ||
    (url || '来源 ' + (index + 1));
  var snippet =
    source.snippet ||
    source.summary ||
    source.content ||
    source.text ||
    source.chunk ||
    source.pageContent ||
    metadata.snippet ||
    metadata.summary ||
    '';
  var page =
    source.page ||
    source.pageNumber ||
    metadata.page ||
    metadata.pageNumber ||
    null;
  var extra =
    source.extra ||
    source.description ||
    metadata.description ||
    (page != null ? '页码：' + page : '');

  return {
    id: source.id || source.docId || source.chunkId || 'source-' + index,
    title: title,
    snippet: snippet,
    extra: extra,
    url: url,
  };
}

function normalizeSources(rawSources) {
  return unwrapSourceCollection(rawSources)
    .map(function (source, index) {
      return normalizeSource(source, index);
    })
    .filter(function (source) {
      return !!source;
    });
}

function extractSourcesFromResponse(chatResponse) {
  if (!chatResponse) {
    return [];
  }

  return normalizeSources(
    chatResponse.sources ||
      chatResponse.references ||
      chatResponse.citations ||
      chatResponse.sourceList ||
      chatResponse.sourceDocs ||
      chatResponse.docs ||
      (chatResponse.data &&
        (chatResponse.data.sources ||
          chatResponse.data.references ||
          chatResponse.data.citations ||
          chatResponse.data.sourceList ||
          chatResponse.data.sourceDocs ||
          chatResponse.data.docs))
  );
}

export {
  unwrapSourceCollection,
  normalizeSource,
  normalizeSources,
  extractSourcesFromResponse,
};

export default {
  unwrapSourceCollection,
  normalizeSource,
  normalizeSources,
  extractSourcesFromResponse,
};
