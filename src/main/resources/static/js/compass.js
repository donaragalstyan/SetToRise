// Values compass: 12 original questions, scored entirely in the browser.
// Results are stored in localStorage only. The server only ever receives show=left|right|both.
(function () {
  "use strict";

  // dir: +1 means agreeing pulls toward the right end of the axis, -1 toward the left.
  const QUESTIONS = [
    // Economic axis
    { axis: "econ", dir: +1, text: "Cutting taxes usually does more for the economy than new government spending." },
    { axis: "econ", dir: -1, text: "The government should make sure everyone has health coverage, even if taxes go up." },
    { axis: "econ", dir: -1, text: "Raising the minimum wage helps workers more than it hurts small businesses." },
    { axis: "econ", dir: +1, text: "Businesses would hire and grow more with fewer regulations." },
    { axis: "econ", dir: -1, text: "The government should actively work to narrow the gap between rich and poor." },
    { axis: "econ", dir: +1, text: "Private companies usually provide services more efficiently than government agencies." },
    // Social axis
    { axis: "social", dir: +1, text: "Stricter enforcement of immigration laws should be a top national priority." },
    { axis: "social", dir: -1, text: "It should be harder to legally buy a gun." },
    { axis: "social", dir: +1, text: "Long-standing traditions and institutions deserve more weight when making laws." },
    { axis: "social", dir: -1, text: "Schools should spend more time on how past discrimination shapes society today." },
    { axis: "social", dir: +1, text: "Police departments in my area need more funding." },
    { axis: "social", dir: -1, text: "Decisions about abortion should be left to the individual, not restricted by law." },
  ];

  // 4-point scale, no neutral middle.
  const SCALE = ["Strongly disagree", "Disagree", "Agree", "Strongly agree"];

  function cfg() { return window.APP_CONFIG.compass; }
  function key() { return window.APP_CONFIG.storageKeys.compass; }

  // answers: array of 0..3 (index into SCALE), one per question.
  function score(answers) {
    const sums = { econ: 0, social: 0 };
    const counts = { econ: 0, social: 0 };
    QUESTIONS.forEach((q, i) => {
      const a = answers[i];
      if (a === null || a === undefined) { return; }
      const agreement = (a - 1.5) / 1.5; // -1, -1/3, +1/3, +1
      sums[q.axis] += agreement * q.dir;
      counts[q.axis] += 1;
    });
    const econ = counts.econ ? sums.econ / counts.econ : 0;
    const social = counts.social ? sums.social / counts.social : 0;
    return { econ, social, show: showFor(econ, social) };
  }

  function showFor(econ, social) {
    const c = cfg();
    if (Math.abs(econ - social) >= c.splitThreshold) { return "both"; }
    const overall = (econ + social) / 2;
    if (overall <= -c.leanThreshold) { return "left"; }
    if (overall >= c.leanThreshold) { return "right"; }
    return "both";
  }

  function load() {
    try {
      const raw = localStorage.getItem(key());
      if (!raw) { return null; }
      const r = JSON.parse(raw);
      if (r && ["left", "right", "both"].includes(r.show)) { return r; }
    } catch (e) { /* storage blocked or corrupt: treat as no result */ }
    return null;
  }

  function save(answers) {
    const r = score(answers);
    const record = { econ: r.econ, social: r.social, show: r.show, answers, takenAt: new Date().toISOString() };
    try { localStorage.setItem(key(), JSON.stringify(record)); } catch (e) { /* still usable this session */ }
    return record;
  }

  function skip() {
    const record = { econ: 0, social: 0, show: "both", answers: [], skipped: true, takenAt: new Date().toISOString() };
    try { localStorage.setItem(key(), JSON.stringify(record)); } catch (e) { /* ignore */ }
    return record;
  }

  function clear() {
    try { localStorage.removeItem(key()); } catch (e) { /* ignore */ }
  }

  window.Compass = { QUESTIONS, SCALE, score, showFor, load, save, skip, clear };
})();
