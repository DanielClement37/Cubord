// src/components/ui/DatePickerModal.tsx
import React, { useState, useMemo, useCallback } from 'react';
import {
    View,
    Modal,
    Pressable,
    ScrollView,
    StyleSheet,
} from 'react-native';
import { Text, Button } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';

interface DatePickerModalProps {
    visible: boolean;
    value: Date;
    minimumDate?: Date;
    onConfirm: (date: Date) => void;
    onCancel: () => void;
}

const MONTHS_SHORT = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

const MONTHS_FULL = [
    'January', 'February', 'March', 'April', 'May', 'June',
    'July', 'August', 'September', 'October', 'November', 'December',
];

const DAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

interface QuickOption {
    label: string;
    days: number;
}

const QUICK_OPTIONS: QuickOption[] = [
    { label: '3 days', days: 3 },
    { label: '1 week', days: 7 },
    { label: '2 weeks', days: 14 },
    { label: '1 month', days: 30 },
    { label: '3 months', days: 90 },
    { label: '6 months', days: 180 },
    { label: '1 year', days: 365 },
];

/** Strip hours/minutes/seconds so we compare dates only */
function stripTime(d: Date): Date {
    return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function addDays(base: Date, days: number): Date {
    const d = new Date(base);
    d.setDate(d.getDate() + days);
    return stripTime(d);
}

function isSameDay(a: Date, b: Date): boolean {
    return (
        a.getFullYear() === b.getFullYear() &&
        a.getMonth() === b.getMonth() &&
        a.getDate() === b.getDate()
    );
}

export function DatePickerModal({
    visible,
    value,
    minimumDate,
    onConfirm,
    onCancel,
}: DatePickerModalProps) {
    const today = useMemo(() => stripTime(new Date()), []);
    const minDate = useMemo(
        () => (minimumDate ? stripTime(minimumDate) : today),
        [minimumDate, today],
    );

    // The selected date — initialised from the passed-in value
    const [selected, setSelected] = useState<Date>(stripTime(value));

    // The month currently being viewed in the calendar
    const [viewYear, setViewYear] = useState(value.getFullYear());
    const [viewMonth, setViewMonth] = useState(value.getMonth());

    // ── Quick-option helpers ────────────────────────
    const quickDates = useMemo(
        () => QUICK_OPTIONS.map((o) => ({ ...o, date: addDays(today, o.days) })),
        [today],
    );

    const handleQuickSelect = useCallback(
        (date: Date) => {
            setSelected(date);
            setViewYear(date.getFullYear());
            setViewMonth(date.getMonth());
        },
        [],
    );

    // ── Calendar grid data ──────────────────────────
    const calendarWeeks = useMemo(() => {
        const firstOfMonth = new Date(viewYear, viewMonth, 1);
        const startDow = firstOfMonth.getDay(); // 0 = Sun
        const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();

        const cells: (Date | null)[] = [];

        // Leading blanks
        for (let i = 0; i < startDow; i++) {
            cells.push(null);
        }
        // Actual days
        for (let d = 1; d <= daysInMonth; d++) {
            cells.push(new Date(viewYear, viewMonth, d));
        }
        // Trailing blanks to fill last row
        while (cells.length % 7 !== 0) {
            cells.push(null);
        }

        // Split into weeks
        const weeks: (Date | null)[][] = [];
        for (let i = 0; i < cells.length; i += 7) {
            weeks.push(cells.slice(i, i + 7));
        }
        return weeks;
    }, [viewYear, viewMonth]);

    // ── Month navigation ────────────────────────────
    const goToPrevMonth = useCallback(() => {
        setViewMonth((m) => {
            if (m === 0) {
                setViewYear((y) => y - 1);
                return 11;
            }
            return m - 1;
        });
    }, []);

    const goToNextMonth = useCallback(() => {
        setViewMonth((m) => {
            if (m === 11) {
                setViewYear((y) => y + 1);
                return 0;
            }
            return m + 1;
        });
    }, []);

    const canGoPrev = useMemo(() => {
        const prevLast = new Date(viewYear, viewMonth, 0); // last day of previous month
        return prevLast >= minDate;
    }, [viewYear, viewMonth, minDate]);

    // ── Confirm ─────────────────────────────────────
    const handleConfirm = useCallback(() => {
        onConfirm(selected);
    }, [onConfirm, selected]);

    return (
        <Modal visible={visible} transparent animationType="fade">
            <Pressable style={styles.overlay} onPress={onCancel}>
                <Pressable style={styles.container} onPress={() => {}}>
                    <ScrollView
                        showsVerticalScrollIndicator={false}
                        bounces={false}
                    >
                        <Text size="lg" weight="bold" style={styles.title}>
                            Select Expiration Date
                        </Text>

                        {/* ── Quick-select shortcuts ── */}
                        <Text
                            size="sm"
                            weight="medium"
                            color="secondary"
                            style={styles.sectionLabel}
                        >
                            Quick Select
                        </Text>
                        <View style={styles.quickRow}>
                            {quickDates.map((opt) => {
                                const isActive = isSameDay(opt.date, selected);
                                return (
                                    <Pressable
                                        key={opt.label}
                                        style={[
                                            styles.quickChip,
                                            isActive && styles.quickChipActive,
                                        ]}
                                        onPress={() => handleQuickSelect(opt.date)}
                                    >
                                        <Text
                                            size="sm"
                                            weight={isActive ? 'bold' : 'medium'}
                                            style={
                                                isActive
                                                    ? { color: palette.white }
                                                    : { color: palette.sage700 }
                                            }
                                        >
                                            {opt.label}
                                        </Text>
                                    </Pressable>
                                );
                            })}
                        </View>

                        {/* ── Divider ── */}
                        <View style={styles.divider} />

                        {/* ── Calendar header ── */}
                        <Text
                            size="sm"
                            weight="medium"
                            color="secondary"
                            style={styles.sectionLabel}
                        >
                            Or pick a specific date
                        </Text>
                        <View style={styles.calendarHeader}>
                            <Pressable
                                onPress={goToPrevMonth}
                                hitSlop={12}
                                disabled={!canGoPrev}
                                style={{ opacity: canGoPrev ? 1 : 0.3 }}
                            >
                                <Text size="lg" weight="bold">
                                    ◀
                                </Text>
                            </Pressable>
                            <Text size="md" weight="bold">
                                {MONTHS_FULL[viewMonth]} {viewYear}
                            </Text>
                            <Pressable onPress={goToNextMonth} hitSlop={12}>
                                <Text size="lg" weight="bold">
                                    ▶
                                </Text>
                            </Pressable>
                        </View>

                        {/* ── Day-of-week labels ── */}
                        <View style={styles.weekRow}>
                            {DAY_LABELS.map((d) => (
                                <View key={d} style={styles.dayCell}>
                                    <Text
                                        size="sm"
                                        weight="semibold"
                                        color="secondary"
                                    >
                                        {d}
                                    </Text>
                                </View>
                            ))}
                        </View>

                        {/* ── Calendar grid ── */}
                        {calendarWeeks.map((week, wi) => (
                            <View key={wi} style={styles.weekRow}>
                                {week.map((date, di) => {
                                    if (!date) {
                                        return (
                                            <View
                                                key={`blank-${di}`}
                                                style={styles.dayCell}
                                            />
                                        );
                                    }

                                    const disabled = date < minDate;
                                    const isSelected = isSameDay(date, selected);
                                    const isToday = isSameDay(date, today);

                                    return (
                                        <Pressable
                                            key={date.getDate()}
                                            style={[
                                                styles.dayCell,
                                                isSelected && styles.dayCellSelected,
                                                isToday &&
                                                    !isSelected &&
                                                    styles.dayCellToday,
                                            ]}
                                            disabled={disabled}
                                            onPress={() => setSelected(date)}
                                        >
                                            <Text
                                                size="sm"
                                                weight={
                                                    isSelected
                                                        ? 'bold'
                                                        : isToday
                                                          ? 'semibold'
                                                          : 'regular'
                                                }
                                                style={[
                                                    disabled && {
                                                        color: palette.sand200,
                                                    },
                                                    isSelected && {
                                                        color: palette.white,
                                                    },
                                                    isToday &&
                                                        !isSelected && {
                                                            color: palette.sage700,
                                                        },
                                                ]}
                                            >
                                                {date.getDate()}
                                            </Text>
                                        </Pressable>
                                    );
                                })}
                            </View>
                        ))}

                        {/* ── Selected date preview ── */}
                        <View style={styles.previewRow}>
                            <Text size="sm" color="secondary">
                                Selected:{' '}
                            </Text>
                            <Text size="sm" weight="semibold">
                                {MONTHS_SHORT[selected.getMonth()]}{' '}
                                {selected.getDate()}, {selected.getFullYear()}
                            </Text>
                        </View>

                        {/* ── Action buttons ── */}
                        <View style={styles.actions}>
                            <Button
                                label="Cancel"
                                variant="ghost"
                                onPress={onCancel}
                                style={{ flex: 1 }}
                            />
                            <Button
                                label="Confirm"
                                onPress={handleConfirm}
                                style={{ flex: 1 }}
                            />
                        </View>
                    </ScrollView>
                </Pressable>
            </Pressable>
        </Modal>
    );
}

const CELL_SIZE = 40;

const styles = StyleSheet.create({
    overlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.4)',
        justifyContent: 'center',
        alignItems: 'center',
        padding: spacing.lg,
    },
    container: {
        backgroundColor: palette.white,
        borderRadius: radius.lg,
        padding: spacing.lg,
        width: '100%',
        maxHeight: '85%',
    },
    title: {
        marginBottom: spacing.sm,
        textAlign: 'center',
    },
    sectionLabel: {
        marginBottom: spacing.sm,
    },

    // ── Quick select ──
    quickRow: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: spacing.xs,
        marginBottom: spacing.sm,
    },
    quickChip: {
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm,
        borderRadius: radius.full,
        backgroundColor: palette.sage50,
        borderWidth: 1,
        borderColor: palette.sage200,
    },
    quickChipActive: {
        backgroundColor: palette.sage700,
        borderColor: palette.sage700,
    },

    // ── Divider ──
    divider: {
        height: 1,
        backgroundColor: palette.cream300,
        marginVertical: spacing.md,
    },

    // ── Calendar ──
    calendarHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: spacing.sm,
        paddingHorizontal: spacing.xs,
    },
    weekRow: {
        flexDirection: 'row',
        justifyContent: 'space-around',
    },
    dayCell: {
        width: CELL_SIZE,
        height: CELL_SIZE,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: radius.full,
        marginVertical: 2,
    },
    dayCellSelected: {
        backgroundColor: palette.sage700,
    },
    dayCellToday: {
        borderWidth: 1.5,
        borderColor: palette.sage400,
    },

    // ── Preview ──
    previewRow: {
        flexDirection: 'row',
        justifyContent: 'center',
        alignItems: 'center',
        marginTop: spacing.md,
        marginBottom: spacing.xs,
    },

    // ── Actions ──
    actions: {
        flexDirection: 'row',
        gap: spacing.sm,
        marginTop: spacing.md,
    },
});