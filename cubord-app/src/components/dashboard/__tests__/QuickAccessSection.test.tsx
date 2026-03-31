// src/components/dashboard/__tests__/QuickAccessSection.test.tsx
import React from 'react';
import { render, screen, fireEvent } from '@test/utils';
import { QuickAccessSection } from '../QuickAccessSection';
import { buildLocation } from '@test/factories';

describe('QuickAccessSection', () => {
    const defaultProps = {
        locations: undefined as any,
        isLoading: false,
        onLocationPress: jest.fn(),
        onAddLocationPress: jest.fn(),
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('renders the section label', () => {
        render(<QuickAccessSection {...defaultProps} locations={[]} />);

        expect(screen.getByText('QUICK ACCESS')).toBeTruthy();
    });

    it('shows a loading spinner when isLoading is true', () => {
        render(<QuickAccessSection {...defaultProps} isLoading={true} />);

        expect(screen.getByLabelText('Loading')).toBeTruthy();
    });

    it('renders location chips for each location', () => {
        const locations = [
            buildLocation({ name: 'Kitchen Pantry' }),
            buildLocation({ name: 'Garage Freezer' }),
        ];

        render(<QuickAccessSection {...defaultProps} locations={locations} />);

        expect(screen.getByText('Kitchen Pantry')).toBeTruthy();
        expect(screen.getByText('Garage Freezer')).toBeTruthy();
    });

    it('always renders the "Add Location" chip', () => {
        render(<QuickAccessSection {...defaultProps} locations={[]} />);

        expect(screen.getByText(/Add/)).toBeTruthy();
        expect(screen.getByText(/Location/)).toBeTruthy();
    });

    it('calls onLocationPress with the location when a chip is tapped', () => {
        const location = buildLocation({ name: 'Fridge' });

        render(<QuickAccessSection {...defaultProps} locations={[location]} />);
        fireEvent.press(screen.getByText('Fridge'));

        expect(defaultProps.onLocationPress).toHaveBeenCalledWith(location);
    });

    it('calls onAddLocationPress when the add chip is tapped', () => {
        render(<QuickAccessSection {...defaultProps} locations={[]} />);

        fireEvent.press(screen.getByText(/Add/));

        expect(defaultProps.onAddLocationPress).toHaveBeenCalledTimes(1);
    });

    it('renders no location chips when locations is an empty array', () => {
        render(<QuickAccessSection {...defaultProps} locations={[]} />);

        // Only the "Add Location" chip should exist
        expect(screen.getByText(/Add/)).toBeTruthy();
    });
});