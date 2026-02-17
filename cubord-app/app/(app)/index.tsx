import { View, ScrollView, StyleSheet, Alert } from 'react-native';
import { useAuth } from '@/contexts/AuthContext';
import { TokenLoggerButton } from '@/components/TokenLoggerButton';
import {
    ScreenContainer,
    Text,
    Button,
    Card,
    TextInput,
    Badge,
    Spinner,
} from '@/components/ui';
import { spacing } from '@/styles';
import { useState } from 'react';

export default function HomeScreen() {
    const { signOut } = useAuth();
    const [inputValue, setInputValue] = useState('');
    const [errorInput, setErrorInput] = useState('');

    return (
        <ScreenContainer>
            <ScrollView
                showsVerticalScrollIndicator={false}
                contentContainerStyle={styles.scroll}
            >
                {/* ── Existing logic ─────────────────── */}
                <Text size="xl" weight="bold">
                    Welcome! You are signed in.
                </Text>
                <TokenLoggerButton />

                {/* ── Text variants ──────────────────── */}
                <SectionTitle title="Text" />
                <Text size="xl" weight="bold">XL Bold — Screen Title</Text>
                <Text size="lg" weight="semibold">LG Semibold — Section Header</Text>
                <Text size="md">MD Regular — Body text</Text>
                <Text size="sm" color="secondary">SM Secondary — Caption</Text>
                <Text size="md" color="error">Error colored text</Text>
                <Text size="md" color="success">Success colored text</Text>

                {/* ── Badge variants ─────────────────── */}
                <SectionTitle title="Badges" />
                <View style={styles.row}>
                    <Badge variant="safe" label="Fresh" />
                    <Badge variant="warning" label="Expiring Soon" />
                    <Badge variant="danger" label="Expired" />
                    <Badge variant="neutral" label="12 items" />
                </View>

                {/* ── Button variants ────────────────── */}
                <SectionTitle title="Buttons" />
                <Button
                    label="Primary Button"
                    onPress={() => Alert.alert('Primary pressed')}
                />
                <Button
                    variant="secondary"
                    label="Secondary Button"
                    onPress={() => Alert.alert('Secondary pressed')}
                />
                <Button
                    variant="ghost"
                    label="Ghost Button"
                    onPress={() => Alert.alert('Ghost pressed')}
                />
                <Button
                    variant="danger"
                    label="Danger Button"
                    onPress={() => Alert.alert('Danger pressed')}
                />
                <Button
                    label="Loading State"
                    loading
                />
                <Button
                    label="Disabled State"
                    disabled
                />

                {/* ── TextInput variants ─────────────── */}
                <SectionTitle title="Text Inputs" />
                <TextInput
                    label="Item Name"
                    placeholder="e.g. Organic Whole Milk"
                    value={inputValue}
                    onChangeText={setInputValue}
                />
                <TextInput
                    label="Quantity"
                    placeholder="Enter quantity"
                    value={errorInput}
                    onChangeText={setErrorInput}
                    error={errorInput.length === 0 ? 'Quantity is required' : undefined}
                />
                <TextInput
                    label="Location (disabled)"
                    placeholder="Kitchen Pantry"
                    disabled
                />

                {/* ── Card variants ──────────────────── */}
                <SectionTitle title="Cards" />
                <Card>
                    <Text size="lg" weight="semibold">Default Card</Text>
                    <Text size="sm" color="secondary">
                        This is a basic surface card with subtle shadow.
                    </Text>
                </Card>
                <Card variant="elevated">
                    <Text size="lg" weight="semibold">Elevated Card</Text>
                    <Text size="sm" color="secondary">
                        Elevated variant with a stronger shadow.
                    </Text>
                </Card>
                <Card
                    variant="elevated"
                    onPress={() => Alert.alert('Card pressed!')}
                >
                    <Text size="lg" weight="semibold">Pressable Card</Text>
                    <Text size="sm" color="secondary">
                        Tap me — I'm an elevated pressable card.
                    </Text>
                </Card>

                {/* ── Spinner variants ────────────────── */}
                <SectionTitle title="Spinners" />
                <View style={styles.row}>
                    <View style={styles.spinnerCol}>
                        <Spinner size="sm" />
                        <Text size="sm" color="secondary" align="center">sm</Text>
                    </View>
                    <View style={styles.spinnerCol}>
                        <Spinner size="md" />
                        <Text size="sm" color="secondary" align="center">md</Text>
                    </View>
                    <View style={styles.spinnerCol}>
                        <Spinner size="lg" />
                        <Text size="sm" color="secondary" align="center">lg</Text>
                    </View>
                </View>

                {/* ── Sign Out (existing logic) ──────── */}
                <View style={styles.signOut}>
                    <Button
                        variant="danger"
                        label="Sign Out"
                        onPress={signOut}
                    />
                </View>
            </ScrollView>
        </ScreenContainer>
    );
}

/** Small helper for section dividers */
function SectionTitle({ title }: { title: string }) {
    return (
        <View style={styles.sectionHeader}>
            <Text size="lg" weight="bold" color="secondary">
                {title}
            </Text>
        </View>
    );
}

const styles = StyleSheet.create({
    scroll: {
        gap: spacing.md,
        paddingBottom: spacing.xxl,
    },
    row: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: spacing.sm,
        alignItems: 'center',
    },
    sectionHeader: {
        marginTop: spacing.lg,
        borderBottomWidth: 1,
        borderBottomColor: '#EDE3D6',
        paddingBottom: spacing.xs,
    },
    spinnerCol: {
        alignItems: 'center',
        gap: spacing.xs,
        width: 60,
    },
    signOut: {
        marginTop: spacing.xl,
    },
});