// src/components/ui/TabIcons.tsx
import React from 'react';
import HomeIcon from '@assets/images/svgs/HomeTabIcon.svg';
import ScanIcon from '@assets/images/svgs/BarcodeTabIcon.svg';
import PantryIcon from '@assets/images/svgs/PantryTabIcon.svg';
import ProfileIcon from '@assets/images/svgs/ProfileTabIcon.svg';

const ICON_SIZE = 26;

type TabIconProps = {
    focused: boolean;
    color: string;
};

export function HomeTabIcon({color}: TabIconProps) {
    return <HomeIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}

export function ScanTabIcon({color}: TabIconProps) {
    return <ScanIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}

export function PantryTabIcon({color}: TabIconProps) {
    return <PantryIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}

export function ProfileTabIcon({color}: TabIconProps) {
    return <ProfileIcon width={ICON_SIZE} height={ICON_SIZE} color={color}/>;
}