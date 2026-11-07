// SetToRise frontend. Plain JS, no build step.
// Privacy: the compass result lives in localStorage. Requests carry only show=left|right|both.
(function () {
  "use strict";

  const CFG = window.APP_CONFIG;
  // Remove data saved by earlier versions (see legacyStorageKeys in config.js).
  for (const k of CFG.legacyStorageKeys || []) {
    try { localStorage.removeItem(k); } catch (e) { /* ignore */ }
  }
  const view = document.getElementById("view");
  const tabsNav = document.getElementById("tabs");
  const lensTab = document.getElementById("lens-tab");
  const VOTE_URL = "https://vote.gov/";
  let meta = { appName: CFG.appName, topics: [], aiEnabled: false };

  // ---------- tiny DOM helper (always textContent, never innerHTML for data) ----------
  function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    if (attrs) {
      for (const [k, v] of Object.entries(attrs)) {
        if (v === null || v === undefined || v === false) { continue; }
        if (k === "class") { el.className = v; }
        else if (k.startsWith("on")) { el.addEventListener(k.slice(2), v); }
        else if (k === "href") { el.setAttribute("href", safeHref(v)); }
        else { el.setAttribute(k, v === true ? "" : v); }
      }
    }
    for (const c of children.flat(Infinity)) {
      if (c === null || c === undefined || c === false) { continue; }
      el.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return el;
  }

  function safeHref(url) {
    if (typeof url !== "string") { return "#"; }
    if (url.startsWith("#") || url.startsWith("https://") || url.startsWith("http://")) { return url; }
    return "#";
  }

  function externalLink(url, text, cls) {
    return h("a", { href: url, target: "_blank", rel: "noopener noreferrer", class: cls }, text);
  }

  function render(...nodes) {
    view.replaceChildren(...nodes.flat(Infinity).filter((n) => n !== null && n !== undefined && n !== false));
  }

  function timeAgo(iso) {
    if (!iso) { return ""; }
    const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
    if (mins < 1) { return "just now"; }
    if (mins < 60) { return mins + "m ago"; }
    const hrs = Math.round(mins / 60);
    if (hrs < 24) { return hrs + "h ago"; }
    return Math.round(hrs / 24) + "d ago";
  }

  // ---------- lens (local only) ----------
  function currentShow() {
    const r = window.Compass.load();
    return r ? r.show : "both";
  }

  const LENS_NAMES = { left: "Left-leaning", right: "Right-leaning", both: "Both sides" };

  function updateLensChip() {
    const r = window.Compass.load();
    const label = r ? "Your lens: " + LENS_NAMES[r.show] : "Take the quiz";
    lensTab.setAttribute("aria-label", label);
    lensTab.title = label;
  }

  // ---------- API ----------
  async function api(path) {
    const res = await fetch(path, { headers: { Accept: "application/json" } });
    if (!res.ok) {
      const err = new Error("HTTP " + res.status);
      err.status = res.status;
      throw err;
    }
    return res.json();
  }

  // ---------- onboarding (Figma "Onboarding" frame) ----------
  // Ray positions are the Figma frame coordinates, offset so the sun group starts at (0, 0).
  const RAYS = [
    { src: "ray-left-long.svg", w: 21.6506, h: 58.8917, box: [0, 4.47, 74.571, 71.728], turn: 132.85, long: true },
    { src: "ray-right-long.svg", w: 21.6506, h: 58.8917, box: [113.81, 0, 71.728, 74.571], turn: -137.15, long: true },
    { src: "ray-top.svg", w: 21.6506, h: 31.5, box: [82, 15.47, 25, 42], turn: 180 },
    { src: "ray-left-small.svg", w: 21.6506, h: 31.5, box: [6, 71.47, 43.891, 28.327], turn: 85.34 },
    { src: "ray-right-small.svg", w: 21.6506, h: 31.5, box: [137, 67.47, 43.891, 28.327], turn: -94.66 },
  ];

  function renderWelcome() {
    setActiveTab("welcome");
    const rays = RAYS.map((r) =>
      h("div", {
        class: "ray",
        style: "left:" + r.box[0] + "px;top:" + r.box[1] + "px;width:" + r.box[2] + "px;height:" + r.box[3] + "px",
      },
      h("span", { class: "ray-box" + (r.long ? " long" : ""), style: "transform:rotate(" + r.turn + "deg)" },
        h("img", { src: "img/onboarding/" + r.src, width: String(r.w), height: String(r.h), alt: "" }))));
    render(
      h("section", { class: "welcome" },
        h("div", { class: "welcome-hero" },
          h("div", { class: "sunrise", "aria-hidden": "true" }, rays, h("div", { class: "sun" })),
          h("h1", { class: "welcome-title" }, meta.appName)),
        h("a", { class: "welcome-btn", href: "#/quiz" }, "Get Started")));
    view.focus();
  }

  // ---------- quiz ----------
  // Deletes everything this app saved on the device (lens, answers, topic picks) and starts over.
  function startOver() {
    window.Compass.clear();
    try { localStorage.removeItem(CFG.storageKeys.topics); } catch (e) { /* ignore */ }
    updateLensChip();
    location.hash = "#/welcome";
  }

  function renderQuiz() {
    setActiveTab("quiz");
    const prev = window.Compass.load();
    const answers = (prev && Array.isArray(prev.answers) && prev.answers.length === window.Compass.QUESTIONS.length)
      ? prev.answers.slice()
      : new Array(window.Compass.QUESTIONS.length).fill(null);

    const submit = h("button", { class: "btn", type: "submit", disabled: true }, "See my lens");
    const progress = h("span", { class: "muted small" });

    function refresh() {
      const done = answers.filter((a) => a !== null).length;
      progress.textContent = done + " of " + answers.length + " answered";
      submit.disabled = done < answers.length;
    }

    const questions = window.Compass.QUESTIONS.map((q, i) =>
      h("div", { class: "card quiz-q" },
        h("fieldset", null,
          h("legend", null, h("span", { class: "qnum" }, (i + 1) + "."), q.text),
          h("div", { class: "scale" },
            window.Compass.SCALE.map((label, v) =>
              h("label", null,
                h("input", {
                  type: "radio", name: "q" + i, value: String(v), checked: answers[i] === v,
                  onchange: () => { answers[i] = v; refresh(); },
                }),
                h("span", null, label))))))
    );

    const form = h("form", {
      onsubmit: (e) => {
        e.preventDefault();
        const r = window.Compass.save(answers);
        updateLensChip();
        renderQuizResult(r);
      },
    },
    questions,
    h("div", { class: "actions" }, submit, progress,
      h("button", {
        class: "linkish", type: "button",
        onclick: () => { window.Compass.skip(); updateLensChip(); location.hash = "#/politics"; },
      }, "Skip and see both sides")),
    prev
      ? h("div", { class: "actions" },
        h("button", { class: "linkish", type: "button", onclick: startOver },
          "Start over: delete my answers and topic picks from this device"))
      : null);

    render(
      h("h1", null, "A 2-minute values quiz"),
      h("p", { class: "muted" },
        "12 statements, no right answers. Your answers set a lens that frames how each story may affect you. ",
        h("strong", null, "They are scored in your browser and never leave this device."),
        " The server only ever sees which framing to show: left, right or both."),
      form);
    refresh();
    view.focus();
  }

  function plot(econ, social) {
    const ns = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(ns, "svg");
    svg.setAttribute("viewBox", "0 0 100 100");
    svg.setAttribute("class", "plot");
    svg.setAttribute("role", "img");
    svg.setAttribute("aria-label", "Your position: economic " + econ.toFixed(2) + ", social " + social.toFixed(2));
    const add = (name, attrs) => {
      const el = document.createElementNS(ns, name);
      for (const [k, v] of Object.entries(attrs)) { el.setAttribute(k, v); }
      svg.append(el);
      return el;
    };
    add("rect", { x: 1, y: 1, width: 98, height: 98, rx: 6, fill: "none", stroke: "currentColor", "stroke-opacity": 0.25 });
    add("line", { x1: 50, y1: 4, x2: 50, y2: 96, stroke: "currentColor", "stroke-opacity": 0.2 });
    add("line", { x1: 4, y1: 50, x2: 96, y2: 50, stroke: "currentColor", "stroke-opacity": 0.2 });
    const lbl = (x, y, t, anchor) => {
      const el = add("text", { x, y, "font-size": 6, fill: "currentColor", "fill-opacity": 0.6, "text-anchor": anchor });
      el.textContent = t;
    };
    lbl(5, 53.5, "Econ L", "start");
    lbl(95, 53.5, "Econ R", "end");
    lbl(51.5, 9, "Social R", "start");
    lbl(51.5, 95, "Social L", "start");
    // x: economic (-1 left .. +1 right); y: social (+1 right at top).
    add("circle", { cx: 50 + econ * 44, cy: 50 - social * 44, r: 4.5, fill: "var(--accent)" });
    return svg;
  }

  function renderQuizResult(r) {
    const explain = {
      left: "Stories will lead with how they may affect someone who leans left. The Other Side tab shows the strongest right-leaning case.",
      right: "Stories will lead with how they may affect someone who leans right. The Other Side tab shows the strongest left-leaning case.",
      both: "Your answers land near the middle (or split across the two axes), so you will see both framings side by side.",
    };
    render(
      h("h1", null, "Your lens: " + LENS_NAMES[r.show]),
      h("div", { class: "card result" },
        plot(r.econ, r.social),
        h("div", null,
          h("p", null, explain[r.show]),
          h("p", { class: "muted small" },
            "Economic: " + r.econ.toFixed(2) + " · Social: " + r.social.toFixed(2) + " (−1 left to +1 right). ",
            "Saved in this browser only. A lens is a starting point, not a label."))),
      h("div", { class: "actions" },
        h("a", { class: "btn", href: "#/politics", role: "button" }, "Go to my feed"),
        h("button", { class: "btn secondary", type: "button", onclick: renderQuiz }, "Change answers"),
        h("button", {
          class: "linkish", type: "button",
          onclick: startOver,
        }, "Delete my result")));
    view.focus();
  }

  // ---------- feed ----------
  function setActiveTab(tab) {
    document.body.classList.toggle("is-feed", ["politics", "issues", "global"].includes(tab));
    document.body.classList.toggle("is-welcome", tab === "welcome");
    for (const a of tabsNav.querySelectorAll("a")) {
      a.classList.toggle("active", a.dataset.tab === tab);
      if (a.dataset.tab === tab) { a.setAttribute("aria-current", "page"); } else { a.removeAttribute("aria-current"); }
    }
  }

  function loadTopics() {
    try {
      const t = JSON.parse(localStorage.getItem(CFG.storageKeys.topics) || "[]");
      return Array.isArray(t) ? t : [];
    } catch (e) { return []; }
  }

  function saveTopics(t) {
    try { localStorage.setItem(CFG.storageKeys.topics, JSON.stringify(t)); } catch (e) { /* ignore */ }
  }

  function leanBadge(label) {
    return h("span", { class: "lean " + label, title: "Placeholder outlet rating, not a fact" }, label);
  }

  function storyCard(s) {
    return h("article", { class: "card" },
      h("div", { class: "meta" },
        h("span", { class: "outlet" }, s.outlet),
        leanBadge(s.outletLean),
        h("span", null, timeAgo(s.publishedAt))),
      h("h3", null, h("a", { href: "#/story/" + encodeURIComponent(s.id) }, s.headline)),
      s.summary ? h("p", null, s.summary) : null,
      h("div", { class: "meta" },
        externalLink(s.link, "Read at " + s.outlet + " ↗"),
        s.electionRelated ? externalLink(VOTE_URL, "Ballot info: vote.gov ↗", "vote-link") : null),
      s.topics && s.topics.length ? h("div", { class: "tags" }, s.topics.map((t) => h("span", { class: "tag" }, t))) : null);
  }

  // Splits the outlet's one-paragraph summary into up to 3 bullet points (sentences).
  function summaryPoints(text) {
    if (!text) { return []; }
    // Split only where a sentence end is followed by a space and a capital letter,
    // then re-join splits after abbreviations such as "U.S." or "Sen.".
    const raw = text.split(/(?<=[.!?]["”’)]?)\s+(?=["“‘(]?[A-Z])/);
    const out = [];
    for (const part of raw) {
      const prev = out[out.length - 1];
      if (prev && /(?:\b(?:[A-Z]\.){2,}|\b(?:Mr|Mrs|Ms|Dr|Sen|Rep|Gov|Gen|Lt|St|Jr|Sr|No|Vs)\.)$/.test(prev)) {
        out[out.length - 1] = prev + " " + part;
      } else {
        out.push(part);
      }
    }
    return out.map((p) => p.trim()).filter(Boolean).slice(0, 3);
  }

  // The outlet's own thumbnail, loaded straight from the outlet. Grey placeholder if missing or broken.
  function media(s, eager) {
    const box = h("div", { class: "feed-media" });
    if (s.imageUrl) {
      const img = h("img", {
        src: s.imageUrl, alt: "", loading: eager ? "eager" : "lazy", decoding: "async",
        referrerpolicy: "no-referrer",
      });
      img.addEventListener("error", () => img.remove());
      box.append(img);
    }
    return box;
  }

  function feedCard(s, i) {
    const storyHref = "#/story/" + encodeURIComponent(s.id);
    const points = summaryPoints(s.summary);
    return h("article", { class: "feed-card" },
      media(s, i === 0),
      h("h2", { class: "feed-headline" }, h("a", { href: storyHref }, s.headline)),
      h("div", { class: "meta feed-credit" },
        h("span", { class: "outlet" }, s.outlet),
        leanBadge(s.outletLean),
        h("span", null, timeAgo(s.publishedAt)),
        externalLink(s.link, "Read at " + s.outlet + " ↗"),
        s.electionRelated ? externalLink(VOTE_URL, "Ballot info: vote.gov ↗", "vote-link") : null),
      points.length ? h("ul", { class: "feed-points" }, points.map((p) => h("li", null, p))) : null,
      h("a", { class: "perspective-btn", href: "#/perspective/" + encodeURIComponent(s.id) }, "See Another Perspective"));
  }

  async function renderFeed(tab) {
    setActiveTab(tab);
    const show = currentShow();
    const picker = tab === "issues" ? topicPicker(() => renderFeed(tab)) : null;
    render(picker, h("p", { class: "empty" }, h("span", { class: "spinner" }), "Loading headlines…"));
    let data;
    try {
      data = await api("/api/feed?tab=" + encodeURIComponent(tab) + "&show=" + encodeURIComponent(show));
    } catch (e) {
      render(picker, h("p", { class: "empty" }, "Could not load the feed. Try again in a moment."));
      return;
    }
    let stories = data.stories;
    if (tab === "issues") {
      const picked = loadTopics();
      if (!picked.length) {
        render(picker, h("p", { class: "empty" }, "Pick one or more topics above to build your Issues feed."));
        return;
      }
      // Topic filtering happens here, in the browser, so your topic picks also stay on this device.
      stories = stories.filter((s) => s.topics.some((t) => picked.includes(t)));
    }
    if (!stories.length) {
      render(picker, h("p", { class: "empty" }, "No recent stories here yet."));
      return;
    }
    render(picker, stories.map((s, i) => feedCard(s, i)));
  }

  function topicPicker(onChange) {
    const picked = new Set(loadTopics());
    return h("section", { class: "feed-picker" },
      h("h1", null, "Your issues"),
      h("p", { class: "muted small" }, "Picks are saved on this device only."),
      h("div", { class: "chips" },
        meta.topics.map((t) =>
          h("button", {
            class: "chip", type: "button", "aria-pressed": picked.has(t) ? "true" : "false",
            onclick: () => {
              if (picked.has(t)) { picked.delete(t); } else { picked.add(t); }
              saveTopics([...picked]);
              onChange();
            },
          }, t))));
  }

  // ---------- another perspective ----------
  const SIDE_NAMES = { left: "left-leaning", right: "right-leaning" };

  function perspectiveCard(s) {
    const points = summaryPoints(s.summary);
    return h("article", { class: "perspective-card" },
      s.imageUrl ? media(s, true) : null,
      h("h3", { class: "feed-headline" }, externalLink(s.link, s.headline)),
      h("div", { class: "meta feed-credit" },
        h("span", { class: "outlet" }, s.outlet), leanBadge(s.outletLean), h("span", null, timeAgo(s.publishedAt)),
        externalLink(s.link, "Read at " + s.outlet + " ↗")),
      points.length ? h("ul", { class: "feed-points" }, points.map((p) => h("li", null, p))) : null);
  }

  function differencesPanel(result, story, other) {
    const label = h("span", { class: "ai-label" }, "AI-generated");
    if (!result || result.status !== "ok") {
      return h("section", { class: "diff-panel" }, label,
        h("h2", null, "How the coverage differs"),
        h("p", null, (result && result.message) || "The comparison is unavailable."));
    }
    const c = result.comparison;
    if (!c.sameEvent) {
      return h("section", { class: "diff-panel" }, label,
        h("h2", null, "How the coverage differs"),
        h("p", null, "These may not be the same story: our matching is based on shared words, and the AI thinks ",
          story.outlet + " and " + other.outlet + " are covering different events."),
        h("p", { class: "muted small" }, result.label));
    }
    return h("section", { class: "diff-panel" }, label,
      h("h2", null, "How the coverage differs"),
      h("h3", null, "What both report"),
      h("p", null, c.bothReport),
      c.differences && c.differences.length
        ? [h("h3", null, "Where they differ"), h("ul", null, c.differences.map((d) => h("li", null, d)))]
        : null,
      h("p", { class: "muted small" }, result.label));
  }

  // No other-side match: fall back to the AI's strongest case from the other side.
  function fallbackPanel(ai, sides) {
    const label = h("span", { class: "ai-label" }, "AI-generated");
    if (!ai || ai.status !== "ok") {
      return h("section", { class: "diff-panel" }, label,
        h("p", null, (ai && ai.message) || "AI notes are unavailable."));
    }
    // framings.right.strongestOpposingCase is the left-leaning case, and vice versa.
    const caseFor = { left: ai.framings.right.strongestOpposingCase, right: ai.framings.left.strongestOpposingCase };
    return h("section", { class: "diff-panel" }, label,
      h("h2", null, "The other side's view"),
      sides.map((side) => [h("h3", null, "The strongest " + SIDE_NAMES[side] + " case"), h("p", null, caseFor[side])]),
      h("p", { class: "muted small" }, ai.label));
  }

  async function renderPerspective(id) {
    setActiveTab(null);
    render(h("p", { class: "empty" }, h("span", { class: "spinner" }), "Finding coverage from the other side…"));
    let data;
    try {
      data = await api("/api/perspective/" + encodeURIComponent(id));
    } catch (e) {
      render(h("a", { class: "back", href: "#/politics" }, "← Back"),
        h("p", { class: "empty" }, e.status === 404 ? "That story is no longer available." : "Could not load this page."));
      return;
    }
    const s = data.story;
    const backTab = (s.tabs && s.tabs[0]) || "politics";
    const sideText = data.otherSides.map((x) => SIDE_NAMES[x]).join(" and ");
    const others = data.otherCoverage || [];
    const exact = data.match === "same_event";
    const RELATION = { same_event: "Same story", same_issue: "Same issue", loosely_related: "Related topic", related: "Related topic" };
    let intro;
    if (!others.length) {
      intro = h("p", null, "There are no " + sideText + " stories in our feeds right now.");
    } else if (exact && !data.matchReason) {
      intro = h("p", { class: "muted small" },
        "The same story from " + sideText + " outlets. Matched by shared words, so it can miss or mismatch. Lean labels are placeholders.");
    } else {
      intro = [
        h("p", { class: "muted small" },
          exact
            ? "The same story from a " + sideText + " outlet, picked by AI. Lean labels are placeholders."
            : "We couldn't find this exact story from a " + sideText + " outlet, so here is the closest " + sideText + " coverage, picked by AI. Lean labels are placeholders."),
        h("p", { class: "match-note" },
          h("span", { class: "match-chip" }, RELATION[data.match] || "Related topic"),
          data.matchReason || null),
      ];
    }
    render(
      h("div", { class: "perspective" },
        h("a", { class: "back", href: "#/" + backTab }, "← Back"),
        h("section", { class: "you-read" },
          h("p", { class: "muted small" }, "You read"),
          h("p", { class: "you-read-headline" }, h("a", { href: "#/story/" + encodeURIComponent(s.id) }, s.headline)),
          h("div", { class: "meta" }, h("span", { class: "outlet" }, s.outlet), leanBadge(s.outletLean))),
        h("h1", null, "From the other side"),
        intro,
        others.map(perspectiveCard),
        data.comparison ? differencesPanel(data.comparison, s, others[0]) : null,
        data.ai ? fallbackPanel(data.ai, data.otherSides) : null));
    view.focus();
  }

  // ---------- story page ----------
  async function renderStory(id, initialPane) {
    setActiveTab(null);
    render(h("p", { class: "empty" }, h("span", { class: "spinner" }), "Loading story…"));
    let data;
    try {
      data = await api("/api/story/" + encodeURIComponent(id) + "?show=" + encodeURIComponent(currentShow()));
    } catch (e) {
      render(h("a", { class: "back", href: "#/politics" }, "← Back"),
        h("p", { class: "empty" }, e.status === 404 ? "That story is no longer available." : "Could not load the story."));
      return;
    }
    const s = data.story;
    const backTab = (s.tabs && s.tabs[0]) || "politics";
    let lens = currentShow();
    let pane = initialPane === "other" ? "other" : "affects";

    const body = h("div");
    const subtabs = h("div", { class: "subtabs", role: "tablist" });

    function drawPane() {
      subtabs.replaceChildren(
        h("button", { role: "tab", type: "button", "aria-selected": pane === "affects" ? "true" : "false",
          onclick: () => { pane = "affects"; drawPane(); } }, "How it may affect you"),
        h("button", { role: "tab", type: "button", "aria-selected": pane === "other" ? "true" : "false",
          onclick: () => { pane = "other"; drawPane(); } }, "Other Side"));
      body.replaceChildren(aiSection(data.ai, lens, pane));
    }

    const lensSwitch = h("div", { class: "lens-switch" },
      h("span", { class: "muted" }, "View as:"),
      ["left", "both", "right"].map((l) =>
        h("button", { class: "chip", type: "button", "aria-pressed": l === lens ? "true" : "false",
          onclick: (e) => {
            lens = l;
            for (const b of lensSwitch.querySelectorAll("button")) { b.setAttribute("aria-pressed", "false"); }
            e.currentTarget.setAttribute("aria-pressed", "true");
            drawPane();
          } }, LENS_NAMES[l])),
      h("span", { class: "muted small" }, "(this page only)"));

    drawPane();

    render(
      h("a", { class: "back", href: "#/" + backTab }, "← Back"),
      h("section", { class: "story-head" },
        h("div", { class: "meta" },
          h("span", { class: "outlet" }, s.outlet), leanBadge(s.outletLean), h("span", null, timeAgo(s.publishedAt))),
        h("h1", null, s.headline),
        s.summary ? h("p", { class: "summary" }, s.summary) : null,
        externalLink(s.link, "Read the full story at " + s.outlet + " ↗", "source-link")),
      s.electionRelated
        ? h("div", { class: "notice", style: "margin-top:16px" },
          "This story mentions an election. For official, nonpartisan info on registering and voting, visit ",
          externalLink(VOTE_URL, "vote.gov"), ".")
        : null,
      subtabs,
      lensSwitch,
      h("div", { style: "margin-top:12px" }, body),
      otherCoverage(data.otherCoverage),
      h("h2", null, "Sources"),
      h("ul", { class: "citations" },
        data.citations.map((c) => h("li", null, c.outlet + ": ", externalLink(c.url, c.url)))));
    view.focus();
  }

  function aiSection(ai, lens, pane) {
    const label = h("span", { class: "ai-label" }, "AI-generated");
    if (!ai || ai.status !== "ok") {
      return h("div", { class: "card" }, label,
        h("p", null, (ai && ai.message) || "AI notes are unavailable."),
        h("p", { class: "muted small" }, "Read the original story for the full context."));
    }
    const f = ai.framings;
    const sides = lens === "both" ? ["left", "right"] : [lens];
    const blocks = sides.map((side) => {
      if (pane === "affects") {
        return h("div", { class: "framing" },
          h("h4", { class: side }, "If you lean " + side),
          h("p", null, f[side].howThisMayAffectYou));
      }
      const other = side === "left" ? "right" : "left";
      return h("div", { class: "framing" },
        h("h4", { class: other }, "The strongest " + other + "-leaning case" + (lens === "both" ? " (for readers who lean " + side + ")" : "")),
        h("p", null, f[side].strongestOpposingCase));
    });
    return h("div", { class: "card" },
      label,
      h("h4", { style: "margin:4px 0" }, "Neutral summary"),
      h("p", { style: "margin:0 0 14px" }, f.neutralSummary),
      blocks,
      h("p", { class: "muted small", style: "margin-top:14px" }, ai.label));
  }

  function otherCoverage(list) {
    if (!list || !list.length) {
      return h("p", { class: "muted small", style: "margin-top:20px" }, "No other outlets in our feeds have a matching story yet.");
    }
    return h("section", null,
      h("h2", null, "Same story, other outlets"),
      h("p", { class: "muted small" }, "Matched by headline words, so it can miss or mismatch. Lean labels are placeholders."),
      list.map(storyCard));
  }

  // ---------- router ----------
  function route() {
    const hash = location.hash || "";
    // First visit (no lens saved on this device): show the welcome screen, then the quiz.
    if (!window.Compass.load() && hash !== "#/quiz" && hash !== "#/welcome") {
      location.hash = "#/welcome";
      return;
    }
    if (hash === "#/welcome") {
      renderWelcome();
      return;
    }
    if (hash === "#/quiz") {
      renderQuiz();
      return;
    }
    const p = hash.match(/^#\/perspective\/([A-Za-z0-9]+)$/);
    if (p) {
      renderPerspective(p[1]);
      return;
    }
    const m = hash.match(/^#\/story\/([A-Za-z0-9]+)(?:\/(other))?$/);
    if (m) {
      renderStory(m[1], m[2]);
      return;
    }
    const tab = hash.replace("#/", "");
    renderFeed(["politics", "issues", "global"].includes(tab) ? tab : "politics");
  }

  async function init() {
    try {
      meta = await api("/api/meta");
    } catch (e) { /* keep defaults */ }
    document.title = meta.appName;
    document.getElementById("brand").textContent = meta.appName;
    if (meta.nytEnabled) {
      document.getElementById("nyt-credit").hidden = false;
    }
    updateLensChip();
    window.addEventListener("hashchange", () => { route(); window.scrollTo(0, 0); });
    route();
  }

  init();
})();
