# SetToRise

Hackathon MVP. A news reader that:

- sets a reader's **lens** with a 2-minute values quiz that never leaves the device,
- shows only outlets on **your lens's side** (a "both" lens sees every outlet),
- has a **See Another Perspective** page per story: the same story from outlets on the other side, and an AI
  summary of how the coverage differs,
- shows **how each story may affect you**, framed for that lens, with the strongest opposing case,
- splits the feed into **American Politics**, **Social Justice** (topics you pick) and **Global Issues**,
- links election stories to **vote.gov** for official ballot info.

The working name lives in one constant: `AppConfig.APP_NAME` in
`src/main/java/com/settorise/config/AppConfig.java`. The UI reads it from `GET /api/meta`.

## Run it

Requires Java 17+ and Maven.

```bash
# Put keys in .env (gitignored):  ANTHROPIC_API_KEY=...  NYT_API_KEY=...
set -a; source .env; set +a
mvn spring-boot:run            # or: mvn package && java -jar target/settorise-0.1.0.jar
```

Open http://localhost:8080. Your first visit opens the quiz. You can retake it later from the header chip or the footer.

Tests: `mvn test`.

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `ANTHROPIC_API_KEY` | (none) | Claude API key. Without it, the story page says AI notes are off. |
| `NYT_API_KEY` | (none) | NYT Top Stories key (free at developer.nytimes.com). Without it, NYT is skipped. |
| `CLAUDE_MODEL` | `claude-opus-5-5` | Model used for framings. |
| `CLAUDE_FALLBACKS` | `default` | Server-side refusal fallback. Set `off` to disable it. |
| `ANTHROPIC_BASE_URL` | (none) | Optional proxy or local test server for the Claude API. |
| `RSS_CACHE_MINUTES` | `10` | RSS cache lifetime. |
| `NYT_CACHE_MINUTES` | `15` | NYT cache lifetime, clamped to at least 10 in code. |
| `MAX_STORY_AGE_DAYS` | `7` | Items older than this are hidden. |
| `OUTLET_LEANS_FILE` | (bundled placeholder) | Path to a replacement outlet-lean JSON file. |
| `PORT` | `8080` | HTTP port. |

Keys are read only from the environment. `.gitignore` excludes `.env*` files. Never commit keys.

## API

```
GET /api/meta
GET /api/feed?tab=politics|issues|global&show=left|right|both
GET /api/story/{id}?show=left|right|both
GET /api/perspective/{id}
```

- **Feed**: the stories for a tab, with outlet, outlet-lean label, feed summary, link, date, the feed's own image URL, topic tags and an `electionRelated` flag. `show=left` or `show=right` returns only outlets rated on that side; `show=both` returns every outlet, alternating between leans. (Tab ids stay `politics`, `issues` and `global`; the UI calls them American Politics, Social Justice and Global Issues.)
- **Perspective**: the same story from outlets on the other side of the story's own outlet (up to 3, one per outlet, matched by word overlap) and an AI comparison of the story with the best match (`sameEvent`, `bothReport`, `differences`). If nothing matches by words, Claude picks the most relevant other-side article from up to 40 candidates (same tab, ranked by word overlap), and the response says how close it is (`match`: `same_event`, `same_issue` or `loosely_related`, plus `matchReason`). It then includes the story's AI framings, so the page can also show the strongest case from the other side. If the AI is off, the top-ranked candidate is shown. It takes no `show` parameter.
- **Story page**: "Same story, other outlets" lists only outlets on the reader's own side (`show`). The other side appears only after "See Another Perspective".
- **Story**: the story, the AI result (**all** framings, left and right), up to 4 matching stories from other outlets, and citations. `show` only orders the "other outlets" list. The AI result is the same for every reader.

## Privacy design

