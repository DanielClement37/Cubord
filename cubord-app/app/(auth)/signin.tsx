// app/(auth)/signin.tsx
import { View, StyleSheet } from 'react-native';
import { GoogleSigninButton } from '@react-native-google-signin/google-signin';
import { useAuth } from '@contexts/AuthContext';

export default function SignIn() {
    const { signInWithGoogle } = useAuth();
    return (
        <View style={styles.container}>
            <GoogleSigninButton
                size={GoogleSigninButton.Size.Wide}
                color={GoogleSigninButton.Color.Dark}
                onPress={signInWithGoogle}
            />
        </View>
    );
}
const styles = StyleSheet.create({
    container: { flex: 1, justifyContent: 'center', alignItems: 'center' }
});
