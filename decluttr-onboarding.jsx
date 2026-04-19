import { useState, useEffect, useRef } from "react";

const slides = [
  {
    id: 0,
    tag: "The problem",
    headline: ["Your phone is", "packed.", "Your focus", "isn't free."],
    body: "The average phone holds 80+ apps. Most haven't been opened in months — yet they quietly drain your battery, ping your notifications, and crowd the one screen you look at 150 times a day.",
    visual: "clutter",
    accent: "#FF6B35",
  },
  {
    id: 1,
    tag: "Sound familiar?",
    headline: ["You don't want", "to delete it.", "You just don't", "need it right now."],
    body: "After ten searches and five bad installs, you finally found the PDF merger that actually works. You use it twice a year. But you're keeping it — because finding it again would cost you an hour you don't have.",
    visual: "bookshelf",
    accent: "#4ECDC4",
    examples: ["PDF Merger", "Tax Calculator", "Travel Translator", "QR Scanner", "Unit Converter"],
  },
  {
    id: 2,
    tag: "The Decluttr way",
    headline: ["Remove it", "from your phone.", "Keep it on", "your shelf."],
    body: "Archive an app in one tap. It leaves your phone — storage freed, battery saved, home screen cleared — but lives in your personal archive, ready to reinstall from the Play Store the moment you need it again.",
    visual: "archive",
    accent: "#A78BFA",
  },
  {
    id: 3,
    tag: "The notes feature",
    headline: ["Leave yourself", "a note.", "Future you", "will know why."],
    body: "This is what makes Decluttr different. Before you archive, write what the app does best. Six months later, you won't open a mystery icon — you'll open your own review: \"Best PDF merger, handles scanned docs, no watermark on free tier.\"",
    visual: "notes",
    accent: "#F59E0B",
    noteExample: "Best PDF merger I found. Handles scanned docs, no watermark on free tier. Only use during tax season.",
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
      { label: "Still accessible", value: "100%" },
    ],
  },
  {
    id: 5,
    tag: "Your shelf, everywhere",
    headline: ["Your archive", "lives in the", "cloud. Safe.", "Always yours."],
    body: "Every archived app — its link, your notes, your categories — syncs to your account. Switch phones, reinstall Decluttr, and your entire shelf is waiting.",
    visual: "cloud",
    accent: "#60A5FA",
    disclaimer: "Decluttr saves your app archive and notes — not the app itself. Reinstalling opens the Play Store listing. Your in-app data (game saves, documents) is managed by each app's own backup.",
  },
];

const ClutterVisual = ({ accent }) => {
  const apps = [
    { name: "Docs", x: 12, y: 20, size: 44, opacity: 0.9 },
    { name: "VPN", x: 62, y: 8, size: 38, opacity: 0.7 },
    { name: "Fit", x: 30, y: 55, size: 50, opacity: 0.95 },
    { name: "Tax", x: 75, y: 50, size: 36, opacity: 0.6 },
    { name: "PDF", x: 5, y: 72, size: 42, opacity: 0.8 },
    { name: "Scan", x: 50, y: 75, size: 38, opacity: 0.75 },
    { name: "Trans", x: 80, y: 78, size: 34, opacity: 0.5 },
    { name: "Edit", x: 40, y: 30, size: 46, opacity: 0.85 },
  ];
  return (
    <div style={{ position: "relative", width: "100%", height: "100%", overflow: "hidden" }}>
      {apps.map((app, i) => (
        <div
          key={i}
          style={{
            position: "absolute",
            left: `${app.x}%`,
            top: `${app.y}%`,
            width: app.size,
            height: app.size,
            borderRadius: 12,
            background: `linear-gradient(135deg, rgba(255,255,255,0.08), rgba(255,255,255,0.03))`,
            border: `1px solid rgba(255,255,255,0.1)`,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: 9,
            color: `rgba(255,255,255,${app.opacity * 0.7})`,
            opacity: app.opacity,
            fontFamily: "'DM Sans', sans-serif",
            fontWeight: 600,
            letterSpacing: "0.02em",
            backdropFilter: "blur(4px)",
            animation: `float${i % 3} ${3 + (i % 3)}s ease-in-out infinite`,
            transform: `rotate(${(i % 5 - 2) * 3}deg)`,
          }}
        >
          {app.name}
        </div>
      ))}
      <div style={{
        position: "absolute", inset: 0,
        background: `radial-gradient(circle at 50% 50%, ${accent}15 0%, transparent 70%)`,
      }} />
    </div>
  );
};

