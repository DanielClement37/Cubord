// src/components/dashboard/__tests__/NeedsAttentionSection.test.tsx
import React from 'react';
import { render, screen, fireEvent } from '@test/utils';
import { NeedsAttentionSection } from '../NeedsAttentionSection';
import { buildPantryItem, buildProduct, buildPantryStatistics } from '@test/factories';

describe('NeedsAttentionSection', () => {
    const baseProps = {
        expiringItems: undefined as any,
        statistics: undefined as any,
        isLoading: false,
        onExpiringSoonPress: jest.fn(),
        onLowStockPress: jest.fn(),
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('renders the section label', () => {
        render(<NeedsAttentionSection {...baseProps} />);

        expect(screen.getByText('NEEDS ATTENTION')).toBeTruthy();
    });

    it('shows a loading spinner when isLoading is true', () => {
        render(<NeedsAttentionSection {...baseProps} isLoading={true} />);

        expect(screen.getByLabelText('Loading')).toBeTruthy();
    });

    // ── Empty state ──────────────────────────────────
    it('shows the empty positive message when no expiring items and no low stock', () => {
        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={[]}
                statistics={buildPantryStatistics({ lowStockCount: 0 })}
            />,
        );

        expect(screen.getByText(/Nothing needs attention/)).toBeTruthy();
        expect(screen.getByText(/Everything is fresh and stocked/)).toBeTruthy();
    });

    // ── Expiring items ───────────────────────────────
    it('renders the expiring soon card with correct count', () => {
        const items = [
            buildPantryItem({ product: buildProduct({ name: 'Milk' }) }),
            buildPantryItem({ product: buildProduct({ name: 'Yogurt' }) }),
            buildPantryItem({ product: buildProduct({ name: 'Cream' }) }),
        ];

        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={items}
                statistics={buildPantryStatistics()}
            />,
        );

        expect(screen.getByText('Expiring Soon')).toBeTruthy();
        expect(screen.getByText(/\(3\)/)).toBeTruthy();
    });

    it('shows the soonest expiring product name as subtitle', () => {
        const items = [
            buildPantryItem({ product: buildProduct({ name: 'Whole Milk' }) }),
        ];

        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={items}
                statistics={buildPantryStatistics()}
            />,
        );

        expect(screen.getByText('Whole Milk expires soon')).toBeTruthy();
    });

    it('calls onExpiringSoonPress when the expiring card is tapped', () => {
        const items = [buildPantryItem()];

        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={items}
                statistics={buildPantryStatistics()}
            />,
        );

        fireEvent.press(screen.getByText('Expiring Soon'));

        expect(baseProps.onExpiringSoonPress).toHaveBeenCalledTimes(1);
    });

    // ── Low stock ────────────────────────────────────
    it('renders the low stock card when lowStockCount > 0', () => {
        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={[]}
                statistics={buildPantryStatistics({ lowStockCount: 5 })}
            />,
        );

        expect(screen.getByText('Low Stock')).toBeTruthy();
        expect(screen.getByText(/\(5\)/)).toBeTruthy();
        expect(screen.getByText('5 items running low')).toBeTruthy();
    });

    it('uses singular text for 1 low stock item', () => {
        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={[]}
                statistics={buildPantryStatistics({ lowStockCount: 1 })}
            />,
        );

        expect(screen.getByText('1 item running low')).toBeTruthy();
    });

    it('calls onLowStockPress when the low stock card is tapped', () => {
        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={[]}
                statistics={buildPantryStatistics({ lowStockCount: 2 })}
            />,
        );

        fireEvent.press(screen.getByText('Low Stock'));

        expect(baseProps.onLowStockPress).toHaveBeenCalledTimes(1);
    });

    // ── Both alerts ──────────────────────────────────
    it('renders both expiring and low stock cards when both exist', () => {
        const items = [buildPantryItem()];

        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={items}
                statistics={buildPantryStatistics({ lowStockCount: 3 })}
            />,
        );

        expect(screen.getByText('Expiring Soon')).toBeTruthy();
        expect(screen.getByText('Low Stock')).toBeTruthy();
    });

    it('hides the expiring card when list is empty but shows low stock', () => {
        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={[]}
                statistics={buildPantryStatistics({ lowStockCount: 2 })}
            />,
        );

        expect(screen.queryByText('Expiring Soon')).toBeNull();
        expect(screen.getByText('Low Stock')).toBeTruthy();
    });

    it('hides the low stock card when count is 0 but shows expiring', () => {
        const items = [buildPantryItem()];

        render(
            <NeedsAttentionSection
                {...baseProps}
                expiringItems={items}
                statistics={buildPantryStatistics({ lowStockCount: 0 })}
            />,
        );

        expect(screen.getByText('Expiring Soon')).toBeTruthy();
        expect(screen.queryByText('Low Stock')).toBeNull();
    });
});