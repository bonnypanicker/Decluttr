import { useState } from "react";

const ACCENT = "#60A5FA";
const WISH  = "#F472B6";
const TOTAL_SLIDES = 6;
const THIS_SLIDE   = 5;

const slide = {
  tag: "Your shelf, everywhere",
  headline: ["Your archive", "lives in the", "cloud. Safe.", "Always yours."],
  body: "Every archived app — its link, your notes, your categories — syncs to your account. Switch phones, reinstall Decluttr, and your entire shelf is waiting.",
  accent: ACCENT,
  disclaimer:
    "Decluttr saves your app archive and notes — not the app itself. Reinstalling opens the Play Store listing. Your in-app data (game saves, documents) is managed by each app's own backup.",
};

const CloudVisual = ({ accent, disclaimer }) => (
  <div style={{ display:"flex", flexDirection:"column", gap:10, width:"100%" }}>

    {/* ── Sync block ── */}
    <div style={{
      borderRadius:14, border:`1px solid ${accent}25`,
      background:`${accent}06`, padding:"14px 16px",
    }}>
      <div style={{ display:"flex", justifyContent:"space-around", alignItems:"center", marginBottom:10 }}>
        {["📱 Phone A","☁️ Cloud","📱 Phone B"].map((label,i)=>(
          <div key={i} style={{ display:"flex", flexDirection:"column", alignItems:"center", gap:4 }}>
            <div style={{
              width:40, height:40, borderRadius:10,
              background: i===1 ? `${accent}25` : "rgba(255,255,255,0.06)",
              border:`1px solid ${i===1 ? accent+"50" : "rgba(255,255,255,0.1)"}`,
              display:"flex", alignItems:"center", justifyContent:"center", fontSize:18,
            }}>{label.split(" ")[0]}</div>
            <span style={{ fontSize:9, color:"rgba(255,255,255,0.4)", fontFamily:"'DM Sans',sans-serif" }}>
              {label.split(" ")[1]}
            </span>
          </div>
        ))}
      </div>
      <div style={{ display:"flex", alignItems:"center", gap:4 }}>
        {["Archive","Notes","Categories","Tags"].map((item,i)=>(
          <div key={i} style={{
            flex:1, padding:"4px 0", borderRadius:6, textAlign:"center",
            background:`${accent}15`, border:`1px solid ${accent}25`,
            fontSize:9, color:`${accent}cc`,
            fontFamily:"'DM Sans',sans-serif", fontWeight:600,
          }}>{item}</div>
        ))}
      </div>
    </div>

    {/* ── Wishlist addon card ── */}
    <div style={{
      borderRadius:12,
      border:`1px solid ${WISH}22`,
      background:`${WISH}06`,
      padding:"11px 13px",
      display:"flex", flexDirection:"column", gap:9,
    }}>
      {/* Header */}
      <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between" }}>
        <div style={{ display:"flex", alignItems:"center", gap:6 }}>
          <div style={{ width:4, height:4, borderRadius:"50%", background:WISH, opacity:0.7 }} />
          <span style={{
            fontSize:9, fontWeight:700, color:`${WISH}99`,
            fontFamily:"'DM Sans',sans-serif", letterSpacing:"0.1em", textTransform:"uppercase",
          }}>Also included · Wishlist</span>
        </div>
        <span style={{
          fontSize:8, fontWeight:600, color:`${WISH}55`,
          fontFamily:"'DM Sans',sans-serif", letterSpacing:"0.06em",
        }}>ADD-ON</span>
      </div>

      {/* Row 1 — how to add */}
      <div style={{
        display:"flex", alignItems:"center", gap:9,
        padding:"8px 10px", borderRadius:9,
        background:"rgba(255,255,255,0.03)", border:"1px solid rgba(255,255,255,0.06)",
      }}>
        <div style={{ display:"flex", alignItems:"center", gap:5, flexShrink:0 }}>
          <div style={{
            width:28, height:28, borderRadius:7,
            background:"rgba(255,255,255,0.06)", border:"1px solid rgba(255,255,255,0.1)",
            display:"flex", alignItems:"center", justifyContent:"center", fontSize:13,
          }}>▶</div>
          <div style={{ display:"flex", flexDirection:"column", alignItems:"center", gap:1 }}>
            <div style={{ width:14, height:1.5, background:`linear-gradient(to right,${WISH}30,${WISH}65)` }} />
            <span style={{ fontSize:7, color:`${WISH}55`, fontFamily:"'DM Sans',sans-serif", letterSpacing:"0.06em", textTransform:"uppercase" }}>share</span>
          </div>
          <div style={{
            width:28, height:28, borderRadius:7,
            background:`${WISH}15`, border:`1px solid ${WISH}30`,
            display:"flex", alignItems:"center", justifyContent:"center", fontSize:13,
          }}>📦</div>
        </div>
        <div style={{ flex:1 }}>
          <div style={{ fontSize:11, fontWeight:600, color:"rgba(255,255,255,0.75)", fontFamily:"'DM Sans',sans-serif" }}>
            Share any Play Store page
          </div>
          <div style={{ fontSize:10, color:"rgba(255,255,255,0.3)", fontFamily:"'DM Sans',sans-serif", marginTop:1 }}>
            It lands straight in your Wishlist
          </div>
        </div>
      </div>

      {/* Row 2 — where it lives */}
      <div style={{
        display:"flex", alignItems:"center", gap:9,
        padding:"8px 10px", borderRadius:9,
        background:"rgba(255,255,255,0.03)", border:"1px solid rgba(255,255,255,0.06)",
      }}>
        <div style={{
          width:28, height:28, borderRadius:7, flexShrink:0,
          background:`${WISH}12`, border:`1px solid ${WISH}25`,
          display:"flex", alignItems:"center", justifyContent:"center",
        }}>
          <svg width="13" height="14" viewBox="0 0 13 14" fill="none">
            <path d="M2 1.5h9a.5.5 0 0 1 .5.5v10.3a.3.3 0 0 1-.48.24L6.5 9.5l-4.52 3.04A.3.3 0 0 1 1.5 12.3V2a.5.5 0 0 1 .5-.5Z"
              stroke={WISH} strokeOpacity="0.7" strokeWidth="1.1" fill="none"/>
          </svg>
        </div>
        <div style={{ flex:1 }}>
          <div style={{ fontSize:11, fontWeight:600, color:"rgba(255,255,255,0.75)", fontFamily:"'DM Sans',sans-serif" }}>
            Dedicated Wishlist tab in the app
          </div>
          <div style={{ fontSize:10, color:"rgba(255,255,255,0.3)", fontFamily:"'DM Sans',sans-serif", marginTop:1 }}>
            Browse saved apps, add notes, open in Play Store
          </div>
        </div>
      </div>
    </div>

    {/* ── Disclaimer ── */}
    <div style={{
      borderRadius:12, border:"1px solid rgba(255,255,255,0.08)",
      background:"rgba(255,255,255,0.03)", padding:"12px 14px",
    }}>
      <div style={{ display:"flex", gap:8, alignItems:"flex-start" }}>
        <div style={{ fontSize:14, flexShrink:0, marginTop:1 }}>💡</div>
        <div>
          <div style={{
            fontSize:11, fontWeight:700, color:"rgba(255,255,255,0.6)",
            fontFamily:"'DM Sans',sans-serif", marginBottom:4, letterSpacing:"0.04em",
          }}>GOOD TO KNOW</div>
          <p style={{
            fontSize:11, color:"rgba(255,255,255,0.45)", margin:0,
            fontFamily:"'DM Sans',sans-serif", lineHeight:1.6,
          }}>{disclaimer}</p>
        </div>
      </div>
    </div>
  </div>
);

