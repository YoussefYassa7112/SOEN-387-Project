// Opens Google Maps directions from where the driver is now to the spot's address.
// This is a plain link (free, no API key): Google Maps finds the driver's location itself.
function DirectionsButton({ address }) {
    const url = 'https://www.google.com/maps/dir/?api=1&destination=' + encodeURIComponent(address)
    return (
        <a className="button" href={url} target="_blank" rel="noopener noreferrer">
            Get directions
        </a>
    )
}

export default DirectionsButton
