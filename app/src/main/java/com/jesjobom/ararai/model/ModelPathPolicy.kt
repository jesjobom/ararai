package com.jesjobom.ararai.model

import java.io.File

object ModelPathPolicy {
    private const val MODEL_DIRECTORY = "models"

    fun requireValidRelativePath(relativePath: String): String {
        require(relativePath.isNotBlank()) { "model.relativePath must not be blank" }
        require('\\' !in relativePath) {
            "model.relativePath must use forward slashes"
        }

        val segments = relativePath.split('/')
        require(segments.size >= 2 && segments.first() == MODEL_DIRECTORY) {
            "model.relativePath must be under models/"
        }
        require(segments.all { it.isNotEmpty() && it != "." && it != ".." }) {
            "model.relativePath must be normalized and contain no empty or traversal segments"
        }
        require(segments.none { '\u0000' in it }) {
            "model.relativePath must not contain NUL characters"
        }
        return relativePath
    }

    fun resolveContained(
        appFilesRoot: File,
        relativePath: String,
    ): File {
        requireValidRelativePath(relativePath)

        val canonicalAppRoot = appFilesRoot.canonicalFile
        val canonicalModelsRoot = File(canonicalAppRoot, MODEL_DIRECTORY).canonicalFile
        require(canonicalModelsRoot.isStrictChildOf(canonicalAppRoot)) {
            "Application model directory escapes the application files directory"
        }

        val candidate = File(canonicalAppRoot, relativePath).canonicalFile
        require(candidate.isStrictChildOf(canonicalModelsRoot)) {
            "Configured model path escapes the application model directory"
        }
        return candidate
    }

    private fun File.isStrictChildOf(parent: File): Boolean = path.startsWith(parent.path.withTrailingSeparator())

    private fun String.withTrailingSeparator(): String {
        if (endsWith(File.separator)) return this
        return "$this${File.separator}"
    }
}