const BookshelfVisual = ({ examples, accent }) => (
  <div style={{ display: "flex", flexDirection: "column", gap: 10, width: "100%" }}>
    {examples.map((name, i) => (
      <div
        key={i}
        style={{
          display: "flex",
          alignItems: "center",
          gap: 12,
          padding: "10px 14px",
          borderRadius: 12,
          background: "rgba(255,255,255,0.04)",
          border: "1px solid rgba(255,255,255,0.08)",
          animation: `slideIn 0.4s ease both`,
          animationDelay: `${i * 0.08}s`,
        }}
      >
        <div style={{
          width: 36, height: 36, borderRadius: 8,
          background: `linear-gradient(135deg, ${accent}30, ${accent}10)`,
          border: `1px solid ${accent}40`,
          flexShrink: 0,
        }} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: "rgba(255,255,255,0.9)", fontFamily: "'DM Sans', sans-serif" }}>{name}</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.35)", marginTop: 2, fontFamily: "'DM Sans', sans-serif" }}>Used twice in the last year</div>
        </div>
        <div style={{
          fontSize: 10, fontWeight: 700, color: accent,
          background: `${accent}15`, padding: "3px 8px", borderRadius: 20,
          fontFamily: "'DM Sans', sans-serif", letterSpacing: "0.05em",
        }}>KEEP</div>
      </div>
    ))}
  </div>
);

const ArchiveVisual = ({ accent }) => (
  <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 16, width: "100%" }}>
    <div style={{ display: "flex", alignItems: "center", gap: 0, width: "100%", justifyContent: "center" }}>
      {/* Phone side */}
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
        <div style={{
          width: 72, height: 120, borderRadius: 14, border: "2px solid rgba(255,255,255,0.15)",
          background: "rgba(255,255,255,0.04)", display: "flex", flexDirection: "column",
          padding: 8, gap: 4, position: "relative", overflow: "hidden"
        }}>
          <div style={{ fontSize: 8, color: "rgba(255,255,255,0.3)", textAlign: "center", fontFamily: "'DM Sans', sans-serif", marginBottom: 2 }}>PHONE</div>
          {[1, 2].map(i => (
            <div key={i} style={{ height: 22, borderRadius: 6, background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.05)" }} />
          ))}
          <div style={{ height: 22, borderRadius: 6, background: `${accent}20`, border: `1px solid ${accent}40`, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <span style={{ fontSize: 7, color: accent, fontFamily: "'DM Sans', sans-serif", fontWeight: 700 }}>PDF Merger</span>
          </div>
          <div style={{ height: 22, borderRadius: 6, background: "rgba(255,255,255,0.07)", border: "1px solid rgba(255,255,255,0.05)" }} />
          {/* X mark overlay */}
          <div style={{
            position: "absolute", top: 0, left: 0, right: 0, bottom: 0,
            background: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center",
            borderRadius: 12,
          }}>
            <div style={{ fontSize: 28, color: `${accent}cc` }}>↗</div>
          </div>
        </div>
        <span style={{ fontSize: 10, color: "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif" }}>Freed</span>
      </div>

      {/* Arrow */}
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4, padding: "0 16px" }}>
        <div style={{ width: 50, height: 2, background: `linear-gradient(to right, transparent, ${accent}, transparent)` }} />
        <span style={{ fontSize: 9, color: accent, fontFamily: "'DM Sans', sans-serif", fontWeight: 700, letterSpacing: "0.1em" }}>ARCHIVE</span>
      </div>

      {/* Shelf side */}
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
        <div style={{
          width: 72, height: 120, borderRadius: 14, border: `2px solid ${accent}40`,
          background: `${accent}08`, display: "flex", flexDirection: "column",
          padding: 8, gap: 4
        }}>
          <div style={{ fontSize: 8, color: `${accent}80`, textAlign: "center", fontFamily: "'DM Sans', sans-serif", marginBottom: 2 }}>YOUR SHELF</div>
          {["Tax Calc", "QR Scan", "PDF Merger", "VPN Pro"].map((name, i) => (
            <div key={i} style={{
              height: 20, borderRadius: 5,
              background: i === 2 ? `${accent}25` : "rgba(255,255,255,0.06)",
              border: `1px solid ${i === 2 ? accent + "50" : "rgba(255,255,255,0.05)"}`,
              display: "flex", alignItems: "center", paddingLeft: 5
            }}>
              <span style={{ fontSize: 6, color: i === 2 ? accent : "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif" }}>{name}</span>
            </div>
          ))}
        </div>
        <span style={{ fontSize: 10, color: `${accent}aa`, fontFamily: "'DM Sans', sans-serif" }}>Remembered</span>
      </div>
    </div>

    <div style={{
      width: "100%", padding: "10px 14px", borderRadius: 10,
      background: `${accent}10`, border: `1px solid ${accent}25`,
    }}>
      <p style={{ fontSize: 11, color: "rgba(255,255,255,0.6)", margin: 0, fontFamily: "'DM Sans', sans-serif", lineHeight: 1.6, textAlign: "center" }}>
        One tap reinstall via Play Store — whenever you need it back
      </p>
    </div>
  </div>
);