- The quiz is scored in the browser (`static/js/compass.js`). Answers, axis scores and the lens are kept in `localStorage` under `str.compass.v2`.
- The server receives exactly one reader-derived value: `show=left|right|both`. No API accepts answers, scores or axis values.
- Story images are loaded by the browser directly from each outlet's image server (with `referrerpolicy="no-referrer"`). That means the outlets' image servers see the reader's IP address, as with any embedded image. The app never downloads or stores the images.
- The server returns **every** framing for a story. The browser picks which to display, and the "View as" switch on a story page changes it locally. So the AI cache is per article, not per reader, and nothing about a reader's lean is stored on the server.
- Issue topic picks also stay in `localStorage` (`str.topics.v2`). The browser filters the Issues tab itself, so the server never learns which topics a reader follows.
- "Delete my result" on the quiz result screen clears the stored lens.
- The server sets no cookies and has no accounts. Spring's default access logs are off, but a reverse proxy in front of the app would log `show=` from the query string.

## Compass

There are 12 original statements (6 economic, 6 social) on a 4-point agree/disagree scale with no neutral middle. Each axis is scored from −1 (left) to +1 (right). The lens comes from the average of the two axes:

- at or below `-leanThreshold` → `left`
- at or above `+leanThreshold` → `right`
- otherwise → `both` (center readers see both framings)

If the two axes differ by at least `splitThreshold`, the lens is `both`. Both thresholds are in `static/js/config.js`.

## News sources and terms

Only headline, outlet, the feed's own summary (trimmed to 300 chars), link, date and the feed's own image URL (Media RSS `media:content`/`media:thumbnail`, an image enclosure, or the largest NYT `multimedia` image) are kept. Images are hotlinked, not copied. MS NOW's feed repeats its host in image URLs (`www.ms.now/www.ms.now/...`, which 404s), so the app removes the repeated host. When a feed item has no image at all (some MS NOW items), the app requests that article's page **once** (cached per link, only for stories recent enough to show) and reads only its link-preview image (`og:image`, else `twitter:image`) from the page head. Nothing else from the page is kept or sent to the AI. This is the one exception to "no article pages are fetched", chosen on 2026-11-07 so every card has a photo. Note that MS NOW's robots.txt opts out of AI crawlers (including `anthropic-ai`); this request uses the app's own User-Agent and does not feed the AI, but review it before any public release. Check each source's terms for image use before any public release. Headlines are shown as published. The only processing is decoding HTML entities and collapsing whitespace. Every card credits the outlet and links to the original. No article text is fetched or scraped (the only article-page request is the preview-image fallback above), and the full-body `content:encoded` field that some RSS feeds include is ignored. Stories are held in memory only.

This is a **non-commercial** demo. Check each source's current terms before any public release:

