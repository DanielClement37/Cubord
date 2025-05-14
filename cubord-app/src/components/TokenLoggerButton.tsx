import React from 'react';
import { Button, StyleSheet } from 'react-native';
import { supabase } from '@services/supabase';

export const TokenLoggerButton: React.FC = () => {
  const logJwtToken = async () => {
    try {
      const session = await supabase.auth.getSession();
      if (session && session.data.session) {
        console.log('JWT Token:', session.data.session.access_token);
      } else {
        console.log('No active session found');
      }
    } catch (error) {
      console.error('Error getting JWT token:', error);
    }
  };

  return (
    <Button 
      title="Log JWT Token" 
      onPress={logJwtToken} 
    />
  );
};

const styles = StyleSheet.create({});