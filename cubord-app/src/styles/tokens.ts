/**
 * Non-color design tokens — values matched to Figma designs.
 */

// ── Spacing (px) ────────────────────────
export const spacing = {
    xs:  4,
    sm:  8,
    md:  16,
    lg:  24,
    xl:  32,
    xxl: 48,
} as const;

// ── Typography ──────────────────────────
export const fontSize = {
    xs: 10,  // badge counts, tiny labels
    sm: 12,  // captions, expiry dates, chip text
    md: 16,  // body text, item names
    lg: 20,  // section titles ("Pantry Overview", location headers)
    xl: 28,  // screen titles ("Good morning, Alex", "Pantry")
} as const;

export const fontWeight = {
    regular:  '400' as const,
    medium:   '500' as const,
    semibold: '600' as const,
    bold:     '700' as const,
};

/**
 * Font family map — React Native doesn't support CSS `font-weight` +
 * `font-family` combos, so each weight needs its own family name.
 */
export const fontFamily = {
    regular:  'NunitoSans-Regular',
    medium:   'NunitoSans-Medium',
    semibold: 'NunitoSans-SemiBold',
    bold:     'NunitoSans-Bold',
} as const;

// ── Border Radius ───────────────────────
export const radius = {
    sm:   8,    // small chips, filter pills
    md:   12,   // item cards, input fields
    lg:   20,   // overview card, attention cards
    full: 9999, // circular avatars, FAB, dots
} as const;

// ── Shadows (React Native style objects) ─
export const shadow = {
    sm: {
        shadowColor: '#2E2419',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.06,
        shadowRadius: 3,
        elevation: 1,
    },
    md: {
        shadowColor: '#2E2419',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.10,
        shadowRadius: 6,
        elevation: 3,
    },
    lg: {
        shadowColor: '#2E2419',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.14,
        shadowRadius: 12,
        elevation: 6,
    },
} as const;