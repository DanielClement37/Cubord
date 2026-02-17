import { palette } from '@/styles/colors';

/**
 * Light theme — maps semantic token names to palette values.
 * This is the shape every theme must satisfy.
 */
export const lightTheme = {
    // ── Surfaces ──────────────────────────
    colorBackground:        palette.cream100,       // warm cream page background
    colorSurface:           palette.white,          // card / sheet background
    colorSurfaceElevated:   palette.white,          // modals, popovers

    // ── Text ──────────────────────────────
    colorText:              palette.sand800,         // primary text (dark brown)
    colorTextSecondary:     palette.sand500,         // labels, subtitles
    colorTextInverse:       palette.white,

    // ── Primary ───────────────────────────
    colorPrimary:           palette.sage500,         // main brand green
    colorPrimaryLight:      palette.sage100,         // tinted badge backgrounds
    colorPrimaryDark:       palette.sage700,         // pressed / hover states

    // ── Feedback ──────────────────────────
    colorError:             palette.red400,
    colorSuccess:           palette.green300,
    colorWarning:           palette.amber300,

    // ── Expiration ────────────────────────
    colorExpirySafe:        palette.green300,         // green dot
    colorExpiryWarning:     palette.amber300,         // amber dot
    colorExpiryDanger:      palette.red400,           // red dot

    // ── Borders / dividers ────────────────
    colorBorder:            palette.sand100,
    colorDivider:           palette.cream300,
} as const;