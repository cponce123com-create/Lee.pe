package pe.lee.app

import androidx.core.content.FileProvider

/**
 * FileProvider dedicated to the bundled EPUB.
 *
 * It is a distinct class (not `androidx.core.content.FileProvider`) on purpose:
 * the share-card feature already declares the plain FileProvider, and the manifest
 * merger collapses two providers with the same class name into one, which would
 * force a single path configuration for both.
 */
class BundledFileProvider : FileProvider()