- **CNN (RSS)**: CNN offers RSS for personal, non-commercial use with attribution and a link back. **All three CNN feeds still respond, but none has new items** (newest are from 2023–2024), so the age filter hides them. The startup log reports them as `SOURCE STALE`.
- **Fox News (RSS)**: feeds at `moxie.foxnews.com/google-publisher/*.xml`. Use is subject to Fox News' terms of use. Attribution and link back are required, and commercial reuse needs permission.
- **MS NOW (RSS)**: `https://www.ms.now/feed`, subject to MS NOW's terms of use. The feed rejects requests without a User-Agent, so the app sends an honest one naming the app (`AppConfig.USER_AGENT`).
- **The New York Times (Top Stories API)**: governed by the NYT Developer APIs Terms of Use: attribution ("Data provided by The New York Times", shown in the footer when enabled), link back, non-commercial, and rate limits of about 500 requests/day and 5/minute. The app calls 3 sections (`politics`, `world`, `us`) at most once per 15 minutes: up to 288 requests/day.
- **AP**: not used. The paid AP API is out of scope.
- **Added 2026-11-07 (RSS, no keys):** right-leaning New York Post (politics, world), Washington Examiner (news), The Washington Times (politics, world) and The Daily Caller; left-leaning The Guardian (US, world), NBC News (politics, world) and ABC News (politics, world). Each was checked to be current with an image on every item, so none needs the preview-image page fetch. Their terms of use have **not** been reviewed: all are personal, non-commercial use with attribution and a link back at most, and several sites' robots.txt files opt out of AI crawlers. Review each before any public release.
- **Considered and skipped:** NPR, The Washington Post, HuffPost and CBS News (feeds have no images, and NPR, the Post and HuffPost block Anthropic's crawlers in robots.txt, so fetching their pages for images was ruled out); Politico and the Los Angeles Times (images on only some items); Salon (feed stale); Breitbart and The Federalist (no images in feed).

At startup, every source is fetched once and logged as `SOURCE OK`, `SOURCE STALE` or `SOURCE FAILED`. A failing source keeps its last good items, and the app backs off until its next cache window.

## AI framings

`ai/SummaryService.java` calls the Claude API through the official Java SDK, using structured output into Java records (`ai/Framings.java`). The input is only the outlet, headline, feed summary and link. It returns:

- a neutral summary,
- for a left-leaning and a right-leaning reader: "how this may affect you" and the strongest opposing case.

The prompt requires neutral tone, steelmanning, no verdicts, no guessing the reader's politics, and no facts beyond the headline and summary. Every result is labeled **AI-generated** with the outlet and source link as citations. Results are cached per article ID (in memory, up to 2,000). Concurrent requests for the same story share one API call. Failures are not cached, so they retry on the next view. Server-side refusal fallback is on by default (`CLAUDE_FALLBACKS`).

## Outlet lean labels

`src/main/resources/outlet-leans.json` maps outlet → lean. **The values are placeholders**, marked as such in the file and logged as a warning at startup. Replace them with licensed ratings before any public release, or point `OUTLET_LEANS_FILE` at your own file. They are used only to alternate outlets in the feed and in "Same story, other outlets". The UI marks them as placeholders, not facts.

## Known limits

- **Source balance**: CNN's RSS feeds are stale. On 2026-11-07 the Politics tab had 116 right-leaning stories (Fox 37, New York Post 27, Washington Times 26, Daily Caller 16, Washington Examiner 10) and 153 left-leaning ones (Guardian 44, NYT 39, ABC 32, NBC 28, MS NOW 10). There is no live center source; a licensed AP feed would add one.
- **Keyword tagging**: tabs, topics and the election flag come from keyword lists (`news/Classifier.java`). Expect some misses and mis-tags. A world story needs a US signal before it gets a vote.gov link.
- **Matching coverage**: "Same story, other outlets" and "See Another Perspective" use word overlap, so they can miss or mismatch stories. The AI comparison flags a best match that is not the same event. With only Fox vs NYT and MS NOW, 2 of 25 Fox politics stories had a left-leaning match by words; after adding outlets on 2026-11-07, 3 of 12 right-lens stories did. For the rest, the AI-picked article is often only on the same issue or a related topic, and the page labels it that way.
- **One-sided feeds**: with the placeholder ratings, a right lens sees only Fox News and a left lens sees The New York Times and MS NOW. Center outlets (none live) appear only for a "both" lens.
- **AI notes**: these see only the headline and a one-line summary, so they can be thin or wrong. The UI says so and links to the original. Verified live on 2026-11-07: the first call took about 10 seconds, and repeat views come from the cache.
- **Storage**: everything is in memory. A restart clears caches and costs one fresh AI call per story viewed.
- **First AI view**: the first view of a story waits a few seconds for Claude. Later views are cached.
- **No auth or rate limiting** on the API. Do not expose it publicly as is, since anyone could run up AI costs.
- **Static asset caching**: assets use `?v=N` in `index.html`. Bump N when you change CSS or JS.

## Layout

```
src/main/java/com/settorise/
  config/   AppConfig (APP_NAME, sources), AppSettings (env vars)
  news/     RssClient (Rome), NytClient, NewsService (cache + startup check), Classifier, OutletLeans
  ai/       SummaryService (Claude), Framings, AiResult
  api/      ApiController, FeedService (tabs, outlet mixing, related coverage)
src/main/resources/
  application.properties, outlet-leans.json
  static/   index.html, css/styles.css, js/{config,compass,app}.js
```
