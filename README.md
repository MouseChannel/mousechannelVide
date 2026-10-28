# mousechannelVide

Author and organization: mousechannel.

A Kotlin IntelliJ Platform plugin with Neovide-inspired elastic cursor movement.
Four independently damped corners stretch into a continuous polygon during motion,
with a narrowed trailing edge that returns to full size as the cursor arrives.
This is an approximation of Neovide's appearance, not its renderer or particle effects.

## Build

Requires JDK 25 and Gradle 9.2.1. Targets IntelliJ Platform 2026.2 (build 262+).
The Gradle wrapper is included.

```powershell
.\gradlew.bat test buildPlugin
# Or build against an installed IDE, using a JDK compatible with that IDE:
.\gradlew.bat test buildPlugin "-PlocalIdePath=D:/soft/jetbrains/Rider"
```

Install `build/distributions/neovide-caret-0.1.9.zip` with
Settings > Plugins > gear > Install Plugin from Disk, then restart the IDE.
Settings > Editor > mousechannelVide controls duration, stretch, frame cap and opacity.
Uncheck "Follow editor caret color" to choose a custom animation color.
"Neon glow and gradient trail" adds a hue-shifted tail, a bright leading edge
and two lightweight glow outlines. Disable it to keep the flat-color trail.
Arrival expansion and Bloom have been removed. Motion stops and the native
caret is restored immediately on arrival.
Use `runIde` to launch an isolated development IDE.

## Runtime behavior

- The logical caret never moves artificially; only its appearance is animated.
- Native caret attributes are hidden during movement and restored on arrival.
- Fixed four-corner arrays and a reused path; no particle or history allocations.
- A one-shot Swing timer follows absolute nanosecond deadlines only during animation.
  Requested frame rates range from 60 to 120; legacy lower settings target 60.
  Late frames skip missed deadlines instead of queueing catch-up work. Dirty repaint is clipped
  to the viewport and covers the previous and current silhouette.
- Large scrolling, selection, multiple carets, IME composition, loss of focus,
  hidden editors, off-screen destinations and EDT stalls cancel animation and restore the caret.
- Visible full-line jumps animate regardless of distance. Small scroll steps
  and viewport resizing preserve animation while the target remains visible.
- Focus acquisition and cancellation synchronize the animation to the actual caret.
- Only the focused editor registers a glass-pane painter. Disabling the feature
  removes that registration. Frame timing compensates for callback time and millisecond rounding.
- Repaint clipping reuses rectangles and coordinate-conversion scratch storage.
- Only main file editors are attached. Multi-caret animation is not enabled.

Enable "Log animation performance diagnostics" to write per-animation frame/paint
counts, maximum tick/paint CPU duration and maximum dirty-rectangle viewport
percentage, achieved timer-callback FPS, maximum interval and intervals over 25ms
to the IDE log. Diagnostics are disabled by default. These measure
plugin callbacks and requested repaint area, not end-to-end latency or the entire
Swing repaint cost. A long diagonal trail can still cover a large bounding box.

## Verification

Unit tests check frame-rate independence and convergence. Interactive IDE checks
are still required for Chinese IME, IdeaVim, split editors, HiDPI, folding and
rapid navigation. Profile EDT time with JFR under typing and scrolling load;
there is no measured latency or FPS guarantee yet.
