/**
 * Heuristic: guess a default number of days until expiration
 * based on the product category and/or name.
 *
 * Returns `null` when no reasonable guess can be made.
 */

type Rule = {
    keywords: string[];
    days: number;
};

/**
 * Ordered from most-perishable to least.
 * The first matching rule wins.
 */
const RULES: Rule[] = [
    // Ultra-perishable (1–5 days)
    {keywords: ['fresh juice', 'deli', 'sushi'], days: 3},

    // Dairy & bread (5–14 days)
    {keywords: ['milk', 'yogurt', 'yoghurt', 'cream cheese', 'sour cream', 'cottage cheese'], days: 10},
    {keywords: ['bread', 'bagel', 'tortilla', 'bun', 'roll', 'muffin', 'croissant'], days: 7},
    {keywords: ['egg'], days: 21},

    // Refrigerated proteins (5–7 days)
    {keywords: ['chicken', 'turkey', 'beef', 'pork', 'ground meat', 'sausage', 'bacon'], days: 5},
    {keywords: ['fish', 'salmon', 'shrimp', 'seafood'], days: 3},

    // Cheese (longer-life dairy)
    {keywords: ['cheese', 'butter'], days: 30},

    // Produce
    {keywords: ['lettuce', 'spinach', 'greens', 'herb', 'cilantro', 'parsley', 'basil'], days: 5},
    {keywords: ['berry', 'berries', 'strawberry', 'blueberry', 'raspberry'], days: 5},
    {keywords: ['banana', 'avocado', 'peach', 'pear', 'mango', 'melon'], days: 7},
    {keywords: ['apple', 'orange', 'lemon', 'lime', 'grapefruit', 'grape', 'citrus'], days: 14},
    {keywords: ['carrot', 'celery', 'broccoli', 'cauliflower', 'pepper', 'tomato', 'cucumber', 'zucchini'], days: 10},
    {keywords: ['potato', 'onion', 'garlic', 'sweet potato'], days: 30},

    // Condiments & sauces (opened)
    {keywords: ['ketchup', 'mustard', 'mayo', 'mayonnaise', 'salsa', 'dressing', 'sauce'], days: 60},

    // Frozen
    {keywords: ['frozen', 'ice cream'], days: 180},

    // Canned / shelf-stable
    {keywords: ['canned', 'can of', 'soup', 'beans', 'tuna', 'sardine', 'tomato paste', 'tomato sauce'], days: 730},
    {keywords: ['pasta', 'noodle', 'rice', 'grain', 'oat', 'cereal', 'flour', 'sugar', 'salt'], days: 365},
    {keywords: ['snack', 'chip', 'cracker', 'cookie', 'granola', 'bar'], days: 90},

    // Beverages
    {keywords: ['water', 'soda', 'pop', 'seltzer', 'sparkling'], days: 365},
    {keywords: ['juice', 'tea', 'coffee'], days: 180},
];

function normalize(text: string): string {
    return text.toLowerCase().trim();
}

/**
 * Returns a guessed number of days until expiration, or `null`.
 *
 * Priority:
 *  1. `product.defaultExpirationDays` (from the backend)
 *  2. Keyword match against name and category
 */
export function guessExpirationDays(
    name: string | null | undefined,
    category: string | null | undefined,
    defaultExpirationDays: number | null | undefined,
): number | null {
    // Backend already knows best
    if (defaultExpirationDays && defaultExpirationDays > 0) {
        return defaultExpirationDays;
    }

    const haystack = normalize([name ?? '', category ?? ''].join(' '));

    for (const rule of RULES) {
        if (rule.keywords.some((kw) => haystack.includes(kw))) {
            return rule.days;
        }
    }

    return null;
}

/**
 * Convenience: returns a Date object set to `today + guessed days`, or `null`.
 */
export function guessExpirationDate(
    name: string | null | undefined,
    category: string | null | undefined,
    defaultExpirationDays: number | null | undefined,
): Date | null {
    const days = guessExpirationDays(name, category, defaultExpirationDays);
    if (days === null) return null;

    const d = new Date();
    d.setDate(d.getDate() + days);
    return d;
}