import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
    View,
    StyleSheet,
    Modal,
    Pressable,
    Keyboard,
    ScrollView,
} from 'react-native';
import { CameraView, useCameraPermissions } from 'expo-camera';
import * as Haptics from 'expo-haptics';
import { Text, Button, TextInput, Spinner } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { lookupProductByUPC } from '@/services/productLookup';
import type { ProductResponse } from '@/types';

interface ScannerScreenProps {
    onProductFound: (product: ProductResponse) => void;
    onProductNotFound: (upc: string) => void;
    onSearchProduct: () => void;
}

export function ScannerScreen({ onProductFound, onProductNotFound, onSearchProduct }: ScannerScreenProps) {
    const [permission, requestPermission] = useCameraPermissions();
    const [isProcessing, setIsProcessing] = useState(false);
    const [scanStatus, setScanStatus] = useState<'idle' | 'scanning' | 'error'>('idle');
    const [errorMessage, setErrorMessage] = useState('');
    const [showManualEntry, setShowManualEntry] = useState(false);
    const [manualUpc, setManualUpc] = useState('');
    const [manualError, setManualError] = useState('');
    const processingRef = useRef(false);

    // Keep ref in sync
    useEffect(() => {
        processingRef.current = isProcessing;
    }, [isProcessing]);

    const handleLookup = useCallback(
        async (upc: string) => {
            setIsProcessing(true);
            setScanStatus('scanning');
            setErrorMessage('');

            try {
                Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);

                const result = await lookupProductByUPC(upc);

                if (result.status === 'found') {
                    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
                    onProductFound(result.product);
                } else {
                    onProductNotFound(result.upc);
                }
            } catch (error) {
                setScanStatus('error');
                setErrorMessage(
                    error instanceof Error ? error.message : 'Something went wrong. Try again.',
                );
                setIsProcessing(false);
            }
        },
        [onProductFound, onProductNotFound],
    );

    const onBarcodeScanned = useCallback(
        (result: { data: string; type: string }) => {
            if (processingRef.current) return;
            handleLookup(result.data);
        },
        [handleLookup],
    );

    const handleManualSubmit = useCallback(() => {
        const trimmed = manualUpc.trim();
        if (!/^\d{8,14}$/.test(trimmed)) {
            setManualError('Enter a valid barcode (8–14 digits)');
            return;
        }
        setManualError('');
        setShowManualEntry(false);
        setManualUpc('');
        handleLookup(trimmed);
    }, [manualUpc, handleLookup]);

    const resetScanner = useCallback(() => {
        setIsProcessing(false);
        setScanStatus('idle');
        setErrorMessage('');
    }, []);

    // ── Permission states ──
    if (!permission) {
        return (
            <View style={styles.centered}>
                <Spinner size="lg" />
            </View>
        );
    }

    if (!permission.granted) {
        return (
            <View style={styles.centered}>
                <Text size="lg" weight="semibold" align="center">
                    Camera access needed
                </Text>
                <Text
                    size="md"
                    color="secondary"
                    align="center"
                    style={{ marginTop: spacing.sm, marginBottom: spacing.lg }}
                >
                    We need camera access to scan barcodes
                </Text>
                <Button label="Grant Permission" onPress={requestPermission} />
            </View>
        );
    }

    return (
        <View style={styles.container}>
            {/* Camera */}
            <View style={styles.cameraContainer}>
                <CameraView
                    style={StyleSheet.absoluteFill}
                    facing="back"
                    barcodeScannerSettings={{
                        barcodeTypes: ['ean13', 'ean8', 'upc_a', 'upc_e', 'code128', 'code39'],
                    }}
                    onBarcodeScanned={isProcessing ? undefined : onBarcodeScanned}
                />

                {/* Scan overlay */}
                <View style={styles.overlay}>
                    <View style={styles.overlayTop} />
                    <View style={styles.overlayMiddle}>
                        <View style={styles.overlaySide} />
                        <View style={styles.scanWindow}>
                            <View style={[styles.corner, styles.cornerTopLeft]} />
                            <View style={[styles.corner, styles.cornerTopRight]} />
                            <View style={[styles.corner, styles.cornerBottomLeft]} />
                            <View style={[styles.corner, styles.cornerBottomRight]} />
                        </View>
                        <View style={styles.overlaySide} />
                    </View>
                    <View style={styles.overlayBottom}>
                        {scanStatus === 'idle' && (
                            <Text size="md" style={styles.overlayText}>
                                Point camera at a barcode
                            </Text>
                        )}
                        {scanStatus === 'scanning' && (
                            <View style={styles.scanningRow}>
                                <Spinner size="sm" color={palette.white} />
                                <Text
                                    size="md"
                                    style={[styles.overlayText, { marginLeft: spacing.sm }]}
                                >
                                    Scanning…
                                </Text>
                            </View>
                        )}
                        {scanStatus === 'error' && (
                            <View style={styles.errorContainer}>
                                <Text size="sm" style={styles.overlayText}>
                                    {errorMessage}
                                </Text>
                                <Button
                                    label="Try Again"
                                    variant="secondary"
                                    onPress={resetScanner}
                                    style={{ marginTop: spacing.sm }}
                                />
                            </View>
                        )}
                    </View>
                </View>

                {/* Top bar */}
                <View style={styles.topBar}>
                    <Text size="lg" weight="bold" style={{ color: palette.white }}>
                        Barcode Scanning
                    </Text>
                </View>
            </View>

            {/* Bottom actions */}
            <View style={styles.bottomPanel}>
                <Button
                    label="Search for product"
                    variant="secondary"
                    onPress={onSearchProduct}
                />
                <Button
                    label="Enter barcode manually"
                    variant="ghost"
                    onPress={() => setShowManualEntry(true)}
                    style={{ marginTop: spacing.sm }}
                />
            </View>

            {/* Manual Entry Modal */}
            <Modal
                visible={showManualEntry}
                animationType="slide"
                transparent
                onRequestClose={() => setShowManualEntry(false)}
            >
                <Pressable
                    style={styles.modalOverlay}
                    onPress={() => {
                        Keyboard.dismiss();
                        setShowManualEntry(false);
                    }}
                >
                    <ScrollView
                        contentContainerStyle={styles.modalScrollContent}
                        bounces={false}
                        keyboardShouldPersistTaps="handled"
                    >
                        <View style={{ flex: 1 }} />
                        <Pressable style={styles.modalContent} onPress={() => {}}>
                            <Text size="lg" weight="bold" style={{ marginBottom: spacing.sm }}>
                                Enter Barcode
                            </Text>
                            <Text
                                size="sm"
                                color="secondary"
                                style={{ marginBottom: spacing.lg }}
                            >
                                Enter the numbers below the barcode on your product
                            </Text>

                            <TextInput
                                label="UPC / Barcode"
                                placeholder="0 1 2 3 4 5 6 7 8 9 0 1 2"
                                value={manualUpc}
                                onChangeText={(text) => {
                                    setManualUpc(text.replace(/[^0-9]/g, ''));
                                    setManualError('');
                                }}
                                keyboardType="number-pad"
                                maxLength={14}
                                error={manualError}
                                autoFocus
                            />

                            <Text
                                size="sm"
                                color="secondary"
                                align="center"
                                style={{ marginTop: spacing.xs, marginBottom: spacing.lg }}
                            >
                                Usually 8–14 digits
                            </Text>

                            <Button
                                label="Look Up Product"
                                onPress={handleManualSubmit}
                                loading={isProcessing}
                            />
                            <Button
                                label="Cancel"
                                variant="ghost"
                                onPress={() => {
                                    setShowManualEntry(false);
                                    setManualUpc('');
                                    setManualError('');
                                }}
                                style={{ marginTop: spacing.sm }}
                            />
                        </Pressable>
                    </ScrollView>
                </Pressable>
            </Modal>
        </View>
    );
}

