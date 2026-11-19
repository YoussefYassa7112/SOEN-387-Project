import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { GoogleOAuthProvider } from '@react-oauth/google'
import { Elements } from '@stripe/react-stripe-js'

import './index.css'
import App from './App.jsx'
import { stripePromise } from './stripe.js'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <GoogleOAuthProvider
      clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}
    >
      {/* Lets any page show Stripe's card form (stripePromise is null without a key). */}
      <Elements stripe={stripePromise}>
        <App />
      </Elements>
    </GoogleOAuthProvider>
  </StrictMode>,
)
