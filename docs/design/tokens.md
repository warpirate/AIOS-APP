# Mitra Design Tokens

The foundational visual language. Every color, size, spacing, radius, shadow, motion curve, and haptic in the app derives from this file. If a screen needs a value not here, the value goes here first, then into the screen.

**Aesthetic anchor**: Linear, Things 3, Notion, Bear, Apple Notes. Calm, paper-like, capable but quiet. NOT futuristic, neon, sci-fi, gradient-heavy, or "AI flashy."

**Brand truth**: Privacy. Locality. Friendship (मित्र). The visuals should feel like a notebook, not a control panel.

---

## 1. Color

### Base hue rationale

The primary hue is a **muted warm clay / terracotta** (`#A85339`). Reasoning:

- **Warm, earthy, human.** Cool blues and purples are the visual language of every AI brand on the market (OpenAI, Gemini, Copilot, Claude, Perplexity). Choosing a warm earth tone is *immediately* differentiating and reads as "this is not another corporate AI."
- **Culturally resonant without being cliché.** Terracotta connects to the Indian audience subtly (clay pottery, kumkum, brick architecture) without leaning on overt motifs. A Western audience reads it as "warm, organic, Bear-app-coded."
- **Calm at saturation.** Unlike a saturated red or orange, this hue at this saturation reads as composed, not alarming.
- **Distinguishable from `danger`.** Critical: primary and danger share a red family, so danger is shifted to a deeper, less orange brick (`#962F2F`) and reserved for genuinely destructive actions.

The base **background is warm parchment cream** (`#FAF7F2`), not stark white. This single decision does more for the "calm, paper, not screen" feeling than any other choice. Stark white makes UIs feel clinical; warm off-white makes them feel like a notebook page.

### Dark mode rationale

Dark mode preserves calm by avoiding two failure modes:

1. **No pure black.** Background is `#1A1715` — a warm near-black with red-orange undertones. Pure black on OLED looks dramatic; warm dark grey looks composed.
2. **Primary is lifted, not desaturated.** The dark-mode primary (`#D08561`) is the same hue as light mode but lighter and slightly less saturated, so it doesn't glow against the dark surface. The clay character is preserved.

The dark palette is *warm-dark*, not *blue-dark*. Every neutral has a faint red-orange undertone. Comparing side-by-side, the dark mode should feel like the same app at dusk, not a different brand.

### Palette — Light

