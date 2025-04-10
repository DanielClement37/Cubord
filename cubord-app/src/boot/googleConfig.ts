import { GoogleSignin } from '@react-native-google-signin/google-signin';
import Constants from 'expo-constants';


let configured = false;

export function configureGoogle() {
    const extra = Constants.expoConfig!.extra as any;
    if (configured) return;
    GoogleSignin.configure({
        webClientId:     extra.GOOGLE_WEB_CLIENT_ID,     // *required* for idToken
        offlineAccess:   true,                           // we want refresh tokens
        forceCodeForRefreshToken: false,                 // keep default when using idToken
        profileImageSize: 120
    });
    configured = true;
}