// Small helper used by every part of the test page to talk to the backend.

const API_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080') + '/api/v1'

// Sends one request and returns what happened: the request line, the status code
// and the response body. Error responses (400, 401, 404, 409...) are returned too,
// so the page can show them instead of hiding them.
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

    const request = method + ' /api/v1' + path

    try {
        const response = await fetch(API_URL + path, options)
        const text = await response.text()

        // Some answers have no body (for example 204 No Content).
        let data = '(no body)'
        if (text !== '') {
            try {
                data = JSON.parse(text)
            } catch {
                data = text
            }
        }

        return { request: request, status: response.status, body: data }
    } catch (error) {
        // The backend is not running, or the browser blocked the call.
        return { request: request, status: 'network error', body: error.message }
    }
}

// <input type="datetime-local"> gives "2026-12-01T10:00" with no time zone.
// The contract needs a full date-time with an offset, so we convert it.
export function toApiTime(localValue) {
    if (localValue === '') {
        return ''
    }
    return new Date(localValue).toISOString()
}
