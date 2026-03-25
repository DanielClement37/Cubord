// src/screens/WelcomeScreen.tsx
import React, { useState } from 'react';
import {
    View,
    Modal,
    Pressable,
    KeyboardAvoidingView,
    Platform,
    StyleSheet,
} from 'react-native';
import { useAppStore } from '@/stores/appStore';
import { palette } from '@/styles/colors';
import { spacing, radius, shadow } from '@/styles/tokens';
import { lightTheme } from '@/styles/themes/light';
import { Text, Button, TextInput, ScreenContainer } from '@/components/ui';
import { showError, showInfo } from '@/utils/toast';
import { useCreateHousehold } from '@/hooks/mutations/useCreateHousehold';
import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';

/* ------------------------------------------------------------------ */
/*  Create Household Bottom Sheet Modal                               */
/* ------------------------------------------------------------------ */

interface CreateModalProps {
    visible: boolean;
    onClose: () => void;
}

function CreateHouseholdModal({ visible, onClose }: CreateModalProps) {
    const [name, setName] = useState('');
    const [error, setError] = useState('');
    const setActiveHouseholdId = useAppStore((s) => s.setActiveHouseholdId);

    const { mutate, isPending } = useCreateHousehold();

    const handleCreate = () => {
        const trimmed = name.trim();
        if (trimmed.length < 2) {
            setError('Name must be at least 2 characters');
            return;
        }
        setError('');
        mutate(
            { name: trimmed },
            {
                onSuccess: (household) => {
                    setActiveHouseholdId(household.id);
                    setName('');
                    setError('');
                },
                onError: () => {
                    showError('Failed to create household', 'Please try again.');
                },
            },
        );
    };

    const handleClose = () => {
        setName('');
        setError('');
        onClose();
    };

    return (
        <Modal
            visible={visible}
            transparent
            animationType="fade"
            onRequestClose={handleClose}
        >
            <KeyboardAvoidingView
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                style={modalStyles.keyboardView}
            >
                <Pressable style={modalStyles.backdrop} onPress={handleClose}>
                    <Pressable
                        style={modalStyles.sheet}
                        onPress={() => {}} // prevent backdrop close
                    >
                        {/* Drag handle */}
                        <View style={modalStyles.handleRow}>
                            <View style={modalStyles.handle} />
                        </View>

                        <Text size="lg" weight="bold" align="center">
                            Create Household
                        </Text>
                        <Text
                            size="sm"
                            weight="regular"
                            color="secondary"
                            align="center"
                            style={{ marginTop: spacing.xs }}
                        >
                            Give it a name
                        </Text>

                        <View style={modalStyles.inputSection}>
                            <TextInput
                                label="Household name"
                                placeholder="smith house"
                                value={name}
                                onChangeText={setName}
                                maxLength={50}
                                autoFocus
                                error={error || undefined}
                            />
                        </View>

                        <Button
                            label="Create household"
                            loading={isPending}
                            onPress={handleCreate}
                            style={modalStyles.createButton}
                        />

                        <Pressable
                            onPress={handleClose}
                            style={modalStyles.cancelButton}
                            accessibilityRole="button"
                            accessibilityLabel="Cancel"
                        >
                            <Text size="md" weight="medium" color="secondary">
                                Cancel
                            </Text>
                        </Pressable>
                    </Pressable>
                </Pressable>
            </KeyboardAvoidingView>
        </Modal>
    );
}

const modalStyles = StyleSheet.create({
    backdrop: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.45)',
        justifyContent: 'center',
        alignItems: 'center',
        padding: spacing.lg,
    },
    keyboardView: {
        flex: 1,
    },
    sheet: {
        backgroundColor: lightTheme.colorSurface,
        borderRadius: radius.lg,
        paddingHorizontal: spacing.lg,
        paddingBottom: spacing.xl,
        width: '100%',
        maxWidth: 400,
        ...shadow.lg,
    },
    handleRow: {
        alignItems: 'center',
        paddingTop: spacing.sm,
        paddingBottom: spacing.md,
    },
    handle: {
        width: 40,
        height: 4,
        borderRadius: 2,
        backgroundColor: palette.sand200,
    },
    inputSection: {
        marginTop: spacing.lg,
    },
    createButton: {
        marginTop: spacing.lg,
    },
    cancelButton: {
        alignItems: 'center',
        marginTop: spacing.md,
        paddingVertical: spacing.sm,
    },
});

/* ------------------------------------------------------------------ */
/*  Welcome Screen (First Time User Post-Login)                       */
/* ------------------------------------------------------------------ */

export function WelcomeScreen() {
    const [modalVisible, setModalVisible] = useState(false);

    return (
        <ScreenContainer centered edges={['top', 'bottom', 'left', 'right']}>
            {/* Icon */}
            <View style={styles.iconCircle}>
                <Ionicons name="home-outline" size={32} color={palette.sage600} />
            </View>

            {/* Heading */}
            <Text size="xl" weight="bold" align="center" style={styles.title}>
                Welcome to Cubord!
            </Text>
            <Text
                size="md"
                weight="regular"
                color="secondary"
                align="center"
                style={styles.subtitle}
            >
                Manage your household pantry{'\n'}and never run out of essentials
            </Text>

            {/* Illustration placeholder */}
            <View style={styles.illustration}>
                <MaterialCommunityIcons
                    name="food-apple-outline"
                    size={48}
                    color={palette.red400}
                />
                <MaterialCommunityIcons
                    name="bottle-soda-classic-outline"
                    size={48}
                    color={palette.sage400}
                    style={{ marginLeft: -8 }}
                />
            </View>

            {/* Action buttons */}
            <View style={styles.buttonGroup}>
                <Button
                    variant="secondary"
                    label="I was invited to a home"
                    leftIcon={
                        <MaterialCommunityIcons
                            name="email-outline"
                            size={20}
                            color={lightTheme.colorPrimary}
                        />
                    }
                    onPress={() => {
                        // TODO: Navigate to accept invitation flow
                        showInfo('Coming soon', 'Invitation flow is not yet available.');
                    }}
                    style={styles.actionButton}
                />
                <Button
                    variant="primary"
                    label="Create a new home"
                    leftIcon={
                        <Ionicons name="home-outline" size={20} color={palette.white} />
                    }
                    onPress={() => setModalVisible(true)}
                    style={styles.actionButton}
                />
            </View>

            <CreateHouseholdModal
                visible={modalVisible}
                onClose={() => setModalVisible(false)}
            />
        </ScreenContainer>
    );
}

const styles = StyleSheet.create({
    iconCircle: {
        width: 72,
        height: 72,
        borderRadius: 36,
        backgroundColor: palette.sage50,
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: spacing.lg,
    },
    title: {
        marginBottom: spacing.sm,
    },
    subtitle: {
        marginBottom: spacing.xl,
        lineHeight: 22,
    },
    illustration: {
        flexDirection: 'row',
        alignItems: 'flex-end',
        marginBottom: spacing.xxl,
    },
    buttonGroup: {
        width: '100%',
        gap: spacing.sm,
    },
    actionButton: {
        width: '100%',
    },
});
