import { loadStripe } from '@stripe/stripe-js'

// Stripe's publishable key (pk_test_...). It is meant to be public, so it is safe in the browser.
// Never put the secret key (sk_test_...) here: that one stays on the backend.
const publishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY

// True when the card form can be shown. Without the key, the booking form falls back
// to a simple list of Stripe test cards.
export const STRIPE_ENABLED = Boolean(publishableKey)

export const stripePromise = STRIPE_ENABLED ? loadStripe(publishableKey) : null