const NotesVisual = ({ accent, noteExample }) => (
  <div style={{ width: "100%", position: "relative" }}>
    <div style={{
      borderRadius: 16, border: `1px solid ${accent}30`,
      background: `linear-gradient(135deg, ${accent}08, rgba(255,255,255,0.02))`,
      padding: 16, position: "relative", overflow: "hidden"
    }}>
      {/* App header */}
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
        <div style={{
          width: 42, height: 42, borderRadius: 10,
          background: `linear-gradient(135deg, ${accent}40, ${accent}15)`,
          border: `1px solid ${accent}50`,
        }} />
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, color: "rgba(255,255,255,0.9)", fontFamily: "'DM Sans', sans-serif" }}>PDF Merger Pro</div>
          <div style={{ fontSize: 11, color: "rgba(255,255,255,0.35)", fontFamily: "'DM Sans', sans-serif" }}>Archived · 3 months ago</div>
        </div>
        <div style={{
          marginLeft: "auto", fontSize: 10, color: accent, fontWeight: 700,
          background: `${accent}15`, padding: "4px 10px", borderRadius: 20,
          border: `1px solid ${accent}30`, fontFamily: "'DM Sans', sans-serif"
        }}>Tools</div>
      </div>

      {/* Note card */}
      <div style={{
        background: "rgba(0,0,0,0.25)", borderRadius: 10, padding: "12px 14px",
        border: "1px solid rgba(255,255,255,0.07)", position: "relative"
      }}>
        <div style={{
          display: "flex", alignItems: "center", gap: 6, marginBottom: 8
        }}>
          <div style={{ width: 6, height: 6, borderRadius: "50%", background: accent }} />
          <span style={{ fontSize: 10, fontWeight: 700, color: `${accent}cc`, fontFamily: "'DM Sans', sans-serif", letterSpacing: "0.08em" }}>YOUR NOTE</span>
        </div>
        <p style={{
          fontSize: 12, color: "rgba(255,255,255,0.75)", margin: 0,
          fontFamily: "'Georgia', serif", lineHeight: 1.7,
          fontStyle: "italic"
        }}>
          "{noteExample}"
        </p>
        {/* Cursor blink */}
        <span style={{
          display: "inline-block", width: 2, height: 14, background: accent,
          marginLeft: 2, verticalAlign: "middle",
          animation: "blink 1s step-end infinite"
        }} />
      </div>
    </div>
  </div>
);

