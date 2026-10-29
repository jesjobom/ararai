# Design: search-only web results for widgets

Register `web_search_top_results@1` for widgets while retaining `web_search@1` for Chat. The new contract takes a bounded query and requested result count (1–10); the application supplies provider order, token, network endpoint, and runtime limits. Exa is primary, Tavily is controlled fallback. Only configured and verified providers are candidates; the Chat provider-enable switches do not control widget availability.

Use provider search endpoints only. Normalize ranked title and HTTPS canonical URL metadata into a bounded result array; no Tavily Extract, Exa Highlights, raw content, provider answer, or direct site fetch occurs. A result count below the requested maximum is valid. The first item represents provider rank, not independent truth. Keep credentials and raw provider responses out of model context and saved widget data.

The widget's global web-search switch defaults off and requires at least one configured provider. Existing confirmed widget revisions do not receive new permission. Links must exactly match tool output and pass a second application-owned destination policy on opening. For web results, admit only safe public HTTPS URLs; keep the narrower Wikipedia article policy for Wikipedia tools.

A content-reading tool is a separate contract and permission; it is out of scope for this search-only change.
