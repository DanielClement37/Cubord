/**
 * Standard units for pantry items.
 */
export const PANTRY_UNITS = [
    'Unit',
    'oz',
    'lb',
    'g',
    'kg',
    'fl oz',
    'cup',
    'pt',
    'qt',
    'gal',
    'L',
    'mL',
    'dozen',
    'pack',
    'bag',
    'box',
    'can',
    'jar',
    'bottle',
    'bunch',
    'loaf',
    'slice',
] as const;

export type PantryUnit = (typeof PANTRY_UNITS)[number];