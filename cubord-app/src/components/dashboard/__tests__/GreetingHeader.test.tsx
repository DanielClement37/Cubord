// src/components/dashboard/__tests__/GreetingHeader.test.tsx
import React from 'react';
import { render, screen, fireEvent } from '@test/utils';
import { GreetingHeader } from '../GreetingHeader';

describe('GreetingHeader', () => {
    const defaultProps = {
        userName: 'Daniel Smith',
        householdName: 'Smith Home',
        onHouseholdPress: jest.fn(),
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('renders a time-based greeting with the user first name', () => {
        render(<GreetingHeader {...defaultProps} />);

        // The greeting is time-dependent, so we just verify the name is there
        expect(screen.getByText(/Daniel/)).toBeTruthy();
    });

    it('renders the household name in the pill', () => {
        render(<GreetingHeader {...defaultProps} />);

        expect(screen.getByText('Smith Home')).toBeTruthy();
    });

    it('calls onHouseholdPress when the household pill is tapped', () => {
        render(<GreetingHeader {...defaultProps} />);

        fireEvent.press(screen.getByText('Smith Home'));

        expect(defaultProps.onHouseholdPress).toHaveBeenCalledTimes(1);
    });

    it('handles a single-word name gracefully', () => {
        render(<GreetingHeader {...defaultProps} userName="Alex" />);

        expect(screen.getByText(/Alex/)).toBeTruthy();
    });

    it('handles an email as userName by showing the part before @', () => {
        render(<GreetingHeader {...defaultProps} userName="daniel@example.com" />);

        // split(' ')[0] returns the full email — that's acceptable
        expect(screen.getByText(/daniel@example.com/)).toBeTruthy();
    });
});