import { ScreenContainer, Text } from '@/components/ui';

export default function ScanScreen() {
    return (
        <ScreenContainer >
            <Text size="xl" weight="bold">Scan</Text>
            <Text size="md" color="secondary">Barcode scanner coming soon</Text>
        </ScreenContainer>
    );
}