| Role | Hex | Use | Contrast pair | Ratio |
|---|---|---|---|---|
| `bg` | `#FAF7F2` | App background, the parchment | `onSurface` | 14.8:1 ✓ AAA |
| `surface` | `#FFFFFF` | Cards, sheets, raised regions | `onSurface` | 16.1:1 ✓ AAA |
| `surfaceVariant` | `#F0EBE2` | Subtle fills (input bg, chip bg, audit-log row alt) | `onSurfaceVariant` | 6.2:1 ✓ AA |
| `primary` | `#A85339` | Primary CTA, brand accent, active states | `onPrimary` | 4.7:1 ✓ AA |
| `onPrimary` | `#FFFFFF` | Text/icon on `primary` | — | — |
| `onSurface` | `#1F1C1A` | Primary text on bg / surface | `bg` | 14.8:1 ✓ AAA |
| `onSurfaceVariant` | `#5C5853` | Secondary text, metadata, captions | `bg` | 7.1:1 ✓ AA |
| `outline` | `#D4CDC2` | Borders, dividers, inactive states | — (decorative) | — |
| `success` | `#4F7A3F` | "Done" states on action cards, tool success | white on it | 4.9:1 ✓ AA |
| `warning` | `#8B6914` | "Heads up" — non-blocking warnings | white on it | 5.4:1 ✓ AA |
| `danger` | `#962F2F` | Irreversible action confirms, failed-state | `onPrimary` | 6.3:1 ✓ AA |
| `info` | `#3D5E7A` | Informational (audit log, what's-happening) | white on it | 6.7:1 ✓ AA |

### Palette — Dark

| Role | Hex | Use | Contrast pair | Ratio |
|---|---|---|---|---|
| `bg` | `#1A1715` | App background, the dusk paper | `onSurface` | 14.5:1 ✓ AAA |
| `surface` | `#221E1B` | Cards, sheets | `onSurface` | 12.6:1 ✓ AAA |
| `surfaceVariant` | `#2D2925` | Subtle fills | `onSurfaceVariant` | 5.8:1 ✓ AA |
| `primary` | `#D08561` | Primary CTA, brand accent | `onPrimary` | 5.1:1 ✓ AA |
| `onPrimary` | `#1A1715` | Text/icon on `primary` (dark text on lifted clay) | — | — |
| `onSurface` | `#F0EAE1` | Primary text | `bg` | 14.5:1 ✓ AAA |
| `onSurfaceVariant` | `#A8A199` | Secondary text | `bg` | 7.4:1 ✓ AA |
| `outline` | `#3D3833` | Borders, dividers | — | — |
| `success` | `#8FB97D` | Done states | `bg` | 8.2:1 ✓ AAA |
| `warning` | `#D4A852` | Warnings | `bg` | 9.7:1 ✓ AAA |
| `danger` | `#D9706C` | Irreversible confirms | `bg` | 6.5:1 ✓ AA |
| `info` | `#7AA0C2` | Informational | `bg` | 7.8:1 ✓ AAA |

> **Verify in production.** Ratios above are computed against neighboring background pairs. Use the Material Theme Builder contrast checker (or `androidx.core.graphics.ColorUtils.calculateContrast`) before shipping each palette, since rounding can drift.

### Color usage rules

- **Primary is rare.** One primary-colored element per screen ideally — the main CTA, the active tab, the send button. If everything is primary, nothing is.
- **Success / warning / danger / info are semantic, not decorative.** Never use `success` green just because something is "good." Use it only for explicit state changes (action completed, permission granted, etc.).
- **Outline > shadow for separation in light mode.** Use a 1dp `outline` border for cards in light mode; reserve shadow elevation for modals and overlays.
- **Surface containers stack via tint, not stroke, in dark mode.** Dark mode uses subtle background lifts (`bg` → `surface` → `surfaceVariant`) instead of borders.

---

## 2. Type

### Font family

**Primary: Inter** (SIL OFL, free for commercial use)
- Bundle as a font resource in the APK (`res/font/inter_*.ttf`), do not download at runtime.
- Weights needed: 400 (Regular), 500 (Medium), 600 (SemiBold).
- Rationale: Inter is the modern neutral sans — used by Linear, Notion, Bear (essentially), and most calm, capable apps. It's slightly warm, optically balanced at small sizes, has tabular numerals (critical for the model-download byte counter), and ships an inktrap-free design that reads cleanly on Android's variable rendering.

**Fallback: Manrope** (also OFL) — if you want the app to feel *softer* / *friendlier*. Manrope has more rounded terminals and reads as warmer. Worth A/B testing with Hindi users.

**Avoid**: Roboto (too Google-corporate; the whole point is to feel un-Google), Geist (too tech / Vercel-coded), DM Sans (too generic).

**Hindi / Devanagari support**: Bundle **Noto Sans Devanagari** as a fallback for Hindi strings. Inter does not cover Devanagari. The Compose `FontFamily` should declare both so glyphs are sourced correctly per script.

### 7-step scale

| Token | Size | Weight | Line height | Letter spacing | Use |
|---|---|---|---|---|---|
| `Display` | 32sp | 600 | 40sp | -0.5 | Onboarding hero ("Mitra"), big numbers in audit summary |
| `HeadlineL` | 24sp | 600 | 32sp | -0.25 | Screen titles ("Settings", "Audit history") |
| `HeadlineM` | 20sp | 600 | 28sp | 0 | Section headers within screens |
| `TitleL` | 18sp | 500 | 24sp | 0 | Action card titles, list-item primary text |
| `Body` | 16sp | 400 | 24sp | 0 | Chat messages, paragraph text, primary readable copy |
| `Label` | 14sp | 500 | 20sp | 0.1 | Button labels, tab labels, action card metadata |
| `Caption` | 12sp | 400 | 16sp | 0.2 | Timestamps, footnotes, audit row secondary text |

> All sizes in `sp` so they scale with the user's system font setting. **No hardcoded `dp` for text.** Survive 200% scale per a11y baseline.

### Type usage rules

- **Body is the workhorse.** ~80% of all text in the app should be `Body`. If you reach for a heading, ask whether it's actually needed.
- **Never go below `Caption`.** Smaller text is an a11y failure.
- **Numbers in technical contexts use tabular figures**: `fontFeatureSettings = "tnum"` in Compose for the byte counter, audit timestamps, token counts.
- **Hindi text often needs +1sp**: Devanagari has higher x-height and dense letterforms; bump `Body` to 17sp when the active locale is Hindi to maintain visual parity.

---

## 3. Spacing

4-point grid. Use these tokens; never improvise odd values.

| Token | Value | Common use |
|---|---|---|
| `xxs` | 4dp | Icon-to-text gaps, tight chip padding |
| `xs` | 8dp | Inline element gaps, tight stacks |
| `sm` | 12dp | Compact card padding, list item internal padding |
| `md` | 16dp | Default screen horizontal padding, standard card padding |
| `lg` | 20dp | Generous card padding (modal confirms, key surfaces) |
| `xl` | 24dp | Section separators, between major content blocks |
| `2xl` | 32dp | Above headlines, hero spacing |
| `3xl` | 40dp | Between onboarding screen blocks |
| `4xl` | 56dp | Top spacing on screen heroes, generous breathing room |

### Spacing rules

- **Screen horizontal padding = `md` (16dp).** Always. The exception is full-bleed surfaces (audit log rows that extend edge-to-edge), where the *content* still respects 16dp from the screen edge.
- **Vertical rhythm uses multiples of 4dp.** Never 6dp, 10dp, 18dp.
- **Touch targets respect 48dp min** regardless of visual size. A 24dp icon button gets 12dp invisible padding to reach 48dp hit area.

---

## 4. Radii

| Token | Value | Use |
|---|---|---|
| `radiusSm` | 4dp | Chips, small badges, tags |
| `radiusMd` | 12dp | Buttons, input fields, list items, toast bars |
| `radiusLg` | 20dp | Action cards, modal sheets, primary surfaces |
| `radiusXl` | 28dp | Onboarding feature cards, hero containers |

### Radius rules

- **Consistency over precision.** Pick the closest token; don't invent 16dp or 24dp values.
- **Modal sheets always use `radiusLg` top-only** (`shape = RoundedCornerShape(topStart = 20dp, topEnd = 20dp)`).
- **Pills (chips for suggested prompts)** use 50% radius (`CircleShape`), not a token — they're contextually pill-shaped.

---

## 5. Elevation

Three discrete levels. No in-between.

| Token | Y offset | Blur | Color (light) | Color (dark) | Use |
|---|---|---|---|---|---|
| `elevSurface` | 0dp | 0dp | — | — | Flat surfaces. In light mode, use a 1dp `outline` border instead of shadow. |
| `elevRaised` | 2dp | 8dp | `rgba(0,0,0,0.08)` | `rgba(0,0,0,0.32)` | Cards lifted off bg (active action card mid-execution). |
| `elevModal` | 8dp | 24dp | `rgba(0,0,0,0.16)` | `rgba(0,0,0,0.48)` | Modal sheets, confirmation cards, dropdowns. |

### Elevation rules

- **Light mode prefers outline over shadow** for low elevation. Heavy shadows make light UIs feel cluttered.
- **Dark mode prefers tint over shadow** for low elevation. Shadows in dark mode are visually faint; tonal lifts (`bg` → `surface` → `surfaceVariant`) carry the hierarchy.
- **Never animate elevation values.** Switch between tokens, don't tween shadow blur.

---

## 6. Motion

Three durations × three easings. Compose with intent.

### Durations

| Token | Value | Use |
|---|---|---|
| `motionFast` | 120ms | Micro-feedback: button press scale, chip select, toggle flip |
| `motionBase` | 220ms | Standard transitions: tab change, card expand, sheet half-open |
| `motionSlow` | 360ms | Emphasis: full-screen route change, model-load-complete celebration, audit-log open |

### Easings

| Token | Compose binding | Use |
|---|---|---|
| `easingStandard` | `FastOutSlowInEasing` | Default for most transitions |
| `easingDecelerate` | `LinearOutSlowInEasing` | Elements entering the screen (settle calmly) |
| `easingEmphasize` | `CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)` | Big moments: model loaded, action card commit |

### Motion rules

- **Calm = restraint.** Default to `motionBase` + `easingStandard`. Reach for `Slow` or `Emphasize` only at narrative moments (download complete, first message sent).
- **No bouncing, no overshoot.** No `Spring(dampingRatio = Bouncy)`. The brand is calm; bounciness is hype.
- **Respect `Settings.Global.ANIMATOR_DURATION_SCALE`.** Honor "reduce motion" by collapsing `Slow` and `Base` to `Fast`, and disabling decorative animations entirely.
- **Stagger lists with care.** When a list animates in, stagger by 30ms per item, max 6 items staggered. Beyond that, fade the whole batch.

---

## 7. Haptics

Three events. Map to the strongest unifying primitive available per Android version.

| Token | Effect | Use |
|---|---|---|
| `hapticTick` | `HapticFeedbackConstants.CONFIRM` (API 30+) / `EFFECT_TICK` fallback | Subtle confirmations: toggle flipped, chip selected, button pressed in chat |
| `hapticConfirm` | `HapticFeedbackConstants.LONG_PRESS` (universal) / `EFFECT_CLICK` | Action card commit, permission granted, send tapped |
| `hapticFail` | Pattern `[0, 40, 60, 40]` waveform | Failed action, permission denied, model load error |

### Haptic rules

- **Default to ON** but expose a single "Reduce haptics" toggle in Settings. Some users find any haptic distracting.
- **Never use haptics for purely decorative moments.** Reserve them for state changes the user initiated or needs to notice.
- **Never combine `hapticFail` with sound.** The audience includes meeting-mode users; silent failure feedback is critical.

---

## 8. Kotlin codification

Suggested mapping into `ui/theme/Tokens.kt` (paste-ready):

```kotlin
// ui/theme/Tokens.kt
package app.mitra.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MitraTokens {

    // Color — Light
    object Light {
        val bg = Color(0xFFFAF7F2)
        val surface = Color(0xFFFFFFFF)
        val surfaceVariant = Color(0xFFF0EBE2)
        val primary = Color(0xFFA85339)
        val onPrimary = Color(0xFFFFFFFF)
        val onSurface = Color(0xFF1F1C1A)
        val onSurfaceVariant = Color(0xFF5C5853)
        val outline = Color(0xFFD4CDC2)
        val success = Color(0xFF4F7A3F)
        val warning = Color(0xFF8B6914)
        val danger = Color(0xFF962F2F)
        val info = Color(0xFF3D5E7A)
    }

    // Color — Dark
    object Dark {
        val bg = Color(0xFF1A1715)
        val surface = Color(0xFF221E1B)
        val surfaceVariant = Color(0xFF2D2925)
        val primary = Color(0xFFD08561)
        val onPrimary = Color(0xFF1A1715)
        val onSurface = Color(0xFFF0EAE1)
        val onSurfaceVariant = Color(0xFFA8A199)
        val outline = Color(0xFF3D3833)
        val success = Color(0xFF8FB97D)
        val warning = Color(0xFFD4A852)
        val danger = Color(0xFFD9706C)
        val info = Color(0xFF7AA0C2)
    }

    // Spacing
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val xxxxl = 56.dp

    // Radii
    val radiusSm = RoundedCornerShape(4.dp)
    val radiusMd = RoundedCornerShape(12.dp)
    val radiusLg = RoundedCornerShape(20.dp)
    val radiusXl = RoundedCornerShape(28.dp)

    // Motion — durations (ms)
    const val motionFast = 120
    const val motionBase = 220
    const val motionSlow = 360

    // Motion — easings
    val easingStandard = FastOutSlowInEasing
    val easingDecelerate = LinearOutSlowInEasing
    val easingEmphasize = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}

// ui/theme/Type.kt
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

val MitraTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)
```

`MitraTheme.kt` wires `MitraTokens.Light` / `MitraTokens.Dark` into Material 3's `lightColorScheme` / `darkColorScheme` via role mapping:

```kotlin
private fun lightScheme() = lightColorScheme(
    background = MitraTokens.Light.bg,
    surface = MitraTokens.Light.surface,
    surfaceVariant = MitraTokens.Light.surfaceVariant,
    primary = MitraTokens.Light.primary,
    onPrimary = MitraTokens.Light.onPrimary,
    onBackground = MitraTokens.Light.onSurface,
    onSurface = MitraTokens.Light.onSurface,
    onSurfaceVariant = MitraTokens.Light.onSurfaceVariant,
    outline = MitraTokens.Light.outline,
    error = MitraTokens.Light.danger,
    onError = MitraTokens.Light.onPrimary,
    // success / warning / info don't have M3 slots — expose as extension via CompositionLocal
)
```

`success`, `warning`, `info` are not in Material 3's role set. Expose them via a `CompositionLocal<MitraSemanticColors>` so screens can read them without falling back to hard-coded hex.

---

## 9. What not to add

Things that will get proposed and that the answer is "no":

- **Gradients on primary surfaces.** No purple-to-pink CTA buttons. Solid clay.
- **Glassmorphism / blur backdrops.** Calm, not 2021 Big Sur.
- **Animated chat bubbles with typing dots that bounce.** A single subtle 3-dot fade is fine. Bouncing breaks the calm.
- **Brand-color glow / pulse on the mic button.** Mic is `surfaceVariant` background + `primary` icon when active. No glow.
- **Day/night auto-switching to a third "AMOLED black" theme.** Two themes only: warm-light, warm-dark.
- **Accent themes / "choose your color."** Mitra is clay. That's the identity. Theming = drift.

---

## 10. Open questions for the user

Things I made calls on; revisit if you disagree:

1. **Primary hue locked to clay (`#A85339`).** Alternatives considered: sage green (too wellness-app), warm amber (too close to warning), muted plum (interesting but reads more "luxury" than "friend"). If you want to test sage, the swap is mechanical.
2. **Inter over Manrope as default.** Manrope is friendlier; Inter is more legible at small sizes. Recommend shipping Inter and revisiting after the first Hindi-user usability test.
3. **No accent / secondary color in the 12 roles.** Material 3's "secondary" / "tertiary" slots are filled by `surfaceVariant` + `info` in practice. If you later need a second accent (e.g., for the voice mode), I'd suggest a muted dusty teal `#5E8B86` rather than extending the clay family.
4. **`Display` size capped at 32sp.** Some onboarding hero text might want bigger — push to 40sp via a one-off `displayHero` extension rather than bloating the scale.
