import React, { createContext, useContext, useState, useCallback } from 'react';
import AlertModal from './AlertModal';

export interface AlertOptions {
  title?: string;
  message: string;
  type?: 'success' | 'error' | 'info' | 'warning';
  autoClose?: number;
}

interface AlertState extends AlertOptions {
  resolve: () => void;
}

interface AlertContextValue {
  showAlert: (options: AlertOptions) => Promise<void>;
}

const AlertContext = createContext<AlertContextValue | null>(null);

export const AlertProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [alert, setAlert] = useState<AlertState | null>(null);

  const showAlert = useCallback((options: AlertOptions): Promise<void> => {
    return new Promise<void>((resolve) => {
      setAlert({ ...options, resolve });
    });
  }, []);

  const handleClose = useCallback(() => {
    if (alert) {
      alert.resolve();
      setAlert(null);
    }
  }, [alert]);

  return (
    <AlertContext.Provider value={{ showAlert }}>
      {children}
      <AlertModal
        isOpen={alert !== null}
        onClose={handleClose}
        title={alert?.title}
        message={alert?.message || ''}
        type={alert?.type}
        autoClose={alert?.autoClose}
      />
    </AlertContext.Provider>
  );
};

export const useAlert = (): AlertContextValue => {
  const context = useContext(AlertContext);
  if (!context) {
    throw new Error('useAlert must be used within AlertProvider');
  }
  return context;
};