export default function LastPanel() {
  return (
    <>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=DM+Sans:wght@400;500;600;700;800&display=swap');
        *{box-sizing:border-box;margin:0;padding:0;}
        body{background:#050507;display:flex;justify-content:center;}
        @keyframes fadeSlideIn{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:translateY(0)}}
        @keyframes progressFill{from{width:0}to{width:100%}}
        .get-started-btn:hover{filter:brightness(1.1);transform:translateY(-1px);box-shadow:0 8px 24px rgba(0,0,0,0.4);}
        .get-started-btn{transition:all 0.2s ease;}
        .sign-in-btn:hover{background:rgba(255,255,255,0.06)!important;}
      `}</style>

      <div style={{
        width:"100%", maxWidth:390, minHeight:"100vh",
        background:"#0a0b0f", display:"flex", flexDirection:"column",
        position:"relative", overflow:"hidden", userSelect:"none",
      }}>
        <div style={{
          position:"absolute", inset:0, pointerEvents:"none",
          background:`radial-gradient(ellipse 80% 50% at 50% 0%, ${ACCENT}12 0%, transparent 60%)`,
        }} />

        <div style={{
          display:"flex", justifyContent:"space-between", alignItems:"center",
          padding:"52px 28px 0", position:"relative", zIndex:10,
        }}>
          <div style={{ display:"flex", alignItems:"center", gap:7 }}>
            <div style={{
              width:28, height:28, borderRadius:8,
              background:`linear-gradient(135deg,${ACCENT},${ACCENT}80)`,
              display:"flex", alignItems:"center", justifyContent:"center",
            }}>
              <span style={{ fontSize:14 }}>📦</span>
            </div>
            <span style={{
              fontFamily:"'DM Serif Display',serif", fontSize:16,
              color:"rgba(255,255,255,0.9)", letterSpacing:"-0.01em",
            }}>Decluttr</span>
          </div>
        </div>

        <div style={{ padding:"16px 28px 0", position:"relative", zIndex:10 }}>
          <div style={{ display:"flex", gap:5 }}>
            {Array.from({ length:TOTAL_SLIDES }).map((_,i)=>(
              <div key={i} style={{ flex:1, height:2, borderRadius:2, background:ACCENT, overflow:"hidden" }}>
                {i===THIS_SLIDE && (
                  <div style={{ height:"100%", background:ACCENT, borderRadius:2, animation:"progressFill 6s linear forwards" }} />
                )}
              </div>
            ))}
          </div>
        </div>

        <div style={{
          flex:1, display:"flex", flexDirection:"column",
          padding:"28px 28px 0", position:"relative", zIndex:10,
          animation:"fadeSlideIn 0.4s cubic-bezier(0.22,1,0.36,1) both",
        }}>
          <div style={{ display:"inline-flex", alignItems:"center", gap:6, marginBottom:18, alignSelf:"flex-start" }}>
            <div style={{ width:4, height:4, borderRadius:"50%", background:ACCENT }} />
            <span style={{
              fontSize:11, fontWeight:700, color:`${ACCENT}cc`,
              fontFamily:"'DM Sans',sans-serif", letterSpacing:"0.12em", textTransform:"uppercase",
            }}>{slide.tag}</span>
          </div>

          <div style={{ marginBottom:20 }}>
            {slide.headline.map((line,i)=>(
              <div key={i} style={{
                fontFamily:"'DM Serif Display',serif",
                fontSize:i%2===1 ? 34 : 28, lineHeight:1.15,
                color:i%2===1 ? "rgba(255,255,255,0.98)" : "rgba(255,255,255,0.45)",
                letterSpacing:"-0.02em",
                animation:"fadeSlideIn 0.5s cubic-bezier(0.22,1,0.36,1) both",
                animationDelay:`${i*0.07}s`,
              }}>{line}</div>
            ))}
          </div>

          <p style={{
            fontSize:14, color:"rgba(255,255,255,0.5)",
            fontFamily:"'DM Sans',sans-serif", lineHeight:1.7, marginBottom:24,
            animation:"fadeSlideIn 0.5s ease both", animationDelay:"0.25s",
          }}>{slide.body}</p>

          <div style={{ flex:1, minHeight:180, animation:"fadeSlideIn 0.5s ease both", animationDelay:"0.3s" }}>
            <CloudVisual accent={ACCENT} disclaimer={slide.disclaimer} />
          </div>
        </div>

        <div style={{
          padding:"20px 28px 40px", position:"relative", zIndex:10,
          background:"linear-gradient(to top,#0a0b0f 70%,transparent)",
        }}>
          <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
            <button className="get-started-btn" style={{
              width:"100%", height:54, borderRadius:14,
              background:`linear-gradient(135deg,${ACCENT},${ACCENT}cc)`,
              border:"none", cursor:"pointer",
              display:"flex", alignItems:"center", justifyContent:"center", gap:10,
            }}>
              <svg width="20" height="20" viewBox="0 0 24 24">
                <path fill="white" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="white" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="white" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                <path fill="white" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
              </svg>
              <span style={{ fontSize:15, fontWeight:700, color:"#0a0b0f", fontFamily:"'DM Sans',sans-serif" }}>
                Continue with Google
              </span>
            </button>

            <button className="sign-in-btn" style={{
              width:"100%", height:48, borderRadius:14,
              background:"rgba(255,255,255,0.04)", border:"1px solid rgba(255,255,255,0.1)",
              cursor:"pointer", fontSize:14, fontWeight:600, color:"rgba(255,255,255,0.6)",
              fontFamily:"'DM Sans',sans-serif", transition:"background 0.2s ease",
            }}>Sign in with Email</button>

            <p style={{
              fontSize:11, color:"rgba(255,255,255,0.2)",
              fontFamily:"'DM Sans',sans-serif", textAlign:"center", lineHeight:1.5, marginTop:4,
            }}>By continuing you agree to our Terms &amp; Privacy Policy</p>
          </div>
        </div>
      </div>
    </>
  );
}
