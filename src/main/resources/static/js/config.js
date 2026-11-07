// Client-side config. Compass thresholds live here so they are easy to tune.
// Nothing in this file (or in localStorage) is ever sent to the server.
window.APP_CONFIG = {
  // Fallback name; the real one comes from the server (AppConfig.APP_NAME) via /api/meta.
  appName: "Set to Rise",

  compass: {
    // Each axis is scored from -1 (left) to +1 (right).
    // The reader's overall lean is the average of both axes.
    // If it is at or below -leanThreshold we show "left"; at or above +leanThreshold, "right";
    // otherwise "both" (center readers see both framings).
    leanThreshold: 0.2,
    // If the two axes point in opposite directions by at least this much, show "both" anyway.
    splitThreshold: 0.8,
  },

  // Bump these versions to make every device start over at the welcome screen.
  // Keys listed in legacyStorageKeys are deleted on load, so old answers don't linger.
  storageKeys: {
    compass: "str.compass.v2",
    topics: "str.topics.v2",
  },
  legacyStorageKeys: ["str.compass.v1", "str.topics.v1"],
};
