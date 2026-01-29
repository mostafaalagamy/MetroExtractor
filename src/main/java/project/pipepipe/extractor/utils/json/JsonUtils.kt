package project.pipepipe.extractor.utils.json

import com.fasterxml.jackson.databind.JsonNode

// String methods
fun JsonNode.requireString(fieldName: String): String {
    val node = when {
        fieldName == "/" -> this
        fieldName.startsWith("/") -> this.at(fieldName)
        else -> this.path(fieldName)
    }
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    return node.asText()
}

fun JsonNode.requireString(index: Int): String {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    return node.asText()
}

// Int methods
fun JsonNode.requireInt(fieldName: String): Int {
    val node = if (fieldName.startsWith("/")) this.at(fieldName) else this.path(fieldName)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    if (!node.isNumber && !node.isTextual) {
        throw IllegalArgumentException("Required field '$fieldName' is not a number")
    }
    return node.asInt()
}

fun JsonNode.requireInt(index: Int): Int {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    if (!node.isNumber && !node.isTextual) {
        throw IllegalArgumentException("Required element at index '$index' is not a number")
    }
    return node.asInt()
}

// Long methods
fun JsonNode.requireLong(fieldName: String): Long {
    val node = if (fieldName.startsWith("/")) this.at(fieldName) else this.path(fieldName)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    if (!node.isNumber && !node.isTextual) {
        throw IllegalArgumentException("Required field '$fieldName' is not a number")
    }
    return node.asLong()
}

fun JsonNode.requireLong(index: Int): Long {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    if (!node.isNumber && !node.isTextual) {
        throw IllegalArgumentException("Required element at index '$index' is not a number")
    }
    return node.asLong()
}

// Double methods
fun JsonNode.requireDouble(fieldName: String): Double {
    val node = if (fieldName.startsWith("/")) this.at(fieldName) else this.path(fieldName)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    if (!node.isNumber && !node.isTextual) {
        throw IllegalArgumentException("Required field '$fieldName' is not a number")
    }
    return node.asDouble()
}

fun JsonNode.requireDouble(index: Int): Double {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    if (!node.isNumber && !node.isTextual) {
        throw IllegalArgumentException("Required element at index '$index' is not a number")
    }
    return node.asDouble()
}

// Boolean methods
fun JsonNode.requireBoolean(fieldName: String): Boolean {
    val node = if (fieldName.startsWith("/")) this.at(fieldName) else this.path(fieldName)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    if (!node.isBoolean) {
        throw IllegalArgumentException("Required field '$fieldName' is not a boolean")
    }
    return node.asBoolean()
}

fun JsonNode.requireBoolean(index: Int): Boolean {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    if (!node.isBoolean) {
        throw IllegalArgumentException("Required element at index '$index' is not a boolean")
    }
    return node.asBoolean()
}

// Node methods
fun JsonNode.requireNode(fieldName: String): JsonNode {
    val node = if (fieldName.startsWith("/")) this.at(fieldName) else this.path(fieldName)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    return node
}

fun JsonNode.requireNode(index: Int): JsonNode {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    return node
}

// Array methods
fun JsonNode.requireArray(fieldName: String): JsonNode {
    val node = if (fieldName.startsWith("/")) this.at(fieldName) else this.path(fieldName)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    if (!node.isArray) {
        throw IllegalArgumentException("Required field '$fieldName' is not an array")
    }
    return node
}

fun JsonNode.requireArray(index: Int): JsonNode {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    if (!node.isArray) {
        throw IllegalArgumentException("Required element at index '$index' is not an array")
    }
    return node
}

// Object methods
fun JsonNode.requireObject(fieldName: String): JsonNode {
    if (fieldName == "/") return this
    val node = if (fieldName.startsWith("/")) this.at(fieldName) else this.path(fieldName)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required field '$fieldName' is missing or null")
    }
    if (!node.isObject) {
        throw IllegalArgumentException("Required field '$fieldName' is not an object")
    }
    return node
}

fun JsonNode.requireObject(index: Int): JsonNode {
    require(this.isArray) { "Node must be an array to access by index" }
    val node = this.path(index)
    if (node.isMissingNode || node.isNull) {
        throw IllegalArgumentException("Required element at index '$index' is missing or null")
    }
    if (!node.isObject) {
        throw IllegalArgumentException("Required element at index '$index' is not an object")
    }
    return node
}

class InvalidJsonResponseException(url: String): Exception(url)
