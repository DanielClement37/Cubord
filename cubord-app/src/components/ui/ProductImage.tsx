import React, { useState } from 'react';
import { View, Image, StyleSheet, type ViewStyle } from 'react-native';
import { Text } from '@/components/ui/Text';
import { palette } from '@/styles/colors';
import { radius } from '@/styles/tokens';
import { getProductEmoji } from '@/utils/productEmoji';

interface ProductImageProps {
    imageUrl: string | null | undefined;
    name: string | null | undefined;
    category: string | null | undefined;
    /** Width and height of the container. Default: 56 */
    size?: number;
    style?: ViewStyle;
}

/**
 * Displays a product image if one is available, otherwise falls back
 * to an emoji that best matches the product category/name.
 */
export function ProductImage({
                                 imageUrl,
                                 name,
                                 category,
                                 size = 56,
                                 style,
                             }: ProductImageProps) {
    const [failed, setFailed] = useState(false);
    const showImage = !!imageUrl && !failed;

    const containerStyle: ViewStyle = {
        width: size,
        height: size,
        borderRadius: radius.sm,
        backgroundColor: palette.cream200,
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
    };

    if (showImage) {
        return (
            <View style={[containerStyle, style]}>
                <Image
                    source={{ uri: imageUrl }}
                    style={styles.image}
                    resizeMode="cover"
                    onError={() => setFailed(true)}
                />
            </View>
        );
    }

    const emoji = getProductEmoji(name, category);
    return (
        <View style={[containerStyle, style]}>
            <Text size="xl">{emoji}</Text>
        </View>
    );
}

const styles = StyleSheet.create({
    image: {
        width: '100%',
        height: '100%',
    },
});