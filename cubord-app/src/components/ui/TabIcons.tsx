// src/components/ui/TabIcons.tsx
import React from 'react';
import {View, StyleSheet} from 'react-native';
import {SvgProps} from 'react-native-svg';
import HomeIcon from '@assets/images/svgs/HomeTabIcon.svg';
import ScanIcon from '@assets/images/svgs/BarcodeTabIcon.svg';
import PantryIcon from '@assets/images/svgs/PantryTabIcon.svg';
import ProfileIcon from '@assets/images/svgs/ProfileTabIcon.svg';
import {palette} from '@/styles/colors';
import {shadow} from '@/styles/tokens';

const ICON_SIZE = 26;

type TabIconProps = {
    focused: boolean;
    color: string;
};

export function HomeTabIcon({color}: TabIconProps) {
    return <HomeIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}

export function ScanTabIcon({focused, color}: TabIconProps) {
    return <ScanIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}

export function PantryTabIcon({color}: TabIconProps) {
    return <PantryIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}

export function ProfileTabIcon({color}: TabIconProps) {
    return <ProfileIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}

const styles = StyleSheet.create({
    scanBubble: {
        width: 50,
        height: 50,
        borderRadius: 25,
        justifyContent: 'center',
        alignItems: 'center',
    },
    scanActive: {
        backgroundColor: palette.sage500,
        ...shadow.md,
    },
    scanInactive: {
        backgroundColor: palette.cream200,
    },
});