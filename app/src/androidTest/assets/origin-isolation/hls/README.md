# Origin-isolation HLS fixture (master).

The relay must keep the original URL resolution semantics for every nested
resource below: variant playlist, key, init/map segment and media segments.
`config-b.json` points site `origin_site` at this file through the test HTTP
fixture server, so the absolute URLs contain the loopback base actually in use.
A matching `media.m3u8` sits next to this file and uses *relative* references,
which is the case a global string replacement would corrupt.
