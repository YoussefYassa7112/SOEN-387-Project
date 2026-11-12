// Helpers used by every page to talk to the Locus backend.

const API_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080') + '/api/v1'

// Sends one request and returns everything about it:
//   request: what we sent (method, path, headers, body)
//   status:  the HTTP status code
//   body:    the JSON answer (or a short text)
// Error answers (400, 401, 404, 409...) are returned too, never thrown,
// so the page can show a message and the demo details can show the raw JSON.
export async function callApi(method, path, token, body, extraHeaders) {
    const headers = {}

    if (body !== undefined) {
        headers['Content-Type'] = 'application/json'
    }
    if (token) {
        headers['Authorization'] = 'Bearer ' + token
    }
    if (extraHeaders) {
        Object.assign(headers, extraHeaders)
    }

    const options = { method: method, headers: headers }
    if (body !== undefined) {
        options.body = JSON.stringify(body)
    }

    const request = { method: method, path: '/api/v1' + path, headers: headers, body: body }

    try {
        const response = await fetch(API_URL + path, options)
        const text = await response.text()

        // Some answers have no body (for example 204 No Content).
        let data = null
        if (text !== '') {
            try {
                data = JSON.parse(text)
            } catch {
                data = text
            }
        }

        return { request: request, status: response.status, body: data }
    } catch {
        // The backend is not running, or the browser blocked the call.
        return { request: request, status: 0, body: { message: 'Cannot reach the server. Is the backend running?' } }
    }
}

// True when the status code means success (200 to 299).
export function isSuccess(result) {
    return result.status >= 200 && result.status < 300
}

// A short message for the user when a request failed.
export function errorMessage(result) {
    if (result.status === 401) {
        return 'Please sign in first.'
    }
    if (result.body && result.body.message) {
        return result.body.message
    }
    return 'Something went wrong (status ' + result.status + ').'
}

// <input type="datetime-local"> gives "2026-12-01T10:00" with no time zone.
// The contract needs a full date-time with an offset, so we convert it.
export function toApiTime(inputValue) {
    if (inputValue === '') {
        return ''
    }
    return new Date(inputValue).toISOString()
}

// The opposite: turns an API date-time into a value for <input type="datetime-local">.
export function toInputTime(apiValue) {
    const date = new Date(apiValue)
    const pad = (number) => String(number).padStart(2, '0')
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
        + 'T' + pad(date.getHours()) + ':' + pad(date.getMinutes())
}

// Shows an API date-time in a readable way, in the user's time zone.
export function showTime(apiValue) {
    return new Date(apiValue).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
}

// Shows a price like "$10.00".
export function showPrice(value) {
    return '$' + Number(value).toFixed(2)
}
