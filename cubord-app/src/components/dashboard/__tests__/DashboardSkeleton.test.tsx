// src/components/dashboard/__tests__/DashboardSkeleton.test.tsx
import React from 'react';
import { render, screen } from '@test/utils';
import { DashboardSkeleton } from '../DashboardSkeleton';

describe('DashboardSkeleton', () => {
    it('renders without crashing', () => {
        const { toJSON } = render(<DashboardSkeleton />);

        expect(toJSON()).toBeTruthy();
    });

    it('renders a loading indicator', () => {
        render(<DashboardSkeleton />);

        expect(screen.getByLabelText('Loading')).toBeTruthy();
    });
});