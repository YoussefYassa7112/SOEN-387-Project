package com.squared.locus.controller;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The one JSON shape for every error response, as defined by ApiError in openapi.yaml.
 *
 * Example:
 * { "code": "PARKING_SPOT_UNAVAILABLE",
 *   "message": "The parking spot is already reserved during the requested time interval.",
 *   "correlationId": "req_8f91" }
 *
 * "details" is optional and is left out of the JSON when it is null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, String correlationId, Map<String, Object> details) {
}
