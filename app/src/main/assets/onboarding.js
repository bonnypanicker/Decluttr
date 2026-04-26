var DecluttrOnboarding = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __require = /* @__PURE__ */ ((x) => typeof require !== "undefined" ? require : typeof Proxy !== "undefined" ? new Proxy(x, {
    get: (a, b) => (typeof require !== "undefined" ? require : a)[b]
  }) : x)(function(x) {
    if (typeof require !== "undefined") return require.apply(this, arguments);
    throw Error('Dynamic require of "' + x + '" is not supported');
  });
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // app/src/main/assets/decluttr-onboarding.jsx
  var decluttr_onboarding_exports = {};
  __export(decluttr_onboarding_exports, {
    default: () => DecluttrOnboarding
  });
  var import_react = __require("react");
  var slides = [
    {
      id: 0,
      tag: "The problem",
      headline: ["Your phone is", "packed.", "Your focus", "isn't free."],
      body: "The average phone holds 80+ apps. Most haven't been opened in months \u2014 yet they quietly drain your battery, ping your notifications, and crowd the one screen you look at 150 times a day.",
      visual: "clutter",
      accent: "#FF6B35"
    },
    {
      id: 1,
      tag: "Sound familiar?",
      headline: ["You don't want", "to delete it.", "You just don't", "need it right now."],
      body: "After ten searches and five bad installs, you finally found the PDF merger that actually works. You use it twice a year. But you're keeping it \u2014 because finding it again would cost you an hour you don't have.",
      visual: "bookshelf",
      accent: "#4ECDC4",
      examples: ["PDF Merger", "Tax Calculator", "Travel Translator", "QR Scanner", "Unit Converter"]
    },
    {
      id: 2,
      tag: "The Decluttr way",
      headline: ["Remove it", "from your phone.", "Keep it on", "your shelf."],
      body: "Archive an app in one tap. It leaves your phone \u2014 storage freed, battery saved, home screen cleared \u2014 but lives in your personal archive, ready to reinstall from the Play Store the moment you need it again.",
      visual: "archive",
      accent: "#A78BFA"
    },
    {
      id: 3,
      tag: "The notes feature",
      headline: ["Leave yourself", "a note.", "Future you", "will know why."],
      body: `This is what makes Decluttr different. Before you archive, write what the app does best. Six months later, you won't open a mystery icon \u2014 you'll open your own review: "Best PDF merger, handles scanned docs, no watermark on free tier."`,
      visual: "notes",
      accent: "#F59E0B",
      noteExample: "Best PDF merger I found. Handles scanned docs, no watermark on free tier. Only use during tax season."
    },
    {
      id: 4,
      tag: "Find what to clean",
      headline: ["Decluttr finds", "what you", "haven't touched", "in months."],
      body: "Connect your usage stats once and Decluttr surfaces your rarely-opened apps automatically. Review them in bulk, archive the ones worth keeping, uninstall the rest. One session, a lighter phone.",
      visual: "discover",
      accent: "#34D399",
      stats: [
        { label: "Storage freed", value: "2.4 GB" },
        { label: "Apps archived", value: "18" },
        { label: "Still accessible", value: "100%" }
      ]
    },
    {
      id: 5,
      tag: "Your shelf, everywhere",
      headline: ["Your archive", "lives in the", "cloud. Safe.", "Always yours."],
      body: "Every archived app \u2014 its link, your notes, your categories \u2014 syncs to your account. Switch phones, reinstall Decluttr, and your entire shelf is waiting.",
      visual: "cloud",
      accent: "#60A5FA",
      disclaimer: "Decluttr saves your app archive and notes \u2014 not the app itself. Reinstalling opens the Play Store listing. Your in-app data (game saves, documents) is managed by each app's own backup."
    }
  ];
  var ClutterVisual = ({ accent }) => {
    const [tick, setTick] = (0, import_react.useState)(0);
    (0, import_react.useEffect)(() => {
      const id = setInterval(() => setTick((t) => t + 1), 1800);
      return () => clearInterval(id);
    }, []);
    const grid = [
      { emoji: "\u{1F4C4}", name: "Docs", badge: 0, dim: false, color: "#4A90E2" },
      { emoji: "\u{1F3B5}", name: "Music", badge: 0, dim: false, color: "#E2574A" },
      { emoji: "\u{1F6D2}", name: "Shop", badge: 7, dim: true, color: "#F5A623" },
      { emoji: "\u{1F5FA}\uFE0F", name: "Maps", badge: 0, dim: false, color: "#34A853" },
      { emoji: "\u{1F4B8}", name: "Tax", badge: 0, dim: true, color: "#9B59B6" },
      { emoji: "\u{1F4F7}", name: "Cam", badge: 0, dim: false, color: "#E74C3C" },
      { emoji: "\u{1F4F0}", name: "News", badge: 12, dim: true, color: "#1ABC9C" },
      { emoji: "\u{1F527}", name: "PDF", badge: 0, dim: true, color: "#E67E22" },
      { emoji: "\u{1F4AC}", name: "Chat", badge: 3, dim: false, color: "#3498DB" },
      { emoji: "\u{1F310}", name: "VPN", badge: 0, dim: true, color: "#8E44AD" },
      { emoji: "\u{1F3CB}\uFE0F", name: "Fit", badge: 1, dim: true, color: "#27AE60" },
      { emoji: "\u{1F50D}", name: "Scan", badge: 0, dim: true, color: "#E74C3C" },
      { emoji: "\u{1F4CA}", name: "Stats", badge: 0, dim: true, color: "#2980B9" },
      { emoji: "\u{1F3AE}", name: "Game", badge: 5, dim: true, color: "#D35400" },
      { emoji: "\u{1F324}\uFE0F", name: "Weathr", badge: 0, dim: false, color: "#16A085" },
      { emoji: "\u{1F514}", name: "Notif", badge: 9, dim: true, color: "#C0392B" }
    ];
    const battery = 34 - tick % 3;
    const notifCount = [12, 14, 11, 15][tick % 4];
    return /* @__PURE__ */ React.createElement("div", { style: {
      display: "flex",
      justifyContent: "center",
      alignItems: "flex-start",
      width: "100%",
      height: "100%",
      position: "relative"
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      width: 160,
      height: 240,
      borderRadius: "50%",
      background: `radial-gradient(circle, ${accent}22 0%, transparent 70%)`,
      top: "50%",
      left: "50%",
      transform: "translate(-50%, -50%)",
      pointerEvents: "none"
    } }), /* @__PURE__ */ React.createElement("div", { style: {
      width: 168,
      height: 318,
      borderRadius: "22px",
      background: "linear-gradient(175deg, #222228 0%, #101014 100%)",
      border: "1.5px solid rgba(255,255,255,0.10)",
      boxShadow: `0 28px 60px rgba(0,0,0,0.65), 0 0 0 1px rgba(255,255,255,0.03), inset 0 1px 0 rgba(255,255,255,0.06)`,
      display: "flex",
      flexDirection: "column",
      overflow: "hidden",
      position: "relative",
      flexShrink: 0
    } }, [38, 62, 82].map((top, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      position: "absolute",
      left: -3,
      top,
      width: 3,
      height: i === 0 ? 10 : 18,
      borderRadius: "2px 0 0 2px",
      background: "rgba(255,255,255,0.12)"
    } })), /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      right: -3,
      top: 60,
      width: 3,
      height: 22,
      borderRadius: "0 2px 2px 0",
      background: "rgba(255,255,255,0.15)"
    } }), /* @__PURE__ */ React.createElement("div", { style: {
      display: "flex",
      justifyContent: "space-between",
      alignItems: "center",
      padding: "10px 14px 0",
      fontSize: 8,
      position: "relative",
      zIndex: 10,
      color: "rgba(255,255,255,0.55)",
      fontFamily: "'DM Sans', sans-serif",
      fontWeight: 600
    } }, /* @__PURE__ */ React.createElement("span", null, "9:41"), /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      left: "50%",
      top: 8,
      transform: "translateX(-50%)",
      width: 9,
      height: 9,
      borderRadius: "50%",
      background: "#060608",
      boxShadow: "0 0 0 1px rgba(255,255,255,0.08)"
    } }), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 4 } }, /* @__PURE__ */ React.createElement("div", { style: { position: "relative" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 8 } }, "\u{1F514}"), /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      top: -2,
      right: -3,
      width: 11,
      height: 11,
      borderRadius: "50%",
      background: accent,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      fontSize: 6,
      fontWeight: 800,
      color: "#0a0b0f",
      transition: "all 0.3s ease"
    } }, notifCount)), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "flex-end", gap: 1 } }, [3, 5, 7, 9].map((h, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      width: 2,
      height: h,
      borderRadius: 1,
      background: i < 3 ? "rgba(255,255,255,0.5)" : "rgba(255,255,255,0.15)"
    } }))), /* @__PURE__ */ React.createElement("svg", { width: "10", height: "8", viewBox: "0 0 10 8", fill: "none" }, /* @__PURE__ */ React.createElement("path", { d: "M5 6.5a1 1 0 100 2 1 1 0 000-2z", fill: "rgba(255,255,255,0.5)" }), /* @__PURE__ */ React.createElement("path", { d: "M2.5 4.5C3.2 3.8 4 3.5 5 3.5s1.8.3 2.5 1", stroke: "rgba(255,255,255,0.4)", strokeWidth: "1", strokeLinecap: "round", fill: "none" }), /* @__PURE__ */ React.createElement("path", { d: "M0.5 2.5C1.8 1.2 3.3.5 5 .5s3.2.7 4.5 2", stroke: "rgba(255,255,255,0.2)", strokeWidth: "1", strokeLinecap: "round", fill: "none" })), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 2 } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center" } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 15,
      height: 8,
      borderRadius: "2px",
      border: `1px solid ${battery < 20 ? accent : "rgba(255,255,255,0.35)"}`,
      padding: "1px",
      position: "relative"
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      height: "100%",
      borderRadius: "1px",
      width: `${battery}%`,
      background: battery < 20 ? accent : "rgba(255,255,255,0.55)",
      transition: "width 0.8s ease"
    } })), /* @__PURE__ */ React.createElement("div", { style: {
      width: 2,
      height: 4,
      borderRadius: "0 1px 1px 0",
      background: battery < 20 ? accent : "rgba(255,255,255,0.3)",
      marginLeft: 0
    } })), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 7, color: battery < 20 ? accent : "rgba(255,255,255,0.35)", transition: "color 0.5s" } }, battery, "%")))), /* @__PURE__ */ React.createElement("div", { style: {
      position: "absolute",
      inset: 0,
      background: `radial-gradient(ellipse at 50% 30%, ${accent}08, transparent 60%)`,
      pointerEvents: "none"
    } }), /* @__PURE__ */ React.createElement("div", { style: {
      flex: 1,
      padding: "6px 10px 4px",
      display: "grid",
      gridTemplateColumns: "repeat(4, 1fr)",
      gap: "6px 4px",
      alignContent: "start"
    } }, grid.map((app, i) => /* @__PURE__ */ React.createElement(
      "div",
      {
        key: i,
        style: {
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          gap: 3,
          animation: `fadeSlideIn 0.4s ease both`,
          animationDelay: `${i * 0.03}s`,
          opacity: app.dim ? 0.38 : 1,
          transition: "opacity 0.4s ease"
        }
      },
      /* @__PURE__ */ React.createElement("div", { style: { position: "relative" } }, /* @__PURE__ */ React.createElement("div", { style: {
        width: 33,
        height: 33,
        borderRadius: 9,
        background: app.dim ? "rgba(255,255,255,0.05)" : `linear-gradient(145deg, ${app.color}30, ${app.color}10)`,
        border: `1px solid ${app.dim ? "rgba(255,255,255,0.06)" : app.color + "35"}`,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 15,
        filter: app.dim ? "grayscale(0.8)" : "none"
      } }, app.emoji), app.badge > 0 && /* @__PURE__ */ React.createElement("div", { style: {
        position: "absolute",
        top: -3,
        right: -3,
        minWidth: 13,
        height: 13,
        borderRadius: 7,
        background: "#FF3B30",
        border: "1.5px solid #0a0b0f",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 7,
        fontWeight: 800,
        color: "#fff",
        fontFamily: "'DM Sans', sans-serif",
        padding: "0 2px",
        animation: "blink 2s ease-in-out infinite",
        animationDelay: `${i * 0.3}s`
      } }, app.badge)),
      /* @__PURE__ */ React.createElement("span", { style: {
        fontSize: 7,
        color: app.dim ? "rgba(255,255,255,0.2)" : "rgba(255,255,255,0.65)",
        fontFamily: "'DM Sans', sans-serif",
        fontWeight: 500,
        maxWidth: 34,
        textAlign: "center",
        lineHeight: 1.1,
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap"
      } }, app.name)
    ))), /* @__PURE__ */ React.createElement("div", { style: {
      padding: "6px 0 10px",
      display: "flex",
      justifyContent: "center",
      alignItems: "center"
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 90,
      height: 4,
      borderRadius: 2,
      background: "rgba(255,255,255,0.18)"
    } }))), [
      { text: "37 notifications", top: "18%", left: "-8px", align: "flex-end" },
      { text: "14 apps unused", top: "42%", left: "-8px", align: "flex-end" },
      { text: "Battery at 34%", top: "66%", left: "-8px", align: "flex-end" }
    ].map((label, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      position: "absolute",
      top: label.top,
      left: label.left,
      display: "flex",
      alignItems: "center",
      gap: 5,
      flexDirection: "row-reverse",
      animation: `fadeSlideIn 0.5s ease both`,
      animationDelay: `${0.4 + i * 0.12}s`
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 20,
      height: 1,
      background: `linear-gradient(to left, ${accent}60, transparent)`
    } }), /* @__PURE__ */ React.createElement("span", { style: {
      fontSize: 9,
      color: `${accent}90`,
      fontFamily: "'DM Sans', sans-serif",
      fontWeight: 700,
      letterSpacing: "0.04em",
      whiteSpace: "nowrap",
      textAlign: "right"
    } }, label.text))));
  };
  var BookshelfVisual = ({ examples, accent }) => /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10, width: "100%" } }, examples.map((name, i) => /* @__PURE__ */ React.createElement(
    "div",
    {
      key: i,
      style: {
        display: "flex",
        alignItems: "center",
        gap: 12,
        padding: "10px 14px",
        borderRadius: 12,
        background: "rgba(255,255,255,0.04)",
        border: "1px solid rgba(255,255,255,0.08)",
        animation: `slideIn 0.4s ease both`,
        animationDelay: `${i * 0.08}s`
      }
    },
    /* @__PURE__ */ React.createElement("div", { style: {
      width: 36,
      height: 36,
      borderRadius: 8,
      background: `linear-gradient(135deg, ${accent}30, ${accent}10)`,
      border: `1px solid ${accent}40`,
      flexShrink: 0
    } }),
    /* @__PURE__ */ React.createElement("div", { style: { flex: 1 } }, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 13, fontWeight: 600, color: "rgba(255,255,255,0.9)", fontFamily: "'DM Sans', sans-serif" } }, name), /* @__PURE__ */ React.createElement("div", { style: { fontSize: 11, color: "rgba(255,255,255,0.35)", marginTop: 2, fontFamily: "'DM Sans', sans-serif" } }, "Used twice in the last year")),
    /* @__PURE__ */ React.createElement("div", { style: {
      fontSize: 10,
      fontWeight: 700,
      color: accent,
      background: `${accent}15`,
      padding: "3px 8px",
      borderRadius: 20,
      fontFamily: "'DM Sans', sans-serif",
      letterSpacing: "0.05em"
    } }, "KEEP")
  )));
  var ArchiveVisual = ({ accent }) => /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", alignItems: "center", gap: 16, width: "100%" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 0, width: "100%", justifyContent: "center" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", alignItems: "center", gap: 8 } }, /* @__PURE__ */ React.createElement("div", { style: {
    width: 72,
    height: 120,
    borderRadius: 14,
    border: "2px solid rgba(255,255,255,0.15)",
    background: "rgba(255,255,255,0.04)",
    display: "flex",
    flexDirection: "column",
    padding: 8,
    gap: 4,
    position: "relative",
    overflow: "hidden"
  } }, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 8, color: "rgba(255,255,255,0.3)", textAlign: "center", fontFamily: "'DM Sans', sans-serif", marginBottom: 2 } }, "PHONE"), [1, 2].map((i) => /* @__PURE__ */ React.createElement("div", { key: i, style: { height: 22, borderRadius: 6, background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.05)" } })), /* @__PURE__ */ React.createElement("div", { style: { height: 22, borderRadius: 6, background: `${accent}20`, border: `1px solid ${accent}40`, display: "flex", alignItems: "center", justifyContent: "center" } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 7, color: accent, fontFamily: "'DM Sans', sans-serif", fontWeight: 700 } }, "PDF Merger")), /* @__PURE__ */ React.createElement("div", { style: { height: 22, borderRadius: 6, background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.05)" } }), /* @__PURE__ */ React.createElement("div", { style: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: "rgba(0,0,0,0.5)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 12
  } }, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 28, color: `${accent}cc` } }, "\u2197"))), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10, color: "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif" } }, "Freed")), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", alignItems: "center", gap: 4, padding: "0 16px" } }, /* @__PURE__ */ React.createElement("div", { style: { width: 50, height: 2, background: `linear-gradient(to right, transparent, ${accent}, transparent)` } }), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 9, color: accent, fontFamily: "'DM Sans', sans-serif", fontWeight: 700, letterSpacing: "0.1em" } }, "ARCHIVE")), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", alignItems: "center", gap: 8 } }, /* @__PURE__ */ React.createElement("div", { style: {
    width: 72,
    height: 120,
    borderRadius: 14,
    border: `2px solid ${accent}40`,
    background: `${accent}08`,
    display: "flex",
    flexDirection: "column",
    padding: 8,
    gap: 4
  } }, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 8, color: `${accent}80`, textAlign: "center", fontFamily: "'DM Sans', sans-serif", marginBottom: 2 } }, "YOUR SHELF"), ["Tax Calc", "QR Scan", "PDF Merger", "VPN Pro"].map((name, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
    height: 20,
    borderRadius: 5,
    background: i === 2 ? `${accent}25` : "rgba(255,255,255,0.06)",
    border: `1px solid ${i === 2 ? accent + "50" : "rgba(255,255,255,0.05)"}`,
    display: "flex",
    alignItems: "center",
    paddingLeft: 5
  } }, /* @__PURE__ */ React.createElement("span", { style: { fontSize: 6, color: i === 2 ? accent : "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif" } }, name)))), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10, color: `${accent}aa`, fontFamily: "'DM Sans', sans-serif" } }, "Remembered"))), /* @__PURE__ */ React.createElement("div", { style: {
    width: "100%",
    padding: "10px 14px",
    borderRadius: 10,
    background: `${accent}10`,
    border: `1px solid ${accent}25`
  } }, /* @__PURE__ */ React.createElement("p", { style: { fontSize: 11, color: "rgba(255,255,255,0.6)", margin: 0, fontFamily: "'DM Sans', sans-serif", lineHeight: 1.6, textAlign: "center" } }, "One tap reinstall via Play Store \u2014 whenever you need it back")));
  var NotesVisual = ({ accent, noteExample }) => /* @__PURE__ */ React.createElement("div", { style: { width: "100%", position: "relative" } }, /* @__PURE__ */ React.createElement("div", { style: {
    borderRadius: 16,
    border: `1px solid ${accent}30`,
    background: `linear-gradient(135deg, ${accent}08, rgba(255,255,255,0.02))`,
    padding: 16,
    position: "relative",
    overflow: "hidden"
  } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 10, marginBottom: 14 } }, /* @__PURE__ */ React.createElement("div", { style: {
    width: 42,
    height: 42,
    borderRadius: 10,
    background: `linear-gradient(135deg, ${accent}40, ${accent}15)`,
    border: `1px solid ${accent}50`
  } }), /* @__PURE__ */ React.createElement("div", null, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 13, fontWeight: 700, color: "rgba(255,255,255,0.9)", fontFamily: "'DM Sans', sans-serif" } }, "PDF Merger Pro"), /* @__PURE__ */ React.createElement("div", { style: { fontSize: 11, color: "rgba(255,255,255,0.35)", fontFamily: "'DM Sans', sans-serif" } }, "Archived \xB7 3 months ago")), /* @__PURE__ */ React.createElement("div", { style: {
    marginLeft: "auto",
    fontSize: 10,
    color: accent,
    fontWeight: 700,
    background: `${accent}15`,
    padding: "4px 10px",
    borderRadius: 20,
    border: `1px solid ${accent}30`,
    fontFamily: "'DM Sans', sans-serif"
  } }, "Tools")), /* @__PURE__ */ React.createElement("div", { style: {
    background: "rgba(0,0,0,0.25)",
    borderRadius: 10,
    padding: "12px 14px",
    border: "1px solid rgba(255,255,255,0.07)",
    position: "relative"
  } }, /* @__PURE__ */ React.createElement("div", { style: {
    display: "flex",
    alignItems: "center",
    gap: 6,
    marginBottom: 8
  } }, /* @__PURE__ */ React.createElement("div", { style: { width: 6, height: 6, borderRadius: "50%", background: accent } }), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 10, fontWeight: 700, color: `${accent}cc`, fontFamily: "'DM Sans', sans-serif", letterSpacing: "0.08em" } }, "YOUR NOTE")), /* @__PURE__ */ React.createElement("p", { style: {
    fontSize: 12,
    color: "rgba(255,255,255,0.75)",
    margin: 0,
    fontFamily: "'Georgia', serif",
    lineHeight: 1.7,
    fontStyle: "italic"
  } }, '"', noteExample, '"'), /* @__PURE__ */ React.createElement("span", { style: {
    display: "inline-block",
    width: 2,
    height: 14,
    background: accent,
    marginLeft: 2,
    verticalAlign: "middle",
    animation: "blink 1s step-end infinite"
  } }))));
  var DiscoverVisual = ({ accent, stats }) => {
    const items = [
      { name: "Currency Converter", days: 184, size: "24 MB" },
      { name: "QR Code Reader", days: 97, size: "18 MB" },
      { name: "Screen Recorder", days: 220, size: "61 MB" },
      { name: "Language Tutor", days: 145, size: "89 MB" }
    ];
    return /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 10, width: "100%" } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 8, marginBottom: 4 } }, stats.map((s, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      flex: 1,
      padding: "8px 6px",
      borderRadius: 10,
      background: "rgba(255,255,255,0.04)",
      border: "1px solid rgba(255,255,255,0.07)",
      textAlign: "center"
    } }, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 15, fontWeight: 800, color: accent, fontFamily: "'DM Sans', sans-serif" } }, s.value), /* @__PURE__ */ React.createElement("div", { style: { fontSize: 9, color: "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif", marginTop: 2 } }, s.label)))), items.map((item, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
      display: "flex",
      alignItems: "center",
      gap: 10,
      padding: "9px 12px",
      borderRadius: 10,
      background: "rgba(255,255,255,0.03)",
      border: "1px solid rgba(255,255,255,0.07)",
      animation: "slideIn 0.3s ease both",
      animationDelay: `${i * 0.07}s`
    } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 32,
      height: 32,
      borderRadius: 8,
      background: `linear-gradient(135deg, ${accent}20, ${accent}08)`,
      border: `1px solid ${accent}30`,
      flexShrink: 0
    } }), /* @__PURE__ */ React.createElement("div", { style: { flex: 1, minWidth: 0 } }, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 12, fontWeight: 600, color: "rgba(255,255,255,0.85)", fontFamily: "'DM Sans', sans-serif", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" } }, item.name), /* @__PURE__ */ React.createElement("div", { style: { fontSize: 10, color: "rgba(255,255,255,0.3)", fontFamily: "'DM Sans', sans-serif", marginTop: 1 } }, "Last used ", item.days, " days ago \xB7 ", item.size)), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 5 } }, /* @__PURE__ */ React.createElement("div", { style: {
      width: 26,
      height: 26,
      borderRadius: 6,
      background: `${accent}20`,
      border: `1px solid ${accent}35`,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      fontSize: 12,
      cursor: "pointer"
    } }, "\u{1F4E6}"), /* @__PURE__ */ React.createElement("div", { style: {
      width: 26,
      height: 26,
      borderRadius: 6,
      background: "rgba(255,80,80,0.1)",
      border: "1px solid rgba(255,80,80,0.2)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      fontSize: 12,
      cursor: "pointer"
    } }, "\u{1F5D1}")))));
  };
  var CloudVisual = ({ accent, disclaimer }) => /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 12, width: "100%" } }, /* @__PURE__ */ React.createElement("div", { style: {
    borderRadius: 14,
    border: `1px solid ${accent}25`,
    background: `${accent}06`,
    padding: "14px 16px"
  } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "space-around", alignItems: "center", marginBottom: 10 } }, ["\u{1F4F1} Phone A", "\u2601\uFE0F Cloud", "\u{1F4F1} Phone B"].map((label, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: { display: "flex", flexDirection: "column", alignItems: "center", gap: 4 } }, /* @__PURE__ */ React.createElement("div", { style: {
    width: 40,
    height: 40,
    borderRadius: 10,
    background: i === 1 ? `${accent}25` : "rgba(255,255,255,0.06)",
    border: `1px solid ${i === 1 ? accent + "50" : "rgba(255,255,255,0.1)"}`,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: 18
  } }, label.split(" ")[0]), /* @__PURE__ */ React.createElement("span", { style: { fontSize: 9, color: "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif" } }, label.split(" ")[1])))), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 4 } }, ["Archive", "Notes", "Categories", "Tags"].map((item, i) => /* @__PURE__ */ React.createElement("div", { key: i, style: {
    flex: 1,
    padding: "4px 0",
    borderRadius: 6,
    textAlign: "center",
    background: `${accent}15`,
    border: `1px solid ${accent}25`,
    fontSize: 9,
    color: `${accent}cc`,
    fontFamily: "'DM Sans', sans-serif",
    fontWeight: 600
  } }, item)))), /* @__PURE__ */ React.createElement("div", { style: {
    borderRadius: 12,
    border: "1px solid rgba(255,255,255,0.08)",
    background: "rgba(255,255,255,0.03)",
    padding: "12px 14px"
  } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 8, alignItems: "flex-start" } }, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 14, flexShrink: 0, marginTop: 1 } }, "\u{1F4A1}"), /* @__PURE__ */ React.createElement("div", null, /* @__PURE__ */ React.createElement("div", { style: { fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", fontFamily: "'DM Sans', sans-serif", marginBottom: 4, letterSpacing: "0.04em" } }, "GOOD TO KNOW"), /* @__PURE__ */ React.createElement("p", { style: { fontSize: 11, color: "rgba(255,255,255,0.45)", margin: 0, fontFamily: "'DM Sans', sans-serif", lineHeight: 1.6 } }, disclaimer)))));
  var visuals = {
    clutter: ClutterVisual,
    bookshelf: BookshelfVisual,
    archive: ArchiveVisual,
    notes: NotesVisual,
    discover: DiscoverVisual,
    cloud: CloudVisual
  };
  function DecluttrOnboarding() {
    const [current, setCurrent] = (0, import_react.useState)(0);
    const [animDir, setAnimDir] = (0, import_react.useState)(1);
    const [isAnimating, setIsAnimating] = (0, import_react.useState)(false);
    const [visible, setVisible] = (0, import_react.useState)(true);
    const touchStartX = (0, import_react.useRef)(null);
    const goTo = (index, dir = 1) => {
      if (isAnimating || index === current) return;
      setIsAnimating(true);
      setAnimDir(dir);
      setVisible(false);
      setTimeout(() => {
        setCurrent(index);
        setVisible(true);
        setTimeout(() => setIsAnimating(false), 400);
      }, 250);
    };
    const next = () => current < slides.length - 1 && goTo(current + 1, 1);
    const prev = () => current > 0 && goTo(current - 1, -1);
    const onTouchStart = (e) => {
      touchStartX.current = e.touches[0].clientX;
    };
    const onTouchEnd = (e) => {
      if (!touchStartX.current) return;
      const diff = touchStartX.current - e.changedTouches[0].clientX;
      if (Math.abs(diff) > 40) diff > 0 ? next() : prev();
      touchStartX.current = null;
    };
    const slide = slides[current];
    const Visual = visuals[slide.visual];
    return /* @__PURE__ */ React.createElement(React.Fragment, null, /* @__PURE__ */ React.createElement("style", null, `
        @import url('https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=DM+Sans:wght@400;500;600;700;800&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { background: #0a0b0f; }

        @keyframes float0 { 0%,100%{transform:translateY(0) rotate(-4deg)} 50%{transform:translateY(-8px) rotate(-4deg)} }
        @keyframes float1 { 0%,100%{transform:translateY(0) rotate(2deg)} 50%{transform:translateY(-12px) rotate(2deg)} }
        @keyframes float2 { 0%,100%{transform:translateY(0) rotate(-1deg)} 50%{transform:translateY(-6px) rotate(-1deg)} }
        @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }
        @keyframes slideIn { from{opacity:0;transform:translateX(20px)} to{opacity:1;transform:translateX(0)} }
        @keyframes fadeSlideIn { from{opacity:0;transform:translateY(16px)} to{opacity:1;transform:translateY(0)} }
        @keyframes fadeSlideOut { from{opacity:1;transform:translateY(0)} to{opacity:0;transform:translateY(-12px)} }
        @keyframes progressFill { from{width:0} to{width:100%} }

        .slide-enter { animation: fadeSlideIn 0.4s cubic-bezier(0.22,1,0.36,1) both; }
        .slide-exit { animation: fadeSlideOut 0.25s ease both; }

        .next-btn:hover { background: rgba(255,255,255,0.12) !important; }
        .dot:hover { transform: scale(1.3); }
        
        .get-started-btn:hover { filter: brightness(1.1); transform: translateY(-1px); box-shadow: 0 8px 24px rgba(0,0,0,0.4); }
        .get-started-btn { transition: all 0.2s ease; }
      `), /* @__PURE__ */ React.createElement(
      "div",
      {
        style: {
          width: "100%",
          maxWidth: 390,
          minHeight: "100dvh",
          margin: "0 auto",
          background: "#0a0b0f",
          display: "flex",
          flexDirection: "column",
          position: "relative",
          overflow: "hidden",
          userSelect: "none"
        },
        onTouchStart,
        onTouchEnd
      },
      /* @__PURE__ */ React.createElement("div", { style: {
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        background: `radial-gradient(ellipse 80% 50% at 50% 0%, ${slide.accent}12 0%, transparent 60%)`,
        transition: "background 0.6s ease"
      } }),
      /* @__PURE__ */ React.createElement("div", { style: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        padding: "calc(env(safe-area-inset-top, 0px) + 18px) 28px 0",
        position: "relative",
        zIndex: 10
      } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", alignItems: "center", gap: 7 } }, /* @__PURE__ */ React.createElement("div", { style: {
        width: 28,
        height: 28,
        borderRadius: 8,
        background: "rgba(255,255,255,0.06)",
        border: "1px solid rgba(255,255,255,0.15)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center"
      } }, /* @__PURE__ */ React.createElement("img", { src: "file:///android_asset/icon_final.png", alt: "Decluttr", style: { width: 20, height: 20, objectFit: "contain" } })), /* @__PURE__ */ React.createElement("span", { style: { fontFamily: "'DM Serif Display', serif", fontSize: 16, color: "rgba(255,255,255,0.9)", letterSpacing: "-0.01em" } }, "Decluttr")), current < slides.length - 1 && /* @__PURE__ */ React.createElement(
        "button",
        {
          onClick: () => goTo(slides.length - 1, 1),
          style: {
            background: "none",
            border: "none",
            cursor: "pointer",
            fontSize: 12,
            color: "rgba(255,255,255,0.35)",
            fontFamily: "'DM Sans', sans-serif",
            fontWeight: 600,
            letterSpacing: "0.06em",
            padding: "4px 0"
          }
        },
        "SKIP"
      )),
      /* @__PURE__ */ React.createElement("div", { style: { padding: "16px 28px 0", position: "relative", zIndex: 10 } }, /* @__PURE__ */ React.createElement("div", { style: { display: "flex", gap: 5 } }, slides.map((_, i) => /* @__PURE__ */ React.createElement(
        "div",
        {
          key: i,
          onClick: () => goTo(i, i > current ? 1 : -1),
          style: {
            flex: 1,
            height: 2,
            borderRadius: 2,
            background: i <= current ? slide.accent : "rgba(255,255,255,0.1)",
            cursor: "pointer",
            transition: "background 0.4s ease",
            overflow: "hidden"
          }
        },
        i === current && /* @__PURE__ */ React.createElement("div", { style: {
          height: "100%",
          background: slide.accent,
          borderRadius: 2,
          animation: "progressFill 6s linear forwards"
        } })
      )))),
      /* @__PURE__ */ React.createElement(
        "div",
        {
          key: current,
          className: "slide-enter",
          style: {
            flex: 1,
            display: "flex",
            flexDirection: "column",
            padding: "28px 28px 0",
            position: "relative",
            zIndex: 10
          }
        },
        /* @__PURE__ */ React.createElement("div", { style: {
          display: "inline-flex",
          alignItems: "center",
          gap: 6,
          marginBottom: 18,
          alignSelf: "flex-start"
        } }, /* @__PURE__ */ React.createElement("div", { style: { width: 4, height: 4, borderRadius: "50%", background: slide.accent } }), /* @__PURE__ */ React.createElement("span", { style: {
          fontSize: 11,
          fontWeight: 700,
          color: `${slide.accent}cc`,
          fontFamily: "'DM Sans', sans-serif",
          letterSpacing: "0.12em",
          textTransform: "uppercase"
        } }, slide.tag)),
        /* @__PURE__ */ React.createElement("div", { style: { marginBottom: 20 } }, slide.headline.map((line, i) => /* @__PURE__ */ React.createElement(
          "div",
          {
            key: i,
            style: {
              fontFamily: "'DM Serif Display', serif",
              fontSize: i % 2 === 1 ? 34 : 28,
              lineHeight: 1.15,
              color: i % 2 === 1 ? "rgba(255,255,255,0.98)" : "rgba(255,255,255,0.45)",
              letterSpacing: "-0.02em",
              animation: `fadeSlideIn 0.5s cubic-bezier(0.22,1,0.36,1) both`,
              animationDelay: `${i * 0.07}s`
            }
          },
          line
        ))),
        /* @__PURE__ */ React.createElement("p", { style: {
          fontSize: 14,
          color: "rgba(255,255,255,0.5)",
          fontFamily: "'DM Sans', sans-serif",
          lineHeight: 1.7,
          marginBottom: 24,
          animation: "fadeSlideIn 0.5s ease both",
          animationDelay: "0.25s"
        } }, slide.body),
        /* @__PURE__ */ React.createElement("div", { style: {
          flex: 1,
          minHeight: 180,
          maxHeight: 280,
          animation: "fadeSlideIn 0.5s ease both",
          animationDelay: "0.3s"
        } }, /* @__PURE__ */ React.createElement(Visual, { ...slide }))
      ),
      /* @__PURE__ */ React.createElement("div", { style: {
        padding: "20px 28px 40px",
        position: "relative",
        zIndex: 10,
        background: "linear-gradient(to top, #0a0b0f 70%, transparent)"
      } }, current < slides.length - 1 ? /* @__PURE__ */ React.createElement(React.Fragment, null, /* @__PURE__ */ React.createElement(
        "button",
        {
          onClick: next,
          className: "next-btn",
          style: {
            width: "100%",
            height: 52,
            borderRadius: 14,
            background: slide.accent,
            border: "none",
            cursor: "pointer",
            fontSize: 15,
            fontWeight: 700,
            color: "#0a0b0f",
            fontFamily: "'DM Sans', sans-serif",
            letterSpacing: "-0.01em",
            transition: "background 0.3s ease",
            marginBottom: 12
          }
        },
        "Continue \u2192"
      ), /* @__PURE__ */ React.createElement("div", { style: { display: "flex", justifyContent: "center", gap: 6 } }, slides.map((_, i) => /* @__PURE__ */ React.createElement(
        "div",
        {
          key: i,
          className: "dot",
          onClick: () => goTo(i, i > current ? 1 : -1),
          style: {
            width: i === current ? 20 : 6,
            height: 6,
            borderRadius: 3,
            background: i === current ? slide.accent : "rgba(255,255,255,0.15)",
            cursor: "pointer",
            transition: "all 0.3s ease"
          }
        }
      )))) : (
        /* Last slide — sign in */
        /* @__PURE__ */ React.createElement("div", { style: { display: "flex", flexDirection: "column", gap: 12 } }, /* @__PURE__ */ React.createElement(
          "button",
          {
            className: "get-started-btn",
            style: {
              width: "100%",
              height: 54,
              borderRadius: 14,
              background: `linear-gradient(135deg, ${slide.accent}, ${slide.accent}cc)`,
              border: "none",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: 10
            }
          },
          /* @__PURE__ */ React.createElement("svg", { width: "20", height: "20", viewBox: "0 0 24 24" }, /* @__PURE__ */ React.createElement("path", { fill: "white", d: "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" }), /* @__PURE__ */ React.createElement("path", { fill: "white", d: "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" }), /* @__PURE__ */ React.createElement("path", { fill: "white", d: "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" }), /* @__PURE__ */ React.createElement("path", { fill: "white", d: "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" })),
          /* @__PURE__ */ React.createElement("span", { style: {
            fontSize: 15,
            fontWeight: 700,
            color: "#0a0b0f",
            fontFamily: "'DM Sans', sans-serif"
          } }, "Continue with Google")
        ), /* @__PURE__ */ React.createElement("p", { style: {
          fontSize: 11,
          color: "rgba(255,255,255,0.2)",
          fontFamily: "'DM Sans', sans-serif",
          textAlign: "center",
          lineHeight: 1.5,
          marginTop: 4
        } }, "By continuing you agree to our", " ", /* @__PURE__ */ React.createElement(
          "span",
          {
            style: { color: "rgba(255,255,255,0.5)", textDecoration: "underline", cursor: "pointer" },
            onClick: () => window.AndroidAuth && window.AndroidAuth.openUrl("https://decluttr-3c299.web.app/terms-and-conditions.html")
          },
          "Terms"
        ), " ", "&", " ", /* @__PURE__ */ React.createElement(
          "span",
          {
            style: { color: "rgba(255,255,255,0.5)", textDecoration: "underline", cursor: "pointer" },
            onClick: () => window.AndroidAuth && window.AndroidAuth.openUrl("https://decluttr-3c299.web.app/privacy-policy.html")
          },
          "Privacy Policy"
        )))
      ))
    ));
  }
  return __toCommonJS(decluttr_onboarding_exports);
})();
