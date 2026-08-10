# Accessibility

Chalna custom controls expose role, enabled/selected/checked state, content description, state description, focus order, and at least 48 dp targets. Decorative Aura/Glow/thumbnail layers do not enter the accessibility tree.

Recording start, saved, cancel, and failure are announced once through semantic state/live-region changes; elapsed time is not announced each second. Gallery reports item duration/storage/selection and selected count. Batch outcomes use transient assertive/polite semantics appropriate to severity. Player controls expose seek range and increment/decrement actions in addition to touch dragging.

Focus indication, hover, Enter/Space activation, D-pad movement, Back/Escape, and mouse wheel scrolling are part of custom interaction behavior. Press feedback uses restrained luminance/scale rather than Material ripple.

Layouts scroll where needed, use a bounded content width, increase Gallery columns with width, and avoid orientation lock. Korean and English resources are complete; dates/times/numbers follow locale and system 12/24-hour settings. UI QA covers 200% font, long English, pseudo-locale/RTL, Night/Mist, compact and expanded layouts, and reduced motion.

Normal text targets 4.5:1 contrast; large text and control graphics target 3:1. State is never color-only. System reduced-motion, Chalna reduced mode, lifecycle pause, and power saver stop or simplify decorative movement without removing state information.
