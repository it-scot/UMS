import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";

const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

export const firebaseConfigured = Object.values(config).every(Boolean);
export const localEmailLogin = import.meta.env.DEV
  && import.meta.env.VITE_LOCAL_EMAIL_LOGIN === "true";
export const auth = firebaseConfigured
  ? getAuth(initializeApp(config))
  : null;
