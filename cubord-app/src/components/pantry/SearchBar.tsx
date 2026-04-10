import React, { useState, useRef, useCallback, useEffect } from 'react';
import {
    View,
    TextInput as RNTextInput,
    StyleSheet,
    Pressable,
} from 'react-native';
import { palette } from '@/styles/colors';
import { spacing, radius, fontFamily, fontSize } from '@/styles/tokens';
import { Ionicons } from '@expo/vector-icons';

interface SearchBarProps {
    placeholder?: string;
    /** Called with the debounced search text (after 350ms of inactivity). */
    onSearch: (query: string) => void;
    /** Delay in ms before triggering onSearch. Default: 350. */
    debounceMs?: number;
}

/**
 * A styled search input with a magnifying glass icon, debounced callback,
 * and an inline clear button.
 */
export function SearchBar({
                              placeholder = 'Search items…',
                              onSearch,
                              debounceMs = 350,
                          }: SearchBarProps) {
    const [value, setValue] = useState('');
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        if (debounceRef.current) clearTimeout(debounceRef.current);

        debounceRef.current = setTimeout(() => {
            onSearch(value.trim());
        }, debounceMs);

        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [value, debounceMs, onSearch]);

    const handleClear = useCallback(() => {
        setValue('');
        onSearch('');
    }, [onSearch]);

    return (
        <View style={styles.container}>
            <Ionicons
                name="search"
                size={18}
                color={palette.sand400}
                style={styles.icon}
            />
            <RNTextInput
                value={value}
                onChangeText={setValue}
                placeholder={placeholder}
                placeholderTextColor={palette.sand300}
                style={styles.input}
                returnKeyType="search"
                autoCorrect={false}
                autoCapitalize="none"
            />
            {value.length > 0 && (
                <Pressable onPress={handleClear} hitSlop={8}>
                    <Ionicons name="close-circle" size={18} color={palette.sand300} />
                </Pressable>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: palette.white,
        borderRadius: radius.md,
        borderWidth: 1,
        borderColor: palette.sand100,
        paddingHorizontal: spacing.md,
        height: 44,
        gap: spacing.sm,
    },
    icon: {
        flexShrink: 0,
    },
    input: {
        flex: 1,
        fontFamily: fontFamily.regular,
        fontSize: fontSize.md,
        color: palette.sand800,
        paddingVertical: 0,
    },
});