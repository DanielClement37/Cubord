import { ScreenContainer, Text } from '@/components/ui';

export default function PantryScreen() {
    return (
        <ScreenContainer >
            <Text size="xl" weight="bold">Pantry</Text>
            <Text size="md" color="secondary">Your pantry items will appear here</Text>
        </ScreenContainer>
    );
}