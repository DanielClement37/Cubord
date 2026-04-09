/**
 * Maps a product's category or name to the closest emoji.
 * Used as a fallback when no product image URL is available.
 */

type EmojiRule = {
    keywords: string[];
    emoji: string;
};

/**
 * Ordered from most-specific to least-specific.
 * The first matching rule wins.
 */
const EMOJI_RULES: EmojiRule[] = [
    // ── Proteins ──────────────────────────
    { keywords: ['chicken', 'poultry'], emoji: '🍗' },
    { keywords: ['beef', 'steak', 'ground meat'], emoji: '🥩' },
    { keywords: ['pork', 'ham', 'bacon'], emoji: '🥓' },
    { keywords: ['turkey'], emoji: '🦃' },
    { keywords: ['sausage', 'hot dog', 'hotdog'], emoji: '🌭' },
    { keywords: ['fish', 'salmon', 'tuna', 'sardine', 'cod'], emoji: '🐟' },
    { keywords: ['shrimp', 'prawn', 'seafood', 'crab', 'lobster'], emoji: '🦐' },
    { keywords: ['egg'], emoji: '🥚' },

    // ── Dairy ─────────────────────────────
    { keywords: ['milk'], emoji: '🥛' },
    { keywords: ['cheese', 'cream cheese', 'cottage cheese'], emoji: '🧀' },
    { keywords: ['yogurt', 'yoghurt'], emoji: '🥛' },
    { keywords: ['butter'], emoji: '🧈' },
    { keywords: ['ice cream', 'gelato'], emoji: '🍦' },

    // ── Bread & Bakery ────────────────────
    { keywords: ['bread', 'loaf', 'toast'], emoji: '🍞' },
    { keywords: ['bagel'], emoji: '🥯' },
    { keywords: ['croissant', 'pastry'], emoji: '🥐' },
    { keywords: ['muffin', 'cupcake'], emoji: '🧁' },
    { keywords: ['cookie', 'biscuit'], emoji: '🍪' },
    { keywords: ['cake'], emoji: '🎂' },
    { keywords: ['pie'], emoji: '🥧' },
    { keywords: ['tortilla', 'wrap', 'flatbread'], emoji: '🫓' },
    { keywords: ['pretzel'], emoji: '🥨' },
    { keywords: ['pancake', 'waffle'], emoji: '🧇' },

    // ── Fruits ────────────────────────────
    { keywords: ['apple'], emoji: '🍎' },
    { keywords: ['banana'], emoji: '🍌' },
    { keywords: ['orange', 'tangerine', 'clementine'], emoji: '🍊' },
    { keywords: ['lemon'], emoji: '🍋' },
    { keywords: ['lime'], emoji: '🍋' },
    { keywords: ['grape'], emoji: '🍇' },
    { keywords: ['strawberry'], emoji: '🍓' },
    { keywords: ['blueberry', 'berry', 'berries', 'raspberry', 'blackberry'], emoji: '🫐' },
    { keywords: ['peach', 'nectarine'], emoji: '🍑' },
    { keywords: ['pear'], emoji: '🍐' },
    { keywords: ['watermelon'], emoji: '🍉' },
    { keywords: ['melon', 'cantaloupe', 'honeydew'], emoji: '🍈' },
    { keywords: ['pineapple'], emoji: '🍍' },
    { keywords: ['mango'], emoji: '🥭' },
    { keywords: ['coconut'], emoji: '🥥' },
    { keywords: ['avocado'], emoji: '🥑' },
    { keywords: ['cherry', 'cherries'], emoji: '🍒' },
    { keywords: ['kiwi'], emoji: '🥝' },

    // ── Vegetables ────────────────────────
    { keywords: ['tomato'], emoji: '🍅' },
    { keywords: ['carrot'], emoji: '🥕' },
    { keywords: ['corn'], emoji: '🌽' },
    { keywords: ['broccoli'], emoji: '🥦' },
    { keywords: ['lettuce', 'salad', 'greens', 'spinach', 'kale'], emoji: '🥬' },
    { keywords: ['cucumber'], emoji: '🥒' },
    { keywords: ['pepper', 'jalapeño', 'chili'], emoji: '🌶️' },
    { keywords: ['mushroom'], emoji: '🍄' },
    { keywords: ['onion'], emoji: '🧅' },
    { keywords: ['garlic'], emoji: '🧄' },
    { keywords: ['potato'], emoji: '🥔' },
    { keywords: ['sweet potato', 'yam'], emoji: '🍠' },
    { keywords: ['eggplant', 'aubergine'], emoji: '🍆' },
    { keywords: ['bean', 'legume', 'lentil'], emoji: '🫘' },
    { keywords: ['pea'], emoji: '🫛' },

    // ── Grains & Pasta ────────────────────
    { keywords: ['pasta', 'spaghetti', 'noodle', 'macaroni', 'penne'], emoji: '🍝' },
    { keywords: ['rice'], emoji: '🍚' },
    { keywords: ['cereal', 'oat', 'granola'], emoji: '🥣' },
    { keywords: ['flour'], emoji: '🌾' },

    // ── Condiments & Sauces ───────────────
    { keywords: ['ketchup', 'tomato sauce', 'tomato paste'], emoji: '🍅' },
    { keywords: ['honey'], emoji: '🍯' },
    { keywords: ['salt'], emoji: '🧂' },
    { keywords: ['sauce', 'dressing', 'mustard', 'mayo', 'mayonnaise', 'salsa'], emoji: '🫙' },
    { keywords: ['oil', 'olive oil', 'cooking oil'], emoji: '🫒' },
    { keywords: ['vinegar'], emoji: '🫙' },
    { keywords: ['spice', 'seasoning', 'herb', 'cilantro', 'parsley', 'basil', 'oregano'], emoji: '🌿' },

    // ── Snacks ────────────────────────────
    { keywords: ['chip', 'crisp', 'nacho'], emoji: '🍟' },
    { keywords: ['cracker'], emoji: '🍘' },
    { keywords: ['popcorn'], emoji: '🍿' },
    { keywords: ['nut', 'peanut', 'almond', 'cashew', 'walnut'], emoji: '🥜' },
    { keywords: ['chocolate', 'cocoa'], emoji: '🍫' },
    { keywords: ['candy', 'sweet', 'gummy'], emoji: '🍬' },
    { keywords: ['granola bar', 'protein bar', 'bar', 'snack bar'], emoji: '🍫' },

    // ── Beverages ─────────────────────────
    { keywords: ['water'], emoji: '💧' },
    { keywords: ['juice'], emoji: '🧃' },
    { keywords: ['soda', 'pop', 'cola', 'seltzer', 'sparkling'], emoji: '🥤' },
    { keywords: ['coffee'], emoji: '☕' },
    { keywords: ['tea'], emoji: '🍵' },
    { keywords: ['beer', 'ale', 'lager'], emoji: '🍺' },
    { keywords: ['wine'], emoji: '🍷' },

    // ── Prepared / Frozen ─────────────────
    { keywords: ['pizza'], emoji: '🍕' },
    { keywords: ['burger', 'hamburger'], emoji: '🍔' },
    { keywords: ['taco'], emoji: '🌮' },
    { keywords: ['burrito'], emoji: '🌯' },
    { keywords: ['sushi'], emoji: '🍣' },
    { keywords: ['soup', 'broth', 'stew'], emoji: '🍲' },
    { keywords: ['sandwich', 'sub'], emoji: '🥪' },
    { keywords: ['frozen'], emoji: '🧊' },
    { keywords: ['canned', 'can of'], emoji: '🥫' },

    // ── Other ─────────────────────────────
    { keywords: ['sugar'], emoji: '🍬' },
    { keywords: ['baby food', 'infant'], emoji: '🍼' },
    { keywords: ['pet food', 'dog food', 'cat food'], emoji: '🐾' },

    // ── Broad categories (check last) ─────
    { keywords: ['snack'], emoji: '🍿' },
    { keywords: ['fruit'], emoji: '🍎' },
    { keywords: ['vegetable', 'produce', 'veggie'], emoji: '🥬' },
    { keywords: ['meat', 'protein'], emoji: '🥩' },
    { keywords: ['dairy'], emoji: '🥛' },
    { keywords: ['bakery', 'baked'], emoji: '🍞' },
    { keywords: ['beverage', 'drink'], emoji: '🥤' },
    { keywords: ['condiment'], emoji: '🫙' },
    { keywords: ['deli'], emoji: '🥪' },
    { keywords: ['pantry', 'grocery', 'food'], emoji: '🛒' },
];

/** Default emoji when nothing matches */
const DEFAULT_EMOJI = '📦';

function normalize(text: string): string {
    return text.toLowerCase().trim();
}

/**
 * Returns the most relevant emoji for a product based on its name and category.
 */
export function getProductEmoji(
    name: string | null | undefined,
    category: string | null | undefined,
): string {
    const haystack = normalize([name ?? '', category ?? ''].join(' '));

    for (const rule of EMOJI_RULES) {
        if (rule.keywords.some((kw) => haystack.includes(kw))) {
            return rule.emoji;
        }
    }

    return DEFAULT_EMOJI;
}