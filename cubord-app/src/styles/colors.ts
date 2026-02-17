/**
 * Raw color palette — named by hue & shade, NOT by role.
 * Semantic mapping (e.g. "primary", "background") happens in the theme layer.
 *
 * Naming convention:  <hue><shade>   (shade 50–950, lighter → darker)
 */
export const palette = {
    // ── Absolute ──────────────────────────
    white:      '#FFFFFF',
    black:      '#000000',

    // ── Cream / Warm Ivory ────────────────
    // The warm off-white family used for backgrounds and subtle fills
    cream50:    '#FFFCF7',
    cream100:   '#FDF6EE',
    cream200:   '#F5EDE3',
    cream300:   '#EDE3D6',
    cream400:   '#E2D5C5',

    // ── Sand / Warm Gray ──────────────────
    // Warm-tinted neutrals for borders, muted text, dividers
    sand100:    '#E8DFD4',
    sand200:    '#D4C9BC',
    sand300:    '#B5A899',
    sand400:    '#968978',
    sand500:    '#786A5B',
    sand600:    '#5C4F42',
    sand700:    '#42372C',
    sand800:    '#2E2419',

    // ── Sage / Olive Green ────────────────
    // The earthy green family seen in icons, tabs, FAB, badges
    sage50:     '#F4F7F0',
    sage100:    '#E6EDDC',
    sage200:    '#CDD9B9',
    sage300:    '#AABF8A',
    sage400:    '#8DA66A',
    sage500:    '#6B7F4E',
    sage600:    '#576841',
    sage700:    '#465436',
    sage800:    '#38432C',
    sage900:    '#2A3221',

    // ── Red ───────────────────────────────
    // Danger / error / expiry-critical
    red50:      '#FDF0EE',
    red100:     '#F5D0CB',
    red200:     '#E39E95',
    red300:     '#D4675A',
    red400:     '#C0392B',
    red500:     '#A33025',
    red600:     '#7F251D',

    // ── Amber ─────────────────────────────
    // Warnings / expiry-approaching
    amber50:    '#FFF8E8',
    amber100:   '#FAEABC',
    amber200:   '#F0D078',
    amber300:   '#D4910A',
    amber400:   '#B47A08',
    amber500:   '#8F6006',

    // ── Green (true) ──────────────────────
    // Success / safe / confirmed states
    green50:    '#F0F6EB',
    green100:   '#D5E6C6',
    green200:   '#A8CC8D',
    green300:   '#5B8C3E',
    green400:   '#4A7332',
    green500:   '#3A5A27',
} as const;