const DiscoverVisual = ({ accent, stats }) => {
  const items = [
    { name: "Currency Converter", days: 184, size: "24 MB" },
    { name: "QR Code Reader", days: 97, size: "18 MB" },
    { name: "Screen Recorder", days: 220, size: "61 MB" },
    { name: "Language Tutor", days: 145, size: "89 MB" },
  ];
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10, width: "100%" }}>
      <div style={{ display: "flex", gap: 8, marginBottom: 4 }}>
        {stats.map((s, i) => (
          <div key={i} style={{
            flex: 1, padding: "8px 6px", borderRadius: 10,
            background: "rgba(255,255,255,0.04)", border: "1px solid rgba(255,255,255,0.07)",
            textAlign: "center"
          }}>
            <div style={{ fontSize: 15, fontWeight: 800, color: accent, fontFamily: "'DM Sans', sans-serif" }}>{s.value}</div>
            <div style={{ fontSize: 9, color: "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>
      {items.map((item, i) => (
        <div key={i} style={{
          display: "flex", alignItems: "center", gap: 10, padding: "9px 12px",
          borderRadius: 10, background: "rgba(255,255,255,0.03)",
          border: "1px solid rgba(255,255,255,0.07)",
          animation: "slideIn 0.3s ease both",
          animationDelay: `${i * 0.07}s`,
        }}>
          <div style={{
            width: 32, height: 32, borderRadius: 8,
            background: `linear-gradient(135deg, ${accent}20, ${accent}08)`,
            border: `1px solid ${accent}30`, flexShrink: 0
          }} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: "rgba(255,255,255,0.85)", fontFamily: "'DM Sans', sans-serif", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{item.name}</div>
            <div style={{ fontSize: 10, color: "rgba(255,255,255,0.3)", fontFamily: "'DM Sans', sans-serif", marginTop: 1 }}>Last used {item.days} days ago · {item.size}</div>
          </div>
          <div style={{ display: "flex", gap: 5 }}>
            <div style={{
              width: 26, height: 26, borderRadius: 6,
              background: `${accent}20`, border: `1px solid ${accent}35`,
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 12, cursor: "pointer"
            }}>📦</div>
            <div style={{
              width: 26, height: 26, borderRadius: 6,
              background: "rgba(255,80,80,0.1)", border: "1px solid rgba(255,80,80,0.2)",
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 12, cursor: "pointer"
            }}>🗑</div>
          </div>
        </div>
      ))}
    </div>
  );
};

const CloudVisual = ({ accent, disclaimer }) => (
  <div style={{ display: "flex", flexDirection: "column", gap: 12, width: "100%" }}>
    {/* Sync illustration */}
    <div style={{
      borderRadius: 14, border: `1px solid ${accent}25`,
      background: `${accent}06`, padding: "14px 16px",
    }}>
      <div style={{ display: "flex", justifyContent: "space-around", alignItems: "center", marginBottom: 10 }}>
        {["📱 Phone A", "☁️ Cloud", "📱 Phone B"].map((label, i) => (
          <div key={i} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
            <div style={{
              width: 40, height: 40, borderRadius: 10,
              background: i === 1 ? `${accent}25` : "rgba(255,255,255,0.06)",
              border: `1px solid ${i === 1 ? accent + "50" : "rgba(255,255,255,0.1)"}`,
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 18
            }}>{label.split(" ")[0]}</div>
            <span style={{ fontSize: 9, color: "rgba(255,255,255,0.4)", fontFamily: "'DM Sans', sans-serif" }}>{label.split(" ")[1]}</span>
          </div>
        ))}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
        {["Archive", "Notes", "Categories", "Tags"].map((item, i) => (
          <div key={i} style={{
            flex: 1, padding: "4px 0", borderRadius: 6, textAlign: "center",
            background: `${accent}15`, border: `1px solid ${accent}25`,
            fontSize: 9, color: `${accent}cc`, fontFamily: "'DM Sans', sans-serif", fontWeight: 600
          }}>{item}</div>
        ))}
      </div>
    </div>

    {/* Honest disclaimer */}
    <div style={{
      borderRadius: 12, border: "1px solid rgba(255,255,255,0.08)",
      background: "rgba(255,255,255,0.03)", padding: "12px 14px",
    }}>
      <div style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
        <div style={{ fontSize: 14, flexShrink: 0, marginTop: 1 }}>💡</div>
        <div>
          <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", fontFamily: "'DM Sans', sans-serif", marginBottom: 4, letterSpacing: "0.04em" }}>GOOD TO KNOW</div>
          <p style={{ fontSize: 11, color: "rgba(255,255,255,0.45)", margin: 0, fontFamily: "'DM Sans', sans-serif", lineHeight: 1.6 }}>
            {disclaimer}
          </p>
        </div>
      </div>
    </div>
  </div>
);

const visuals = {
  clutter: ClutterVisual,
  bookshelf: BookshelfVisual,
  archive: ArchiveVisual,
  notes: NotesVisual,
  discover: DiscoverVisual,
  cloud: CloudVisual,
};

export default function DecluttrOnboarding() {
  const [current, setCurrent] = useState(0);
  const [animDir, setAnimDir] = useState(1);
  const [isAnimating, setIsAnimating] = useState(false);
  const [visible, setVisible] = useState(true);
  const touchStartX = useRef(null);

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

  const onTouchStart = (e) => { touchStartX.current = e.touches[0].clientX; };
  const onTouchEnd = (e) => {
    if (!touchStartX.current) return;
    const diff = touchStartX.current - e.changedTouches[0].clientX;
    if (Math.abs(diff) > 40) diff > 0 ? next() : prev();
    touchStartX.current = null;
  };

  const slide = slides[current];
  const Visual = visuals[slide.visual];

  return (
    <>
      <style>{`
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
        .sign-in-btn:hover { background: rgba(255,255,255,0.06) !important; }
        .get-started-btn:hover { filter: brightness(1.1); transform: translateY(-1px); box-shadow: 0 8px 24px rgba(0,0,0,0.4); }
        .get-started-btn { transition: all 0.2s ease; }
      `}</style>

      <div
        style={{
          width: "100%", maxWidth: 390, minHeight: "100vh",
          margin: "0 auto", background: "#0a0b0f",
          display: "flex", flexDirection: "column",
          position: "relative", overflow: "hidden", userSelect: "none",
        }}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        {/* Ambient background glow */}
        <div style={{
          position: "absolute", inset: 0, pointerEvents: "none",
          background: `radial-gradient(ellipse 80% 50% at 50% 0%, ${slide.accent}12 0%, transparent 60%)`,
          transition: "background 0.6s ease",
        }} />

        {/* Top bar */}
        <div style={{
          display: "flex", justifyContent: "space-between", alignItems: "center",
          padding: "52px 28px 0",
          position: "relative", zIndex: 10,
        }}>
          <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
            <div style={{
              width: 28, height: 28, borderRadius: 8,
              background: `linear-gradient(135deg, ${slide.accent}, ${slide.accent}80)`,
              display: "flex", alignItems: "center", justifyContent: "center",
              transition: "background 0.4s ease",
            }}>
              <span style={{ fontSize: 14 }}>📦</span>
            </div>
            <span style={{ fontFamily: "'DM Serif Display', serif", fontSize: 16, color: "rgba(255,255,255,0.9)", letterSpacing: "-0.01em" }}>
              Decluttr
            </span>
          </div>
          {current < slides.length - 1 && (
            <button
              onClick={() => goTo(slides.length - 1, 1)}
              style={{
                background: "none", border: "none", cursor: "pointer",
                fontSize: 12, color: "rgba(255,255,255,0.35)",
                fontFamily: "'DM Sans', sans-serif", fontWeight: 600,
                letterSpacing: "0.06em", padding: "4px 0",
              }}
            >
              SKIP
            </button>
          )}
        </div>

        {/* Progress bar */}
        <div style={{ padding: "16px 28px 0", position: "relative", zIndex: 10 }}>
          <div style={{ display: "flex", gap: 5 }}>
            {slides.map((_, i) => (
              <div
                key={i}
                onClick={() => goTo(i, i > current ? 1 : -1)}
                style={{
                  flex: 1, height: 2, borderRadius: 2,
                  background: i <= current ? slide.accent : "rgba(255,255,255,0.1)",
                  cursor: "pointer",
                  transition: "background 0.4s ease",
                  overflow: "hidden",
                }}
              >
                {i === current && (
                  <div style={{
                    height: "100%", background: slide.accent, borderRadius: 2,
                    animation: "progressFill 6s linear forwards",
                  }} />
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Main content */}
        <div
          key={current}
          className="slide-enter"
          style={{
            flex: 1, display: "flex", flexDirection: "column",
            padding: "28px 28px 0", position: "relative", zIndex: 10,
          }}
        >
          {/* Tag */}
          <div style={{
            display: "inline-flex", alignItems: "center", gap: 6, marginBottom: 18,
            alignSelf: "flex-start",
          }}>
            <div style={{ width: 4, height: 4, borderRadius: "50%", background: slide.accent }} />
            <span style={{
              fontSize: 11, fontWeight: 700, color: `${slide.accent}cc`,
              fontFamily: "'DM Sans', sans-serif", letterSpacing: "0.12em",
              textTransform: "uppercase",
            }}>{slide.tag}</span>
          </div>

          {/* Headline */}
          <div style={{ marginBottom: 20 }}>
            {slide.headline.map((line, i) => (
              <div
                key={i}
                style={{
                  fontFamily: "'DM Serif Display', serif",
                  fontSize: i % 2 === 1 ? 34 : 28,
                  lineHeight: 1.15,
                  color: i % 2 === 1 ? "rgba(255,255,255,0.98)" : "rgba(255,255,255,0.45)",
                  letterSpacing: "-0.02em",
                  animation: `fadeSlideIn 0.5s cubic-bezier(0.22,1,0.36,1) both`,
                  animationDelay: `${i * 0.07}s`,
                }}
              >
                {line}
              </div>
            ))}
          </div>

          {/* Body text */}
          <p style={{
            fontSize: 14, color: "rgba(255,255,255,0.5)",
            fontFamily: "'DM Sans', sans-serif", lineHeight: 1.7,
            marginBottom: 24,
            animation: "fadeSlideIn 0.5s ease both",
            animationDelay: "0.25s",
          }}>
            {slide.body}
          </p>

          {/* Visual area */}
          <div style={{
            flex: 1, minHeight: 180, maxHeight: 280,
            animation: "fadeSlideIn 0.5s ease both",
            animationDelay: "0.3s",
          }}>
            <Visual {...slide} />
          </div>
        </div>

        {/* Bottom section */}
        <div style={{
          padding: "20px 28px 40px",
          position: "relative", zIndex: 10,
          background: "linear-gradient(to top, #0a0b0f 70%, transparent)",
        }}>
          {current < slides.length - 1 ? (
            <>
              <button
                onClick={next}
                className="next-btn"
                style={{
                  width: "100%", height: 52, borderRadius: 14,
                  background: slide.accent,
                  border: "none", cursor: "pointer",
                  fontSize: 15, fontWeight: 700, color: "#0a0b0f",
                  fontFamily: "'DM Sans', sans-serif", letterSpacing: "-0.01em",
                  transition: "background 0.3s ease",
                  marginBottom: 12,
                }}
              >
                Continue →
              </button>

              {/* Dot navigation */}
              <div style={{ display: "flex", justifyContent: "center", gap: 6 }}>
                {slides.map((_, i) => (
                  <div
                    key={i}
                    className="dot"
                    onClick={() => goTo(i, i > current ? 1 : -1)}
                    style={{
                      width: i === current ? 20 : 6, height: 6, borderRadius: 3,
                      background: i === current ? slide.accent : "rgba(255,255,255,0.15)",
                      cursor: "pointer", transition: "all 0.3s ease",
                    }}
                  />
                ))}
              </div>
            </>
          ) : (
            /* Last slide — sign in */
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <button
                className="get-started-btn"
                style={{
                  width: "100%", height: 54, borderRadius: 14,
                  background: `linear-gradient(135deg, ${slide.accent}, ${slide.accent}cc)`,
                  border: "none", cursor: "pointer",
                  display: "flex", alignItems: "center", justifyContent: "center",
                  gap: 10,
                }}
              >
                <svg width="20" height="20" viewBox="0 0 24 24">
                  <path fill="white" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                  <path fill="white" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                  <path fill="white" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                  <path fill="white" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                </svg>
                <span style={{
                  fontSize: 15, fontWeight: 700, color: "#0a0b0f",
                  fontFamily: "'DM Sans', sans-serif",
                }}>
                  Continue with Google
                </span>
              </button>

              <button
                className="sign-in-btn"
                style={{
                  width: "100%", height: 48, borderRadius: 14,
                  background: "rgba(255,255,255,0.04)",
                  border: "1px solid rgba(255,255,255,0.1)",
                  cursor: "pointer", fontSize: 14, fontWeight: 600,
                  color: "rgba(255,255,255,0.6)",
                  fontFamily: "'DM Sans', sans-serif",
                  transition: "background 0.2s ease",
                }}
              >
                Sign in with Email
              </button>

              <p style={{
                fontSize: 11, color: "rgba(255,255,255,0.2)",
                fontFamily: "'DM Sans', sans-serif", textAlign: "center",
                lineHeight: 1.5, marginTop: 4,
              }}>
                By continuing you agree to our Terms & Privacy Policy
              </p>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
