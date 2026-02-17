import { lightTheme } from './themes/light';

/** The canonical theme shape — derived from the light theme. */
export type Theme = typeof lightTheme;

export const themes = {
    light: lightTheme,
    //dark: darkTheme,
} as const;

export type ThemeName = keyof typeof themes;