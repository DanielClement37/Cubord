import {fontFamily} from "@/styles/tokens";

/**
 * Global / reset-level style constants shared across all screens.
 */
export const globalStyles = {
    /** Default font family (regular weight) */
    fontFamily: fontFamily.regular,

    /** Standard screen padding */
    screenPaddingHorizontal: 20,
    screenPaddingVertical: 16,

    /** Bottom tab bar height (for safe area offset calculations) */
    tabBarHeight: 64,
} as const;