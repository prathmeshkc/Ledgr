package com.prachaudhari.ledgr.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

/**
 * Centralized exception handler for the entire API.
 *
 * Catches exceptions thrown by controllers and services and maps them
 * to consistent JSON error responses with appropriate HTTP status codes.
 * Without this, unhandled exceptions bubble up as 500s with Spring's
 * default HTML error page — unhelpful for API consumers.
 *
 * Error response format:
 * ```json
 * {
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Invalid state transition: AUTHORIZED -> AUTHORIZED",
 *   "timestamp": "2026-10-10T00:00:00Z"
 * }
 * ```
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handles invalid arguments — bad state transitions, invalid account types,
     * malformed request data.
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                status = 400,
                error = "Bad Request",
                message = ex.message ?: "Invalid request"
            )
        )
    }

    /**
     * Handles illegal state — typically missing infrastructure like the Hold Account
     * not being seeded.
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorResponse> {
        logger.error("Internal error: {}", ex.message)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = 500,
                error = "Internal Server Error",
                message = ex.message ?: "Unexpected server error"
            )
        )
    }

    /**
     * Handles entity not found — payment or account lookup by ID that returns no result.
     */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        logger.warn("Not found: {}", ex.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse(
                status = 404,
                error = "Not Found",
                message = ex.message ?: "Resource not found"
            )
        )
    }
}

data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)