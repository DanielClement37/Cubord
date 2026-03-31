// src/components/dashboard/__tests__/PantryOverviewCard.test.tsx
import React from 'react';
import { render, screen } from '@test/utils';
import { PantryOverviewCard } from '../PantryOverviewCard';

describe('PantryOverviewCard', () => {
    it('renders the title', () => {
        render(
            <PantryOverviewCard totalItems={0} locationsCount={0} isLoading={false} />,
        );

        expect(screen.getByText('Pantry Overview')).toBeTruthy();
    });

    it('displays a loading indicator when isLoading is true', () => {
        render(
            <PantryOverviewCard totalItems={undefined} locationsCount={undefined} isLoading={true} />,
        );

        expect(screen.getByLabelText('Loading')).toBeTruthy();
        expect(screen.queryByText('Items')).toBeNull();
    });

    it('renders total items and locations count when loaded', () => {
        render(
            <PantryOverviewCard totalItems={47} locationsCount={3} isLoading={false} />,
        );

        expect(screen.getByText('47')).toBeTruthy();
        expect(screen.getByText('Items')).toBeTruthy();
        expect(screen.getByText('3')).toBeTruthy();
        expect(screen.getByText('Locations')).toBeTruthy();
    });

    it('defaults to 0 when values are undefined', () => {
        render(
            <PantryOverviewCard totalItems={undefined} locationsCount={undefined} isLoading={false} />,
        );

        const zeros = screen.getAllByText('0');
        expect(zeros).toHaveLength(2);
    });
});