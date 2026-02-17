import Toast, { ToastShowParams } from 'react-native-toast-message';

const DEFAULTS: Partial<ToastShowParams> = {
    visibilityTime: 3000,
    topOffset: 56,
};

export function showSuccess(title: string, message?: string) {
    Toast.show({ ...DEFAULTS, type: 'success', text1: title, text2: message });
}

export function showError(title: string, message?: string) {
    Toast.show({ ...DEFAULTS, type: 'error', text1: title, text2: message, visibilityTime: 4000 });
}

export function showInfo(title: string, message?: string) {
    Toast.show({ ...DEFAULTS, type: 'info', text1: title, text2: message });
}