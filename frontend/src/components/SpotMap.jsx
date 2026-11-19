import { useState } from 'react'
import { CircleMarker, MapContainer, Popup, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { showPrice } from '../api.js'

// A free map (Leaflet + OpenStreetMap tiles, no API key) with one dot per parking spot.
// LOCUS-51: one spot on its page. LOCUS-53: all spots of a search.
// LOCUS-54: if a spot has no coordinates, or the map tiles cannot load, we show a short
// note instead, and the rest of the page (details, booking) keeps working.
function SpotMap({ spots, openSpot }) {
    const [tilesFailed, setTilesFailed] = useState(false)

    // Only spots that the server could place on the map.
    const spotsWithPlace = spots.filter((spot) => spot.latitude != null && spot.longitude != null)

    if (spotsWithPlace.length === 0) {
        return <p className="small muted">No map location yet for {spots.length === 1 ? 'this spot' : 'these spots'}.</p>
    }

    // The map zooms to fit every dot.
    const points = spotsWithPlace.map((spot) => [spot.latitude, spot.longitude])

    return (
        <div>
            {/* The key makes a new map when the list of spots changes, so it zooms to the new dots. */}
            <MapContainer
                key={spotsWithPlace.map((spot) => spot.spotId).join(',')}
                className="spot-map"
                bounds={points}
                boundsOptions={{ padding: [30, 30], maxZoom: 16 }}
                scrollWheelZoom={false}
            >
                <TileLayer
                    url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                    eventHandlers={{ tileerror: () => setTilesFailed(true) }}
                />
                {spotsWithPlace.map((spot) => (
                    <CircleMarker key={spot.spotId} center={[spot.latitude, spot.longitude]} radius={9} pathOptions={{ fillOpacity: 0.8 }}>
                        <Popup>
                            <strong>{spot.title}</strong>
                            <br />
                            {showPrice(spot.pricePerHour)} / hour
                            {openSpot && (
                                <>
                                    <br />
                                    <button className="link-button" onClick={() => openSpot(spot.spotId)}>View spot</button>
                                </>
                            )}
                        </Popup>
                    </CircleMarker>
                ))}
            </MapContainer>
            {tilesFailed && (
                <p className="small muted">The map could not load right now. All parking details are still shown.</p>
            )}
            {spotsWithPlace.length < spots.length && (
                <p className="small muted">{spots.length - spotsWithPlace.length} spot(s) have no map location yet.</p>
            )}
        </div>
    )
}

export default SpotMap
