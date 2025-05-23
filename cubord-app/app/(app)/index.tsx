import { Button, Text, View } from 'react-native';
import { useAuth } from '@contexts/AuthContext';
import {TokenLoggerButton} from "@components/TokenLoggerButton";

export default function HomeScreen() {
    const { signOut } = useAuth();
    return (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
            <Text>Welcome! You are signed in.</Text>
            <TokenLoggerButton />
            <Button title="Sign Out" onPress={signOut} />
        </View>
    );
}