const SCAN_WINDOW_SIZE = 250;
const CORNER_SIZE = 24;
const CORNER_THICKNESS = 3;

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: palette.black,
    },
    centered: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: palette.cream100,
        paddingHorizontal: spacing.lg,
    },
    cameraContainer: {
        flex: 1,
        position: 'relative',
    },
    topBar: {
        position: 'absolute',
        top: 60,
        left: 0,
        right: 0,
        alignItems: 'center',
    },
    overlay: {
        ...StyleSheet.absoluteFillObject,
        justifyContent: 'center',
    },
    overlayTop: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.5)',
    },
    overlayMiddle: {
        flexDirection: 'row',
        height: SCAN_WINDOW_SIZE,
    },
    overlaySide: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.5)',
    },
    scanWindow: {
        width: SCAN_WINDOW_SIZE,
        height: SCAN_WINDOW_SIZE,
        position: 'relative',
    },
    overlayBottom: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.5)',
        alignItems: 'center',
        paddingTop: spacing.lg,
    },
    overlayText: {
        color: palette.white,
    },
    scanningRow: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    errorContainer: {
        alignItems: 'center',
        paddingHorizontal: spacing.lg,
    },
    corner: {
        position: 'absolute',
        width: CORNER_SIZE,
        height: CORNER_SIZE,
    },
    cornerTopLeft: {
        top: 0,
        left: 0,
        borderTopWidth: CORNER_THICKNESS,
        borderLeftWidth: CORNER_THICKNESS,
        borderColor: palette.white,
        borderTopLeftRadius: 4,
    },
    cornerTopRight: {
        top: 0,
        right: 0,
        borderTopWidth: CORNER_THICKNESS,
        borderRightWidth: CORNER_THICKNESS,
        borderColor: palette.white,
        borderTopRightRadius: 4,
    },
    cornerBottomLeft: {
        bottom: 0,
        left: 0,
        borderBottomWidth: CORNER_THICKNESS,
        borderLeftWidth: CORNER_THICKNESS,
        borderColor: palette.white,
        borderBottomLeftRadius: 4,
    },
    cornerBottomRight: {
        bottom: 0,
        right: 0,
        borderBottomWidth: CORNER_THICKNESS,
        borderRightWidth: CORNER_THICKNESS,
        borderColor: palette.white,
        borderBottomRightRadius: 4,
    },
    bottomPanel: {
        backgroundColor: palette.cream100,
        paddingHorizontal: spacing.lg,
        paddingTop: spacing.md,
        paddingBottom: spacing.lg,
    },
    modalOverlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.4)',
    },
    modalScrollContent: {
        flexGrow: 1,
        justifyContent: 'flex-end',
    },
    modalContent: {
        backgroundColor: palette.cream100,
        borderTopLeftRadius: radius.lg,
        borderTopRightRadius: radius.lg,
        padding: spacing.lg,
        paddingBottom: spacing.xxl,
    },
});